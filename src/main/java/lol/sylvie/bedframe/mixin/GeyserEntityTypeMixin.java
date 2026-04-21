package lol.sylvie.bedframe.mixin;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.entity.BedrockEntityDefinition;
import org.geysermc.geyser.entity.GeyserEntityType;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.SimpleMappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = GeyserEntityType.class, remap = false)
public abstract class GeyserEntityTypeMixin {

    @Shadow private Identifier identifier;

    /**
     * @author you
     * @reason Use custom identifier lookup instead of JAVA_ENTITY_TYPES keyed lookup
     */
    @Overwrite
    public @Nullable GeyserEntityDefinition defaultBedrockDefinition() {
        SimpleMappedRegistry<Identifier, BedrockEntityDefinition> registry = Registries.BEDROCK_ENTITY_DEFINITIONS;
        if (registry == null) {
            throw new IllegalStateException("No entity definition registered for " + String.valueOf(this));
        }
        return GeyserEntityDefinition.of(this.identifier);
    }
}
