package com.omniarena.kit;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class PlayerKit extends Kit {
    private final UUID ownerId;

    public PlayerKit(String name, UUID ownerId) {
        this.name = name;
        this.displayName = name;
        this.ownerId = ownerId;
        this.cooldownSeconds = 0;
        this.requiredPermission = "";
        this.arenaRestriction = "";
    }

    public UUID getOwnerId() { return ownerId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerKit)) return false;
        PlayerKit that = (PlayerKit) o;
        return cooldownSeconds == that.cooldownSeconds
                && Objects.equals(ownerId, that.ownerId)
                && Objects.equals(name, that.name)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(description, that.description)
                && Objects.equals(icon, that.icon)
                && Arrays.equals(contents, that.contents)
                && Arrays.equals(armor, that.armor)
                && Objects.equals(offHand, that.offHand)
                && Objects.equals(effects, that.effects)
                && Objects.equals(requiredPermission, that.requiredPermission)
                && Objects.equals(arenaRestriction, that.arenaRestriction);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(ownerId, name, displayName, description, icon, offHand, effects,
                cooldownSeconds, requiredPermission, arenaRestriction);
        result = 31 * result + Arrays.hashCode(contents);
        result = 31 * result + Arrays.hashCode(armor);
        return result;
    }

    @Override
    public String toString() {
        return "PlayerKit(name=" + name + ", ownerId=" + ownerId + ")";
    }
}
