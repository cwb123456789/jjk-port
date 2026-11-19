package com.jujutsuenhancement.item;

import com.jujutsuenhancement.combat.GojoSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A technique item (术式) of a character: the addon gives them to the player when the technique is chosen and every
 * right click (the tag {@code itemUse:ds:<id>}) runs the move that fits the player's state, see {@link GojoSkills}.
 */
public class TechniqueItem extends BedrockItem {
    private final String id;

    public TechniqueItem(String id, Properties properties) {
        super(properties);
        this.id = id;
    }

    /** While a technique item is held the energy (咒力) is shown above the hotbar. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (selected && !level.isClientSide && entity instanceof ServerPlayer sp && sp.tickCount % 5 == 0) {
            sp.displayClientMessage(com.jujutsuenhancement.text.BedrockText.parse("§l§b咒力: §l§f" + com.jujutsuenhancement.stats.Energy.get(sp)
                    + " §l§7/ §l§f" + com.jujutsuenhancement.stats.Energy.cap(sp)), true);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
            if (id.equals("limitless2") || id.equals("limitless3")) GojoSkills.onUse(sp, id);
        }
        return InteractionResultHolder.consume(stack);
    }
}
