package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.BedrockFxEntity;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * Yuji Itadori's skills for a player ({@code functions/yuji/*.mcfunction}): the item 黑闪战斗风格 ({@code ds:black_fists}), 解-灵魂
 * ({@code ds:soul_shrine1}) and 领域展开 『伏魔御佛龛』 ({@code ds:soul_shrine2}).
 *
 * <p>The addon runs a move as {@code move} counting down from N; {@code t} here counts ticks since the click, so the addon's
 * {@code move=m} is {@code t = N - m}. Moves that connect jump to a second phase ({@code move=1001..}); {@code c} counts the
 * ticks of that phase (1001 - m). The finishing cutscenes (the target nearly dead) are left for the cutscene stage.
 *
 * <pre>
 *  black_fists  click 激连差击 N36 cd200 | sneak 双重连踢 N43 cd300 | jump 震地跃击 N87 cd420 | sneak+left 疾驰追击 N16 cd800
 *               jump+left 力之争竞 N16 cd2400 | sneak+jump 「黑闪」(final_flash) N16 cd4200
 *  soul_shrine1 click 解-灵魂 N26 75/cd200 | sneak 断肢裂解 N16 75/cd200 | jump 缭闪连斩 N21 400/cd900
 *               sneak+left 切石掷击 N36 200/cd200 | sneak+jump 裂岩轰闪 N31 600/cd2000
 *  soul_shrine2 领域展开 500/cd12000
 * </pre>
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YujiSkills {
    public static final int FIERCE = 1, TWIN = 2, QUAKE = 3, PURSUIT = 4, VENGEANCE = 5, FLASH = 6;
    public static final int SOUL1 = 7, SOUL2 = 8, SOUL3 = 9, SOUL4 = 10, SOUL5 = 11, DOMAIN = 12;
    private static final int[] CD = {0, 200, 300, 420, 800, 2400, 4200, 200, 200, 900, 200, 2000, 12000};
    private static final int[] COST = {0, 0, 0, 0, 0, 0, 0, 75, 75, 400, 200, 600, 500};
    private static final String[] NAME = {"", "激连差击", "双重连踢", "震地跃击", "疾驰追击", "力之争竞", "「黑闪」", "解-灵魂", "断肢裂解", "缭闪连斩",
            "切石掷击", "裂岩轰闪", "『伏魔御佛龛』"};
    private static final String[] ANIM = {"", "black_fists.ab1", "black_fists.ab2", "black_fists.ab3", "black_fists.ab4", "black_fists.ab5",
            "black_fists.ab6", "soul_shrine.ab1", "soul_shrine.ab2", "soul_shrine.ab3", "soul_shrine.ab4", "soul_shrine.ab5", "yuji_domain"};
    private static final String[] FX = {"", "yuji_fierce", "yuji_twin", "yuji_quake_air", "yuji_pursuit", "yuji_vengeance", "yuji_flash",
            "yuji_soul1", "yuji_soul2", "yuji_soul3", "yuji_soul4", "yuji_soul5", ""};

    private static final class Rock {
        UUID owner;
        Vec3 pos, dir;
        int life = 40;
    }

    private static final class State {
        int skill, t, c, phase, frames, qte, domainLeft, domainT;
        boolean bf, bfDecided;
        UUID target;
        final int[] cd = new int[13];
        final Set<UUID> frozen = new HashSet<>();
        final List<Rock> rocks = new ArrayList<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static {
        Energy.setBusy(YujiSkills::isBusy);
    }

    private YujiSkills() {}

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

    // ------------------------------------------------------------------ input

    public static void onUse(ServerPlayer p, String item) {
        State s = state(p);
        if (!p.isAlive()) return;
        if (s.skill != 0) {
            if (item.equals("black_fists")) s.qte = s.t;   // the QTE of the Black Flash: a right click while the move runs
            return;
        }
        if (GojoSkills.isBusy(p) || KatanaCombat.isBusy(p)) return;
        boolean sneak = p.isShiftKeyDown(), jump = !p.onGround();
        if (item.equals("black_fists")) {
            cast(p, s, sneak && jump ? FLASH : jump ? QUAKE : sneak ? TWIN : FIERCE);
        } else if (item.equals("soul_shrine1")) {
            cast(p, s, sneak && jump ? SOUL5 : jump ? SOUL3 : sneak ? SOUL2 : SOUL1);
        } else if (item.equals("soul_shrine2")) {
            cast(p, s, DOMAIN);
        }
    }

    /** Left click (the addon's {@code punch} / {@code detect_left}) while holding the fists or 解-灵魂. */
    public static void onLeftClick(ServerPlayer p) {
        State s = state(p);
        if (!p.isAlive() || s.skill != 0 || GojoSkills.isBusy(p) || KatanaCombat.isBusy(p)) return;
        boolean sneak = p.isShiftKeyDown(), jump = !p.onGround();
        String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem()).getPath();
        if (id.equals("black_fists")) {
            if (sneak && !jump) cast(p, s, PURSUIT);
            else if (jump && !sneak) cast(p, s, VENGEANCE);
        } else if (id.equals("soul_shrine1") && sneak) {
            cast(p, s, SOUL4);
        }
    }

    private static void cast(ServerPlayer p, State s, int skill) {
        ServerLevel level = p.serverLevel();
        if (COST[skill] > 0 && Energy.get(p) < COST[skill]) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.87F);
            return;
        }
        if (s.cd[skill] > 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.4F);
            p.displayClientMessage(BedrockText.parse("§l§c" + NAME[skill] + " 冷却中: §l§e" + s.cd[skill] + "§c."), false);
            return;
        }
        if (COST[skill] > 0) Energy.set(p, Energy.get(p) - COST[skill]);
        s.cd[skill] = CD[skill];
        s.skill = skill;
        s.t = 0;
        s.c = 0;
        s.phase = 0;
        s.bf = false;
        s.bfDecided = false;
        s.qte = -1;
        s.target = null;
    }

    private static void end(State s) {
        s.skill = 0;
        s.t = 0;
        s.c = 0;
        s.phase = 0;
        s.target = null;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (com.jujutsuenhancement.bfn.BfnRuntime.isEnabled()) return;
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        for (int i = 1; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
        if (s.frames > 0) s.frames--;
        ServerLevel level = p.serverLevel();
        tickRocks(p, s, level);
        domainTick(p, s, level);
        if (s.skill == 0) return;
        if (!p.isAlive()) {
            end(s);
            return;
        }
        s.t++;
        if (s.t == 1) {
            if (!ANIM[s.skill].isEmpty()) ModNet.playAnim(p, ANIM[s.skill], 0);
            if (!FX[s.skill].isEmpty()) ModNet.moveFx(p, FX[s.skill]);
        }
        switch (s.skill) {
            case FIERCE -> fierce(p, s, level);
            case TWIN -> twin(p, s, level);
            case QUAKE -> quake(p, s, level);
            case PURSUIT -> pursuit(p, s, level);
            case VENGEANCE -> vengeance(p, s, level);
            case FLASH -> grabKill(p, s, level, 11, 22, 281, 1.25D, 200.0F, "yuji_flash2", 4200, "black_fists.ab6", 5.0F);
            case SOUL1 -> soul1(p, s, level);
            case SOUL2 -> soul2(p, s, level);
            case SOUL3 -> soul3(p, s, level);
            case SOUL4 -> soul4(p, s, level);
            case SOUL5 -> grabKill(p, s, level, 19, 20, 126, 1.4D, 200.0F, "yuji_soul5b", 2000, "soul_shrine.ab5b", 0.0F);
            case DOMAIN -> domainCast(p, s, level);
            default -> end(s);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        State s = STATES.get(event.getEntity().getUUID());
        if (s != null && event.getEntity() instanceof ServerPlayer p) releaseFrozen(p.serverLevel(), s);
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (com.jujutsuenhancement.bfn.BfnRuntime.isEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.frames > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean valid(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof Player pl) {
            if (pl.isCreative() || pl.isSpectator()) return false;
            return !hasFrames(pl) && !GojoSkills.hasFrames(pl);
        }
        return true;
    }

    private static List<LivingEntity> targets(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> valid(p, e) && e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).distanceTo(c) <= r + e.getBbWidth() * 0.5D);
    }

    /** {@code positioned ^^^d} from the caster's feet along its view. */
    private static Vec3 front(ServerPlayer p, double d) {
        return p.position().add(p.getLookAngle().scale(d)).add(0.0D, 0.9D, 0.0D);
    }

    private static List<LivingEntity> strike(ServerPlayer p, double fwd, double r, float dmg, int stun) {
        List<LivingEntity> hit = targets(p, front(p, fwd), r);
        for (LivingEntity t : hit) {
            GojoTechEntity.hurt(p.serverLevel(), p, t, dmg);
            if (stun > 0) GojoTechEntity.stun(t, stun);
        }
        return hit;
    }

    private static void holdStill(ServerPlayer p) {
        Vec3 m = p.getDeltaMovement();
        p.setDeltaMovement(0.0D, Math.min(0.0D, m.y), 0.0D);
        p.hurtMarked = true;
    }

    /** {@code scriptevent ds:knockback v}: a push of the caster along its view. */
    private static void lunge(ServerPlayer p, double v) {
        Vec3 look = p.getLookAngle();
        Vec3 h = new Vec3(look.x, 0.0D, look.z);
        if (h.lengthSqr() < 1.0E-6D) return;
        h = h.normalize().scale(v);
        p.setDeltaMovement(h.x, p.getDeltaMovement().y, h.z);
        p.hurtMarked = true;
    }

    private static void place(LivingEntity t, Vec3 pos, ServerPlayer facing) {
        float yaw = (float) (Mth.atan2(-(facing.getX() - pos.x), facing.getZ() - pos.z) * 57.29578D);
        if (t instanceof ServerPlayer sp) {
            sp.connection.teleport(pos.x, pos.y, pos.z, yaw, 0.0F);
        } else {
            t.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            t.setYHeadRot(yaw);
        }
        t.setDeltaMovement(Vec3.ZERO);
        t.fallDistance = 0.0F;
    }

    private static Vec3 ahead(ServerPlayer p, double d) {
        Vec3 look = p.getLookAngle();
        Vec3 h = new Vec3(look.x, 0.0D, look.z);
        h = h.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : h.normalize();
        return p.position().add(h.scale(d));
    }

    private static LivingEntity target(ServerPlayer p, State s) {
        if (s.target == null) return null;
        Entity e = p.serverLevel().getEntity(s.target);
        return e instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    private static LivingEntity nearest(ServerPlayer p, double fwd, double r) {
        LivingEntity best = null;
        double bd = 1.0E9D;
        Vec3 c = front(p, fwd);
        for (LivingEntity t : targets(p, c, r)) {
            double d = t.position().add(0.0D, t.getBbHeight() * 0.5D, 0.0D).distanceToSqr(c);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    private static void knockdown(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2, false, false));
    }

    private static void title(ServerPlayer p, String text) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 4, 0));
        p.connection.send(new ClientboundSetTitleTextPacket(BedrockText.parse("\n\n\n\n\n\n\n\n\n\n\n\n§r§f§l< " + text + " §l§f>\n\n")));
    }

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** A Black Flash connected: the counter of Black Flashes and of the special-grade ones that raise the technique. */
    private static void blackFlashed(ServerPlayer p, List<LivingEntity> hit) {
        CompoundTag d = data(p);
        d.putInt("jje_bfcount", d.getInt("jje_bfcount") + 1);
        for (LivingEntity t : hit) {
            if (t instanceof Player || t.getMaxHealth() >= 100.0F) {
                d.putInt("jje_bfspecial", d.getInt("jje_bfspecial") + 1);
                if (d.getInt("jje_bfspecial") == 10) {
                    p.displayClientMessage(BedrockText.parse("§l§e你感觉你对 §l§4御厨子 §l§e的潜能正在被激发!\n(已满足术式升级条件)"), false);
                }
                break;
            }
        }
    }

    // ------------------------------------------------------------------ 激连差击 (black_fists.ab1, N 36)

    private static void fierce(ServerPlayer p, State s, ServerLevel level) {
        holdStill(p);
        if (s.t == 6 || s.t == 7 || s.t == 12 || s.t == 13) lunge(p, 0.5D);
        if (s.t == 7 || s.t == 13) strike(p, 2.5D, 2.45D, 6.0F, 15);
        if (s.t >= 19 && s.t <= 23) s.frames = 7;
        if (s.t == 21 && !s.bfDecided) {
            s.bfDecided = true;
            s.bf = s.qte >= 19 && s.qte <= 21;
            ModNet.moveFx(p, s.bf ? "yuji_fierce_b" : "yuji_fierce_n");
        }
        if (s.t >= 17 && s.t <= 21) title(p, s.bf ? "§a 右键(长按)" : "§e右键(长按)");
        if (s.t == 21 && s.qte >= 14 && s.qte <= 18 && !s.bf) title(p, "§4失败");
        if (s.t == 23) {
            List<LivingEntity> hit = strike(p, s.bf ? 3.5D : 2.5D, s.bf ? 3.45D : 2.45D, s.bf ? 16.0F : 12.0F, s.bf ? 30 : 25);
            if (s.bf && !hit.isEmpty()) {
                blackFlashed(p, hit);
                s.cd[FIERCE] = 5;
                for (LivingEntity t : hit) t.setSecondsOnFire(0);
            }
        }
        if (s.t >= 36) end(s);
    }

    // ------------------------------------------------------------------ 双重连踢 (black_fists.ab2, N 43)

    private static void twin(ServerPlayer p, State s, ServerLevel level) {
        holdStill(p);
        if (s.t <= 23) s.frames = 4;
        if (s.t == 13) {
            LivingEntity t = nearest(p, 2.0D, 1.95D);
            if (t == null) {
                s.frames = 0;
                end(s);
                return;
            }
            s.target = t.getUUID();
            GojoTechEntity.stun(t, 40);
        }
        LivingEntity t = target(p, s);
        if (s.t >= 13 && s.t <= 37 && t != null) {
            place(t, ahead(p, s.t >= 15 ? 1.0D : 2.0D), p);
            GojoTechEntity.stun(t, 5);
        }
        if (s.t == 33 && t != null) knockdown(t, 40);
        if (s.t == 37 && t != null) {
            GojoTechEntity.hurt(level, p, t, 20.0F);
            BedrockFxEntity.play(level, "gojo_blitz_hit", t.getX(), t.getY(), t.getZ(), p.getYRot());
        }
        if (s.t >= 43) end(s);
    }

    // ------------------------------------------------------------------ 震地跃击 (black_fists.ab3, N 87)

    private static void quake(ServerPlayer p, State s, ServerLevel level) {
        if (s.phase == 0) {
            if (s.t == 1) {
                Vec3 look = p.getLookAngle();
                Vec3 h = new Vec3(look.x, 0.0D, look.z);
                h = h.lengthSqr() < 1.0E-6D ? Vec3.ZERO : h.normalize().scale(0.9D);
                p.setDeltaMovement(h.x, 0.75D, h.z);       // scriptevent ds:leapforwards2
                p.hurtMarked = true;
            }
            if (s.t >= 3 && p.onGround() || s.t > 120) {
                s.phase = 1;
                s.c = 0;
                s.frames = 75;
                ModNet.moveFx(p, "yuji_quake_land");
            }
            return;
        }
        s.c++;
        holdStill(p);
        // landing = move 82: the hits at move 79, 69 and 19
        if (s.c == 4) quakeHit(p, level, 6.0D, 10.0F, 25);
        if (s.c == 14) quakeHit(p, level, 6.0D, 10.0F, 60);
        if (s.c == 64) quakeHit(p, level, 8.0D, 20.0F, 30);
        if (s.c >= 82) end(s);
    }

    private static void quakeHit(ServerPlayer p, ServerLevel level, double r, float dmg, int stun) {
        for (LivingEntity t : targets(p, p.position(), r)) {
            GojoTechEntity.hurt(level, p, t, dmg);
            GojoTechEntity.stun(t, stun);
            knockdown(t, 25);
        }
        BedrockFxEntity.play(level, "yuji_rock_burst", p.getX(), p.getY(), p.getZ(), p.getYRot());
    }

    // ------------------------------------------------------------------ 疾驰追击 (black_fists.ab4, N 16 then the chase)

    private static void pursuit(ServerPlayer p, State s, ServerLevel level) {
        if (s.phase == 0) {
            holdStill(p);
            if (s.t >= 4 && s.t <= 6) lunge(p, 0.6D);
            if (s.t == 6) {
                LivingEntity t = nearest(p, 2.5D, 2.45D);
                if (t != null) {
                    GojoTechEntity.hurt(level, p, t, 2.0F);
                    GojoTechEntity.stun(t, 45);
                    s.target = t.getUUID();
                    s.phase = 1;
                    s.c = 0;
                    s.frames = 56;
                    ModNet.playAnim(p, "black_fists.ab4b", 0);
                    ModNet.moveFx(p, "yuji_pursuit2");
                }
            }
            if (s.t >= 16 && s.phase == 0) end(s);
            return;
        }
        s.c++;
        LivingEntity t = target(p, s);
        holdStill(p);
        if (t != null) {
            place(t, ahead(p, 2.0D), p);
            GojoTechEntity.stun(t, 25);
        }
        if (s.c == 34 && t != null) GojoTechEntity.hurt(level, p, t, 12.0F);
        if (s.c == 46 && t != null) {
            GojoTechEntity.hurt(level, p, t, 8.0F);
            GojoTechEntity.stun(t, 45);
            BedrockFxEntity.play(level, "gojo_blitz_hit", t.getX(), t.getY(), t.getZ(), p.getYRot());
        }
        if (s.c >= 47 || t == null) end(s);
    }

    // ------------------------------------------------------------------ 力之争竞 (black_fists.ab5, N 16 then the barrage)

    private static final int[] BARRAGE = {3, 7, 12, 15, 16, 17, 19, 21, 27};

    private static void vengeance(ServerPlayer p, State s, ServerLevel level) {
        if (s.phase == 0) {
            holdStill(p);
            if (s.t >= 4) for (LivingEntity t : targets(p, p.position(), 30.0D)) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 11, 9, false, false));
            if (s.t == 11) {
                LivingEntity t = nearest(p, 2.0D, 1.95D);
                if (t != null) {
                    s.target = t.getUUID();
                    s.phase = 1;
                    s.c = 0;
                    ModNet.moveFx(p, "yuji_vengeance2");
                }
            }
            if (s.t >= 16 && s.phase == 0) end(s);
            return;
        }
        s.c++;
        LivingEntity t = target(p, s);
        holdStill(p);
        if (t != null) {
            place(t, ahead(p, 2.0D), p);
            GojoTechEntity.stun(t, 15);
        }
        for (int b : BARRAGE) if (s.c == b && t != null) GojoTechEntity.hurt(level, p, t, 6.0F);
        if (s.c >= 52 || t == null) end(s);
    }

    // ------------------------------------------------------------------ 黑闪 (final_flash) and 裂岩轰闪: grab the target, hold it, kill

    private static void grabKill(ServerPlayer p, State s, ServerLevel level, int grabAt, int heldFrom, int killAt, double dist, float dmg,
                                 String fx, int cd, String anim, float shake) {
        if (s.phase == 0) {
            holdStill(p);
            if (s.t >= 19 && s.t <= 31 && s.skill == SOUL5) s.frames = 8;
            if (s.t == grabAt) {
                LivingEntity t = nearest(p, s.skill == SOUL5 ? 3.97D : 2.0D, s.skill == SOUL5 ? 3.9D : 1.95D);
                if (t != null) {
                    s.target = t.getUUID();
                    s.phase = 1;
                    s.c = 0;
                    ModNet.playAnim(p, anim, 0);
                    ModNet.moveFx(p, fx);
                }
            }
            if (s.t >= 16 && s.skill == FLASH && s.phase == 0 || s.t >= 31 && s.phase == 0) end(s);
            return;
        }
        s.c++;
        s.frames = 6;
        LivingEntity t = target(p, s);
        holdStill(p);
        if (t != null) {
            place(t, ahead(p, s.c >= heldFrom ? dist : 2.0D), p);
            GojoTechEntity.stun(t, 25);
        }
        if (s.c == killAt && t != null) {
            GojoTechEntity.hurt(level, p, t, dmg);
            BedrockFxEntity.play(level, "yuji_rock_burst", t.getX(), t.getY(), t.getZ(), p.getYRot());
        }
        if (s.c >= killAt + 12 || t == null && s.c > 3) end(s);
    }

    // ------------------------------------------------------------------ 解-灵魂 (soul_shrine.ab1, N 26)

    private static void soul1(ServerPlayer p, State s, ServerLevel level) {
        if (s.phase == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 2, false, false));
            if (s.t >= 10 && s.t <= 12) lunge(p, 0.5D);
            if (s.t == 11) {
                LivingEntity t = nearest(p, 2.5D, 2.45D);
                if (t != null) {
                    GojoTechEntity.hurt(level, p, t, 6.0F);
                    GojoTechEntity.stun(t, 30);
                    s.target = t.getUUID();
                    s.phase = 1;
                    s.c = 0;
                    ModNet.playAnim(p, "soul_shrine.ab1b", 0);
                    ModNet.moveFx(p, "yuji_soul1b");
                }
            }
            if (s.t >= 26 && s.phase == 0) end(s);
            return;
        }
        s.c++;
        holdStill(p);
        LivingEntity t = target(p, s);
        if (t != null && s.c <= 20) place(t, ahead(p, 1.0D), p);
        if (s.c == 5 && t != null) {
            GojoTechEntity.hurt(level, p, t, 20.0F);
            GojoTechEntity.stun(t, 30);
        }
        if (s.c == 6) p.teleportTo(ahead(p, 2.5D).x, p.getY(), ahead(p, 2.5D).z);
        if (s.c >= 31 || t == null && s.c > 6) end(s);
    }

    // ------------------------------------------------------------------ 断肢裂解 (soul_shrine.ab2, N 16)

    private static void soul2(ServerPlayer p, State s, ServerLevel level) {
        holdStill(p);
        if (s.t == 1) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
        if (s.t >= 2 && s.t <= 7) s.frames = 5;
        if (s.t == 8) {
            for (LivingEntity t : targets(p, p.position().add(0.0D, 0.9D, 0.0D), 4.5D)) {
                GojoTechEntity.hurt(level, p, t, 7.0F);
                GojoTechEntity.stun(t, 30);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
            }
        }
        if (s.t >= 16) end(s);
    }

    // ------------------------------------------------------------------ 缭闪连斩 (soul_shrine.ab3, N 21)

    private static void soul3(ServerPlayer p, State s, ServerLevel level) {
        if (s.phase == 0) {
            if (s.t <= 18) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2, 0, false, false));
            if (s.t >= 16) s.frames = 6;
            if (s.t == 15) {
                Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(8.1D));
                LivingEntity best = null;
                double bd = 1.0E9D;
                for (LivingEntity t : targets(p, c, 7.99D)) {
                    double d = t.position().distanceToSqr(c);
                    if (d < bd) {
                        bd = d;
                        best = t;
                    }
                }
                if (best != null) {
                    s.target = best.getUUID();
                    s.phase = 1;
                    s.c = 0;
                    ModNet.playAnim(p, "soul_shrine.ab3b", 0);
                    ModNet.moveFx(p, "yuji_soul3b");
                }
            }
            if (s.t >= 21 && s.phase == 0) end(s);
            return;
        }
        s.c++;
        s.frames = 6;
        LivingEntity t = target(p, s);
        if (t != null && s.c <= 40) {
            place(t, ahead(p, 0.25D), p);
            GojoTechEntity.stun(t, 30);
        }
        if (s.c == 7) {
            for (LivingEntity e : targets(p, p.position(), 6.0D)) {
                GojoTechEntity.hurt(level, p, e, 10.0F);
                GojoTechEntity.stun(e, 25);
                knockdown(e, 25);
            }
            BedrockFxEntity.play(level, "yuji_rock_burst", p.getX(), p.getY(), p.getZ(), p.getYRot());
        }
        if (s.c >= 11 && s.c <= 31 && t != null) GojoTechEntity.hurt(level, p, t, 2.0F);
        if (s.c >= 36 || t == null && s.c > 7) end(s);
    }

    // ------------------------------------------------------------------ 切石掷击 (soul_shrine.ab4, N 36)

    private static void soul4(ServerPlayer p, State s, ServerLevel level) {
        holdStill(p);
        if (s.t >= 18 && s.t <= 19) s.frames = 8;
        if (s.t == 19) {
            Rock r = new Rock();
            r.owner = p.getUUID();
            r.pos = p.position().add(0.0D, 1.0D, 0.0D).add(p.getLookAngle());
            r.dir = p.getLookAngle().normalize();
            s.rocks.add(r);
        }
        if (s.t >= 36) end(s);
    }

    /** ds:rock_throw: it flies 3 blocks per tick along the view, bursts (40 damage within 10) when it touches an entity or a block. */
    private static void tickRocks(ServerPlayer p, State s, ServerLevel level) {
        for (int i = s.rocks.size() - 1; i >= 0; i--) {
            Rock r = s.rocks.get(i);
            boolean burst = false;
            for (int k = 0; k < 3 && !burst; k++) {
                Vec3 n = r.pos.add(r.dir);
                if (!level.getBlockState(net.minecraft.core.BlockPos.containing(n)).isAir()
                        && !level.getBlockState(net.minecraft.core.BlockPos.containing(n)).getCollisionShape(level, net.minecraft.core.BlockPos.containing(n)).isEmpty()) {
                    burst = true;
                    break;
                }
                r.pos = n;
                if (!targets(p, r.pos, 3.5D).isEmpty()) burst = true;
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, r.pos.x, r.pos.y, r.pos.z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
            r.life--;
            if (burst || r.life <= 0) {
                for (LivingEntity t : targets(p, r.pos, 10.0D)) {
                    GojoTechEntity.hurt(level, p, t, 40.0F);
                    knockdown(t, 25);
                }
                BedrockFxEntity.play(level, "yuji_rock_burst", r.pos.x, r.pos.y, r.pos.z, p.getYRot());
                s.rocks.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ 领域展开 『伏魔御佛龛』

    private static void domainCast(ServerPlayer p, State s, ServerLevel level) {
        holdStill(p);
        if (s.t == 1) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4, false, false));
        s.frames = 4;
        if (s.t == 100) {
            s.domainLeft = 550;
            s.domainT = 0;
            p.sendSystemMessage(BedrockText.parse("§l§c领域展开 『伏魔御佛龛』"));
        }
        if (s.t >= 160) end(s);
    }

    /** yuji/yujis_domain: every 70 ticks the slash cuts everything within 50 for 20 and stuns it; everybody inside is held (Stun). */
    private static void domainTick(ServerPlayer p, State s, ServerLevel level) {
        if (s.domainLeft <= 0) return;
        s.domainLeft--;
        s.domainT++;
        if (!p.isAlive()) {
            s.domainLeft = 0;
            releaseFrozen(level, s);
            return;
        }
        for (LivingEntity e : targets(p, p.position(), 33.0D)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 99, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.JUMP, 6, 128, false, false));
            if (e instanceof Mob mob) {
                mob.setTarget(null);
                if (!mob.isNoAi()) {
                    mob.setNoAi(true);
                    s.frozen.add(mob.getUUID());
                }
            }
        }
        if (s.domainT % 70 == 0) {
            for (LivingEntity e : targets(p, p.position(), 50.0D)) {
                GojoTechEntity.hurt(level, p, e, 20.0F);
                GojoTechEntity.stun(e, 25);
                BedrockFxEntity.play(level, "yuji_domain_slash", e.getX(), e.getY(), e.getZ(), p.getYRot());
            }
        }
        if (s.domainLeft == 0) releaseFrozen(level, s);
    }

    private static void releaseFrozen(ServerLevel level, State s) {
        for (UUID id : s.frozen) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob) mob.setNoAi(false);
        }
        s.frozen.clear();
    }

    public static void resetCooldowns(Player p) {
        State s = STATES.get(p.getUUID());
        if (s != null) java.util.Arrays.fill(s.cd, 0);
    }
}
