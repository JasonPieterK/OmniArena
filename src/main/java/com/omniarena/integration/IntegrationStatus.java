package com.omniarena.integration;

/**
 * Snapshot of one optional-plugin hook, for diagnostics (/oa doctor, logs).
 *
 * @param name      hook key as used under {@code integrations.<name>} in config.yml (e.g. "vault", "citizens")
 * @param present   the dependency plugin is installed and enabled right now
 * @param enabled   the hook is live: switched on in config, dependency present and not auto-disabled after failures
 * @param lastError the most recent error this hook produced, or {@code null} when it never failed
 */
public record IntegrationStatus(String name, boolean present, boolean enabled, String lastError) {
}
