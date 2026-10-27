package com.jujutsuenhancement.integration;

import com.jujutsuenhancement.entity.HollowPurpleEntity;
import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.jujutsuenhancement.JujutsuEnhancement;

/** Temporary test command until the Gojo skill input/state system is connected. */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PrototypeCommands {
    private static final String CHARGE_START_KEY = "jujutsuenhancement_purple_200_charge_start";
    private static final String CHARGE_READY_KEY = "jujutsuenhancement_purple_200_ready_at";
    private static final int CHARGE_TICKS = 100;
    private static final String NPC_CHARGE_START = "jujutsuenhancement_npc_purple_charge_start";
    private static final String NPC_PURPLE_USED = "jujutsuenhancement_npc_200_purple_used";
    private static final String NPC_TARGET_UUID = "jujutsuenhancement_npc_purple_target";
    private static final String NPC_LOW_HEALTH_PURPLE_READY_AT = "jujutsuenhancement_npc_low_health_purple_ready_at";
    private static final int NPC_LOW_HEALTH_PURPLE_COOLDOWN_TICKS = 40;
    private static final String NATIVE_SUKUNA_OPEN_TEST_READY_AT = "jujutsuenhancement_native_sukuna_open_test_ready_at";
    private static final String NATIVE_SUKUNA_OPEN_TEST_RUNNING = "jujutsuenhancement_native_sukuna_open_test_running";
    private static final String NATIVE_SUKUNA_OPEN_TEST_TICKS = "jujutsuenhancement_native_sukuna_open_test_ticks";
    private static final String NATIVE_SUKUNA_OPEN_TEST_CNT1 = "jujutsuenhancement_native_sukuna_open_test_cnt1";
    private static final long NATIVE_SUKUNA_OPEN_TEST_COOLDOWN_TICKS = 100L;
    private PrototypeCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jujutsuenhancement")
                .then(Commands.literal("hollow_purple")
                        .executes(ctx -> firePurple(ctx.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("hollow_purple_200")
                        .executes(ctx -> beginChargedPurple(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("mahito_domain_visual_test")
                        .executes(ctx -> spawnMahitoDomainVisuals(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("mahito_domain_hands_test")
                        .executes(ctx -> spawnMahitoDomainPart(ctx.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("mahito_domain_barrier_test")
                        .executes(ctx -> spawnMahitoDomainPart(ctx.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("mahito_prototype")
                        .executes(ctx -> spawnMahitoPrototype(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("mahito_native_status")
                        .executes(ctx -> inspectNativeMahito(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("sukuna_native_open_test")
                        .executes(ctx -> triggerNativeSukunaOpen(ctx.getSource().getPlayerOrException()))));
    }

    /** Spawn the interactive Mahito prototype so its 0.2-second form switch and domain sequence can be tested in-game. */
    private static int spawnMahitoPrototype(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 origin = player.position().add(look.scale(2.5D));
        var mahito = ModEntities.MAHITO_PROTOTYPE.get().create(level);
        if (mahito == null) {
            player.sendSystemMessage(Component.literal("真人原型创建失败；请检查实体注册日志。"));
            return 0;
        }
        mahito.moveTo(origin.x, origin.y, origin.z, player.getYRot(), 0.0F);
        if (!level.addFreshEntity(mahito)) {
            player.sendSystemMessage(Component.literal("真人原型未能加入世界；请检查 latest.log。"));
            return 0;
        }
        player.sendSystemMessage(Component.literal("已生成真人原型。潜行+右键触发领域/形态切换；普通形态在 0.2 秒后切换为遍杀即灵体原型。注意：这是独立测试实体，尚未替换 JujutsuCraft 原生真人 NPC。"));
        return 1;
    }

    /** Inspect the closest native JujutsuCraft Mahito without changing its state. */
    private static int inspectNativeMahito(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(32.0D);
        LivingEntity target = player.serverLevel().getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity.getClass().getName().equals("net.mcreator.jujutsucraft.entity.MahitoEntity"))
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (target == null) {
            player.sendSystemMessage(Component.literal("32 格内没有找到 JujutsuCraft 原生真人（MahitoEntity）。"));
            return 0;
        }
        String texture = "<unavailable>";
        String animation = "<unavailable>";
        try {
            texture = String.valueOf(target.getClass().getMethod("getTexture").invoke(target));
        } catch (ReflectiveOperationException ignored) {
            // Diagnostic command only: report unavailable rather than interrupt gameplay.
        }
        try {
            animation = String.valueOf(target.getClass().getMethod("getSyncedAnimation").invoke(target));
        } catch (ReflectiveOperationException ignored) {
            // Some JujutsuCraft builds may expose a different method name.
        }
        double skill = target.getPersistentData().getDouble("skill");
        player.sendSystemMessage(Component.literal("原生真人状态：skill=" + skill + ", texture=" + texture
                + ", syncedAnimation=" + animation + ", phase2Bridge="
                + target.getPersistentData().getBoolean("jujutsuenhancement_native_mahito_phase2_applied")));
        return 1;
    }

    /**
     * Test the actual JujutsuCraft OpenProcedure on a nearby native Sukuna NPC.
     * This deliberately calls the native procedure only on a real LivingEntity/Mob with a live target;
     * the custom HeianSukunaEntity is not compatible with the native procedure contract.
     */
    private static int triggerNativeSukunaOpen(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(32.0D);
        Mob target = player.serverLevel().getEntitiesOfClass(Mob.class, searchBox,
                entity -> isNativeSukuna(entity) && entity.getTarget() != null && entity.getTarget().isAlive())
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (target == null) {
            player.sendSystemMessage(Component.literal("32 格内未找到正在锁定存活目标的原生宿傩 NPC。请先让原生宿傩锁定一个目标，再执行此测试。"));
            return 0;
        }
        long now = player.serverLevel().getGameTime();
        long readyAt = target.getPersistentData().getLong(NATIVE_SUKUNA_OPEN_TEST_READY_AT);
        if (now < readyAt) {
            player.sendSystemMessage(Component.literal("该宿傩刚执行过「开」测试，还需等待 " + (readyAt - now)
                    + " Tick；冷却归属于该 NPC，不影响其他宿傩。"));
            return 0;
        }
        // OpenProcedure is a tick-driven state machine: cnt1 advances on every
        // invocation and the projectile window occurs later. Do not invoke it only
        // once here; schedule the native dispatcher for successive server ticks.
        target.getPersistentData().putDouble("skill", 107.0D);
        target.getPersistentData().putDouble("cnt1", 0.0D);
        target.getPersistentData().putBoolean(NATIVE_SUKUNA_OPEN_TEST_RUNNING, true);
        target.getPersistentData().putInt(NATIVE_SUKUNA_OPEN_TEST_TICKS, 0);
        target.getPersistentData().putDouble(NATIVE_SUKUNA_OPEN_TEST_CNT1, 0.0D);
        target.getPersistentData().putLong(NATIVE_SUKUNA_OPEN_TEST_READY_AT, now + NATIVE_SUKUNA_OPEN_TEST_COOLDOWN_TICKS);
        player.sendSystemMessage(Component.literal("已启动原生宿傩技能 107（开）的逐 Tick 测试（服务端 Tick 结束阶段驱动），执行者="
                + target.getClass().getSimpleName() + "，目标=" + target.getTarget().getName().getString()
                + "。技能由服务端连续驱动，最多 60 Tick 后结束；NPC AI 自动选技仍未接入。"));
        return 1;
    }

    /**
     * Drive the native dispatcher at server-level END, after SukunaEntity's own tick/AI procedure.
     * The test session snapshots cnt1 so the native AI cannot reset the test's animation timeline
     * before the dispatcher advances it. This remains an opt-in test command, not production AI.
     */
    @SubscribeEvent
    public static void tickNativeSukunaOpenTest(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !isNativeSukuna(living)
                    || !living.getPersistentData().getBoolean(NATIVE_SUKUNA_OPEN_TEST_RUNNING)) {
                continue;
            }
            if (living instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
                living.getPersistentData().putDouble("skill", 0.0D);
                living.getPersistentData().putBoolean(NATIVE_SUKUNA_OPEN_TEST_RUNNING, false);
                continue;
            }
            int sessionTicks = living.getPersistentData().getInt(NATIVE_SUKUNA_OPEN_TEST_TICKS);
            if (sessionTicks >= 60) {
                living.getPersistentData().putDouble("skill", 0.0D);
                living.getPersistentData().putBoolean(NATIVE_SUKUNA_OPEN_TEST_RUNNING, false);
                continue;
            }
            try {
                // Restore the session-owned state after the entity's native AI has ticked.
                living.getPersistentData().putDouble("skill", 107.0D);
                living.getPersistentData().putDouble("cnt1",
                        living.getPersistentData().getDouble(NATIVE_SUKUNA_OPEN_TEST_CNT1));
                Class<?> procedure = Class.forName("net.mcreator.jujutsucraft.procedures.CursedTechniqueSukunaProcedure");
                var execute = procedure.getMethod("execute", net.minecraft.world.level.LevelAccessor.class,
                        double.class, double.class, double.class, Entity.class);
                execute.invoke(null, living.level(), living.getX(), living.getY(), living.getZ(), living);
                double nextCnt1 = living.getPersistentData().getDouble("cnt1");
                living.getPersistentData().putDouble(NATIVE_SUKUNA_OPEN_TEST_CNT1, nextCnt1);
                living.getPersistentData().putInt(NATIVE_SUKUNA_OPEN_TEST_TICKS, sessionTicks + 1);
                if (living.getPersistentData().getDouble("skill") != 107.0D || nextCnt1 > 45.0D) {
                    living.getPersistentData().putBoolean(NATIVE_SUKUNA_OPEN_TEST_RUNNING, false);
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                living.getPersistentData().putDouble("skill", 0.0D);
                living.getPersistentData().putBoolean(NATIVE_SUKUNA_OPEN_TEST_RUNNING, false);
            }
        }
    }

    private static boolean isNativeSukuna(Entity entity) {
        String name = entity.getClass().getName();
        return name.equals("net.mcreator.jujutsucraft.entity.SukunaEntity")
                || name.equals("net.mcreator.jujutsucraft.entity.SukunaFushiguroEntity")
                || name.equals("net.mcreator.jujutsucraft.entity.SukunaPerfectEntity");
    }

    /** Spawn the short-lived domain hand and barrier effects without dealing damage, for visual testing. */
    private static int spawnMahitoDomainVisuals(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 origin = player.position().add(look.scale(3.0D));
        var hands = ModEntities.MAHITO_DOMAIN_HANDS.get().create(level);
        var barrier = ModEntities.MAHITO_DOMAIN_BARRIER.get().create(level);
        int spawned = 0;
        if (hands != null) {
            hands.moveTo(origin.x, origin.y + 0.2D, origin.z, player.getYRot(), 0.0F);
            if (level.addFreshEntity(hands)) spawned++;
        }
        if (barrier != null) {
            barrier.moveTo(origin.x, origin.y, origin.z, player.getYRot(), 0.0F);
            if (level.addFreshEntity(barrier)) spawned++;
        }
        if (spawned == 2) {
            player.sendSystemMessage(Component.literal("已生成真人领域手势与屏障视觉测试，约 3 秒后自动消失；此命令不会造成伤害。"));
            return 1;
        }
        player.sendSystemMessage(Component.literal("领域视觉测试实体生成不完整：" + spawned + "/2。请检查模组注册与日志。"));
        return 0;
    }

    /** Spawn one domain visual at a time to isolate model, texture, and renderer failures. */
    private static int spawnMahitoDomainPart(ServerPlayer player, boolean handsOnly) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 origin = player.position().add(look.scale(3.0D));
        Entity visual = (handsOnly ? ModEntities.MAHITO_DOMAIN_HANDS.get()
                : ModEntities.MAHITO_DOMAIN_BARRIER.get()).create(level);
        if (visual == null) {
            player.sendSystemMessage(Component.literal("领域视觉实体创建失败；请检查注册日志。"));
            return 0;
        }
        visual.moveTo(origin.x, origin.y + (handsOnly ? 0.2D : 0.0D), origin.z, player.getYRot(), 0.0F);
        if (!level.addFreshEntity(visual)) {
            player.sendSystemMessage(Component.literal("领域视觉实体未能加入世界；请检查 latest.log。"));
            return 0;
        }
        String label = handsOnly ? "真人领域手势" : "真人领域屏障";
        player.sendSystemMessage(Component.literal("已单独生成" + label + "视觉测试，约 3 秒后自动消失；不会造成伤害。"));
        return 1;
    }

    private static int beginChargedPurple(ServerPlayer player) {
        long now = player.level().getGameTime();
        long readyAt = player.getPersistentData().getLong(CHARGE_READY_KEY);
        if (now < readyAt) {
            player.sendSystemMessage(Component.literal("200% 虚式茈冷却中，还需 " + ((readyAt - now + 19) / 20) + " 秒。"));
            return 0;
        }
        if (player.getPersistentData().getLong(CHARGE_START_KEY) > 0L) {
            player.sendSystemMessage(Component.literal("200% 虚式茈正在蓄力，请等待完成。"));
            return 0;
        }
        player.getPersistentData().putLong(CHARGE_START_KEY, Math.max(1L, now));
        player.sendSystemMessage(Component.literal("开始蓄力 200% 虚式茈：5 秒后自动发射。蓄力期间保持存活。"));
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                player.getX(), player.getEyeY(), player.getZ(), 24, 0.45D, 0.45D, 0.45D, 0.025D);
        player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
        return 1;
    }

    /** A charged Purple is an in-session action; do not resume it after reconnecting. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().getPersistentData().remove(CHARGE_START_KEY);
    }

    /** Charging cannot be carried across dimensions; preserve the independent cooldown. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        event.getEntity().getPersistentData().remove(CHARGE_START_KEY);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) return;
        long start = player.getPersistentData().getLong(CHARGE_START_KEY);
        if (start <= 0L) return;
        if (!player.isAlive()) {
            player.getPersistentData().remove(CHARGE_START_KEY);
            player.sendSystemMessage(Component.literal("你已倒下，200% 虚式茈蓄力中断。"));
            return;
        }
        if (player.level().getGameTime() - start < CHARGE_TICKS) return;
        player.getPersistentData().remove(CHARGE_START_KEY);
        long now = player.level().getGameTime();
        player.getPersistentData().putLong(CHARGE_READY_KEY, now + 400L);
        firePurple(player, true);
    }

    /**
     * One-shot NPC prototype: Gojo Satoru begins a five-second 200% Purple charge when
     * a Sukuna-possessed Megumi NPC is within 20 blocks. Class names are checked by
     * string to avoid a hard compile-time dependency on JujutsuCraft internals.
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity gojo = event.getEntity();
        if (gojo.level().isClientSide || !(gojo.level() instanceof ServerLevel level)) return;
        if (!gojo.getClass().getSimpleName().equals("GojoSatoruEntity")) return;
        long now = level.getGameTime();
        // Emergency Purple: below 20% health, Gojo can repeatedly fire normal Purple
        // only while crouching. If a fresh one-shot 200% encounter is available, begin
        // that charge first; otherwise the emergency branch could prevent the Meguna
        // trigger from ever starting while Gojo is already low on health.
        long emergencyReadyAt = gojo.getPersistentData().getLong(NPC_LOW_HEALTH_PURPLE_READY_AT);
        boolean prioritizeChargedPurple = !gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)
                && gojo.getPersistentData().getLong(NPC_CHARGE_START) == 0L
                && findNearbyPossessedMegumi(gojo, level) != null;
        if (!prioritizeChargedPurple && gojo.isCrouching() && gojo.getHealth() > 0.0F
                && gojo.getHealth() <= gojo.getMaxHealth() * 0.20F && now >= emergencyReadyAt) {
            Entity target = findEmergencyPurpleTarget(gojo, level);
            Vec3 emergencyDirection = target == null ? gojo.getLookAngle().normalize()
                    : target.getBoundingBox().getCenter().subtract(gojo.getEyePosition()).normalize();
            HollowPurpleEntity emergencyPurple = ModEntities.HOLLOW_PURPLE.get().create(level);
            if (emergencyPurple != null) {
                Vec3 emergencyOrigin = gojo.getEyePosition().add(emergencyDirection.scale(1.8D));
                emergencyPurple.setPos(emergencyOrigin.x, emergencyOrigin.y, emergencyOrigin.z);
                emergencyPurple.launch(gojo, emergencyDirection);
                if (level.addFreshEntity(emergencyPurple)) {
                    gojo.getPersistentData().putLong(NPC_LOW_HEALTH_PURPLE_READY_AT,
                            now + NPC_LOW_HEALTH_PURPLE_COOLDOWN_TICKS);
                    // Keep the independent 200% Purple charge and locked target intact.
                    // Emergency shots have their own cooldown and must not cancel the
                    // one-shot charged attack's progress.
                    level.sendParticles(ParticleTypes.FLASH, gojo.getX(), gojo.getEyeY(), gojo.getZ(), 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.END_ROD, emergencyOrigin.x, emergencyOrigin.y, emergencyOrigin.z,
                            36, 0.55D, 0.55D, 0.55D, 0.04D);
                    level.playSound(null, gojo.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                            SoundSource.HOSTILE, 1.5F, 0.7F);
                }
            }
            return;
        }
        if (gojo.getPersistentData().getBoolean(NPC_PURPLE_USED)) return;

        long chargeStart = gojo.getPersistentData().getLong(NPC_CHARGE_START);
        if (chargeStart == 0L) {
            Entity possessedMegumi = findNearbyPossessedMegumi(gojo, level);
            if (possessedMegumi == null) return;
            gojo.getPersistentData().putUUID(NPC_TARGET_UUID, possessedMegumi.getUUID());
            gojo.getPersistentData().putLong(NPC_CHARGE_START, Math.max(1L, now));
            level.sendParticles(ParticleTypes.END_ROD, gojo.getX(), gojo.getEyeY(), gojo.getZ(),
                    36, 0.7D, 0.6D, 0.7D, 0.035D);
            level.playSound(null, gojo.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.HOSTILE, 1.4F, 0.55F);
            return;
        }
        Entity target = null;
        if (gojo.getPersistentData().hasUUID(NPC_TARGET_UUID)) {
            Entity candidate = level.getEntity(gojo.getPersistentData().getUUID(NPC_TARGET_UUID));
            if (candidate != null && candidate.isAlive() && candidate.level() == level
                    && candidate.distanceToSqr(gojo) <= 32.0D * 32.0D
                    && candidate.getClass().getSimpleName().equals("SukunaFushiguroEntity")
                    && !gojo.isAlliedTo(candidate)) {
                target = candidate;
            }
        }
        // Do not release a locked 200% Purple at an unrelated direction if the locked
        // Sukuna target dies, changes dimension, or escapes during the five-second charge.
        if (target == null) {
            gojo.getPersistentData().remove(NPC_CHARGE_START);
            gojo.getPersistentData().remove(NPC_TARGET_UUID);
            level.sendParticles(ParticleTypes.SMOKE, gojo.getX(), gojo.getEyeY(), gojo.getZ(),
                    12, 0.35D, 0.35D, 0.35D, 0.015D);
            return;
        }
        long chargeElapsed = now - chargeStart;
        if (chargeElapsed < CHARGE_TICKS) {
            if (gojo.tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, gojo.getX(), gojo.getEyeY(), gojo.getZ(),
                        8, 0.35D, 0.35D, 0.35D, 0.08D);
            }
            // Distinct final-second buildup cue for the NPC's 200% Purple. This is a
            // server-side particle cue, not a claim that the native GeckoLib animation
            // has been replaced or connected.
            if (chargeElapsed >= CHARGE_TICKS - 20L && gojo.tickCount % 2 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, gojo.getX(), gojo.getEyeY(), gojo.getZ(),
                        10, 0.5D, 0.45D, 0.5D, 0.025D);
                level.sendParticles(ParticleTypes.END_ROD, gojo.getX(), gojo.getEyeY(), gojo.getZ(),
                        6, 0.4D, 0.35D, 0.4D, 0.02D);
            }
            return;
        }

        Vec3 direction = target.getBoundingBox().getCenter().subtract(gojo.getEyePosition()).normalize();
        HollowPurpleEntity purple = ModEntities.HOLLOW_PURPLE.get().create(level);
        if (purple == null) return;
        Vec3 origin = gojo.getEyePosition().add(direction.scale(1.8D));
        purple.setPos(origin.x, origin.y, origin.z);
        purple.setCharged200Percent();
        purple.launch(gojo, direction);
        if (level.addFreshEntity(purple)) {
            gojo.getPersistentData().putBoolean(NPC_PURPLE_USED, true);
            gojo.getPersistentData().remove(NPC_CHARGE_START);
            gojo.getPersistentData().remove(NPC_TARGET_UUID);
            level.playSound(null, gojo.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 1.8F, 0.65F);
        }
    }

    /** Find the nearest living, non-allied Sukuna-possessed Megumi within the 200% trigger radius. */
    private static Entity findNearbyPossessedMegumi(LivingEntity gojo, ServerLevel level) {
        AABB search = gojo.getBoundingBox().inflate(20.0D);
        return level.getEntities(gojo, search, Entity::isAlive).stream()
                .filter(e -> e.getClass().getSimpleName().equals("SukunaFushiguroEntity"))
                .filter(e -> !gojo.isAlliedTo(e))
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(gojo)))
                .orElse(null);
    }

    /** Prefer the requested Sukuna-versus-Gojo matchup; otherwise choose the nearest living non-ally. */
    private static Entity findEmergencyPurpleTarget(LivingEntity gojo, ServerLevel level) {
        AABB search = gojo.getBoundingBox().inflate(32.0D);
        var candidates = level.getEntities(gojo, search, Entity::isAlive).stream()
                .filter(e -> e instanceof LivingEntity && e != gojo && !gojo.isAlliedTo(e))
                .filter(e -> e.distanceToSqr(gojo) <= 32.0D * 32.0D)
                .toList();
        Entity sukuna = candidates.stream()
                .filter(e -> e.getClass().getSimpleName().equals("SukunaFushiguroEntity"))
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(gojo)))
                .orElse(null);
        if (sukuna != null) return sukuna;
        return candidates.stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(gojo)))
                .orElse(null);
    }

    private static int firePurple(ServerPlayer player, boolean charged) {
        long now = player.level().getGameTime();
        long readyAt = player.getPersistentData().getLong("jujutsuenhancement_purple_cooldown");
        if (!charged && now < readyAt) {
            player.sendSystemMessage(Component.literal("虚式茈冷却中，还需 " + ((readyAt - now + 19) / 20) + " 秒。"));
            return 0;
        }
        HollowPurpleEntity purple = ModEntities.HOLLOW_PURPLE.get().create(player.serverLevel());
        if (purple == null) return 0;
        Vec3 look = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(look.scale(1.8D));
        purple.setPos(origin.x, origin.y, origin.z);
        if (charged) purple.setCharged200Percent();
        purple.launch(player, look);
        if (!player.serverLevel().addFreshEntity(purple)) {
            player.sendSystemMessage(Component.literal("虚式茈生成失败。"));
            return 0;
        }
        if (!charged) player.getPersistentData().putLong("jujutsuenhancement_purple_cooldown", player.level().getGameTime() + 100L);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                origin.x, origin.y, origin.z, 32, 0.5D, 0.5D, 0.5D, 0.2D);
        player.sendSystemMessage(Component.literal(charged
                ? "200% 虚式茈已发射（蓄力原型：80 点伤害、较大命中范围，冷却 20 秒）。"
                : "虚式茈原型已发射（测试技能，冷却 5 秒）。"));
        return 1;
    }
}
