package com.jujutsuenhancement;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.jujutsuenhancement.registry.ModCreativeTabs;
import com.jujutsuenhancement.registry.ModEntities;
import com.jujutsuenhancement.registry.ModItems;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(JujutsuEnhancement.MODID)
public final class JujutsuEnhancement {
    public static final String MODID = "jujutsuenhancement";
    private static final Logger LOGGER = LogUtils.getLogger();

    public JujutsuEnhancement() {
        ModEntities.ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModCreativeTabs.TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(com.jujutsuenhancement.net.ModNet::register));
        // optional skin add-on: present when its classes are in the game (as its own jar or merged into this one)
        try {
            Class.forName("com.jujutsuenhancement.skin.SkinAddon").getMethod("init").invoke(null);
            LOGGER.info("[JujutsuEnhancement] skin add-on loaded");
        } catch (ClassNotFoundException ignored) {
            // the main mod runs alone
        } catch (Throwable t) {
            LOGGER.warn("[JujutsuEnhancement] skin add-on failed to start: {}", t.toString());
        }
        LOGGER.info("[JujutsuEnhancement] Loaded version 0.1.0");
        LOGGER.info("[JujutsuEnhancement] JujutsuCraft detected: {}",
                ModList.get().isLoaded("jujutsucraft"));
    }
}
