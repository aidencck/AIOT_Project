package com.aiot.device.controller;

import com.aiot.common.api.Result;
import com.aiot.device.dto.ProductReq;
import com.aiot.device.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @Autowired
    private DeviceService deviceService;

    @PostMapping
    public Result<String> createProduct(@RequestBody ProductReq req) {
        try {
            String productId = deviceService.createProduct(req);
            return Result.success(productId);
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }
}
