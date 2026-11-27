package com.jujutsuenhancement.bfn;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.JujutsuEnhancement;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Runs the Bedrock add-on's own functions: every server tick the root list of {@code functions/tick.json} (_scores, _missions, _self_cmds,
 * remove_scores). Off until {@code /jje bfn on}; {@code /jje bfn run <function>} runs one function as the player.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BfnRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean enabled;
    private static McHost host;
    private static Engine engine;
    private static BfnData data;
    private static List<String> roots;
    private static int ticks;
    private static long lastCost;

    private BfnRuntime() {}

    private static void ensure(MinecraftServer server) {
        if (host != null) return;
        data = BfnData.get(server);
        host = new McHost(server, data.scores);
        engine = new Engine(host);
        roots = new ArrayList<>();
        String t = McHost.read("functions/tick.json");
        if (t == null) t = "{\"values\":[\"_scores\",\"_missions\",\"_self_cmds\",\"remove_scores\"]}";
        for (JsonElement e : JsonParser.parseString(t).getAsJsonObject().getAsJsonArray("values")) roots.add(e.getAsString());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        if (data != null) data.setDirty();
        if (host != null) host.flushTemps();
        host = null;
        engine = null;
        data = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (host != null) host.tickTemp();
        if (!enabled) return;
        MinecraftServer server = event.getServer();
        ensure(server);
        long t0 = System.nanoTime();
        host.refresh();
        engine.budget = 400_000;
        for (String r : roots) engine.runFunction(r, host.rootCtx());
        lastCost = System.nanoTime() - t0;
        if (++ticks % 200 == 0) data.setDirty();
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jje").requires(s -> s.hasPermission(2)).then(Commands.literal("bfn")
                .then(Commands.literal("on").executes(c -> {
                    enabled = true;
                    c.getSource().sendSuccess(() -> Component.literal("基岩命令引擎: 开"), false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(c -> {
                    enabled = false;
                    c.getSource().sendSuccess(() -> Component.literal("基岩命令引擎: 关"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    String m = enabled ? "开" : "关";
                    String cost = host == null ? "" : String.format("  上一刻 %.2f ms, 未支持命令 %d 种", lastCost / 1e6, host.missing.size());
                    c.getSource().sendSuccess(() -> Component.literal("基岩命令引擎: " + m + cost), false);
                    return 1;
                }))
                .then(Commands.literal("missing").executes(c -> {
                    if (host == null) return 0;
                    StringBuilder sb = new StringBuilder("未支持:");
                    int n = 0;
                    List<Map.Entry<String, Integer>> es = new ArrayList<>(host.missing.entrySet());
                    es.sort((x, y) -> y.getValue() - x.getValue());
                    for (Map.Entry<String, Integer> en : es) {
                        if (n++ >= 25) break;
                        sb.append("\n").append(en.getKey()).append(" x").append(en.getValue());
                    }
                    String s = sb.toString();
                    c.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))
                .then(Commands.literal("run").then(Commands.argument("function", StringArgumentType.greedyString()).executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    ensure(server);
                    host.refresh();
                    engine.budget = 400_000;
                    String fn = StringArgumentType.getString(c, "function");
                    Ctx ctx = host.rootCtx();
                    if (c.getSource().getEntity() != null) {
                        McEnt me = new McEnt(c.getSource().getEntity(), McHost.dimOf(c.getSource().getLevel()));
                        ctx = new Ctx(me, me.x(), me.y(), me.z(), me.yaw(), me.pitch(), false, me.dim());
                    }
                    engine.runFunction(fn, ctx);
                    c.getSource().sendSuccess(() -> Component.literal("已运行 " + fn), false);
                    return 1;
                })))));
    }
}
