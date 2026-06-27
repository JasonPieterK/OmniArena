package com.omniarena.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.UUID;

public class LuckPermsIntegration {

    private final LuckPerms api;

    public LuckPermsIntegration() {
        this.api = LuckPermsProvider.get();
    }

    public String getPrimaryGroup(Player player) {
        User user = api.getUserManager().getUser(player.getUniqueId());
        return user != null ? user.getPrimaryGroup() : "default";
    }

    public boolean hasPermission(UUID uuid, String permission) {
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return false;
        return user.getCachedData().getPermissionData()
                .checkPermission(permission).asBoolean();
    }
}
