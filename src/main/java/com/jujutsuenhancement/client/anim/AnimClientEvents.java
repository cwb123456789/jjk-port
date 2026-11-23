package com.jujutsuenhancement.client.anim;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.client.render.HeldKatanaLayer;
import com.jujutsuenhancement.item.KatanaItem;
import com.jujutsuenhancement.net.ModNet;
import com.jujutsuenhancement.net.SwingPacket;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Installs the animated player model and the held-weapon layer, forwards the attack key and runs the animation events. */
public final class AnimClientEvents {
    private AnimClientEvents() {}

    @Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod_ {
        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : new String[] {"default", "slim"}) {
                var renderer = event.getSkin(skin);
                if (renderer == null) continue;
                boolean slim = skin.equals("slim");
                try {
                    AnimPlayerModel model = new AnimPlayerModel(
                            event.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                    Field field = null;
                    for (Class<?> c = renderer.getClass(); c != null && field == null; c = c.getSuperclass()) {
                        for (Field f : c.getDeclaredFields()) {
                            if (!Modifier.isStatic(f.getModifiers()) && EntityModel.class.isAssignableFrom(f.getType())) {
                                field = f;
                                break;
                            }
                        }
                    }
                    if (field == null) throw new IllegalStateException("no model field in " + renderer.getClass());
                    field.setAccessible(true);
                    field.set(renderer, model);
                    ((LivingEntityRenderer) renderer).addLayer(new HeldKatanaLayer((net.minecraft.client.renderer.entity.RenderLayerParent) renderer));
                } catch (Exception e) {
                    LogUtils.getLogger().warn("Could not install the animated player model ({}): {}", skin, e.toString());
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Forge_ {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) PlayerAnims.tick();
        }

        @SubscribeEvent
        public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isAttack()) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            var held = player.getMainHandItem().getItem();
            String hid = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held).getPath();
            boolean yuji = (hid.equals("black_fists") && (player.isShiftKeyDown() || !player.onGround())) || (hid.equals("soul_shrine1") && player.isShiftKeyDown()) || (hid.equals("sukuna2") && player.isShiftKeyDown()) || hid.equals("sukuna1") || hid.equals("sukuna5") || hid.equals("sukuna4") || hid.equals("sukuna_fists") || hid.equals("cleave_fists") || hid.equals("four_fists");
            if (!(held instanceof KatanaItem) && !yuji) return;
            ModNet.CHANNEL.sendToServer(new SwingPacket());
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}
