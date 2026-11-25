package com.jujutsuenhancement.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.net.ModNet;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The shared animation / CG base made testable:
 * <pre>
 * /jje cg anim &lt;name&gt;   plays any of the 1913 Bedrock animations (name without the "animation." prefix) on you
 * /jje cg fx &lt;key&gt;      plays the timeline (particles, sounds, camera shots, fades, impact frames) of any Bedrock function:
 *                       "&lt;folder&gt;/&lt;function&gt;#&lt;counter&gt;", e.g. limitless/limitless5b#move
 * /jje cg stop          ends the cutscene camera
 * </pre>
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID)
public final class CgCommands {
    private static List<String> animNames;
    private static List<String> fxKeys;

    private CgCommands() {}

    private static List<String> read(String path, boolean stripAnim) {
        List<String> out = new ArrayList<>();
        try (var in = CgCommands.class.getResourceAsStream("/assets/" + JujutsuEnhancement.MODID + "/" + path)) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : o.keySet()) out.add(stripAnim && k.startsWith("animation.") ? k.substring(10) : k);
            }
        } catch (Exception ignored) {
            // no suggestions then
        }
        out.sort(null);
        return out;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jje").then(Commands.literal("cg")
                .then(Commands.literal("anim").then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            if (animNames == null) animNames = read("bedrock/anims/index.json", true);
                            return SharedSuggestionProvider.suggest(animNames, b);
                        }).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ModNet.playAnim(p, StringArgumentType.getString(ctx, "name"), 0);
                            return 1;
                        })))
                .then(Commands.literal("fx").then(Commands.argument("key", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            if (fxKeys == null) fxKeys = read("bedrock/fx/index.json", false);
                            return SharedSuggestionProvider.suggest(fxKeys, b);
                        }).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String key = StringArgumentType.getString(ctx, "key");
                            ModNet.moveFx(p, key.startsWith("fx:") || key.contains("#") ? (key.startsWith("fx:") ? key : "fx:" + key) : key);
                            p.sendSystemMessage(Component.literal("播放 " + key));
                            return 1;
                        })))
                .then(Commands.literal("stop").executes(ctx -> {
                    ModNet.moveFx(ctx.getSource().getPlayerOrException(), "cg:stop");
                    return 1;
                }))));
    }
}
