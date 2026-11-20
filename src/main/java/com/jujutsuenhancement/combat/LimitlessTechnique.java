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
 * The technique item "无下限术式 · 天上天下，唯我独尊" ({@code ds:limitless_technique}), ported from
 * {@code functions/limitless/limitless_technique.mcfunction}: using it gives the technique (the tag {@code limitless}, the item
 * limitless1 and, with creative mode, limitless1..6); sneaking + using it spends 10 talent points ({@code invest}) on the next
 * skill: 2 苍, 3 赫 (needs 反转术式), 4 空间撕扯, 5 茈 (needs 六眼), 6 领域展开.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimitlessTechnique {
    private static final String HAS = "jje_limitless", FORMS = "jje_forms", INVEST = "jje_invest";
    private static final String[] NAMES = {"", "无限", "§l§b术式顺转 苍", "§l§c术式反转 赫", "§l§3空间撕扯", "§l§5虚式「茈」", "§l§9领域展开『无量空处』"};

    private LimitlessTechnique() {}

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean has(Player p) { return data(p).getBoolean(HAS); }
    public static int forms(Player p) { return data(p).getInt(FORMS); }
    public static int invest(Player p) { return data(p).getInt(INVEST); }
    public static void addInvest(Player p, int n) { data(p).putInt(INVEST, Math.max(0, invest(p) + n)); }

    private static void give(ServerPlayer p, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(JujutsuEnhancement.MODID, id));
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        if (!p.getInventory().contains(stack)) p.getInventory().add(stack);
    }

    private static void giveFor(ServerPlayer p, int forms) {
        for (int i = 1; i <= Math.min(6, forms); i++) give(p, "limitless" + i);
    }

    /** Right click: choose the technique. */
    public static void select(ServerPlayer p) {
        if (has(p)) {
            p.displayClientMessage(BedrockText.parse("§l§c你已经在使用 无下限术式 了! "), false);
            return;
        }
        CompoundTag d = data(p);
        d.putBoolean(HAS, true);
        d.putInt(FORMS, p.isCreative() ? 6 : 1);
        giveFor(p, forms(p));
        p.displayClientMessage(BedrockText.parse("§l§1╔════════════════════╗\n§l§1║  §l§b无下限术式  §l§1║\n§l§1╚════════════════════╝"), false);
        if (p.isCreative()) p.displayClientMessage(BedrockText.parse("§l§a创造玩家给予所有技能"), false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0F, 0.25F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.85F);
    }

    /** Sneak + right click: learn the next skill for 10 talent points. */
    public static void learn(ServerPlayer p) {
        if (!has(p)) {
            p.displayClientMessage(BedrockText.parse("§l§c请先选择 无下限术式 (不潜行右键)"), false);
            return;
        }
        giveFor(p, forms(p));
        int forms = forms(p);
        if (invest(p) < 10) {
            p.displayClientMessage(BedrockText.parse("§l§c天赋点不足: §l§e" + invest(p) + " §l§7/ 10"), false);
            return;
        }
        if (forms >= 6) {
            p.displayClientMessage(BedrockText.parse("§l§c你已经掌握了无下限术式的所有秘传，技巧与用法，你没有什么可以进步的了"), false);
            return;
        }
        boolean cant = false;
        if (forms == 4 && !p.getTags().contains("sixeyes")) {
            p.displayClientMessage(BedrockText.parse("§l§c此次升级需要拥有 §l§b六眼"), false);
            cant = true;
        } else if (forms == 2 && !p.getTags().contains("reversecursed")) {
            p.displayClientMessage(BedrockText.parse("§l§c此次升级需要掌握 §l§f反转术式"), false);
            cant = true;
        }
        if (cant) {
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 1.0F, 0.3F);
            return;
        }
        data(p).putInt(FORMS, forms + 1);
        addInvest(p, -10);
        p.displayClientMessage(BedrockText.parse("§l§1无下限术式 " + (forms + 1) + "号技能: " + NAMES[forms + 1] + "§l§4\n§l§e[解锁]"), false);
        giveFor(p, forms + 1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 0.5F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0F, 2.0F);
    }

    /** Gives the technique's skills back (the addon keeps them on death). */
    public static void refill(ServerPlayer p) {
        if (has(p)) giveFor(p, forms(p));
    }

    public static void clear(ServerPlayer p) {
        CompoundTag d = data(p);
        d.remove(HAS);
        d.remove(FORMS);
        for (int i = 1; i <= 6; i++) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(JujutsuEnhancement.MODID, "limitless" + i));
            if (item != null) p.getInventory().clearOrCountMatchingItems(s -> s.is(item), -1, p.inventoryMenu.getCraftSlots());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag from = data(event.getOriginal());
        CompoundTag to = data(event.getEntity());
        for (String k : new String[]{HAS, FORMS, INVEST}) {
            if (from.contains(k)) to.put(k, from.get(k).copy());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) refill(p);
    }
}
