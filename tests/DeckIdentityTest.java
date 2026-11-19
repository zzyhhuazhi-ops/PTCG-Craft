import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class DeckIdentityTest {
    public static void main(String[] args) {
        var base = BundledRules.loadCardDefinitions().find("fixture-basic-h").orElseThrow();
        var print2 = new CardDefinition("fixture-basic-other-print", base.nameZh(), base.category(), base.trainerCategory(),
            "H", base.regions(), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
        var definitions = new ArrayList<>(BundledRules.loadCardDefinitions().all()); definitions.add(print2);
        UUID owner = UUID.randomUUID(); var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner,
            i < 3 ? base.id() : i < 5 ? print2.id() : "fixture-energy-h"));
        var validator = new DeckValidator(new CardDatabase(definitions));
        var rule = BundledRules.loadRulesets().get("standard-international-2026");
        if (validator.validate(rule, cards).issues().stream().noneMatch(i -> i.code().equals("同名数量超限")))
            throw new AssertionError("different printings bypassed the four-copy name limit");
        cards.set(4, cards.get(3));
        if (validator.validate(rule, cards).issues().stream().noneMatch(i -> i.code().equals("卡牌实例重复")))
            throw new AssertionError("duplicate physical instance accepted");
        System.out.println("DeckIdentityTest passed: cross-printing name count and unique physical instances");
    }
}
