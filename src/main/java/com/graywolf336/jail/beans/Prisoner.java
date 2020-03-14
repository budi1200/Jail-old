package com.graywolf336.jail.beans;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.graywolf336.jail.enums.Lang;

/**
 * Represents a Prisoner, player who is jailed, and contains all the information about him/her.
 *
 * @author graywolf336
 * @since 2.x.x
 * @version 3.1.1
 */
public class Prisoner {
    private String uuid, name, jailer, reason, inventory, armor;
    private boolean muted = true, offlinePending = false, teleporting = false, toBeTransferred = false, changed = false;
    private long time = -1L, afk = 0L;
    private Location previousPosition;
    private GameMode previousGameMode;

    /**
     * Creates the prisoner instance with the lot of data provided.
     *
     * @param uuid The uuid of the prisoner
     * @param name The name of the prisoner
     * @param muted Whether the prisoner is muted or not
     * @param time The amount of remaining time the prisoner has
     * @param jailer The name of the person who jailed this prisoner
     * @param reason The reason why this prisoner is in jail
     */
    public Prisoner(String uuid, String name, boolean muted, long time, String jailer, String reason) {
        this.uuid = uuid;
        this.name = name;
        this.muted = muted;
        this.time = time;
        this.jailer = jailer;
        this.reason = reason;
        finishSetup();
    }

    /**
     * Creates the prisoner instance with the lot of data provided.
     *
     * @param uuid The uuid of the prisoner
     * @param name The name of the prisoner
     * @param time The amount of remaining time the prisoner has
     * @param jailer The name of the person who jailed this prisoner
     * @param reason The reason why this prisoner is in jail
     */
    public Prisoner(String uuid, String name, long time, String jailer, String reason) {
        this.uuid = uuid;
        this.name = name;
        this.time = time;
        this.jailer = jailer;
        this.reason = reason;
        finishSetup();
    }

    /**
     * Creates the prisoner instance with the lot of data provided.
     *
     * @param uuid The uuid of the prisoner
     * @param name The name of the prisoner
     * @param time The amount of remaining time the prisoner has
     * @param reason The reason why this prisoner is in jail
     */
    public Prisoner(String uuid, String name, long time, String reason) {
        this.uuid = uuid;
        this.name = name;
        this.time = time;
        this.reason = reason;
        finishSetup();
    }

    /**
     * Creates the prisoner instance with the lot of data provided.
     *
     * @param uuid The uuid of the prisoner
     * @param name The name of the prisoner
     * @param time The amount of remaining time the prisoner has
     */
    public Prisoner(String uuid, String name, long time) {
        this.uuid = uuid;
        this.name = name;
        this.time = time;
        finishSetup();
    }

    /**
     * Creates the prisoner instance with the data provided.
     *
     * @param player The instance of the player who is to be jailed
     * @param muted Whether the prisoner is muted or not
     * @param time The amount of remaining time the prisoner has
     * @param jailer The jailer who jailed the prisoner
     * @param reason The reason why this prisoner is in jail
     */
    public Prisoner(Player player, boolean muted, long time, String jailer, String reason) {
        this.uuid = player.getUniqueId().toString();
        this.name = player.getName();
        this.muted = muted;
        this.time = time;
        this.jailer = jailer;
        this.reason = reason;
        finishSetup();
    }

    /**
     * Creates the prisoner instance with the data provided.
     *
     * @param player The instance of the player who is to be jailed
     * @param time The amount of remaining time the prisoner has
     * @param jailer The jailer who jailed the prisoner
     * @param reason The reason why this prisoner is in jail
     */
    public Prisoner(Player player, long time, String jailer, String reason) {
        this.uuid = player.getUniqueId().toString();
        this.name = player.getName();
        this.time = time;
        this.jailer = jailer;
        this.reason = reason;
        finishSetup();
    }

    /**
     * The most basic prisoner instance creation via providing the player, time, and reason.
     *
     * @param player The instance of the player who is to be jailed
     * @param time The amount of remaining time the prisoner has
     * @param reason The reason why this prisoner is in jail
     */
    public Prisoner(Player player, long time, String reason) {
        this.uuid = player.getUniqueId().toString();
        this.name = player.getName();
        this.time = time;
        this.reason = reason;
        finishSetup();
    }

    /**
     * The most basic prisoner instance creation via providing the player and time.
     *
     * @param player The instance of the player who is to be jailed
     * @param time The amount of remaining time the prisoner has
     */
    public Prisoner(Player player, long time) {
        this.uuid = player.getUniqueId().toString();
        this.name = player.getName();
        this.time = time;
        finishSetup();
    }

    /** Finishes the setup of the prisoner data, set to defaults. */
    private void finishSetup() {
        if(jailer == null)
            jailer = Lang.DEFAULTJAILER.get();
        if(reason == null)
            Lang.DEFAULTJAILEDREASON.get();
        if(inventory == null)
            inventory = "";
        if(armor == null)
            armor = "";
        if(previousGameMode == null)
            previousGameMode = GameMode.SURVIVAL;
        previousPosition = null;
    }

    /** Returns the UUID of the prisoner. */
    public UUID getUUID() {
        return UUID.fromString(this.uuid);
    }

    /** Gets the name of this prisoner. */
    public String getLastKnownName() {
        return this.name;
    }

    /** Sets the name of this prisoner. */
    public String setLastKnownName(String username) {
        this.name = username;
        this.changed = true;
        return this.name;
    }

    /** Gets the reason this player was jailed for. */
    public String getReason() {
        return this.reason;
    }

    /**
     * Sets the reason this player was jailed for.
     *
     * @param reason the player was jailed.
     * @return the reason the player was jailed, what we have stored about them.
     */
    public String setReason(String reason) {
        this.reason = reason;
        this.changed = true;
        return this.reason;
    }

    /** Gets the person who jailed this prisoner. */
    public String getJailer() {
        return this.jailer;
    }

    /** Sets the person who jailed this prisoner. */
    public void setJailer(String jailer) {
        this.jailer = jailer;
        this.changed = true;
    }

    /** Gets whether the prisoner is muted or not. */
    public boolean isMuted() {
        return this.muted;
    }

    /** Sets whether the prisoner is muted or not. */
    public void setMuted(boolean muted) {
        this.muted = muted;
        this.changed = true;
    }
    
    /** Gets whether the prisoner is jailed forever or not. */
    public boolean isJailedForever() {
        return this.time == -1;
    }

    /** Gets the remaining time the prisoner has. */
    public long getRemainingTime() {
        return this.time;
    }

    /** Gets the remaining time the prisoner has in minutes. */
    public long getRemainingTimeInMinutes() {
        return TimeUnit.MINUTES.convert(time, TimeUnit.MILLISECONDS);
    }

    /** Gets the remaining time the prison has in minutes except only in int format. */
    public int getRemainingTimeInMinutesInt() {
        return (int) this.getRemainingTimeInMinutes();
    }

    /**
     * Sets the remaining time the prisoner has left.
     *
     * @param time The amount of time left, in milliseconds.
     */
    public void setRemainingTime(long time) {
        this.time = time;
        this.changed = true;
    }

    /**
     * Adds the given time to the remaining time the prisoner has left, unless they're jailed forever.
     *
     * @param time to add to the prisoner's remaining time.
     * @return the new remaining time the prisoner has
     */
    public long addTime(long time) {
        if(this.time != -1L) {
            this.time += time;
            this.changed = true;
        }

        return this.time;
    }

    /**
     * Subtracts the given time from the remaining time the prisoner has left, unless they're jailed forever.
     *
     * @param time to subtract from the prisoner's remaining time.
     * @return the new remaining time the prisoner has
     */
    public long subtractTime(long time) {
        if(this.time != -1L && this.time - time > -1L) {
            this.time -= time;
            this.changed = true;
        }

        return this.time;
    }

    /** Gets whether the player is offline or not. */
    public boolean isOfflinePending() {
        return this.offlinePending;
    }

    /** Sets whether the player is offline or not. */
    public void setOfflinePending(boolean offline) {
        this.offlinePending = offline;
        this.changed = true;
    }

    /** Gets whether the player is being teleported or not. */
    public boolean isTeleporting() {
        return this.teleporting;
    }

    /** Sets whether the player is being teleported or not. */
    public void setTeleporting(boolean teleport) {
        this.teleporting = teleport;
    }

    /** Gets whether the prisoner is going to be transferred or not, mainly for teleporting on join purposes. */
    public boolean isToBeTransferred() {
        return this.toBeTransferred;
    }

    /** Sets whether the prisoner is going to be transferred or not, mainly for teleporting on join purposes. */
    public void setToBeTransferred(boolean transferred) {
        this.toBeTransferred = transferred;
        this.changed = true;
    }

    /** Gets the previous location of this player, can be null. */
    public Location getPreviousLocation() {
        return this.previousPosition;
    }

    /** Gets the previous location of this player, separated by a comma. */
    public String getPreviousLocationString() {
        if(previousPosition == null) return "";
        else if(previousPosition.getWorld() == null) return "";
        else return previousPosition.getWorld().getName() + "," +
        previousPosition.getX() + "," +
        previousPosition.getY() + "," +
        previousPosition.getZ() + "," +
        previousPosition.getYaw() + "," +
        previousPosition.getPitch();
    }

    /** Sets the previous location of this player. */
    public void setPreviousPosition(Location location) {
        this.previousPosition = location;
    }

    /** Sets the previous location of this player from a comma separated string. */
    public void setPreviousPosition(String location) {
        if(location == null) return;
        if(location.isEmpty()) return;

        this.changed = true;
        String[] s = location.split(",");
        this.previousPosition = new Location(Bukkit.getWorld(s[0]),
                Double.valueOf(s[1]),
                Double.valueOf(s[2]),
                Double.valueOf(s[3]),
                Float.valueOf(s[4]),
                Float.valueOf(s[5]));
    }

    /** Gets the previous gamemode of this player. */
    public GameMode getPreviousGameMode() {
        return this.previousGameMode;
    }

    /** Sets the previous gamemode of this player. */
    public void setPreviousGameMode(GameMode previous) {
        this.previousGameMode = previous;
        this.changed = true;
    }

    /** Sets the previous gamemode of this player based upon the provided string. */
    public void setPreviousGameMode(String previous) {
        if(previous == null) return;
        else if(previous.isEmpty()) return;
        else this.previousGameMode = GameMode.valueOf(previous);
        this.changed = true;
    }

    /** Gets the inventory string for this player, it is encoded in Base64 string. */
    public String getInventory() {
        return this.inventory;
    }

    /** Sets the inventory Base64 string. */
    public void setInventory(String inventory) {
        this.inventory = inventory;
        this.changed = true;
    }

    /** Gets the armor content, encoded in Base64 string. */
    public String getArmor() {
        return this.armor;
    }

    /** Sets the armor inventory Base64 string. */
    public void setArmor(String armor) {
        this.armor = armor;
        this.changed = true;
    }

    /** Gets the time, in milliseconds, this prisoner has been afk. */
    public long getAFKTime() {
        return this.afk;
    }

    /** Sets the time, in milliseconds, this prisoner has been afk. */
    public void setAFKTime(long time) {
        this.afk = time;
    }

    /** Checks if the prisoner was changed or not. */
    public boolean wasChanged() {
        return this.changed;
    }

    /** Sets whether the prisoner was changed or not. */
    public boolean setChanged(boolean change) {
        this.changed = change;
        return this.changed;
    }
}
