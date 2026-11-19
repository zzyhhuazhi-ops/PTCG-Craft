import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.util.*;

/** Actual collection printings and the server validator share one mark index. */
public final class PrintedRegulationMarksTest {
    public static void main(String[] args) {
        var catalog = CardCatalog.load(); var definitions = BundledRules.loadCardDefinitions();
        var combat = BundledCombatData.load(); var formats = BundledRules.loadCurrentRulesets();
        var marks = PrintedRegulationMarks.all();
        check(marks.size() == 14784, "every registered source identity must be classified");
        for (var c : catalog) {
            check(marks.containsKey(c.id()), "missing catalog classification " + c.id());
            check(c.regulationMark().matches("[A-J]?"), "invented mark " + c.id());
            definitions.find(c.id()).ifPresent(d -> check(d.regulationMark().equals(c.regulationMark().isEmpty() ? "NONE" : c.regulationMark()), "UI/server disagree " + c.id()));
        }
        check(PrintedRegulationMarks.mark("sc_svp_003_unmarked").equals("G"), "unmarked rarity confused with regulation mark");
        check(PrintedRegulationMarks.find("sc_csve2c_003_unmarked").isEmpty(), "removed printing still classified");
        check(PrintedRegulationMarks.mark("sc_csm1ac_182_sr").equals("B"), "Chinese A-C marks lost");
        for (int i = 253; i <= 256; i++) check(PrintedRegulationMarks.mark("swshbsp_en_" + i).equals("F"), "visually reviewed missing promo");
        for (int i = 1; i <= 8; i++) check(PrintedRegulationMarks.mark("sve_en_00" + i).isEmpty(), "internal D code falsely printed on energy");
        check(PrintedRegulationMarks.find("made_up").isEmpty(), "unknown printing inferred");
        check(PrintedRegulationMarks.mark("bw1_en_001").isEmpty(), "old set given guessed letter");
        var owner = UUID.randomUUID(); var boxes = new CardBoxService(Clock.systemUTC());
        var box = boxes.create(owner, "赛制标记", CurrentFormats.OPEN);
        boxes.mint(owner, "sc_svp_003_unmarked"); boxes.mint(owner, "sc_cs5bc_004_k");
        var editor = new BoxEditorSession(boxes, definitions, combat, formats, Map.of(), owner, box.id());
        var view = editor.view(owner);
        view = editor.submit(owner, view.revision(), "SOURCE", "COLLECTION", 0, "");
        view = editor.submit(owner, view.revision(), "PAGE", "", 0, "标记：G");
        check(view.collection().size() == 1 && view.collection().getFirst().definitionId().equals("sc_svp_003_unmarked"), "mark filter incorrect");
        for (String ruleId : List.of(CurrentFormats.INTERNATIONAL, CurrentFormats.CHINESE)) {
            var rule = formats.get(ruleId);
            for (String mark : List.of("A","B","C","D","E","F","G","H","I","J","NONE","SD100","K")) {
                var c = card("test-mark", mark, CardCategory.POKEMON, false);
                boolean expected = (ruleId.equals(CurrentFormats.INTERNATIONAL) ? Set.of("H","I","J") : Set.of("G","H","I","J")).contains(mark);
                check(rule.allows(c) == expected, "wrong current mark eligibility " + mark);
            }
            var basic = definitions.find("sve_en_001").orElseThrow();
            check(rule.cardPool().assess(rule, basic, EnergyType.GRASS).status() == CardPoolPolicy.Status.LEGAL, "unmarked basic energy exception lost");
            check(!rule.allows(card("unreviewed-trainer", "NONE", CardCategory.TRAINER, false)), "unreviewed blank trainer admitted");
            check(!rule.allows(card("unmarked-special-energy", "NONE", CardCategory.ENERGY, false)), "special energy received basic exception");
            check(rule.allows(definitions.find("swsh2_en_154").orElseThrow()), "verified Boss reprint rejected");
            String oldUnmarked = rule.cardPool().legalCardIds().stream()
                .filter(id -> PrintedRegulationMarks.mark(id).isEmpty()).findFirst().orElseThrow();
            check(rule.allows(definitions.find(oldUnmarked).orElseThrow()), "official unmarked reprint exception lost");
            var deck = new ArrayList<CardInstance>();
            for (int i=0; i<60; i++) deck.add(new CardInstance(UUID.randomUUID(), owner, i<4 ? "fixture-basic-h" : "sve_en_001"));
            var validator = new DeckValidator(definitions, combat);
            check(validator.validate(rule, deck).canStartMatch(), "valid server deck rejected");
            deck.set(0, new CardInstance(UUID.randomUUID(), owner, "sc_cbb5c_0101_circle"));
            check(PrintedRegulationMarks.mark("sc_cbb5c_0101_circle").equals("H"), "collectible H classification missing");
            var report = validator.validate(rule, deck);
            check(report.canStartMatch(), "reviewed exclusive Captain Pikachu rejected: "+report.issues());
        }
        var starter = formats.get(CurrentFormats.STARTER);
        for (String id : starter.cardPool().legalCardIds()) {
            check(!definitions.find(id).orElseThrow().regulationMark().equals("SD100"), "fake SD100 letter retained");
            check(starter.allows(definitions.find(id).orElseThrow()), "starter fixed pool broken");
        }
        System.out.println("PrintedRegulationMarksTest passed: 14784 classifications; exact printings, aliases, A-J, basic/reprint exceptions, UI search and server combat gate");
    }
    private static CardDefinition card(String id, String mark, CardCategory category, boolean basicEnergy) {
        return new CardDefinition(id,"测试",category,category==CardCategory.TRAINER?TrainerCategory.ITEM:TrainerCategory.NONE,mark,
            Set.of("international"),category==CardCategory.POKEMON,basicEnergy,4,Map.of(),EffectSupport.SUPPORTED,null);
    }
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
