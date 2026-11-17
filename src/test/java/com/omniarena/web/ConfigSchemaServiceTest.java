package com.omniarena.web;

import com.omniarena.web.model.SettingDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSchemaServiceTest {

    @Test
    void schemaListsOnlyRealUniqueKeys() {
        ConfigSchemaService service = new ConfigSchemaService(null);
        Set<String> seen = new HashSet<>();
        for (SettingDefinition def : service.settings()) {
            assertTrue(seen.add(def.key()), "duplicate key " + def.key());
            assertFalse(def.key().startsWith("extras."), "padding key " + def.key());
            assertFalse(def.key().startsWith("web.remote."), "fake key " + def.key());
            assertFalse(def.key().startsWith("qol."), "fake key " + def.key());
        }
        assertTrue(seen.contains("plugin.debug"));
    }
}
