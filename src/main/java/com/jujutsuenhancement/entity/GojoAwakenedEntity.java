package com.jujutsuenhancement.entity;

import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;
import java.util.List;

/**
 * Awakened Gojo: a standalone mob (the original JujutsuCraft is untouched) that uses
 * the Bedrock "Jujutsu Awakening" animations and timings for its techniques.
 *
 * Timings are taken from the Bedrock behaviour pack (limitless5b/5c/6a/6b):
 *   normal purple : 24.0 s animation, projectile forms at 18.0 s and is fired at 22.75 s
 *   200% purple   : 16.05 s animation, 5 s charge, projectile forms at 10 s and fires at 11.75 s
 *   unlimited     : 3.8 s animation, crouching
 *   domain        : 18.5 s animation, 10 s of stun inside the domain
 *   quick domain  : 7.025 s animation, domain starts after 0.2 s
 */
public final class GojoAwakenedEntity extends PathfinderMob implements GeoEntity, TestCastable {
    // --- animation state ids (synced to clients) ---
    public static final int ANIM_NONE = 0;
    public static final int ANIM_PURPLE_NORMAL = 1;
    public static final int ANIM_PURPLE_200 = 2;
    public static final int ANIM_PURPLE_UNLIMITED = 3;
    public static final int ANIM_DOMAIN = 4;
    public static final int ANIM_DOMAIN_QUICK = 5;
    public static final int ANIM_ATTACK_BASE = 10; // 11..15 = attack1..attack5

    private static final EntityDataAccessor<Integer> ANIM =
            SynchedEntityData.defineId(GojoAwakenedEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.gojo.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.gojo.walk");
    private static final RawAnimation PURPLE_NORMAL = RawAnimation.begin().thenPlay("animation.gojo.purple_normal");
    private static final RawAnimation PURPLE_200 = RawAnimation.begin().thenPlay("animation.gojo.purple_200");
    private static final RawAnimation PURPLE_UNLIMITED = RawAnimation.begin().thenPlay("animation.gojo.purple_unlimited");
    private static final RawAnimation DOMAIN = RawAnimation.begin().thenPlay("animation.gojo.domain");
    private static final RawAnimation DOMAIN_QUICK = RawAnimation.begin().thenPlay("animation.gojo.domain_quick");
    private static final RawAnimation[] ATTACKS = {
            RawAnimation.begin().thenPlay("animation.gojo.attack1"),
            RawAnimation.begin().thenPlay("animation.gojo.attack2"),
            RawAnimation.begin().thenPlay("animation.gojo.attack3"),
            RawAnimation.begin().thenPlay("animation.gojo.attack4"),
            RawAnimation.begin().thenPlay("animation.gojo.attack5")};
    private static final int[] ATTACK_TICKS = {21, 16, 16, 17, 19};

    // --- skill timings (ticks) ---
    private static final int PURPLE_NORMAL_TOTAL = 480;
    private static final int PURPLE_NORMAL_FORM = 360;
    private static final int PURPLE_NORMAL_FIRE = 455;
    private static final int PURPLE_200_TOTAL = 321;
    private static final int PURPLE_200_FORM = 100;
    private static final int PURPLE_200_FIRE = 240;
    private static final int UNLIMITED_TOTAL = 76;
    private static final int UNLIMITED_RELEASE = 56;
    private static final int DOMAIN_TOTAL = 370;
    private static final int DOMAIN_START = 1;
    private static final int DOMAIN_LENGTH = 200;
    private static final int QUICK_TOTAL = 141;
    private static final int QUICK_START = 4;
    private static final int QUICK_LENGTH = 80;
    private static final double DOMAIN_RADIUS = 32.0D;

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.42F, 0.08F, 0.95F), 1.6F);
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.12F, 0.38F, 1.0F), 1.3F);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.18F), 1.3F);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // --- server state ---
    private int skill = ANIM_NONE;
    private int skillTick;
    private int attackIndex;
    private boolean attackHit;
    private boolean purple200Used;
    private long nextAttackTime;
    private long nextPurpleTime;
    private long nextUnlimitedTime;
    private long nextDomainTime;
    private long nextQuickDomainTime;
    private boolean casting200ForSukuna;
    private long domainEndTime;
    private final java.util.Set<java.util.UUID> frozenMobs = new java.util.HashSet<>();
    private static final int DOMAIN_DURATION = 900;
    private static final double DOMAIN_LOCK_RADIUS = 40.0D;

    public GojoAwakenedEntity(EntityType<? extends GojoAwakenedEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    public int getAnimState() {
        return this.entityData.get(ANIM);
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ChaseTargetGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e != this && isHostileCandidate(e)));
    }

    /** Hostile mobs and JujutsuCraft curses / Sukuna forms count as enemies; players and villagers do not. */
    private boolean isHostileCandidate(LivingEntity e) {
        if (e instanceof GojoAwakenedEntity || e instanceof Player) return false;
        if (e instanceof Monster) return true;
        String name = e.getClass().getSimpleName();
        return name.startsWith("Sukuna") || name.startsWith("Cursed") || name.startsWith("Mahito")
                || name.startsWith("Jogo") || name.startsWith("Choso");
    }

    /** Whether a target of one of this Gojo's techniques should be hurt. */
    public boolean isEnemy(Entity e) {
        if (!(e instanceof LivingEntity living) || e == this || e instanceof GojoAwakenedEntity) return false;
        if (living instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (living == getTarget()) return true;
        if (living instanceof Mob m && m.getTarget() == this) return true;
        return isHostileCandidate(living);
    }

    private static final class ChaseTargetGoal extends Goal {
        private final GojoAwakenedEntity gojo;
        ChaseTargetGoal(GojoAwakenedEntity gojo) {
            this.gojo = gojo;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            LivingEntity t = gojo.getTarget();
            return t != null && t.isAlive() && gojo.skill == ANIM_NONE;
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            LivingEntity t = gojo.getTarget();
            if (t == null) return;
            gojo.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (gojo.distanceToSqr(t) > 2.6D * 2.6D) {
                gojo.getNavigation().moveTo(t, 1.15D);
            } else {
                gojo.getNavigation().stop();
            }
        }
    }

    // ------------------------------------------------------------- skills

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.isAlive() && this.level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    private void serverTick(ServerLevel level) {
        tickDomainLock(level);
        if (skill != ANIM_NONE) {
            runSkill(level);
            return;
        }
        long now = level.getGameTime();
        // Seeing Sukuna (possessed Megumi): release one 200% purple, then never again.
        if (!purple200Used) {
            Entity sukuna = findPossessedMegumi(level);
            if (sukuna != null) {
                casting200ForSukuna = true;
                startSkill(ANIM_PURPLE_200, sukuna);
                return;
            }
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        double dist = this.distanceTo(target);
        // Low health: unlimited purple.
        if (getHealth() <= getMaxHealth() * 0.3F && now >= nextUnlimitedTime) {
            startSkill(ANIM_PURPLE_UNLIMITED, target);
            return;
        }
        if (dist < 3.4D && now >= nextAttackTime) {
            startSkill(ANIM_ATTACK_BASE + 1 + attackIndex, target);
            attackIndex = (attackIndex + 1) % ATTACKS.length;
            return;
        }
        if (dist >= 3.4D && this.random.nextInt(40) == 0) {
            int enemies = countEnemies(level, DOMAIN_RADIUS);
            if (now >= nextDomainTime && (enemies >= 3 || getHealth() < getMaxHealth() * 0.6F)) {
                startSkill(ANIM_DOMAIN, target);
            } else if (now >= nextQuickDomainTime && enemies >= 2) {
                startSkill(ANIM_DOMAIN_QUICK, target);
            } else if (now >= nextPurpleTime && dist >= 8.0D && hasLineOfSight(target)) {
                startSkill(ANIM_PURPLE_NORMAL, target);
            }
        }
    }

    private Entity findPossessedMegumi(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(24.0D);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : level.getEntities(this, box, Entity::isAlive)) {
            String cn = e.getClass().getSimpleName();
            if (!cn.equals("SukunaFushiguroEntity") && !cn.equals("SukunaAwakenedEntity")) continue;
            if (!this.hasLineOfSight(e)) continue;
            double d = e.distanceToSqr(this);
            if (d < bestDist) {
                best = e;
                bestDist = d;
            }
        }
        return best;
    }

    private int countEnemies(ServerLevel level, double radius) {
        int n = 0;
        for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(radius), Entity::isAlive)) {
            if (isEnemy(e)) n++;
        }
        return n;
    }

    private Entity skillTarget;

    private void startSkill(int id, Entity target) {
        this.skill = id;
        this.skillTick = 0;
        this.attackHit = false;
        this.skillTarget = target;
        this.getNavigation().stop();
        this.entityData.set(ANIM, id);
        if (id == ANIM_PURPLE_UNLIMITED) {
            this.setShiftKeyDown(true);
            this.setNoGravity(true);
            this.setPos(getX(), getY() + 2.0D, getZ()); // levitation before the shot
        }
        this.setTarget(target instanceof LivingEntity le ? le : this.getTarget());
    }

    private void endSkill(long cooldownTicks) {
        long now = this.level().getGameTime();
        switch (skill) {
            case ANIM_PURPLE_NORMAL -> nextPurpleTime = now + cooldownTicks;
            case ANIM_PURPLE_UNLIMITED -> nextUnlimitedTime = now + cooldownTicks;
            case ANIM_DOMAIN -> nextDomainTime = now + cooldownTicks;
            case ANIM_DOMAIN_QUICK -> nextQuickDomainTime = now + cooldownTicks;
            default -> { }
        }
        if (skill > ANIM_ATTACK_BASE) nextAttackTime = now + 8;
        this.skill = ANIM_NONE;
        this.skillTick = 0;
        this.skillTarget = null;
        this.casting200ForSukuna = false;
        this.setShiftKeyDown(false);
        this.setNoGravity(false);
        this.entityData.set(ANIM, ANIM_NONE);
    }

    private void runSkill(ServerLevel level) {
        skillTick++;
        this.getNavigation().stop();
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
        if (skillTarget != null && !skillTarget.isAlive()) skillTarget = null;
        if (skillTarget != null && skill != ANIM_DOMAIN && skill != ANIM_DOMAIN_QUICK) faceTarget(skillTarget);

        switch (skill) {
            case ANIM_PURPLE_NORMAL -> {
                if (skillTick >= PURPLE_NORMAL_FORM && skillTick < PURPLE_NORMAL_FIRE) gather(level, 6.0D, false);
                if (skillTick == PURPLE_NORMAL_FIRE) firePurple(level, false);
                if (skillTick >= PURPLE_NORMAL_TOTAL) endSkill(600);
            }
            case ANIM_PURPLE_200 -> {
                if (skillTick > 20 && skillTick < PURPLE_200_FIRE && skillTick % 3 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.4D, getZ(), 4, 0.8D, 0.8D, 0.8D, 0.05D);
                }
                if (skillTick >= PURPLE_200_FORM && skillTick < PURPLE_200_FIRE) gather(level, 12.0D, true);
                if (skillTick == PURPLE_200_FIRE) {
                    firePurple(level, true);
                    if (casting200ForSukuna) purple200Used = true;
                }
                if (skillTick >= PURPLE_200_TOTAL) {
                    purple200Used |= casting200ForSukuna;
                    endSkill(0);
                }
            }
            case ANIM_PURPLE_UNLIMITED -> {
                if (skillTick > 5 && skillTick < UNLIMITED_RELEASE) {
                    level.sendParticles(RED, getX() - 1.2D, getY() + getBbHeight() + 1.0D, getZ(), 6, 0.4D, 0.4D, 0.4D, 0.02D);
                    level.sendParticles(BLUE, getX() + 1.2D, getY() + getBbHeight() + 1.0D, getZ(), 6, 0.4D, 0.4D, 0.4D, 0.02D);
                }
                if (skillTick == UNLIMITED_RELEASE) fireUnlimited(level);
                if (skillTick >= UNLIMITED_TOTAL) endSkill(600);
            }
            case ANIM_DOMAIN -> runDomain(level, DOMAIN_START, DOMAIN_LENGTH, DOMAIN_TOTAL, 2400, 6, 26.0F);
            case ANIM_DOMAIN_QUICK -> runDomain(level, QUICK_START, QUICK_LENGTH, QUICK_TOTAL, 1200, 3, 12.0F);
            default -> runAttack(level);
        }
    }

    private void faceTarget(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.yRotO = yaw;
    }

    private Vec3 aimDirection() {
        if (skillTarget != null) {
            Vec3 d = skillTarget.getBoundingBox().getCenter().subtract(this.getEyePosition());
            if (d.lengthSqr() > 1.0E-4D) return d.normalize();
        }
        return this.getLookAngle().normalize();
    }

    private void gather(ServerLevel level, double ahead, boolean big) {
        Vec3 dir = this.getLookAngle().normalize();
        Vec3 c = this.getEyePosition().add(dir.scale(ahead));
        double spread = big ? 1.4D : 0.7D;
        level.sendParticles(PURPLE, c.x, c.y, c.z, big ? 16 : 8, spread, spread, spread, 0.02D);
        level.sendParticles(BLUE, c.x, c.y, c.z, big ? 8 : 4, spread, spread, spread, 0.03D);
        if (skillTick % 10 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        }
    }

    private void firePurple(ServerLevel level, boolean charged) {
        Vec3 dir = aimDirection();
        HollowPurpleEntity purple = ModEntities.HOLLOW_PURPLE.get().create(level);
        if (purple == null) return;
        Vec3 origin = this.getEyePosition().add(dir.scale(charged ? 16.0D : 6.0D));
        purple.setPos(origin.x, origin.y, origin.z);
        if (charged) purple.setCharged200Percent();
        purple.launch(this, dir);
        if (level.addFreshEntity(purple)) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                    charged ? 2.5F : 1.8F, charged ? 0.55F : 0.7F);
            level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        }
    }

    private void fireUnlimited(ServerLevel level) {
        HollowPurpleEntity purple = ModEntities.HOLLOW_PURPLE.get().create(level);
        if (purple == null) return;
        Vec3 dir = aimDirection();
        Vec3 origin = this.getEyePosition().add(dir);
        purple.setPos(origin.x, origin.y, origin.z);
        purple.setUnlimited();
        purple.launch(this, dir);
        level.addFreshEntity(purple);
    }

    /**
     * Unlimited Void as the Bedrock functions run it: there is no direct damage. Everything in the
     * domain is locked (Stun/Move/Disabled refreshed every tick) for the whole duration, which
     * outlives Gojo's own animation. {@code start} is the tick the lock begins (1 for the full
     * domain, 4 = 0.2 s for the quick domain).
     */
    private void runDomain(ServerLevel level, int start, int length, int total, long cooldown, int amplifier, float finalDamage) {
        if (skillTick == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        if (skillTick == start) {
            domainEndTime = level.getGameTime() + DOMAIN_DURATION;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 4.0F, 0.5F);
            level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.0D, getZ(), 1, 0, 0, 0, 0);
        }
        if (skillTick >= total) {
            endSkill(Math.max(0L, domainEndTime - level.getGameTime()) + cooldown);
        }
    }

    /** Keeps every enemy inside the domain frozen until it ends. */
    private void tickDomainLock(ServerLevel level) {
        long now = level.getGameTime();
        if (now >= domainEndTime) {
            releaseFrozen(level);
            return;
        }
        for (Entity e : level.getEntities(this, getBoundingBox().inflate(DOMAIN_LOCK_RADIUS),
                x -> isEnemy(x) && x.distanceToSqr(this) <= DOMAIN_LOCK_RADIUS * DOMAIN_LOCK_RADIUS)) {
            LivingEntity living = (LivingEntity) e;
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 99, false, false));
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6, 9, false, false));
            living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 6, 9, false, false));
            living.addEffect(new MobEffectInstance(MobEffects.JUMP, 6, 128, false, false));
            if (living instanceof Mob mob) {
                mob.setTarget(null);
                if (!mob.isNoAi()) {
                    mob.setNoAi(true);
                    frozenMobs.add(mob.getUUID());
                }
            }
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, living.getX(), living.getY() + 1.0D, living.getZ(), 3, 0.3D, 0.6D, 0.3D, 0.01D);
            }
        }
    }

    private void releaseFrozen(ServerLevel level) {
        if (frozenMobs.isEmpty()) return;
        for (UUID id : frozenMobs) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob) mob.setNoAi(false);
        }
        frozenMobs.clear();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel server && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            releaseFrozen(server);
        }
        super.remove(reason);
    }

    private void runAttack(ServerLevel level) {
        int index = skill - ANIM_ATTACK_BASE - 1;
        if (index < 0 || index >= ATTACKS.length) {
            endSkill(0);
            return;
        }
        int total = ATTACK_TICKS[index];
        int hitAt = Math.max(1, (int) (total * 0.4F));
        if (skillTick == hitAt && !attackHit) {
            attackHit = true;
            Entity t = skillTarget;
            if (t != null && this.distanceToSqr(t) <= 4.5D * 4.5D) {
                float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                t.hurt(this.damageSources().mobAttack(this), damage * (index == 4 ? 1.8F : 1.0F));
                Vec3 push = t.position().subtract(this.position()).normalize().scale(index == 4 ? 1.2D : 0.5D);
                t.push(push.x, 0.25D, push.z);
                level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2F, 0.8F);
            }
        }
        if (skillTick >= total) endSkill(0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Infinity: nothing short of the void reaches Gojo while he is using a technique.
        if (skill != ANIM_NONE && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public java.util.List<String> castableSkills() {
        return java.util.List.of("purple", "purple200", "unlimited", "domain", "quick_domain",
                "attack1", "attack2", "attack3", "attack4", "attack5");
    }

    @Override
    public boolean cast(String name, LivingEntity target) {
        if (skill != ANIM_NONE || !(level() instanceof ServerLevel)) return false;
        int id = switch (name) {
            case "purple" -> ANIM_PURPLE_NORMAL;
            case "purple200" -> ANIM_PURPLE_200;
            case "unlimited" -> ANIM_PURPLE_UNLIMITED;
            case "domain" -> ANIM_DOMAIN;
            case "quick_domain" -> ANIM_DOMAIN_QUICK;
            case "attack1" -> ANIM_ATTACK_BASE + 1;
            case "attack2" -> ANIM_ATTACK_BASE + 2;
            case "attack3" -> ANIM_ATTACK_BASE + 3;
            case "attack4" -> ANIM_ATTACK_BASE + 4;
            case "attack5" -> ANIM_ATTACK_BASE + 5;
            default -> -1;
        };
        if (id < 0) return false;
        casting200ForSukuna = false;
        startSkill(id, target);
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return TestCastable.interact(this, player, hand);
    }


    // --------------------------------------------------------- persistence

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIM, ANIM_NONE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Purple200Used", purple200Used);
        tag.putInt("AttackIndex", attackIndex);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        purple200Used = tag.getBoolean("Purple200Used");
        attackIndex = Math.floorMod(tag.getInt("AttackIndex"), ATTACKS.length);
    }

    // ------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            int a = getAnimState();
            if (a == ANIM_PURPLE_NORMAL) return state.setAndContinue(PURPLE_NORMAL);
            if (a == ANIM_PURPLE_200) return state.setAndContinue(PURPLE_200);
            if (a == ANIM_PURPLE_UNLIMITED) return state.setAndContinue(PURPLE_UNLIMITED);
            if (a == ANIM_DOMAIN) return state.setAndContinue(DOMAIN);
            if (a == ANIM_DOMAIN_QUICK) return state.setAndContinue(DOMAIN_QUICK);
            if (a > ANIM_ATTACK_BASE && a <= ANIM_ATTACK_BASE + ATTACKS.length) {
                return state.setAndContinue(ATTACKS[a - ANIM_ATTACK_BASE - 1]);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
