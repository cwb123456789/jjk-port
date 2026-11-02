package com.jujutsuenhancement.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lets a player trigger an awakened mob's techniques by hand (right-click = cast, sneak + right-click = next). */
public interface TestCastable {
    List<String> castableSkills();

    /** Starts the named technique; returns false if the mob is busy or the name is unknown. */
    boolean cast(String name, LivingEntity target);

    Map<UUID, Integer> CURSORS = new HashMap<>();

    static <T extends Mob & TestCastable> InteractionResult interact(T mob, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (mob.level().isClientSide) return InteractionResult.SUCCESS;
        List<String> names = mob.castableSkills();
        if (names.isEmpty()) return InteractionResult.PASS;
        int index = Math.floorMod(CURSORS.getOrDefault(mob.getUUID(), 0), names.size());
        String name = names.get(index);
        if (player.isShiftKeyDown()) {
            int next = (index + 1) % names.size();
            CURSORS.put(mob.getUUID(), next);
            player.displayClientMessage(Component.literal("下一个招式：" + names.get(next) + "  [" + (next + 1) + "/" + names.size() + "]"), true);
            return InteractionResult.SUCCESS;
        }
        LivingEntity target = mob.getTarget() != null && mob.getTarget().isAlive() ? mob.getTarget() : player;
        mob.setTarget(target);
        boolean ok = mob.cast(name, target);
        if (ok) CURSORS.put(mob.getUUID(), (index + 1) % names.size());
        player.displayClientMessage(Component.literal((ok ? "释放：" : "忙碌中，稍后再点：") + name
                + "  [" + (index + 1) + "/" + names.size() + "]"), true);
        return InteractionResult.SUCCESS;
    }
}
