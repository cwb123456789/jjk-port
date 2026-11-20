package com.jujutsuenhancement.item;

import com.jujutsuenhancement.combat.LimitlessTechnique;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** ds:limitless_technique, "无下限术式": right click chooses the technique, sneak + right click learns the next skill. */
public class LimitlessTechniqueItem extends BedrockItem {
    public LimitlessTechniqueItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) LimitlessTechnique.learn(sp);
            else LimitlessTechnique.select(sp);
        }
        return InteractionResultHolder.consume(stack);
    }
}
