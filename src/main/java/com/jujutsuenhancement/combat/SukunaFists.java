package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.stats.Energy;
import com.jujutsuenhancement.text.BedrockText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 宿傩 拳法风格 {@code ds:sukuna_fists} ({@code functions/sukuna/fighting/fist_m1s, use_sukuna_heavy1}, {@code attacks/basic_combat/*}).
 *
 * <pre>
 *  M1 (left click, 5 hits: 6 / 8 / 6 / 8 / 16 ticks, combo reset after 25 idle ticks)
 *       hits 1..4 at BasicM1 = 3: 2 damage (override) r1.8 at ^^^1.85 (big targets: r4.849 at ^^^3.85), Stun 16 (hits 1, 2) / 6 (hit 3)
 *       hit 5 at BasicM1 = 6 (t 10): 8 damage r5, Stun 20, the caster is held 15 ticks and has frames for 10
 *  right click (cooldown)
 *       standing            疾闪升拳?  demonic_blows    N31 cd 120   6 damage Stun 25 at t 12, a click at t 14..18 continues (6 damage, levitation)
 *       jump + sprint       堕天落拳   hellish_descent N31 cd 229   25 damage r6 knockdown 25 at t 16, frames t 8..21
 *       sneak               放开手脚吧 open_space      N56 cd 200   grabs within 2.3 at t 6, carries it, a click at t 43..49: 30 damage, knockdown 80
 *       jump                震地重踏   step            N51 cd 229   20 damage r9 at t 31, frames t 18..46
 *       sneak + left click  在这死吧   takedown        N71 cd 600   target within 8 of ^^^8 at t 6, 2 damage at t 39, 15 damage + knockdown 30 at t 54
 *       sneak + jump        魔乱狂袭   demonic_blitz   N21 cd 1200  dash (knockback 12), grabs within 3.5, carries it for 100 ticks, 40 damage + knockdown 70
 * </pre>
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaFists {
    private static final int BLOWS = 1, DESCENT = 2, SPACE = 3, STEP = 4, TAKEDOWN = 5, BLITZ = 6;
    private static final String[] NAME = {"", "魔弹重击", "堕天落拳", "放开手脚吧", "震地重踏", "在这死吧", "魔乱狂袭"};
    private static final int[] N = {0, 31, 31, 56, 51, 71, 21};
    private static final int[] CD = {0, 120, 229, 200, 229, 600, 1200};
    private static final String[] ANIM = {"", "demonic_blows1", "hellish_descent", "open_space", "step", "takedown_charge", "demonic_blitz_dash"};
    private static final int[] M1_N = {6, 8, 6, 8, 16};

    private static final class State {
        int skill, t, c, phase, m1s, m1, m1Reset, disabled, frames;
        final int[] cd = new int[7];
        UUID target;
        boolean click;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SukunaFists() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    private static boolean holds(Player p) {
        return p.getMainHandItem().getItem() == com.jujutsuenhancement.registry.ModItems.SUKUNA_FISTS.get();
    }

    public static boolean isBusy(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && (s.skill != 0 || s.m1 > 0);
    }

    public static boolean hasFrames(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.frames > 0;
    }

    private static boolean otherBusy(ServerPlayer p) {
        return SukunaSkills.isBusy(p) || SukunaFuga.isBusy(p) || SukunaDomain.casting(p) || GojoSkills.isBusy(p) || YujiSkills.isBusy(p) || KatanaCombat.isBusy(p);
    }

    private static boolean isStunned(Player p) {
        MobEffectInstance e = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return e != null && e.getAmplifier() >= 6;
    }

    // ------------------------------------------------------------------ input

    /** Right click (use_sukuna_heavy1). */
    public static void onUse(ServerPlayer p) {
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§4宿傩未掌控身体主导权"), true);
            return;
        }
        State s = state(p);
        if (s.skill != 0 || s.m1 > 0 || !p.isAlive() || isStunned(p) || otherBusy(p)) return;
        boolean sneak = p.isShiftKeyDown(), air = !p.onGround(), sprint = p.isSprinting();
        int k;
        if (!sneak && !air) k = BLOWS;
        else if (!sneak && air && sprint) k = DESCENT;
        else if (sneak && !air && !sprint) k = SPACE;
        else if (!sneak && air) k = STEP;
        else if (sneak && air && !sprint) k = BLITZ;
        else return;
        start(p, s, k);
    }

    /** Left click: sneaking is 在这死吧, otherwise the next hit of the combo. */
    public static void onLeftClick(ServerPlayer p) {
        if (!holds(p) || !p.isAlive()) return;
        State s = state(p);
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§4宿傩未掌控身体主导权"), true);
            return;
        }
        if (s.skill != 0) {
            s.click = true; // the "punch" continuations of demonic_blows / open_space
            return;
        }
        if (isStunned(p) || otherBusy(p)) return;
        if (p.isShiftKeyDown()) {
            start(p, s, TAKEDOWN);
            return;
        }
        if (s.m1 > 0 || s.disabled > 0) return;
        s.m1s = s.m1s >= 5 ? 1 : s.m1s + 1;
        s.m1 = M1_N[s.m1s - 1];
        s.m1Reset = 25;
        ModNet.playAnim(p, "sukuna.attack" + s.m1s, 0);
    }

    private static void start(ServerPlayer p, State s, int k) {
        if (s.cd[k] > 0) {
            p.sendSystemMessage(BedrockText.parse("§l§c" + NAME[k] + " 冷却中: §l§e" + s.cd[k] + "§c."));
            return;
        }
        s.skill = k;
        s.t = 0;
        s.c = 0;
        s.phase = 0;
        s.target = null;
        s.click = false;
        s.cd[k] = CD[k];
        s.m1 = 0;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        for (int i = 1; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
        if (s.frames > 0) s.frames--;
        if (s.disabled > 0) s.disabled--;
        if (s.m1Reset > 0 && --s.m1Reset == 0 && s.m1 == 0) s.m1s = 0;
        if (!p.isAlive()) {
            s.skill = 0;
            s.m1 = 0;
            return;
        }
        ServerLevel level = p.serverLevel();
        if (s.m1 > 0) m1Tick(p, s, level);
        if (s.skill != 0) skillTick(p, s, level);
    }

    private static void m1Tick(ServerPlayer p, State s, ServerLevel level) {
        if (isStunned(p)) {
            s.m1 = 0;
            s.m1s = 0;
            return;
        }
        int n = M1_N[s.m1s - 1];
        s.m1--;
        int m1 = s.m1; // BasicM1 after the countdown
        if (s.m1s <= 4) {
            if (m1 == 3) {
                Vec3 f = flat(p);
                Vec3 small = p.position().add(f.scale(1.85D));
                Vec3 big = p.position().add(f.scale(3.85D));
                for (LivingEntity t : around(level, p, small, 1.8D, false)) hit1(level, p, t, s.m1s);
                for (LivingEntity t : around(level, p, big, 4.849D, true)) hit1(level, p, t, s.m1s);
            }
        } else { // hit 5
            if (m1 == n - 1) {
                s.frames = 10;
                s.disabled = 15;
            }
            if (m1 >= 9 && n - m1 <= 7) push(p, f(p), 2.0D);
            if (m1 == 6) {
                for (LivingEntity t : around(level, p, p.position(), 5.0D, false)) {
                    GojoTechEntity.hurt(level, p, t, 8.0F);
                    GojoTechEntity.stun(t, 20);
                    Vec3 d = t.position().subtract(p.position()).multiply(1, 0, 1);
                    if (d.lengthSqr() > 1.0E-4D) {
                        d = d.normalize();
                        t.setDeltaMovement(d.x * 1.3D, 0.5D, d.z * 1.3D);
                        t.hurtMarked = true;
                    }
                }
            }
        }
        if (s.m1 == 0 && s.m1s == 5) s.m1s = 0;
    }

    private static void hit1(ServerLevel level, ServerPlayer p, LivingEntity t, int combo) {
        GojoTechEntity.hurt(level, p, t, 2.0F);
        if (combo <= 2) GojoTechEntity.stun(t, 16);
        if (combo == 3) GojoTechEntity.stun(t, 6);
    }

    private static void skillTick(ServerPlayer p, State s, ServerLevel level) {
        if (isStunned(p) && s.t <= N[s.skill] - 25 && s.phase == 0) { // move 25..: Stun cancels
            s.skill = 0;
            return;
        }
        s.t++;
        int t = s.t;
        if (t == 1) ModNet.playAnim(p, ANIM[s.skill], 0);
        LivingEntity target = s.target == null ? null : level.getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
        switch (s.skill) {
            case BLOWS -> blows(p, s, level, t, target);
            case DESCENT -> descent(p, s, level, t);
            case SPACE -> space(p, s, level, t, target);
            case STEP -> step(p, s, level, t);
            case TAKEDOWN -> takedown(p, s, level, t, target);
            case BLITZ -> blitz(p, s, level, t, target);
            default -> s.skill = 0;
        }
    }

    /** demonic_blows: knockback 2 for 4 ticks, the blow at move 19 (t 12), the click at move 13..17 continues with the second blow. */
    private static void blows(ServerPlayer p, State s, ServerLevel level, int t, LivingEntity target) {
        if (s.phase == 0) {
            if (t <= 5) push(p, f(p), 2.0D);
            else if (t <= 12) push(p, f(p), 1.0D);
            else hold(p);
            if (t >= 12 && t <= 14) hold(p);
            if (t == 12) {
                Vec3 at = p.position().add(flat(p).scale(2.5D));
                List<LivingEntity> hit = around(level, p, at, 3.7D, null);
                for (LivingEntity e : hit) {
                    GojoTechEntity.hurt(level, p, e, 6.0F);
                    GojoTechEntity.stun(e, 25);
                    if (s.target == null) s.target = e.getUUID();
                }
                if (hit.isEmpty()) s.target = null;
                s.frames = 0;
            }
            if (t >= 8 && t <= 12) s.frames = 6;
            if (t >= 14 && t <= 18 && s.click && s.target != null) {
                s.phase = 1;
                s.c = 0;
                ModNet.playAnim(p, "demonic_blows2", 0);
            } else if (t >= 18) {
                s.skill = 0;
            }
        } else {
            s.c++;
            hold(p);
            s.frames = 6;
            if (target != null && s.c <= 3) {
                Vec3 pos = p.position().add(flat(p).scale(1.5D));
                target.teleportTo(pos.x, p.getY(), pos.z);
            }
            if (s.c == 11 && target != null) {
                GojoTechEntity.hurt(level, p, target, 6.0F);
                GojoTechEntity.stun(target, 25);
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 25, false, false));
            }
            if (s.c >= 24) s.skill = 0;
        }
    }

    /** hellish_descent: knockback 2, frames t 8..21, 25 damage + knockdown 25 within 6 of ^^^4 at move 15 (t 16). */
    private static void descent(ServerPlayer p, State s, ServerLevel level, int t) {
        if (t <= 9) push(p, f(p), 2.0D);
        if (t >= 8 && t <= 21) s.frames = 6;
        if (t >= 14 && t <= 26) hold(p);
        if (t == 16) {
            Vec3 at = p.position().add(flat(p).scale(4.0D));
            for (LivingEntity e : around(level, p, at, 6.0D, null)) {
                GojoTechEntity.hurt(level, p, e, 25.0F);
                knockdown(e, 25);
                GojoTechEntity.stun(e, 25);
            }
        }
        if (t >= N[DESCENT]) s.skill = 0;
    }

    /** open_space: grab within 2.3 of ^^^2.25 at move 50 (t 6), carried 0.75 per tick; the click continues with 30 damage + knockdown 80. */
    private static void space(ServerPlayer p, State s, ServerLevel level, int t, LivingEntity target) {
        s.frames = 7;
        if (s.phase == 0) {
            hold(p);
            if (t == 6) {
                List<LivingEntity> hit = around(level, p, p.position().add(flat(p).scale(2.25D)), 2.3D, null);
                if (!hit.isEmpty()) s.target = hit.get(0).getUUID();
            }
            if (target != null && t >= 6 && t <= 30) {
                Vec3 pos = target.position().add(flat(p).scale(0.75D));
                target.teleportTo(pos.x, pos.y, pos.z);
                GojoTechEntity.stun(target, 25);
            }
            if (t == 36 && target != null) target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 5, false, false));
            if (t >= 43 && t <= 49 && s.click && target != null) {
                s.phase = 1;
                s.c = 0;
                ModNet.playAnim(p, "open_space2", 0);
            }
            if (t >= N[SPACE]) s.skill = 0;
        } else {
            s.c++;
            hold(p);
            if (target != null && s.c >= 1 && s.c <= 16) {
                Vec3 pos = p.position().add(flat(p).scale(1.5D));
                target.teleportTo(pos.x, p.getY() + 2.0D, pos.z);
                GojoTechEntity.stun(target, 25);
            }
            if (s.c == 16 && target != null) {
                GojoTechEntity.hurt(level, p, target, 30.0F);
                knockdown(target, 80);
                GojoTechEntity.stun(target, 25);
                Vec3 d = flat(p);
                target.setDeltaMovement(d.x * 0.8D, 0.6D, d.z * 0.8D);
                target.hurtMarked = true;
            }
            if (s.c >= 30) s.skill = 0;
        }
    }

    /** step: falls to the ground, frames t 18..46, 20 damage within 9 at move 20 (t 31), things are knocked away. */
    private static void step(ServerPlayer p, State s, ServerLevel level, int t) {
        if (t >= 18 && t <= 46) s.frames = 6;
        if (t <= 17 && !p.onGround()) p.setDeltaMovement(p.getDeltaMovement().x * 0.5D, -1.5D, p.getDeltaMovement().z * 0.5D);
        else hold(p);
        p.hurtMarked = true;
        if (t == 31) {
            for (LivingEntity e : around(level, p, p.position(), 9.0D, null)) {
                GojoTechEntity.hurt(level, p, e, 20.0F);
                Vec3 d = e.position().subtract(p.position()).multiply(1, 0, 1);
                if (d.lengthSqr() > 1.0E-4D) {
                    d = d.normalize();
                    e.setDeltaMovement(d.x * 1.5D, 0.6D, d.z * 1.5D);
                    e.hurtMarked = true;
                }
            }
        }
        if (t >= N[STEP]) s.skill = 0;
    }

    /** takedown: the target within 7.99 of ^^^8 at move 65 (t 6) is held; 2 damage at move 32, 15 damage + knockdown 30 at move 17. */
    private static void takedown(ServerPlayer p, State s, ServerLevel level, int t, LivingEntity target) {
        s.frames = t >= 5 ? 6 : s.frames;
        if (t == 6) {
            Vec3 at = p.position().add(flat(p).scale(8.0D));
            List<LivingEntity> hit = around(level, p, at, 7.99D, null);
            LivingEntity best = null;
            for (LivingEntity e : hit) if (best == null || e.distanceToSqr(at) < best.distanceToSqr(at)) best = e;
            if (best != null) s.target = best.getUUID();
        }
        if (t < 15) hold(p);
        if (target != null && t >= 6) {
            GojoTechEntity.stun(target, 25);
            if (t >= 15 && t <= 46) {
                Vec3 d = target.position().subtract(p.position());
                if (d.horizontalDistance() > 1.6D) {
                    Vec3 step = d.multiply(1, 0, 1).normalize().scale(-0.25D);
                    p.setDeltaMovement(-step.x * 2.0D, p.getDeltaMovement().y, -step.z * 2.0D);
                    p.hurtMarked = true;
                } else hold(p);
                p.setYRot((float) (Math.atan2(-d.x, d.z) * 180.0D / Math.PI));
            }
            if (t == 39) GojoTechEntity.hurt(level, p, target, 2.0F);
            if (t == 54) {
                GojoTechEntity.hurt(level, p, target, 15.0F);
                knockdown(target, 30);
                GojoTechEntity.stun(target, 25);
            }
        }
        if (t >= N[TAKEDOWN]) s.skill = 0;
    }

    /** demonic_blitz: dash (knockback 12 at move 10..5), the grabbed target is carried for 100 ticks and slammed: 40 damage, knockdown 70. */
    private static void blitz(ServerPlayer p, State s, ServerLevel level, int t, LivingEntity target) {
        s.frames = 7;
        if (s.phase == 0) {
            if (t >= 11 && t <= 16) {
                push(p, f(p), 1.2D);
                if (s.target == null) {
                    List<LivingEntity> hit = around(level, p, p.position().add(flat(p).scale(2.0D)), 3.5D, null);
                    if (!hit.isEmpty()) s.target = hit.get(0).getUUID();
                }
            }
            if (t >= 17 && t <= 20) hold(p);
            if (t >= 17 && s.target != null) {
                s.phase = 1;
                s.c = 0;
                ModNet.playAnim(p, "demonic_blitz1", 0);
            } else if (t >= N[BLITZ]) s.skill = 0;
        } else {
            s.c++;
            hold(p);
            if (target != null) {
                GojoTechEntity.stun(target, 45);
                double dist = s.c < 30 ? 4.0D : 2.0D;
                Vec3 pos = p.position().add(flat(p).scale(dist));
                target.teleportTo(pos.x, p.getY() + (s.c > 20 && s.c < 90 ? 2.0D : 0.0D), pos.z);
            }
            if (s.c == 99 && target != null) {
                GojoTechEntity.hurt(level, p, target, 40.0F);
                knockdown(target, 70);
                GojoTechEntity.stun(target, 40);
            }
            if (s.c >= 100) s.skill = 0;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void knockdown(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2, false, false));
    }

    private static void hold(ServerPlayer p) {
        p.setDeltaMovement(0.0D, Math.min(0.0D, p.getDeltaMovement().y), 0.0D);
        p.hurtMarked = true;
    }

    private static Vec3 flat(Player p) {
        double y = Math.toRadians(p.getYRot());
        return new Vec3(-Math.sin(y), 0.0D, Math.cos(y));
    }

    private static Vec3 f(Player p) {
        return flat(p);
    }

    private static void push(ServerPlayer p, Vec3 d, double h) {
        p.setDeltaMovement(d.x * h, p.getDeltaMovement().y, d.z * h);
        p.hurtMarked = true;
    }

    /** {@code big}: only Big targets (height 3, width 2.5); otherwise only the small ones. */
    private static List<LivingEntity> around(ServerLevel level, Player own, Vec3 c, double r, Boolean big) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1.0D),
                t -> SukunaFuga.validTarget(own, t) && t.position().add(0, t.getBbHeight() * 0.5D, 0).distanceTo(c.add(0, 1.0D, 0)) <= r + t.getBbWidth() * 0.5D
                        && (big == null || GojoTechEntity.isBig(t) == big));
    }

    @SubscribeEvent
    public static void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.frames > 0 && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
