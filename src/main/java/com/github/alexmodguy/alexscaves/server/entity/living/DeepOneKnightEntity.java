package com.github.alexmodguy.alexscaves.server.entity.living;

import com.github.alexmodguy.alexscaves.server.entity.ai.DeepOneAttackGoal;
import com.github.alexmodguy.alexscaves.server.entity.ai.DeepOneDisappearGoal;
import com.github.alexmodguy.alexscaves.server.entity.ai.DeepOneReactToPlayerGoal;
import com.github.alexmodguy.alexscaves.server.entity.ai.DeepOneWanderGoal;
import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.UUID;

public class DeepOneKnightEntity extends DeepOneBaseEntity {

    public static final Animation ANIMATION_THROW = Animation.create(20);
    public static final Animation ANIMATION_BITE = Animation.create(8);
    public static final Animation ANIMATION_SCRATCH = Animation.create(22);
    public static final Animation ANIMATION_TRADE = Animation.create(55);
    private ItemStack swappedWeapon = ItemStack.EMPTY;

    private UUID lastThrownTrident = null;
    private boolean melee = random.nextBoolean();
    private static final EntityDimensions SWIMMING_SIZE = new EntityDimensions(1.2F, 1.3F, false);

    public DeepOneKnightEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DeepOneAttackGoal(this));
        this.goalSelector.addGoal(1, new DeepOneReactToPlayerGoal(this){
            @Override
            public boolean canUse() {
                return super.canUse() && lastThrownTrident == null;
            }

            @Override
            public boolean canContinueToUse() {
                return super.canContinueToUse() && lastThrownTrident == null;
            }
        });
        this.goalSelector.addGoal(2, new DeepOneDisappearGoal(this));
        this.goalSelector.addGoal(3, new DeepOneWanderGoal(this, 12, 1D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByHostileTargetGoal());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, 20, false, true, playerTargetPredicate));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Husk.class, 12, true, false, null));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.MAX_HEALTH, 60.0D).add(Attributes.ATTACK_DAMAGE, 5.0D);
    }

    public EntityDimensions getSwimmingSize() {
        return SWIMMING_SIZE;
    }

    public boolean isNoon() {
        String s = ChatFormatting.stripFormatting(this.getName().getString());
        return s != null && (s.toLowerCase().contains("noon") || s.toLowerCase().contains("stinkyfish"));
    }

    @Override
    public void tick(){
        super.tick();
        LivingEntity target = getTarget();
        if((target == null || !target.isAlive()) && !level.isClientSide && lastThrownTrident != null){
            pickUpTrident();
        }
    }

    @Override
    public boolean startDisappearBehavior(Player player) {
        this.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ(), 20.0F, (float) this.getMaxHeadXRot());
        if(!this.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()){
            swappedWeapon = this.getItemInHand(InteractionHand.MAIN_HAND).copy();
        }
        this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.INK_SAC));
        if (this.getAnimation() == NO_ANIMATION) {
            this.setAnimation(ANIMATION_THROW);
        } else if (this.getAnimation() == ANIMATION_THROW) {
            if (this.getAnimationTick() > 10) {
                if(this.getItemInHand(InteractionHand.MAIN_HAND).is(Items.INK_SAC)){
                    this.setItemInHand(InteractionHand.MAIN_HAND, swappedWeapon);
                }
                return super.startDisappearBehavior(player);
            }
        }
        return false;
    }

    protected boolean pickUpTrident(){
        if(lastThrownTrident != null && !level.isClientSide){
            if(((ServerLevel)level).getEntity(lastThrownTrident) instanceof ThrownTrident trident){
                if(this.distanceTo(trident) < 2.0D){
                    if(this.getAnimation() == NO_ANIMATION){
                        this.setAnimation(ANIMATION_SCRATCH);
                    }
                    this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
                    trident.remove(RemovalReason.DISCARDED);
                    this.getNavigation().stop();
                    lastThrownTrident = null;
                }else{
                    this.getNavigation().moveTo(trident, 1.5F);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void startAttackBehavior(LivingEntity target) {
        double distance = this.distanceTo(target);
        float f = this.getBbWidth() + target.getBbWidth();
        if((distance > 15 || !melee)){
            melee = false;
            if(!pickUpTrident() && this.getItemInHand(InteractionHand.MAIN_HAND).is(Items.TRIDENT)){
                if(this.hasLineOfSight(target) && distance < 35){
                    this.getNavigation().stop();
                    if(this.getAnimation() == NO_ANIMATION){
                        this.setAnimation(ANIMATION_THROW);
                    }else if(this.getAnimation() == ANIMATION_THROW && this.getAnimationTick() > 8){
                        melee = true;
                        throwTrident(target);
                        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }
                }else{
                    this.getNavigation().moveTo(target, 1.2);
                }
            }
        }
        if(melee){
            if(distance < f + 1.0F){
                if (this.getAnimation() == IAnimatedEntity.NO_ANIMATION) {
                    setAnimation(this.getRandom().nextBoolean() ? ANIMATION_SCRATCH : ANIMATION_BITE);
                }
            }else{
                this.getNavigation().moveTo(target, 1.2);
            }
        }
        if (this.getAnimation() == ANIMATION_SCRATCH) {
            if (this.getAnimationTick() > 5 && this.getAnimationTick() < 9 || this.getAnimationTick() > 12 && this.getAnimationTick() < 16) {
                checkAndDealMeleeDamage(target, 1.0F);
            }
        }
        if (this.getAnimation() == ANIMATION_BITE) {
            if (this.getAnimationTick() > 3 && this.getAnimationTick() <= 7) {
                checkAndDealMeleeDamage(target, 1.0F);
            }
        }
    }

    public void throwTrident(LivingEntity target) {
        ThrownTrident throwntrident = new ThrownTrident(this.level, this, new ItemStack(Items.TRIDENT));
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333D) - throwntrident.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        throwntrident.shoot(d0, d1 + d3 * (double)0.2F, d2, 1.6F, (float)(14 - this.level.getDifficulty().getId() * 4));
        this.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level.addFreshEntity(throwntrident);
        lastThrownTrident = throwntrident.getUUID();
    }

    protected void checkAndDealMeleeDamage(LivingEntity target, float multiplier) {
        super.checkAndDealMeleeDamage(target, multiplier);
        melee = random.nextBoolean();
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if(!swappedWeapon.isEmpty()){
            compound.put("SwappedWeapon", swappedWeapon.save(new CompoundTag()));
        }
        if (this.lastThrownTrident != null) {
            compound.putUUID("LastTridentUUID", this.lastThrownTrident);
        }
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if(compound.contains("SwappedWeapon")){
            swappedWeapon = ItemStack.of(compound.getCompound("SwappedWeapon"));
        }
        if (compound.hasUUID("LastTridentUUID")) {
            this.lastThrownTrident = compound.getUUID("HologramUUID");
        }
    }

    public SpawnGroupData finalizeSpawn(ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, MobSpawnType mobSpawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag compoundTag) {
        spawnGroupData = super.finalizeSpawn(serverLevelAccessor, difficultyInstance, mobSpawnType, spawnGroupData, compoundTag);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        return spawnGroupData;
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_THROW, ANIMATION_BITE, ANIMATION_SCRATCH, ANIMATION_TRADE};
    }

}
