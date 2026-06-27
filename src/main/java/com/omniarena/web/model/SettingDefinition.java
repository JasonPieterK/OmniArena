package com.omniarena.web.model;

public record SettingDefinition(
        String key,
        String label,
        String group,
        SettingType type,
        String description,
        String defaultValue,
        boolean liveApply
) {
    public enum SettingType {
        BOOLEAN,
        INTEGER,
        DECIMAL,
        STRING,
        SELECT,
        COLOR,
        LIST
    }
}
