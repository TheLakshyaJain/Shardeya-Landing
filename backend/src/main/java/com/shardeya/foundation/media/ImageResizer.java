package com.shardeya.foundation.media;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Plain {@code Graphics2D} scaling rather than a third-party library
 * (thumbnailator etc.) — one method covers everything this milestone needs
 * (thumb/card/full derivatives), not worth a new dependency for. Re-encoding
 * through {@code BufferedImage}/{@code ImageIO} also naturally strips EXIF
 * (including GPS) since the fresh JPEG never carries the original's metadata
 * segments — satisfies M-05's "EXIF GPS stripped" rule as a side effect,
 * without a dedicated EXIF-scrubbing step.
 */
public final class ImageResizer {

    private ImageResizer() {
    }

    public static byte[] resizeToWidth(byte[] original, int targetWidth) throws IOException {
        BufferedImage source = ImageIO.read(new java.io.ByteArrayInputStream(original));
        if (source == null) {
            throw new IOException("Not a decodable raster image");
        }
        if (source.getWidth() <= targetWidth) {
            return reencodeJpeg(source);
        }
        int targetHeight = Math.round(targetWidth * (source.getHeight() / (float) source.getWidth()));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return reencodeJpeg(resized);
    }

    public static int[] dimensions(byte[] original) throws IOException {
        BufferedImage source = ImageIO.read(new java.io.ByteArrayInputStream(original));
        if (source == null) {
            throw new IOException("Not a decodable raster image");
        }
        return new int[] { source.getWidth(), source.getHeight() };
    }

    private static byte[] reencodeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
