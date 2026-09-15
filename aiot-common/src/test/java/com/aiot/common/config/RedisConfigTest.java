package com.aiot.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class RedisConfigTest {

    private final RedisTemplate<String, Object> template =
            new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));

    @Test
    void shouldRoundTripStringValue() {
        RedisSerializer serializer = template.getValueSerializer();

        byte[] bytes = serializer.serialize("hello");
        Object result = serializer.deserialize(bytes);

        assertInstanceOf(String.class, result);
        assertEquals("hello", result);
    }

    @Test
    void shouldRoundTripIntegerValue() {
        RedisSerializer serializer = template.getValueSerializer();

        byte[] bytes = serializer.serialize(42);
        Object result = serializer.deserialize(bytes);

        assertInstanceOf(Integer.class, result);
        assertEquals(42, result);
    }

    @Test
    void shouldRoundTripDtoWithLocalDateTime() {
        RedisSerializer serializer = template.getValueSerializer();

        SampleDto dto = new SampleDto();
        dto.setId(1L);
        dto.setName("device");
        dto.setCreatedAt(LocalDateTime.of(2026, 8, 20, 12, 30, 45));

        byte[] bytes = serializer.serialize(dto);
        Object result = serializer.deserialize(bytes);

        assertInstanceOf(SampleDto.class, result);
        SampleDto restored = (SampleDto) result;
        assertEquals(1L, restored.getId());
        assertEquals("device", restored.getName());
        assertEquals(dto.getCreatedAt(), restored.getCreatedAt());
    }

    public static class SampleDto {

        private Long id;
        private String name;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
