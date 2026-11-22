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
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「竈」 開 (Fuga), the item {@code ds:sukuna4} ({@code functions/sukuna/attacks/fuga/divine_flames.mcfunction}).
 *
 * <pre>
 *  click (on the ground, not sneaking, 15 fingers, the fuga meter at 500)  cost 1000, cd 800
 *     move 56 -> 11   charge (fuga_activate), invulnerable from move 40
 *     move 10..4      the choice: right click (hold) switches 炎矢 / 焰矛 / 灼刃 / 炽镰, left click selects
 *  炎矢 (move 1001)  the arrow is shot at move 829 (c = 172), 2.5 blocks per tick, explodes on a block / on anything within 15 / after 70 blocks:
 *                    100 damage (r50) at 945, 100 (r70) at 875 and every tick 800..975 2 fire + 1 override (r50), Stun 60
 *  焰矛 (move 4001)  the spear is thrown at c = 140 (1 block per tick, then 0.5), each tick spear_explosion at a random spot within 40:
 *                    6 damage (r32) six ticks later
 *  灼刃 (move 5001)  the blade at c = 81, 2 blocks per tick for 60 ticks, 150 fire damage (r7.99) at 11 points of a column of 80 blocks;
 *                    afterwards the choice comes back (move 4)
 *  炽镰 (move 7001)  the scythe at c = 41, 3 blocks per tick for 21 ticks, the first thing within 5 is held 15 blocks ahead
 *                    (Stun 50) and takes 5 damage per tick for 50 ticks
 * </pre>
 * {@code t = N - move}. Terrain destruction ("griefable" blocks) and the camera cutscene of the scythe belong to later stages.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaFuga {
    private static final int COST = 1000, CD = 800;
    private static final int ARROW = 1, SPEAR = 2, MUGETSU = 3, SCYTHE = 4;
    private static final String[] FORM = {"", "炎矢", "焰矛", "灼刃", "炽镰"};
    private static final String[] ANIM = {"", "fuga_arrow1", "fuga_spear1", "fuga_mugetsu", "fuga_scythe"};
    private static final String[] FX = {"", "sukuna_fuga_arrow", "sukuna_fuga_spear", "sukuna_fuga_mugetsu", "sukuna_fuga_scythe"};

    private static final class State {
        int phase; // 0 idle, 1 charge, 2 choice, 3 cast
        int t, c, g = -1, form, scroll = 1, cd, frames;
        UUID grab;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SukunaFuga() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean isBusy(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.phase != 0;
    }

    public static boolean hasFrames(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.frames > 0;
    }

    // ------------------------------------------------------------------ input

    /** Right click with sukuna4: starts the charge, or while choosing switches the form. */
    public static void onUse(ServerPlayer p) {
        State s = state(p);
        if (s.phase == 2) {
            s.scroll = s.scroll % 4 + 1;
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 1.0F, 1.25F);
            showChoice(p, s);
            return;
        }
        if (s.phase != 0 || !p.isAlive() || SukunaSkills.isBusy(p) || GojoSkills.isBusy(p) || YujiSkills.isBusy(p) || KatanaCombat.isBusy(p) || SukunaDomain.casting(p) || SukunaFists.isBusy(p) || SukunaCleave.isBusy(p)) return;
        if (!SukunaTechnique.active(p)) {
            p.displayClientMessage(BedrockText.parse("§l§c需要宿傩掌控身体"), true);
            return;
        }
        ServerLevel level = p.serverLevel();
        if (SukunaTechnique.fingers(p) < 15) return;
        if (!SukunaTechnique.fugaReady(p)) {
            p.displayClientMessage(BedrockText.parse("§l§4解和捌的烹饪步骤还未经历完全"), false);
            return;
        }
        if (p.isShiftKeyDown() || !p.onGround() || isStunned(p)) return;
        if (Energy.get(p) < COST) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.87F);
            return;
        }
        if (s.cd > 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.05F, 0.4F);
            p.displayClientMessage(BedrockText.parse("§l§c「竈」 冷却中: §l§e" + s.cd + "§c."), false);
            return;
        }
        Energy.set(p, Energy.get(p) - COST);
        s.cd = CD;
        s.phase = 1;
        s.t = 0;
        s.c = 0;
        s.g = -1;
        s.scroll = 1;
        s.grab = null;
        SukunaTechnique.resetFuga(p);
    }

    /** Left click while choosing: the form is selected. */
    public static void onLeftClick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.phase != 2) return;
        s.form = s.scroll;
        s.phase = 3;
        s.c = 0;
        s.g = -1;
        s.grab = null;
        s.frames = 0;
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 1.0F, 2.0F);
        p.connection.send(new ClientboundSetTitleTextPacket(BedrockText.parse("§l")));
    }

    private static void showChoice(ServerPlayer p, State s) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 6, 2));
        p.connection.send(new ClientboundSetSubtitleTextPacket(BedrockText.parse("§l§o§e-右键(长按) 以切换, 左键(单击) 以选定§r  §l§c(晚上用看起来效果更好)")));
        p.connection.send(new ClientboundSetTitleTextPacket(BedrockText.parse("§l§6-[ §c" + FORM[s.scroll] + " §6]-")));
    }

    private static boolean isStunned(Player p) {
        MobEffectInstance e = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return e != null && e.getAmplifier() >= 6;
    }

    // ------------------------------------------------------------------ the caster

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.cd > 0) s.cd--;
        if (s.frames > 0) s.frames--;
        if (s.phase == 0) return;
        if (!p.isAlive()) {
            s.phase = 0;
            return;
        }
        ServerLevel level = p.serverLevel();
        hold(p);
        if (s.phase == 1) {
            s.t++;
            int t = s.t;
            if (t == 1) {
                ModNet.playAnim(p, "fuga_activate", 0);
                ModNet.moveFx(p, "sukuna_fuga");
            }
            if (t >= 16) s.frames = 6; // Frames 6 for move 1..40
            if (t <= 30 && isStunned(p)) { // move 26.. with Stun: move 3
                s.phase = 0;
                s.frames = 0;
                return;
            }
            if (t >= 46) {
                s.phase = 2;
                ModNet.playAnim(p, "fuga_activate_hold", 0);
                showChoice(p, s);
            }
            return;
        }
        if (s.phase == 2) {
            s.frames = 6;
            if (p.tickCount % 2 == 0) showChoice(p, s);
            if (p.isShiftKeyDown()) { // not in the addon (the choice only ends with the left click): sneaking lets the player out
                s.phase = 0;
                s.frames = 0;
                p.connection.send(new ClientboundSetTitleTextPacket(BedrockText.parse("§l")));
            }
            return;
        }
        castTick(p, s, level);
    }

    private static void castTick(ServerPlayer p, State s, ServerLevel level) {
        if (isStunned(p) && s.g < 0) { // move 26..: Stun -> move 3
            s.phase = 0;
            return;
        }
        s.c++;
        int c = s.c;
        Vec3 look = p.getLookAngle();
        if (c == 1) {
            ModNet.playAnim(p, ANIM[s.form], 0);
            ModNet.moveFx(p, FX[s.form]);
        }
        switch (s.form) {
            case ARROW -> {
                if (c == 172) {
                    Vec3 at = local(p, 0, 1, 1);
                    Vec3 aim = local(p, 0, 0, 20).subtract(at);
                    EFFECTS.add(Eff.arrow(level, p.getUUID(), at, aim.normalize()));
                }
                if (c >= 187) s.phase = 0;
            }
            case SPEAR -> {
                if (c == 140) EFFECTS.add(Eff.spear(level, p.getUUID(), local(p, 0, 0, 1), look));
                if (c >= 147) s.phase = 0;
            }
            case MUGETSU -> {
                if (c == 81) EFFECTS.add(Eff.mugetsu(level, p.getUUID(), local(p, 0, 0, 8), look));
                if (c >= 131) { // move 4: back to the choice
                    s.phase = 2;
                    ModNet.playAnim(p, "fuga_activate_hold", 0);
                }
            }
            case SCYTHE -> {
                if (s.g < 0) {
                    if (c == 41) EFFECTS.add(Eff.scythe(level, p.getUUID(), local(p, 0, 1.5, 2), look));
                    if (c >= 64) s.phase = 0; // move 6937: nothing was caught
                } else scytheGrab(p, s, level);
            }
            default -> s.phase = 0;
        }
    }

    /** Called by the scythe effect: the first thing within 5 blocks is caught (move 6801). */
    private static void caught(UUID owner, LivingEntity target) {
        State s = STATES.get(owner);
        if (s == null || s.phase != 3 || s.form != SCYTHE || s.g >= 0) return;
        s.grab = target.getUUID();
        s.g = 0; // the grab timeline restarts (move 6801)
    }

    private static void scytheGrab(ServerPlayer p, State s, ServerLevel level) {
        LivingEntity target = null;
        if (s.grab != null && level.getEntity(s.grab) instanceof LivingEntity le) target = le;
        int g = s.g;
        if (g == 0) {
            ModNet.playAnim(p, "fuga_scythe2", 0);
            ModNet.moveFx(p, "sukuna_fuga_grab");
        }
        if (target != null && target.isAlive()) {
            if (g <= 2) {
                Vec3 f = new Vec3(-Math.sin(Math.toRadians(p.getYRot())), 0.0D, Math.cos(Math.toRadians(p.getYRot())));
                Vec3 to = p.position().add(f.scale(15.0D)).add(0, 0.22D, 0);
                target.teleportTo(to.x, to.y, to.z);
                Vec3 d = target.position().subtract(p.position());
                p.setYRot((float) (Mth.atan2(-d.x, d.z) * 180.0D / Math.PI));
                p.yHeadRot = p.getYRot();
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            }
            if (g == 1) {
                Vec3 d = p.position().subtract(target.position());
                target.setYRot((float) (Mth.atan2(-d.x, d.z) * 180.0D / Math.PI));
            }
            GojoTechEntity.stun(target, 50);
            target.setDeltaMovement(0.0D, Math.min(0.0D, target.getDeltaMovement().y), 0.0D);
            target.hurtMarked = true;
            if (g >= 72 && g <= 121) GojoTechEntity.hurt(level, p, target, 5.0F);
        }
        if (g <= 4) { // scriptevent ds:knockback 2 at move 6797..6801
            Vec3 f = new Vec3(-Math.sin(Math.toRadians(p.getYRot())), 0.0D, Math.cos(Math.toRadians(p.getYRot())));
            p.setDeltaMovement(f.x * 2.0D, 0.0D, f.z * 2.0D);
            p.hurtMarked = true;
        }
        s.g++;
        if (s.g >= 127) {
            s.phase = 0;
            s.g = -1;
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

    private static void hold(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s != null && s.g >= 0 && s.g <= 4) return; // the knockback of the grab
        p.setDeltaMovement(0.0D, Math.min(0.0D, p.getDeltaMovement().y), 0.0D);
        p.hurtMarked = true;
    }

    /** The addon's ^left ^up ^forward frame of the player (feet, yaw and pitch). */
    private static Vec3 local(Entity e, double l, double u, double f) {
        double y = Math.toRadians(e.getYRot()), p = Math.toRadians(e.getXRot());
        Vec3 fwd = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 left = new Vec3(Math.cos(y), 0.0D, Math.sin(y));
        Vec3 up = new Vec3(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p));
        return e.position().add(left.scale(l)).add(up.scale(u)).add(fwd.scale(f));
    }

    // ------------------------------------------------------------------ the entities of the skill

    private static final List<Eff> EFFECTS = new ArrayList<>();
    private static final java.util.Random RNG = new java.util.Random();

    private static final int K_ARROW = 1, K_BOOM = 2, K_SPEAR = 3, K_BLAST = 4, K_MUGETSU = 5, K_SCYTHE = 6;

    private static final class Eff {
        final int kind;
        final ServerLevel level;
        final UUID owner;
        Vec3 pos, dir;
        int d, age;
        BedrockFxEntity marker;
        final Map<UUID, Integer> hit = new HashMap<>();
        boolean exploded;

        Eff(int kind, ServerLevel level, UUID owner, Vec3 pos, Vec3 dir, int d) {
            this.kind = kind;
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.dir = dir;
            this.d = d;
        }

        void marker(String key, int life) {
            float yaw = dir == null ? 0.0F : (float) (Mth.atan2(-dir.x, dir.z) * 180.0D / Math.PI);
            float pitch = dir == null ? 0.0F : (float) (-Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)) * 180.0D / Math.PI);
            marker = BedrockFxEntity.timeline(level, key, pos.x, pos.y, pos.z, yaw, pitch, life);
        }

        void sync() {
            if (marker != null && marker.isAlive()) marker.setPos(pos.x, pos.y, pos.z);
        }

        void end() {
            if (marker != null) marker.discard();
        }

        static Eff arrow(ServerLevel l, UUID o, Vec3 pos, Vec3 dir) {
            Eff e = new Eff(K_ARROW, l, o, pos, dir, 60);
            e.marker("fuga_arrow_fly", 70);
            return e;
        }

        static Eff spear(ServerLevel l, UUID o, Vec3 pos, Vec3 dir) {
            Eff e = new Eff(K_SPEAR, l, o, pos, dir, 201);
            e.marker("fuga_spear_fly", 205);
            return e;
        }

        static Eff mugetsu(ServerLevel l, UUID o, Vec3 pos, Vec3 dir) {
            Eff e = new Eff(K_MUGETSU, l, o, pos, dir, 61);
            e.marker("fuga_mugetsu_fly", 65);
            return e;
        }

        static Eff scythe(ServerLevel l, UUID o, Vec3 pos, Vec3 dir) {
            Eff e = new Eff(K_SCYTHE, l, o, pos, dir, 21);
            e.marker("fuga_scythe_fly", 25);
            return e;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || EFFECTS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        List<Eff> add = new ArrayList<>();
        for (int i = EFFECTS.size() - 1; i >= 0; i--) {
            Eff e = EFFECTS.get(i);
            boolean done = switch (e.kind) {
                case K_ARROW -> tickArrow(server, e, add);
                case K_BOOM -> tickBoom(server, e);
                case K_SPEAR -> tickSpear(server, e, add);
                case K_BLAST -> tickBlast(server, e);
                case K_MUGETSU -> tickMugetsu(server, e);
                case K_SCYTHE -> tickScythe(server, e);
                default -> true;
            };
            if (done) {
                e.end();
                EFFECTS.remove(i);
            }
        }
        EFFECTS.addAll(add);
    }

    private static ServerPlayer owner(MinecraftServer server, Eff e) {
        return server.getPlayerList().getPlayer(e.owner);
    }

    static boolean validTarget(Entity own, LivingEntity t) {
        if (t == own || !t.isAlive() || t instanceof ArmorStand) return false;
        if (own != null && t.isAlliedTo(own)) return false;
        if (t instanceof Player pl) {
            if (pl.isCreative() || pl.isSpectator()) return false;
            return !SukunaSkills.hasFrames(pl) && !GojoSkills.hasFrames(pl) && !YujiSkills.hasFrames(pl);
        }
        return true;
    }

    private static List<LivingEntity> around(Eff e, Entity own, Vec3 c, double r) {
        return e.level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                t -> validTarget(own, t) && t.position().distanceTo(c) <= r + t.getBbWidth() * 0.5D);
    }

    /** fuga_arrow1: 2.5 blocks per tick; it explodes on a block, when something is within 15, or when its owner is more than 70 away. */
    private static boolean tickArrow(MinecraftServer server, Eff e, List<Eff> add) {
        ServerPlayer own = owner(server, e);
        e.d--;
        e.pos = e.pos.add(e.dir.scale(2.5D));
        boolean boom = false;
        if (e.d >= 12 && (own == null || own.position().distanceTo(e.pos) > 70.0D)) e.d = 11;
        if (e.d >= 12 && !around(e, own, e.pos, 15.0D).isEmpty()) e.d = 11;
        if (e.d >= 7 && e.d <= 10) boom = true;
        if (e.d >= 7 && !e.level.getBlockState(BlockPos.containing(e.pos)).isAir()) boom = true;
        e.sync();
        if (boom || e.d <= 6) {
            Eff b = new Eff(K_BOOM, e.level, e.owner, e.pos, null, 1001);
            b.marker("fuga_boom", 235);
            add.add(b);
            return true;
        }
        return false;
    }

    /** The explosion (Deleter 1001 -> 775): 100 at 945 (r50), 100 at 875 (r70), 2 fire + 1 override and Stun 60 each tick 975..800 (r50). */
    private static boolean tickBoom(MinecraftServer server, Eff e) {
        ServerPlayer own = owner(server, e);
        e.d--;
        int d = e.d;
        if (d == 945) blast(e, own, 50.0D, 100.0F);
        if (d == 875) blast(e, own, 70.0D, 100.0F);
        if (d >= 800 && d <= 975) {
            for (LivingEntity t : around(e, own, e.pos, 50.0D)) {
                t.hurt(e.level.damageSources().onFire(), 2.0F);
                GojoTechEntity.hurt(e.level, own, t, 1.0F);
                GojoTechEntity.stun(t, 60);
                if (d <= 971) t.setSecondsOnFire(5);
            }
        }
        return d <= 775;
    }

    private static void blast(Eff e, Entity own, double r, float dmg) {
        for (LivingEntity t : around(e, own, e.pos, r)) GojoTechEntity.hurt(e.level, own, t, dmg);
    }

    /** fuga_spear: 0.5 blocks per tick (1 while Deleter >= 160), a spear_explosion every tick 160..20, killed at 70. */
    private static boolean tickSpear(MinecraftServer server, Eff e, List<Eff> add) {
        e.d--;
        e.pos = e.pos.add(e.dir.scale(e.d >= 160 ? 1.0D : 0.5D));
        e.sync();
        if (e.d >= 20 && e.d <= 160) {
            double a = RNG.nextDouble() * Math.PI * 2.0D, r = 3.0D + RNG.nextDouble() * 37.0D;
            BlockPos bp = e.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(e.pos.x + Math.cos(a) * r, e.pos.y, e.pos.z + Math.sin(a) * r));
            Eff b = new Eff(K_BLAST, e.level, e.owner, new Vec3(bp.getX() + 0.5D, bp.getY(), bp.getZ() + 0.5D), null, 9);
            b.marker("fuga_spear_blast", 12);
            add.add(b);
        }
        return e.d <= 70;
    }

    /** spear_explosion: six ticks after it appeared (Deleter 3) everything within 32 takes 6 damage and Stun 20. */
    private static boolean tickBlast(MinecraftServer server, Eff e) {
        e.age++;
        if (e.age == 6) {
            ServerPlayer own = owner(server, e);
            for (LivingEntity t : around(e, own, e.pos, 32.0D)) {
                GojoTechEntity.hurt(e.level, own, t, 24.0F);   // spear_explosion (1.84.5: 6 -> 24)
                GojoTechEntity.stun(t, 20);
                t.setSecondsOnFire(1);
            }
        }
        return e.age >= 8;
    }

    /** fuga_mugetsu: 2 blocks per tick for 60 ticks (climbing out of blocks), 150 fire damage within 7.99 of 11 points of a column. */
    private static boolean tickMugetsu(MinecraftServer server, Eff e) {
        ServerPlayer own = owner(server, e);
        e.d--;
        e.age++;
        e.pos = e.pos.add(e.dir.scale(2.0D));
        for (int i = 0; i < 3; i++) if (!e.level.getBlockState(BlockPos.containing(e.pos.add(0, 1, 0))).isAir()) e.pos = e.pos.add(0, 1, 0);
        e.sync();
        if (e.d >= 1 && e.d <= 58) {
            for (int k = 0; k <= 10; k++) {
                Vec3 c = e.pos.add(0, 8.0D * k, 0);
                for (LivingEntity t : around(e, own, c, 7.99D)) {
                    Integer last = e.hit.get(t.getUUID());
                    if (last != null && e.age - last < 10) continue;
                    e.hit.put(t.getUUID(), e.age);
                    GojoTechEntity.hurt(e.level, own, t, 150.0F);
                    t.setSecondsOnFire(4);
                }
            }
        }
        return e.d <= 0;
    }

    /** scythe.mcfunction: 3 blocks per tick; the first thing within 5 is caught. */
    private static boolean tickScythe(MinecraftServer server, Eff e) {
        ServerPlayer own = owner(server, e);
        e.d--;
        e.pos = e.pos.add(e.dir.scale(3.0D));
        e.sync();
        List<LivingEntity> near = around(e, own, e.pos, 5.0D);
        if (!near.isEmpty()) {
            LivingEntity best = near.get(0);
            for (LivingEntity t : near) if (t.position().distanceToSqr(e.pos) < best.position().distanceToSqr(e.pos)) best = t;
            caught(e.owner, best);
            return true;
        }
        return e.d <= 0;
    }
}
