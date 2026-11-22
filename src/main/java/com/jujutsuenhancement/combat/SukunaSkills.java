package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.BedrockFxEntity;
import com.jujutsuenhancement.entity.DismantleSlashEntity;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.stats.Energy;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Sukuna's 解 (Dismantle) skills for a player, the item {@code ds:sukuna1} ({@code functions/sukuna/attacks/dismantles/ab*.mcfunction}).
 * As in {@link YujiSkills}, {@code t = N - move}.
 *
 * <pre>
 *  click            解-快斩   ab1   N11 cost50 cd50      slash ds:dismantle at t6
 *  click, look down 空中解    ab1b  N66 cost50 cd480     three downward slashes at t41/43/45 (in the air only)
 *  jump             解        ab1d  N36 cost75 cd150     back dash, ds:dissect at t21
 *  jump + back      后撤宽斩  ab1e  N41 cost75 cd150     ds:wide_dismantle at t21
 *  sneak            集连裂斩  ab2   N31 cost200 cd800    ds:rapid_dismantle at t21 (40 damage, r8, Stun 40)
 *  sneak in the air 连斩      ab2b  N13 cost200 cd200    ds:dismantle_quick every tick t3..12 (5 damage, r2.5)
 *  sprint + jump    重踢断斩  ab2d  N31 cost200 cd1200   ds:heavy_dismantle at t17..19 (60 damage, r15, Stun 40)
 *  sprint + jump, look down   ab2e  N66 cost200 cd1200   five bursts of 80 damage (r11) at t46..50
 * </pre>
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaSkills {
    private static final int QUICK = 1, DOWN = 2, DISSECT = 3, WIDE = 4, RAPID = 5, SPAM = 6, HEAVY = 7, HEAVY2 = 8,
            CLEAVE = 9, COUNTER = 10, LAYERED = 11, SPIDER = 12, CARVE = 13, CHANT = 14, WCS = 15, WS1 = 16, WS2 = 17;
    private static final int[] N = {0, 11, 66, 36, 41, 31, 13, 31, 66, 11, 16, 26, 41, 191, 21, 31, 136, 136};
    private static final int[] CD = {0, 50, 480, 150, 150, 800, 200, 1200, 1200, 30, 100, 100, 600, 6000, 100, 100, 7200, 7200};
    private static final int[] COST = {0, 50, 50, 75, 75, 200, 200, 200, 200, 30, 30, 100, 100, 300, 200, 500, 3333, 3333};
    private static final String[] NAME = {"", "解-快斩", "空中解", "解", "后撤宽斩", "集连裂斩", "连斩", "重踢断斩", "重踢断斩", "捌", "捌·防反", "雕斩裂刻", "蜘蛛之网", "乱斩裂割", "咒词吟唱", "空间斩", "最大输出 空间斩", "最大输出 空间斩: 垂直"};
    private static final String[] ANIM = {"", "dismantle1", "dismantle_downwards", "dissect", "dismantle_backwards", "rapid_dismantle",
            "dismantle_spam", "heavy_dismantle", "heavy_dismantle2", "cleave", "cleave_counter", "layered_cleave", "spiderweb", "carving_combo", "world_slash_chant", "world_slash_small1", "world_slash1", "world_slash2"};
    private static final String[] FX = {"", "sukuna_ab1", "sukuna_ab1b", "sukuna_ab1d", "sukuna_ab1e", "sukuna_ab2", "sukuna_ab2b",
            "sukuna_ab2d", "sukuna_ab2e", "sukuna_cleave", "sukuna_counter", "sukuna_layered", "sukuna_spider", "sukuna_carve", "sukuna_chant", "sukuna_wcs", "sukuna_ws1", "sukuna_ws2"};
    /** the ability scoreboards share cooldowns: ab1 (1,2), ab3 (3,4), ab4/ab4 (5,6), ab5 (7,8). */
    private static final int[] GROUP = {0, 1, 1, 3, 3, 5, 5, 7, 7, 11, 11, 11, 12, 13, 14, 15, 14, 14};

    private static final class Proj {
        UUID owner;
        Vec3 pos, dir;
        int kind, left;
        final Set<UUID> hit = new HashSet<>();
    }

    private static final class State {
        int skill, t, frames;
        final int[] cd = new int[16];
        UUID counterTarget;
        boolean countered;
        final List<Proj> projs = new ArrayList<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SukunaSkills() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean isBusy(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.skill != 0;
    }

    public static boolean hasFrames(Player p) {
        State s = STATES.get(p.getUUID());
        return (s != null && s.frames > 0) || SukunaFuga.hasFrames(p) || SukunaFists.hasFrames(p) || SukunaCleave.hasFrames(p);
    }

    public static void onUse(ServerPlayer p, String item) {
        if (item.equals("sukuna4")) {
            SukunaFuga.onUse(p);
            return;
        }
        if (item.equals("sukuna_fists")) {
            SukunaFists.onUse(p);
            return;
        }
        if (item.equals("cleave_fists")) {
            SukunaCleave.onUse(p);
            return;
        }
        if (item.equals("sukuna3")) {
            SukunaDomain.onUse(p);
            return;
        }
        if (!item.equals("sukuna1") && !item.equals("sukuna2") && !item.equals("sukuna5")) return;
        State s = state(p);
        if (SukunaFuga.isBusy(p) || SukunaDomain.casting(p) || SukunaFists.isBusy(p) || SukunaCleave.isBusy(p)) return;
        if (s.skill != 0 || !p.isAlive() || GojoSkills.isBusy(p) || YujiSkills.isBusy(p) || KatanaCombat.isBusy(p)) return;
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§c需要宿傩掌控身体"), true);
            return;
        }
        boolean sneak = p.isShiftKeyDown(), air = !p.onGround(), sprint = p.isSprinting();
        boolean down = p.getXRot() > 60.0F;
        int skill;
        if (item.equals("sukuna5")) {
            if (sneak || air) {
                if (!SukunaTechnique.heian(p)) {
                    p.displayClientMessage(BedrockText.parse("§l§4-=- 使用前置: 原身形态转变 -=-"), false);
                    return;
                }
                cast(p, s, sneak ? WS1 : WS2);
            } else cast(p, s, CHANT);
            return;
        }
        if (item.equals("sukuna2")) {
            if (sneak) skill = SPIDER;
            else if (air) skill = LAYERED;
            else skill = down ? COUNTER : CLEAVE;
            cast(p, s, skill);
            return;
        }
        if (sneak) skill = air ? SPAM : RAPID;
        else if (air && sprint) skill = down ? HEAVY2 : HEAVY;
        else if (air) skill = p.zza < 0.0F ? WIDE : DISSECT;
        else skill = down && p.level().getBlockState(p.blockPosition().below()).isAir() ? DOWN : QUICK;
        if (skill == DOWN && p.level().getBlockState(p.blockPosition().below()).isAir() == false) skill = QUICK;
        cast(p, s, skill);
    }

    /** Left click: sneak + left click with sukuna2 is 乱斩裂割. */
    public static void onLeftClick(ServerPlayer p) {
        State s = state(p);
        if (s.skill != 0 || !p.isAlive() || GojoSkills.isBusy(p) || YujiSkills.isBusy(p) || KatanaCombat.isBusy(p)) return;
        String id = ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem()).getPath();
        if (id.equals("sukuna2") && p.isShiftKeyDown() && SukunaTechnique.active(p)) cast(p, s, CARVE);
        if (id.equals("sukuna5") && !p.isShiftKeyDown() && SukunaTechnique.active(p)) {
            if (SukunaTechnique.chants(p) < 3) {
                p.displayClientMessage(BedrockText.parse("§l§c需要先 咒词吟唱 3 次 (" + SukunaTechnique.chants(p) + " / 3)"), true);
                return;
            }
            cast(p, s, WCS);
        }
    }

    private static void cast(ServerPlayer p, State s, int skill) {
        ServerLevel level = p.serverLevel();
        if (Energy.get(p) < COST[skill]) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.87F);
            return;
        }
        if (s.cd[GROUP[skill]] > 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.4F);
            p.displayClientMessage(BedrockText.parse("§l§c" + NAME[skill] + " 冷却中: §l§e" + s.cd[GROUP[skill]] + "§c."), false);
            return;
        }
        Energy.set(p, Energy.get(p) - COST[skill]);
        s.cd[GROUP[skill]] = CD[skill];
        s.skill = skill;
        s.t = 0;
        s.counterTarget = null;
        s.countered = false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        for (int i = 1; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
        if (s.frames > 0) s.frames--;
        ServerLevel level = p.serverLevel();
        tickProjs(p, s, level);
        if (s.skill == 0) return;
        if (!p.isAlive()) {
            s.skill = 0;
            return;
        }
        s.t++;
        int t = s.t, sk = s.skill;
        if (t == 1) {
            ModNet.playAnim(p, ANIM[sk], 0);
            ModNet.moveFx(p, FX[sk]);
        }
        Vec3 look = p.getLookAngle();
        switch (sk) {
            case QUICK -> {
                hold(p);
                if (t == 6) launch(p, s, level, DismantleSlashEntity.QUICK, p.position().add(look.scale(2.0D)).add(0, 1.5D + look.y * 2.0D, 0), look);
            }
            case DOWN -> {
                if (t <= 2) p.setDeltaMovement(p.getDeltaMovement().x, 0.6D, p.getDeltaMovement().z);
                if (t >= 9 && t <= 41) hold(p);
                if (t >= 37 && t <= 51) s.frames = 15;
                Vec3 f = p.position();
                if (t == 41) launch(p, s, level, DismantleSlashEntity.DOWN, f.add(0, -1, 0), new Vec3(0, -1, 0));
                if (t == 43) launch(p, s, level, DismantleSlashEntity.DOWN, f.add(0, -1, 3), new Vec3(0, -1, 0));
                if (t == 45) launch(p, s, level, DismantleSlashEntity.DOWN, f.add(-3, -1, 0), new Vec3(0, -1, 0));
            }
            case DISSECT -> {
                if (t == 2) push(p, look, -7.0D, 0.0D);
                if (t >= 4 && t <= 9) push(p, look, 2.0D, 0.0D);
                if (t >= 4 && t <= 20) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2, 1, false, false));
                if (t >= 21 && t <= 22) hold(p);
                if (t == 21) launch(p, s, level, DismantleSlashEntity.DISSECT, p.position().add(0, 1, 0).add(look), look);
            }
            case WIDE -> {
                if (t >= 2 && t <= 7) push(p, look, -3.5D, 0.0D);
                if (t == 12 || t == 13) push(p, look, 5.0D, 0.0D);
                if (t >= 17 && t <= 27) hold(p);
                if (t == 21) launch(p, s, level, DismantleSlashEntity.WIDE, p.position().add(0, 1, 0).add(look), look);
            }
            case RAPID -> {
                hold(p);
                if (t == 21) {
                    Proj pr = new Proj();
                    pr.owner = p.getUUID();
                    pr.pos = p.position().add(0, 1, 0).add(look.scale(3.0D));
                    pr.dir = look.normalize();
                    pr.kind = RAPID;
                    pr.left = 24;
                    s.projs.add(pr);
                }
            }
            case SPAM -> {
                if (t <= 12) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2, 1, false, false));
                if (t >= 3 && t <= 12) {
                    Proj pr = new Proj();
                    pr.owner = p.getUUID();
                    pr.pos = p.position().add(look.scale(2.0D)).add(0, 1.5D + look.y * 2.0D, 0);
                    pr.dir = look.normalize();
                    pr.kind = SPAM;
                    pr.left = 25;
                    s.projs.add(pr);
                }
            }
            case HEAVY -> {
                s.frames = Math.max(s.frames, t <= 20 ? 5 : 0);
                if (t <= 2) p.setDeltaMovement(p.getDeltaMovement().x, 1.0D, p.getDeltaMovement().z);
                if (t >= 3 && t <= 20) hold(p);
                if (t == 17) {
                    Proj pr = new Proj();
                    pr.owner = p.getUUID();
                    pr.pos = p.position().add(0, 1, 0).add(look.scale(3.0D));
                    pr.dir = look.normalize();
                    pr.kind = HEAVY;
                    pr.left = 24;
                    s.projs.add(pr);
                }
            }
            case HEAVY2 -> {
                s.frames = 5;
                if (t <= 6) p.setDeltaMovement(p.getDeltaMovement().x, -1.0D, p.getDeltaMovement().z);
                if (t <= 11) push(p, look, 3.0D, 0.0D);
                if (t >= 11 && t <= 56) hold(p);
                Vec3 h = new Vec3(look.x, 0, look.z);
                h = h.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : h.normalize();
                Vec3 left = new Vec3(h.z, 0, -h.x);
                if (t == 46) burst(p, level, p.position().add(left.scale(-9)).add(h.scale(13)));
                if (t == 47) burst(p, level, p.position().add(left.scale(-4)).add(h.scale(14)));
                if (t == 48) burst(p, level, p.position().add(h.scale(15)));
                if (t == 49) burst(p, level, p.position().add(left.scale(4)).add(h.scale(14)));
                if (t == 50) burst(p, level, p.position().add(left.scale(9)).add(h.scale(13)));
            }
            case CLEAVE -> {
                if (t == 5 || t == 6) {
                    if (targets(p, p.position().add(look.scale(2.5D)).add(0, 0.9D, 0), 4.0D).isEmpty()) push(p, look, 1.0D, 0.0D);
                }
                if (t == 5) {
                    List<LivingEntity> hit = targets(p, p.position().add(look.scale(2.5D)).add(0, 0.9D, 0), 2.49D);
                    for (LivingEntity e : hit) {
                        GojoTechEntity.hurt(level, p, e, 6.0F);
                        GojoTechEntity.stun(e, 15);
                    }
                    if (!hit.isEmpty()) SukunaTechnique.addFuga(p, 3);
                }
                if (t >= 3 && t <= 4) hold(p);
            }
            case COUNTER -> {
                if (t >= 2 && t <= 15) hold(p);
                if (t >= 5 && t <= 15) s.frames = 4;
                LivingEntity ct = s.counterTarget == null ? null : (level.getEntity(s.counterTarget) instanceof LivingEntity l && l.isAlive() ? l : null);
                if (t == 9 && ct == null) GojoTechEntity.stun(p, 10);
                if (t == 9 && ct != null) {
                    s.countered = true;
                    GojoTechEntity.stun(ct, 15);
                    ModNet.playAnim(p, "cleave_counter2", 0);
                }
                if (s.countered && ct != null && t >= 10 && t <= 15) {
                    Vec3 at = p.position().add(look.scale(1.0D)).add(0, 0.15D, 0);
                    ct.teleportTo(at.x, at.y, at.z);
                    ct.setDeltaMovement(Vec3.ZERO);
                    GojoTechEntity.stun(ct, 25);
                    if (t >= 11 && t <= 14) GojoTechEntity.hurt(level, p, ct, 2.0F);
                }
            }
            case LAYERED -> {
                if (t <= 4) p.setDeltaMovement(p.getDeltaMovement().x, -1.0D, p.getDeltaMovement().z);
                if (t <= 6) push(p, look, 2.0D, 0.0D);
                if (t >= 6 && t <= 22) s.frames = 5;
                Vec3 c = p.position().add(look.scale(2.25D)).add(0, 0.9D, 0);
                if (t >= 18 && t <= 26) hold(p);
                if (t >= 7) for (LivingEntity e : targets(p, c, 2.249D)) {
                    e.setDeltaMovement(Vec3.ZERO);
                }
                if (t == 16) {
                    List<LivingEntity> hit = targets(p, c, 2.249D);
                    for (LivingEntity e : hit) {
                        GojoTechEntity.hurt(level, p, e, 33.0F);
                        GojoTechEntity.stun(e, 35);
                    }
                    if (!hit.isEmpty()) SukunaTechnique.addFuga(p, 2);
                }
            }
            case SPIDER -> {
                if (t >= 6 && t <= 36) hold(p);
                if (t >= 6 && t <= 37) s.frames = 5;
                if (t == 31) {
                    List<LivingEntity> hit = targets(p, p.position(), 15.0D);
                    for (LivingEntity e : hit) {
                        GojoTechEntity.hurt(level, p, e, 30.0F);
                        GojoTechEntity.stun(e, 35);
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 2, false, false));
                        SukunaTechnique.addFuga(p, 15);
                    }
                    BedrockFxEntity.play(level, "dismantle_down_land", p.getX(), p.getY(), p.getZ(), p.getYRot());
                }
            }
            case CARVE -> {
                if (t <= 160) s.frames = 5;   // carving_combo (1.84.5): Frames from move 31
                if (t >= 160 && t <= 191) hold(p);
                if (t == 4 || t == 5) push(p, look, 2.0D, 0.0D);
                int[] hm = {21, 31, 36, 41, 61, 86};
                float[] hd = {5, 2, 2, 2, 5, 5};
                Vec3 c = p.position().add(look.scale(4.0D)).add(0, 0.9D, 0);
                for (int i = 0; i < hm.length; i++) {
                    if (t == hm[i]) {
                        List<LivingEntity> hit = targets(p, c, 6.0D);
                        for (LivingEntity e : hit) {
                            GojoTechEntity.hurt(level, p, e, hd[i]);
                            GojoTechEntity.stun(e, 25);
                        }
                        if (!hit.isEmpty()) SukunaTechnique.addFuga(p, 5);
                    }
                }
                if (t >= 101 && t <= 159) {
                    hold(p);
                    for (LivingEntity e : targets(p, p.position().add(0, 1, 0), 6.0D)) GojoTechEntity.hurt(level, p, e, 1.0F);
                }
            }
            case CHANT -> {
                if (t == 1 && SukunaTechnique.chants(p) >= 3) {
                    p.displayClientMessage(BedrockText.parse("§l§4咒词已吟唱完毕..."), false);
                    Energy.set(p, Energy.get(p) + 200);
                    s.cd[GROUP[sk]] = 0;
                    s.skill = 0;
                    return;
                }
                if (t <= 20) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 2, false, false));
                if (t == 11) {
                    SukunaTechnique.addChant(p);
                    level.playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.6F);
                }
            }
            case WCS -> {
                if (t <= 30) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2, 25, false, false));
                if (t == 21) {
                    slashLine(p, level, p.getEyePosition(), look, 7.0D, 80.0D, 6.99D);
                    SukunaTechnique.resetChants(p);
                }
            }
            case WS1, WS2 -> {
                if (t <= 17) for (LivingEntity e : targets(p, p.position(), 5.0D)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 11, false, false));
                if (t <= 2 || t >= 98 && t <= 106) hold(p);
                if (t == 103) {
                    for (int i = 1; i <= 5; i++) slashAt(p, level, p.position().add(look.scale(7.5D * i)).add(0, 1.0D, 0), 7.49D);
                    SukunaTechnique.resetChants(p);
                }
            }
            default -> s.skill = 0;
        }
        if (t >= N[sk] && s.skill == sk) {
            s.skill = 0;
            s.t = 0;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.frames > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (s.skill == COUNTER && s.t >= 5 && s.t <= 9 && event.getSource().getEntity() instanceof LivingEntity a && a != p) s.counterTarget = a.getUUID();
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void hold(ServerPlayer p) {
        p.setDeltaMovement(0.0D, 0.0D, 0.0D);
        p.fallDistance = 0.0F;
        p.hurtMarked = true;
    }

    /** {@code scriptevent ds:knockback h v}: applyKnockback along the view direction (negative = away). */
    private static void push(ServerPlayer p, Vec3 look, double h, double v) {
        Vec3 d = new Vec3(look.x, 0.0D, look.z);
        if (d.lengthSqr() < 1.0E-6D) return;
        d = d.normalize().scale(h);
        p.setDeltaMovement(d.x, p.getDeltaMovement().y + v, d.z);
        p.hurtMarked = true;
    }

    private static void launch(ServerPlayer p, State s, ServerLevel level, int kind, Vec3 pos, Vec3 dir) {
        DismantleSlashEntity.launch(level, p, kind, pos, dir);
    }

    private static boolean valid(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof Player pl) {
            if (pl.isCreative() || pl.isSpectator()) return false;
            return !hasFrames(pl) && !GojoSkills.hasFrames(pl) && !YujiSkills.hasFrames(pl);
        }
        return true;
    }

    private static List<LivingEntity> targets(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> valid(p, e) && e.position().distanceTo(c) <= r + e.getBbWidth() * 0.5D);
    }

    /** wcs_damage3: 250 damage and Stun 45 to everything within r of every point of the line (the function calls itself 7 blocks further). */
    private static void slashLine(ServerPlayer p, ServerLevel level, Vec3 from, Vec3 dir, double step, double max, double r) {
        for (double d = step; d <= max; d += step) slashAt(p, level, from.add(dir.scale(d)), r);
    }

    private static void slashAt(ServerPlayer p, ServerLevel level, Vec3 at, double r) {
        for (LivingEntity e : targets(p, at, r)) {
            GojoTechEntity.hurt(level, p, e, 250.0F);
            GojoTechEntity.stun(e, 45);
        }
    }

    private static void burst(ServerPlayer p, ServerLevel level, Vec3 at) {
        for (LivingEntity t : targets(p, at, 11.0D)) {
            GojoTechEntity.hurt(level, p, t, 100.0F); // max_dismantle1_land (1.84.5: 80 -> 100)
            GojoTechEntity.obliterateIfLow(level, p, t, 100.0F);
            GojoTechEntity.stun(t, 40);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 2, false, false));
        }
        BedrockFxEntity.play(level, "dismantle_down_land", at.x, at.y, at.z, p.getYRot());
    }

    /** ds:rapid_dismantle / ds:heavy_dismantle (7 blocks per tick, Deleter 24 until 11) and ds:dismantle_quick (5 blocks per tick). */
    private static void tickProjs(ServerPlayer p, State s, ServerLevel level) {
        for (int i = s.projs.size() - 1; i >= 0; i--) {
            Proj pr = s.projs.get(i);
            boolean done = false;
            if (pr.kind == SPAM) {
                for (int k = 0; k < 5 && !done; k++) {
                    Vec3 n = pr.pos.add(pr.dir);
                    BlockPos bp = BlockPos.containing(n);
                    if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                        done = true;
                        break;
                    }
                    pr.pos = n;
                    List<LivingEntity> hit = targets(p, pr.pos, 2.5D);
                    if (!hit.isEmpty()) {
                        for (LivingEntity t : hit) {
                            GojoTechEntity.hurt(level, p, t, 5.0F);   // dismantle_quick_land (1.84.5): no Stun 5 any more, low health is obliterated
                            GojoTechEntity.obliterateIfLow(level, p, t, 5.0F);
                        }
                        BedrockFxEntity.play(level, "dismantle_land", pr.pos.x, pr.pos.y, pr.pos.z, p.getYRot());
                        done = true;
                    }
                }
                if (--pr.left <= 0) done = true;
            } else {
                double speed = pr.kind == HEAVY ? 7.2D : 7.0D;
                boolean blocked = false;
                if (pr.left >= 11) {
                    int steps = (int) Math.ceil(speed);
                    for (int k = 0; k < steps; k++) {
                        Vec3 n = pr.pos.add(pr.dir.scale(speed / steps));
                        BlockPos bp = BlockPos.containing(n.add(pr.dir));
                        if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                            blocked = true;
                            break;
                        }
                        pr.pos = n;
                        if (pr.kind == HEAVY) {
                            for (LivingEntity t : targets(p, pr.pos, 15.0D)) {
                                if (!pr.hit.add(t.getUUID())) continue;
                                GojoTechEntity.hurt(level, p, t, 125.0F);   // heavy_dismantle_land (1.84.5: 60 -> 125)
                                GojoTechEntity.obliterateIfLow(level, p, t, 125.0F);
                                GojoTechEntity.stun(t, 40);
                                BedrockFxEntity.play(level, "dissect_land", t.getX(), t.getY(), t.getZ(), p.getYRot());
                            }
                        }
                    }
                }
                pr.left--;
                if (blocked) pr.left = Math.min(pr.left, 11);
                if (pr.left < 11 || blocked) {
                    if (pr.kind == RAPID) {
                        for (LivingEntity t : targets(p, pr.pos, 8.0D)) {
                            GojoTechEntity.hurt(level, p, t, 60.0F);   // rapid_dismantle_land (1.84.5: 40 -> 60)
                            GojoTechEntity.stun(t, 40);
                        }
                    }
                    BedrockFxEntity.play(level, "dismantle_down_land", pr.pos.x, pr.pos.y, pr.pos.z, p.getYRot());
                    done = true;
                }
            }
            if (done) s.projs.remove(i);
        }
    }
}
