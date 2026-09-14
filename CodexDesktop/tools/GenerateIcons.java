import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the CodexDesktop application icon.
 *
 * <p>Run with: {@code java tools/GenerateIcons.java <resourcesDir> <packagingDir>}
 *
 * <p>The mark is a rounded square with a prompt chevron and caret bar — it stays legible at 16 px
 * in the taskbar, which ruled out anything with fine detail or text. Everything is drawn
 * programmatically so the icon can be regenerated and tweaked without binary assets in the repo.
 */
public final class GenerateIcons {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        Path iconsDir = Path.of(args.length > 0 ? args[0] : "src/main/resources/icons");
        Path packagingDir = Path.of(args.length > 1 ? args[1] : "packaging");
        Files.createDirectories(iconsDir);
        Files.createDirectories(packagingDir);

        List<BufferedImage> images = new ArrayList<>();
        for (int size : SIZES) {
            BufferedImage image = render(size);
            images.add(image);
            ImageIO.write(image, "png", iconsDir.resolve("app-" + size + ".png").toFile());
        }
        writeIco(packagingDir.resolve("codex-desktop.ico"), images);
        System.out.println("Wrote " + SIZES.length + " PNG icons to " + iconsDir.toAbsolutePath());
        System.out.println("Wrote ICO to " + packagingDir.resolve("codex-desktop.ico").toAbsolutePath());
    }

    private static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double inset = size * 0.045;
        double side = size - inset * 2;
        double radius = size * 0.235;

        // Body: a restrained two-stop blue that reads as flat at small sizes.
        RoundRectangle2D body = new RoundRectangle2D.Double(inset, inset, side, side, radius, radius);
        g.setPaint(new GradientPaint(0, (float) inset, new Color(0x4C74FF),
                0, (float) (inset + side), new Color(0x2C46C8)));
        g.fill(body);

        // Hairline highlight so the icon keeps an edge on both light and dark taskbars.
        if (size >= 32) {
            g.setPaint(new Color(255, 255, 255, 38));
            g.setStroke(new BasicStroke((float) Math.max(1.0, size * 0.012)));
            g.draw(new RoundRectangle2D.Double(inset + 0.5, inset + 0.5, side - 1, side - 1, radius, radius));
        }

        double centerX = size / 2.0;
        double centerY = size / 2.0;
        float stroke = (float) Math.max(1.5, size * 0.072);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(Color.WHITE);

        // Chevron: ">" shifted left of centre, with the caret bar to its right.
        double chevronWidth = size * 0.125;
        double chevronHeight = size * 0.16;
        double chevronX = centerX - size * 0.175;
        double chevronY = centerY - size * 0.035;
        GeneralPath chevron = new GeneralPath();
        chevron.moveTo(chevronX, chevronY - chevronHeight);
        chevron.lineTo(chevronX + chevronWidth, chevronY);
        chevron.lineTo(chevronX, chevronY + chevronHeight);
        g.draw(chevron);

        // Caret bar, suggesting a prompt waiting for input.
        double barY = chevronY + chevronHeight;
        double barStartX = centerX + size * 0.015;
        double barEndX = centerX + size * 0.185;
        g.draw(new java.awt.geom.Line2D.Double(barStartX, barY, barEndX, barY));

        g.dispose();
        return image;
    }

    /**
     * Writes a PNG-compressed ICO (supported by Windows Vista and later), which keeps the file
     * small while carrying every size Windows asks for.
     */
    private static void writeIco(Path target, List<BufferedImage> images) throws IOException {
        List<byte[]> encoded = new ArrayList<>();
        for (BufferedImage image : images) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(image, "png", buffer);
            encoded.add(buffer.toByteArray());
        }
        try (OutputStream out = Files.newOutputStream(target);
             DataOutputStream data = new DataOutputStream(out)) {
            writeShortLE(data, 0);                 // reserved
            writeShortLE(data, 1);                 // type: icon
            writeShortLE(data, encoded.size());    // image count

            int offset = 6 + 16 * encoded.size();
            for (int i = 0; i < encoded.size(); i++) {
                BufferedImage image = images.get(i);
                data.writeByte(image.getWidth() >= 256 ? 0 : image.getWidth());
                data.writeByte(image.getHeight() >= 256 ? 0 : image.getHeight());
                data.writeByte(0);                 // palette size
                data.writeByte(0);                 // reserved
                writeShortLE(data, 1);             // colour planes
                writeShortLE(data, 32);            // bits per pixel
                writeIntLE(data, encoded.get(i).length);
                writeIntLE(data, offset);
                offset += encoded.get(i).length;
            }
            for (byte[] png : encoded) {
                data.write(png);
            }
        }
    }

    private static void writeShortLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
    }

    private static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }
}
