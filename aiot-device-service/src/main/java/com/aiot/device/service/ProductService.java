package com.aiot.device.service;

import com.aiot.device.dto.ProductReq;
import com.aiot.device.dto.ProductResp;

import java.util.List;

public interface ProductService {
    String createProduct(ProductReq req);
    ProductResp getProductByKey(String productKey);
    List<ProductResp> listProducts();
    void updateThingModel(String productKey, String thingModelJson);
}
