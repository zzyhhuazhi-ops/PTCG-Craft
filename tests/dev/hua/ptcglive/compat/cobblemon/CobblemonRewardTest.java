package dev.hua.ptcglive.compat.cobblemon;

import dev.hua.ptcglive.CardCatalog;
import java.util.Random;

public final class CobblemonRewardTest {
    public static void main(String[] args) {
        var table = CobblemonRewardTable.load(CardCatalog.load());
        check(table.speciesCount() == 1025, "Cobblemon 1.8 的 1025 个物种必须全部有卡池");
        check(table.cardCount("pikachu") > 0, "皮卡丘卡池为空");
        check(table.cardCount("nidoranf") > 0 && table.cardCount("nidoranm") > 0, "尼多兰性别物种未正确映射");

        var random = new Random(20261021L);
        boolean sawOne = false, sawThree = false;
        for (int i = 0; i < 20_000; i++) {
            var cards = table.roll("pikachu", random);
            check(cards.size() >= 1 && cards.size() <= 3, "奖励数量必须为 1–3 张");
            sawOne |= cards.size() == 1;
            sawThree |= cards.size() == 3;
            check(cards.stream().map(CardCatalog.Entry::id).distinct().count() == cards.size(), "同次奖励不应重复同一印刷");
        }
        check(sawOne && sawThree, "固定随机序列必须覆盖 1 张和 3 张奖励");
        check(CobblemonRewardTable.rarityWeight("普通") > CobblemonRewardTable.rarityWeight("非凡"), "普通权重必须高于非凡");
        check(CobblemonRewardTable.rarityWeight("非凡") > CobblemonRewardTable.rarityWeight("稀有"), "非凡权重必须高于稀有");
        check(CobblemonRewardTable.rarityWeight("稀有") > CobblemonRewardTable.rarityWeight("双重稀有"), "稀有权重必须高于双重稀有");
        check(CobblemonRewardTable.rarityWeight("双重稀有") > CobblemonRewardTable.rarityWeight("特别插画稀有"), "双重稀有权重必须高于特别插画稀有");
        check(CobblemonRewardTable.rarityWeight("特别插画稀有") > CobblemonRewardTable.rarityWeight("极稀有"), "特别插画稀有权重必须高于极稀有");
        check(CobblemonRewardTable.rarityWeight("极稀有") == 1, "最高稀有度权重下限必须为 1");
        check(table.roll("missing_species", random).isEmpty(), "不存在的物种不得发放无关卡牌");
        System.out.println("Cobblemon reward test passed: 1025 species, guaranteed 1–3 cards, rarity floor 1");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
