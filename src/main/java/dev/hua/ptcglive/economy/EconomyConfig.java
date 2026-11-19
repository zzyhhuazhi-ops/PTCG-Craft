package dev.hua.ptcglive.economy;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, server-owned prices. Clients receive display copies only. */
public record EconomyConfig(long packPrice, Map<String, Long> mineralValues) {
    public static final String SUPPORTER_HIGH_RARITY_PACK = "all_supporter_high_rarity";
    public static final String SC_EXCLUSIVE_PACK = "simplified_chinese_exclusive";
    public static final java.util.Set<String> ERA_SUPPORTER_PACKS = java.util.Set.of("cn_supporter_sm", "cn_supporter_swsh", "cn_supporter_sv", "mega_supporter_high_rarity");
    private static final String RESOURCE = "/data/ptcglive/economy/economy.json";

    public EconomyConfig {
        if (packPrice != 20) throw new IllegalArgumentException("普通卡包售价必须为 20 宝可币");
        mineralValues = positiveCopy(mineralValues, "矿物");
    }

    public static EconomyConfig loadBundled() {
        var stream = EconomyConfig.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("缺少经济配置：" + RESOURCE);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Raw raw = new Gson().fromJson(reader, Raw.class);
            if (raw == null) throw new IllegalStateException("经济配置为空");
            return new EconomyConfig(raw.packPrice, raw.mineralValues);
        } catch (Exception error) {
            throw new IllegalStateException("无法读取经济配置", error);
        }
    }

    public long mineralValue(String itemId) {
        Long value = mineralValues.get(itemId);
        if (value == null) throw new IllegalArgumentException("该物品不能兑换宝可币");
        return value;
    }

    public long packPrice(String setId) {
        return SUPPORTER_HIGH_RARITY_PACK.equals(setId) || SC_EXCLUSIVE_PACK.equals(setId) || ERA_SUPPORTER_PACKS.contains(setId) ? 2000 : packPrice;
    }

    public long cartPrice(Map<String, Integer> cart) {
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("购物车为空");
        long total = 0;
        for (var row : cart.entrySet()) {
            if (row.getKey() == null || row.getKey().isBlank() || row.getValue() == null || row.getValue() < 1)
                throw new IllegalArgumentException("购物车包含无效卡包或数量");
            total = Math.addExact(total, Math.multiplyExact(packPrice(row.getKey()), row.getValue()));
        }
        return total;
    }

    private static Map<String, Long> positiveCopy(Map<String, Long> source, String label) {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException(label + "价格表不能为空");
        var result = new LinkedHashMap<String, Long>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value < 1)
                throw new IllegalArgumentException(label + "价格无效：" + key);
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException(label + "价格重复：" + key);
        });
        return Map.copyOf(result);
    }

    private static final class Raw {
        long packPrice;
        Map<String, Long> mineralValues;
    }
}
