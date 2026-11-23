package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.BedrockFxEntity;
import com.jujutsuenhancement.entity.GojoTechEntity;
import com.jujutsuenhancement.item.KatanaItem;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.stats.PlayerStats;
import com.jujutsuenhancement.text.BedrockText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * The katana's fighting, ported from functions/fighting/katana/*.mcfunction. The addon keeps the state in scoreboards
 * that count down once per tick; the fields of {@link State} carry the same names:
 * <ul>
 *   <li>left click: the four-hit combo (BasicM1 / BasicM1s / M1Reset / Disabled), the hit lands when BasicM1 reaches 3;</li>
 *   <li>right click: 疾斩 (wep1, move 21, cooldown 160), sneaking: 袭滑快斩 (wep2, cooldown 200);</li>
 *   <li>sneak + left click with the tag "heavenlyrestriction": 零时刃闪 (wep3, move 36, cooldown 400).</li>
 * </ul>
 * Left out for now: the Black Flash chance of the heavy skills and the Mahoraga adaptation.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KatanaCombat {
    private static final class State {
        int basicM1, combo, m1Reset, disabled, move, skill, wep1, wep2, wep3, frames;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final int[] M1_TICKS = {8, 8, 9, 14};
    private static final int[] M1_IDLE_BLOCK = {30, 30, 31, 46};

    private KatanaCombat() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    private static boolean holds(Player p) {
        return p.getMainHandItem().getItem() instanceof KatanaItem;
    }

    // ------------------------------------------------------------------ input

    public static void onLeftClick(ServerPlayer p) {
        if (!holds(p) || !p.isAlive()) return;
        State s = state(p);
        if (p.isShiftKeyDown()) {
            // 零时刃闪 needs the heavenly restriction (tag)
            if (!p.getTags().contains("heavenlyrestriction") || s.skill != 0 || s.disabled != 0) return;
            if (s.wep3 == 0) {
                s.skill = 3;
                s.move = 36;
                s.wep3 = 400;
            } else {
                cooldown(p, "零时刃闪", s.wep3);
            }
            return;
        }
        if (s.basicM1 != 0 || s.disabled != 0 || s.skill != 0) return;
        s.combo = s.combo >= 4 ? 1 : s.combo + 1;
        s.basicM1 = M1_TICKS[s.combo - 1];
        s.m1Reset = 22;
        ModNet.playAnim(p, "katana_attack" + s.combo, M1_IDLE_BLOCK[s.combo - 1]);
    }

    public static void onRightClick(ServerPlayer p) {
        State s = state(p);
        if (s.skill != 0 || s.basicM1 != 0 || !p.isAlive()) return;
        if (p.isShiftKeyDown()) {
            if (s.wep2 == 0) {
                s.skill = 2;
                s.move = 21;
                s.wep2 = 200;
            } else {
                cooldown(p, "袭滑快斩", s.wep2);
            }
        } else {
            if (s.wep1 == 0) {
                s.skill = 1;
                s.move = 21;
                s.wep1 = 160;
            } else {
                cooldown(p, "疾斩", s.wep1);
            }
        }
    }

    private static void cooldown(ServerPlayer p, String name, int left) {
        p.displayClientMessage(BedrockText.parse("§l§c" + name + " 冷却中: §l§e" + left + "§l§c."), false);
        ((ServerLevel) p.level()).playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.4F);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.wep1 > 0) s.wep1--;
        if (s.wep2 > 0) s.wep2--;
        if (s.wep3 > 0) s.wep3--;
        if (s.frames > 0) s.frames--;
        if (!p.isAlive() || !holds(p)) {
            s.basicM1 = 0;
            s.skill = 0;
            s.move = 0;
            s.combo = 0;
            return;
        }
        if (s.skill != 0) {
            switch (s.skill) {
                case 1 -> quickdraw(p, s);
                case 2 -> prominence(p, s);
                default -> rapidCuts(p, s);
            }
            if (--s.move <= 0) {
                s.move = 0;
                s.skill = 0;
            }
            return;
        }
        if (s.basicM1 > 0) {
            s.basicM1--;
            s.m1Reset = 22;
            s.disabled = Math.max(s.disabled, s.combo >= 4 ? 32 : s.combo == 3 ? 5 : 4);
            if (s.basicM1 == 3) m1Hit(p, s);
            if (s.basicM1 <= 2 && s.combo >= 4) s.combo = 0;
            return;
        }
        if (s.disabled > 0) s.disabled--;
        if (s.combo > 0 && s.m1Reset > 0 && --s.m1Reset == 0) s.combo = 0;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /** Frames: the player cannot be hurt while a skill gives it invulnerability frames. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && s.frames > 0 && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ helpers

    private static Vec3 point(Player p, double dist) {
        return p.position().add(p.getLookAngle().scale(dist));
    }

    private static List<LivingEntity> near(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r),
                e -> e != p && e.isAlive() && e.distanceToSqr(c) <= r * r && !isFramed(e));
    }

    /** Whether a katana move or combo hit is running (another technique cannot start meanwhile). */
    public static boolean isBusy(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && (s.skill != 0 || s.basicM1 != 0);
    }

    private static boolean isFramed(LivingEntity e) {
        if (!(e instanceof Player pl)) return false;
        State s = STATES.get(pl.getUUID());
        return s != null && s.frames > 0;
    }

    private static float bonus(Player p) {
        return PlayerStats.get(p, "damageadd") / 2.0F;
    }

    private static void fx(ServerPlayer p, String name) {
        BedrockFxEntity.play(p.serverLevel(), name, p.getX(), p.getY(), p.getZ(), p.getYRot());
    }

    private static void hit(ServerPlayer p, LivingEntity t, float amount, int stun, double up) {
        GojoTechEntity.hurt(p.serverLevel(), p, t, amount);
        if (stun > 0) GojoTechEntity.stun(t, stun);
        if (up > 0) {
            Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.25);
            t.setDeltaMovement(away.x, up, away.z);
            t.hurtMarked = true;
        }
    }

    private static void push(ServerPlayer p, double speed) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize().scale(speed);
        p.setDeltaMovement(p.getDeltaMovement().add(flat.x, 0, flat.z));
        p.hurtMarked = true;
    }

    private static void freeze(ServerPlayer p) {
        p.setDeltaMovement(0, Math.min(p.getDeltaMovement().y, 0), 0);
        p.hurtMarked = true;
    }

    private static void faceUser(ServerPlayer p, List<LivingEntity> targets) {
        for (LivingEntity t : targets) {
            if (!(t instanceof Player)) t.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        }
    }

    // ------------------------------------------------------------------ the combo (katana_m1s)

    private static void m1Hit(ServerPlayer p, State s) {
        int step = s.combo;
        fx(p, "katana_swing");
        List<LivingEntity> targets = near(p, point(p, 2.45), 2.4);
        if (step == 4) {
            fx(p, "katana_hit4");
            if (targets.isEmpty()) push(p, 0.33);
        } else if (!targets.isEmpty()) {
            fx(p, "katana_hit");
        }
        for (LivingEntity t : targets) {
            if (step == 4) hit(p, t, 4 + bonus(p), 0, 0.55);
            else hit(p, t, 2 + bonus(p), step == 3 ? 6 : 12, 0);
        }
    }

    // ------------------------------------------------------------------ 疾斩 (quickdraw)

    private static void quickdraw(ServerPlayer p, State s) {
        int m = s.move;
        if (m == 21) ModNet.playAnim(p, "katana.ab1", 21);
        if (m >= 12 && m <= 17) push(p, 0.33);
        if (m <= 12) freeze(p);
        if (m >= 9 && m <= 12) faceUser(p, near(p, point(p, 3), 2.99));
        if (m == 11) {
            fx(p, "katana_quick_wind");
            if (!near(p, point(p, 3), 2.99).isEmpty()) fx(p, "katana_quick_sword");
        }
        if (m == 10) {
            fx(p, "katana_quick_hit");
            for (LivingEntity t : near(p, point(p, 3), 2.99)) {
                p.serverLevel(); // damage 10 from the player (entity_attack: armour and invulnerability frames apply)
                t.hurt(p.damageSources().playerAttack(p), 10 + bonus(p));
                Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.25);
                t.setDeltaMovement(away.x, 0.5, away.z);
                t.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ 袭滑快斩 (prominence)

    private static void prominence(ServerPlayer p, State s) {
        int m = s.move;
        if (m == 21) ModNet.playAnim(p, "katana.ab2", 21);
        if (m >= 4 && m <= 18) s.frames = 4;
        if (m >= 8 && m <= 17) push(p, 0.5);
        if (m >= 6 && m <= 17) faceUser(p, near(p, p.position(), 2.99));
        if (m == 16) fx(p, "katana_prom_start");
        if (m == 11) fx(p, "katana_quick_wind");
        if (m >= 5 && m <= 7) fx(p, "katana_prom_tick");
        if (m >= 5 && m <= 16) {
            List<LivingEntity> targets = near(p, p.position(), 2.99);
            for (LivingEntity t : targets) hit(p, t, 1 + (m == 15 ? bonus(p) : 0), 15, 0);
            if (m == 5 && !targets.isEmpty()) {
                fx(p, "katana_prom_hit");
                for (LivingEntity t : targets) hit(p, t, PlayerStats.get(p, "damageadd"), 0, 0.5);
            }
        }
    }

    // ------------------------------------------------------------------ 零时刃闪 (rapid cuts)

    private static void rapidCuts(ServerPlayer p, State s) {
        int m = s.move;
        if (m == 36) ModNet.playAnim(p, "katana.ab3", 36);
        if (m == 34) fx(p, "katana_rapid_start");
        if (m >= 31 && m <= 34) push(p, -0.33);
        if (m >= 11 && m <= 30) {
            s.frames = 4;
            freeze(p);
            faceUser(p, near(p, point(p, 4), 3.99));
        }
        if (m == 30 || m == 10) fx(p, "katana_rapid_dirt");
        if (m >= 16 && m <= 30) {
            List<LivingEntity> targets = near(p, point(p, 4), 3.99);
            fx(p, "katana_rapid_tick");
            for (LivingEntity t : targets) hit(p, t, 2, 15, 0);
        }
        if (m == 10) {
            fx(p, "katana_rapid_end");
            for (LivingEntity t : near(p, point(p, 4), 3.99)) hit(p, t, PlayerStats.get(p, "damageadd"), 0, 0.4);
        }
    }

    public static void resetCooldowns(Player p) {
        State s = STATES.get(p.getUUID());
        if (s != null) s.wep1 = s.wep2 = s.wep3 = 0;
    }
}
