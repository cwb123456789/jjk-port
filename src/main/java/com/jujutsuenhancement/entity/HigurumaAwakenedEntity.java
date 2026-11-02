package com.jujutsuenhancement.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Awakened Higuruma: gavel combat, the Deadly Sentencing trial domain, then the
 * Executioner's Sword (new skill set) for a limited time after a guilty verdict.
 */
public final class HigurumaAwakenedEntity extends PathfinderMob implements GeoEntity, TestCastable {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(HigurumaAwakenedEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SWORD =
            SynchedEntityData.defineId(HigurumaAwakenedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final Map<String, RawAnimation> CACHE = new HashMap<>();

    private static RawAnimation anim(String k) {
        return CACHE.computeIfAbsent(k, n -> switch (n) {
            case "idle", "walk" -> RawAnimation.begin().thenLoop("animation.higuruma." + n);
            case "domain" -> RawAnimation.begin().thenPlay("animation.higuruma.domain_start")
                    .thenPlay("animation.higuruma.domain");
            default -> RawAnimation.begin().thenPlay("animation.higuruma." + n);
        });
    }

    private record Move(String anim, int ticks, int hit, float damage, double reach, double knockback) {}

    private static final Move[] GAVEL_COMBO = {
            new Move("gavel_attack1", 13, 5, 1.0F, 3.0D, 0.3D), new Move("gavel_attack2", 13, 5, 1.0F, 3.0D, 0.3D),
            new Move("gavel_attack3", 14, 6, 1.0F, 3.0D, 0.3D), new Move("gavel_attack4", 14, 6, 1.1F, 3.0D, 0.4D),
            new Move("gavel_attack5", 25, 11, 1.7F, 3.4D, 1.0D)};
    private static final Move[] GAVEL_SKILL = {
            new Move("gavel_ab1", 19, 9, 1.8F, 3.4D, 0.8D), new Move("gavel_ab2", 25, 12, 2.0F, 3.6D, 1.0D),
            new Move("gavel_ab3", 40, 20, 2.5F, 4.0D, 1.4D)};
    private static final Move[] SWORD_COMBO = {
            new Move("sword_attack1", 19, 8, 1.5F, 3.8D, 0.5D), new Move("sword_attack2", 20, 8, 1.5F, 3.8D, 0.5D),
            new Move("sword_attack3", 19, 8, 1.5F, 3.8D, 0.5D), new Move("sword_attack4", 22, 10, 1.7F, 4.0D, 0.7D),
            new Move("sword_attack5", 22, 10, 2.0F, 4.2D, 1.2D)};
    private static final Move[] SWORD_SKILL = {
            new Move("sword_ab1", 17, 7, 2.0F, 4.0D, 0.8D), new Move("sword_ab2", 18, 8, 2.0F, 4.0D, 0.8D),
            new Move("sword_ab3", 31, 14, 2.6F, 4.4D, 1.2D), new Move("sword_ab4", 35, 17, 3.0F, 4.6D, 1.5D),
            new Move("sword_ab5", 50, 26, 3.8F, 6.0D, 2.0D)};

    private static final int DOMAIN_INTRO = 40;
    private static final int DOMAIN_TRIAL = 241;
    private static final int SENTENCE_TICKS = 70;
    private static final int SENTENCE_HIT = 40;
    private static final double DOMAIN_RADIUS = 20.0D;
    private static final int SWORD_DURATION = 1200;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Move move;
    private int moveTick;
    private boolean moveHit;
    private int comboIndex;
    private long nextMove;
    private long nextDomain;
    private int domainTick = -1;
    private int sentenceTick = -1;
    private long swordUntil;

    public HigurumaAwakenedEntity(EntityType<? extends HigurumaAwakenedEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        equip(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    public boolean isSwordForm() { return entityData.get(SWORD); }

    /** Gavel normally, the Executioner's Sword in sword form (falls back to vanilla items). */
    private void equip(boolean sword) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("jujutsucraft", sword ? "executioners_sword" : "gavel"));
        if (item == null || item == Items.AIR) item = sword ? Items.IRON_SWORD : Items.WOODEN_AXE;
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    private static boolean isCurse(LivingEntity e) {
        if (e instanceof Player || e instanceof HigurumaAwakenedEntity || e instanceof YujiAwakenedEntity
                || e instanceof GojoAwakenedEntity) return false;
        if (e instanceof Monster) return true;
        String n = e.getClass().getSimpleName();
        return n.startsWith("Sukuna") || n.startsWith("Mahito") || n.startsWith("Cursed") || n.startsWith("Jogo")
                || n.startsWith("Choso") || n.startsWith("Hanami");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e != this && isCurse(e)));
    }

    private boolean busy() { return move != null || domainTick >= 0 || sentenceTick >= 0; }

    private static final class ChaseGoal extends Goal {
        private final HigurumaAwakenedEntity mob;
        ChaseGoal(HigurumaAwakenedEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.busy();
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (mob.distanceToSqr(t) > 2.6D * 2.6D) mob.getNavigation().moveTo(t, 1.25D);
            else mob.getNavigation().stop();
        }
    }

    private boolean isFoe(Entity e) {
        return e instanceof LivingEntity le && le != this && (isCurse(le) || le == getTarget());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive() && level() instanceof ServerLevel server) serverTick(server);
    }

    private void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        if (isSwordForm() && now >= swordUntil && !busy()) {
            entityData.set(SWORD, false);
            equip(false);
        }
        if (domainTick >= 0) { runDomain(level); return; }
        if (sentenceTick >= 0) { runSentence(level); return; }
        if (move != null) { runMove(level); return; }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;
        double dist = distanceTo(t);
        if (!isSwordForm() && now >= nextDomain && dist < 14.0D
                && (getHealth() < getMaxHealth() * 0.7F || random.nextInt(250) == 0)) {
            domainTick = 0;
            getNavigation().stop();
            entityData.set(ANIM, "domain");
            return;
        }
        if (dist < 3.4D && now >= nextMove) {
            boolean sword = isSwordForm();
            Move[] combo = sword ? SWORD_COMBO : GAVEL_COMBO;
            Move[] skill = sword ? SWORD_SKILL : GAVEL_SKILL;
            move = random.nextInt(4) == 0 ? skill[random.nextInt(skill.length)] : combo[comboIndex++ % combo.length];
            moveTick = 0;
            moveHit = false;
            getNavigation().stop();
            entityData.set(ANIM, move.anim());
        }
    }

    private void freeze() {
        getNavigation().stop();
        Vec3 d = getDeltaMovement();
        setDeltaMovement(0.0D, Math.min(0.0D, d.y), 0.0D);
    }

    private void face(Entity t) {
        float yaw = (float) (Math.atan2(t.getZ() - getZ(), t.getX() - getX()) * 180.0D / Math.PI) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void runMove(ServerLevel level) {
        moveTick++;
        freeze();
        LivingEntity t = getTarget();
        if (t != null && moveTick < move.hit()) face(t);
        if (moveTick == move.hit() && !moveHit) {
            moveHit = true;
            Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * move.damage();
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(move.reach()), this::isFoe)) {
                Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
                if (to.lengthSqr() > 1.0E-4D && to.normalize().dot(look) < 0.2D) continue;
                e.hurt(damageSources().mobAttack(this), dmg);
                Vec3 push = to.lengthSqr() > 1.0E-4D ? to.normalize().scale(move.knockback()) : Vec3.ZERO;
                e.push(push.x, 0.2D + move.knockback() * 0.1D, push.z);
                level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6D, e.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.1D);
            }
            level.playSound(null, getX(), getY(), getZ(),
                    isSwordForm() ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.9F);
        }
        if (moveTick >= move.ticks()) {
            move = null;
            nextMove = level.getGameTime() + 5;
            entityData.set(ANIM, "");
        }
    }

    // ---- Deadly Sentencing: trial -> verdict -> executioner's sword

    private void runDomain(ServerLevel level) {
        domainTick++;
        freeze();
        int end = DOMAIN_INTRO + DOMAIN_TRIAL;
        if (domainTick == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (domainTick == DOMAIN_INTRO) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 4.0F, 0.6F);
            level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.0D, getZ(), 1, 0, 0, 0, 0);
        }
        if (domainTick >= DOMAIN_INTRO && domainTick < end) {
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(DOMAIN_RADIUS),
                    x -> isFoe(x) && x.distanceToSqr(this) <= DOMAIN_RADIUS * DOMAIN_RADIUS)) {
                LivingEntity le = (LivingEntity) e;
                if (domainTick % 10 == 0) {
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 4, false, false));
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 14, 3, false, false));
                }
                if (domainTick % 40 == 0) {
                    le.hurt(damageSources().indirectMagic(this, this), 3.0F);
                    level.sendParticles(ParticleTypes.ENCHANT, le.getX(), le.getY() + 1.0D, le.getZ(), 20, 0.5D, 0.8D, 0.5D, 0.5D);
                }
            }
            if (domainTick % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0D, getZ(), 12, DOMAIN_RADIUS / 3.0D, 4.0D, DOMAIN_RADIUS / 3.0D, 0.01D);
            }
        }
        if (domainTick >= end) {
            // Verdict: guilty. The judge's confiscation weakens the accused and the sword is granted.
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(DOMAIN_RADIUS),
                    x -> isFoe(x) && x.distanceToSqr(this) <= DOMAIN_RADIUS * DOMAIN_RADIUS)) {
                ((LivingEntity) e).addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
            }
            domainTick = -1;
            sentenceTick = 0;
            entityData.set(SWORD, true);
            equip(true);
            swordUntil = level.getGameTime() + SWORD_DURATION + SENTENCE_TICKS;
            entityData.set(ANIM, "death_sentence");
            nextDomain = level.getGameTime() + 2400;
        }
    }

    private void runSentence(ServerLevel level) {
        sentenceTick++;
        freeze();
        LivingEntity t = getTarget();
        if (t != null) face(t);
        if (sentenceTick == SENTENCE_HIT && t != null && distanceToSqr(t) < 12.0D * 12.0D) {
            // The death sentence: a heavy blow scaled to the accused's health.
            float dmg = Math.min(120.0F, Math.max(60.0F, t.getMaxHealth() * 0.4F));
            t.hurt(damageSources().indirectMagic(this, this), dmg);
            level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1.0D, t.getZ(), 3, 0.4D, 0.4D, 0.4D, 0.0D);
            level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.0D, t.getZ(), 40, 0.6D, 0.8D, 0.6D, 0.4D);
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        if (sentenceTick >= SENTENCE_TICKS) {
            sentenceTick = -1;
            entityData.set(ANIM, "");
            nextMove = level.getGameTime() + 10;
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (domainTick >= 0 && domainTick < DOMAIN_INTRO) return false;
        return super.hurt(source, amount);
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public java.util.List<String> castableSkills() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Move[] group : new Move[][] {GAVEL_COMBO, GAVEL_SKILL, SWORD_COMBO, SWORD_SKILL}) {
            for (Move m : group) names.add(m.anim());
        }
        names.add("domain");
        return names;
    }

    @Override
    public boolean cast(String name, LivingEntity target) {
        if (busy() || !(level() instanceof ServerLevel)) return false;
        if (name.equals("domain")) {
            domainTick = 0;
            getNavigation().stop();
            entityData.set(ANIM, "domain");
            return true;
        }
        for (Move[] group : new Move[][] {GAVEL_COMBO, GAVEL_SKILL, SWORD_COMBO, SWORD_SKILL}) {
            for (Move m : group) {
                if (m.anim().equals(name)) {
                    move = m;
                    moveTick = 0;
                    moveHit = false;
                    getNavigation().stop();
                    entityData.set(ANIM, m.anim());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return TestCastable.interact(this, player, hand);
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIM, "");
        entityData.define(SWORD, false);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SWORD, false);
        equip(false);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> {
            String a = entityData.get(ANIM);
            if (!a.isEmpty()) return state.setAndContinue(anim(a));
            return state.setAndContinue(anim(state.isMoving() ? "walk" : "idle"));
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
