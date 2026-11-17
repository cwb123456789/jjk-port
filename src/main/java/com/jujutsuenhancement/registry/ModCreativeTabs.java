package com.jujutsuenhancement.registry;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The "咒术觉醒" creative tab: every ported item, spawn egg, relic, technique and piece of equipment. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JujutsuEnhancement.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.jujutsuenhancement"))
            .icon(() -> new ItemStack(ModItems.UNOBTANIUM_INGOT.get()))
            .displayItems((parameters, output) -> ModItems.TAB.forEach(item -> output.accept(item.get())))
            .build());

    private ModCreativeTabs() {}
}
