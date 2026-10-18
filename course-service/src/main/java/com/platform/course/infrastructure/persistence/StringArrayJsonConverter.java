package com.platform.course.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * TiDB 切换配套: PG text[] 数组列改为 json 列后的字符串数组转换器。
 * 序列化: String[] -> JSON 数组文本; 反序列化: JSON 数组文本 -> String[]。
 */
@Converter
public class StringArrayJsonConverter implements AttributeConverter<String[], String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(String[] attribute) {
        if (attribute == null) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化 tags 失败", e);
        }
    }

    @Override
    public String[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new String[0];
        }
        try {
            return MAPPER.readValue(dbData, String[].class);
        } catch (Exception e) {
            throw new IllegalArgumentException("反序列化 tags 失败", e);
        }
    }
}
