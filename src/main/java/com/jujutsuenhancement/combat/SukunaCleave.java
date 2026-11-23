package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.registry.ModItems;
import com.jujutsuenhancement.text.BedrockText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 宿傩 斩击战斗风格 {@code ds:cleave_fists} (1.84.5: {@code functions/sukuna/fighting/slash_m1s, use_sukuna_heavy2},
 * {@code attacks/cleave_combat/*}); also the switching between the styles (tap sneak while looking down).
 *
 * <pre>
 *  M1 (left click, 5 hits: 7 / 8 / 8 / 8 / 26 ticks): hits 1..4 at BasicM1 = 3: 3 damage r1.8 at ^^^1.85 (Big: r4.849 at ^^^3.85),
 *       Stun 16 (hits 1, 2) / 6 (hit 3); hit 5 (N 26): frames from tick 12, held at 15..20, at BasicM1 16 the 解 projectile dismantlem1
 *  right click     斩刻     carve           N19  cd120   grabs within 2.249 of ^^^2.25, at move 7 (t 12) 15 damage, Stun 25, +12 fuga
 *  sneak           袭斩三瓣 rushing_cleave  N26  cd225   dash (knockback 12) at move 9..5 hits everything within 4 once: 30 damage, Stun 30
 *  jump            双划对斩 dual_cleave     N26  cd200   25 damage r2.249 at move 15 (t 11), Stun 25
 *  sneak + left    乱缭斩尽 rapid_cleaves   N56  cd800   target held 0.75 ahead, 2 damage per tick for 24 ticks, knockdown 30
 *  sneak + jump    连斩切刻 cleave_combo    N76  cd800   hits 1 / 1 / 1 / 1 / 1 / 6 on a target held 2.5 ahead, Stun 25, +3 fuga per hit
 * </pre>
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaCleave {
    private static final int CARVE = 1, RUSH = 2, DUAL = 3, RAPID = 4, COMBO = 5;
    private static final String[] NAME = {"", "斩刻", "袭斩三瓣", "双划对斩", "乱缭斩尽", "连斩切刻"};
    private static final int[] N = {0, 19, 26, 26, 56, 76};
    private static final int[] CD = {0, 120, 225, 200, 800, 800};
    private static final String[] ANIM = {"", "carve", "rushing_cleave", "dual_cleave", "rapid_cleave", "cleave_combo"};
    private static final int[] M1_N = {7, 8, 8, 8, 26};

    private static final class Proj {
        UUID owner;
        Vec3 pos, dir;
        int left = 25;
        final Set<UUID> hit = new HashSet<>();
    }

    private static final class State {
        int skill, t, m1s, m1, m1Reset, disabled, frames, sneakTicks;
        final int[] cd = new int[6];
        UUID target;
        final Set<UUID> rushed = new HashSet<>();
        final List<Proj> projs = new ArrayList<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SukunaCleave() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    private static boolean holds(Player p) {
        return p.getMainHandItem().getItem() == ModItems.CLEAVE_FISTS.get();
    }

    public static boolean isBusy(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && (s.skill != 0 || s.m1 > 0);
    }

    public static boolean hasFrames(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.frames > 0;
    }

    // ------------------------------------------------------------------ input

    public static void onUse(ServerPlayer p) {
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§4宿傩未掌控身体主导权"), true);
            return;
        }
        State s = state(p);
        if (s.skill != 0 || s.m1 > 0 || !p.isAlive() || SukunaFists.isStunned(p) || SukunaFists.otherBusy(p)) return;
        boolean sneak = p.isShiftKeyDown(), air = !p.onGround();
        int k = sneak && air ? COMBO : sneak ? RUSH : air ? DUAL : CARVE;
        start(p, s, k);
    }

    public static void onLeftClick(ServerPlayer p) {
        if (!holds(p) || !p.isAlive()) return;
        State s = state(p);
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§4宿傩未掌控身体主导权"), true);
            return;
        }
        if (s.skill != 0 || SukunaFists.isStunned(p) || SukunaFists.otherBusy(p)) return;
        if (p.isShiftKeyDown()) {
            start(p, s, RAPID);
            return;
        }
        if (s.m1 > 0 || s.disabled > 0) return;
        s.m1s = s.m1s >= 5 ? 1 : s.m1s + 1;
        s.m1 = M1_N[s.m1s - 1];
        s.m1Reset = 25;
        ModNet.playAnim(p, "cleave.attack" + s.m1s, 0);
    }

    private static void start(ServerPlayer p, State s, int k) {
        if (s.cd[k] > 0) {
            p.sendSystemMessage(BedrockText.parse("§l§c" + NAME[k] + " 冷却中: §l§e" + s.cd[k] + "§c."));
            return;
        }
        s.skill = k;
        s.t = 0;
        s.target = null;
        s.rushed.clear();
        s.cd[k] = CD[k];
        s.m1 = 0;
    }

    // ------------------------------------------------------------------ the style switch

    private static Item style(String id) {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(JujutsuEnhancement.MODID, id));
    }

    /** fighting_switch1/2/3: the held style item is replaced (fists <-> slash; four arms -> fists). */
    private static void switchStyle(ServerPlayer p, State s) {
        Item held = p.getMainHandItem().getItem();
        String to;
        if (held == ModItems.SUKUNA_FISTS.get()) to = "cleave_fists";
        else if (held == ModItems.CLEAVE_FISTS.get()) to = SukunaTechnique.heian(p) ? "four_fists" : "sukuna_fists";
        else if (held == ModItems.FOUR_FISTS.get()) to = "sukuna_fists";
        else return;
        Item it = style(to);
        if (it == null) return;
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(it));
        s.disabled = 7;
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 2.0F);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = state(p);
        // tap sneak while looking down (Sneaking=1..4 and !uplook) with a style item in the hand
        if (p.isShiftKeyDown()) s.sneakTicks++;
        else s.sneakTicks = 0;
        if (s.sneakTicks == 1 && p.getXRot() > 75.0F && !SukunaFists.isStunned(p) && SukunaTechnique.active(p)
                && (holds(p) || p.getMainHandItem().getItem() == ModItems.SUKUNA_FISTS.get() || p.getMainHandItem().getItem() == ModItems.FOUR_FISTS.get())) switchStyle(p, s);
        for (int i = 1; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
        if (s.frames > 0) s.frames--;
        if (s.disabled > 0) s.disabled--;
        if (s.m1Reset > 0 && --s.m1Reset == 0 && s.m1 == 0) s.m1s = 0;
        ServerLevel level = p.serverLevel();
        tickProjs(p, s, level);
        if (!p.isAlive()) {
            s.skill = 0;
            s.m1 = 0;
            return;
        }
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
        if (s.m1s <= 4) {
            if (s.m1s == 4 && m1 >= 4 && m1 <= 6) s.frames = 6;
            if (m1 == 3) {
                Vec3 f = SukunaFists.flat(p);
                for (LivingEntity t : SukunaFists.around(level, p, p.position().add(f.scale(1.85D)), 1.8D, false)) hit1(level, p, t, s.m1s);
                for (LivingEntity t : SukunaFists.around(level, p, p.position().add(f.scale(3.85D)), 4.849D, true)) hit1(level, p, t, s.m1s);
            }
        } else {
            if (m1 >= 14) s.frames = 7;
            if (m1 >= 21) SukunaFists.push(p, SukunaFists.flat(p), 1.0D);
            else if (m1 >= 15 && m1 <= 20) SukunaFists.hold(p);
            if (m1 == 16) {
                Proj pr = new Proj();
                pr.owner = p.getUUID();
                Vec3 look = p.getLookAngle();
                pr.pos = p.position().add(0.0D, 1.0D, 0.0D).add(look.scale(0.15D));
                pr.dir = new Vec3(look.x, look.y + 0.05D, look.z).normalize();
                s.projs.add(pr);
                ModNet.moveFx(p, "sukuna_m1_slash");
            }
        }
        if (s.m1 == 0 && s.m1s == 5) s.m1s = 0;
    }

    private static void hit1(ServerLevel level, ServerPlayer p, LivingEntity t, int combo) {
        GojoTechEntity.hurt(level, p, t, 3.0F);
        if (combo <= 2) GojoTechEntity.stun(t, 16);
        if (combo == 3) GojoTechEntity.stun(t, 6);
    }

    private static void skillTick(ServerPlayer p, State s, ServerLevel level) {
        if (SukunaFists.isStunned(p) && s.t >= 5 && s.t <= N[s.skill] - 14) {
            s.skill = 0;
            return;
        }
        s.t++;
        int t = s.t;
        if (t == 1) ModNet.playAnim(p, ANIM[s.skill], 0);
        LivingEntity target = s.target == null ? null : level.getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
        switch (s.skill) {
            case CARVE -> carve(p, s, level, t);
            case RUSH -> rush(p, s, level, t);
            case DUAL -> dual(p, s, level, t);
            case RAPID -> rapid(p, s, level, t, target);
            case COMBO -> combo(p, s, level, t, target);
            default -> s.skill = 0;
        }
    }

    private static LivingEntity nearest(ServerLevel level, ServerPlayer p, double fwd, double r) {
        Vec3 at = p.position().add(SukunaFists.flat(p).scale(fwd));
        LivingEntity best = null;
        for (LivingEntity e : SukunaFists.around(level, p, at, r, null)) if (best == null || e.distanceToSqr(at) < best.distanceToSqr(at)) best = e;
        return best;
    }

    private static void face(LivingEntity t, ServerPlayer p) {
        Vec3 d = p.position().subtract(t.position());
        t.setYRot((float) (Math.atan2(-d.x, d.z) * 180.0D / Math.PI));
    }

    /** carve: move 18 -> 1; targets within 2.249 of ^^^2.25 are brought to that point; move 7 (t 12): 15 damage, Stun 25. */
    private static void carve(ServerPlayer p, State s, ServerLevel level, int t) {
        if (t >= 8 && t <= 16) SukunaFists.hold(p);
        Vec3 at = p.position().add(SukunaFists.flat(p).scale(2.25D));
        if (t >= 7 && t <= 18) {
            for (LivingEntity e : SukunaFists.around(level, p, at, 2.249D, null)) {
                e.teleportTo(at.x, p.getY(), at.z);
                face(e, p);
                e.setDeltaMovement(Vec3.ZERO);
                if (t == 12) {
                    GojoTechEntity.hurt(level, p, e, 15.0F);
                    GojoTechEntity.stun(e, 25);
                }
                if (t == 16) {
                    SukunaTechnique.addFuga(p, 12);
                    if (e.getHealth() <= e.getMaxHealth() * 0.15F) GojoTechEntity.hurt(level, p, e, 50.0F);
                }
            }
        }
        if (t == 9 || t == 10) {
            if (SukunaFists.around(level, p, at, 2.249D, null).isEmpty()) s.skill = 0;
        }
        if (t >= N[CARVE]) s.skill = 0;
    }

    /** rushing_cleave: frames t 16..25 (move 10..1), knockback 12 at move 9..5 (t 17..21): everything within 4 is hit once for 30. */
    private static void rush(ServerPlayer p, State s, ServerLevel level, int t) {
        if (t >= 16) s.frames = 6;
        if (t >= 17 && t <= 21) {
            SukunaFists.push(p, SukunaFists.flat(p), 2.4D);
            for (LivingEntity e : SukunaFists.around(level, p, p.position(), 4.0D, null)) {
                if (!s.rushed.add(e.getUUID())) continue;
                GojoTechEntity.hurt(level, p, e, 30.0F);
                GojoTechEntity.stun(e, 30);
                SukunaTechnique.addFuga(p, 3);
            }
        } else if (t >= 22) SukunaFists.hold(p);
        if (t >= N[RUSH]) s.skill = 0;
    }

    /** dual_cleave: move 15 (t 11): 25 damage within 2.249 of ^^^2.25; held 2.5 ahead at t 10..13; knockback 5 at t 11..15 when nothing is within 4. */
    private static void dual(ServerPlayer p, State s, ServerLevel level, int t) {
        Vec3 at = p.position().add(SukunaFists.flat(p).scale(2.25D));
        if (t >= 11 && t <= 15 && SukunaFists.around(level, p, p.position(), 4.0D, null).isEmpty()) SukunaFists.push(p, SukunaFists.flat(p), 1.2D);
        if (t >= 10 && t <= 13) {
            SukunaFists.hold(p);
            for (LivingEntity e : SukunaFists.around(level, p, p.position().add(SukunaFists.flat(p).scale(2.5D)), 2.49D, null)) {
                e.teleportTo(p.getX() + SukunaFists.flat(p).x * 2.5D, p.getY(), p.getZ() + SukunaFists.flat(p).z * 2.5D);
                face(e, p);
                GojoTechEntity.stun(e, 25);
            }
        }
        if (t == 11) {
            List<LivingEntity> hit = SukunaFists.around(level, p, at, 2.249D, null);
            for (LivingEntity e : hit) GojoTechEntity.hurt(level, p, e, 25.0F);
            if (!hit.isEmpty()) SukunaTechnique.addFuga(p, 5);
        }
        if (t >= N[DUAL]) s.skill = 0;
    }

    /** rapid_cleaves: t 12..42 the target is held 0.75 ahead / 0.25 up, 2 damage each tick t 18..41, knockdown 30 at t 42. */
    private static void rapid(ServerPlayer p, State s, ServerLevel level, int t, LivingEntity target) {
        if (t >= 12 && t <= 42) SukunaFists.hold(p);
        if (t == 12) {
            LivingEntity e = nearest(level, p, 2.25D, 2.249D);
            if (e == null) {
                s.skill = 0;
                return;
            }
            s.target = e.getUUID();
        }
        if (target != null && t >= 12 && t <= 42) {
            Vec3 f = SukunaFists.flat(p);
            target.teleportTo(p.getX() + f.x * 0.75D, p.getY() + 0.25D, p.getZ() + f.z * 0.75D);
            target.setDeltaMovement(Vec3.ZERO);
            face(target, p);
            GojoTechEntity.stun(target, 25);
            if (t >= 18 && t <= 41) GojoTechEntity.hurt(level, p, target, 2.0F);
            if (t == 41 && target.getHealth() <= target.getMaxHealth() * 0.15F) GojoTechEntity.hurt(level, p, target, 50.0F);
            if (t == 42) SukunaFists.knockdown(target, 30);
        } else if (target == null && t > 12) s.skill = 0;
        if (t >= N[RAPID]) s.skill = 0;
    }

    /** cleave_combo: a target is held 2.5 ahead from t 10 to 64; hits (move 65 / 55 / 46 / 41 / 35 / 30 / 25: 1 damage, Stun 25, +3 fuga), finishing 6 at t 63. */
    private static void combo(ServerPlayer p, State s, ServerLevel level, int t, LivingEntity target) {
        if (t >= 6 && t <= 9) SukunaFists.hold(p);
        if (t >= 10 && t <= 65) {
            Vec3 f = SukunaFists.flat(p);
            Vec3 at = p.position().add(f.scale(2.5D));
            if (t == 10) {
                LivingEntity e = nearest(level, p, 2.5D, 2.49D);
                if (e != null) s.target = e.getUUID();
            }
            if (target != null) {
                target.teleportTo(at.x, p.getY(), at.z);
                face(target, p);
                target.setDeltaMovement(Vec3.ZERO);
                GojoTechEntity.stun(target, 25);
            }
            int[] moves = {55, 46, 41, 35, 30, 25};
            for (int mv : moves) {
                int tt = 76 - mv;
                if (t == tt - 10) SukunaFists.push(p, f, 2.0D);
                if (t == tt && target != null) {
                    GojoTechEntity.hurt(level, p, target, 1.0F);
                    SukunaTechnique.addFuga(p, 3);
                }
            }
            if (t == 63 && target != null) {
                GojoTechEntity.hurt(level, p, target, 6.0F);
                SukunaTechnique.addFuga(p, 10);
            }
        }
        if (t >= N[COMBO]) s.skill = 0;
    }

    /** dismantlem1: 2.4 blocks per tick for 14 ticks (then until a block), five spheres of 1.99 around it: 15 damage, Stun 25, +1 fuga. */
    private static void tickProjs(ServerPlayer p, State s, ServerLevel level) {
        for (int i = s.projs.size() - 1; i >= 0; i--) {
            Proj pr = s.projs.get(i);
            pr.left--;
            Vec3 next = pr.pos.add(pr.dir.scale(2.4D));
            BlockPos bp = BlockPos.containing(next);
            boolean blocked = !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
            if (!blocked) pr.pos = next;
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 side = pr.dir.cross(up);
            side = side.lengthSqr() < 1.0E-6D ? new Vec3(1, 0, 0) : side.normalize();
            Vec3[] pts = {pr.pos, pr.pos.add(side.scale(2)).add(0, 2, 0), pr.pos.add(side.scale(-2)).add(0, -2, 0), pr.pos.add(side.scale(4)).add(0, 4, 0),
                    pr.pos.add(side.scale(-4)).add(0, -4, 0)};
            for (Vec3 c : pts) {
                for (LivingEntity t : SukunaFists.around(level, p, c, 1.99D, null)) {
                    if (!pr.hit.add(t.getUUID())) continue;
                    GojoTechEntity.hurt(level, p, t, 15.0F);
                    GojoTechEntity.stun(t, 25);
                    SukunaTechnique.addFuga(p, 1);
                }
            }
            if (blocked || pr.left <= 11 && pr.left < 0 || pr.left <= 0) s.projs.remove(i);
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.frames > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    public static void resetCooldowns(Player p) {
        State s = STATES.get(p.getUUID());
        if (s != null) java.util.Arrays.fill(s.cd, 0);
    }
}
