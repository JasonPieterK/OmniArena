package com.omniarena.integration;

/** TEMPORARY (self-healing branch): replaced by the integration rework. */
public record IntegrationStatus(String name, boolean present, boolean enabled, String lastError) {}
