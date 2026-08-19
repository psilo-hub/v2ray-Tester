package free.svoss.tools.v2ray;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class QrHelper {

    private static final int QR_PNG_SIZE = 640;

    private QrHelper() {
    }

    static BitMatrix buildQrMatrix(String content, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
    }

    static String renderQrAscii(String content) {
        try {
            BitMatrix matrix = buildQrMatrix(content, 1);

            StringBuilder sb = new StringBuilder();

            for (int y = 0; y < matrix.getHeight(); y += 2) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    boolean top = matrix.get(x, y);
                    boolean bottom = y + 1 < matrix.getHeight() && matrix.get(x, y + 1);

                    if (top && bottom) sb.append('\u2588');
                    else if (top) sb.append('\u2580');
                    else if (bottom) sb.append('\u2584');
                    else sb.append(' ');
                }
                sb.append('\n');
            }

            return sb.toString();
        } catch (WriterException e) {
            return "QR error: " + e.getMessage();
        }
    }

    static void saveQrPng(String content, File file) {
        try {
            BitMatrix matrix = buildQrMatrix(content, QR_PNG_SIZE);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ImageIO.write(image, "png", file);
        } catch (WriterException | IOException e) {
            System.err.println("Failed to save QR PNG " + file + ": " + e.getMessage());
        }
    }
}
