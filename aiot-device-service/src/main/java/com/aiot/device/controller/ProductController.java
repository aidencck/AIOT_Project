package com.aiot.device.controller;

import com.aiot.device.dto.ProductReq;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 产品接口
 */
@Tag(name = "产品", description = "产品定义与物模型管理相关接口")
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    @Autowired
    private ProductService productService;

    @Operation(summary = "创建产品", description = "创建新的产品定义")
    @ApiResponse(responseCode = "201", description = "创建成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createProduct(@Valid @RequestBody ProductReq req) {
        return productService.createProduct(req);
    }

    @Operation(summary = "查询产品列表", description = "查询全部产品列表")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping
    public List<ProductResp> listProducts() {
        return productService.listProducts();
    }

    @Operation(summary = "获取产品详情", description = "根据产品 Key 查询产品详情")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "产品不存在")
    @GetMapping("/{productKey}")
    public ProductResp getProduct(@Parameter(description = "产品 Key") @PathVariable @NotBlank(message = "productKey 不能为空") String productKey) {
        return productService.getProductByKey(productKey);
    }

    @Operation(summary = "删除产品", description = "根据产品 Key 删除产品")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "产品不存在")
    @DeleteMapping("/{productKey}")
    public Void deleteProduct(@Parameter(description = "产品 Key") @PathVariable @NotBlank(message = "productKey 不能为空") String productKey) {
        productService.deleteProduct(productKey);
        return null;
    }

    @Operation(summary = "更新产品物模型", description = "根据产品 Key 更新物模型 JSON")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "产品不存在")
    @PutMapping("/{productKey}/thing-model")
    public Void updateThingModel(@Parameter(description = "产品 Key") @PathVariable @NotBlank(message = "productKey 不能为空") String productKey,
                                 @RequestBody @NotBlank(message = "thingModelJson 不能为空") String thingModelJson) {
        productService.updateThingModel(productKey, thingModelJson);
        return null;
    }
}
