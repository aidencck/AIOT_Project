package com.aiot.device.support;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class DeviceModelStandardizer {

    private static final String SCHEMA_VALUE = "aiot.device-model/v1";
    private static final int MAX_IDENTIFIER_LENGTH = 64;

    private final ObjectMapper objectMapper;

    public DeviceModelStandardizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String standardize(String rawDeviceModelJson) {
        return standardize(rawDeviceModelJson, null, null);
    }

    public String standardize(String rawDeviceModelJson, String productKey, String productName) {
        ObjectNode root = parseToObject(rawDeviceModelJson);
        if (!StringUtils.hasText(root.path("schema").asText())) {
            root.put("schema", SCHEMA_VALUE);
        }
        ensureArray(root, "properties");
        ensureArray(root, "events");
        ensureArray(root, "services");
        if (!root.has("metadata") || !root.get("metadata").isObject()) {
            root.set("metadata", objectMapper.createObjectNode());
        }
        ObjectNode metadata = (ObjectNode) root.get("metadata");
        if (StringUtils.hasText(productKey)) {
            metadata.put("identifier", productKey);
            metadata.put("productKey", productKey);
            metadata.put("deviceModelKey", productKey);
        }
        if (StringUtils.hasText(productName) && !StringUtils.hasText(metadata.path("name").asText())) {
            metadata.put("name", productName);
        }
        return writeAsJson(root);
    }

    public String extractIdentifierCandidate(String rawDeviceModelJson) {
        ObjectNode root = parseToObject(rawDeviceModelJson);
        ObjectNode metadata = root.path("metadata").isObject() ? (ObjectNode) root.path("metadata") : objectMapper.createObjectNode();
        return firstNonBlank(
                normalizeIdentifier(root.path("productKey").asText(null)),
                normalizeIdentifier(root.path("deviceModelKey").asText(null)),
                normalizeIdentifier(root.path("identifier").asText(null)),
                normalizeIdentifier(root.path("productCode").asText(null)),
                normalizeIdentifier(root.path("modelKey").asText(null)),
                normalizeIdentifier(root.path("modelId").asText(null)),
                normalizeIdentifier(metadata.path("productKey").asText(null)),
                normalizeIdentifier(metadata.path("deviceModelKey").asText(null)),
                normalizeIdentifier(metadata.path("identifier").asText(null)),
                normalizeIdentifier(metadata.path("productCode").asText(null)),
                normalizeIdentifier(metadata.path("modelKey").asText(null)),
                normalizeIdentifier(metadata.path("modelId").asText(null))
        );
    }

    public String normalizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        String normalized = identifier.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "P_" + normalized;
        }
        return normalized.length() > MAX_IDENTIFIER_LENGTH
                ? normalized.substring(0, MAX_IDENTIFIER_LENGTH)
                : normalized;
    }

    private ObjectNode parseToObject(String rawDeviceModelJson) {
        if (!StringUtils.hasText(rawDeviceModelJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(rawDeviceModelJson);
            if (root != null && root.isTextual() && StringUtils.hasText(root.asText())) {
                root = objectMapper.readTree(root.asText());
            }
            if (root == null || root.isNull()) {
                return objectMapper.createObjectNode();
            }
            if (!root.isObject()) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "deviceModelJson 必须是 JSON 对象");
            }
            return (ObjectNode) root.deepCopy();
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "deviceModelJson 不是合法 JSON");
        }
    }

    private void ensureArray(ObjectNode root, String fieldName) {
        JsonNode existing = root.get(fieldName);
        if (existing == null || existing.isNull()) {
            root.set(fieldName, objectMapper.createArrayNode());
            return;
        }
        if (!existing.isArray()) {
            ArrayNode wrapped = objectMapper.createArrayNode();
            wrapped.add(existing);
            root.set(fieldName, wrapped);
        }
    }

    private String writeAsJson(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ResultCode.FAILED, "deviceModelJson 标准化失败");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
