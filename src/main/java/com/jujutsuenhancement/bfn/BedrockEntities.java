package com.jujutsuenhancement.bfn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jujutsuenhancement.registry.ModEntities;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Bedrock entity definitions of the behaviour pack ({@code bedrock/entity_rt.json}): component groups, components, events, properties.
 * The event interpreter works on any Forge entity: the active component groups are kept in its persistent data ({@code bfn_groups}), the
 * properties as {@code bfn_<name>} numbers (what {@link McEnt#property} reads).
 */
public final class BedrockEntities {
    private static Map<String, JsonObject> defs;

    private BedrockEntities() {}

    private static synchronized void load() {
        if (defs != null) return;
        defs = new java.util.HashMap<>();
        String t = McHost.read("entity_rt.json");
        if (t == null) return;
        for (Map.Entry<String, JsonElement> en : JsonParser.parseString(t).getAsJsonObject().entrySet()) defs.put(en.getKey(), en.getValue().getAsJsonObject());
    }

    /** the definition of a Bedrock entity ({@code ds:projectile}, {@code player}), or null */
    public static JsonObject def(String id) {
        load();
        JsonObject d = defs.get(id);
        if (d == null && !id.contains(":")) d = defs.get("minecraft:" + id);
        return d;
    }

    public static boolean has(String id) {
        return def(id) != null;
    }

    // ---------------------------------------------------------------------------------------------------------------- state

    public static Set<String> groups(Entity e) {
        Set<String> out = new LinkedHashSet<>();
        ListTag l = e.getPersistentData().getList("bfn_groups", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    static void setGroups(Entity e, Set<String> g) {
        ListTag l = new ListTag();
        for (String s : g) l.add(StringTag.valueOf(s));
        e.getPersistentData().put("bfn_groups", l);
    }

    /** the component {@code name}: the base one, replaced by the one of the active component groups */
    public static JsonElement component(Entity e, JsonObject def, String name) {
        if (def == null) return null;
        JsonElement r = null;
        JsonObject c = def.has("c") ? def.getAsJsonObject("c") : null;
        if (c != null && c.has(name)) r = c.get(name);
        if (def.has("g")) {
            JsonObject g = def.getAsJsonObject("g");
            for (String n : groups(e)) {
                if (g.has(n) && g.getAsJsonObject(n).has(name)) r = g.getAsJsonObject(n).get(name);
            }
        }
        return r;
    }

    public static List<String> families(Entity e, JsonObject def) {
        List<String> out = new ArrayList<>();
        JsonElement tf = component(e, def, "minecraft:type_family");
        if (tf != null && tf.isJsonObject() && tf.getAsJsonObject().has("family")) for (JsonElement f : tf.getAsJsonObject().getAsJsonArray("family")) out.add(f.getAsString());
        return out;
    }

    public static void setProperty(Entity e, String name, double v) {
        String n = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        e.getPersistentData().putDouble("bfn_" + n, v);
    }

    /** the default of a property of the entity type, or {@code NaN} when the type has no such property */
    public static double propDefault(String typeId, String name) {
        JsonObject d = def(typeId);
        if (d == null || !d.has("p")) return Double.NaN;
        JsonObject p = d.getAsJsonObject("p");
        String key = name.startsWith("property:") ? name : "property:" + name;
        if (!p.has(key)) return Double.NaN;
        JsonElement df = p.getAsJsonObject(key).get("default");
        if (df == null || !df.isJsonPrimitive()) return 0;
        if (df.getAsJsonPrimitive().isBoolean()) return df.getAsBoolean() ? 1 : 0;
        return df.getAsDouble();
    }

    /** the defaults of all properties of the type, written where missing */
    static void initProperties(Entity e, String typeId) {
        JsonObject d = def(typeId);
        if (d == null || !d.has("p")) return;
        for (String k : d.getAsJsonObject("p").keySet()) {
            String n = k.substring(k.indexOf(':') + 1);
            if (!e.getPersistentData().contains("bfn_" + n)) setProperty(e, n, propDefault(typeId, k));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- creating

    /** The Forge entity for a Bedrock id: a type the mod registers itself, else the generic Bedrock entity (when the pack defines the id). */
    public static Entity create(ServerLevel lv, String bedrockId) {
        if (bedrockId.equals("xp_bottle")) bedrockId = "experience_bottle";
        else if (bedrockId.equals("fireworks_rocket")) bedrockId = "firework_rocket";
        String path = bedrockId.startsWith("ds:") ? bedrockId.substring(3) : bedrockId;
        ResourceLocation id = bedrockId.startsWith("ds:") ? new ResourceLocation("jujutsuenhancement", path)
                : bedrockId.equals("xp_orb") ? new ResourceLocation("minecraft", "experience_orb")
                : new ResourceLocation(bedrockId.contains(":") ? bedrockId : "minecraft:" + bedrockId);
        if (ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(id);
            Entity e = t == null ? null : t.create(lv);
            if (e != null) return e;
        }
        if (has(bedrockId) && !bedrockId.startsWith("minecraft:")) {
            BfnEntity b = ModEntities.BFN_ENTITY.get().create(lv);
            if (b != null) {
                b.setBid(bedrockId);
                return b;
            }
        }
        return null;
    }

    /** a freshly added entity: {@code minecraft:entity_spawned}, then the spawn event of the summon */
    public static void start(Entity e, String spawnEvent) {
        String id = McEnt.bedrockId(e);
        initProperties(e, id);
        fire(e, "minecraft:entity_spawned", null);
        if (spawnEvent != null && !spawnEvent.isEmpty() && !spawnEvent.equals("none")) fire(e, spawnEvent, null);
    }

    // ---------------------------------------------------------------------------------------------------------------- events

    /** fires the event of the entity's definition (nothing when the entity or the event is unknown) */
    public static void fire(Entity e, String event, Entity other) {
        JsonObject def = def(McEnt.bedrockId(e));
        if (def == null || !def.has("e")) return;
        JsonObject evs = def.getAsJsonObject("e");
        if (!evs.has(event)) return;
        run(e, def, evs.get(event), other, 0);
    }

    private static void run(Entity e, JsonObject def, JsonElement r, Entity other, int depth) {
        if (r == null || depth > 12 || e.isRemoved()) return;
        if (r.isJsonArray()) {
            for (JsonElement x : r.getAsJsonArray()) run(e, def, x, other, depth + 1);
            return;
        }
        if (!r.isJsonObject()) return;
        JsonObject o = r.getAsJsonObject();
        if (o.has("filters") && !Filters.test(o.get("filters"), e, other, def)) return;
        if (o.has("randomize") && o.get("randomize").isJsonArray()) {
            JsonArray a = o.getAsJsonArray("randomize");
            double total = 0;
            for (JsonElement x : a) total += x.getAsJsonObject().has("weight") ? x.getAsJsonObject().get("weight").getAsDouble() : 1;
            double pick = ThreadLocalRandom.current().nextDouble() * total;
            for (JsonElement x : a) {
                pick -= x.getAsJsonObject().has("weight") ? x.getAsJsonObject().get("weight").getAsDouble() : 1;
                if (pick <= 0) {
                    run(e, def, x, other, depth + 1);
                    break;
                }
            }
        }
        if (o.has("remove")) changeGroups(e, o.get("remove"), false);
        if (o.has("add")) changeGroups(e, o.get("add"), true);
        if (o.has("set_property") && o.get("set_property").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("set_property").entrySet()) {
                JsonElement v = en.getValue();
                if (v.isJsonPrimitive()) {
                    if (v.getAsJsonPrimitive().isBoolean()) setProperty(e, en.getKey(), v.getAsBoolean() ? 1 : 0);
                    else if (v.getAsJsonPrimitive().isNumber()) setProperty(e, en.getKey(), v.getAsDouble());
                }
            }
        }
        for (String k : new String[] {"queue_command", "run_command"}) {
            if (!o.has(k) || !o.get(k).isJsonObject()) continue;
            JsonElement c = o.getAsJsonObject(k).get("command");
            if (c == null) continue;
            if (c.isJsonArray()) for (JsonElement x : c.getAsJsonArray()) command(e, x.getAsString());
            else command(e, c.getAsString());
        }
        if (o.has("sequence") && o.get("sequence").isJsonArray()) for (JsonElement x : o.getAsJsonArray("sequence")) run(e, def, x, other, depth + 1);
        if (o.has("trigger")) {
            JsonElement t = o.get("trigger");
            String name = t.isJsonObject() ? (t.getAsJsonObject().has("event") ? t.getAsJsonObject().get("event").getAsString() : "") : t.getAsString();
            if (!name.isEmpty() && def.has("e") && def.getAsJsonObject("e").has(name)) run(e, def, def.getAsJsonObject("e").get(name), other, depth + 1);
        }
    }

    private static void changeGroups(Entity e, JsonElement spec, boolean add) {
        if (!spec.isJsonObject() || !spec.getAsJsonObject().has("component_groups")) return;
        Set<String> g = groups(e);
        for (JsonElement x : spec.getAsJsonObject().getAsJsonArray("component_groups")) {
            if (add) g.add(x.getAsString());
            else g.remove(x.getAsString());
        }
        setGroups(e, g);
    }

    private static void command(Entity e, String cmd) {
        if (e instanceof BfnEntity b) b.queue(cmd);
        else BfnRuntime.runAs(e, cmd);
    }

    // ---------------------------------------------------------------------------------------------------------------- filters

    /** Bedrock filter trees (all_of / any_of / none_of / single tests) over the entities involved. */
    static final class Filters {
        private Filters() {}

        static boolean test(JsonElement f, Entity self, Entity other, JsonObject def) {
            if (f == null) return true;
            if (f.isJsonArray()) {
                for (JsonElement x : f.getAsJsonArray()) if (!test(x, self, other, def)) return false;
                return true;
            }
            if (!f.isJsonObject()) return true;
            JsonObject o = f.getAsJsonObject();
            if (o.has("all_of")) for (JsonElement x : o.getAsJsonArray("all_of")) if (!test(x, self, other, def)) return false;
            if (o.has("any_of")) {
                boolean any = false;
                for (JsonElement x : o.getAsJsonArray("any_of")) if (test(x, self, other, def)) {
                    any = true;
                    break;
                }
                if (!any) return false;
            }
            if (o.has("none_of")) for (JsonElement x : o.getAsJsonArray("none_of")) if (test(x, self, other, def)) return false;
            if (o.has("test")) return single(o, self, other, def);
            return true;
        }

        private static String str(JsonObject o, String k) {
            JsonElement v = o.get(k);
            return v == null || v.isJsonNull() ? null : v.getAsString();
        }

        private static boolean single(JsonObject o, Entity self, Entity other, JsonObject def) {
            String test = str(o, "test");
            String subject = str(o, "subject");
            String op = str(o, "operator");
            JsonElement val = o.get("value");
            Entity s = subject == null || subject.equals("self") ? self : other;
            boolean neg = "!=".equals(op) || "not".equals(op);
            boolean r;
            switch (test) {
                case "is_family" -> {
                    if (s == null) {
                        r = false;
                        break;
                    }
                    List<String> fam = McHost.families(new McEnt(s, 0));
                    r = val == null || fam.contains(val.getAsString());
                }
                case "has_tag" -> r = s != null && val != null && s.getTags().contains(val.getAsString());
                case "is_underwater" -> r = s != null && s.isUnderWater() == (val == null || val.getAsBoolean());
                case "in_water" -> r = s != null && s.isInWater() == (val == null || val.getAsBoolean());
                case "is_daytime" -> r = s != null && s.level().isDay() == (val == null || val.getAsBoolean());
                case "has_component" -> {
                    String c = val == null ? "" : val.getAsString();
                    if (s == null) r = false;
                    else if (c.equals("minecraft:armor_stand")) r = s instanceof ArmorStand;
                    else if (s instanceof BfnEntity b) r = component(b, b.def(), c) != null;
                    else r = false;
                }
                case "bool_property", "int_property", "float_property", "enum_property" -> {
                    if (s == null) {
                        r = false;
                        break;
                    }
                    double cur = new McEnt(s, 0).property(str(o, "domain") == null ? "" : str(o, "domain"));
                    if (val != null && val.isJsonPrimitive() && val.getAsJsonPrimitive().isBoolean()) r = (cur != 0) == val.getAsBoolean();
                    else return compare(cur, op, val == null ? 0 : val.getAsDouble());
                }
                case "hourly_clock_time" -> {
                    return s != null && compare(s.level().getDayTime() % 24000L, op, val == null ? 0 : val.getAsDouble());
                }
                case "actor_health" -> {
                    return s instanceof LivingEntity le && compare(le.getHealth(), op, val == null ? 0 : val.getAsDouble());
                }
                case "distance_to_nearest_player" -> {
                    var p = s == null ? null : s.level().getNearestPlayer(s, 256);
                    return p != null && compare(p.distanceTo(s), op, val == null ? 0 : val.getAsDouble());
                }
                case "is_game_rule" -> {
                    String d = str(o, "domain");
                    boolean on = d == null || !d.equalsIgnoreCase("mobgriefing") || (s != null && s.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
                    r = on == (val == null || val.getAsBoolean());
                }
                case "is_difficulty" -> r = s != null && val != null && s.level().getDifficulty().getKey().equalsIgnoreCase(val.getAsString());
                case "is_skin_id", "is_mark_variant" -> {
                    JsonElement c = s instanceof BfnEntity b ? component(b, b.def(), test.equals("is_skin_id") ? "minecraft:skin_id" : "minecraft:mark_variant") : null;
                    double cur = c != null && c.isJsonObject() && c.getAsJsonObject().has("value") ? c.getAsJsonObject().get("value").getAsDouble() : 0;
                    return compare(cur, op, val == null ? 0 : val.getAsDouble());
                }
                case "has_damage" -> r = true;
                case "is_owner", "has_biome_tag", "is_biome", "is_snow_covered", "has_trade_supply", "is_leashed_to" -> r = false;
                default -> r = false;
            }
            return neg != r;
        }

        private static boolean compare(double a, String op, double b) {
            if (op == null || op.equals("==") || op.equals("equals")) return a == b;
            return switch (op) {
                case "!=", "not" -> a != b;
                case "<" -> a < b;
                case ">" -> a > b;
                case "<=" -> a <= b;
                case ">=" -> a >= b;
                default -> false;
            };
        }
    }
}
