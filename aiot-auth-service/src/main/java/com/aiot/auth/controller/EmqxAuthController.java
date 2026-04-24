package com.aiot.auth.controller;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;
import com.aiot.auth.service.AuthService;
import com.aiot.common.config.SkipResponseWrap;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * EMQX HTTP Auth & Webhook Endpoints
 * Return 200 OK for Success, 401 Unauthorized for Failure
 */
@RestController
@RequestMapping("/api/v1/emqx")
@SkipResponseWrap
public class EmqxAuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/auth")
    public ResponseEntity<String> authenticate(@Valid @RequestBody EmqxAuthReq req) {
        boolean isValid = authService.authenticateDevice(req);
        if (isValid) {
            return ResponseEntity.ok("allow");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("deny");
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@Valid @RequestBody EmqxWebhookReq req,
                                          @RequestHeader(value = "x-emqx-signature", required = false) String signatureHeader) {
        boolean verified = authService.verifyWebhookSignature(
                req.getAction(), req.getClientid(), req.getUsername(), req.getTimestamp(), signatureHeader
        );
        if (!verified) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        authService.handleDeviceStatusWebhook(req);
        return ResponseEntity.ok("success");
    }
}
