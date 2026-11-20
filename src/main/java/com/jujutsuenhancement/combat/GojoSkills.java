package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.BedrockFxEntity;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.entity.HollowPurpleEntity;
import com.jujutsuenhancement.registry.ModEntities;
import com.jujutsuenhancement.stats.Energy;
import com.jujutsuenhancement.text.BedrockText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The limitless (无下限术式) items of Gojo for a player: 苍 ({@code ds:limitless2}) and 赫 ({@code ds:limitless3}), ported from
 * {@code functions/limitless/limitless_click.mcfunction} and {@code limitless2*.mcfunction / limitless3*.mcfunction}.
 *
 * <p>The addon keeps the state of a move in scoreboards: the click sets {@code move} to N and the cooldown {@code abilityK},
 * and removes the cost from Energy; the move function then runs once per tick with {@code move} counting down. {@code t}
 * here is the number of ticks since the click, so the addon's {@code move=m} is {@code t = N - m}.
 *
 * <pre>
 *   item 苍 (limitless2, ability2)  click                          50  move 15  cd 250  Blue (flying)
 *                                   jump + click                   50  move 21  cd 240  Blue pull
 *                                   sneak + look down + click     250  move 141 cd 400  Max Blue
 *                                   sneak + six eyes              300  move 66  cd(ability7) 500  chant (not ported yet)
 *   item 赫 (limitless3, ability3)  click (not looking down)       30  move 41  cd 300  Red
 *                                   jump + click                  200  move 51  cd 500  Red, maximum output (Reversal)
 *                                   look down + click              15  move 16  cd 80   Red launch
 *                                   sneak + six eyes              300  move 66  cd(ability8) 500  chant (not ported yet)
 * </pre>
 * "look down" is the addon's {@code !uplook} (pitch above 75 degrees); "jump" is approximated by being in the air.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GojoSkills {
    public static final int BLUE = 1, PULL = 2, MAX = 3, RED = 4, RED_SMALL = 5, REVERSE = 6;
    public static final int BLITZ = 7, BLITZ_AREA = 8, KICK = 9;
    public static final int PURPLE_FAST = 10, PURPLE = 11, PURPLE200 = 12, PURPLE_UNL = 13;
    public static final int DOMAIN = 14, DOMAIN_QUICK = 15;
    private static final DustParticleOptions DUST_PURPLE = new DustParticleOptions(new Vector3f(0.42F, 0.08F, 0.95F), 1.6F);
    private static final DustParticleOptions DUST_RED = new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.12F), 1.6F);
    private static final DustParticleOptions DUST_BLUE = new DustParticleOptions(new Vector3f(0.12F, 0.38F, 1.0F), 1.6F);

    // {total ticks of the move, tick of the main event}
    private static final int BLUE_SHOOT = 11, BLUE_TOTAL = 14;                                 // N 15, shot at move 4
    private static final int PULL_MARK = 6, PULL_STUN = 10, PULL_TELEPORT = 14, PULL_TOTAL = 20; // N 21, moves 15, 11, 7
    private static final int MAX_SPAWN = 47, MAX_TOTAL = 140;                                  // N 141, MaxBlue at move 94
    private static final int RED_BLAST = 28, RED_TOTAL = 40;                                   // N 41, damage at move 13
    private static final int SMALL_BLAST = 11, SMALL_TOTAL = 15;                               // N 16, damage at move 5
    private static final int REVERSE_BLAST = 41, REVERSE_TOTAL = 50;                           // N 51, blast at move 10
    private static final double PULL_RADIUS = 14.95D;
    private static final double RED_RADIUS = 15.0D;

    private static final class State {
        int skill, t, frames, ability2, ability3, ability4, ability5, ability12, domainCD, domainFx = -1;
        long domainEnd;
        UUID kickTarget;
        final Set<UUID> frozen = new HashSet<>();
        final List<UUID> pullTargets = new ArrayList<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static {
        Energy.setBusy(GojoSkills::isBusy);
    }

    private GojoSkills() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean isCasting(ServerPlayer p, int skill) {
        State s = STATES.get(p.getUUID());
        return s != null && s.skill == skill;
    }

    public static boolean isBusy(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.skill != 0;
    }

    public static boolean hasFrames(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.frames > 0;
    }

    // ------------------------------------------------------------------ the click

    /** limitless_click.mcfunction for the items ds:limitless2 and ds:limitless3. */
    public static void onUse(ServerPlayer p, String item) {
        State s = state(p);
        if (!p.isAlive() || s.skill != 0 || KatanaCombat.isBusy(p)) return;
        boolean sneak = p.isShiftKeyDown();
        boolean airborne = !p.onGround();
        boolean lookDown = p.getXRot() > 75.0F;           // !uplook: "tag @a[rx=75] add uplook"
        boolean sixEyes = p.getTags().contains("sixeyes");
        if (item.equals("limitless2")) {
            if (sneak && !lookDown) {
                p.displayClientMessage(sixEyes ? BedrockText.parse("§l§b术式顺转 苍: 咒词吟唱 §l§7(尚未实装)")
                        : BedrockText.parse("§l§c需要 §l§b六眼§l§c 以使用此技能吟唱咒词"), false);
                return;
            }
            if (sneak) cast(p, s, MAX, 250, s.ability2, "术式顺转 苍");
            else if (airborne) cast(p, s, PULL, 50, s.ability2, "术式顺转 苍");
            else cast(p, s, BLUE, 50, s.ability2, "术式顺转 苍");
        } else if (item.equals("limitless3")) {
            if (sneak) {
                p.displayClientMessage(sixEyes ? BedrockText.parse("§l§c术式反转 赫: 咒词吟唱 §l§7(尚未实装)")
                        : BedrockText.parse("§l§c需要 §l§b六眼§l§c 以使用此技能吟唱咒词"), false);
                return;
            }
            if (lookDown) cast(p, s, RED_SMALL, 15, s.ability3, "术式反转 赫");
            else if (airborne) cast(p, s, REVERSE, 200, s.ability3, "术式反转 赫");
            else cast(p, s, RED, 30, s.ability3, "术式反转 赫");
        } else if (item.equals("limitless4")) {
            if (sneak) cast(p, s, BLITZ_AREA, 100, s.ability4, "空间撕扯");
            else if (airborne) cast(p, s, KICK, 100, s.ability4, "空间撕扯");
            else cast(p, s, BLITZ, 45, s.ability4, "空间撕扯");
        } else if (item.equals("limitless5")) {
            boolean lookUp = p.getXRot() <= -80.0F;           // the addon's "lookingup" (rx=-80)
            if (sneak) {
                if (lookUp) cast(p, s, PURPLE_UNL, 7500, s.ability12, "虚式「茈」");
                else cast(p, s, PURPLE, 150, s.ability5, "虚式「茈」");
            } else if (lookDown) cast(p, s, PURPLE200, 3500, s.ability5, "虚式「茈」");
            else cast(p, s, PURPLE_FAST, 80, s.ability5, "虚式「茈」");
        } else if (item.equals("limitless6")) {
            if (airborne && !sneak) cast(p, s, DOMAIN_QUICK, 300, s.domainCD, "领域展开");
            else cast(p, s, DOMAIN, 800, s.domainCD, "领域展开");
        } else if (item.equals("limitless1")) {
            p.displayClientMessage(BedrockText.parse("§l§c无限: 尚未实装"), false);
        }
    }

    private static void cast(ServerPlayer p, State s, int skill, int cost, int cooldown, String name) {
        ServerLevel level = p.serverLevel();
        if (Energy.get(p) < cost) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.87F);
            return;
        }
        if (cooldown > 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.4F);
            p.displayClientMessage(BedrockText.parse("§l§c" + name + " 冷却中: §l§e" + cooldown + "§c."), false);
            return;
        }
        Energy.set(p, Energy.get(p) - cost);
        s.skill = skill;
        s.t = 0;
        switch (skill) {
            case BLUE -> s.ability2 = 250;
            case PULL -> s.ability2 = 240;
            case MAX -> s.ability2 = 400;
            case RED -> s.ability3 = 300;
            case RED_SMALL -> s.ability3 = 80;
            case REVERSE -> s.ability3 = 500;
            case BLITZ -> s.ability4 = 80;
            case BLITZ_AREA -> s.ability4 = 600;
            case KICK -> s.ability4 = 250;
            case PURPLE_FAST -> s.ability5 = 800;
            case PURPLE -> s.ability5 = 2400;
            case PURPLE200 -> s.ability5 = 6000;
            case PURPLE_UNL -> s.ability12 = 32000;
            case DOMAIN -> s.domainCD = 12000;
            default -> s.domainCD = 7200;
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.ability2 > 0) s.ability2--;
        if (s.ability3 > 0) s.ability3--;
        if (s.ability4 > 0) s.ability4--;
        if (s.ability5 > 0) s.ability5--;
        if (s.ability12 > 0) s.ability12--;
        if (s.domainCD > 0) s.domainCD--;
        if (s.frames > 0) s.frames--;
        domainTick(p, s, p.serverLevel());
        if (s.skill == 0) return;
        if (!p.isAlive()) {
            s.skill = 0;
            return;
        }
        s.t++;
        ServerLevel level = p.serverLevel();
        switch (s.skill) {
            case BLUE -> blue(p, s, level);
            case PULL -> pull(p, s, level);
            case MAX -> max(p, s, level);
            case RED -> red(p, s, level);
            case RED_SMALL -> redSmall(p, s, level);
            case BLITZ -> blitz(p, s, level);
            case BLITZ_AREA -> blitzArea(p, s, level);
            case KICK -> kick(p, s, level);
            case PURPLE_FAST -> purpleFast(p, s, level);
            case PURPLE -> purple(p, s, level, 360, 456, 480, 6.0D, 0);
            case PURPLE200 -> purple(p, s, level, 100, 201, 320, 16.0D, 1);
            case PURPLE_UNL -> purple(p, s, level, 21, 306, 480, 1.0D, 2);
            case DOMAIN -> domain(p, s, level, false);
            case DOMAIN_QUICK -> domain(p, s, level, true);
            default -> reverse(p, s, level);
        }
    }

    private static void end(State s) {
        s.skill = 0;
        s.t = 0;
        s.pullTargets.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            State s = STATES.get(p.getUUID());
            if (s != null) releaseFrozen(p.serverLevel(), s);
        }
        STATES.remove(event.getEntity().getUUID());
    }

    /** Frames: the player cannot be hurt while a move gives it invulnerability frames. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.frames > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ 苍

    /** limitless2: animation lapse_blue1 at move 14; at move 4 {@code ds:shootprojectile ds:projectile Lapse velocity:4 Deleter 45}. */
    private static void blue(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) ModNet.playAnim(p, "lapse_blue1", 0);
        if (s.t == BLUE_SHOOT) {
            GojoTechEntity.launch(level, p, GojoTechEntity.BLUE_FLY, new Vec3(p.getX(), p.getY() + 0.1D, p.getZ()), p.getLookAngle());
            level.playSound(null, p.getX(), p.getY() + 1.0D, p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0F, 1.25F);
        }
        if (s.t >= BLUE_TOTAL) end(s);
    }

    /** limitless2c: animation lapse_pull; everything within 14.95 of the point 15 ahead is stunned and put 1.5 blocks in front of the caster. */
    private static void pull(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) ModNet.playAnim(p, "lapse_pull", 0);
        if (s.t == PULL_MARK) {
            Vec3 center = p.position().add(p.getLookAngle().normalize().scale(15.0D));
            s.pullTargets.clear();
            for (LivingEntity e : targets(p, center, PULL_RADIUS)) {
                s.pullTargets.add(e.getUUID());
                BedrockFxEntity.play(level, "gojo_pull_hit", e.getX(), e.getY(), e.getZ(), p.getYRot());
            }
            level.playSound(null, p.getX(), p.getY() + 1.0D, p.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        if (s.t == PULL_STUN) {
            for (UUID id : s.pullTargets) {
                Entity e = level.getEntity(id);
                if (!(e instanceof LivingEntity t) || !t.isAlive()) continue;
                GojoTechEntity.stun(t, 35);
                BedrockFxEntity.play(level, "gojo_pull_mark", t.getX(), t.getY() + 1.0D, t.getZ(), p.getYRot());
            }
        }
        if (s.t == PULL_TELEPORT) {
            double yawRad = Math.toRadians(p.getYRot());
            // "positioned ^^0.5^1.5": 1.5 ahead of the caster along the facing, which is -sin / +cos for yaw
            double tx = p.getX() - Math.sin(yawRad) * 1.5D, tz = p.getZ() + Math.cos(yawRad) * 1.5D, ty = p.getY() + 0.5D;
            for (UUID id : s.pullTargets) {
                Entity e = level.getEntity(id);
                if (!(e instanceof LivingEntity t) || !t.isAlive()) continue;
                float faceYaw = p.getYRot() + 180.0F;
                if (t instanceof ServerPlayer sp) sp.connection.teleport(tx, ty, tz, faceYaw, sp.getXRot());
                else {
                    t.moveTo(tx, ty, tz, faceYaw, t.getXRot());
                    t.setDeltaMovement(Vec3.ZERO);
                }
                t.fallDistance = 0.0F;
            }
            s.pullTargets.clear();
        }
        if (s.t >= PULL_TOTAL) end(s);
    }

    /** limitless2b: animation lapse_blue2; MaxBlue is summoned 14 blocks ahead at move 94 and follows the caster. */
    private static void max(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) {
            ModNet.playAnim(p, "lapse_blue2", 0);
        }
        if (s.t == 3) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 5.0F, 0.7F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 5.0F, 0.74F);
        }
        if (s.t == MAX_SPAWN) {
            GojoTechEntity.launch(level, p, GojoTechEntity.BLUE_MAX, p.position().add(p.getLookAngle().normalize().scale(14.0D)), p.getLookAngle());
        }
        if (s.t >= MAX_TOTAL) end(s);
    }

    // ------------------------------------------------------------------ 赫

    /** limitless3: animation reversal_red1; Frames while it lasts, slow falling until move 20; at move 13 the blast. */
    private static void red(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) ModNet.playAnim(p, "reversal_red1", 0);
        s.frames = 4;
        if (s.t <= 21) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2, 9, false, false));
        if (s.t >= 27 && s.t <= 33) freeze(p);
        if (s.t == 6) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 0.5F);
        if (s.t == RED_BLAST - 3) BedrockFxEntity.play(level, "gojo_red_blast", p.getX(), p.getY(), p.getZ(), p.getYRot());
        if (s.t == RED_BLAST) {
            Vec3 c = p.position();
            for (LivingEntity t : targets(p, c, RED_RADIUS)) GojoTechEntity.hurt(level, p, t, 35.0F);
            // levitation 1 15 for everything within 7.5 of the points 7.5 above and 8.5 to each side of that
            double yawRad = Math.toRadians(p.getYRot());
            Vec3 left = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
            Vec3[] lifts = {c.add(0.0D, 7.5D, 0.0D), c.add(left.scale(-8.5D)).add(0.0D, 7.5D, 0.0D), c.add(left.scale(8.5D)).add(0.0D, 7.5D, 0.0D)};
            for (Vec3 lc : lifts) {
                for (LivingEntity t : targets(p, lc, 7.5D)) t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 15, false, false));
            }
            GojoTechEntity.roar(level, p, c, 7.0D, 1.5D);  // ds:red_reverse
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 0.5F);
        }
        if (s.t >= RED_TOTAL) end(s);
    }

    /** limitless3b: animation reversal_red3; Frames for the last 7 ticks; at move 5 damage 12 within 5 and the caster is launched. */
    private static void redSmall(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) ModNet.playAnim(p, "reversal_red3", 0);
        if (s.t >= 9) s.frames = 4;
        if (s.t == SMALL_BLAST) {
            BedrockFxEntity.play(level, "gojo_red_small", p.getX(), p.getY(), p.getZ(), p.getYRot());
            for (LivingEntity t : targets(p, p.position(), 5.0D)) {
                GojoTechEntity.hurt(level, p, t, 12.0F);
                Vec3 away = t.position().subtract(p.position()).multiply(1.0D, 0.0D, 1.0D);
                away = away.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 0) : away.normalize();
                t.setDeltaMovement(away.x * 1.5D, 0.5D, away.z * 1.5D);   // ds:knockback summons
                t.hurtMarked = true;
            }
            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 34, false, false));   // "effect @s levitation 1 35"
        }
        if (s.t >= SMALL_TOTAL) end(s);
    }

    /** limitless3c: animation reversal_red2 (red2b in the air); at move 10 a 100-damage blast 7 blocks ahead and the Reversal beam. */
    private static void reverse(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) ModNet.playAnim(p, p.onGround() ? "reversal_red2" : "reversal_red2b", 0);
        s.frames = 5;
        if (s.t >= 40) freeze(p);
        if (s.t == 6) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 0.37F);
        if (s.t == 38) BedrockFxEntity.play(level, "gojo_red_big", p.getX(), p.getY(), p.getZ(), p.getYRot());
        if (s.t == REVERSE_BLAST) {
            Vec3 look = p.getLookAngle().normalize();
            Vec3 c = p.position().add(look.scale(7.0D));
            for (LivingEntity t : targets(p, c, 6.95D)) {
                GojoTechEntity.hurt(level, p, t, 100.0F);
                GojoTechEntity.stun(t, 30);
                Vec3 away = t.position().subtract(p.position()).multiply(1.0D, 0.0D, 1.0D);
                away = away.lengthSqr() < 1.0E-6D ? look.multiply(1.0D, 0.0D, 1.0D) : away.normalize();
                t.setDeltaMovement(away.x * 3.0D, 0.5D, away.z * 3.0D);   // ds:knockback -120
                t.hurtMarked = true;
            }
            GojoTechEntity.launch(level, p, GojoTechEntity.RED_BEAM, new Vec3(p.getX(), p.getY() + 0.1D, p.getZ()), look);
        }
        if (s.t >= REVERSE_TOTAL) end(s);
    }

    // ------------------------------------------------------------------ helpers

    /** "tp @s ~~~": the move stops the caster's motion. */
    private static void freeze(ServerPlayer p) {
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
    }

    private static boolean valid(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof Player pl) {
            if (pl.isCreative() || pl.isSpectator()) return false;
            return !hasFrames(pl);
        }
        return true;
    }

    /** The entities a move hits: everything alive within r of c except the caster, armor stands, creative/spectator players and entities with Frames. */
    private static List<LivingEntity> targets(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> valid(p, e) && e.position().distanceTo(c) <= r);
    }

    private static double distToSegment(Vec3 pt, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double k = l2 < 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, pt.subtract(a).dot(ab) / l2));
        return pt.distanceTo(a.add(ab.scale(k)));
    }

    /** The caster stands still while the cast lasts (the addon cancels the player's motion), falling is allowed. */
    private static void holdStill(ServerPlayer p) {
        Vec3 m = p.getDeltaMovement();
        p.setDeltaMovement(0.0D, Math.min(0.0D, m.y), 0.0D);
        p.hurtMarked = true;
    }

    // ------------------------------------------------------------------ 空间撕扯 (limitless4)

    /** limitless4: animation spatial_blitz, Frames; at move 5 a line ahead (step 0.33, stops at a block) hits everything within 2.5 for 8 fire + 8 projectile, Stun 15. */
    private static void blitz(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) {
            ModNet.playAnim(p, "spatial_blitz", 0);
            ModNet.moveFx(p, "blitz");
        }
        s.frames = 4;
        if (s.t == 11) {
            Vec3 eye = p.getEyePosition(), dir = p.getLookAngle().normalize();
            Vec3 end = eye.add(dir.scale(21.0D));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
            final Vec3 fe = end;
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(2.5D),
                    e -> valid(p, e) && distToSegment(e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), eye, fe) <= 2.5D)) {
                GojoTechEntity.hurt(level, p, t, 16.0F);
                t.setSecondsOnFire(3);
                GojoTechEntity.stun(t, 15);
                BedrockFxEntity.play(level, "gojo_blitz_hit", t.getX(), t.getY(), t.getZ(), p.getYRot());
            }
        }
        if (s.t >= 15) end(s);
    }

    /** limitless4b: animation spacial_blitz; for moves 8..2 everything within 30 takes 4 (override) and Stun 15. */
    private static void blitzArea(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) {
            ModNet.playAnim(p, "spacial_blitz", 0);
            ModNet.moveFx(p, "blitz_area");
        }
        s.frames = 4;
        if (s.t >= 7 && s.t <= 13) {
            holdStill(p);
            for (LivingEntity t : targets(p, p.position(), 30.0D)) {
                GojoTechEntity.hurt(level, p, t, 4.0F);
                t.setSecondsOnFire(2);
                GojoTechEntity.stun(t, 15);
            }
        }
        if (s.t >= 14) end(s);
    }

    /** limitless4c: animation spacial_kick; at move 25 the nearest target ahead is marked, the caster is put behind it (^0.3 ^0.4 ^-1) and at move 15 hits it for 8, Stun 20. */
    private static void kick(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) {
            ModNet.playAnim(p, "spacial_kick", 0);
            ModNet.moveFx(p, "kick");
        }
        s.frames = 4;
        if (s.t == 11) {
            s.kickTarget = null;
            Vec3 eye = p.getEyePosition(), dir = p.getLookAngle().normalize();
            for (double d = 0.0D; d <= 50.0D && s.kickTarget == null; d += 1.0D) {
                Vec3 c = eye.add(dir.scale(d));
                LivingEntity best = null;
                double bd = 4.99D;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(5.0D), x -> valid(p, x))) {
                    double dd = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).distanceTo(c);
                    if (dd < bd) { bd = dd; best = e; }
                }
                if (best != null) s.kickTarget = best.getUUID();
            }
            if (s.kickTarget == null) { end(s); return; }
        }
        Entity te = s.kickTarget == null ? null : level.getEntity(s.kickTarget);
        if (s.t >= 11 && s.t <= 21 && te instanceof LivingEntity t && t.isAlive()) {
            double yaw = Math.toRadians(t.getYRot());
            double x = t.getX() + Math.cos(yaw) * 0.3D + Math.sin(yaw), z = t.getZ() + Math.sin(yaw) * 0.3D - Math.cos(yaw);
            p.connection.teleport(x, t.getY() + 0.4D, z, t.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            GojoTechEntity.stun(t, 5);
            if (s.t == 21) {
                GojoTechEntity.hurt(level, p, t, 8.0F);
                GojoTechEntity.stun(t, 20);
                Vec3 look = p.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
                t.setDeltaMovement(look.x * 1.5D, 0.4D, look.z * 1.5D);
                t.hurtMarked = true;
            }
        }
        if (s.t >= 35) { s.kickTarget = null; end(s); }
    }

    // ------------------------------------------------------------------ 茈 (limitless5)

    private static void launchPurple(ServerPlayer p, ServerLevel level, int mode, double ahead) {
        HollowPurpleEntity purple = ModEntities.HOLLOW_PURPLE.get().create(level);
        if (purple == null) return;
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 origin = p.getEyePosition().add(dir.scale(ahead));
        purple.setPos(origin.x, origin.y, origin.z);
        if (mode == 1) purple.setCharged200Percent();
        else if (mode == 2) purple.setUnlimited();
        purple.launch(p, dir);
        level.addFreshEntity(purple);
    }

    /** limitless5 (move 101): animation imaginary_purple; Frames for moves 52..1, slowness for moves 60..21, at move 44 the purple is shot. */
    private static void purpleFast(ServerPlayer p, State s, ServerLevel level) {
        if (s.t == 1) {
            ModNet.playAnim(p, "imaginary_purple", 0);
            ModNet.moveFx(p, "purple_fast");
        }
        if (s.t >= 49) s.frames = 4;
        if (s.t >= 41 && s.t <= 80) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false));
        if (s.t == 57) launchPurple(p, level, 0, 6.0D);
        if (s.t >= 100) end(s);
    }

    /** limitless5b / 5c / 5d: long casts (the caster stands still); mode 0 purple (move 481, shot at 25), 1 200% purple (move 321, shot at 120), 2 unlimited (move 481). */
    private static void purple(ServerPlayer p, State s, ServerLevel level, int gatherFrom, int fire, int total, double ahead, int mode) {
        if (s.t == 1) {
            ModNet.playAnim(p, mode == 0 ? "hollowpurple" : mode == 1 ? "200_purple" : "limitless_purple", 0);
            ModNet.moveFx(p, mode == 0 ? "purple" : mode == 1 ? "purple200" : "purple_unl");
        }
        if (s.t < fire) holdStill(p);
        if (s.t == fire) launchPurple(p, level, mode, mode == 1 ? 16.0D : mode == 2 ? 1.0D : 6.0D);
        if (s.t >= total) end(s);
    }

    // ------------------------------------------------------------------ 领域展开 (limitless6)

    /** limitless6 / 6b: animation infinite_void (6b: infinite_void_2sec); the show is played by the clients, everything within 40 is held until the domain ends. */
    private static void domain(ServerPlayer p, State s, ServerLevel level, boolean quick) {
        int start = quick ? 4 : 1, total = quick ? 141 : 370;
        if (s.t == 1) ModNet.playAnim(p, quick ? "infinite_void_2sec" : "infinite_void", 0);
        if (s.t == start) {
            s.domainEnd = level.getGameTime() + (quick ? 80 + 431 : 885);
            s.domainFx = quick ? 1 : 0;
            ModNet.domainFx(p, s.domainFx);
        }
        if (s.t < 20) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 4, false, false));
        if (s.t >= total) end(s);
    }

    private static void domainTick(ServerPlayer p, State s, ServerLevel level) {
        if (s.domainFx < 0) return;
        long now = level.getGameTime();
        if (now >= s.domainEnd || !p.isAlive()) {
            releaseFrozen(level, s);
            s.domainFx = -1;
            ModNet.domainFx(p, -1);
            return;
        }
        for (LivingEntity e : targets(p, p.position(), 40.0D)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 99, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6, 9, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 6, 9, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.JUMP, 6, 128, false, false));
            if (e instanceof Mob mob) {
                mob.setTarget(null);
                if (!mob.isNoAi()) {
                    mob.setNoAi(true);
                    s.frozen.add(mob.getUUID());
                }
            }
        }
    }

    private static void releaseFrozen(ServerLevel level, State s) {
        for (UUID id : s.frozen) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob) mob.setNoAi(false);
        }
        s.frozen.clear();
    }
}
