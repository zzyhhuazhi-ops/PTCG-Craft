package dev.hua.ptcglive.client;

import net.minecraft.client.texture.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Shared decoder for the viewer, held cards and region thumbnails. */
public final class CardImageDecoder {
    private static final int MAX_ENCODED_BYTES = 32 * 1024 * 1024;
    private static final int MAX_SIDE = 8192;
    private static final long MAX_PIXELS = 32L * 1024 * 1024;
    private CardImageDecoder() { }

    /** Caller owns the stream and returned image. Never writes a disk cache or resizes the original. */
    public static NativeImage read(InputStream stream) throws IOException {
        // 1.21.1 NativeImage.read always validates PNG before invoking STB. Our
        // resource packs and compact fallback art contain actual JPEG data.
        byte[] encoded = stream.readNBytes(MAX_ENCODED_BYTES + 1);
        if (encoded.length > MAX_ENCODED_BYTES) throw new IOException("卡面文件超过 32 MiB 限制");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("无法识别卡面图片格式，需要 JPEG 或 PNG");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG"))
                    throw new IOException("不支持的卡面格式：" + format);
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS)
                    throw new IOException("卡面尺寸超过安全限制：" + width + "×" + height);
                var decoded = reader.read(0);
                NativeImage result = null;
                try {
                    result = new NativeImage(width, height, false);
                    int[] row = new int[width];
                    for (int y = 0; y < height; y++) {
                        decoded.getRGB(0, y, width, 1, row, 0, width);
                        for (int x = 0; x < width; x++) {
                            int argb = row[x];
                            // ImageIO ARGB -> NativeImage 1.21.1 ABGR, preserving alpha.
                            int abgr = (argb & 0xff00ff00) | ((argb >>> 16) & 255) | ((argb & 255) << 16);
                            result.setColor(x, y, abgr);
                        }
                    }
                    NativeImage complete = result; result = null;
                    return complete;
                } finally {
                    if (result != null) result.close();
                    decoded.flush();
                }
            } finally { reader.dispose(); }
        }
    }

    /** Returns a separately owned held texture; never upscales a compact fallback. */
    public static NativeImage forHeldCard(NativeImage original) {
        double scale = Math.min(1.0, 1080.0 / Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        var scaled = new NativeImage(width, height, false);
        try {
            original.resizeSubRectTo(0, 0, original.getWidth(), original.getHeight(), scaled);
            return scaled;
        } catch (RuntimeException | Error failure) { scaled.close(); throw failure; }
    }
}
