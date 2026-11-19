package dev.hua.ptcglive.packs;

/** A bounded intent, never client-supplied cards, odds or random seeds. */
public final class PackBatchPolicy {
    private PackBatchPolicy() { }
    public static void validateCount(int count) {
        if (count != 1 && count != 4 && count != 5 && count != 8 && count != 10 && count != 16 && count != 32 && count != 64) throw new IllegalArgumentException("只允许一次打开 1、4、5、8、10、16、32 或 64 包");
    }
    public static int nextCount(int count) {
        validateCount(count);
        return switch (count) { case 1 -> 4; case 4 -> 5; case 5 -> 8; case 8 -> 10; case 10 -> 16; case 16 -> 32; case 32 -> 64; default -> 1; };
    }
    public static int countForAction(String action) {
        return switch (action) {
            case "CONFIRM", "CONFIRM_1", "ALBUM_1" -> 1;
            case "CONFIRM_4", "ALBUM_4" -> 4;
            case "CONFIRM_5", "ALBUM_5" -> 5;
            case "CONFIRM_8", "ALBUM_8" -> 8;
            case "CONFIRM_10", "ALBUM_10" -> 10;
            case "CONFIRM_16", "ALBUM_16" -> 16;
            case "CONFIRM_32", "ALBUM_32" -> 32;
            case "CONFIRM_64", "ALBUM_64" -> 64;
            default -> 0;
        };
    }
}
