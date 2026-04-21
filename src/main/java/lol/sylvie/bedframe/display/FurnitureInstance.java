package lol.sylvie.bedframe.display;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FurnitureInstance {
    private final BlockPos anchorPos;
    private final Identifier blockId;
    private final OversizedDisplaySpec spec;
    private final List<UUID> javaCarrierEntityUuids = new ArrayList<>();

    public FurnitureInstance(BlockPos anchorPos, Identifier blockId, OversizedDisplaySpec spec) {
        this.anchorPos = anchorPos.toImmutable();
        this.blockId = blockId;
        this.spec = spec;
    }

    public BlockPos anchorPos() {
        return anchorPos;
    }

    public Identifier blockId() {
        return blockId;
    }

    public OversizedDisplaySpec spec() {
        return spec;
    }

    public List<UUID> javaCarrierEntityUuids() {
        return javaCarrierEntityUuids;
    }

    public void addCarrier(Entity entity) {
        this.javaCarrierEntityUuids.add(entity.getUuid());
    }
}
