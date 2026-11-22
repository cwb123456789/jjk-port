package com.jujutsuenhancement.combat;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.text.BedrockText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The technique item "诅咒之王 · 两面宿傩" ({@code ds:sukuna_technique}), after {@code functions/sukuna/sukuna_use.mcfunction} and
 * {@code grant_skills.mcfunction}: it needs eaten Sukuna fingers ({@code finger}; creative players count as having 15), right click
 * hands the body over to Sukuna (the tag {@code sukuna}, needed by every skill) and back, sneak + right click gives / takes back the
 * skill items (sukuna1 from 1 finger, sukuna2 from 2, sukuna3 from 3, sukuna4 from 15).
 * The takeover cutscene (sukunacutscene 51 / 91) belongs to the cutscene stage.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SukunaTechnique {
    private static final String FINGERS = "jje_fingers", MODE = "jje_smode";
    private static final String[] IDS = {"sukuna_fists", "cleave_fists", "sukuna1", "sukuna2", "sukuna3", "sukuna4", "sukuna5"};

    private SukunaTechnique() {}

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int fingers(Player p) { return p.isCreative() ? Math.max(15, data(p).getInt(FINGERS)) : data(p).getInt(FINGERS); }
    public static void setFingers(Player p, int n) { data(p).putInt(FINGERS, Math.max(0, Math.min(20, n))); }
    /** The {@code fuga} score: every hit of the cleaves fills it, at 500 「竈」 开 is ready (fuga_ready.mcfunction). */
    public static int fuga(Player p) { return data(p).getInt("jje_fuga"); }
    public static void addFuga(Player p, int n) {
        int before = fuga(p);
        int now = Math.max(0, before + n);
        if (now > 600) now = 500; // sukuna_cmds: fuga 601.. -> 500
        data(p).putInt("jje_fuga", now);
        if (before < 500 && now >= 500 && p instanceof ServerPlayer sp) {
            sp.displayClientMessage(BedrockText.parse("§r§6§l- 「竈」 开 已就绪"), false);
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 3.0F, 0.35F);
        }
    }
    /** The tag {@code fugaready}: the meter reached 500 (creative players always have it). */
    public static boolean fugaReady(Player p) { return p.isCreative() || fuga(p) >= 500; }
    /** divine_flames: the meter is emptied the moment 「竈」 starts. */
    public static void resetFuga(Player p) { data(p).putInt("jje_fuga", 0); }
    public static int chants(Player p) { return data(p).getInt("jje_wcschant"); }
    public static void addChant(Player p) { data(p).putInt("jje_wcschant", Math.min(3, chants(p) + 1)); }
    public static void resetChants(Player p) { data(p).putInt("jje_wcschant", 0); }
    /** Original body (千年原身): the tag {@code body_transformation}; creative players have it. */
    public static boolean heian(Player p) { return p.isCreative() || data(p).getBoolean("jje_heian"); }
    public static void setHeian(Player p, boolean b) { data(p).putBoolean("jje_heian", b); }
    public static boolean active(Player p) { return data(p).getBoolean(MODE); }

    public static void use(ServerPlayer p) {
        if (fingers(p) < 1) {
            p.displayClientMessage(BedrockText.parse("§l§4你需要先吃下两面宿傩的手指才可使用此物品"), false);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.2F, 0.5F);
            return;
        }
        if (p.isShiftKeyDown()) {
            if (!active(p)) {
                p.displayClientMessage(BedrockText.parse("§l§c只有宿傩掌控身体时才能获得技能物品"), false);
                return;
            }
            boolean has = false;
            for (String id : IDS) has |= p.getInventory().contains(new ItemStack(item(id)));
            if (has) clear(p); else grant(p);
            return;
        }
        boolean on = !active(p);
        data(p).putBoolean(MODE, on);
        p.displayClientMessage(BedrockText.parse(on ? "§l§4宿傩 §l§c接管了身体" : "§l§7宿傩 §l§f退回了意识深处"), false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, on ? 0.6F : 1.4F);
        if (!on) clear(p);
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(JujutsuEnhancement.MODID, id));
    }

    public static void grant(ServerPlayer p) {
        int f = fingers(p);
        if (f >= 1) {
            give(p, "sukuna_fists");
            give(p, "sukuna1");
        }
        if (f >= 2) give(p, "sukuna2");
        if (f >= 3) give(p, "sukuna3");
        if (f >= 15) {
            give(p, "sukuna4");
            give(p, "sukuna5");
        }
        p.displayClientMessage(BedrockText.parse("§l§a技能物品已获得"), false);
    }

    private static void give(ServerPlayer p, String id) {
        Item it = item(id);
        if (it == null) return;
        ItemStack st = new ItemStack(it);
        if (!p.getInventory().contains(st)) p.getInventory().add(st);
    }

    public static void clear(ServerPlayer p) {
        for (String id : IDS) {
            Item it = item(id);
            if (it != null) p.getInventory().clearOrCountMatchingItems(s -> s.is(it), -1, p.inventoryMenu.getCraftSlots());
        }
        p.displayClientMessage(BedrockText.parse("§l§a术式技能物品已取消"), false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag from = data(event.getOriginal()), to = data(event.getEntity());
        for (String k : new String[]{FINGERS, MODE, "jje_fuga", "jje_wcschant", "jje_heian"}) if (from.contains(k)) to.put(k, from.get(k).copy());
    }
}
