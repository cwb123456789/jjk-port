package com.jujutsuenhancement.bfn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The add-on's player animation controllers (animation_controllers/detect.json, listed in player.json "animate"): they turn the player's
 * input into scores - detect_left (swinging), detect_right, detect_jump, detect_sneak (+detect_doublesneak), detect_sprint, detect_health.
 * Every controller has the states default (score 0) and active (score 1); the score is written when the state is entered.
 */
public final class PlayerInput {
    private static final Map<UUID, Boolean> JUMP = new ConcurrentHashMap<>();
    private static final Map<UUID, int[]> STATE = new HashMap<>();   // left, right, jump, sneak, sprint, health

    private PlayerInput() {}

    public static void setJump(UUID id, boolean down) {
        JUMP.put(id, down);
    }

    static void forget(UUID id) {
        STATE.remove(id);
        JUMP.remove(id);
    }

    static void tick(McHost host, Engine eng, MinecraftServer server) {
        Scores sc = host.scores();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String key = new McEnt(p, McHost.dimOf(p.level())).key();
            int[] st = STATE.computeIfAbsent(p.getUUID(), k -> new int[] {-1, -1, -1, -1, -1, -1});
            flag(sc, key, st, 0, "detect_left", p.swinging);
            flag(sc, key, st, 1, "detect_right", false);
            flag(sc, key, st, 2, "detect_jump", JUMP.getOrDefault(p.getUUID(), false));
            boolean sneak = p.isShiftKeyDown();
            if (st[3] != (sneak ? 1 : 0)) {
                st[3] = sneak ? 1 : 0;
                sc.set("detect_sneak", key, st[3]);
                if (!sneak) sc.set("detect_doublesneak", key, 0);
            }
            flag(sc, key, st, 4, "detect_sprint", p.isSprinting());
            float h = p.getHealth();
            int n = st[5];
            if (!p.isAlive() || h <= 0) n = 0;
            else if (Math.abs(h - Math.round(h)) < 1e-4 && Math.round(h) >= 1 && Math.round(h) <= 10000) n = Math.round(h);
            if (n != st[5]) {
                st[5] = n;
                sc.set("detect_health", key, n);
            }
        }
    }

    private static void flag(Scores sc, String key, int[] st, int i, String obj, boolean on) {
        int v = on ? 1 : 0;
        if (st[i] != v) {
            st[i] = v;
            sc.set(obj, key, v);
        }
    }
}
