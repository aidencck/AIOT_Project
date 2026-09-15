package com.aiot.device.service.impl;

import com.aiot.device.client.KnowledgeRebuildClient;
import com.aiot.device.dto.ProductReq;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.entity.Product;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.support.DeviceModelStandardizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    private ProductRepository productRepository;
    private KnowledgeRebuildClient knowledgeRebuildClient;
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        knowledgeRebuildClient = mock(KnowledgeRebuildClient.class);

        productService = new ProductServiceImpl();
        ReflectionTestUtils.setField(productService, "productRepository", productRepository);
        ReflectionTestUtils.setField(productService, "knowledgeRebuildClient", knowledgeRebuildClient);
        ReflectionTestUtils.setField(productService, "deviceModelStandardizer",
                new DeviceModelStandardizer(new ObjectMapper()));
    }

    @Test
    void createProduct_shouldStandardizeDeviceModelJson() {
        ProductReq req = new ProductReq();
        req.setName("ice-maker");
        req.setNodeType(1);
        req.setDeviceModelJson("{\"properties\":{\"power\":{\"type\":\"bool\"}}}");
        when(productRepository.selectOne(any())).thenReturn(null);

        String productKey = productService.createProduct(req);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).insert(captor.capture());
        assertEquals("ICE_MAKER", productKey);
        assertEquals("ICE_MAKER", captor.getValue().getProductKey());
        assertTrue(captor.getValue().getThingModelJson().contains("\"identifier\":\"ICE_MAKER\""));
        assertTrue(captor.getValue().getThingModelJson().contains("\"deviceModelKey\":\"ICE_MAKER\""));
        verify(knowledgeRebuildClient).rebuild(org.mockito.ArgumentMatchers.eq(productKey),
                org.mockito.ArgumentMatchers.argThat(model ->
                        model.contains("\"schema\":\"aiot.device-model/v1\"")
                                && model.contains("\"properties\":[")
                                && model.contains("\"events\":[]")
                                && model.contains("\"services\":[]")
                                && model.contains("\"identifier\":\"ICE_MAKER\"")),
                org.mockito.ArgumentMatchers.eq("product-create"));
    }

    @Test
    void getProductByKey_shouldExposeThingModelAndDeviceModelTogether() {
        Product product = new Product();
        product.setId("p-1");
        product.setProductKey("pk-1");
        product.setName("ice-maker");
        product.setNodeType(1);
        product.setThingModelJson("{\"properties\":[]}");
        when(productRepository.selectOne(any())).thenReturn(product);

        ProductResp resp = productService.getProductByKey("pk-1");

        assertEquals(resp.getThingModelJson(), resp.getDeviceModelJson());
        assertEquals("pk-1", resp.getDeviceModelKey());
        assertTrue(resp.getThingModelJson().contains("\"schema\":\"aiot.device-model/v1\""));
        assertTrue(resp.getThingModelJson().contains("\"identifier\":\"pk-1\""));
    }

    @Test
    void createProduct_shouldPreferExplicitProductKey() {
        ProductReq req = new ProductReq();
        req.setProductKey("ice-maker-pro");
        req.setName("ice-maker-pro");
        req.setNodeType(1);
        req.setThingModelJson("{\"metadata\":{\"identifier\":\"legacy-random\"}}");
        when(productRepository.selectOne(any())).thenReturn(null);

        String productKey = productService.createProduct(req);

        assertEquals("ICE_MAKER_PRO", productKey);
        verify(productRepository).insert(any(Product.class));
    }
}
