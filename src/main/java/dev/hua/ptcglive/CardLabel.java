package dev.hua.ptcglive;

import java.util.Map;

/** Display abbreviations only; never used to infer format legality or replace audited printed titles. */
public final class CardLabel {
    private CardLabel() { }
    private static final Map<String, String> RARITY = Map.ofEntries(
        Map.entry("待核对", "UNKNOWN"), Map.entry("角色特别稀有", "CSR"),
        Map.entry("●", "C"), Map.entry("普通", "C"), Map.entry("非凡", "U"), Map.entry("稀有", "R"), Map.entry("闪卡稀有", "R"),
        Map.entry("双重稀有", "RR"), Map.entry("超稀有", "SR"), Map.entry("秘密稀有", "SR"),
        Map.entry("插画稀有", "AR"), Map.entry("特别插画稀有", "SAR"), Map.entry("极稀有", "UR"),
        Map.entry("超级极稀有", "MUR"), Map.entry("特典", "PROMO"), Map.entry("闪色稀有", "S"),
        Map.entry("闪色超稀有", "SSR"), Map.entry("彩虹稀有", "HR"), Map.entry("光辉稀有", "K"),
        Map.entry("ACE SPEC 稀有", "ACE"), Map.entry("ACE 稀有", "ACE"), Map.entry("惊奇稀有", "A"),
        Map.entry("★★★", "★★★"), Map.entry("特殊金卡（无标记）", "金卡"),
        Map.entry("EX 闪卡稀有", "RR"), Map.entry("GX 闪卡稀有", "RR"), Map.entry("V 闪卡稀有", "RR"),
        Map.entry("VMAX 闪卡稀有", "RRR"), Map.entry("VSTAR 闪卡稀有", "RRR"), Map.entry("BREAK 稀有", "R"),
        Map.entry("棱镜之星稀有", "PR"), Map.entry("闪耀稀有", "S"), Map.entry("训练家画廊闪卡稀有", "CHR"),
        Map.entry("黑白稀有", "BWR"), Map.entry("皮卡丘稀有", "PKR"), Map.entry("未来稀有", "FUR"), Map.entry("训练家画廊超稀有", "SR"), Map.entry("经典收藏", "CLASSIC"), Map.entry("超级攻击稀有", "MA"));
    public static String rarityCode(String printedRarity) {
        if (printedRarity == null || printedRarity.isBlank()) return "OTHER";
        return RARITY.containsValue(printedRarity) ? printedRarity : RARITY.getOrDefault(printedRarity, "OTHER");
    }
    public static String format(String imported) {
        String[] fields = imported.split(" · ", -1);
        if (fields.length != 3) return imported;
        String rarity = rarityCode(fields[2]); if (rarity.equals("OTHER")) rarity = "?";
        if (rarity.equals("UNKNOWN")) rarity = "待核对";
        return fields[0] + " " + rarity + " " + fields[1];
    }
}
