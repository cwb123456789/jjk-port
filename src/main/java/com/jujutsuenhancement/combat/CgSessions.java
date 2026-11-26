package com.jujutsuenhancement.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The cutscenes (CG animations, hurt animations, camera shots) that are running on a player. A CG registers one when it starts (every
 * effect timeline with a camera shot does, see {@code ModNet.moveFx}) and it ends by itself when the timeline is over; the skin add-on's
 * commands (咒术替换 / 服饰替换) cancel the running one so the player can move and swap the skin, then call {@code resume} so the CG
 * plays again from its first tick, unchanged except for the skin.
 */
@Mod.EventBusSubscriber(modid = com.jujutsuenhancement.JujutsuEnhancement.MODID)
public final class CgSessions {
    public static final class Session {
        public final String id;
        public final Runnable restart;
        public final Runnable cancel;
        public long endTick;
        public final long startTick;
        public final int ticks;
        /** the player animation of the CG, replayed with it */
        public String anim;
        public int idle;
        /** the other people shown in the CG (their entity ids): after 咒术替换 they can be re-skinned too */
        public final java.util.Set<Integer> participants = new java.util.LinkedHashSet<>();

        Session(String id, Runnable restart, Runnable cancel, long startTick, int ticks) {
            this.id = id;
            this.restart = restart;
            this.cancel = cancel;
            this.startTick = startTick;
            this.ticks = ticks;
            this.endTick = startTick + ticks;
        }
    }

    private static final Map<UUID, Session> RUNNING = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> PAUSED = new ConcurrentHashMap<>();

    private CgSessions() {}

    public static Session begin(ServerPlayer p, String id, int ticks, Runnable restart, Runnable cancel) {
        Session s = new Session(id, restart, cancel, p.level().getGameTime(), Math.max(ticks, 1));
        RUNNING.put(p.getUUID(), s);
        PAUSED.remove(p.getUUID());
        return s;
    }

    /** A technique/CG that shows another person (victim, partner) tells the session, so both can be re-skinned. */
    public static void addParticipant(ServerPlayer caster, net.minecraft.world.entity.Entity other) {
        Session s = RUNNING.get(caster.getUUID());
        if (s == null) s = PAUSED.get(caster.getUUID());
        if (s != null && other != null && other != caster) s.participants.add(other.getId());
    }

    /** The people of the running or paused CG besides the caster. */
    public static int[] participants(ServerPlayer p) {
        Session s = RUNNING.get(p.getUUID());
        if (s == null) s = PAUSED.get(p.getUUID());
        return s == null ? new int[0] : s.participants.stream().mapToInt(Integer::intValue).toArray();
    }

    public static Session current(ServerPlayer p) {
        return RUNNING.get(p.getUUID());
    }

    public static void end(ServerPlayer p) {
        RUNNING.remove(p.getUUID());
    }

    public static boolean running(ServerPlayer p) {
        return RUNNING.containsKey(p.getUUID());
    }

    /** Frees the player from the running CG (it can be replayed with {@link #resume}). */
    public static boolean pause(ServerPlayer p) {
        Session s = RUNNING.remove(p.getUUID());
        if (s == null) return false;
        PAUSED.put(p.getUUID(), s);
        if (s.cancel != null) s.cancel.run();
        else com.jujutsuenhancement.net.ModNet.stopCg(p);
        return true;
    }

    /** Replays the CG that {@link #pause} stopped. */
    public static boolean resume(ServerPlayer p) {
        Session s = PAUSED.remove(p.getUUID());
        if (s == null) return false;
        if (s.restart != null) s.restart.run();
        else com.jujutsuenhancement.net.ModNet.replayCg(p, s);
        return true;
    }

    public static boolean paused(ServerPlayer p) {
        return PAUSED.containsKey(p.getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Session s = RUNNING.get(p.getUUID());
        if (s != null && p.level().getGameTime() >= s.endTick) RUNNING.remove(p.getUUID());
        if (!p.isAlive()) {
            RUNNING.remove(p.getUUID());
            PAUSED.remove(p.getUUID());
        }
    }
}
