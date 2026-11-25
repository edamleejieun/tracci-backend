package com.tracci.module.trip.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * 生成不依赖外部网络的系统默认旅程封面。
 */
final class DefaultDestinationCoverAsset {

    static final int WIDTH = 1600;
    static final int HEIGHT = 1000;

    private DefaultDestinationCoverAsset() {
    }

    static byte[] jpegBytes() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new GradientPaint(
                    0, 0, new Color(222, 237, 250),
                    WIDTH, HEIGHT, new Color(248, 240, 218)));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            graphics.setColor(new Color(255, 250, 236, 190));
            graphics.fill(new Ellipse2D.Double(1120, 120, 230, 230));

            Path2D farMountain = new Path2D.Double();
            farMountain.moveTo(0, 760);
            farMountain.curveTo(250, 570, 420, 610, 650, 720);
            farMountain.curveTo(880, 540, 1070, 550, 1320, 700);
            farMountain.curveTo(1450, 640, 1540, 650, WIDTH, 690);
            farMountain.lineTo(WIDTH, HEIGHT);
            farMountain.lineTo(0, HEIGHT);
            farMountain.closePath();
            graphics.setColor(new Color(91, 143, 173));
            graphics.fill(farMountain);

            Path2D nearMountain = new Path2D.Double();
            nearMountain.moveTo(0, 820);
            nearMountain.curveTo(280, 690, 520, 720, 760, 850);
            nearMountain.curveTo(1040, 650, 1280, 690, WIDTH, 810);
            nearMountain.lineTo(WIDTH, HEIGHT);
            nearMountain.lineTo(0, HEIGHT);
            nearMountain.closePath();
            graphics.setColor(new Color(49, 103, 129));
            graphics.fill(nearMountain);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpg", output)) {
                throw new IllegalStateException("JPEG writer unavailable");
            }
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate default destination cover", ex);
        } finally {
            graphics.dispose();
        }
    }
}
