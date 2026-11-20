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
 * The technique item "御厨子" ({@code ds:yuji_technique}), ported from {@code functions/yuji/yuji_technique.mcfunction}: using it
 * chooses the technique (the tag {@code yuji_awakened}; creative players get every skill); sneaking + using it spends 10 Black Flashes
 * on special-grade beings ({@code blackflash_amount_special}) on the next skill: 1 黑闪战斗风格 (black_fists), 2 解-灵魂 (soul_shrine1),
 * 3 领域展开 + 赤血操术 (soul_shrine2, blood_manipulation). Sneak + left click with the item gives the skills back (yuji_clear).
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YujiTechnique {
    private static final String HAS = "jje_yuji", FORMS = "jje_yforms", SPECIAL = "jje_bfspecial", COUNT = "jje_bfcount";
    private static final String[] IDS = {"black_fists", "soul_shrine1", "soul_shrine2", "blood_manipulation"};

    private YujiTechnique() {}

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean has(Player p) { return data(p).getBoolean(HAS); }
    public static int forms(Player p) { return data(p).getInt(FORMS); }
    public static int special(Player p) { return data(p).getInt(SPECIAL); }
    public static void addSpecial(Player p, int n) { data(p).putInt(SPECIAL, Math.max(0, special(p) + n)); }

    private static void give(ServerPlayer p, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(JujutsuEnhancement.MODID, id));
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        if (!p.getInventory().contains(stack)) p.getInventory().add(stack);
    }

    private static void giveFor(ServerPlayer p, int forms) {
        if (forms >= 1) give(p, IDS[0]);
        if (forms >= 2) give(p, IDS[1]);
        if (forms >= 3) {
            give(p, IDS[2]);
            give(p, IDS[3]);
        }
    }

    public static void select(ServerPlayer p) {
        if (has(p)) {
            p.displayClientMessage(BedrockText.parse("§l§c你已经在使用术式 御厨子 了! "), false);
            return;
        }
        CompoundTag d = data(p);
        d.putBoolean(HAS, true);
        d.putInt(FORMS, p.isCreative() ? 3 : 0);
        p.displayClientMessage(BedrockText.parse("\n§4╔════════════════════╗\n§4║  §l§c正在使用术式: 御厨子  §4║\n§4╚════════════════════╝"), false);
        if (p.isCreative()) {
            p.displayClientMessage(BedrockText.parse("§l§a创造玩家给予所有技能"), false);
            giveFor(p, 3);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0F, 0.25F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.85F);
    }

    public static void learn(ServerPlayer p) {
        if (!has(p)) {
            p.displayClientMessage(BedrockText.parse("§l§c请先选择术式 御厨子 (不潜行右键)"), false);
            return;
        }
        giveFor(p, forms(p));
        int forms = forms(p);
        if (forms >= 3) {
            p.displayClientMessage(BedrockText.parse("§l§c你已经掌握了御厨子的全部技巧"), false);
            return;
        }
        if (special(p) < 10) {
            p.displayClientMessage(BedrockText.parse("§l§c你感觉自己对于 §4御厨子§c 的理解还远不够深 §l§7(" + special(p) + " / 10 次黑闪特级生物)"), false);
            return;
        }
        data(p).putInt(FORMS, forms + 1);
        addSpecial(p, -10);
        giveFor(p, forms + 1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0F, 2.0F);
        p.displayClientMessage(BedrockText.parse("§l§e御厨子 技能解锁!"), false);
    }

    public static void clear(ServerPlayer p) {
        for (String id : IDS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(JujutsuEnhancement.MODID, id));
            if (item != null) p.getInventory().clearOrCountMatchingItems(s -> s.is(item), -1, p.inventoryMenu.getCraftSlots());
        }
        CompoundTag d = data(p);
        d.remove(HAS);
        p.displayClientMessage(BedrockText.parse("§l§a术式技能物品已取消"), false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag from = data(event.getOriginal());
        CompoundTag to = data(event.getEntity());
        for (String k : new String[]{HAS, FORMS, SPECIAL, COUNT}) {
            if (from.contains(k)) to.put(k, from.get(k).copy());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && has(p)) giveFor(p, forms(p));
    }
}
