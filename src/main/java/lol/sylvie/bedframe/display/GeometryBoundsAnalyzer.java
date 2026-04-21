package lol.sylvie.bedframe.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lol.sylvie.bedframe.util.ResourceHelper;

import java.util.Objects;

public final class GeometryBoundsAnalyzer {
    public record Bounds(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        public double sizeX() { return maxX - minX; }
        public double sizeY() { return maxY - minY; }
        public double sizeZ() { return maxZ - minZ; }

        public boolean intersectsBaseBlock() {
            return maxX > -8 && minX < 8
                    && maxY > 0 && minY < 16
                    && maxZ > -8 && minZ < 8;
        }

        public boolean exceedsOversizedLimits() {
            return sizeX() > 30.0
                    || sizeY() > 30.0
                    || sizeZ() > 30.0
                    || minX < -30.0 || maxX > 30.0
                    || minY < -30.0 || maxY > 30.0
                    || minZ < -30.0 || maxZ > 30.0
                    || !intersectsBaseBlock();
        }
    }

    private GeometryBoundsAnalyzer() {
    }

    public static Bounds analyze(JsonObject geometryRoot) {
        JsonArray geometries = geometryRoot.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new IllegalArgumentException("No minecraft:geometry array");
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonArray bones = geometry.getAsJsonArray("bones");
        if (bones == null) {
            throw new IllegalArgumentException("No bones array");
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (JsonElement boneEl : bones) {
            JsonObject bone = boneEl.getAsJsonObject();
            JsonArray cubes = bone.getAsJsonArray("cubes");
            if (cubes == null) {
                continue;
            }

            for (JsonElement cubeEl : cubes) {
                JsonObject cube = cubeEl.getAsJsonObject();
                JsonArray origin = cube.getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonArray("size");
                if (origin == null || size == null) {
                    continue;
                }

                double ox = origin.get(0).getAsDouble();
                double oy = origin.get(1).getAsDouble();
                double oz = origin.get(2).getAsDouble();

                double sx = size.get(0).getAsDouble();
                double sy = size.get(1).getAsDouble();
                double sz = size.get(2).getAsDouble();

                double cubeMinX = Math.min(ox, ox + sx);
                double cubeMinY = Math.min(oy, oy + sy);
                double cubeMinZ = Math.min(oz, oz + sz);
                double cubeMaxX = Math.max(ox, ox + sx);
                double cubeMaxY = Math.max(oy, oy + sy);
                double cubeMaxZ = Math.max(oz, oz + sz);

                minX = Math.min(minX, cubeMinX);
                minY = Math.min(minY, cubeMinY);
                minZ = Math.min(minZ, cubeMinZ);
                maxX = Math.max(maxX, cubeMaxX);
                maxY = Math.max(maxY, cubeMaxY);
                maxZ = Math.max(maxZ, cubeMaxZ);
            }
        }

        if (!Double.isFinite(minX)) {
            throw new IllegalArgumentException("No cubes found");
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
