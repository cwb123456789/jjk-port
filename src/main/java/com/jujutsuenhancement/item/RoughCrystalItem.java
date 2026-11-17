package com.jujutsuenhancement.item;

import com.jujutsuenhancement.entity.BedrockFxEntity;
import com.jujutsuenhancement.stats.PlayerStats;
import com.jujutsuenhancement.text.BedrockText;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * ds:rough_crystal, "粗糙咒晶" (a 咒物). Behavior of functions/relics/rough_crystal.mcfunction: using it adds strength
 * until the body's limit is reached.
 *
 * <pre>
 * limit: 20 (heavenly restriction, mechimaru) / 24 (+120percent) ; 50 normal / 60 (+120percent)
 * strength >= limit               -> "你的身体已经在此方面达到极限了..." (the crystal stays)
 * otherwise  strength2 += 3 when strength == limit-1, else 2     (the file adds 1 and then 2 at the last point)
 *            if strength2 >= 5 and 0 <= strength <= 59: damageadd += 1, strength2 -= 5
 *            strength += 1 when strength == limit-1, else 2
 *            message, random.glass (pitch 0.35), beacon.power (pitch 1.5), particles ds:rough_crystal1/2, the crystal is used up
 * </pre>
 */
public class RoughCrystalItem extends BedrockItem {
    public RoughCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        boolean heavenly = player.getTags().contains("heavenlyrestriction_mechimaru");
        boolean percent120 = player.getTags().contains("120percent");
        int limit = heavenly ? (percent120 ? 24 : 20) : (percent120 ? 60 : 50);
        int strength = PlayerStats.get(player, "strength");
        if (strength >= limit) {
            player.sendSystemMessage(BedrockText.parse("§l§c你的身体已经在此方面达到极限了..."));
            return InteractionResultHolder.fail(stack);
        }

        int strength2 = PlayerStats.get(player, "strength2") + (strength == limit - 1 ? 3 : 2);
        int damageadd = PlayerStats.get(player, "damageadd");
        if (strength2 >= 5 && strength >= 0 && strength <= 59) {
            damageadd++;
            strength2 -= 5;
        }
        PlayerStats.set(player, "strength2", strength2);
        PlayerStats.set(player, "damageadd", damageadd);
        PlayerStats.set(player, "strength", strength + (strength == limit - 1 ? 1 : 2));
        PlayerStats.applyDamageAdd(player);

        player.sendSystemMessage(BedrockText.parse("\n§l§o§e你打碎了这个咒晶...\n\n§l§e+2 §4力量"));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.35F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F);
        BedrockFxEntity.play((ServerLevel) level, "rough_crystal", player.getX(), player.getY(), player.getZ(), player.getYRot());
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}
