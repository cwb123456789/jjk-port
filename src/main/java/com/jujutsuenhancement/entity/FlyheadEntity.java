package com.jujutsuenhancement.entity;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.text.BedrockText;
import java.util.EnumSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * ds:flyhead, "蝇头" (grade 4 curse) of entities/entities/curses/flyhead.json: 4 health, a 3 damage hit, a 0.4 x 0.8
 * box, no gravity, immune to fire and falls, and vex-like charging (behavior.charge_attack). Its goals keep the
 * addon's priorities: hurt_by_target 1, float 3, charge_attack 8, look_at_player 9, nearest_attackable_target 11
 * (radius 64, does not need to see the target; anything but curses and the addon's helper entities).
 * It drops nothing, gives 1 experience to a player's kill and dies with the particle ds:curse_death_small and
 * mob.spider.death at pitch 1.2.
 */
public class FlyheadEntity extends Monster implements GeoEntity {
    /** Entities of the "curse" family: they do not fight each other (the addon's has_tag curse / purecurse tests). */
    public static final TagKey<EntityType<?>> CURSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(JujutsuEnhancement.MODID, "curse"));

    // every animation slot of the client entity (move, bob, admire, celebrate...) plays animation.flyhead
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.flyhead");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FlyheadEntity(EntityType<? extends FlyheadEntity> type, Level level) {
        super(type, level);
        this.moveControl = new ChargeMoveControl(this);
        this.xpReward = 1;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 4.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(3, new FloatGoal(this));
        this.goalSelector.addGoal(8, new ChargeGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.02F));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Mob.class, 6.0F, 0.02F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(11, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, false, false,
                target -> !target.getType().is(CURSES)));
    }

    /** minecraft:damage_sensor: no fall damage. */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** minecraft:loot nothing.json */
    @Override
    protected ResourceLocation getDefaultLootTable() {
        return BuiltInLootTables.EMPTY;
    }

    /** The name with Bedrock's colour rules (the lang entry is the addon's "§r§l§f[§a 4级咒灵 ...] 蝇头§r"). */
    @Override
    protected Component getTypeName() {
        String key = getType().getDescriptionId();
        Language language = Language.getInstance();
        return language.has(key) ? BedrockText.parse(language.getOrDefault(key)) : super.getTypeName();
    }

    /** events.curse_die: particle ds:curse_death_small and mob.spider.death (pitch 1.2, heard within 35 blocks). */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_DEATH, SoundSource.HOSTILE, 2.2F, 1.2F);
            com.jujutsuenhancement.entity.BedrockFxEntity.play(server, "curse_death_small", getX(), getY(), getZ(), getYRot());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 0, state -> state.setAndContinue(FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Vex style flying: accelerates towards the wanted point, turns to its target. */
    private static final class ChargeMoveControl extends MoveControl {
        private final FlyheadEntity flyhead;

        ChargeMoveControl(FlyheadEntity flyhead) {
            super(flyhead);
            this.flyhead = flyhead;
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO) return;
            Vec3 delta = new Vec3(wantedX - flyhead.getX(), wantedY - flyhead.getY(), wantedZ - flyhead.getZ());
            double length = delta.length();
            if (length < flyhead.getBoundingBox().getSize()) {
                operation = Operation.WAIT;
                flyhead.setDeltaMovement(flyhead.getDeltaMovement().scale(0.5D));
                return;
            }
            // movement 1.0 of the addon: about 0.9 blocks per tick at full speed (acceleration / drag 0.91)
            double acceleration = speedModifier * flyhead.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.083D;
            flyhead.setDeltaMovement(flyhead.getDeltaMovement().add(delta.scale(acceleration / length)));
            LivingEntity target = flyhead.getTarget();
            if (target == null) {
                Vec3 motion = flyhead.getDeltaMovement();
                flyhead.setYRot(-((float) Mth.atan2(motion.x, motion.z)) * (180.0F / (float) Math.PI));
            } else {
                flyhead.setYRot(-((float) Mth.atan2(target.getX() - flyhead.getX(), target.getZ() - flyhead.getZ()))
                        * (180.0F / (float) Math.PI));
            }
            flyhead.yBodyRot = flyhead.getYRot();
        }
    }

    /** minecraft:behavior.charge_attack: dash at the target, hurt it on contact. */
    private final class ChargeGoal extends Goal {
        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && !getMoveControl().hasWanted()
                    && random.nextInt(reducedTickDelay(7)) == 0 && distanceToSqr(target) > 4.0D;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTarget();
            return getMoveControl().hasWanted() && target != null && target.isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            Vec3 at = target.getEyePosition();
            getMoveControl().setWantedPosition(at.x, at.y, at.z, 1.0D);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            if (getBoundingBox().intersects(target.getBoundingBox())) {
                doHurtTarget(target);
            } else if (distanceToSqr(target) < 9.0D) {
                Vec3 at = target.getEyePosition();
                getMoveControl().setWantedPosition(at.x, at.y, at.z, 1.0D);
            }
        }
    }
}
