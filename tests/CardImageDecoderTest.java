import dev.hua.ptcglive.client.CardImageDecoder;
import net.minecraft.client.texture.NativeImage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipFile;

/** Runs the real Minecraft NativeImage code without creating a window or a GL context. */
public final class CardImageDecoderTest {
    private static int decoded;
    public static void main(String[] args) throws Exception {
        ImageIO.setUseCache(false);
        Path originals = Path.of("src/generated/resources/assets/ptcglive_mc/card_art");
        Path thumbs = Path.of("src/generated/resources/assets/ptcglive_mc/card_thumb");
        Path reportedCard = originals.resolve("rsv10-5_en_024.jpg");
        try (var stream = Files.newInputStream(reportedCard)) {
            try (var unexpected = NativeImage.read(stream)) {
                throw new AssertionError("Minecraft 1.21.1 regression fixture should reject JPEG as PNG");
            } catch (IOException expected) {
                check(expected.getMessage().contains("PNG"), "Reproduce the user's Bad PNG Signature error");
            }
        }
        System.out.println("Reproduced Minecraft 1.21.1 Bad PNG Signature with reported JPEG card");
        for (String id : new String[]{"rsv10-5_en_024", "swshbsp_en_307"}) {
            verify(Files.readAllBytes(originals.resolve(id + ".jpg")));
            verify(Files.readAllBytes(thumbs.resolve(id + ".jpg")));
        }
        var fixture = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        fixture.setRGB(0, 0, 0xffff0000); fixture.setRGB(1, 0, 0x8000ff00); fixture.setRGB(2, 0, 0x000000ff);
        var png = new ByteArrayOutputStream(); ImageIO.write(fixture, "png", png);
        verify(png.toByteArray());
        // Optional generated asset directories exercise the actual Minecraft
        // decoder against a delivered batch without starting a game window.
        for (String directory : args) {
            if (Files.isRegularFile(Path.of(directory))) {
                // Includes the last image entry to exercise ZIP64 offsets in
                // transparent resource packs larger than four GiB.
                try (var zip = new ZipFile(directory)) {
                    var faces = zip.stream().filter(e -> e.getName().contains("/card_art/")
                        && !e.isDirectory()).toList();
                    for (int index : new int[]{0, faces.size() / 2, faces.size() - 1}) {
                        try (var stream = zip.getInputStream(faces.get(index))) { verify(stream.readAllBytes()); }
                    }
                    var images = zip.stream().filter(e -> e.getName().endsWith(".png") || e.getName().endsWith(".jpg")).toList();
                    try (var stream = zip.getInputStream(images.getLast())) { verify(stream.readAllBytes()); }
                }
                continue;
            }
            try (var paths = Files.newDirectoryStream(Path.of(directory), "*.png")) {
                for (Path path : paths) verify(Files.readAllBytes(path));
            }
        }
        byte[] oversized = png.toByteArray();
        // The decoder must check header dimensions before allocating image pixels.
        java.nio.ByteBuffer.wrap(oversized).putInt(16, 9000);
        var crc = new java.util.zip.CRC32(); crc.update(oversized, 12, 17);
        java.nio.ByteBuffer.wrap(oversized).putInt(29, (int) crc.getValue());
        try (var unexpected = CardImageDecoder.read(new ByteArrayInputStream(oversized))) {
            throw new AssertionError("Oversized image must be rejected before decoding");
        } catch (IOException expected) { check(expected.getMessage().contains("尺寸"), "Dimension safety check"); }
        for (byte[] corrupt : new byte[][]{new byte[0], new byte[]{1, 2, 3}, new byte[]{(byte)0xff, (byte)0xd8}}) {
            try (var unexpected = CardImageDecoder.read(new ByteArrayInputStream(corrupt))) {
                throw new AssertionError("Corrupt image must not decode");
            } catch (IOException expected) { }
        }
        String installedPacks = System.getProperty("ptcglive.test.packDir", "");
        int packs = 0;
        if (!installedPacks.isBlank()) {
            try (var paths = Files.newDirectoryStream(Path.of(installedPacks), "ptcglive-hd-*.zip")) {
                for (Path path : paths) try (var zip = new ZipFile(path.toFile())) {
                    var entry = zip.stream().filter(e -> e.getName().startsWith("assets/ptcglive_mc/card_art/")
                        && e.getName().endsWith(".jpg")).findFirst().orElseThrow();
                    try (var stream = zip.getInputStream(entry)) { verify(stream.readAllBytes()); }
                    packs++;
                }
            }
            check(packs == 98, "Expected 98 installed HD packs, got " + packs);
        }
        System.out.println("CardImageDecoderTest PASSED: " + decoded + " images; " + packs + " installed HD packs; original dimensions/RGBA/corrupt data");
    }
    private static void verify(byte[] bytes) throws Exception {
        var expected = ImageIO.read(new ByteArrayInputStream(bytes));
        check(expected != null, "Fixture must be valid PNG or JPEG");
        try (var actual = CardImageDecoder.read(new ByteArrayInputStream(bytes))) {
            check(actual.getWidth() == expected.getWidth() && actual.getHeight() == expected.getHeight(), "Original resolution changed");
            // Sample corners, centre and channels without writing any converted artwork to disk.
            for (int y : new int[]{0, expected.getHeight() / 2, expected.getHeight() - 1})
                for (int x : new int[]{0, expected.getWidth() / 2, expected.getWidth() - 1}) {
                    int argb = expected.getRGB(x, y);
                    int abgr = (argb & 0xff00ff00) | ((argb >>> 16) & 255) | ((argb & 255) << 16);
                    check(actual.getColor(x, y) == abgr, "Incorrect red/blue/alpha channels");
                }
            try (var held = CardImageDecoder.forHeldCard(actual)) {
                int expectedLongestSide = Math.min(1080, Math.max(expected.getWidth(), expected.getHeight()));
                check(Math.max(held.getWidth(), held.getHeight()) == expectedLongestSide, "Held artwork must be bounded to 1080, without upscaling");
                check(actual.getWidth() == expected.getWidth() && actual.getHeight() == expected.getHeight(), "Held conversion changed original image");
            }
        }
        decoded++;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
