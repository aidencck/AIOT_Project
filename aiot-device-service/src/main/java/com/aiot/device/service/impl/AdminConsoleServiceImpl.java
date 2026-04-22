package com.aiot.device.service.impl;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.AdminLatestClosureResp;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.service.AdminConsoleService;
import com.aiot.device.service.DeviceService;
import com.aiot.device.service.OtaService;
import com.aiot.device.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AdminConsoleServiceImpl implements AdminConsoleService {

    @Value("${aiot.home-service.base-url:http://127.0.0.1:8083}")
    private String homeServiceBaseUrl;

    @Value("${aiot.rule-service.base-url:http://127.0.0.1:8084}")
    private String ruleServiceBaseUrl;

    private final ProductService productService;
    private final DeviceService deviceService;
    private final OtaService otaService;

    public AdminConsoleServiceImpl(ProductService productService, DeviceService deviceService, OtaService otaService) {
        this.productService = productService;
        this.deviceService = deviceService;
        this.otaService = otaService;
    }

    @Override
    public AdminConsoleOverviewResp getOverview(String homeId, String authorizationHeader) {
        List<Map<String, Object>> homes = loadHomes(authorizationHeader);
        String selectedHomeId = pickHomeId(homeId, homes);
        List<Map<String, Object>> members = loadHomeMembers(selectedHomeId, authorizationHeader);
        List<ProductResp> products = productService.listProducts();
        List<DeviceResp> devices = deviceService.listDevicesByHomeId(selectedHomeId);
        List<OtaUpgradeTaskResp> otaTasks = otaService.listUpgradeTasks(selectedHomeId);
        Map<String, Object> ops = loadOpsOverview();

        return AdminConsoleOverviewResp.builder()
                .homeCount(homes.size())
                .memberCount(members.size())
                .productCount(products.size())
                .deviceCount(devices.size())
                .otaTaskCount(otaTasks.size())
                .todayAlarmCount(toInteger(ops.get("todayAlarmCount")))
                .pendingWorkOrderCount(toInteger(ops.get("pendingWorkOrderCount")))
                .oneTimeResolveRate(toDouble(ops.get("oneTimeResolveRate")))
                .build();
    }

    @Override
    public AdminLatestClosureResp getLatestClosure(String homeId, String authorizationHeader) {
        List<Map<String, Object>> homes = loadHomes(authorizationHeader);
        String selectedHomeId = pickHomeId(homeId, homes);
        List<Map<String, Object>> members = loadHomeMembers(selectedHomeId, authorizationHeader);
        List<ProductResp> products = productService.listProducts();
        List<DeviceResp> devices = deviceService.listDevicesByHomeId(selectedHomeId);
        List<OtaUpgradeTaskResp> otaTasks = otaService.listUpgradeTasks(selectedHomeId);
        Map<String, Object> ops = loadOpsOverview();
        return AdminLatestClosureResp.builder()
                .homes(homes)
                .members(members)
                .products(products)
                .devices(devices)
                .otaTasks(otaTasks)
                .opsOverview(ops)
                .build();
    }

    private List<Map<String, Object>> loadHomes(String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        Result<List<Map<String, Object>>> result = WebClient.builder()
                .baseUrl(homeServiceBaseUrl)
                .defaultHeader("Authorization", authorizationHeader)
                .build()
                .get()
                .uri("/api/v1/homes")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<List<Map<String, Object>>>>() {})
                .timeout(Duration.ofSeconds(2))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(150)))
                .block();
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            throw new BusinessException(ResultCode.FAILED, "拉取家庭列表失败");
        }
        return result.getData();
    }

    private List<Map<String, Object>> loadHomeMembers(String homeId, String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        Result<List<Map<String, Object>>> result = WebClient.builder()
                .baseUrl(homeServiceBaseUrl)
                .defaultHeader("Authorization", authorizationHeader)
                .build()
                .get()
                .uri("/api/v1/homes/{homeId}/members", homeId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<List<Map<String, Object>>>>() {})
                .timeout(Duration.ofSeconds(2))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(150)))
                .block();
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return Collections.emptyList();
        }
        return result.getData();
    }

    private Map<String, Object> loadOpsOverview() {
        Result<Map<String, Object>> result = WebClient.builder()
                .baseUrl(ruleServiceBaseUrl)
                .build()
                .get()
                .uri("/api/v1/admin/dashboard/overview")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<Map<String, Object>>>() {})
                .timeout(Duration.ofSeconds(2))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(150)))
                .block();
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return Collections.emptyMap();
        }
        return result.getData();
    }

    private String pickHomeId(String requestedHomeId, List<Map<String, Object>> homes) {
        if (StringUtils.hasText(requestedHomeId)) {
            return requestedHomeId;
        }
        if (homes == null || homes.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "当前用户未绑定任何家庭");
        }
        Object id = homes.get(0).get("id");
        if (id == null || !StringUtils.hasText(String.valueOf(id))) {
            throw new BusinessException(ResultCode.FAILED, "家庭数据异常，缺少 homeId");
        }
        return String.valueOf(id);
    }

    private void ensureAuthorizationHeader(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少有效的 Authorization Header");
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0D;
        }
    }
}
