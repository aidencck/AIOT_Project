package com.aiot.rule.client;

import com.aiot.common.api.Result;
import com.aiot.common.dto.device.DeviceStatusSummaryResp;
import com.aiot.common.http.CrossServiceHttpExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceStatusSummaryClientTest {

    private CrossServiceHttpExecutor executor;
    private RestClient.Builder builder;
    private RestClient restClient;
    private DeviceStatusSummaryClient client;

    @BeforeEach
    void setUp() {
        executor = mock(CrossServiceHttpExecutor.class);
        builder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        when(builder.clone()).thenReturn(builder);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        client = new DeviceStatusSummaryClient(executor, builder, "lb://aiot-device-service", "internal-token");
    }

    private static DeviceStatusSummaryResp summary(long online, long offline, long total) {
        DeviceStatusSummaryResp resp = new DeviceStatusSummaryResp();
        resp.setOnlineCount(online);
        resp.setOfflineCount(offline);
        resp.setTotalCount(total);
        return resp;
    }

    private static void assertEmptySummary(DeviceStatusSummaryResp actual) {
        assertThat(actual.getOnlineCount()).isZero();
        assertThat(actual.getOfflineCount()).isZero();
        assertThat(actual.getTotalCount()).isZero();
    }

    @Test
    void shouldReturnDataWhenResultHasData() {
        DeviceStatusSummaryResp data = summary(1L, 2L, 3L);
        Result<DeviceStatusSummaryResp> result = new Result<>();
        result.setData(data);
        when(executor.executeBlocking(eq("device-status-summary"), any(Supplier.class))).thenReturn(result);

        DeviceStatusSummaryResp actual = client.getStatusSummary();

        assertThat(actual).isSameAs(data);
        assertThat(actual.getOnlineCount()).isEqualTo(1L);
        assertThat(actual.getOfflineCount()).isEqualTo(2L);
        assertThat(actual.getTotalCount()).isEqualTo(3L);
    }

    @Test
    void shouldReturnEmptySummaryWhenResultIsNull() {
        when(executor.executeBlocking(eq("device-status-summary"), any(Supplier.class))).thenReturn(null);

        DeviceStatusSummaryResp actual = client.getStatusSummary();

        assertEmptySummary(actual);
    }

    @Test
    void shouldReturnEmptySummaryWhenDataIsNull() {
        Result<DeviceStatusSummaryResp> result = new Result<>();
        result.setData(null);
        when(executor.executeBlocking(eq("device-status-summary"), any(Supplier.class))).thenReturn(result);

        DeviceStatusSummaryResp actual = client.getStatusSummary();

        assertEmptySummary(actual);
    }

    @Test
    void shouldReturnEmptySummaryWhenExecutorThrows() {
        doThrow(new RuntimeException("boom"))
                .when(executor)
                .executeBlocking(eq("device-status-summary"), any(Supplier.class));

        DeviceStatusSummaryResp actual = client.getStatusSummary();

        assertEmptySummary(actual);
    }
}
