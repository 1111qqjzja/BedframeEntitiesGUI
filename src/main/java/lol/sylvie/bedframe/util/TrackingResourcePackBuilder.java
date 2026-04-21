package lol.sylvie.bedframe.util;

import eu.pb4.polymer.resourcepack.api.PackResource;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class TrackingResourcePackBuilder implements ResourcePackBuilder {
    private final ResourcePackBuilder delegate;

    public TrackingResourcePackBuilder(ResourcePackBuilder delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        PathRegistry.onBuilderWrapped(delegate);
    }

    public ResourcePackBuilder delegate() {
        return delegate;
    }

    @Override
    public boolean addData(String path, byte[] data) {
        PathRegistry.recordPath(path);
        return delegate.addData(path, data);
    }

    @Override
    public boolean addData(String path, PackResource packResource) {
        PathRegistry.recordPath(path);
        return delegate.addData(path, packResource);
    }

    @Override
    public boolean copyAssets(String path) {
        return delegate.copyAssets(path);
    }

    @Override
    public boolean copyFromPath(Path path, String target, boolean override) {
        PathRegistry.recordPath(target);
        return delegate.copyFromPath(path, target, override);
    }

    @Override
    public byte[] getData(String path) {
        return delegate.getData(path);
    }

    @Override
    public @Nullable PackResource getResource(String path) {
        return delegate.getResource(path);
    }

    @Override
    public byte @Nullable [] getDataOrSource(String path) {
        return delegate.getDataOrSource(path);
    }

    @Override
    public void forEachResource(BiConsumer<String, PackResource> consumer) {
        delegate.forEachResource((path, resource) -> {
            PathRegistry.recordPath(path);
            consumer.accept(path, resource);
        });
    }

    @Override
    public boolean addAssetsSource(String path) {
        return delegate.addAssetsSource(path);
    }

    @Override
    public void addResourceConverter(ResourceConverter resourceConverter) {
        delegate.addResourceConverter(resourceConverter);
    }

    @Override
    public void addPreFinishTask(Consumer<ResourcePackBuilder> consumer) {
        delegate.addPreFinishTask(consumer);
    }
}
