package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.ProductReq;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.entity.Product;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.service.ProductService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Override
    public String createProduct(ProductReq req) {
        Product product = new Product();
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setNodeType(req.getNodeType());
        
        if (StringUtils.hasText(req.getThingModelJson())) {
            product.setThingModelJson(req.getThingModelJson());
        } else {
            product.setThingModelJson("{}");
        }

        // 自动生成 ProductKey (如 PK_xxxxx)
        String productKey = "PK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        product.setProductKey(productKey);

        productRepository.insert(product);
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
    public void updateThingModel(String productKey, String thingModelJson) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        Product product = productRepository.selectOne(wrapper);
        
        if (product == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "产品不存在");
        }
        
        product.setThingModelJson(thingModelJson);
        productRepository.updateById(product);
    }

    private ProductResp convertToResp(Product product) {
        ProductResp resp = new ProductResp();
        resp.setId(product.getId());
        resp.setProductKey(product.getProductKey());
        resp.setName(product.getName());
        resp.setDescription(product.getDescription());
        resp.setNodeType(product.getNodeType());
        resp.setThingModelJson(product.getThingModelJson());
        return resp;
    }
}
