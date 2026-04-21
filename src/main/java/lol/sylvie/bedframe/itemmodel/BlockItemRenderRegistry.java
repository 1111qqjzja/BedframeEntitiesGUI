package lol.sylvie.bedframe.itemmodel;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockItemRenderRegistry {
    private static final Map<Identifier, BlockItemRenderSpec> BY_ITEM = new LinkedHashMap<>();

    private BlockItemRenderRegistry() {
    }

    public static void clear() {
        BY_ITEM.clear();
    }

    public static void register(BlockItemRenderSpec spec) {
        BY_ITEM.put(spec.itemId(), spec);
    }

    public static BlockItemRenderSpec get(Identifier itemId) {
        return BY_ITEM.get(itemId);
    }

    public static boolean contains(Identifier itemId) {
        return BY_ITEM.containsKey(itemId);
    }

    public static Collection<BlockItemRenderSpec> all() {
        return List.copyOf(BY_ITEM.values());
    }
}
