package com.omniarena.web.model;

import java.util.Map;
import java.util.List;

public record ConfigSnapshot(
        long generatedAt,
        List<SettingDefinition> settings,
        Map<String, Object> globals,
        List<ArenaView> arenas,
        String docsTitle,
        List<String> docsTopics
) {}
