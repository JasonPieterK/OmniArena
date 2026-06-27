package com.omniarena.kit;

import java.util.Arrays;
import java.util.Objects;

public class AdminKit extends Kit {
    private boolean isPublic;

    public AdminKit(String name) {
        this.name = name;
        this.displayName = name;
        this.isPublic = false;
        this.cooldownSeconds = 0;
        this.requiredPermission = "";
        this.arenaRestriction = "";
    }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminKit)) return false;
        AdminKit that = (AdminKit) o;
        return isPublic == that.isPublic
                && cooldownSeconds == that.cooldownSeconds
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
        int result = Objects.hash(name, displayName, description, icon, offHand, effects,
                cooldownSeconds, requiredPermission, arenaRestriction, isPublic);
        result = 31 * result + Arrays.hashCode(contents);
        result = 31 * result + Arrays.hashCode(armor);
        return result;
    }

    @Override
    public String toString() {
        return "AdminKit(name=" + name + ", isPublic=" + isPublic + ")";
    }
}
