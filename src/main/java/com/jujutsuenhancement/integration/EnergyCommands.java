package com.jujutsuenhancement.integration;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.stats.Energy;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /jje energy                 - show your Energy / EnergyCap
 * /jje energy fill            - fill the Energy
 * /jje energy cap &lt;n&gt;         - set the EnergyCap (the addon raises it with levels and traits; "gojo" tag adds 100)
 * (the addon's tags are entity tags: /tag @s add sixeyes, /tag @s add gojo ...)
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnergyCommands {
    private EnergyCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jje").then(Commands.literal("energy")
                .executes(ctx -> show(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("fill").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Energy.set(p, Energy.cap(p));
                    return show(p);
                }))
                .then(Commands.literal("cap").then(Commands.argument("n", IntegerArgumentType.integer(0, 100000)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Energy.setBase(p, IntegerArgumentType.getInteger(ctx, "n"));
                    return show(p);
                })))
                ).then(Commands.literal("invest").then(Commands.argument("n", IntegerArgumentType.integer(-1000, 1000)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    com.jujutsuenhancement.combat.LimitlessTechnique.addInvest(p, IntegerArgumentType.getInteger(ctx, "n"));
                    p.sendSystemMessage(Component.literal("天赋点 " + com.jujutsuenhancement.combat.LimitlessTechnique.invest(p)));
                    return 1;
                }))).then(Commands.literal("technique").then(Commands.literal("clear").executes(ctx -> {
                    com.jujutsuenhancement.combat.LimitlessTechnique.clear(ctx.getSource().getPlayerOrException());
                    return 1;
                })).then(Commands.literal("yuji").then(Commands.literal("clear").executes(ctx -> {
                    com.jujutsuenhancement.combat.YujiTechnique.clear(ctx.getSource().getPlayerOrException());
                    return 1;
                })))).then(Commands.literal("fingers").then(Commands.argument("n", IntegerArgumentType.integer(0, 20)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    com.jujutsuenhancement.combat.SukunaTechnique.setFingers(p, IntegerArgumentType.getInteger(ctx, "n"));
                    p.sendSystemMessage(Component.literal("宿傩手指数 " + com.jujutsuenhancement.combat.SukunaTechnique.fingers(p)));
                    return 1;
                }))).then(Commands.literal("bfspecial").then(Commands.argument("n", IntegerArgumentType.integer(-1000, 1000)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    com.jujutsuenhancement.combat.YujiTechnique.addSpecial(p, IntegerArgumentType.getInteger(ctx, "n"));
                    p.sendSystemMessage(Component.literal("累积黑闪特级生物次数 " + com.jujutsuenhancement.combat.YujiTechnique.special(p)));
                    return 1;
                }))));
    }

    private static int show(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("咒力 " + Energy.get(p) + " / " + Energy.cap(p)));
        return 1;
    }
}
