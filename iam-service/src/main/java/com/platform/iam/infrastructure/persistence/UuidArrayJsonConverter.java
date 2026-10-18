package com.platform.iam.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * TiDB 切换配套: PG uuid[] 数组列改为 json 列后的 UUID 数组转换器。
 */
@Converter
public class UuidArrayJsonConverter implements AttributeConverter<UUID[], String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(UUID[] attribute) {
        if (attribute == null) {
            return "[]";
        }
        try {
            String[] ids = new String[attribute.length];
            for (int i = 0; i < attribute.length; i++) {
                ids[i] = attribute[i] == null ? null : attribute[i].toString();
            }
            return MAPPER.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化 UUID 数组失败", e);
        }
    }

    @Override
    public UUID[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || "[]".equals(dbData.trim())) {
            return new UUID[0];
        }
        try {
            String[] ids = MAPPER.readValue(dbData, String[].class);
            UUID[] out = new UUID[ids.length];
            for (int i = 0; i < ids.length; i++) {
                out[i] = ids[i] == null ? null : UUID.fromString(ids[i]);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("反序列化 UUID 数组失败", e);
        }
    }
}
