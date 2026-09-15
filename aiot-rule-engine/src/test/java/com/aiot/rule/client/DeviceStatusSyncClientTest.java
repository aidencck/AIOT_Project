package com.aiot.rule.client;

import com.aiot.common.http.CrossServiceHttpExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceStatusSyncClientTest {

    private CrossServiceHttpExecutor executor;
    private RestClient.Builder builder;
    private RestClient restClient;
    private DeviceStatusSyncClient client;

    @BeforeEach
    void setUp() {
        executor = mock(CrossServiceHttpExecutor.class);
        builder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        when(builder.clone()).thenReturn(builder);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        client = new DeviceStatusSyncClient(executor, builder, "lb://aiot-device-service", "internal-token");
    }

    @Test
    void shouldNotThrowWhenExecutorReturnsNull() {
        when(executor.executeBlocking(eq("device-status-sync"), any(Supplier.class))).thenReturn(null);

        assertThatCode(() -> client.syncStatus("device-1", 1)).doesNotThrowAnyException();

        verify(executor).executeBlocking(eq("device-status-sync"), any(Supplier.class));
    }

    @Test
    void shouldSwallowExceptionThrownByExecutor() {
        doThrow(new RuntimeException("boom"))
                .when(executor)
                .executeBlocking(eq("device-status-sync"), any(Supplier.class));

        assertThatCode(() -> client.syncStatus("device-1", 1)).doesNotThrowAnyException();

        verify(executor).executeBlocking(eq("device-status-sync"), any(Supplier.class));
    }
}
