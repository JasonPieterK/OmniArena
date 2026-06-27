package com.omniarena.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSchemaServiceTest {

    @Test
    void schemaHas250Settings() {
        ConfigSchemaService service = new ConfigSchemaService(null);
        assertEquals(250, service.settings().size());
    }
}
