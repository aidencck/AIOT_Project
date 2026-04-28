package com.aiot.device.controller;

import com.aiot.device.dto.ProductReq;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产品接口
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping
    public String createProduct(@Valid @RequestBody ProductReq req) {
        return productService.createProduct(req);
    }

    @GetMapping
    public List<ProductResp> listProducts() {
        return productService.listProducts();
    }

    @GetMapping("/{productKey}")
    public ProductResp getProduct(@PathVariable @NotBlank(message = "productKey 不能为空") String productKey) {
        return productService.getProductByKey(productKey);
    }

    @PutMapping("/{productKey}/thing-model")
    public Void updateThingModel(@PathVariable @NotBlank(message = "productKey 不能为空") String productKey,
                                 @RequestBody @NotBlank(message = "thingModelJson 不能为空") String thingModelJson) {
        productService.updateThingModel(productKey, thingModelJson);
        return null;
    }
}
