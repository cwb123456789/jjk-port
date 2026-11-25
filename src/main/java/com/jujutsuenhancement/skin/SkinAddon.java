package com.jujutsuenhancement.skin;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The skin / outfit add-on of Jujutsu Enhancement. It has no mod identity of its own: the main mod calls {@link #init()} when these
 * classes are present, so the add-on works as a separate jar and also when its files are unzipped into the main jar.
 */
public final class SkinAddon {
    private SkinAddon() {}

    public static void init() {
        SkinNet.register();
        MinecraftForge.EVENT_BUS.register(SkinCommands.class);
        if (FMLEnvironment.dist.isClient()) {
            SkinClient.init();
        }
        LogUtils.getLogger().info("[JujutsuEnhancement skin add-on] ready");
    }
}
