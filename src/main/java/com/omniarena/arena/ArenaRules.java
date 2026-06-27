package com.omniarena.arena;

public class ArenaRules {
    private boolean fallDamage = true;
    private boolean fireDamage = true;
    private boolean drowning = true;
    private boolean suffocation = true;
    private boolean friendlyFire = false;
    private boolean blockBreak = false;
    private boolean blockPlace = false;
    private boolean itemDrop = false;
    private boolean hungerDrain = false;

    public boolean isFallDamage() { return fallDamage; }
    public void setFallDamage(boolean fallDamage) { this.fallDamage = fallDamage; }
    public boolean isFireDamage() { return fireDamage; }
    public void setFireDamage(boolean fireDamage) { this.fireDamage = fireDamage; }
    public boolean isDrowning() { return drowning; }
    public void setDrowning(boolean drowning) { this.drowning = drowning; }
    public boolean isSuffocation() { return suffocation; }
    public void setSuffocation(boolean suffocation) { this.suffocation = suffocation; }
    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }
    public boolean isBlockBreak() { return blockBreak; }
    public void setBlockBreak(boolean blockBreak) { this.blockBreak = blockBreak; }
    public boolean isBlockPlace() { return blockPlace; }
    public void setBlockPlace(boolean blockPlace) { this.blockPlace = blockPlace; }
    public boolean isItemDrop() { return itemDrop; }
    public void setItemDrop(boolean itemDrop) { this.itemDrop = itemDrop; }
    public boolean isHungerDrain() { return hungerDrain; }
    public void setHungerDrain(boolean hungerDrain) { this.hungerDrain = hungerDrain; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaRules)) return false;
        ArenaRules that = (ArenaRules) o;
        return fallDamage == that.fallDamage && fireDamage == that.fireDamage
                && drowning == that.drowning && suffocation == that.suffocation
                && friendlyFire == that.friendlyFire && blockBreak == that.blockBreak
                && blockPlace == that.blockPlace && itemDrop == that.itemDrop
                && hungerDrain == that.hungerDrain;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(fallDamage, fireDamage, drowning, suffocation,
                friendlyFire, blockBreak, blockPlace, itemDrop, hungerDrain);
    }

    @Override
    public String toString() {
        return "ArenaRules(fallDamage=" + fallDamage + ", fireDamage=" + fireDamage
                + ", drowning=" + drowning + ", suffocation=" + suffocation
                + ", friendlyFire=" + friendlyFire + ", blockBreak=" + blockBreak
                + ", blockPlace=" + blockPlace + ", itemDrop=" + itemDrop
                + ", hungerDrain=" + hungerDrain + ")";
    }
}
