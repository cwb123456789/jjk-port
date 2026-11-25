package com.jujutsuenhancement.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * The cutscenes (CG animations, hurt animations, camera shots) that are running on a player. A technique registers one when its CG
 * starts and ends it when the CG is over; the skin add-on's commands (咒术替换 / 服饰替换) cancel the running one so the player can
 * move and swap the skin, then call {@code restart} so the CG plays again, unchanged except for the skin.
 */
public final class CgSessions {
    public record Session(String id, Runnable restart, Runnable cancel) {}

    private static final Map<UUID, Session> RUNNING = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> PAUSED = new ConcurrentHashMap<>();

    private CgSessions() {}

    public static void begin(ServerPlayer p, String id, Runnable restart, Runnable cancel) {
        RUNNING.put(p.getUUID(), new Session(id, restart, cancel));
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
        if (s.cancel() != null) s.cancel().run();
        return true;
    }

    /** Replays the CG that {@link #pause} stopped. */
    public static boolean resume(ServerPlayer p) {
        Session s = PAUSED.remove(p.getUUID());
        if (s == null) return false;
        if (s.restart() != null) s.restart().run();
        return true;
    }

    public static boolean paused(ServerPlayer p) {
        return PAUSED.containsKey(p.getUUID());
    }
}
