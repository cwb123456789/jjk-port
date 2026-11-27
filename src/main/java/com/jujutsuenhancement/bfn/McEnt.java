package com.jujutsuenhancement.bfn;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/** A Forge entity seen by the command engine. */
public final class McEnt implements Ent {
    final Entity e;
    private final int dim;
    private String type;

    McEnt(Entity e, int dim) {
        this.e = e;
        this.dim = dim;
    }

    /** Bedrock id of the Forge entity type: minecraft:zombie -> zombie, jujutsuenhancement:x -> ds:x */
    static String bedrockId(Entity e) {
        if (e instanceof BfnEntity b) return b.bid();
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (k == null) return "unknown";
        if (k.getNamespace().equals("minecraft")) {
            return switch (k.getPath()) {
                case "experience_orb" -> "xp_orb";
                case "lightning_bolt" -> "lightning_bolt";
                default -> k.getPath();
            };
        }
        if (k.getNamespace().equals("jujutsuenhancement")) return "ds:" + k.getPath();
        return k.toString();
    }

    @Override
    public String key() {
        return e instanceof Player p ? p.getGameProfile().getName() : e.getStringUUID();
    }

    @Override
    public String typeId() {
        if (type == null) type = bedrockId(e);
        return type;
    }

    @Override
    public String nameTag() {
        if (e instanceof Player p) return p.getGameProfile().getName();
        return e.hasCustomName() ? e.getCustomName().getString() : "";
    }

    @Override
    public boolean isPlayer() {
        return e instanceof Player;
    }

    @Override
    public boolean isAlive() {
        return e.isAlive();
    }

    @Override
    public Set<String> tags() {
        return new AbstractSet<>() {
            @Override
            public Iterator<String> iterator() {
                return List.copyOf(e.getTags()).iterator();
            }

            @Override
            public int size() {
                return e.getTags().size();
            }

            @Override
            public boolean contains(Object o) {
                return e.getTags().contains(o);
            }

            @Override
            public boolean add(String s) {
                return e.addTag(s);
            }

            @Override
            public boolean remove(Object o) {
                return o instanceof String s && e.removeTag(s);
            }
        };
    }

    @Override
    public double x() {
        return e.getX();
    }

    @Override
    public double y() {
        return e.getY();
    }

    @Override
    public double z() {
        return e.getZ();
    }

    @Override
    public float yaw() {
        return e.getYRot();
    }

    @Override
    public float pitch() {
        return e.getXRot();
    }

    @Override
    public double eyeHeight() {
        return e.getEyeHeight();
    }

    @Override
    public int dim() {
        return dim;
    }

    @Override
    public List<String> families() {
        return McHost.families(this);
    }

    @Override
    public double[] box() {
        AABB b = e.getBoundingBox();
        return new double[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
    }

    @Override
    public int gamemode() {
        if (e instanceof ServerPlayer sp) {
            GameType g = sp.gameMode.getGameModeForPlayer();
            return g.getId();
        }
        return 6;
    }

    @Override
    public int level() {
        return e instanceof Player p ? p.experienceLevel : 0;
    }

    @Override
    public double property(String name) {
        String n = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        var tag = e.getPersistentData();
        if (tag.contains("bfn_" + n)) return tag.getDouble("bfn_" + n);
        double dflt = BedrockEntities.propDefault(typeId(), n);
        if (!Double.isNaN(dflt)) return dflt;
        return e.getTags().contains(n) ? 1 : 0;
    }

    private static String itemId(String bedrock) {
        if (bedrock.startsWith("ds:")) return "jujutsuenhancement:" + bedrock.substring(3);
        return bedrock.contains(":") ? bedrock : "minecraft:" + bedrock;
    }

    private static boolean itemMatches(ItemStack st, Selector.HasItem q) {
        if (st.isEmpty()) return false;
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (k == null || !k.toString().equals(itemId(q.item()))) return false;
        return q.quantity() == null || q.quantity().test(st.getCount());
    }

    @Override
    public boolean hasItem(Selector.HasItem q) {
        if (!(e instanceof LivingEntity le)) return false;
        String loc = q.location() == null ? "" : q.location();
        if (loc.contains("mainhand")) return itemMatches(le.getMainHandItem(), q);
        if (loc.contains("offhand")) return itemMatches(le.getOffhandItem(), q);
        if (loc.contains("armor.head")) return itemMatches(le.getItemBySlot(EquipmentSlot.HEAD), q);
        if (loc.contains("armor.chest")) return itemMatches(le.getItemBySlot(EquipmentSlot.CHEST), q);
        if (loc.contains("armor.legs")) return itemMatches(le.getItemBySlot(EquipmentSlot.LEGS), q);
        if (loc.contains("armor.feet")) return itemMatches(le.getItemBySlot(EquipmentSlot.FEET), q);
        if (e instanceof Player p) {
            var inv = p.getInventory();
            if (q.slot() != null && loc.contains("hotbar")) return q.slot() >= 0 && q.slot() < 9 && itemMatches(inv.getItem(q.slot()), q);
            if (q.slot() != null && loc.contains("inventory")) return q.slot() >= 0 && q.slot() + 9 < 36 && itemMatches(inv.getItem(q.slot() + 9), q);
            for (int i = 0; i < inv.getContainerSize(); i++) if (itemMatches(inv.getItem(i), q)) return true;
            return false;
        }
        return itemMatches(le.getMainHandItem(), q) || itemMatches(le.getOffhandItem(), q);
    }

    @Override
    public Object handle() {
        return e;
    }

    static boolean isMonster(Entity e) {
        return e instanceof Monster;
    }
}
