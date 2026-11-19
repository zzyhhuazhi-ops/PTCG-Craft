import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.economy.EconomyConfig;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.util.*;

public final class ExclusiveCollectionPackTest {
    public static void main(String[] args) throws Exception {
        var cards = CardCatalog.load();
        var rules = BundledRules.loadCardDefinitions();
        var pack = CustomPackCatalog.loadBundled(cards, rules).get("simplified_chinese_exclusive");
        var pool = pack.pools().get("collectible");
        check(pack.cardsPerPack() == 5 && pool.size() == 52, "收藏包必须为52张，每包5张");
        check(EconomyConfig.loadBundled().packPrice(pack.setId()) == 2000, "收藏包价格改变");
        var removed = Set.of(
            "sc_cs5bc_001_c","sc_cs5bc_002_u","sc_csve2c_003_unmarked","sc_cs5bc_039_c",
            "sc_cs5bc_040_u","sc_cs5bc_112_c","sc_cs5ac_001_c","sc_cs5ac_002_u","sc_cs5ac_003_r",
            "sc_cs5ac_064_c","sc_cs5ac_065_c","sc_cs5_5c_012_c","sc_cs5_5c_013_u");
        for (String id:removed) {
            check(dev.hua.ptcglive.CardExclusions.contains(id),"缺少删除登记："+id);
            check(!pool.containsKey(id)&&rules.find(id).isEmpty()&&cards.stream().noneMatch(c->c.id().equals(id)),"删除卡仍被加载："+id);
        }
        var combat=dev.hua.ptcglive.rules.BundledCombatData.load();
        var issues=new ArrayList<String>();
        var openRules=BundledRules.loadCurrentRulesets().get(CurrentFormats.OPEN);
        for (String id:pool.keySet()) {
            check(rules.find(id).isPresent(),"保留卡缺少规则："+id);
            var owner=UUID.randomUUID();var deck=new ArrayList<CardInstance>();
            for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,i==0?id:i<4?"fixture-basic-h":"sve_en_001"));
            var report=new DeckValidator(rules,combat).validate(openRules,deck);
            check(report.canStartMatch(),"保留卡未通过服务端组卡检查："+id+" "+report.issues());
            combat.pokemon(id).ifPresent(p->{
                p.attacks().forEach(a->dev.hua.ptcglive.engine.effect.AttackProgramRules.unsupportedReason(a).ifPresent(why->issues.add(id+" "+a.nameZh()+": "+why)));
                p.abilities().forEach(a->{
                    if(a.activated()&&a.composed())dev.hua.ptcglive.engine.effect.ActivatedAbilityRules.unsupportedReason(a).ifPresent(why->issues.add(id+": "+why));
                    if(a.triggered())dev.hua.ptcglive.engine.effect.TriggeredAbilityRules.unsupportedReason(a).ifPresent(why->issues.add(id+": "+why));
                });
            });
            combat.trainer(id).filter(t->t.composed()).ifPresent(t->dev.hua.ptcglive.engine.effect.TrainerProgramRules.unsupportedReason(t.effectProgram()).ifPresent(why->issues.add(id+": "+why)));
            check(combat.pokemon(id).isPresent()||combat.trainer(id).isPresent(),"战斗数据加载失败："+id);
        }
        check(issues.isEmpty(),"独占卡程序错误："+issues);
        for (String id : pool.keySet()) {
            check(pool.get(id) == 1 && Math.abs(PackOpeningService.probability(pool, id) - 1d/52) < 1e-12, "抽取权重错误");
            for (String path : List.of("card_thumb/" + id + ".jpg", "textures/item/cards/" + id + ".png")) {
                try (var stream = ExclusiveCollectionPackTest.class.getResourceAsStream("/assets/ptcglive_mc/" + path)) {
                    check(stream != null && javax.imageio.ImageIO.read(stream) != null, "缺少或损坏内置小图：" + id);
                }
            }
            for (String ns : List.of("ptcglive", "ptcglive_mc"))
                check(ExclusiveCollectionPackTest.class.getResource("/assets/" + ns + "/models/item/" + id + ".json") != null, "缺少物品模型");
        }
        var preview = PackUiSnapshot.preview(UUID.randomUUID(), pack, cards, rules);
        check(preview.cards().size() == 52, "预览没有更新卡池");
        for (var row : preview.cards())
            check(Math.abs(row.chances().getFirst().probability() - 1d/52) < 1e-12, "显示概率和实际概率不同");
        var clock = Clock.systemUTC(); var ledger = new CardBoxService(clock);
        var opening = new PackOpeningService(ledger); var owner = UUID.randomUUID();
        var physical = opening.drawPhysical(owner, pack, new ServerRandom(42));
        check(physical.cards().size() == 5 && ledger.unboxedForOwner(owner).isEmpty(), "实体发卡预览重复写入账本");
        var seen = new HashSet<String>(); var instances = new HashSet<UUID>();
        for (int seed = 0; seed < 300; seed++) {
            var result = opening.open(owner, pack, new ServerRandom(seed));
            check(result.cards().size() == 5, "实际开包张数错误");
            for (var card : result.cards()) {
                check(pool.containsKey(card.definitionId()) && instances.add(card.instanceId()), "越界或重复实体UUID");
                seen.add(card.definitionId());
            }
        }
        check(seen.equals(pool.keySet()) && instances.size() == 1500, "卡池存在不可抽到的卡或发卡数量错误");
        var box = ledger.create(owner, "新增独占卡", CurrentFormats.OPEN);
        var card = ledger.unboxedForOwner(owner).getFirst();
        ledger.put(owner, box.id(), card.instanceId());
        var restored = CardBoxService.restore(clock, ledger.snapshot());
        check(restored.snapshot().cards().size() == 1500, "持久化恢复丢卡");
        check(restored.snapshot().boxes().getFirst().contents().contains(card.instanceId()), "持久化丢失卡盒位置");
        for (String id : seen) check(restored.discovered(owner, id), "图鉴发现记录未持久化");
        check(restored.unboxedForOwner(UUID.randomUUID()).isEmpty(), "玩家资产串号");
        System.out.println("ExclusiveCollectionPackTest passed: 52 cards, 13 removals, exact odds, 2000 coins, 1500 draws, assets and persistence");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
