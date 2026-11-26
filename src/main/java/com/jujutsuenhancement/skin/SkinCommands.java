package com.jujutsuenhancement.skin;

import com.jujutsuenhancement.combat.CgSessions;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 咒术替换: during any CG / animation (hurt animations included) it frees the player and opens the skin screen; when the screen is closed
 * the CG restarts, unchanged except for the skin. Outside a CG it just opens the screen.
 * 服饰替换: the same for outfits.
 */
public final class SkinCommands {
    private SkinCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        for (String name : new String[] {"咒术替换", "服饰替换"}) {
            boolean outfit = name.equals("服饰替换");
            event.getDispatcher().register(Commands.literal(name).executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                int[] others = CgSessions.participants(p);   // read before the pause: the session moves to "paused" but keeps them
                boolean was = CgSessions.pause(p);
                if (was) p.sendSystemMessage(Component.literal("CG 已暂停，可以自由移动并更换" + (outfit ? "服饰" : "皮肤") + "，关闭界面后 CG 会重新开始"));
                SkinNet.open(p, was, others);
                return 1;
            }));
        }
    }
}
