package com.jujutsuenhancement.integration;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.TestCastable;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/**
 * /jje skills        - list the skills of the nearest awakened mob
 * /jje cast <skill>  - make it cast a skill at you right now
 * /jje fight         - make it target you (even in creative)
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedCommands {
    private AwakenedCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jje")
                .then(Commands.literal("skills").executes(ctx -> skills(ctx.getSource())))
                .then(Commands.literal("fight").executes(ctx -> fight(ctx.getSource())))
                .then(Commands.literal("cast")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Mob m = nearest(ctx.getSource());
                                    if (m instanceof TestCastable c) return SharedSuggestionProvider.suggest(c.castableSkills(), builder);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "skill"))))));
    }

    private static Mob nearest(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return null;
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0D),
                e -> e instanceof TestCastable && e.isAlive());
        return mobs.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
    }

    private static int skills(CommandSourceStack src) {
        Mob m = nearest(src);
        if (!(m instanceof TestCastable c)) {
            src.sendFailure(Component.literal("48 格内没有觉醒版角色"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(m.getName().getString() + " 的技能: " + String.join(", ", c.castableSkills())), false);
        return 1;
    }

    private static int fight(CommandSourceStack src) {
        Mob m = nearest(src);
        if (m == null || !(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("48 格内没有觉醒版角色"));
            return 0;
        }
        m.setTarget(player);
        src.sendSuccess(() -> Component.literal(m.getName().getString() + " 现在把你当作目标"), false);
        return 1;
    }

    private static int cast(CommandSourceStack src, String skill) {
        Mob m = nearest(src);
        if (!(m instanceof TestCastable c) || !(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("48 格内没有觉醒版角色"));
            return 0;
        }
        m.setTarget(player);
        if (!c.cast(skill, player)) {
            src.sendFailure(Component.literal("无法释放 " + skill + "（名字不对，或它正在放别的技能）。用 /jje skills 查看"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(m.getName().getString() + " 释放 " + skill), false);
        return 1;
    }
}
