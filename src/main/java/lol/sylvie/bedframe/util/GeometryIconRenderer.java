package lol.sylvie.bedframe.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;
import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public final class GeometryIconRenderer {
    private GeometryIconRenderer() {
    }

    public record RenderTextureSet(
            BufferedImage atlas,
            int textureWidth,
            int textureHeight
    ) {
    }

    public record GeometryCube(
            float ox, float oy, float oz,
            float sx, float sy, float sz,
            int uvx, int uvy
    ) {
    }

    private record FaceQuad(
            double depth,
            Polygon poly,
            BufferedImage texture
    ) {
    }

    public static BufferedImage renderFromGeometry(
            JsonObject geometryRoot,
            RenderTextureSet textures,
            int outSize
    ) {
        List<GeometryCube> cubes = extractCubes(geometryRoot);
        if (cubes.isEmpty()) {
            return null;
        }

        List<FaceQuad> faces = new ArrayList<>();

        for (GeometryCube cube : cubes) {
            faces.addAll(buildCubeFaces(cube, textures));
        }

        if (faces.isEmpty()) {
            return null;
        }

        faces.sort(Comparator.comparingDouble(FaceQuad::depth));

        BufferedImage canvas = new BufferedImage(outSize, outSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        for (FaceQuad face : faces) {
            if (face.texture() == null || face.poly() == null) continue;
            paintTexturedQuad(g, face.texture(), face.poly());
        }

        g.dispose();
        return cropAndScale(canvas, outSize);
    }

    public static JsonObject readGeometry(Path geometryFile) throws IOException {
        return GSON.fromJson(Files.readString(geometryFile), JsonObject.class);
    }

    public static RenderTextureSet readTextureSet(InputStream atlasStream) throws IOException {
        BufferedImage atlas = ImageIO.read(atlasStream);
        if (atlas == null) {
            return null;
        }
        return new RenderTextureSet(atlas, atlas.getWidth(), atlas.getHeight());
    }

    public static void writePng(BufferedImage image, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static List<GeometryCube> extractCubes(JsonObject geometryRoot) {
        List<GeometryCube> out = new ArrayList<>();

        JsonArray geometries = geometryRoot.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            return out;
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonArray bones = geometry.getAsJsonArray("bones");
        if (bones == null) {
            return out;
        }

        for (JsonElement boneEl : bones) {
            JsonObject bone = boneEl.getAsJsonObject();
            JsonArray cubes = bone.getAsJsonArray("cubes");
            if (cubes == null) continue;

            for (JsonElement cubeEl : cubes) {
                JsonObject cube = cubeEl.getAsJsonObject();

                JsonArray origin = cube.getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonArray("size");
                JsonArray uv = cube.getAsJsonArray("uv");

                if (origin == null || size == null || uv == null) {
                    continue;
                }

                out.add(new GeometryCube(
                        origin.get(0).getAsFloat(),
                        origin.get(1).getAsFloat(),
                        origin.get(2).getAsFloat(),
                        size.get(0).getAsFloat(),
                        size.get(1).getAsFloat(),
                        size.get(2).getAsFloat(),
                        uv.get(0).getAsInt(),
                        uv.get(1).getAsInt()
                ));
            }
        }

        return out;
    }

    private static List<FaceQuad> buildCubeFaces(GeometryCube cube, RenderTextureSet textures) {
        List<FaceQuad> out = new ArrayList<>();

        Vec3 p000 = project(cube.ox(), cube.oy(), cube.oz());
        Vec3 p100 = project(cube.ox() + cube.sx(), cube.oy(), cube.oz());
        Vec3 p010 = project(cube.ox(), cube.oy() + cube.sy(), cube.oz());
        Vec3 p110 = project(cube.ox() + cube.sx(), cube.oy() + cube.sy(), cube.oz());

        Vec3 p001 = project(cube.ox(), cube.oy(), cube.oz() + cube.sz());
        Vec3 p101 = project(cube.ox() + cube.sx(), cube.oy(), cube.oz() + cube.sz());
        Vec3 p011 = project(cube.ox(), cube.oy() + cube.sy(), cube.oz() + cube.sz());
        Vec3 p111 = project(cube.ox() + cube.sx(), cube.oy() + cube.sy(), cube.oz() + cube.sz());

        out.add(makeFace(
                new Vec3[]{p010, p110, p111, p011},
                avgDepth(p010, p110, p111, p011),
                cropFaceTexture(textures, cube.uvx(), cube.uvy(), Math.max(1, Math.round(cube.sx())), Math.max(1, Math.round(cube.sz())))
        ));

        out.add(makeFace(
                new Vec3[]{p001, p011, p010, p000},
                avgDepth(p001, p011, p010, p000),
                tint(cropFaceTexture(textures, cube.uvx(), cube.uvy(), Math.max(1, Math.round(cube.sz())), Math.max(1, Math.round(cube.sy()))), 0.82f)
        ));

        out.add(makeFace(
                new Vec3[]{p100, p110, p111, p101},
                avgDepth(p100, p110, p111, p101),
                tint(cropFaceTexture(textures, cube.uvx(), cube.uvy(), Math.max(1, Math.round(cube.sz())), Math.max(1, Math.round(cube.sy()))), 0.92f)
        ));

        return out;
    }

    private static FaceQuad makeFace(Vec3[] points, double depth, BufferedImage texture) {
        Polygon poly = new Polygon();
        for (Vec3 p : points) {
            poly.addPoint((int) Math.round(p.x + 64), (int) Math.round(96 - p.y));
        }
        return new FaceQuad(depth, poly, texture);
    }

    private static BufferedImage cropFaceTexture(RenderTextureSet set, int u, int v, int w, int h) {
        int maxW = Math.max(1, Math.min(w, set.atlas().getWidth() - u));
        int maxH = Math.max(1, Math.min(h, set.atlas().getHeight() - v));
        if (u < 0 || v < 0 || u >= set.atlas().getWidth() || v >= set.atlas().getHeight()) {
            return null;
        }
        return set.atlas().getSubimage(u, v, maxW, maxH);
    }

    private static BufferedImage tint(BufferedImage src, float mul) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                r = clamp(Math.round(r * mul));
                g = clamp(Math.round(g * mul));
                b = clamp(Math.round(b * mul));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void paintTexturedQuad(Graphics2D g, BufferedImage texture, Polygon poly) {
        Rectangle bounds = poly.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }

        Shape oldClip = g.getClip();
        g.setClip(poly);
        g.drawImage(texture, bounds.x, bounds.y, bounds.width, bounds.height, null);
        g.setClip(oldClip);
    }

    private static BufferedImage cropAndScale(BufferedImage src, int outSize) {
        int minX = src.getWidth(), minY = src.getHeight();
        int maxX = -1, maxY = -1;

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int a = (src.getRGB(x, y) >>> 24) & 0xFF;
                if (a > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return src;
        }

        BufferedImage cropped = src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        BufferedImage out = new BufferedImage(outSize, outSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int pad = 2;
        int targetW = outSize - pad * 2;
        int targetH = outSize - pad * 2;

        double sx = targetW / (double) cropped.getWidth();
        double sy = targetH / (double) cropped.getHeight();
        double scale = Math.min(sx, sy);

        int drawW = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.round(cropped.getHeight() * scale));

        int dx = (outSize - drawW) / 2;
        int dy = (outSize - drawH) / 2;

        g.drawImage(cropped, dx, dy, drawW, drawH, null);
        g.dispose();
        return out;
    }

    private static double avgDepth(Vec3... points) {
        double sum = 0;
        for (Vec3 p : points) {
            sum += p.z;
        }
        return sum / points.length;
    }

    private static Vec3 project(double x, double y, double z) {
        double px = (x - z) * 1.2;
        double py = y + (x + z) * 0.5;
        double depth = x + z + y * 0.01;
        return new Vec3(px, py, depth);
    }

    private record Vec3(double x, double y, double z) {
    }
}
