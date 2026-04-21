package lol.sylvie.bedframe.screen.waystones;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SimpleWaystoneTeleportContext implements WaystoneTeleportContext {
    private final Entity entity;
    private final Waystone targetWaystone;
    private final List<MobEntity> leashedEntities;
    private final List<Entity> additionalEntities = new ArrayList<>();
    private Optional<Waystone> fromWaystone;
    private ItemStack warpItem = ItemStack.EMPTY;
    private Hand warpHand = Hand.MAIN_HAND;
    private WarpRequirement requirements = NoopWarpRequirement.INSTANCE;
    private boolean playsSound = true;
    private boolean playsEffect = true;
    private boolean appliesModifiers = true;
    private final Set<Identifier> flags = new HashSet<>();

    public SimpleWaystoneTeleportContext(Entity entity, Waystone targetWaystone, @Nullable Waystone fromWaystone, List<MobEntity> leashedEntities) {
        this.entity = entity;
        this.targetWaystone = targetWaystone;
        this.fromWaystone = Optional.ofNullable(fromWaystone);
        this.leashedEntities = leashedEntities;
    }

    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public Waystone getTargetWaystone() {
        return targetWaystone;
    }

    @Override
    public List<MobEntity> getLeashedEntities() {
        return leashedEntities;
    }

    @Override
    public List<Entity> getAdditionalEntities() {
        return additionalEntities;
    }

    @Override
    public WaystoneTeleportContext addAdditionalEntity(Entity additionalEntity) {
        additionalEntities.add(additionalEntity);
        return this;
    }

    @Override
    public Optional<Waystone> getFromWaystone() {
        return fromWaystone;
    }

    @Override
    public WaystoneTeleportContext setFromWaystone(@Nullable Waystone fromWaystone) {
        this.fromWaystone = Optional.ofNullable(fromWaystone);
        return this;
    }

    @Override
    public ItemStack getWarpItem() {
        return warpItem;
    }

    @Override
    public WaystoneTeleportContext setWarpItem(ItemStack warpItem) {
        this.warpItem = warpItem;
        return this;
    }

    @Override
    public Hand getWarpHand() {
        return warpHand;
    }

    @Override
    public WaystoneTeleportContext setWarpHand(Hand warpHand) {
        this.warpHand = warpHand;
        return this;
    }

    @Override
    public boolean isDimensionalTeleport() {
        return !targetWaystone.getDimension().equals(entity.getEntityWorld().getRegistryKey());
    }

    @Override
    public WarpRequirement getRequirements() {
        return requirements;
    }

    @Override
    public WaystoneTeleportContext setRequirements(WarpRequirement warpRequirement) {
        this.requirements = warpRequirement;
        return this;
    }

    @Override
    public boolean playsSound() {
        return playsSound;
    }

    @Override
    public WaystoneTeleportContext setPlaysSound(boolean playsSound) {
        this.playsSound = playsSound;
        return this;
    }

    @Override
    public boolean playsEffect() {
        return playsEffect;
    }

    @Override
    public WaystoneTeleportContext setPlaysEffect(boolean playsEffect) {
        this.playsEffect = playsEffect;
        return this;
    }

    @Override
    public boolean appliesModifiers() {
        return appliesModifiers;
    }

    @Override
    public WaystoneTeleportContext setAppliesModifiers(boolean appliesModifiers) {
        this.appliesModifiers = appliesModifiers;
        return this;
    }

    @Override
    public Set<Identifier> getFlags() {
        return flags;
    }

    @Override
    public WaystoneTeleportContext addFlag(Identifier flag) {
        flags.add(flag);
        return this;
    }

    @Override
    public WaystoneTeleportContext removeFlag(Identifier flag) {
        flags.remove(flag);
        return this;
    }
}
