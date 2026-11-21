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
    private static final int QUICK = 1, DOWN = 2, DISSECT = 3, WIDE = 4, RAPID = 5, SPAM = 6, HEAVY = 7, HEAVY2 = 8;
    private static final int[] N = {0, 11, 66, 36, 41, 31, 13, 31, 66};
    private static final int[] CD = {0, 50, 480, 150, 150, 800, 200, 1200, 1200};
    private static final int[] COST = {0, 50, 50, 75, 75, 200, 200, 200, 200};
    private static final String[] NAME = {"", "解-快斩", "空中解", "解", "后撤宽斩", "集连裂斩", "连斩", "重踢断斩", "重踢断斩"};
    private static final String[] ANIM = {"", "dismantle1", "dismantle_downwards", "dissect", "dismantle_backwards", "rapid_dismantle",
            "dismantle_spam", "heavy_dismantle", "heavy_dismantle2"};
    private static final String[] FX = {"", "sukuna_ab1", "sukuna_ab1b", "sukuna_ab1d", "sukuna_ab1e", "sukuna_ab2", "sukuna_ab2b",
            "sukuna_ab2d", "sukuna_ab2e"};
    /** the ability scoreboards share cooldowns: ab1 (1,2), ab3 (3,4), ab4/ab4 (5,6), ab5 (7,8). */
    private static final int[] GROUP = {0, 1, 1, 3, 3, 5, 5, 7, 7};

    private static final class Proj {
        UUID owner;
        Vec3 pos, dir;
        int kind, left;
        final Set<UUID> hit = new HashSet<>();
    }

    private static final class State {
        int skill, t, frames;
        final int[] cd = new int[9];
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
        return s != null && s.frames > 0;
    }

    public static void onUse(ServerPlayer p, String item) {
        if (!item.equals("sukuna1")) return;
        State s = state(p);
        if (s.skill != 0 || !p.isAlive() || GojoSkills.isBusy(p) || YujiSkills.isBusy(p) || KatanaCombat.isBusy(p)) return;
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§c需要宿傩掌控身体"), true);
            return;
        }
        boolean sneak = p.isShiftKeyDown(), air = !p.onGround(), sprint = p.isSprinting();
        boolean down = p.getXRot() > 60.0F;
        int skill;
        if (sneak) skill = air ? SPAM : RAPID;
        else if (air && sprint) skill = down ? HEAVY2 : HEAVY;
        else if (air) skill = p.zza < 0.0F ? WIDE : DISSECT;
        else skill = down && p.level().getBlockState(p.blockPosition().below()).isAir() ? DOWN : QUICK;
        if (skill == DOWN && p.level().getBlockState(p.blockPosition().below()).isAir() == false) skill = QUICK;
        cast(p, s, skill);
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
        if (s != null && s.frames > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
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

    private static void burst(ServerPlayer p, ServerLevel level, Vec3 at) {
        for (LivingEntity t : targets(p, at, 11.0D)) {
            GojoTechEntity.hurt(level, p, t, 80.0F);
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
                            GojoTechEntity.hurt(level, p, t, 5.0F);
                            GojoTechEntity.stun(t, 5);
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
                                GojoTechEntity.hurt(level, p, t, 60.0F);
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
                            GojoTechEntity.hurt(level, p, t, 40.0F);
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
