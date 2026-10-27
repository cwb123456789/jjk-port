package com.jujutsuenhancement.integration;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * First native-domain clash bridge. It detects simultaneous native domain skills and
 * presents one cue per active overlap episode without replacing or mutating JujutsuCraft's skill state.
 * Supports native Mahito, Sukuna, Gojo, Yuji and Higuruma domain-skill IDs verified against v50.1.
 * Native skill suppression/winner resolution is intentionally not attempted until the base
 * mod's domain lifecycle has been verified in-game.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NativeDomainClashBridge {
    private static final double CLASH_RADIUS = 32.0D;
    private static final long PAIR_RELEASE_GRACE_TICKS = 10L;
    private static final long PAIR_STALE_TICKS = 200L;
    private static final long CLASH_SEQUENCE_TICKS = 40L;
    private static final long CLASH_PULSE_INTERVAL_TICKS = 5L;
    private static final String MAHITO = "net.mcreator.jujutsucraft.entity.MahitoEntity";
    private static final String SUKUNA = "net.mcreator.jujutsucraft.entity.SukunaEntity";
    private static final String SUKUNA_FUSHIGURO = "net.mcreator.jujutsucraft.entity.SukunaFushiguroEntity";
    private static final String SUKUNA_PERFECT = "net.mcreator.jujutsucraft.entity.SukunaPerfectEntity";
    private static final String GOJO = "net.mcreator.jujutsucraft.entity.GojoSatoruEntity";
    private static final String ITADORI = "net.mcreator.jujutsucraft.entity.ItadoriYujiEntity";
    private static final String ITADORI_SHIBUYA = "net.mcreator.jujutsucraft.entity.ItadoriYujiShibuyaEntity";
    private static final String ITADORI_SHINJUKU = "net.mcreator.jujutsucraft.entity.ItadoriYujiShinjukuEntity";
    private static final String HIGURUMA = "net.mcreator.jujutsucraft.entity.HigurumaHiromiEntity";
    private static final Map<String, PairState> ACTIVE_PAIRS = new HashMap<>();
    private static long lastCleanupGameTime = Long.MIN_VALUE;

    private NativeDomainClashBridge() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel level)) return;
        int skill = activeDomainSkill(entity);
        if (skill == 0 || entity.tickCount % 5 != 0) return;

        long now = level.getGameTime();
        cleanupExpired(now);
        AABB area = entity.getBoundingBox().inflate(CLASH_RADIUS);
        for (LivingEntity opponent : level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != entity && candidate.isAlive()
                        && activeDomainSkill(candidate) != 0
                        && entity.distanceToSqr(candidate) <= CLASH_RADIUS * CLASH_RADIUS)) {
            // Process each pair once, independent of which entity's tick runs first.
            if (compareUuid(entity.getUUID(), opponent.getUUID()) >= 0) continue;
            String key = pairKey(entity.getUUID(), opponent.getUUID());
            PairState state = ACTIVE_PAIRS.get(key);
            // A new overlap starts a short clash sequence. Persistent overlaps pulse briefly,
            // rather than replaying the full activation sound every few ticks.
            if (state == null || now - state.lastSeen > PAIR_RELEASE_GRACE_TICKS) {
                state = new PairState(now);
                ACTIVE_PAIRS.put(key, state);
                playClashCue(level, entity, opponent);
            } else if (now - state.startedAt <= CLASH_SEQUENCE_TICKS
                    && now - state.lastPulse >= CLASH_PULSE_INTERVAL_TICKS) {
                playClashPulse(level, entity, opponent);
                state.lastPulse = now;
            }
            state.lastSeen = now;
        }
    }

    /** Returns a domain skill ID only for native entity classes and IDs verified in JujutsuCraft v50.1. */
    private static int activeDomainSkill(LivingEntity entity) {
        String className = entity.getClass().getName();
        double rawSkill = entity.getPersistentData().getDouble("skill");
        if (className.equals(MAHITO) && rawSkill == 1520.0D) return 1520;
        if ((className.equals(SUKUNA) || className.equals(SUKUNA_FUSHIGURO)
                || className.equals(SUKUNA_PERFECT)) && rawSkill == 120.0D) return 120;
        if (className.equals(GOJO) && rawSkill == 220.0D) return 220;
        // Verified from v50.1 dispatchers: Yuji's domain is 2100 + 20;
        // Higuruma's Deadly Sentencing domain procedure is 2700 + 20.
        if ((className.equals(ITADORI) || className.equals(ITADORI_SHIBUYA)
                || className.equals(ITADORI_SHINJUKU)) && rawSkill == 2120.0D) return 2120;
        if (className.equals(HIGURUMA) && rawSkill == 2720.0D) return 2720;
        return 0;
    }

    private static void playClashCue(ServerLevel level, LivingEntity first, LivingEntity second) {
        Vec3 midpoint = first.position().add(second.position()).scale(0.5D).add(0.0D, 1.0D, 0.0D);
        level.sendParticles(ParticleTypes.PORTAL, midpoint.x, midpoint.y, midpoint.z,
                72, 1.8D, 1.4D, 1.8D, 0.22D);
        level.sendParticles(ParticleTypes.END_ROD, midpoint.x, midpoint.y, midpoint.z,
                24, 1.0D, 0.8D, 1.0D, 0.08D);
        level.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.85F, 0.55F);
    }

    private static void playClashPulse(ServerLevel level, LivingEntity first, LivingEntity second) {
        Vec3 midpoint = first.position().add(second.position()).scale(0.5D).add(0.0D, 1.0D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, midpoint.x, midpoint.y, midpoint.z,
                18, 0.8D, 0.65D, 0.8D, 0.12D);
        level.sendParticles(ParticleTypes.PORTAL, midpoint.x, midpoint.y, midpoint.z,
                12, 0.65D, 0.5D, 0.65D, 0.06D);
    }

    private static void cleanupExpired(long now) {
        if (lastCleanupGameTime != Long.MIN_VALUE && now - lastCleanupGameTime < 200L) return;
        lastCleanupGameTime = now;
        Iterator<Map.Entry<String, PairState>> iterator = ACTIVE_PAIRS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PairState> entry = iterator.next();
            if (now - entry.getValue().lastSeen > PAIR_STALE_TICKS) iterator.remove();
        }
    }

    private static final class PairState {
        private final long startedAt;
        private long lastSeen;
        private long lastPulse;

        private PairState(long now) {
            this.startedAt = now;
            this.lastSeen = now;
            this.lastPulse = now;
        }
    }

    private static int compareUuid(UUID first, UUID second) {
        return Comparator.comparing(UUID::toString).compare(first, second);
    }

    private static String pairKey(UUID first, UUID second) {
        return first.toString() + ":" + second.toString();
    }
}
