package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.DomainShrineEntity;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.registry.ModEntities;
import com.jujutsuenhancement.stats.Energy;
import com.jujutsuenhancement.text.BedrockText;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 領域展開 『伏魔御厨子』, the item {@code ds:sukuna3} ({@code functions/sukuna/attacks/domains/*}).
 *
 * <pre>
 *  1 閉 (right click on the ground)      500 Energy, cd 12000 (domainCD)   the barrier of 33 blocks: 3 damage (override) within 40
 *  2 開 (sneak + right click, 15 fingers) 2500 Energy, cd 16000 (domainCD)  no barrier: 3 damage within 200
 *  3 不完全 (jump + right click, health at most 25 percent)  1500 Energy, cd 32000 (sukuna_ab12): like 開
 *  domainstart 121 -> 1: everything within 48 is gathered on the caster (infinity), the cutscene (domain 221, 281 for 3) starts at domainstart 46
 *  the domain entity is summoned during the cutscene: duration 331 + Energy (1: tick 71 of the cutscene), 600 + Energy (2: tick 71), 1000 (3: tick 57)
 *  each tick the caster pays 1 Energy once the cutscene is over; at 1 Energy the domain fades out (31 ticks)
 *  the damage counter: 0 -> hit everything, then random 1..4 ticks; the ticks at 1..3 give the 竈 meter +1
 *  a second click ends it ("领域已解除")
 * </pre>
 * The arena (y 301 platform), the domain clash, the Mahoraga cutscene and the terrain destruction belong to later stages;
 * in 閉 the barrier is a circle of 33 blocks that keeps everything that was inside it inside.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaDomain {
    private static final String[] NAME = {"", "闭", "开", "不完全"};

    private static final class D {
        int kind, s, c, left = -1, cnt, cd, cd12;
        boolean casting;
        Vec3 center = Vec3.ZERO;
        ServerLevel level;
        UUID visual;
        final Set<UUID> inside = new HashSet<>();
    }

    private static final Map<UUID, D> STATES = new HashMap<>();
    private static final java.util.Random RNG = new java.util.Random();

    private SukunaDomain() {}

    private static D state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new D());
    }

    /** The caster is in the cast / cutscene (held). */
    public static boolean casting(ServerPlayer p) {
        D d = STATES.get(p.getUUID());
        return d != null && d.casting;
    }

    public static boolean active(ServerPlayer p) {
        D d = STATES.get(p.getUUID());
        return d != null && d.left > 0;
    }

    private static int castEnd(D d) {
        return d.kind == 3 ? 280 : 220;
    }

    public static void onUse(ServerPlayer p) {
        D d = state(p);
        ServerLevel level = p.serverLevel();
        if (d.left > 0 && !d.casting && d.c >= castEnd(d)) { // domainactive 21.. and the item used again: the domain is dissolved
            if (d.left > 31) d.left = 31;
            p.sendSystemMessage(BedrockText.parse("§l§c领域已解除"));
            return;
        }
        if (d.casting || d.left > 0) return;
        if (!p.isAlive() || SukunaSkills.isBusy(p) || SukunaFuga.isBusy(p) || GojoSkills.isBusy(p) || YujiSkills.isBusy(p) || KatanaCombat.isBusy(p) || SukunaFists.isBusy(p)) return;
        if (!SukunaTechnique.active(p) || isStunned(p)) return;
        int kind;
        int cost;
        boolean air = !p.onGround();
        if (air) {
            if (!p.isCreative() && p.getHealth() > p.getMaxHealth() * 0.25F) return;
            kind = 3;
            cost = 1500;
        } else if (p.isShiftKeyDown()) {
            if (SukunaTechnique.fingers(p) < 15) {
                p.sendSystemMessage(BedrockText.parse("§l§4咒力输出&操控结界精度不足，使用开放式领域需要至少吃下 §l§c15 §l§4根手指以上"));
                return;
            }
            kind = 2;
            cost = 2500;
        } else {
            kind = 1;
            cost = 500;
        }
        int cd = kind == 3 ? d.cd12 : d.cd;
        if (Energy.get(p) < cost) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.06F, 0.88F);
            return;
        }
        if (cd > 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.06F, 0.6F);
            p.sendSystemMessage(BedrockText.parse("§l§c『伏魔御厨子』 冷却中: §l§e" + cd + "§c."));
            return;
        }
        Energy.set(p, Energy.get(p) - cost);
        if (kind == 3) d.cd12 = 32000;
        else d.cd = kind == 1 ? 12000 : 16000;
        d.kind = kind;
        d.s = 0;
        d.c = 0;
        d.left = -1;
        d.cnt = 0;
        d.casting = true;
        d.level = level;
        d.inside.clear();
        d.center = p.position();
    }

    private static boolean isStunned(Player p) {
        net.minecraft.world.effect.MobEffectInstance e = p.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        return e != null && e.getAmplifier() >= 6;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        D d = STATES.get(p.getUUID());
        if (d == null) return;
        if (d.cd > 0) d.cd--;
        if (d.cd12 > 0) d.cd12--;
        if (!d.casting && d.left <= 0) return;
        ServerLevel level = p.serverLevel();
        if (!p.isAlive()) {
            finish(d);
            return;
        }
        if (d.casting) cast(p, d, level);
        if (d.left > 0) active(p, d, level);
    }

    private static void cast(ServerPlayer p, D d, ServerLevel level) {
        d.s++;
        p.setDeltaMovement(0.0D, Math.min(0.0D, p.getDeltaMovement().y), 0.0D);
        p.hurtMarked = true;
        if (d.s == 1) {
            ModNet.playAnim(p, d.kind == 3 ? "incomplete_shrine" : "malevolent_shrine", 0);
            ModNet.moveFx(p, "sukuna_dom" + d.kind + "_start");
        }
        // domainstart 120..1: everything within 48 is gathered on the caster
        if (d.s <= 120) {
            for (LivingEntity t : around(level, p, p.position(), 48.0D)) {
                t.teleportTo(p.getX(), p.getY(), p.getZ());
                t.setDeltaMovement(Vec3.ZERO);
                d.inside.add(t.getUUID());
            }
        }
        if (d.s == 75) { // domainstart 46: the cutscene (domain 221 / 281) starts
            d.c = 0;
            d.center = p.position();
        }
        if (d.s >= 75) {
            d.c++;
            if (d.c == 1) {
                ModNet.playAnim(p, d.kind == 3 ? "incomplete_shrine2" : "malevolent_shrine2", 0);
                ModNet.moveFx(p, "sukuna_dom" + d.kind + "_cut");
            }
            if (d.kind == 2 && d.c == 64) ModNet.playAnim(p, "malevolent_shrine_under", 0);
            if (d.kind == 2 && d.c == 121) ModNet.playAnim(p, "malevolent_shrine_hold", 0);
            for (LivingEntity t : around(level, p, d.center, 33.0D)) GojoTechEntity.stun(t, d.kind == 3 ? 7 : 5);
            int act = d.kind == 3 ? 57 : 71;
            if (d.c == act) {
                d.left = (d.kind == 1 ? 331 : d.kind == 2 ? 600 : 1000) + (d.kind == 3 ? 0 : Energy.get(p));
                d.cnt = 0;
                if (d.kind == 1) spawnVisual(level, d);
            }
            if (d.c >= castEnd(d)) d.casting = false;
        }
    }

    private static void spawnVisual(ServerLevel level, D d) {
        DomainShrineEntity e = ModEntities.MALEVOLENT_SHRINE.get().create(level);
        if (e == null) return;
        e.setVisualOnly();
        e.setPos(d.center.x, d.center.y, d.center.z);
        level.addFreshEntity(e);
        d.visual = e.getUUID();
    }

    private static void active(ServerPlayer p, D d, ServerLevel level) {
        boolean cutDone = !d.casting && d.c >= castEnd(d);
        if (!(d.kind == 1 && (SukunaSkills.isBusy(p) || SukunaFuga.isBusy(p)))) d.left--; // the closed domain waits while its caster fights (Deleter + 1)
        if (cutDone) {
            int e = Energy.get(p);
            if (e >= 1) Energy.set(p, e - 1);
            if (e <= 1 && d.left > 31) d.left = 31;
        }
        double r = d.kind == 1 ? 40.0D : 200.0D;
        double clear = d.kind == 1 ? 60.0D : 200.0D;
        if (d.left >= 31) {
            // items and arrows are removed
            AABB box = new AABB(d.center, d.center).inflate(clear);
            for (Entity it : level.getEntitiesOfClass(Entity.class, box, x -> x instanceof ItemEntity || x instanceof AbstractArrow)) it.discard();
            // the damage counter
            if (d.cnt >= 1 && d.cnt <= 3) SukunaTechnique.addFuga(p, 1);
            if (d.cnt > 0) {
                d.cnt--;
            } else {
                for (LivingEntity t : around(level, p, d.kind == 1 ? d.center : p.position(), r)) GojoTechEntity.hurt(level, p, t, 3.0F);
                d.cnt = 1 + RNG.nextInt(4);
            }
        } else if (d.kind == 1) {
            for (LivingEntity t : around(level, p, d.center, 90.0D)) GojoTechEntity.stun(t, 6); // Deleter 1..30
        }
        if (d.kind == 1 && d.left > 0) barrier(p, d, level);
        if (d.left <= 0) finish(d);
    }

    /** The barrier: what was inside stays within 33 blocks of the centre. */
    private static void barrier(ServerPlayer p, D d, ServerLevel level) {
        d.inside.add(p.getUUID());
        for (UUID id : d.inside) {
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive()) continue;
            double dx = e.getX() - d.center.x, dz = e.getZ() - d.center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 32.5D) {
                double k = 32.0D / dist;
                e.teleportTo(d.center.x + dx * k, e.getY(), d.center.z + dz * k);
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
            }
        }
    }

    private static void finish(D d) {
        d.left = -1;
        d.casting = false;
        d.inside.clear();
        if (d.visual != null && d.level != null) {
            Entity e = d.level.getEntity(d.visual);
            if (e != null) e.discard();
        }
        d.visual = null;
    }

    private static List<LivingEntity> around(ServerLevel level, Entity own, Vec3 c, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                t -> SukunaFuga.validTarget(own, t) && t.position().distanceTo(c) <= r);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        D d = STATES.remove(event.getEntity().getUUID());
        if (d != null) finish(d);
    }
}
