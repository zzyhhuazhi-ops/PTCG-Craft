import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.table.*;
import java.time.*;
import java.util.*;

/** Regression coverage for the actual runtime format loader, save migration and server gates. */
public final class CurrentFormatsTest {
    private static final UUID OWNER = new UUID(0, 789);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC);
    public static void main(String[] args) {
        var rules = BundledRules.loadCurrentRulesets();
        check(new ArrayList<>(rules.keySet()).equals(CurrentFormats.IDS), "runtime must expose exactly four ordered formats");
        check(BundledRules.loadRulesets().containsKey("standard-cn-2024-0517"), "historical snapshots lost");
        var international = rules.get(CurrentFormats.INTERNATIONAL);
        var chinese = rules.get(CurrentFormats.CHINESE);
        for (String region : List.of("international", "cn", "cn-community", "jp")) {
            for (String mark : List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "NONE", "SD100", "K")) {
                var card = pokemon("mark-test", "标记测试", mark, region, Map.of(), Set.of());
                check(international.allows(card) == Set.of("H", "I", "J").contains(mark), "international letter/region mismatch " + mark + region);
                check(chinese.allows(card) == Set.of("G", "H", "I", "J").contains(mark), "Chinese letter/region mismatch " + mark + region);
                check(rules.get(CurrentFormats.OPEN).allows(card), "open pool restricted " + mark);
            }
        }
        for (var rule : List.of(international, chinese)) {
            for (EnergyType type : EnergyType.values()) {
                var decision = rule.cardPool().assess(rule, energy("e", true), type);
                boolean allowed = !Set.of(EnergyType.FAIRY, EnergyType.COLORLESS, EnergyType.DRAGON).contains(type);
                check((decision.status() == CardPoolPolicy.Status.LEGAL) == allowed, "basic energy exception " + type);
            }
            check(rule.cardPool().assess(rule, energy("e", true)).status() == CardPoolPolicy.Status.NEEDS_DATA, "unknown energy type admitted");
            check(!rule.allows(energy("special", false)), "unmarked special energy treated as basic");
        }
        var database = BundledRules.loadCardDefinitions();
        var combat = BundledCombatData.load();
        var starter = rules.get(CurrentFormats.STARTER);
        check(starter.cardPool().legalCardIds().size() == 438, "SD100 pool changed unexpectedly");
        for (String id : starter.cardPool().legalCardIds()) check(starter.allows(database.find(id).orElseThrow()), "known SD100 card rejected " + id);
        check(!starter.allows(pokemon("fake-sd100", "测试", "SD100", "cn-community", Map.of(), Set.of())), "SD100 mark alone bypasses pool");
        check(!starter.allows(database.find("sv5_en_014").orElseThrow()), "outside card in SD100 pool");
        check(international.allows(database.find("swsh2_en_154").orElseThrow()), "reviewed Boss's Orders reprint rejected");
        check(chinese.allows(database.find("swsh2_en_154").orElseThrow()), "Chinese reviewed reprint rejected");
        verifyConstruction(rules, database);
        verifyMigrationAndServer(rules, database, combat);
        System.out.println("CurrentFormatsTest passed: four runtime formats, A-J/unknown marks, regions, energy, SD100 mixed pool, reprints, deck limits, migration, server selection and match creation");
    }
    private static void verifyConstruction(Map<String, Ruleset> rules, CardDatabase real) {
        var definitions = new ArrayList<>(real.all());
        definitions.add(pokemon("test-basic", "基础测试", "H", "jp", Map.of(), Set.of()));
        definitions.add(pokemon("test-print", "基础测试", "H", "cn", Map.of(), Set.of()));
        definitions.add(pokemon("test-prism", "棱镜测试", "NONE", "international", Map.of(), Set.of(PokemonFamily.PRISM_STAR)));
        definitions.add(pokemon("test-prism2", "棱镜测试", "NONE", "international", Map.of(), Set.of(PokemonFamily.PRISM_STAR)));
        definitions.add(pokemon("test-normal", "棱镜测试", "H", "international", Map.of(), Set.of()));
        definitions.add(pokemon("test-ace1", "ACE甲", "H", "cn", Map.of("ACE SPEC", 1), Set.of()));
        definitions.add(pokemon("test-ace2", "ACE乙", "H", "cn", Map.of("ACE SPEC", 1), Set.of()));
        definitions.add(pokemon("test-rad1", "光辉甲", "F", "cn", Map.of("光辉宝可梦", 1), Set.of()));
        definitions.add(pokemon("test-rad2", "光辉乙", "F", "cn", Map.of("光辉宝可梦", 1), Set.of()));
        var validator = new DeckValidator(new CardDatabase(definitions));
        var rule = rules.get(CurrentFormats.OPEN);
        var deck = deck("test-basic", "sve_en_001");
        check(validator.validate(rule, deck).canStartMatch(), "legal deck rejected");
        check(has(validator.validate(rule, deck.subList(0, 59)), "牌组张数"), "59 cards accepted");
        var noBasic = deck("sve_en_001", "sve_en_001");
        check(has(validator.validate(rule, noBasic), "缺少基础宝可梦"), "no basic accepted");
        deck.set(4, instance("test-print"));
        check(has(validator.validate(rule, deck), "同名数量超限"), "cross-print count bypass");
        deck = deck("test-basic", "sve_en_001"); deck.set(5, deck.get(4));
        check(has(validator.validate(rule, deck), "卡牌实例重复"), "duplicate instance accepted");
        deck = deck("test-basic", "sve_en_001"); deck.set(5, instance("test-prism")); deck.set(6, instance("test-prism2"));
        check(has(validator.validate(rule, deck), "棱镜之星数量超限"), "duplicate prism print accepted");
        deck.set(6, instance("test-normal"));
        check(!has(validator.validate(rule, deck), "棱镜之星数量超限"), "ordinary same-name Pokemon counted as prism");
        deck = deck("test-normal", "sve_en_001"); deck.set(4, instance("test-prism"));
        check(validator.validate(rule, deck).canStartMatch(), "four ordinary plus one Prism Star should have separate printed names");
        for (String id : List.of("sm5_en_120", "sm5_en_136", "sm9_en_134")) {
            check(real.find(id).orElseThrow().specialDeckLimits().containsKey("棱镜之星"), "non-Pokemon prism metadata missing");
            deck = deck("test-basic", "sve_en_001"); deck.set(5, instance(id)); deck.set(6, instance(id));
            check(has(validator.validate(rule, deck), "棱镜之星数量超限"), "non-Pokemon prism duplicate accepted " + id);
        }
        for (String prefix : List.of("test-ace", "test-rad")) {
            deck = deck("test-basic", "sve_en_001"); deck.set(5, instance(prefix + "1")); deck.set(6, instance(prefix + "2"));
            check(has(validator.validate(rule, deck), "特殊构筑限制"), "different-name special limit bypass " + prefix);
        }
        var sd = new ArrayList<CardInstance>();
        for (String id : rules.get(CurrentFormats.STARTER).cardPool().legalCardIds().stream().sorted().toList()) {
            if (real.find(id).orElseThrow().effectSupport() == EffectSupport.SUPPORTED && !real.find(id).orElseThrow().basicEnergy()) sd.add(instance(id));
            if (sd.size() == 12) break;
        }
        while (sd.size() < 60) sd.add(instance("sd100_cn_grass_energy"));
        check(validator.validate(rules.get(CurrentFormats.STARTER), sd).canStartMatch(), "free mixing within SD100 rejected");
    }
    private static void verifyMigrationAndServer(Map<String, Ruleset> rules, CardDatabase database, CombatCatalog combat) {
        var boxes = new CardBoxService(CLOCK);
        var old = boxes.create(OWNER, "旧卡盒", "standard-cn-2025");
        var actual = boxes.mint(OWNER, "sv5_en_014"); boxes.put(OWNER, old.id(), actual.instanceId());
        var before = boxes.snapshot(); var restored = CardBoxService.restore(CLOCK, before);
        var after = restored.getOwned(OWNER, old.id());
        check(after.selectedRulesetId().equals(CurrentFormats.CHINESE), "legacy box rule not migrated");
        check(after.contents().equals(old.contents()) && after.lastModified().equals(old.lastModified())
            && restored.snapshot().cards().equals(before.cards()) && after.ownerId().equals(old.ownerId()), "migration altered assets or timestamps");
        check(CurrentFormats.migrate("third-party-format").equals("third-party-format"), "unknown selection silently changed");
        var editor = new BoxEditorSession(restored, database, combat, rules, Map.of(), OWNER, old.id());
        var view = editor.view(OWNER);
        check(view.rules().stream().map(BoxEditorSession.RuleRow::id).toList().equals(CurrentFormats.IDS), "box rules differ from four runtime choices");
        check(view.rules().getFirst().reasons().stream().anyMatch(s -> s.contains("正好 60")), "box missing rule details");
        rejects(() -> editor.submit(OWNER, view.revision(), "RULESET", "standard-cn-2025", 0, ""));
        var key = new CardTableKey("minecraft:overworld", 12345);
        var saved = new CardTableManager.Snapshot(List.of(new CardTableManager.SavedWaitingTable(key, OWNER, "standard-cn-2025", null, null, null)), List.of(), List.of());
        var manager = CardTableManager.restore(rules, restored, database, combat, () -> new ServerRandom(79), CLOCK, saved);
        check(manager.snapshot().waitingTables().getFirst().rulesetId().equals(CurrentFormats.CHINESE), "waiting table lost rather than migrated");
        rejects(() -> manager.open(new CardTableKey("minecraft:overworld", 54321), OWNER, "standard-cn-2025"));
        for (String ruleId : List.of(CurrentFormats.INTERNATIONAL, CurrentFormats.CHINESE)) {
            var a = restored.createPopulated(OWNER, "A", ruleId, deck("fixture-basic-h", "sve_en_001").stream().map(CardInstance::definitionId).toList());
            var b = restored.createPopulated(OWNER, "B", ruleId, deck("fixture-basic-h", "sve_en_001").stream().map(CardInstance::definitionId).toList());
            var match = manager.startPractice(OWNER, a.id(), b.id(), ruleId);
            check(match.match().ruleset().id().equals(ruleId), "server froze wrong format");
            manager.endMatch(match.matchId());
        }
    }
    private static List<CardInstance> deck(String basic, String energy) {
        var cards = new ArrayList<CardInstance>(); for (int i = 0; i < 60; i++) cards.add(instance(i < 4 ? basic : energy)); return cards;
    }
    private static CardInstance instance(String id) { return new CardInstance(UUID.randomUUID(), OWNER, id); }
    private static CardDefinition pokemon(String id, String name, String mark, String region, Map<String,Integer> limits, Set<PokemonFamily> families) {
        return new CardDefinition(id,name,CardCategory.POKEMON,TrainerCategory.NONE,mark,Set.of(region),true,false,4,limits,EffectSupport.SUPPORTED,null,families);
    }
    private static CardDefinition energy(String id, boolean basic) {
        return new CardDefinition(id,"能量测试",CardCategory.ENERGY,TrainerCategory.NONE,"NONE",Set.of("jp"),false,basic,4,Map.of(),EffectSupport.SUPPORTED,null);
    }
    private static boolean has(DeckValidationReport report, String code) { return report.issues().stream().anyMatch(i -> i.code().equals(code)); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("invalid operation accepted"); }
}
