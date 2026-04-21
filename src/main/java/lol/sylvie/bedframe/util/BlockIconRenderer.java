package lol.sylvie.bedframe.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BlockIconRenderer {
    private BlockIconRenderer() {
    }

    public record BlockFaces(
            BufferedImage top,
            BufferedImage left,
            BufferedImage right
    ) {
    }

    public static BufferedImage readPng(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            return null;
        }
        return ensureSquare(image);
    }

    public static BufferedImage ensureSquare(BufferedImage src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        if (src.getWidth() == size && src.getHeight() == size) {
            return src;
        }
        try {
            return src.getSubimage(0, 0, size, size);
        } catch (RasterFormatException e) {
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(src, 0, 0, size, size, null);
            g.dispose();
            return out;
        }
    }

    public static BufferedImage resizeNearest(BufferedImage src, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    public static BufferedImage tint(BufferedImage src, float brightness) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }

                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;

                r = clamp(Math.round(r * brightness));
                g = clamp(Math.round(g * brightness));
                b = clamp(Math.round(b * brightness));

                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static BufferedImage renderPseudo3D(BlockFaces faces) {
        BufferedImage topSrc = ensureSquare(faces.top());
        BufferedImage leftSrc = ensureSquare(faces.left());
        BufferedImage rightSrc = ensureSquare(faces.right());

        BufferedImage top = resizeNearest(topSrc, 16, 16);
        BufferedImage left = resizeNearest(tint(leftSrc, 0.78f), 16, 16);
        BufferedImage right = resizeNearest(tint(rightSrc, 0.9f), 16, 16);

        BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

        blitTopFace(out, top, 8, 0);

        blitLeftFace(out, left, 0, 8);

        blitRightFace(out, right, 16, 8);

        return out;
    }

    private static void blitTopFace(BufferedImage canvas, BufferedImage face, int offsetX, int offsetY) {
        int w = face.getWidth();
        int h = face.getHeight();

        for (int y = 0; y < h; y++) {
            int rowWidth = Math.max(1, w - y);
            int rowOffset = y / 2;

            for (int x = 0; x < rowWidth; x++) {
                int srcX = Math.min(w - 1, x);
                int srcY = y;
                int argb = face.getRGB(srcX, srcY);
                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }

                int dstX = offsetX + rowOffset + x;
                int dstY = offsetY + y / 2;
                safeSet(canvas, dstX, dstY, argb);
                safeSet(canvas, dstX, dstY + 1, argb);
            }
        }
    }

    private static void blitLeftFace(BufferedImage canvas, BufferedImage face, int offsetX, int offsetY) {
        int w = face.getWidth();
        int h = face.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w / 2; x++) {
                int srcX = Math.min(w - 1, x * 2);
                int srcY = y;
                int argb = face.getRGB(srcX, srcY);
                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }

                int dstX = offsetX + x;
                int dstY = offsetY + y + (x / 2);
                safeSet(canvas, dstX, dstY, argb);
                safeSet(canvas, dstX + 1, dstY, argb);
            }
        }
    }

    private static void blitRightFace(BufferedImage canvas, BufferedImage face, int offsetX, int offsetY) {
        int w = face.getWidth();
        int h = face.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w / 2; x++) {
                int srcX = Math.min(w - 1, x * 2);
                int srcY = y;
                int argb = face.getRGB(srcX, srcY);
                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }

                int dstX = offsetX + x;
                int dstY = offsetY + y + ((w / 2 - x) / 2);
                safeSet(canvas, dstX, dstY, argb);
                safeSet(canvas, dstX + 1, dstY, argb);
            }
        }
    }

    private static void safeSet(BufferedImage image, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        image.setRGB(x, y, argb);
    }

    public static void writePng(BufferedImage image, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "PNG", output.toFile());
    }
}
