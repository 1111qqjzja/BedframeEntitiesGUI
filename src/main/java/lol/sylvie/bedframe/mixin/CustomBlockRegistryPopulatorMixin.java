package lol.sylvie.bedframe.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.registry.populator.CustomBlockRegistryPopulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

@Mixin(value = CustomBlockRegistryPopulator.class, remap = false)
public class CustomBlockRegistryPopulatorMixin {
	@ModifyVariable(method = "convertComponents", at = @At(value = "STORE", ordinal = 0))
	private static NbtMapBuilder addParticleComponent(NbtMapBuilder value, @Local(argsOnly = true) CustomBlockComponents components) {
		Map<String, MaterialInstance> materials = components.materialInstances();
		if (!materials.containsKey("*")) return value;

		value.putCompound("minecraft:destruction_particles", NbtMap.builder()
						.putString("texture", materials.get("*").texture())
						.build());

		return value;
	}
}
