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

    /** true while the Bedrock command engine drives the game (the hand-ported techniques stand back) */
    public static boolean isEnabled() {
        return enabled;
    }

    /** runs a command line as the player (a form's result: "function point_strength"); only while the engine is on */
    public static void runCommandAs(net.minecraft.server.level.ServerPlayer p, String cmd) {
        if (!enabled) return;
        runAs(p, cmd);
    }

    /** runs {@code r} after {@code n} ticks (system.runTimeout) */
    public static void afterTicks(int n, Runnable r) {
        if (host == null) return;
        int[] left = {n};
        host.addTask(() -> {
            if (--left[0] > 0) return false;
            r.run();
            return true;
        });
    }

    /** sets a score of a player (0..) in the engine's scoreboard */
    public static void setScore(net.minecraft.server.level.ServerPlayer p, String objective, int v) {
        if (host == null) return;
        host.scores().set(objective, p.getGameProfile().getName(), v);
    }

    /** a fake-name score such as {@code destructionlevel Mode}; null if absent */
    public static Integer fakeScore(String objective, String holder) {
        return host == null ? null : host.scores().get(objective, holder);
    }

    /** the score of a player in an objective while the engine runs (0 if it has none), -1 is never returned; see {@link #isEnabled()} */
    public static int score(net.minecraft.server.level.ServerPlayer p, String objective) {
        if (host == null) return 0;
        Integer v = host.scores().get(objective, p.getGameProfile().getName());
        return v == null ? 0 : v;
    }

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

    /** runs a command line as an entity at its position (entity events: queue_command) */
    static void runAs(net.minecraft.world.entity.Entity e, String cmd) {
        MinecraftServer s = e.getServer();
        if (s == null) return;
        ensure(s);
        host.refreshIfStale();
        if (engine.budget < 50_000) engine.budget = 50_000;
        McEnt m = new McEnt(e, McHost.dimOf(e.level()));
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        engine.runLine(c, new Ctx(m, e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), false, m.dim()));
    }

    // ---------------------------------------------------------------------------------------------------------------- join

    /** the add-on runs the function "joined" as every player that joins the world */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return;
        ensure(p.getServer());
        host.addTask(() -> {
            if (!p.isRemoved()) runAs(p, "function joined");
            return true;
        });
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerInput.forget(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------------------------------------------------------- item use

    /** the tags of this tick's item uses: removed again after the root functions have run (the add-on keeps them for one tick) */
    private static final List<Object[]> itemTags = new ArrayList<>();

    private static String bedrockItem(net.minecraft.world.item.ItemStack st) {
        var k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem());
        if (k == null) return "";
        return k.getNamespace().equals("jujutsuenhancement") ? "ds:" + k.getPath() : k.toString();
    }

    private static void itemUsed(net.minecraft.world.entity.player.Player p, net.minecraft.world.item.ItemStack st) {
        if (!enabled || p.level().isClientSide || st.isEmpty()) return;
        String key = "itemUse:" + bedrockItem(st);
        if (p.getTags().contains(key)) return;
        p.addTag(key);
        p.addTag("itemUse");
        itemTags.add(new Object[] {p, key});
    }

    @SubscribeEvent
    public static void onUseItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) itemUsed(event.getEntity(), event.getItemStack());
    }

    @SubscribeEvent
    public static void onUseItemOnBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) itemUsed(event.getEntity(), event.getItemStack());
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
        if (host != null) {
            host.tickTemp();
            host.tickTasks();
        }
        if (!enabled) return;
        MinecraftServer server = event.getServer();
        ensure(server);
        long t0 = System.nanoTime();
        host.refresh();
        PlayerInput.tick(host, engine, server);
        engine.budget = 400_000;
        for (String r : roots) engine.runFunction(r, host.rootCtx());
        for (Object[] it : itemTags) {
            var p = (net.minecraft.world.entity.player.Player) it[0];
            p.removeTag((String) it[1]);
            p.removeTag("itemUse");
        }
        itemTags.clear();
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
