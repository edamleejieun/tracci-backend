package com.tracci.integration.wechat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 将图片压缩到微信 {@code img_sec_check} 接口可接受的范围内。
 * 微信限制：约 1MB、像素不超过 750×1334，格式 JPG/PNG/GIF。
 */
@Slf4j
final class WechatSecCheckImagePreparer {

    static final int MAX_WIDTH = 750;
    static final int MAX_HEIGHT = 1334;
    /** 留少量余量，避免 multipart 边界导致超限。 */
    static final int MAX_BYTES = 980_000;
    private static final float INITIAL_JPEG_QUALITY = 0.85f;
    private static final float MIN_JPEG_QUALITY = 0.45f;
    private static final float JPEG_QUALITY_STEP = 0.1f;

    private WechatSecCheckImagePreparer() {
    }

    static PreparedImage prepare(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new PreparedImage(imageBytes, MediaType.IMAGE_JPEG, "image.jpg");
        }

        BufferedImage source = readImage(imageBytes);
        if (source == null) {
            log.warn("Wechat imgSecCheck preparer failed: unsupported image format bytes={}", imageBytes.length);
            throw new IllegalArgumentException("Unsupported image format for Wechat imgSecCheck");
        }

        BufferedImage scaled = scaleDown(source);
        try {
            byte[] prepared = compressToJpegUnderLimit(scaled);
            log.debug("Wechat imgSecCheck image prepared originalBytes={} preparedBytes={} width={} height={}",
                    imageBytes.length, prepared.length, scaled.getWidth(), scaled.getHeight());
            return new PreparedImage(prepared, MediaType.IMAGE_JPEG, "image.jpg");
        } catch (IOException ex) {
            log.warn("Wechat imgSecCheck image prepare failed message={}", ex.getMessage());
            throw new IllegalArgumentException("Failed to prepare image for Wechat imgSecCheck", ex);
        }
    }

    private static BufferedImage scaleDown(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return toRgb(source);
        }
        double scale = Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static byte[] compressToJpegUnderLimit(BufferedImage image) throws IOException {
        float quality = INITIAL_JPEG_QUALITY;
        byte[] compressed = encodeJpeg(image, quality);
        while (compressed.length > MAX_BYTES && quality > MIN_JPEG_QUALITY) {
            quality -= JPEG_QUALITY_STEP;
            compressed = encodeJpeg(image, quality);
        }
        return compressed;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter available");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static BufferedImage readImage(byte[] imageBytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException ex) {
            return null;
        }
    }

    record PreparedImage(byte[] bytes, MediaType mediaType, String filename) {
    }
}
