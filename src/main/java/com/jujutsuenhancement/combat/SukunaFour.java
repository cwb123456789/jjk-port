package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.registry.ModItems;
import com.jujutsuenhancement.text.BedrockText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 宿傩 四手战斗风格 {@code ds:four_fists} (1.84.5, {@code functions/sukuna/fighting/four_m1s, use_sukuna_heavy3},
 * {@code attacks/four_combat/*}); needs the original body (千年原身).
 *
 * <pre>
 *  M1 (5 hits: 8 / 6 / 6 / 6 / 26 ticks): hits 1..4 at BasicM1 = 3: 7 damage r1.8 at ^^^1.85 (Big r4.849 at ^^^3.85), Stun 16 / 16 / 6;
 *       hit 5: knock-up, then 1 damage and Stun 15 every tick for BasicM1 6..19 within 2.45 of ^^^2.5
 *  right click         双重冲击   double_impact       N36  cd160   target at ^^^2.5 held 1.35 ahead, 40 damage, Stun 25, thrown
 *  sneak               流斩断头   obliterate (counter) N16 cd6000 parry window t 2..12, then 150 damage
 *  sprint + jump       堕天降踏   demonic_leap         N51  cd300   landing: 30 (r20) + 15 (r10) + 10 (r5), knockdown 25, Stun 25
 *  jump                缭斩落袭   overwhelming_power   N114 cd600   hits 12 / 7 / 7 / 12, 1 per tick barrage, slam 16
 *  sneak + jump        「黑闪」   forced_roles         N96  cd800   (the clash game is left for the CG stage)
 * </pre>
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaFour {
    private static final int IMPACT = 1, COUNTER = 2, LEAP = 3, POWER = 4, ROLES = 5;
    private static final String[] NAME = {"", "双重冲击", "流斩断头", "堕天降踏", "缭斩落袭", "「黑闪」"};
    private static final int[] N = {0, 36, 16, 51, 114, 96};
    private static final int[] CD = {0, 160, 6000, 300, 600, 800};
    private static final String[] ANIM = {"", "heian_sukuna.double_impact", "heian_sukuna.demonic_counter", "heian_sukuna.demonic_leap",
            "heian_sukuna.overwhelming_power", "forced_roles"};
    private static final int[] M1_N = {8, 6, 6, 6, 26};

    private static final class State {
        int skill, t, c, phase, m1s, m1, m1Reset, disabled, frames;
        final int[] cd = new int[6];
        UUID target;
        UUID counter;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SukunaFour() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    private static boolean holds(Player p) {
        return p.getMainHandItem().getItem() == ModItems.FOUR_FISTS.get();
    }

    public static boolean isBusy(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && (s.skill != 0 || s.m1 > 0);
    }

    public static boolean hasFrames(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.frames > 0;
    }

    public static void onUse(ServerPlayer p) {
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§4宿傩未掌控身体主导权"), true);
            return;
        }
        State s = state(p);
        if (s.skill != 0 || s.m1 > 0 || !p.isAlive() || SukunaFists.isStunned(p) || SukunaFists.otherBusy(p) || SukunaMax.isBusy(p)) return;
        boolean sneak = p.isShiftKeyDown(), air = !p.onGround(), sprint = p.isSprinting();
        int k;
        if (!sneak && !air) k = IMPACT;
        else if (sneak && !air) k = COUNTER;
        else if (!sneak && sprint) k = LEAP;
        else if (!sneak) k = POWER;
        else k = ROLES;
        if (k == ROLES) {
            p.displayClientMessage(BedrockText.parse("§l§e「黑闪」(强制对抗) 将在 CG 阶段实装"), true);
            return;
        }
        if (s.cd[k] > 0) {
            p.sendSystemMessage(BedrockText.parse("§l§c" + NAME[k] + " 冷却中: §l§e" + s.cd[k] + "§c."));
            return;
        }
        s.skill = k;
        s.t = 0;
        s.c = 0;
        s.phase = 0;
        s.target = null;
        s.counter = null;
        s.cd[k] = CD[k];
        s.m1 = 0;
    }

    public static void onLeftClick(ServerPlayer p) {
        if (!holds(p) || !p.isAlive()) return;
        State s = state(p);
        if (!SukunaTechnique.active(p)) return;
        if (s.skill != 0 || SukunaFists.isStunned(p) || SukunaFists.otherBusy(p) || SukunaMax.isBusy(p) || p.isShiftKeyDown()) return;
        if (s.m1 > 0 || s.disabled > 0) return;
        s.m1s = s.m1s >= 5 ? 1 : s.m1s + 1;
        s.m1 = M1_N[s.m1s - 1];
        s.m1Reset = 25;
        ModNet.playAnim(p, "heian_sukuna.attack" + s.m1s, 0);
    }

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
        if (SukunaFists.isStunned(p)) {
            s.m1 = 0;
            s.m1s = 0;
            return;
        }
        s.m1--;
        int m1 = s.m1;
        Vec3 f = SukunaFists.flat(p);
        if (s.m1s <= 4) {
            if (s.m1s == 4 && m1 >= 4 && m1 <= 6) s.frames = 6;
            if (m1 == 3) {
                for (LivingEntity t : SukunaFists.around(level, p, p.position().add(f.scale(1.85D)), 1.8D, false)) hit1(level, p, t, s.m1s);
                for (LivingEntity t : SukunaFists.around(level, p, p.position().add(f.scale(3.85D)), 4.849D, true)) hit1(level, p, t, s.m1s);
            }
            if (s.m1s >= 3 && m1 >= 4) SukunaFists.push(p, f, 0.35D);
        } else {
            if (m1 >= 22) SukunaFists.push(p, f, 0.67D);
            if (m1 == 5 && p.onGround()) {
                for (LivingEntity t : SukunaFists.around(level, p, p.position().add(f.scale(4.0D)), 3.95D, null)) t.setDeltaMovement(0.0D, 0.9D, 0.0D);
            }
            if (m1 >= 6 && m1 <= 19) {
                for (LivingEntity t : SukunaFists.around(level, p, p.position().add(f.scale(2.5D)), 2.45D, false)) {
                    GojoTechEntity.hurt(level, p, t, 1.0F);
                    GojoTechEntity.stun(t, 15);
                }
                for (LivingEntity t : SukunaFists.around(level, p, p.position().add(f.scale(4.0D)), 4.5D, true)) {
                    GojoTechEntity.hurt(level, p, t, 1.0F);
                    GojoTechEntity.stun(t, 15);
                }
            }
        }
        if (s.m1 == 0 && s.m1s == 5) s.m1s = 0;
    }

    private static void hit1(ServerLevel level, ServerPlayer p, LivingEntity t, int combo) {
        GojoTechEntity.hurt(level, p, t, 7.0F);
        if (combo <= 2) GojoTechEntity.stun(t, 16);
        if (combo == 3) GojoTechEntity.stun(t, 6);
    }

    private static void skillTick(ServerPlayer p, State s, ServerLevel level) {
        s.t++;
        int t = s.t;
        if (t == 1) ModNet.playAnim(p, ANIM[s.skill], 0);
        LivingEntity target = s.target == null ? null : level.getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
        Vec3 f = SukunaFists.flat(p);
        switch (s.skill) {
            case IMPACT -> {
                if (SukunaFists.isStunned(p) && t <= 6) {
                    s.skill = 0;
                    return;
                }
                if (t >= 7 && t <= 31) s.frames = 6;
                if (t <= 4) SukunaFists.push(p, f, 1.0D);
                if (t >= 4 && t <= 8) SukunaFists.hold(p);
                if (t == 5) {
                    LivingEntity e = null;
                    Vec3 at = p.position().add(f.scale(2.5D));
                    for (LivingEntity c : SukunaFists.around(level, p, at, 2.49D, null)) if (e == null || c.distanceToSqr(at) < e.distanceToSqr(at)) e = c;
                    if (e != null) s.target = e.getUUID();
                }
                if (target != null && t >= 8 && t <= 20) {
                    Vec3 to = p.position().add(f.scale(1.35D));
                    target.teleportTo(to.x, p.getY(), to.z);
                    target.setDeltaMovement(Vec3.ZERO);
                }
                if (t == 21 && target != null) {
                    GojoTechEntity.hurt(level, p, target, 40.0F);
                    GojoTechEntity.stun(target, 25);
                    target.teleportTo(p.getX() + f.x * 30.0D, p.getY() + 35.0D, p.getZ() + f.z * 30.0D);
                    target.fallDistance = 0.0F;
                }
                if (t >= N[IMPACT]) s.skill = 0;
            }
            case COUNTER -> counter(p, s, level, t, target);
            case LEAP -> {
                if (t >= 22 && t <= 23) {
                    p.setDeltaMovement(f.x * 1.1D, 0.7D, f.z * 1.1D);
                    p.hurtMarked = true;
                } else if (t >= 27 && t <= 29) {
                    p.setDeltaMovement(0.0D, -1.5D, 0.0D);
                    p.hurtMarked = true;
                } else if (t < 22) SukunaFists.hold(p);
                if (t >= 22 && t <= 29) s.frames = 6;
                if (t == 30) {
                    for (LivingEntity e : SukunaFists.around(level, p, p.position(), 20.0D, null)) {
                        double d = e.position().distanceTo(p.position());
                        float dmg = 30.0F + (d <= 10.0D ? 15.0F : 0.0F) + (d <= 5.0D ? 10.0F : 0.0F);
                        GojoTechEntity.hurt(level, p, e, dmg);
                        SukunaFists.knockdown(e, 25);
                        GojoTechEntity.stun(e, 25);
                    }
                }
                if (t >= N[LEAP]) s.skill = 0;
            }
            case POWER -> power(p, s, level, t, f);
            default -> s.skill = 0;
        }
    }

    /** obliterate: Frames from t 5; a hit taken within t 2..12 is parried (the attacker within 4.99 is caught), then 150 damage. */
    private static void counter(ServerPlayer p, State s, ServerLevel level, int t, LivingEntity target) {
        if (s.phase == 0) {
            SukunaFists.hold(p);
            if (t >= 5) s.frames = 14;
            if (t >= 2 && t <= 12 && s.counter != null && level.getEntity(s.counter) instanceof LivingEntity a && a.isAlive()
                    && a.position().distanceTo(p.position()) <= 4.99D) {
                s.target = a.getUUID();
                s.phase = 1;
                s.c = 0;
                GojoTechEntity.stun(a, 15);
                ModNet.playAnim(p, "heian_sukuna.demonic_counter2", 0);
                return;
            }
            if (t >= N[COUNTER]) s.skill = 0;
            return;
        }
        s.c++;
        s.frames = 6;
        SukunaFists.hold(p);
        if (target == null) {
            s.skill = 0;
            return;
        }
        if (s.c <= 7) {
            Vec3 f = SukunaFists.flat(p);
            Vec3 to = p.position().add(f.scale(1.25D));
            target.teleportTo(to.x, p.getY(), to.z);
        }
        GojoTechEntity.stun(target, 25);
        if (s.c == 47) {
            GojoTechEntity.hurt(level, p, target, 150.0F);
            GojoTechEntity.stun(target, 40);
        }
        if (s.c >= 50) s.skill = 0;
    }

    private static void power(ServerPlayer p, State s, ServerLevel level, int t, Vec3 f) {
        // move 114 - t: hits at move 97 / 91 / 87 / 79 (12 / 7 / 7 / 12), the barrage at move 63..46 (1 per tick, r6), the slam at move 23
        if (t <= 3) SukunaFists.hold(p);
        if (t >= 10 && t <= 17) SukunaFists.push(p, f, 1.4D);
        int[] at = {17, 23, 27, 35};
        float[] dmg = {12.0F, 7.0F, 7.0F, 12.0F};
        for (int i = 0; i < 4; i++) {
            if (t == at[i]) {
                Vec3 c = p.position().add(f.scale(6.0D));
                for (LivingEntity e : SukunaFists.around(level, p, c, 5.9D, null)) {
                    GojoTechEntity.hurt(level, p, e, dmg[i]);
                    GojoTechEntity.stun(e, 25);
                    e.setDeltaMovement(0.0D, 0.5D, 0.0D);
                    e.hurtMarked = true;
                }
            }
        }
        if (t >= 46 && t <= 54) s.frames = 6;
        if (t >= 51 && t <= 68) {
            Vec3 c = p.position().add(0, 1, 0);
            List<LivingEntity> hit = SukunaFists.around(level, p, c, 6.0D, null);
            for (LivingEntity e : hit) {
                GojoTechEntity.hurt(level, p, e, 1.0F);
                GojoTechEntity.stun(e, 25);
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
            }
            if (!hit.isEmpty()) SukunaTechnique.addFuga(p, 2);
        }
        if (t >= 73 && t <= 90) SukunaFists.push(p, f, 0.0D);
        if (t == 91) {
            for (LivingEntity e : SukunaFists.around(level, p, p.position(), 8.0D, null)) {
                GojoTechEntity.hurt(level, p, e, 16.0F);
                SukunaFists.knockdown(e, 25);
                GojoTechEntity.stun(e, 25);
            }
        }
        if (t >= N[POWER]) s.skill = 0;
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.skill == COUNTER && s.phase == 0 && s.t >= 2 && s.t <= 12 && event.getSource().getEntity() instanceof LivingEntity a && a != p) {
            s.counter = a.getUUID();
            event.setCanceled(true);
            return;
        }
        if (s.frames > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
