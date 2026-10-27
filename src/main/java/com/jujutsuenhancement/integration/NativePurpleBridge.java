package com.jujutsuenhancement.integration;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.HollowPurpleEntity;
import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Replaces JujutsuCraft's native Hollow Purple projectile (entity jujutsucraft:purple,
 * spawned by skill 215 for players and Gojo NPCs) with this mod's Hollow Purple.
 *
 * The native skill still handles input, charging, chant lines, cooldown and the
 * Red/Blue orbs. Only the final projectile is swapped, chosen from the native state:
 *   - native "explode" + "betrayal" (crouch after full charge, or Gojo below 40% health)
 *       -> unlimited Purple
 *   - native charge counter cnt6 >= 4 (held to full charge)  -> 200% Purple
 *   - otherwise                                              -> normal Purple
 * Native classes are never referenced at compile time; only registry ids and NBT keys.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NativePurpleBridge {
    private static final ResourceLocation NATIVE_PURPLE = new ResourceLocation("jujutsucraft", "purple");
    private static final String REPLACED_KEY = "jujutsuenhancement_purple_replaced";

    private NativePurpleBridge() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity purple = event.getEntity();
        if (purple.level().isClientSide || !(purple.level() instanceof ServerLevel level)) return;
        if (!NATIVE_PURPLE.equals(ForgeRegistries.ENTITY_TYPES.getKey(purple.getType()))) return;
        CompoundTag data = purple.getPersistentData();
        if (data.getBoolean(REPLACED_KEY)) return;
        // The native skill configures owner/charge data right after spawning; wait one tick.
        if (purple.tickCount < 1) return;
        data.putBoolean(REPLACED_KEY, true);

        Entity owner = findOwner(level, purple, data);
        boolean unlimited = data.getBoolean("explode") && data.getBoolean("betrayal");
        boolean charged = data.getDouble("cnt6") >= 4.0D;

        HollowPurpleEntity replacement = ModEntities.HOLLOW_PURPLE.get().create(level);
        if (replacement == null) return;
        Vec3 look = purple.getLookAngle().normalize();
        replacement.setPos(purple.getX(), purple.getY(), purple.getZ());
        if (unlimited) {
            replacement.setUnlimited();
            replacement.launch(owner, look);
        } else {
            if (charged) replacement.setCharged200Percent();
            replacement.launch(owner, look);
        }
        if (level.addFreshEntity(replacement)) {
            purple.discard();
        }
    }

    /** Owner UUID is stored as a string; the unlimited variant clears it, so fall back to the ranged-name link. */
    private static Entity findOwner(ServerLevel level, LivingEntity purple, CompoundTag data) {
        String raw = data.getString("OWNER_UUID");
        if (!raw.isEmpty()) {
            try {
                Entity e = level.getEntity(UUID.fromString(raw));
                if (e != null) return e;
            } catch (IllegalArgumentException ignored) {
                // fall through to the ranged-name lookup
            }
        }
        double link = data.getDouble("NameRanged_ranged");
        if (link == 0.0D) return null;
        AABB area = purple.getBoundingBox().inflate(12.0D);
        for (Entity candidate : level.getEntities(purple, area, Entity::isAlive)) {
            if (candidate.getPersistentData().getDouble("NameRanged") == link) return candidate;
        }
        return null;
    }
}
