import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs before GUI/network integration: validators and ownership invariants have no Minecraft dependency. */
public final class RulesCoreTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Ruleset STANDARD = new Ruleset("standard-international-2026", "2026.1.0", "国际 Standard（2026 赛季）",
        "international", java.time.LocalDate.of(2026, 4, 10), java.util.Set.of("H", "I", "J"), java.util.Set.of(),
        Map.of("光辉宝可梦", 1, "ACE SPEC", 1), List.of(), List.of());

    public static void main(String[] args) {
        CardDatabase data = BundledRules.loadCardDefinitions();
        Ruleset loaded = BundledRules.loadRulesets().get(STANDARD.id());
        if (loaded == null || !loaded.legalRegulationMarks().equals(java.util.Set.of("H", "I", "J")))
            throw new AssertionError("未加载 2026 国际 Standard 规则包");
        if (loaded.sources().size() < 3) throw new AssertionError("规则来源未记录");
        CardBoxService boxes = new CardBoxService(Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC));
        DeckBox deck = boxes.create(OWNER, "测试雷电卡组", STANDARD.id());
        List<CardInstance> complete = new ArrayList<>();
        for (int i = 0; i < 4; i++) complete.add(put(boxes, deck, "fixture-basic-h"));
        for (int i = 0; i < 56; i++) complete.add(put(boxes, deck, "fixture-energy-h"));
        DeckValidationReport legal = new DeckValidator(data, BundledCombatData.load()).validate(STANDARD, complete);
        if (!legal.canStartOfficialMatch()) throw new AssertionError("合法 60 张卡组被拒绝: " + legal.issues());

        DeckBox other = boxes.create(OWNER, "第二卡盒", STANDARD.id());
        boxes.rename(OWNER, other.id(), "重命名卡盒");
        boxes.selectRuleset(OWNER, other.id(), "casual-international-2026");
        if (!other.nameZh().equals("重命名卡盒") || !other.selectedRulesetId().equals("casual-international-2026") || boxes.boxesForOwner(OWNER).size() != 2)
            throw new AssertionError("卡盒名称、规则包或拥有者列表未更新");
        try { boxes.put(OWNER, other.id(), complete.getFirst().instanceId()); throw new AssertionError("同一实际卡被放入两个卡盒"); }
        catch (IllegalStateException expected) { }
        UUID match = UUID.randomUUID();
        List<CardInstance> snapshot = boxes.snapshotForMatch(OWNER, deck.id(), match);
        if (snapshot.size() != 60) throw new AssertionError("开局快照错误");
        try { boxes.take(OWNER, deck.id(), snapshot.getFirst().instanceId()); throw new AssertionError("锁定卡盒仍可编辑"); }
        catch (IllegalStateException expected) { }
        boxes.unlock(match);

        List<CardInstance> invalid = new ArrayList<>(complete.subList(0, 59));
        invalid.set(0, new CardInstance(UUID.randomUUID(), OWNER, "fixture-rotated-g"));
        invalid.set(1, new CardInstance(UUID.randomUUID(), OWNER, "fixture-unsupported-h"));
        invalid.set(2, new CardInstance(UUID.randomUUID(), OWNER, "fixture-unmodelled-h"));
        DeckValidationReport report = new DeckValidator(data, BundledCombatData.load()).validate(STANDARD, invalid);
        assertCode(report, "牌组张数"); assertCode(report, "赛制不合法"); assertCode(report, "效果未支持"); assertCode(report, "战斗数据未支持");

        try { new CardPackDefinition("demo-h", "演示卡包", Map.of("fixture-basic-h", 1), Map.of("普通", 1), "无", "1"); }
        catch (RuntimeException impossible) { throw new AssertionError("有效五张卡包数据被拒绝", impossible); }
        try { new PreconstructedDeckDefinition("pending", "中国官方商品", "待核验卡组", java.time.LocalDate.now(), "historical-cn", "https://www.pokemon.cn", PreconstructedDeckDefinition.Availability.PENDING_OFFICIAL_CARDLIST, "未找到完整官方卡表", List.of("fixture-basic-h"));
            throw new AssertionError("未核验预组被允许带卡表"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("RulesCoreTest passed: 版本化规则、卡盒所有权、锁定、卡组检查与预组保护均已验证");
    }
    private static CardInstance put(CardBoxService boxes, DeckBox deck, String definitionId) {
        CardInstance card = boxes.mint(OWNER, definitionId); boxes.put(OWNER, deck.id(), card.instanceId()); return card;
    }
    private static void assertCode(DeckValidationReport report, String code) {
        if (report.issues().stream().noneMatch(i -> i.code().equals(code))) throw new AssertionError("缺少失败原因: " + code + " / " + report.issues());
    }
}
