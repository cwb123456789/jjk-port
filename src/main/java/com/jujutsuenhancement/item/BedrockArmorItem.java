package com.jujutsuenhancement.item;

import com.jujutsuenhancement.client.render.BedrockArmorRenderer;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A piece of equipment of the addon (items/armour/*.json + attachables/fits/*.json). The addon renders it with the
 * attachable's own geometry, texture and an idle animation that always plays while the wearer is alive; the same is
 * done here with GeckoLib (geo/&lt;id&gt;.geo.json, textures/armor/&lt;id&gt;.png, animations/&lt;id&gt;.animation.json).
 * Name and description come from the lang entries exported from the addon's display_name.
 */
public class BedrockArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String id;
    private final String idleAnimation;
    private final int damageChanceMin;
    private final int damageChanceMax;

    /**
     * @param id            the addon's id without the "ds:" prefix, also the name of the geo / texture / animation files
     * @param protection    minecraft:wearable.protection
     * @param durability    minecraft:durability.max_durability
     * @param enchantability minecraft:enchantable.value
     * @param damageChance  minecraft:durability.damage_chance min / max (percent)
     * @param idleAnimation the animation of the attachable's "idle" entry, or null when the attachable has none
     */
    public BedrockArmorItem(String id, Type type, int protection, int durability, int enchantability,
                            int damageChanceMin, int damageChanceMax, @Nullable String idleAnimation) {
        super(new BedrockArmorMaterial(id, type, protection, durability, enchantability), type, new Properties().stacksTo(1));
        this.id = id;
        this.idleAnimation = idleAnimation;
        this.damageChanceMin = damageChanceMin;
        this.damageChanceMax = damageChanceMax;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public String id() {
        return id;
    }

    @Override
    public Component getName(ItemStack stack) {
        return BedrockItem.name(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BedrockItem.addLore(this, tooltip);
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** minecraft:durability.damage_chance: a hit wears the piece out only with a chance of min..max percent. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        int chance = damageChanceMin + entity.getRandom().nextInt(damageChanceMax - damageChanceMin + 1);
        return entity.getRandom().nextInt(100) < chance ? amount : 0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (idleAnimation == null) return;
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            state.setAnimation(RawAnimation.begin().thenLoop(idleAnimation));
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BedrockArmorRenderer renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                           HumanoidModel<?> original) {
                if (renderer == null) renderer = new BedrockArmorRenderer(id);
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        });
    }
}
