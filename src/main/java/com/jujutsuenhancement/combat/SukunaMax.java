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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 最大输出 (Maximum Output) of 解 ({@code ds:sukuna1}, {@code attacks/dismantles/maximum_output_charge}) and of 捌 ({@code ds:sukuna2},
 * {@code attacks/cleaves/maximum_output_charge}): sneak + jump with 10 fingers and 500 Energy starts a charge of 66 ticks
 * ({@code move} 66 -> 2); using the item again inside a window jumps to the stage of that window:
 * <pre>
 *   window (move)   38..56 (t 10..28)  27..36 (t 30..39)  9..26 (t 40..57)  4..7 (t 59..62)
 *   stage                1                  2                  3                 4
 *   解   N / cooldown    21 / 888           21 / 999           101 / 1600        61 / 3200
 *        projectile      maxdismantle1      maxdismantle2      50 x maxdismantle3 waffle_dismantle
 *   捌   N / cooldown    76 / 999           76 / 888           60 / 1500         201 / 5000
 * </pre>
 * The cutscene cameras and the terrain destruction belong to the CG stage.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaMax {
    private static final int CHARGE_N = 66;
    private static final int[][] WINDOW = {{10, 28}, {30, 39}, {40, 57}, {59, 62}};   // t windows of stage 1..4
    private static final int[] D_N = {0, 21, 21, 101, 61};
    private static final int[] D_CD = {0, 888, 999, 1600, 3200};
    private static final int[] C_N = {0, 76, 76, 60, 201};
    private static final int[] C_CD = {0, 999, 888, 1500, 5000};
    private static final String[] D_ANIM = {"", "dismantle_maximum1", "dismantle_maximum2", "dismantle_maximum3", "dismantle_maximum4"};
    private static final String[] C_ANIM = {"", "cleave_maximum1", "cleave_maximum2", "cleave_maximum3", "cleave_maximum4"};

    private static final class Proj {
        final int kind; // 1..3 maxdismantle, 4 waffle, 5 shock lane
        UUID owner;
        Vec3 pos, dir;
        int d;
        final Map<UUID, Integer> hit = new HashMap<>();

        Proj(int kind) {
            this.kind = kind;
        }
    }

    private static final class State {
        boolean cleave;
        int phase, t, stage, st, cd;
        boolean click;
        UUID target;
        final List<Proj> projs = new ArrayList<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SukunaMax() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean isBusy(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.phase != 0;
    }

    public static boolean hasFrames(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.phase != 0;
    }

    // ------------------------------------------------------------------ input

    public static void onUse(ServerPlayer p, boolean cleave) {
        State s = state(p);
        if (s.phase == 1) {
            s.click = true;
            return;
        }
        if (s.phase != 0 || !p.isAlive() || !SukunaTechnique.active(p) || SukunaFists.isStunned(p)) return;
        if (!(p.isShiftKeyDown() && !p.onGround())) return;
        if (SukunaFists.otherBusy(p) || SukunaFuga.isBusy(p) || SukunaDomain.casting(p) || SukunaFists.isBusy(p) || SukunaCleave.isBusy(p)) return;
        if (SukunaTechnique.fingers(p) < 10) {
            p.displayClientMessage(BedrockText.parse("§l§4需要至少吃下 10 根宿傩手指"), true);
            return;
        }
        ServerLevel level = p.serverLevel();
        if (Energy.get(p) < 500) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.87F);
            return;
        }
        if (s.cd > 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.4F);
            p.displayClientMessage(BedrockText.parse("§l§c最大输出 冷却中: §l§e" + s.cd + "§c."), false);
            return;
        }
        Energy.set(p, Energy.get(p) - 500);
        s.cleave = cleave;
        s.phase = 1;
        s.t = 0;
        s.stage = 0;
        s.click = false;
        s.target = null;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.cd > 0) s.cd--;
        ServerLevel level = p.serverLevel();
        tickProjs(p, s, level);
        if (s.phase == 0) return;
        if (!p.isAlive()) {
            s.phase = 0;
            return;
        }
        if (s.phase == 1) charge(p, s, level);
        else if (s.cleave) cleaveStage(p, s, level);
        else dismantleStage(p, s, level);
    }

    private static void hold(ServerPlayer p) {
        p.setDeltaMovement(0.0D, 0.0D, 0.0D);
        p.fallDistance = 0.0F;
        p.hurtMarked = true;
    }

    private static void charge(ServerPlayer p, State s, ServerLevel level) {
        s.t++;
        int t = s.t;
        if (t == 1) {
            ModNet.playAnim(p, s.cleave ? "cleave_charge1" : "dismantle_charge1", 0);
            ModNet.moveFx(p, s.cleave ? "sukuna_maxc_charge" : "sukuna_maxd_charge");
        }
        if (t <= 3 && !p.onGround()) { // ds:downfall at move 63..65
            p.setDeltaMovement(0.0D, -1.5D, 0.0D);
            p.hurtMarked = true;
        } else hold(p);
        if (SukunaFists.isStunned(p) && t <= CHARGE_N - 3) {
            s.phase = 0;
            return;
        }
        if (t >= CHARGE_N - 7 && t <= CHARGE_N - 3 && t == CHARGE_N - 7) ModNet.playAnim(p, s.cleave ? "cleave_charge2hold" : "dismantle_charge1hold", 0);
        if (s.click) {
            s.click = false;
            for (int i = 0; i < 4; i++) {
                if (t >= WINDOW[i][0] && t <= WINDOW[i][1]) {
                    startStage(p, s, i + 1);
                    return;
                }
            }
        }
        if (t >= CHARGE_N - 2) s.phase = 0;
    }

    private static void startStage(ServerPlayer p, State s, int stage) {
        s.phase = 2;
        s.stage = stage;
        s.st = 0;
        s.target = null;
        s.cd = (s.cleave ? C_CD : D_CD)[stage];
        ModNet.playAnim(p, (s.cleave ? C_ANIM : D_ANIM)[stage], 0);
        ModNet.moveFx(p, (s.cleave ? "sukuna_maxc" : "sukuna_maxd") + stage);
    }

    private static Vec3 dirOf(ServerPlayer p) {
        Vec3 l = p.getLookAngle();
        return new Vec3(l.x, l.y + 0.05D, l.z).normalize();
    }

    private static void dismantleStage(ServerPlayer p, State s, ServerLevel level) {
        s.st++;
        int t = s.st;
        hold(p);
        Vec3 look = p.getLookAngle();
        switch (s.stage) {
            case 1 -> {
                if (t == 11) spawn(s, p, 1, p.position().add(look.scale(2.5D)), 26);
            }
            case 2 -> {
                if (t == 11) spawn(s, p, 2, p.position().add(0, 1, 0).add(look.scale(2.5D)), 35);
            }
            case 3 -> {
                if (t >= 31 && t <= 80) spawn(s, p, 3, p.position().add(0, 1, 0).add(look.scale(2.0D)), 25);
            }
            case 4 -> {
                if (t == 31) spawn(s, p, 4, p.position().add(0, 12, 0).add(look.scale(2.0D)), 45);
            }
            default -> {
            }
        }
        if (t >= D_N[s.stage]) s.phase = 0;
    }

    private static void spawn(State s, ServerPlayer p, int kind, Vec3 pos, int deleter) {
        Proj pr = new Proj(kind);
        pr.owner = p.getUUID();
        pr.pos = pos;
        pr.dir = dirOf(p);
        pr.d = deleter;
        s.projs.add(pr);
    }

    // ------------------------------------------------------------------ 捌

    private static void cleaveStage(ServerPlayer p, State s, ServerLevel level) {
        s.st++;
        int t = s.st;
        hold(p);
        Vec3 f = SukunaFists.flat(p);
        switch (s.stage) {
            case 1 -> {
                if (t == 15) {
                    LivingEntity e = null;
                    Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(8.0D));
                    for (LivingEntity c : SukunaFists.around(level, p, at, 7.99D, null)) if (e == null || c.distanceToSqr(at) < e.distanceToSqr(at)) e = c;
                    if (e != null) s.target = e.getUUID();
                }
                LivingEntity tg = s.target == null ? null : level.getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
                if (t >= 20 && t <= 24 && tg == null) {
                    s.phase = 0;
                    return;
                }
                if (tg != null && t >= 16 && t <= 76) {
                    Vec3 to = p.position().add(f.scale(1.75D));
                    tg.teleportTo(to.x, p.getY(), to.z);
                    tg.setDeltaMovement(Vec3.ZERO);
                    GojoTechEntity.stun(tg, 25);
                    if (t == 31) {
                        GojoTechEntity.hurt(level, p, tg, 70.0F);
                        SukunaFists.knockdown(tg, 30);
                    }
                    if (t == 33) SukunaTechnique.addFuga(p, 10);
                }
            }
            case 2 -> {
                if (t >= 11 && t <= 71) {
                    Vec3 c = p.position().add(0, 1, 0);
                    List<LivingEntity> hit = SukunaFists.around(level, p, c, 10.0D, null);
                    for (LivingEntity e : hit) {
                        GojoTechEntity.hurt(level, p, e, 2.0F);
                        GojoTechEntity.stun(e, 25);
                    }
                    if (!hit.isEmpty()) SukunaTechnique.addFuga(p, 2);
                }
            }
            case 3 -> {
                int[] at = {11, 31, 46};
                float[] dmg = {36.0F, 32.0F, 42.0F};
                for (int i = 0; i < 3; i++) {
                    if (t == at[i]) {
                        Vec3 c = p.position().add(f.scale(6.0D));
                        List<LivingEntity> hit = SukunaFists.around(level, p, c, 5.99D, null);
                        for (LivingEntity e : hit) {
                            GojoTechEntity.hurt(level, p, e, dmg[i]);
                            GojoTechEntity.stun(e, 15);
                            Vec3 to = c.add(f.scale(-2.5D));
                            e.teleportTo(to.x, e.getY(), to.z);
                        }
                        if (!hit.isEmpty()) SukunaTechnique.addFuga(p, 7);
                    }
                }
            }
            case 4 -> {
                if (t == 122 || t == 123) {
                    for (int lane = -2; lane <= 2; lane++) {
                        Vec3 left = new Vec3(f.z, 0, -f.x);
                        Vec3 base = p.position().add(f.scale(6.0D)).add(left.scale(lane * 2.0D));
                        for (int k = 0; k <= 5; k++) {
                            Vec3 c = base.add(f.scale(10.0D * k));
                            if (c.distanceTo(p.position()) > 50.0D) break;
                            shock(level, p, c);
                        }
                    }
                }
            }
            default -> {
            }
        }
        if (t >= C_N[s.stage]) s.phase = 0;
    }

    /** max_cleave_shockwave: 100 damage within 9.99 and 150 within 9.99 of the point 10 above, Stun 30, fuga +33 / +5. */
    private static void shock(ServerLevel level, ServerPlayer p, Vec3 c) {
        for (LivingEntity e : SukunaFists.around(level, p, c, 9.99D, null)) {
            GojoTechEntity.hurt(level, p, e, 100.0F);
            GojoTechEntity.stun(e, 30);
            SukunaTechnique.addFuga(p, 33);
        }
        Vec3 up = c.add(0, 10, 0);
        for (LivingEntity e : SukunaFists.around(level, p, up, 9.99D, null)) {
            GojoTechEntity.hurt(level, p, e, 150.0F);
            GojoTechEntity.stun(e, 30);
            SukunaTechnique.addFuga(p, 5);
        }
        BedrockFxEntity.play(level, "dismantle_down_land", c.x, c.y, c.z, p.getYRot());
    }

    // ------------------------------------------------------------------ projectiles of 解

    private static void landOne(ServerLevel level, ServerPlayer p, Proj pr, LivingEntity e, float dmg, int stun, int hitCd, int fuga, boolean obl) {
        Integer last = pr.hit.get(e.getUUID());
        if (last != null && pr.hit.getOrDefault(e.getUUID(), 0) > 0 && last + hitCd > p.tickCount) return;
        pr.hit.put(e.getUUID(), p.tickCount);
        GojoTechEntity.hurt(level, p, e, dmg);
        GojoTechEntity.stun(e, stun);
        SukunaTechnique.addFuga(p, fuga);
        if (obl) GojoTechEntity.obliterateIfLow(level, p, e, dmg);
    }

    private static void tickProjs(ServerPlayer p, State s, ServerLevel level) {
        for (int i = s.projs.size() - 1; i >= 0; i--) {
            Proj pr = s.projs.get(i);
            pr.d--;
            boolean done = false;
            switch (pr.kind) {
                case 1 -> { // maxdismantle1: 5 blocks per tick while Deleter >= 11
                    if (pr.d >= 11) {
                        for (int k = 0; k < 5; k++) {
                            Vec3 n = pr.pos.add(pr.dir);
                            if (!level.getBlockState(BlockPos.containing(n)).getCollisionShape(level, BlockPos.containing(n)).isEmpty()) {
                                pr.d = 11;
                                break;
                            }
                            pr.pos = n;
                        }
                    }
                    for (int h = 0; h <= 9; h += 3)
                        for (LivingEntity e : SukunaFists.around(level, p, pr.pos.add(0, h, 0), 2.99D, false)) landOne(level, p, pr, e, 100.0F, 40, 10, 10, true);
                    for (int h = 0; h <= 10; h += 5)
                        for (LivingEntity e : SukunaFists.around(level, p, pr.pos.add(0, h, 0), 4.99D, true)) landOne(level, p, pr, e, 100.0F, 40, 10, 10, true);
                    done = pr.d < 11 && pr.d <= 10;
                }
                case 2 -> { // maxdismantle2: 2 blocks per tick while Deleter >= 11
                    if (pr.d >= 11) {
                        for (int k = 0; k < 2; k++) {
                            Vec3 n = pr.pos.add(pr.dir);
                            if (!level.getBlockState(BlockPos.containing(n)).getCollisionShape(level, BlockPos.containing(n)).isEmpty()) {
                                pr.d = 11;
                                break;
                            }
                            pr.pos = n;
                        }
                    }
                    for (LivingEntity e : SukunaFists.around(level, p, pr.pos, 6.0D, null)) landOne(level, p, pr, e, 25.0F, 40, 10, 10, true);
                    done = pr.d <= 10;
                }
                case 3 -> { // maxdismantle3: 7 blocks per tick for Deleter 1..20, hits within 8 for Deleter 11..20
                    if (pr.d >= 1 && pr.d <= 20) pr.pos = pr.pos.add(pr.dir.scale(7.0D));
                    if (pr.d >= 11 && pr.d <= 20) {
                        for (LivingEntity e : SukunaFists.around(level, p, pr.pos, 8.0D, null)) {
                            GojoTechEntity.hurt(level, p, e, 2.0F);
                            GojoTechEntity.stun(e, 30);
                            SukunaTechnique.addFuga(p, 2);
                            GojoTechEntity.obliterateIfLow(level, p, e, 25.0F);
                        }
                    }
                    done = pr.d <= 0;
                }
                case 4 -> { // waffle_dismantle: 8 blocks per tick for Deleter 11..40, everything within 30 is hit once: 300 damage, Stun 50
                    if (pr.d >= 11 && pr.d <= 40) {
                        pr.pos = pr.pos.add(pr.dir.scale(8.0D));
                        for (LivingEntity e : SukunaFists.around(level, p, pr.pos, 30.0D, null)) {
                            if (pr.hit.containsKey(e.getUUID())) continue;
                            pr.hit.put(e.getUUID(), 1);
                            GojoTechEntity.hurt(level, p, e, 300.0F);
                            GojoTechEntity.stun(e, 50);
                            SukunaTechnique.addFuga(p, 10);
                            GojoTechEntity.obliterateIfLow(level, p, e, 300.0F);
                        }
                    }
                    done = pr.d <= 10;
                }
                default -> done = true;
            }
            if (done) s.projs.remove(i);
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.phase != 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
