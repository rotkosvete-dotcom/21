package ru.talentshow;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Бот-«игрок»: скин задаётся ником, синхронизируется на клиент. */
public class BotEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.STRING);
    public String botId = "";

    public BotEntity(EntityType<? extends BotEntity> t, Level l) { super(t, l); }

    public static AttributeSupplier.Builder attrs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN, "Steve");
    }
    public String getSkin() { return entityData.get(SKIN); }
    public void setSkin(String s) { entityData.set(SKIN, s); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8f));
    }
    @Override public boolean hurt(DamageSource s, float a) {
        return s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(s, a);
    }
    @Override public boolean shouldBeSaved() { return false; }   // боты не остаются в мире
    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
}
