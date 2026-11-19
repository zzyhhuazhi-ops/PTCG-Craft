import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.starter.StarterDeck100Catalog;
import dev.hua.ptcglive.starter.StarterDeck100Service;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.UUID;

/** Regression coverage for the bundled 001–102 snapshot and server-only grant ledger. */
public final class StarterDeck100Test {
    public static void main(String[] args) {
        var catalog = StarterDeck100Catalog.loadBundled();
        if (catalog.decks().size() != 102) throw new AssertionError("缺少起始卡组100条目");
        var listed = new HashSet<String>();
        for (var deck : catalog.decks()) {
            if (deck.total() != 60 || deck.expandedCardIds().size() != 60)
                throw new AssertionError("卡组不是 60 张: " + deck.number());
            listed.addAll(deck.expandedCardIds());
        }
        var available = CardCatalog.load().stream().map(CardCatalog.Entry::id).collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(listed)) throw new AssertionError("起始卡组中存在未注册卡牌定义");
        var definitions = BundledRules.loadCardDefinitions();
        if (listed.stream().anyMatch(id -> definitions.find(id).isEmpty()))
            throw new AssertionError("起始卡组中存在未加载的服务器卡牌定义");
        if (!BundledRules.loadRulesets().containsKey(catalog.rulesetId()))
            throw new AssertionError("起始卡组的历史规则包没有加载");

        UUID owner = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
        var boxes = new CardBoxService(clock);
        var service = new StarterDeck100Service(catalog, boxes);
        var grant = service.grant(owner, 1234L, new ServerRandom(100));
        if (!catalog.deck(grant.deckNumber()).nameZh().equals(grant.deckNameZh())) throw new AssertionError("发放的卡组名称不一致");
        if (boxes.contentsForValidation(owner, grant.boxId()).size() != 60) throw new AssertionError("服务器没有生成完整 60 张卡盒");
        var audit = service.auditForAdministration();
        if (audit.size() != 1 || audit.getFirst().randomAudit().size() != 1
            || !audit.getFirst().randomAudit().getFirst().operation().equals("starter-deck-100-grant")
            || audit.getFirst().randomAudit().getFirst().bound() != 102) throw new AssertionError("随机审计不完整");

        var restored = new StarterDeck100Service(catalog, CardBoxService.restore(clock, boxes.snapshot()), service.snapshot());
        if (restored.auditForAdministration().size() != 1) throw new AssertionError("起始卡组发放审计未随快照恢复");
        var numberedBoxes = new CardBoxService(clock);
        var numbered = new StarterDeck100Service(catalog, numberedBoxes);
        var physicalCards = new HashSet<UUID>();
        for (var deck : catalog.decks()) {
            var selected = numbered.grantNumbered(owner, deck.number(), 1235L);
            var contents = numberedBoxes.contentsForValidation(owner, selected.boxId());
            if (!selected.deckNumber().equals(deck.number()) || !contents.stream().map(dev.hua.ptcglive.rules.CardInstance::definitionId).sorted().toList()
                    .equals(deck.expandedCardIds().stream().sorted().toList()))
                throw new AssertionError("指定编号发放的构筑不一致: " + deck.number());
            for (var card : contents) if (!physicalCards.add(card.instanceId())) throw new AssertionError("不同卡盒重复使用实际卡牌实例");
        }
        for (String invalid : java.util.Arrays.asList("000", "103", "53", "abc", null)) {
            try { numbered.grantNumbered(owner, invalid, 1236L); throw new AssertionError("无效编号被发放: " + invalid); }
            catch (IllegalArgumentException | NullPointerException expected) { }
        }
        if (numbered.snapshot().grants().size() != 102 || numbered.snapshot().grants().stream().anyMatch(g -> !g.randomAudit().isEmpty()))
            throw new AssertionError("指定编号发放的审计错误或无效请求产生记录");
        var numberedRestored = new StarterDeck100Service(catalog, CardBoxService.restore(clock, numberedBoxes.snapshot()), numbered.snapshot());
        if (numberedRestored.snapshot().grants().size() != 102) throw new AssertionError("指定卡组审计未恢复");
        System.out.println("StarterDeck100Test passed: 102 套均为 60 张，服务器随机发放与审计快照可恢复");
    }
}
