package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.BedrockFxEntity;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.stats.Energy;
import com.jujutsuenhancement.text.BedrockText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
        int skill, t, frames, ability2, ability3;
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
            default -> s.ability3 = 500;
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
        if (s.frames > 0) s.frames--;
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

    /** The entities a move hits: everything alive within r of c except the caster, armor stands, creative/spectator players and entities with Frames. */
    private static List<LivingEntity> targets(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> {
            if (e == p || !e.isAlive() || e instanceof ArmorStand || e.position().distanceTo(c) > r) return false;
            if (e instanceof Player pl) {
                if (pl.isCreative() || pl.isSpectator()) return false;
                return !hasFrames(pl);
            }
            return true;
        });
    }
}
