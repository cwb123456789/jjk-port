package com.jujutsuenhancement.item;

import com.jujutsuenhancement.combat.SukunaTechnique;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** ds:sukuna_technique, "诅咒之王 · 两面宿傩". */
public class SukunaTechniqueItem extends BedrockItem {
    public SukunaTechniqueItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) SukunaTechnique.use(sp);
        return InteractionResultHolder.consume(stack);
    }
}
