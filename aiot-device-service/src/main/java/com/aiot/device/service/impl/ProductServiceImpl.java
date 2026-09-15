package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.client.KnowledgeRebuildClient;
import com.aiot.device.dto.ProductReq;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.entity.Product;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.service.ProductService;
import com.aiot.device.support.DeviceModelStandardizer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private KnowledgeRebuildClient knowledgeRebuildClient;

    @Autowired
    private DeviceModelStandardizer deviceModelStandardizer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createProduct(ProductReq req) {
        String rawDeviceModelJson = resolveRawDeviceModelJson(req.getThingModelJson(), req.getDeviceModelJson());
        String productKey = resolveProductKey(req, rawDeviceModelJson);
        Product product = new Product();
        product.setProductKey(productKey);
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setNodeType(req.getNodeType());
        product.setThingModelJson(deviceModelStandardizer.standardize(rawDeviceModelJson, productKey, req.getName()));

        productRepository.insert(product);
        knowledgeRebuildClient.rebuild(productKey, product.getThingModelJson(), "product-create");
        return productKey;
    }

    @Override
    public ProductResp getProductByKey(String productKey) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        Product product = productRepository.selectOne(wrapper);
        
        if (product == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "产品不存在");
        }
        
        return convertToResp(product);
    }

    @Override
    public List<ProductResp> listProducts() {
        List<Product> products = productRepository.selectList(null);
        return products.stream().map(this::convertToResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateThingModel(String productKey, String thingModelJson) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        Product product = productRepository.selectOne(wrapper);
        
        if (product == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "产品不存在");
        }
        String standardized = deviceModelStandardizer.standardize(thingModelJson, product.getProductKey(), product.getName());
        product.setThingModelJson(standardized);
        productRepository.updateById(product);
        knowledgeRebuildClient.rebuild(productKey, standardized, "thing-model-update");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(String productKey) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        Product product = productRepository.selectOne(wrapper);

        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "产品不存在");
        }

        productRepository.deleteById(product.getId());
    }

    private ProductResp convertToResp(Product product) {
        String standardized = deviceModelStandardizer.standardize(product.getThingModelJson(), product.getProductKey(), product.getName());
        ProductResp resp = new ProductResp();
        resp.setId(product.getId());
        resp.setProductKey(product.getProductKey());
        resp.setDeviceModelKey(product.getProductKey());
        resp.setName(product.getName());
        resp.setDescription(product.getDescription());
        resp.setNodeType(product.getNodeType());
        resp.setThingModelJson(standardized);
        resp.setDeviceModelJson(standardized);
        return resp;
    }

    private String resolveRawDeviceModelJson(String thingModelJson, String deviceModelJson) {
        if (StringUtils.hasText(deviceModelJson)) {
            return deviceModelJson;
        }
        return thingModelJson;
    }

    private String resolveProductKey(ProductReq req, String rawDeviceModelJson) {
        String explicitProductKey = deviceModelStandardizer.normalizeIdentifier(req.getProductKey());
        if (StringUtils.hasText(explicitProductKey)) {
            ensureProductKeyAvailable(explicitProductKey);
            return explicitProductKey;
        }
        String deviceModelIdentifier = deviceModelStandardizer.extractIdentifierCandidate(rawDeviceModelJson);
        if (StringUtils.hasText(deviceModelIdentifier)) {
            ensureProductKeyAvailable(deviceModelIdentifier);
            return deviceModelIdentifier;
        }
        String nameBasedKey = deviceModelStandardizer.normalizeIdentifier(req.getName());
        return nextAvailableNameBasedKey(nameBasedKey);
    }

    private String nextAvailableNameBasedKey(String baseKey) {
        String normalizedBaseKey = StringUtils.hasText(baseKey)
                ? baseKey
                : "PRODUCT";
        if (!productKeyExists(normalizedBaseKey)) {
            return normalizedBaseKey;
        }
        for (int suffix = 2; suffix <= 999; suffix++) {
            String candidate = shrinkIdentifier(normalizedBaseKey, suffix);
            if (!productKeyExists(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.FAILED, "无法生成可用的产品标识，请手动指定 productKey");
    }

    private String shrinkIdentifier(String baseKey, int suffix) {
        String suffixValue = "_" + suffix;
        int maxBaseLength = Math.max(1, 64 - suffixValue.length());
        String trimmedBase = baseKey.length() > maxBaseLength ? baseKey.substring(0, maxBaseLength) : baseKey;
        return trimmedBase + suffixValue;
    }

    private void ensureProductKeyAvailable(String productKey) {
        if (productKeyExists(productKey)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "productKey 已存在");
        }
    }

    private boolean productKeyExists(String productKey) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        return productRepository.selectOne(wrapper) != null;
    }
}
