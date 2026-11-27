package com.jujutsuenhancement.bfn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.net.FxOncePacket;
import com.jujutsuenhancement.net.ModNet;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/** The Forge world the command engine runs in: entities, blocks, the functions of the behaviour pack and every world-changing command. */
public final class McHost implements Host {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BASE = "/assets/jujutsuenhancement/bedrock/";
    private static final Set<String> IGNORED = Set.of("gamerule", "tickingarea", "hud", "fog", "stopsound", "setmaxplayers", "kick", "deop", "op",
            "dialogue", "w", "msg", "say", "mobevent", "me", "difficulty", "weather", "time", "setworldspawn", "ability", "inputpermission");

    private static Map<String, JsonObject> entityDefs;
    private static Map<String, String> soundMap;

    final MinecraftServer server;
    private final Scores scores;
    private final Map<String, Map<String, List<String>>> bundles = new HashMap<>();
    private final List<List<McEnt>> byDim = new ArrayList<>();
    private final List<List<McEnt>> playersByDim = new ArrayList<>();
    /** commands the engine could not run: what -> count (shown by /jje bfn missing) */
    public final Map<String, Integer> missing = new TreeMap<>();
    private final java.util.Random random = new java.util.Random();

    public McHost(MinecraftServer server, Scores scores) {
        this.server = server;
        this.scores = scores;
        for (int i = 0; i < 3; i++) {
            byDim.add(new ArrayList<>());
            playersByDim.add(new ArrayList<>());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- resources

    static String read(String path) {
        try (InputStream in = McHost.class.getResourceAsStream(BASE + path)) {
            if (in == null) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1 << 16];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                return sb.toString();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static synchronized void loadTables() {
        if (entityDefs != null) return;
        entityDefs = new HashMap<>();
        soundMap = new HashMap<>();
        String t = read("entities.json");
        if (t != null) for (Map.Entry<String, JsonElement> en : JsonParser.parseString(t).getAsJsonObject().entrySet()) entityDefs.put(en.getKey(), en.getValue().getAsJsonObject());
        t = read("sounds_map.json");
        if (t != null) for (Map.Entry<String, JsonElement> en : JsonParser.parseString(t).getAsJsonObject().entrySet()) soundMap.put(en.getKey(), en.getValue().getAsString());
    }

    static List<String> families(McEnt m) {
        loadTables();
        String id = m.typeId();
        JsonObject d = entityDefs.get(id.contains(":") ? id : "minecraft:" + id);
        List<String> out = new ArrayList<>();
        if (d != null) for (JsonElement f : d.getAsJsonArray("f")) out.add(f.getAsString());
        if (m.e instanceof Player) {
            if (!out.contains("player")) out.add("player");
        } else if (m.e instanceof Monster) {
            if (!out.contains("monster")) out.add("monster");
            if (!out.contains("mob")) out.add("mob");
        } else if (m.e instanceof LivingEntity && !out.contains("mob")) out.add("mob");
        else if (!(m.e instanceof LivingEntity) && !out.contains("inanimate") && d == null) out.add("inanimate");
        return out;
    }

    @Override
    public List<String> functionLines(String path) {
        String top = path.contains("/") ? path.substring(0, path.indexOf('/')) : "_root";
        Map<String, List<String>> b = bundles.get(top);
        if (b == null) {
            b = new HashMap<>();
            String t = read("functions/" + top + ".txt");
            if (t != null) {
                String cur = null;
                List<String> lines = null;
                for (String ln : t.split("\n")) {
                    if (ln.startsWith("@@")) {
                        cur = ln.substring(2);
                        lines = new ArrayList<>();
                        b.put(cur, lines);
                    } else if (lines != null) lines.add(ln);
                }
            }
            bundles.put(top, b);
        }
        return b.get(path);
    }

    // ---------------------------------------------------------------------------------------------------------------- world

    private ServerLevel level(int dim) {
        ResourceKey<Level> k = dim == 1 ? Level.NETHER : dim == 2 ? Level.END : Level.OVERWORLD;
        return server.getLevel(k);
    }

    static int dimOf(Level l) {
        ResourceKey<Level> k = l.dimension();
        return k == Level.NETHER ? 1 : k == Level.END ? 2 : 0;
    }

    /** the entity lists of this tick */
    public void refresh() {
        for (int d = 0; d < 3; d++) {
            List<McEnt> all = byDim.get(d), pl = playersByDim.get(d);
            all.clear();
            pl.clear();
            ServerLevel lv = level(d);
            if (lv == null) continue;
            for (Entity e : lv.getAllEntities()) {
                McEnt m = new McEnt(e, d);
                all.add(m);
                if (e instanceof Player) pl.add(m);
            }
        }
    }

    @Override
    public Iterable<? extends Ent> entities(int dim) {
        return byDim.get(Math.max(0, Math.min(2, dim)));
    }

    @Override
    public List<? extends Ent> players(int dim) {
        return playersByDim.get(Math.max(0, Math.min(2, dim)));
    }

    @Override
    public Scores scores() {
        return scores;
    }

    @Override
    public String blockId(int dim, int x, int y, int z) {
        ServerLevel lv = level(dim);
        if (lv == null) return "air";
        BlockPos p = new BlockPos(x, y, z);
        if (!lv.hasChunkAt(p) || lv.isOutsideBuildHeight(p)) return "air";
        ResourceLocation k = ForgeRegistries.BLOCKS.getKey(lv.getBlockState(p).getBlock());
        return k == null ? "air" : k.getPath();
    }

    @Override
    public void unknown(String what) {
        missing.merge(what, 1, Integer::sum);
        if (missing.get(what) == 1 && missing.size() < 400) LOGGER.debug("[bfn] not run: {}", what);
    }

    // ---------------------------------------------------------------------------------------------------------------- commands

    private static String bare(String s) {
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    private List<ServerPlayer> players(Engine eng, Ctx c, String sel) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Ent e : eng.select(sel, c)) if (e.handle() instanceof ServerPlayer sp) out.add(sp);
        return out;
    }

    static void send(ServerPlayer sp, String op, double[] d, String s) {
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new FxOncePacket(op, d, s));
    }

    private void near(Ctx c, double x, double y, double z, double r, String op, double[] d, String s) {
        ServerLevel lv = level(c.dim());
        if (lv == null) return;
        ModNet.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, r, lv.dimension())), new FxOncePacket(op, d, s));
    }

    /** the text of a command after "<verb> <target> <keyword> " (title / titleraw), as written */
    private static String tail(Line ln, String verb, int skipWords) {
        String r = ln.raw;
        int st = ln.subs.isEmpty() ? 0 : r.lastIndexOf(" run " + verb + " ") + 5;
        if (st < 0) return "";
        int i = st + verb.length();
        for (int k = 0; k < skipWords; k++) {
            while (i < r.length() && r.charAt(i) == ' ') i++;
            int depth = 0;
            while (i < r.length() && (r.charAt(i) != ' ' || depth > 0)) {
                char ch = r.charAt(i);
                if (ch == '[') depth++;
                else if (ch == ']') depth--;
                i++;
            }
        }
        return i + 1 <= r.length() ? r.substring(Math.min(i + 1, r.length())) : "";
    }

    /** text parts of a rawtext json; translate keys are not resolved */
    private static String rawText(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            JsonArray a = o.getAsJsonArray("rawtext");
            if (a != null) for (JsonElement el : a) {
                JsonObject p = el.getAsJsonObject();
                if (p.has("text")) sb.append(p.get("text").getAsString());
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static int glyph(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0xE300 && ch <= 0xE3FF) return ch;
        }
        return 0;
    }

    @Override
    public void command(Engine eng, Ctx c, Line ln) {
        try {
            run(eng, c, ln);
        } catch (RuntimeException ex) {
            unknown("error " + ln.verb + ": " + ex.getClass().getSimpleName());
        }
    }

    private void run(Engine eng, Ctx c, Line ln) {
        List<String> a = ln.args;
        switch (ln.verb) {
            case "particle" -> particle(eng, c, a);
            case "playsound" -> playsound(eng, c, a);
            case "camerashake" -> {
                // camerashake add <target> <intensity> <seconds> [type]
                if (a.size() >= 4 && a.get(0).equals("add")) {
                    double amp = Double.parseDouble(a.get(2)), sec = Double.parseDouble(a.get(3));
                    for (ServerPlayer sp : players(eng, c, a.get(1))) send(sp, "K", new double[] {amp, Math.round(sec * 20.0)}, "");
                }
            }
            case "camera" -> camera(eng, c, a);
            case "title" -> title(eng, c, ln, false);
            case "titleraw" -> title(eng, c, ln, true);
            case "tellraw" -> {
                if (a.size() < 2) return;
                String t = rawText(tail(ln, "tellraw", 1));
                if (!t.isBlank()) for (ServerPlayer sp : players(eng, c, a.get(0))) send(sp, "M", new double[0], t);
            }
            case "tp", "teleport" -> tp(eng, c, a);
            case "damage" -> damage(eng, c, a);
            case "effect" -> effect(eng, c, a);
            case "kill" -> {
                for (Ent e : eng.select(a.isEmpty() ? "@s" : a.get(0), c)) ((Entity) e.handle()).kill();
            }
            case "fill" -> fill(eng, c, a);
            case "setblock" -> setblock(eng, c, a);
            case "summon" -> summon(eng, c, a);
            case "scriptevent" -> {
                if (!a.isEmpty() && !ScriptEvents.run(this, c, a.get(0), a.subList(1, a.size()))) unknown("scriptevent " + a.get(0));
            }
            case "playanimation" -> {
                if (a.size() >= 2) {
                    String anim = a.get(1).startsWith("animation.") ? a.get(1).substring(10) : a.get(1);
                    for (Ent e : eng.select(a.get(0), c)) {
                        if (e.handle() instanceof ServerPlayer sp) ModNet.playAnim(sp, anim, 0);
                        else unknown("playanimation on a non-player");
                    }
                }
            }
            default -> {
                if (!IGNORED.contains(ln.verb)) unknown(ln.verb + (a.isEmpty() ? "" : " " + a.get(0)));
            }
        }
    }

    private void particle(Engine eng, Ctx c, List<String> a) {
        if (a.isEmpty()) return;
        String name = a.get(0);
        if (!name.startsWith("ds:")) return;
        double[] p = {c.x(), c.y(), c.z()};
        if (a.size() >= 2) p = eng.resolve(Coords.read(a, 1, new int[1]), c);
        near(c, p[0], p[1], p[2], 128.0, "P", new double[] {p[0], p[1], p[2], c.yaw()}, name.substring(3));
    }

    private void playsound(Engine eng, Ctx c, List<String> a) {
        if (a.isEmpty()) return;
        loadTables();
        String id = soundMap.get(a.get(0));
        if (id == null) {
            unknown("sound " + a.get(0));
            return;
        }
        String target = a.size() > 1 ? a.get(1) : "@s";
        double[] p = {c.x(), c.y(), c.z()};
        int[] nx = {2};
        if (a.size() > 2) {
            p = eng.resolve(Coords.read(a, 2, nx), c);
        }
        double vol = a.size() > nx[0] ? Double.parseDouble(a.get(nx[0])) : 1.0;
        double pitch = a.size() > nx[0] + 1 ? Double.parseDouble(a.get(nx[0] + 1)) : 1.0;
        for (ServerPlayer sp : players(eng, c, target)) send(sp, "S", new double[] {p[0], p[1], p[2], vol, pitch}, id);
    }

    private void camera(Engine eng, Ctx c, List<String> a) {
        if (a.size() < 2) return;
        List<ServerPlayer> who = players(eng, c, a.get(0));
        switch (a.get(1)) {
            case "clear" -> {
                for (ServerPlayer sp : who) send(sp, "X", new double[0], "");
            }
            case "fade" -> {
                int ti = a.indexOf("time"), ci = a.indexOf("color");
                double in = 0, hold = 0, out = 0;
                int r = 0, g = 0, b = 0;
                if (ti >= 0 && a.size() > ti + 3) {
                    in = Double.parseDouble(a.get(ti + 1));
                    hold = Double.parseDouble(a.get(ti + 2));
                    out = Double.parseDouble(a.get(ti + 3));
                }
                if (ci >= 0 && a.size() > ci + 3) {
                    r = (int) Double.parseDouble(a.get(ci + 1));
                    g = (int) Double.parseDouble(a.get(ci + 2));
                    b = (int) Double.parseDouble(a.get(ci + 3));
                }
                for (ServerPlayer sp : who) send(sp, "F", new double[] {in, hold, out, r, g, b}, "");
            }
            case "set" -> {
                // set minecraft:free [ease <seconds> <type>] [pos <x y z>] [facing <x y z> | rot <pitch> <yaw>]
                int i = 3;
                double ease = 0;
                String easeName = "linear";
                if (i < a.size() && a.get(i).equals("ease")) {
                    ease = Double.parseDouble(a.get(i + 1));
                    easeName = a.get(i + 2);
                    i += 3;
                }
                if (i >= a.size() || !a.get(i).equals("pos")) return;
                int[] nx = new int[1];
                double[] pos = eng.resolve(Coords.read(a, i + 1, nx), c);
                i = nx[0];
                double yaw = c.yaw(), pitch = c.pitch();
                if (i < a.size() && a.get(i).equals("facing")) {
                    double[] look;
                    if (a.get(i + 1).startsWith("@")) {
                        List<Ent> es = eng.select(a.get(i + 1), c);
                        if (es.isEmpty()) return;
                        look = new double[] {es.get(0).x(), es.get(0).y() + es.get(0).eyeHeight(), es.get(0).z()};
                    } else look = eng.resolve(Coords.read(a, i + 1, nx), c);
                    double dx = look[0] - pos[0], dy = look[1] - pos[1], dz = look[2] - pos[2];
                    yaw = Math.toDegrees(Math.atan2(-dx, dz));
                    pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                } else if (i < a.size() && a.get(i).equals("rot") && a.size() > i + 2) {
                    pitch = rel(a.get(i + 1), c.pitch());
                    yaw = rel(a.get(i + 2), c.yaw());
                }
                for (ServerPlayer sp : who) send(sp, "C", new double[] {ease, pos[0], pos[1], pos[2], yaw, pitch}, easeName);
            }
            default -> unknown("camera " + a.get(1));
        }
    }

    private static double rel(String s, double base) {
        if (s.startsWith("~")) return base + (s.length() == 1 ? 0 : Double.parseDouble(s.substring(1)));
        return Double.parseDouble(s);
    }

    private void title(Engine eng, Ctx c, Line ln, boolean raw) {
        List<String> a = ln.args;
        if (a.size() < 2) return;
        String kind = a.get(1);
        List<ServerPlayer> who = players(eng, c, a.get(0));
        String text = tail(ln, ln.verb, 2);
        switch (kind) {
            case "title" -> {
                int g = glyph(text);
                if (g != 0) for (ServerPlayer sp : who) send(sp, "I", new double[] {g}, "");
                else if (text.contains("§l") || text.isBlank() || text.strip().equals("")) for (ServerPlayer sp : who) send(sp, "I", new double[] {0}, "");
                else unknown("title text");
            }
            case "actionbar" -> {
                String t = raw ? rawText(text) : text;
                if (!t.isBlank()) for (ServerPlayer sp : who) send(sp, "A", new double[0], t);
            }
            default -> { }
        }
    }

    private void tp(Engine eng, Ctx c, List<String> a) {
        if (a.isEmpty()) return;
        int i = 0;
        List<Ent> victims;
        boolean firstIsCoord = a.get(0).startsWith("~") || a.get(0).startsWith("^") || Character.isDigit(a.get(0).charAt(0)) || a.get(0).startsWith("-");
        if (firstIsCoord) {
            victims = c.self() == null ? List.of() : List.of(c.self());
        } else {
            victims = eng.select(a.get(0), c);
            i = 1;
        }
        if (i >= a.size()) return;
        double[] pos;
        double yaw = Double.NaN, pitch = Double.NaN;
        int[] nx = {i};
        boolean entityDest = !(a.get(i).startsWith("~") || a.get(i).startsWith("^") || Character.isDigit(a.get(i).charAt(0)) || a.get(i).startsWith("-"));
        if (entityDest) {
            List<Ent> d = eng.select(a.get(i), c);
            if (d.isEmpty()) return;
            pos = new double[] {d.get(0).x(), d.get(0).y(), d.get(0).z()};
            nx[0] = i + 1;
        } else {
            pos = eng.resolve(Coords.read(a, i, nx), c);
        }
        int k = nx[0];
        if (k < a.size() && a.get(k).equals("facing") && a.size() > k + 1) {
            double[] look;
            if (a.get(k + 1).startsWith("@")) {
                List<Ent> es = eng.select(a.get(k + 1), c);
                if (es.isEmpty()) return;
                look = new double[] {es.get(0).x(), es.get(0).y() + es.get(0).eyeHeight(), es.get(0).z()};
            } else look = eng.resolve(Coords.read(a, k + 1, new int[1]), c);
            for (Ent v : victims) {
                double dx = look[0] - pos[0], dy = look[1] - (pos[1] + v.eyeHeight()), dz = look[2] - pos[2];
                move(v, pos, Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            }
            return;
        }
        for (Ent v : victims) {
            double yw = v.yaw(), pt = v.pitch();
            if (k < a.size() && !a.get(k).equals("true") && !a.get(k).equals("false")) {
                yw = rel(a.get(k), v.yaw());
                if (k + 1 < a.size() && !a.get(k + 1).equals("true") && !a.get(k + 1).equals("false")) pt = rel(a.get(k + 1), v.pitch());
            }
            move(v, pos, yw, pt);
        }
    }

    private void move(Ent v, double[] pos, double yaw, double pitch) {
        Entity e = (Entity) v.handle();
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo((ServerLevel) sp.level(), pos[0], pos[1], pos[2], (float) yaw, (float) pitch);
        } else {
            e.moveTo(pos[0], pos[1], pos[2], (float) yaw, (float) pitch);
        }
        e.fallDistance = 0;
    }

    private void damage(Engine eng, Ctx c, List<String> a) {
        if (a.size() < 2) return;
        float amount = (float) Double.parseDouble(a.get(1));
        String cause = a.size() > 2 ? a.get(2) : "override";
        Entity attacker = null;
        int ei = a.indexOf("entity");
        if (ei >= 0 && a.size() > ei + 1) {
            List<Ent> es = eng.select(a.get(ei + 1), c);
            if (!es.isEmpty()) attacker = (Entity) es.get(0).handle();
        }
        for (Ent t : eng.select(a.get(0), c)) {
            if (!(t.handle() instanceof LivingEntity le) || amount <= 0) continue;
            if (le instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) continue;
            var src = le.level().damageSources();
            DamageSource ds;
            if (attacker instanceof Player p) ds = src.playerAttack(p);
            else if (attacker instanceof LivingEntity al) ds = src.mobAttack(al);
            else ds = cause.equals("override") || cause.equals("self_destruct") ? src.genericKill() : src.generic();
            int inv = le.invulnerableTime;
            le.invulnerableTime = 0;   // Bedrock damage does not wait for the hurt cooldown
            le.hurt(ds, amount);
            if (le.isAlive() && inv > 0 && le.invulnerableTime == 0) le.invulnerableTime = inv;
        }
    }

    private static String effectName(String n) {
        n = bare(n);
        return switch (n) {
            case "village_hero" -> "hero_of_the_village";
            case "fatal_poison" -> "poison";
            case "health_boost" -> "health_boost";
            default -> n;
        };
    }

    private void effect(Engine eng, Ctx c, List<String> a) {
        if (a.size() < 2) return;
        List<Ent> ts = eng.select(a.get(0), c);
        if (a.get(1).equals("clear")) {
            for (Ent t : ts) {
                if (!(t.handle() instanceof LivingEntity le)) continue;
                if (a.size() > 2) {
                    MobEffect me = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft", effectName(a.get(2))));
                    if (me != null) le.removeEffect(me);
                } else le.removeAllEffects();
            }
            return;
        }
        MobEffect me = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft", effectName(a.get(1))));
        if (me == null) {
            unknown("effect " + a.get(1));
            return;
        }
        double sec = a.size() > 2 ? Double.parseDouble(a.get(2)) : 30;
        int amp = a.size() > 3 ? (int) Double.parseDouble(a.get(3)) : 0;
        boolean hide = a.size() > 4 && a.get(4).equals("true");
        int ticks = sec >= 1_000_000 ? -1 : (int) Math.min(Integer.MAX_VALUE / 2.0, Math.max(1, Math.round(sec * 20)));
        for (Ent t : ts) {
            if (t.handle() instanceof LivingEntity le) le.addEffect(new MobEffectInstance(me, ticks, Math.max(0, Math.min(255, amp)), false, !hide));
        }
    }

    private BlockState state(String id) {
        String n = bare(id);
        int br = n.indexOf('[');
        if (br >= 0) n = n.substring(0, br);
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", n));
        if (b == null || (b == net.minecraft.world.level.block.Blocks.AIR && !n.equals("air"))) return null;
        return b.defaultBlockState();
    }

    private void fill(Engine eng, Ctx c, List<String> a) {
        // fill x y z x y z block [states] [replace [filter [states]] | destroy | keep | hollow | outline]
        int[] nx = new int[1];
        ServerLevel lv = level(c.dim());
        if (lv == null || a.size() < 7) return;
        Coords c1 = Coords.read(a, 0, nx), c2 = Coords.read(a, nx[0], nx);
        int k = nx[0];
        BlockState to = state(a.get(k));
        if (to == null) {
            unknown("fill block " + a.get(k));
            return;
        }
        k++;
        if (k < a.size() && a.get(k).startsWith("[")) k++;
        String mode = k < a.size() ? a.get(k) : "replace";
        BlockState filter = null;
        boolean filterAir = false;
        if (mode.equals("replace") && k + 1 < a.size()) {
            filter = state(a.get(k + 1));
            filterAir = bare(a.get(k + 1)).equals("air");
        }
        double[] p1 = eng.resolve(c1, c), p2 = eng.resolve(c2, c);
        int x0 = (int) Math.floor(Math.min(p1[0], p2[0])), x1 = (int) Math.floor(Math.max(p1[0], p2[0]));
        int y0 = (int) Math.floor(Math.min(p1[1], p2[1])), y1 = (int) Math.floor(Math.max(p1[1], p2[1]));
        int z0 = (int) Math.floor(Math.min(p1[2], p2[2])), z1 = (int) Math.floor(Math.max(p1[2], p2[2]));
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 32768) return;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            mp.set(x, y, z);
            if (!lv.hasChunkAt(mp) || lv.isOutsideBuildHeight(mp)) continue;
            BlockState cur = lv.getBlockState(mp);
            if (mode.equals("keep") && !cur.isAir()) continue;
            if (mode.equals("replace") && k + 1 < a.size()) {
                if (filter == null) continue;
                if (filterAir ? !cur.isAir() : cur.getBlock() != filter.getBlock()) continue;
            }
            if (mode.equals("hollow") || mode.equals("outline")) {
                boolean edge = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                if (!edge) {
                    if (mode.equals("hollow")) lv.setBlock(mp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    continue;
                }
            }
            if (cur != to) lv.setBlock(mp, to, 2);
        }
    }

    private void setblock(Engine eng, Ctx c, List<String> a) {
        ServerLevel lv = level(c.dim());
        if (lv == null || a.size() < 4) return;
        int[] nx = new int[1];
        double[] p = eng.resolve(Coords.read(a, 0, nx), c);
        BlockState st = state(a.get(nx[0]));
        if (st == null) {
            unknown("setblock " + a.get(nx[0]));
            return;
        }
        BlockPos bp = BlockPos.containing(p[0], p[1], p[2]);
        if (lv.hasChunkAt(bp) && !lv.isOutsideBuildHeight(bp)) lv.setBlock(bp, st, 3);
    }

    private void summon(Engine eng, Ctx c, List<String> a) {
        if (a.isEmpty()) return;
        String t = a.get(0);
        ResourceLocation id = t.startsWith("ds:") ? new ResourceLocation("jujutsuenhancement", t.substring(3))
                : t.equals("xp_orb") ? new ResourceLocation("minecraft", "experience_orb") : new ResourceLocation("minecraft", bare(t));
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            unknown("summon " + t);
            return;
        }
        ServerLevel lv = level(c.dim());
        if (lv == null) return;
        double[] p = {c.x(), c.y(), c.z()};
        if (a.size() >= 2 && (a.get(1).startsWith("~") || a.get(1).startsWith("^") || Character.isDigit(a.get(1).charAt(0)) || a.get(1).startsWith("-"))) {
            p = eng.resolve(Coords.read(a, 1, new int[1]), c);
        }
        Entity e = type.create(lv);
        if (e == null) return;
        e.moveTo(p[0], p[1], p[2], c.yaw(), 0);
        lv.addFreshEntity(e);
        McEnt m = new McEnt(e, c.dim());
        byDim.get(Math.max(0, Math.min(2, c.dim()))).add(m);
    }

    // ---------------------------------------------------------------------------------------------------------------- temporary blocks

    private record Temp(ServerLevel lv, BlockPos pos, long removeAt, BlockState orig) {}

    private final Map<String, Temp> temps = new HashMap<>();

    /** ds:placeblock: a block (light / barrier ...) that is put back after {@code time} ticks; only where the block is {@code replace} (when given) */
    void placeTemp(Ctx c, int radius, int time, String block, Integer light, String replace) {
        ServerLevel lv = level(c.dim());
        if (lv == null) return;
        BlockState st;
        if (block.equals("light_block")) {
            st = net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, light == null ? 15 : light);
        } else {
            st = state(block);
            if (st == null) {
                unknown("placeblock " + block);
                return;
            }
        }
        long removeAt = server.getTickCount() + time;
        int cx = (int) Math.floor(c.x()), cy = (int) Math.floor(c.y()), cz = (int) Math.floor(c.z());
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++) {
            if (x * x + y * y + z * z > radius * radius) continue;
            mp.set(cx + x, cy + y, cz + z);
            if (!lv.hasChunkAt(mp) || lv.isOutsideBuildHeight(mp)) continue;
            BlockState cur = lv.getBlockState(mp);
            if (replace != null && !(replace.equals("air") ? cur.isAir() : bare(String.valueOf(ForgeRegistries.BLOCKS.getKey(cur.getBlock()))).equals(replace))) continue;
            BlockPos fixed = mp.immutable();
            String key = c.dim() + ":" + fixed.asLong();
            if (temps.containsKey(key)) continue;
            temps.put(key, new Temp(lv, fixed, removeAt, cur));
            lv.setBlock(fixed, st, 3);
        }
    }

    /** puts back the temporary blocks whose time is over */
    public void tickTemp() {
        if (temps.isEmpty()) return;
        long now = server.getTickCount();
        var it = temps.values().iterator();
        while (it.hasNext()) {
            Temp t = it.next();
            if (now < t.removeAt()) continue;
            if (t.lv().hasChunkAt(t.pos())) t.lv().setBlock(t.pos(), t.orig(), 3);
            it.remove();
        }
    }

    /** a random source for callers that need one */
    public java.util.Random random() {
        return random;
    }

    /** a point of the world for the root context */
    public Ctx rootCtx() {
        ServerLevel lv = level(0);
        Vec3 sp = lv == null ? Vec3.ZERO : Vec3.atCenterOf(lv.getSharedSpawnPos());
        return new Ctx(null, sp.x, sp.y, sp.z, 0, 0, false, 0);
    }
}
