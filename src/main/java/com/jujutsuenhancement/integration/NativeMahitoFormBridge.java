package com.jujutsuenhancement.integration;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import org.slf4j.Logger;

/**
 * Minimal compatibility bridge for the JujutsuCraft native Mahito entity.
 *
 * When its native Self-Embodiment of Perfection skill (skill=1520) begins,
 * keep the normal texture for four server ticks, then select JujutsuCraft's
 * own phase-two texture. The native entity/model remains owned by JujutsuCraft;
 * this does not replace its skill dispatcher or GeckoLib animation set.
 */
// Disabled: the original JujutsuCraft mod is left untouched.
public final class NativeMahitoFormBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MAHITO_CLASS = "net.mcreator.jujutsucraft.entity.MahitoEntity";
    private static final String SKILL_KEY = "skill";
    private static final int DOMAIN_SKILL_ID = 1520;
    private static final int FORM_DELAY_TICKS = 4;
    private static final String TICKS_KEY = "jujutsuenhancement_native_mahito_domain_ticks";
    private static final String ORIGINAL_TEXTURE_KEY = "jujutsuenhancement_native_mahito_original_texture";
    private static final String APPLIED_KEY = "jujutsuenhancement_native_mahito_phase2_applied";
    private static final String DOMAIN_ANIMATION_TRIGGERED_KEY = "jujutsuenhancement_native_mahito_domain_animation_triggered";
    private static final String PHASE_TWO_TEXTURE = "mahito_phase2__body";
    private static final String DOMAIN_START_ANIMATION = "domain_expansion1";

    private NativeMahitoFormBridge() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !MAHITO_CLASS.equals(entity.getClass().getName())) {
            return;
        }

        var data = entity.getPersistentData();
        // JujutsuCraft stores skill as a double NBT value (its procedures call getDouble).
        double skill = data.getDouble(SKILL_KEY);

        if (skill != DOMAIN_SKILL_ID) {
            // Keep the transformed texture, but arm the domain-start clip again
            // once the native skill dispatcher leaves skill 1520.
            data.remove(TICKS_KEY);
            data.remove(DOMAIN_ANIMATION_TRIGGERED_KEY);
            return;
        }

        int ticks = data.getInt(TICKS_KEY) + 1;
        data.putInt(TICKS_KEY, ticks);
        if (ticks < FORM_DELAY_TICKS) {
            return;
        }

        try {
            Method getTexture = entity.getClass().getMethod("getTexture");
            Method setTexture = entity.getClass().getMethod("setTexture", String.class);
            if (!data.getBoolean(APPLIED_KEY) || ticks % 20 == 0) {
                String currentTexture = (String) getTexture.invoke(entity);
                if (!data.contains(ORIGINAL_TEXTURE_KEY)) {
                    data.putString(ORIGINAL_TEXTURE_KEY, currentTexture);
                }
                // The entity's persistent-data marker may survive a world reload while
                // the base mod's synchronized texture field is reconstructed from defaults.
                // Reconcile periodically instead of trusting APPLIED_KEY as proof that
                // the live texture is still phase two.
                if (!PHASE_TWO_TEXTURE.equals(currentTexture)) {
                    setTexture.invoke(entity, PHASE_TWO_TEXTURE);
                }
                data.putBoolean(APPLIED_KEY, true);
            }
            if (data.getBoolean(DOMAIN_ANIMATION_TRIGGERED_KEY)) {
                return;
            }
            Method setAnimation = entity.getClass().getMethod("setAnimation", String.class);
            // JujutsuCraft exposes a synchronized animation setter. Trigger its
            // native domain-start clip once per domain activation, not once per life.
            setAnimation.invoke(entity, DOMAIN_START_ANIMATION);
            data.putBoolean(DOMAIN_ANIMATION_TRIGGERED_KEY, true);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            // Fail closed: don't interrupt JujutsuCraft's own skill logic.
            data.remove(TICKS_KEY);
            data.remove(DOMAIN_ANIMATION_TRIGGERED_KEY);
            LOGGER.debug("Native Mahito form bridge unavailable: {}", exception.toString());
        }
    }

}
