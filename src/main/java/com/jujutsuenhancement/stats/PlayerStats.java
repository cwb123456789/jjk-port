package com.jujutsuenhancement.stats;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The scoreboard stats of the Bedrock addon that belong to a player (strength, strength2, damageadd...), kept in the
 * player's persistent data so they survive death and restarts.
 *
 * <ul>
 *   <li>{@code strength}: points of strength (soft cap 50 for a normal body, see {@code relics/rough_crystal.mcfunction});</li>
 *   <li>{@code strength2}: the progress towards the next {@code damageadd}; every 5 points of it give +1 {@code damageadd};</li>
 *   <li>{@code damageadd}: the flat damage the addon adds to every hit and skill of the player; here it is an
 *       attack-damage attribute modifier.</li>
 * </ul>
 */
public final class PlayerStats {
    private static final String KEY = "jujutsuenhancement_stats";
    private static final UUID DAMAGE_ADD_ID = UUID.fromString("5b1b6f5e-3a43-4c8a-8d0e-9c1d7a3e2f10");

    private PlayerStats() {}

    /** The stats compound of the player, created on first use. */
    public static CompoundTag tag(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY)) persisted.put(KEY, new CompoundTag());
        return persisted.getCompound(KEY);
    }

    public static int get(Player player, String stat) {
        return tag(player).getInt(stat);
    }

    public static void set(Player player, String stat, int value) {
        tag(player).putInt(stat, value);
    }

    public static void add(Player player, String stat, int amount) {
        set(player, stat, get(player, stat) + amount);
    }

    /** Copies the stats to a new player entity (death, end portal). */
    public static void copy(Player from, Player to) {
        CompoundTag source = tag(from);
        CompoundTag target = tag(to);
        for (String k : source.getAllKeys()) target.put(k, source.get(k).copy());
    }

    /** Makes the attack damage attribute carry the player's damageadd. */
    public static void applyDamageAdd(Player player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;
        int add = get(player, "damageadd");
        AttributeModifier current = attack.getModifier(DAMAGE_ADD_ID);
        if (current != null && current.getAmount() == add) return;
        if (current != null) attack.removeModifier(DAMAGE_ADD_ID);
        if (add != 0) {
            attack.addPermanentModifier(new AttributeModifier(DAMAGE_ADD_ID, "Jujutsu damageadd", add, AttributeModifier.Operation.ADDITION));
        }
    }
}
