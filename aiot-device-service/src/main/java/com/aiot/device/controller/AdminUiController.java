package com.aiot.device.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "管理后台页面", description = "管理后台静态页面入口")
@Controller
public class AdminUiController {

    @Operation(summary = "管理后台首页", description = "重定向到管理后台首页 index.html")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/admin")
    public String adminHome() {
        return "redirect:/admin/index.html";
    }
}
