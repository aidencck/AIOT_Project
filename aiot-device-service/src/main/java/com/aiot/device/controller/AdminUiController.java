package com.aiot.device.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminUiController {

    @GetMapping("/admin")
    public String adminHome() {
        return "redirect:/admin/index.html";
    }
}
