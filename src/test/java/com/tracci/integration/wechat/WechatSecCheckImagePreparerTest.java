package com.tracci.integration.wechat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatSecCheckImagePreparerTest {

    @Test
    void prepare_convertsSmallImageToWechatFriendlyJpeg() throws IOException {
        byte[] original = encodePng(createSolidImage(640, 480, Color.BLUE));

        WechatSecCheckImagePreparer.PreparedImage prepared = WechatSecCheckImagePreparer.prepare(original);

        assertEquals(MediaType.IMAGE_JPEG, prepared.mediaType());
        assertEquals("image.jpg", prepared.filename());
        assertTrue(prepared.bytes().length <= WechatSecCheckImagePreparer.MAX_BYTES);
        int[] dimensions = readDimensions(prepared.bytes());
        assertNotNull(dimensions);
        assertEquals(640, dimensions[0]);
        assertEquals(480, dimensions[1]);
    }

    @Test
    void prepare_compressesLargePngUnderWechatLimit() throws IOException {
        byte[] original = encodePng(createNoisyImage(2400, 3200));

        WechatSecCheckImagePreparer.PreparedImage prepared = WechatSecCheckImagePreparer.prepare(original);

        assertEquals(MediaType.IMAGE_JPEG, prepared.mediaType());
        assertTrue(prepared.bytes().length <= WechatSecCheckImagePreparer.MAX_BYTES);
        assertTrue(prepared.bytes().length < original.length);
        int[] dimensions = readDimensions(prepared.bytes());
        assertNotNull(dimensions);
        assertTrue(dimensions[0] <= WechatSecCheckImagePreparer.MAX_WIDTH);
        assertTrue(dimensions[1] <= WechatSecCheckImagePreparer.MAX_HEIGHT);
    }

    @Test
    void prepare_compressesLargeDimensionsEvenWhenBytesAreSmall() throws IOException {
        byte[] original = encodePng(createSolidImage(1800, 2400, Color.GREEN));

        WechatSecCheckImagePreparer.PreparedImage prepared = WechatSecCheckImagePreparer.prepare(original);

        int[] dimensions = readDimensions(prepared.bytes());
        assertNotNull(dimensions);
        assertTrue(dimensions[0] <= WechatSecCheckImagePreparer.MAX_WIDTH);
        assertTrue(dimensions[1] <= WechatSecCheckImagePreparer.MAX_HEIGHT);
        assertTrue(prepared.bytes().length <= WechatSecCheckImagePreparer.MAX_BYTES);
    }

    @Test
    void prepare_rejectsUnsupportedBytesBeforeCallingWechat() {
        byte[] original = new byte[]{0x01, 0x02, 0x03, 0x04};

        assertThrows(IllegalArgumentException.class, () -> WechatSecCheckImagePreparer.prepare(original));
    }

    private static BufferedImage createNoisyImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int seed = 17;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                seed = seed * 1103515245 + 12345;
                int rgb = (seed >>> 16) & 0xFFFFFF;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static BufferedImage createSolidImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static int[] readDimensions(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (image == null) {
            return null;
        }
        return new int[]{image.getWidth(), image.getHeight()};
    }
}
