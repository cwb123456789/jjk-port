package com.jujutsuenhancement.bfn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jujutsuenhancement.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A behaviour-pack entity (anything the add-on defines that the mod does not implement itself): the components of the Bedrock definition are
 * interpreted here - timers, explode, transformation, despawn, damage_sensor, environment_sensor, on_death, spawn_entity, knockback_roar -
 * and its events run through {@link BedrockEntities#fire}; {@code queue_command} lines run as this entity at its next tick.
 */
public final class BfnEntity extends Entity {
    private static final EntityDataAccessor<String> BID = SynchedEntityData.defineId(BfnEntity.class, EntityDataSerializers.STRING);

    private JsonObject def;
    private String loadedFor = "";
    private float hp = 20;
    private boolean hasHealth;
    private float boxW = 0.6F, boxH = 1.8F;
    private final List<String> queue = new ArrayList<>();
    /** per active component: the countdown etc. */
    private final Map<String, double[]> states = new HashMap<>();
    private int age;

    public BfnEntity(EntityType<? extends BfnEntity> type, Level level) {
        super(type, level);
    }

    public void setBid(String id) {
        entityData.set(BID, id);
        reload();
    }

    public String bid() {
        return entityData.get(BID);
    }

    JsonObject def() {
        if (!loadedFor.equals(bid())) reload();
        return def;
    }

    private void reload() {
        String id = bid();
        loadedFor = id;
        def = BedrockEntities.def(id);
        states.clear();
        if (def == null) return;
        JsonObject box = McHost.boxOf(id);
        if (box != null) {
            boxW = box.getAsJsonArray("b").get(0).getAsFloat();
            boxH = box.getAsJsonArray("b").get(1).getAsFloat();
            refreshDimensions();
        }
        JsonElement h = BedrockEntities.component(this, def, "minecraft:health");
        hasHealth = h != null;
        if (h != null) {
            if (h.isJsonObject() && h.getAsJsonObject().has("value")) hp = h.getAsJsonObject().get("value").getAsFloat();
            else if (h.isJsonPrimitive()) hp = h.getAsFloat();
        }
        JsonElement ph = BedrockEntities.component(this, def, "minecraft:physics");
        boolean gravity = !(ph != null && ph.isJsonObject() && ph.getAsJsonObject().has("has_gravity") && !ph.getAsJsonObject().get("has_gravity").getAsBoolean());
        setNoGravity(!gravity);
    }

    void queue(String cmd) {
        queue.add(cmd);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(boxW, boxH);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BID, "");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("bid")) setBid(tag.getString("bid"));
        if (tag.contains("hp")) hp = tag.getFloat("hp");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("bid", bid());
        tag.putFloat("hp", hp);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ---------------------------------------------------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        JsonObject d = def();
        if (d == null) {
            discard();
            return;
        }
        age++;
        if (!queue.isEmpty()) {
            List<String> q = new ArrayList<>(queue);
            queue.clear();
            for (String c : q) {
                if (isRemoved()) return;
                BfnRuntime.runAs(this, c);
            }
        }
        components(d);
        if (!isRemoved()) physics();
    }

    private void physics() {
        Vec3 m = getDeltaMovement();
        if (isNoGravity() && m.lengthSqr() < 1.0E-7) return;
        if (onGround() && m.lengthSqr() < 1.0E-7) return;
        if (!isNoGravity()) m = m.add(0, -0.04, 0);
        setDeltaMovement(m);
        move(MoverType.SELF, m);
        Vec3 n = getDeltaMovement().scale(0.98);
        if (onGround()) n = new Vec3(n.x * 0.6, 0, n.z * 0.6);
        setDeltaMovement(n);
    }

    private static List<JsonObject> asList(JsonElement e) {
        List<JsonObject> out = new ArrayList<>();
        if (e == null) return out;
        if (e.isJsonObject()) out.add(e.getAsJsonObject());
        else if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) if (x.isJsonObject()) out.add(x.getAsJsonObject());
        return out;
    }

    private static double num(JsonObject o, String k, double dflt) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : dflt;
    }

    /** seconds of a Bedrock timer value: a number, [min, max] or random_time_choices */
    private static double seconds(JsonObject o) {
        JsonElement t = o.get("time");
        if (t != null && t.isJsonArray()) {
            JsonArray a = t.getAsJsonArray();
            double lo = a.get(0).getAsDouble(), hi = a.size() > 1 ? a.get(1).getAsDouble() : lo;
            return lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
        }
        if (t != null && t.isJsonPrimitive()) return t.getAsDouble();
        if (o.has("random_time_choices")) {
            JsonArray a = o.getAsJsonArray("random_time_choices");
            double total = 0;
            for (JsonElement x : a) total += x.getAsJsonObject().get("weight").getAsDouble();
            double pick = ThreadLocalRandom.current().nextDouble() * total;
            for (JsonElement x : a) {
                pick -= x.getAsJsonObject().get("weight").getAsDouble();
                if (pick <= 0) return x.getAsJsonObject().get("value").getAsDouble();
            }
        }
        return 0;
    }

    private static int ticks(double sec) {
        return Math.max(1, (int) Math.round(sec * 20.0));
    }

    private void components(JsonObject d) {
        List<String> owners = new ArrayList<>();
        owners.add("");
        owners.addAll(BedrockEntities.groups(this));
        Set<String> seen = new HashSet<>();
        for (String owner : owners) {
            if (isRemoved()) return;
            if (!owner.isEmpty() && !BedrockEntities.groups(this).contains(owner)) continue;
            JsonObject comps = owner.isEmpty() ? (d.has("c") ? d.getAsJsonObject("c") : null)
                    : (d.has("g") && d.getAsJsonObject("g").has(owner) ? d.getAsJsonObject("g").getAsJsonObject(owner) : null);
            if (comps == null) continue;
            for (String name : new ArrayList<>(comps.keySet())) {
                if (isRemoved()) return;
                JsonElement comp = comps.get(name);
                String key = owner + "|" + name;
                switch (name) {
                    case "minecraft:instant_despawn" -> {
                        discard();
                        return;
                    }
                    case "minecraft:timer" -> {
                        if (!comp.isJsonObject()) break;
                        seen.add(key);
                        JsonObject t = comp.getAsJsonObject();
                        double[] s = states.get(key);
                        if (s == null) {
                            s = new double[] {ticks(seconds(t)), 0};
                            states.put(key, s);
                        }
                        if (s[1] != 0) break;
                        if (--s[0] <= 0) {
                            boolean loop = !t.has("looping") || t.get("looping").getAsBoolean();
                            if (loop) s[0] = ticks(seconds(t));
                            else s[1] = 1;
                            if (t.has("time_down_event") && t.get("time_down_event").isJsonObject()) {
                                JsonObject ev = t.getAsJsonObject("time_down_event");
                                if (ev.has("event")) BedrockEntities.fire(this, ev.get("event").getAsString(), null);
                            }
                        }
                    }
                    case "minecraft:explode" -> {
                        if (!comp.isJsonObject()) break;
                        seen.add(key);
                        JsonObject x = comp.getAsJsonObject();
                        double[] s = states.get(key);
                        if (s == null) {
                            boolean lit = x.has("fuse_lit") && x.get("fuse_lit").getAsBoolean();
                            s = new double[] {lit ? ticks(num(x, "fuse_length", 3)) : -1};
                            states.put(key, s);
                        }
                        if (s[0] > 0 && --s[0] <= 0) {
                            explode(x);
                            return;
                        }
                    }
                    case "minecraft:transformation" -> {
                        if (!comp.isJsonObject()) break;
                        seen.add(key);
                        JsonObject x = comp.getAsJsonObject();
                        double[] s = states.get(key);
                        if (s == null) {
                            s = new double[] {ticks(num(x, "delay", 0))};
                            states.put(key, s);
                        }
                        if (--s[0] <= 0) {
                            morph(x.has("into") ? x.get("into").getAsString() : "");
                            return;
                        }
                    }
                    case "minecraft:despawn" -> {
                        if (comp.isJsonObject() && comp.getAsJsonObject().has("filters")
                                && BedrockEntities.Filters.test(comp.getAsJsonObject().get("filters"), this, null, d)) {
                            discard();
                            return;
                        }
                    }
                    case "minecraft:environment_sensor" -> {
                        if (!comp.isJsonObject() || !comp.getAsJsonObject().has("triggers")) break;
                        int i = 0;
                        for (JsonObject t : asList(comp.getAsJsonObject().get("triggers"))) {
                            String k = key + "#" + i++;
                            seen.add(k);
                            boolean now = !t.has("filters") || BedrockEntities.Filters.test(t.get("filters"), this, null, d);
                            double[] s = states.computeIfAbsent(k, z -> new double[] {0});
                            boolean was = s[0] != 0;
                            s[0] = now ? 1 : 0;
                            if (now && !was && t.has("event")) BedrockEntities.fire(this, t.get("event").getAsString(), null);
                        }
                    }
                    case "minecraft:spawn_entity" -> {
                        int i = 0;
                        for (JsonObject sp : asList(comp.isJsonObject() && comp.getAsJsonObject().has("entities") ? comp.getAsJsonObject().get("entities") : comp)) {
                            String k = key + "#" + i++;
                            seen.add(k);
                            double[] s = states.get(k);
                            if (s == null) {
                                double lo = num(sp, "min_wait_time", 10), hi = num(sp, "max_wait_time", 60);
                                s = new double[] {ticks(lo + ThreadLocalRandom.current().nextDouble() * Math.max(0, hi - lo)), 0};
                                states.put(k, s);
                            }
                            if (s[1] != 0) continue;
                            if (--s[0] <= 0) {
                                spawn(sp);
                                if (!sp.has("single_use") || sp.get("single_use").getAsBoolean()) s[1] = 1;
                                else s[0] = ticks(num(sp, "min_wait_time", 10));
                            }
                        }
                    }
                    case "minecraft:behavior.knockback_roar" -> {
                        if (!comp.isJsonObject()) break;
                        seen.add(key);
                        JsonObject x = comp.getAsJsonObject();
                        double[] s = states.computeIfAbsent(key, z -> new double[] {0});
                        s[0]++;
                        if (s[0] == Math.max(1, Math.round(num(x, "attack_time", 0.5) * 20.0))) roar(x);
                        if (s[0] >= Math.max(1, Math.round(num(x, "duration", 1) * 20.0))) {
                            s[0] = -1_000_000;
                            if (x.has("on_roar_end") && x.get("on_roar_end").isJsonObject() && x.getAsJsonObject("on_roar_end").has("event")) {
                                BedrockEntities.fire(this, x.getAsJsonObject("on_roar_end").get("event").getAsString(), null);
                            }
                        }
                    }
                    default -> {}
                }
            }
        }
        states.keySet().removeIf(k -> !seen.contains(k));
    }

    // ---------------------------------------------------------------------------------------------------------------- actions

    private void explode(JsonObject x) {
        if (!(level() instanceof ServerLevel lv)) return;
        double power = num(x, "power", 3);
        boolean fire = x.has("causes_fire") && x.get("causes_fire").getAsBoolean();
        // blocks break only where a player with the tag "griefable" is in the dimension (same rule as the add-on's destruction scripts)
        boolean grief = false;
        for (var p : lv.players()) if (p.getTags().contains("griefable")) grief = true;
        double px = getX(), py = getY(), pz = getZ();
        discard();
        lv.explode(null, px, py, pz, (float) power, fire && grief, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    private void morph(String into) {
        if (into.isEmpty() || into.startsWith("minecraft:") || !BedrockEntities.has(into) || into.equals(bid())) {
            discard();
            return;
        }
        states.clear();
        BedrockEntities.setGroups(this, new java.util.LinkedHashSet<>());
        setBid(into);
        BedrockEntities.start(this, null);
    }

    private void spawn(JsonObject sp) {
        if (!(level() instanceof ServerLevel lv) || !sp.has("spawn_entity")) return;
        int n = (int) num(sp, "num_to_spawn", 1);
        String id = sp.get("spawn_entity").getAsString();
        String ev = sp.has("spawn_event") ? sp.get("spawn_event").getAsString() : "minecraft:entity_born";
        for (int i = 0; i < n; i++) {
            Entity e = BedrockEntities.create(lv, id);
            if (e == null) continue;
            e.moveTo(getX(), getY(), getZ(), getYRot(), 0);
            lv.addFreshEntity(e);
            BedrockEntities.start(e, ev);
        }
    }

    /**
     * knockback_roar: after attack_time every living entity within knockback_range is thrown away from here. The Bedrock strength unit is not
     * documented; 0.4 blocks per tick per point plus a small lift is used (not verified in game).
     */
    private void roar(JsonObject x) {
        double range = num(x, "knockback_range", 4), strength = num(x, "knockback_strength", 3);
        AABB box = new AABB(getX() - range, getY() - range, getZ() - range, getX() + range, getY() + range, getZ() + range);
        for (Entity t : level().getEntities(this, box, e -> e instanceof LivingEntity && e.isAlive())) {
            Vec3 dir = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
            if (dir.lengthSqr() > range * range) continue;
            ScriptEvents.knock(t, dir, strength * 0.4, 0.4);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- damage / death

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        JsonObject d = def();
        if (d == null) return false;
        JsonElement ds = BedrockEntities.component(this, d, "minecraft:damage_sensor");
        if (ds != null) {
            JsonElement tr = ds.isJsonObject() && ds.getAsJsonObject().has("triggers") ? ds.getAsJsonObject().get("triggers") : ds;
            for (JsonObject t : asList(tr)) {
                String cause = t.has("cause") ? t.get("cause").getAsString() : "all";
                if (!cause.equals("all") && !cause.equals(src.getMsgId())) continue;
                JsonObject on = t.has("on_damage") && t.get("on_damage").isJsonObject() ? t.getAsJsonObject("on_damage") : null;
                JsonElement filters = t.has("filters") ? t.get("filters") : on != null ? on.get("filters") : null;
                if (filters != null && !BedrockEntities.Filters.test(filters, this, src.getEntity(), d)) continue;
                if (on != null && on.has("event")) BedrockEntities.fire(this, on.get("event").getAsString(), src.getEntity());
                if (t.has("deals_damage") && t.get("deals_damage").isJsonPrimitive() && !t.get("deals_damage").getAsBoolean()) return false;
                break;
            }
        }
        if (!hasHealth) return false;
        hp -= amount;
        if (hp <= 0) die();
        return true;
    }

    private void die() {
        JsonObject d = def();
        JsonElement od = d == null ? null : BedrockEntities.component(this, d, "minecraft:on_death");
        if (od != null && od.isJsonObject() && od.getAsJsonObject().has("event")) BedrockEntities.fire(this, od.getAsJsonObject().get("event").getAsString(), null);
        // the on_death event may have started a transformation or a despawn; the entity is gone unless an event kept it
        if (!isRemoved()) discard();
    }

    @Override
    public void kill() {
        if (!level().isClientSide && !isRemoved()) {
            die();
            return;
        }
        super.kill();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** the type registered for this entity */
    public static EntityType<BfnEntity> type() {
        return ModEntities.BFN_ENTITY.get();
    }
}
