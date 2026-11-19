import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.CardExclusions;

/** Verifies the versioned fixture combat data can be parsed without guessing absent facts. */
public final class CombatCatalogTest {
    public static void main(String[] args) {
        CombatCatalog catalog = BundledCombatData.load();
        var definitions = BundledRules.loadCardDefinitions();
        for (String ordinary : java.util.List.of("xy11_en_083", "xybsp_en_184"))
            if (definitions.find(ordinary).isEmpty())
                throw new AssertionError("Ordinary Pokemon merely mentioning BREAK lost its rule definition: " + ordinary);
        for (String excluded : CardExclusions.ids()) {
            if (definitions.find(excluded).isPresent() || catalog.pokemonProfiles().containsKey(excluded)
                || catalog.energyProfiles().containsKey(excluded) || catalog.trainerProfiles().containsKey(excluded))
                throw new AssertionError("BREAK card remains in the rules database: " + excluded);
        }
        var attacker = catalog.pokemon("fixture-attacker-h").orElseThrow();
        var zap = attacker.attack("fixture-zap").orElseThrow();
        var defender = catalog.pokemon("fixture-defender-h").orElseThrow();
        if (attacker.hitPoints() != 70 || zap.energyCost().get(EnergyType.LIGHTNING) != 1
            || zap.baseDamage() != 30 || zap.damageType() != EnergyType.LIGHTNING)
            throw new AssertionError("攻击宝可梦战斗数据错误");
        if (defender.hitPoints() != 50 || defender.weaknessType() != EnergyType.LIGHTNING
            || defender.weaknessMultiplier() != 2 || defender.prizeCards() != 1)
            throw new AssertionError("防御宝可梦战斗数据错误");
        if (catalog.energyType("fixture-energy-h").orElseThrow() != EnergyType.LIGHTNING)
            throw new AssertionError("基本能量类型数据错误");
        var abilityDrawUntil = catalog.pokemon("fixture-ability-draw-until-h").orElseThrow().abilities().getFirst().effect();
        if (abilityDrawUntil.opcode() != AbilityEffect.Opcode.DRAW_UNTIL_HAND_SIZE || abilityDrawUntil.amount() != 8)
            throw new AssertionError("补齐手牌的特性效果没有从版本化数据正确加载");
        var abilitySearch = catalog.pokemon("fixture-ability-search-h").orElseThrow().abilities().getFirst().effect();
        if (abilitySearch.opcode() != AbilityEffect.Opcode.SEARCH_CARD_TO_HAND || abilitySearch.amount() != 1 || !abilitySearch.singleDeckSelection())
            throw new AssertionError("特性牌库检索效果没有从版本化数据正确加载");
        var optionalAbilitySearch = catalog.pokemon("fixture-ability-search-up-to-two-h").orElseThrow().abilities().getFirst().effect();
        if (optionalAbilitySearch.amount() != 2 || optionalAbilitySearch.minimumDeckSelections() != 0 || optionalAbilitySearch.singleDeckSelection())
            throw new AssertionError("至多两张特性牌库检索的最少选择数没有从版本化数据正确加载");
        var composed = catalog.pokemon("fixture-composed-attack-h").orElseThrow().attack("fixture-composed-coin").orElseThrow();
        if (composed.basicDamageTemplate().damage() != 10 || composed.basicDamageTemplate().energyCost().get(EnergyType.COLORLESS) != 1
            || composed.effectProgram().forTrigger(dev.hua.ptcglive.engine.effect.EffectTrigger.ON_ATTACK).size() != 1
            || !composed.basicDamageTemplate().effects().getFirst().type().equals("coin_flip"))
            throw new AssertionError("基础攻击模板或嵌套 EffectProgram 没有从版本化数据正确加载");
        var drawUntil = catalog.trainer("fixture-draw-until-h").orElseThrow().effect();
        if (drawUntil.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.DRAW_UNTIL_HAND_SIZE || drawUntil.amount() != 7)
            throw new AssertionError("补齐手牌的训练家效果没有从版本化数据正确加载");
        var basicSearch = catalog.trainer("fixture-search-basic-h").orElseThrow().effect();
        if (basicSearch.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.SEARCH_BASIC_POKEMON_TO_HAND || !basicSearch.needsDeckSelection())
            throw new AssertionError("基础宝可梦检索训练家效果没有从版本化数据正确加载");
        var pokemonSearch = catalog.trainer("fixture-search-pokemon-h").orElseThrow().effect();
        if (pokemonSearch.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.SEARCH_POKEMON_TO_HAND || !pokemonSearch.needsDeckSelection())
            throw new AssertionError("宝可梦检索训练家效果没有从版本化数据正确加载");
        var cardSearch = catalog.trainer("fixture-search-card-h").orElseThrow().effect();
        if (cardSearch.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.SEARCH_CARD_TO_HAND || !cardSearch.needsDeckSelection())
            throw new AssertionError("任意卡检索训练家效果没有从版本化数据正确加载");
        if (catalog.trainer("fixture-search-cost-h").orElseThrow().effect().discardHandCost() != 2)
            throw new AssertionError("训练家弃置手牌成本没有从版本化数据正确加载");
        var upToTwo = catalog.trainer("fixture-search-up-to-two-h").orElseThrow().effect();
        if (upToTwo.amount() != 2 || upToTwo.minimumDeckSelections() != 0 || !upToTwo.needsDeckSelection())
            throw new AssertionError("至多两张牌库检索的最少选择数没有从版本化数据正确加载");
        var randomDiscard = catalog.trainer("fixture-random-discard-h").orElseThrow().effect();
        if (randomDiscard.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.DISCARD_OPPONENT_RANDOM_HAND || randomDiscard.amount() != 2)
            throw new AssertionError("随机弃置对方手牌训练家效果没有从版本化数据正确加载");
        var discardStadium = catalog.trainer("fixture-discard-stadium-h").orElseThrow().effect();
        if (discardStadium.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.DISCARD_STADIUM || discardStadium.amount() != 0)
            throw new AssertionError("弃置场地训练家效果没有从版本化数据正确加载");
        var cureConditions = catalog.trainer("fixture-cure-conditions-h").orElseThrow().effect();
        if (cureConditions.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.CURE_OWN_ACTIVE_SPECIAL_CONDITIONS || cureConditions.amount() != 0)
            throw new AssertionError("解除异常状态训练家效果没有从版本化数据正确加载");
        var opponentSwitch = catalog.trainer("fixture-opponent-switch-h").orElseThrow().effect();
        if (opponentSwitch.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.SWITCH_OPPONENT_ACTIVE || !opponentSwitch.targetsOpponentPokemon())
            throw new AssertionError("强制交换对方战斗宝可梦训练家效果没有从版本化数据正确加载");
        var evolveFromHand = catalog.trainer("fixture-evolve-hand-h").orElseThrow().effect();
        if (evolveFromHand.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.EVOLVE_OWN_POKEMON_FROM_HAND
            || !evolveFromHand.needsEvolutionCardSelection()
            || !catalog.pokemon("fixture-effect-evolution-stage1-h").orElseThrow().evolvesFromDefinitionId().equals("fixture-effect-evolution-base-h"))
            throw new AssertionError("手牌进化训练家效果或进化来源没有从版本化数据正确加载");
        var skipStage = catalog.trainer("fixture-evolve-skip-stage-h").orElseThrow().effect();
        if (skipStage.opcode() != dev.hua.ptcglive.rules.TrainerEffect.Opcode.EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND
            || !skipStage.requiresSkippedEvolutionStage()
            || !catalog.pokemon("fixture-effect-evolution-stage2-h").orElseThrow().evolvesFromDefinitionId().equals("fixture-effect-evolution-stage1-h"))
            throw new AssertionError("跳过一阶进化训练家效果或二阶进化来源没有从版本化数据正确加载");
        var coinCondition = catalog.pokemon("fixture-coin-condition-h").orElseThrow()
            .attack("fixture-coin-paralyze").orElseThrow().conditions().getFirst();
        if (coinCondition.target() != dev.hua.ptcglive.engine.field.ConditionEffect.Target.DEFENDER
            || coinCondition.condition() != SpecialCondition.PARALYZED
            || coinCondition.coinCondition() == null || coinCondition.coinCondition().flips() != 2
            || coinCondition.coinCondition().requiredHeads() != 1)
            throw new AssertionError("攻击异常状态硬币条件没有从版本化数据正确加载");
        if (catalog.pokemon("fixture-unsupported-h").isPresent())
            throw new AssertionError("未建模卡牌不应获得臆测的战斗数据");
        System.out.println("CombatCatalogTest passed: 数据驱动的 HP、攻击、能量、弱点、奖赏卡与异常状态硬币条件均已验证");
    }
}
