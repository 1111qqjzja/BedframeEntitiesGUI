package lol.sylvie.bedframe.screen.waystones;

import com.mojang.datafixers.util.Either;
import lol.sylvie.bedframe.screen.api.FormActionExecutor;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

public final class WaystonesActionExecutor implements FormActionExecutor {

    @Override
    public boolean supports(String sourceId) {
        return sourceId.startsWith("waystones:");
    }

    @Override
    public boolean handleOption(ServerPlayerEntity player, String sourceId, String optionId) {
        if ("builtin:close".equals(optionId)) {
            WaystonesBedrockBridge.clearAll(player);
            return true;
        }

        if ("open_settings".equals(optionId)) {
            boolean opened = WaystonesBedrockBridge.tryOpenWaystoneSettingsFromSelection(player);
            System.out.println("[Bedframe] open_settings -> " + opened);
            if (!opened) {
                player.sendMessage(Text.literal("§c无法打开设置菜单"), false);
            }
            return true;
        }

        if ("action:back_to_selection".equals(optionId)) {
            boolean reopened = WaystonesBedrockBridge.tryReopenWaystoneSelection(player);
            System.out.println("[Bedframe] back_to_selection -> " + reopened);
            if (!reopened) {
                player.sendMessage(Text.literal("§c无法返回传送菜单"), false);
            }
            return true;
        }

        if (!optionId.startsWith("select:")) {
            return false;
        }

        Object selectionContext = WaystonesBedrockBridge.getSelectionContext(player);
        if (!(selectionContext instanceof WaystonesFamilyPayloads.SelectionPayload payload)) {
            player.sendMessage(Text.literal("§c选择上下文不存在"), false);
            return true;
        }

        int index;
        try {
            index = Integer.parseInt(optionId.substring("select:".length()));
        } catch (NumberFormatException e) {
            player.sendMessage(Text.literal("§c无效目标"), false);
            return true;
        }

        List<?> targets = payload.targets();
        if (index < 0 || index >= targets.size()) {
            player.sendMessage(Text.literal("§c目标不存在"), false);
            return true;
        }

        Object target = targets.get(index);
        if (!(target instanceof Waystone targetWaystone)) {
            player.sendMessage(Text.literal("§c目标不是合法的 Waystone"), false);
            return true;
        }

        Waystone fromWaystone = payload.fromWaystone() instanceof Waystone waystone ? waystone : null;
        ItemStack warpItem = extractWarpItem(payload.sourceObject());
        Hand hand = payload.hand() != null ? payload.hand() : WaystonesBedrockBridge.getLastHand(player);

        System.out.println("[Bedframe] select target index=" + index + ", type=" + payload.type());
        System.out.println("[Bedframe] - fromWaystone=" + fromWaystone);
        System.out.println("[Bedframe] - targetWaystone=" + targetWaystone.getName());
        System.out.println("[Bedframe] - warpItem=" + warpItem);
        System.out.println("[Bedframe] - hand=" + hand);

        WaystoneTeleportContext context = new BedframeWaystoneTeleportContext(player, targetWaystone)
                .setFromWaystone(fromWaystone)
                .setWarpItem(warpItem)
                .setWarpHand(hand);

        Either<?, WaystoneTeleportError> result = WaystoneTeleportManager.tryTeleport(context);
        boolean success = result.left().isPresent();

        System.out.println("[Bedframe] tryWaystonesTeleport -> " + success);
        if (!success) {
            WaystoneTeleportError error = result.right().orElse(null);
            System.out.println("[Bedframe] teleport error = " + error);
            player.sendMessage(Text.literal("§c传送执行失败" + (error != null ? ": " + error.getClass().getSimpleName() : "")), false);
        }

        return true;
    }

    @Override
    public boolean handleCustom(ServerPlayerEntity player, String sourceId, Map<String, Object> values) {
        if (!"waystones:settings".equals(sourceId)) {
            return false;
        }

        Object settingsContext = WaystonesBedrockBridge.getSettingsContext(player);
        Object waystone = WaystonesSettingsInterceptor.extractWaystone(settingsContext);

        if (waystone == null) {
            player.sendMessage(Text.literal("§c无法读取 Waystone 设置对象"), false);
            return true;
        }

        String newName = asString(values.get("name")).trim();
        String selectedLabel = asString(values.get("visibility")).trim();
        boolean back = values.get("return_to_selection") instanceof Boolean b && b;

        List<String> rawVisibilityOptions = WaystonesSettingsInterceptor.extractRawVisibilityOptions(settingsContext, waystone);
        String rawVisibility = WaystonesSettingsInterceptor.displayLabelToRaw(selectedLabel, rawVisibilityOptions);

        System.out.println("[Bedframe] settings submit:");
        System.out.println("[Bedframe] - name=" + newName);
        System.out.println("[Bedframe] - selectedLabel=" + selectedLabel);
        System.out.println("[Bedframe] - rawVisibility=" + rawVisibility);
        System.out.println("[Bedframe] - back=" + back);

        boolean changed = false;

        if (!newName.isBlank()) {
            changed |= trySetWaystoneName(waystone, newName);
        }

        if (!rawVisibility.isBlank()) {
            changed |= trySetWaystoneVisibility(waystone, rawVisibility);
        }

        tryMarkDirty(waystone);
        trySync(waystone);

        player.sendMessage(Text.literal(changed ? "§aWaystone 设置已提交" : "§e没有检测到可提交的设置变化"), false);

        if (back) {
            boolean reopened = WaystonesBedrockBridge.tryReopenWaystoneSelection(player);
            System.out.println("[Bedframe] reopen selection after settings -> " + reopened);
            if (!reopened) {
                player.sendMessage(Text.literal("§c无法返回传送菜单"), false);
            }
        }

        return true;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static ItemStack extractWarpItem(Object sourceObject) {
        if (sourceObject instanceof ItemStack stack) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean trySetWaystoneName(Object waystone, String newName) {
        Text text = Text.literal(newName);

        try {
            for (Method method : waystone.getClass().getMethods()) {
                if (!method.getName().equals("setName")) continue;
                if (method.getParameterCount() != 1) continue;

                Class<?> type = method.getParameterTypes()[0];
                method.setAccessible(true);

                if (type.isAssignableFrom(Text.class)) {
                    method.invoke(waystone, text);
                    return true;
                }

                if (type == String.class) {
                    method.invoke(waystone, newName);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            var field = waystone.getClass().getDeclaredField("name");
            field.setAccessible(true);
            if (field.getType().isAssignableFrom(Text.class)) {
                field.set(waystone, text);
            } else if (field.getType() == String.class) {
                field.set(waystone, newName);
            } else {
                field.set(waystone, text);
            }
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean trySetWaystoneVisibility(Object waystone, String rawVisibility) {
        try {
            Method getter = null;
            for (Method method : waystone.getClass().getMethods()) {
                if (method.getName().equals("getVisibility") && method.getParameterCount() == 0) {
                    getter = method;
                    break;
                }
            }

            Class<?> visibilityType = getter != null ? getter.getReturnType() : null;
            if (visibilityType == null || visibilityType == Object.class) {
                try {
                    var field = waystone.getClass().getDeclaredField("visibility");
                    field.setAccessible(true);
                    Object current = field.get(waystone);
                    if (current != null) {
                        visibilityType = current.getClass();
                    }
                } catch (Throwable ignored) {
                }
            }

            if (visibilityType != null && visibilityType.isEnum()) {
                Object enumValue = Enum.valueOf((Class<? extends Enum>) visibilityType, rawVisibility);

                for (Method method : waystone.getClass().getMethods()) {
                    if (!method.getName().equals("setVisibility")) continue;
                    if (method.getParameterCount() != 1) continue;
                    if (!method.getParameterTypes()[0].isAssignableFrom(visibilityType)) continue;

                    method.setAccessible(true);
                    method.invoke(waystone, enumValue);
                    return true;
                }

                try {
                    var field = waystone.getClass().getDeclaredField("visibility");
                    field.setAccessible(true);
                    field.set(waystone, enumValue);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return false;
    }

    private static void tryMarkDirty(Object obj) {
        if (obj == null) return;

        try {
            Method m = obj.getClass().getMethod("markDirty");
            m.setAccessible(true);
            m.invoke(obj);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method m = obj.getClass().getMethod("setChanged");
            m.setAccessible(true);
            m.invoke(obj);
        } catch (Throwable ignored) {
        }
    }

    private static void trySync(Object obj) {
        if (obj == null) return;

        try {
            Method m = obj.getClass().getMethod("sync");
            m.setAccessible(true);
            m.invoke(obj);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method m = obj.getClass().getMethod("sendUpdate");
            m.setAccessible(true);
            m.invoke(obj);
        } catch (Throwable ignored) {
        }
    }

    private static final class BedframeWaystoneTeleportContext implements WaystoneTeleportContext {
        private final Entity entity;
        private final Waystone targetWaystone;
        private final List<MobEntity> leashedEntities = new ArrayList<>();
        private final List<Entity> additionalEntities = new ArrayList<>();
        private @Nullable Waystone fromWaystone;
        private ItemStack warpItem = ItemStack.EMPTY;
        private Hand warpHand = Hand.MAIN_HAND;
        private WarpRequirement requirements = EmptyWarpRequirement.INSTANCE;
        private boolean playsSound = true;
        private boolean playsEffect = true;
        private boolean appliesModifiers = true;
        private final Set<Identifier> flags = new HashSet<>();

        private BedframeWaystoneTeleportContext(Entity entity, Waystone targetWaystone) {
            this.entity = entity;
            this.targetWaystone = targetWaystone;
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
            this.additionalEntities.add(additionalEntity);
            return this;
        }

        @Override
        public Optional<Waystone> getFromWaystone() {
            return Optional.ofNullable(fromWaystone);
        }

        @Override
        public WaystoneTeleportContext setFromWaystone(@Nullable Waystone fromWaystone) {
            this.fromWaystone = fromWaystone;
            return this;
        }

        @Override
        public ItemStack getWarpItem() {
            return warpItem;
        }

        @Override
        public WaystoneTeleportContext setWarpItem(ItemStack warpItem) {
            this.warpItem = warpItem == null ? ItemStack.EMPTY : warpItem;
            return this;
        }

        @Override
        public Hand getWarpHand() {
            return warpHand;
        }

        @Override
        public WaystoneTeleportContext setWarpHand(Hand warpHand) {
            this.warpHand = warpHand == null ? Hand.MAIN_HAND : warpHand;
            return this;
        }

        @Override
        public boolean isDimensionalTeleport() {
            return !entity.getEntityWorld().getRegistryKey().getValue().equals(targetWaystone.getDimension());
        }

        @Override
        public WarpRequirement getRequirements() {
            return requirements;
        }

        @Override
        public WaystoneTeleportContext setRequirements(WarpRequirement warpRequirement) {
            this.requirements = warpRequirement == null ? EmptyWarpRequirement.INSTANCE : warpRequirement;
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
            this.flags.add(flag);
            return this;
        }

        @Override
        public WaystoneTeleportContext removeFlag(Identifier flag) {
            this.flags.remove(flag);
            return this;
        }
    }

    private enum EmptyWarpRequirement implements WarpRequirement {
        INSTANCE;

        @Override
        public boolean canAfford(net.minecraft.entity.player.PlayerEntity player) {
            return true;
        }

        @Override
        public void appendHoverText(net.minecraft.entity.player.PlayerEntity player, List<Text> tooltip) {
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }
}
