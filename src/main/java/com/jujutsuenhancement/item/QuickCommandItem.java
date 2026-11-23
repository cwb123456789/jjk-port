package com.jujutsuenhancement.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** "咒术觉醒快捷指令" (jjkshortcut:quick_command): using it opens the quick-command menu. */
public class QuickCommandItem extends BedrockItem {
    public QuickCommandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    net.minecraft.client.Minecraft.getInstance().setScreen(new com.jujutsuenhancement.client.QuickCommandScreen(-1)));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
