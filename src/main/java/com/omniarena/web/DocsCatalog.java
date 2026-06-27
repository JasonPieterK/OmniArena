package com.omniarena.web;

import com.omniarena.OmniArena;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DocsCatalog {

    private final OmniArena plugin;

    public DocsCatalog(OmniArena plugin) {
        this.plugin = plugin;
    }

    public String loadMarkdown() {
        try (InputStream in = plugin.getResource("web/docs.md")) {
            if (in == null) return "# OmniArena Docs\n\nDocs missing.";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "# OmniArena Docs\n\nFailed to load docs.";
        }
    }
}
