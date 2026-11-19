import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “Once during your turn, you may search your deck for a Basic {L} Energy card and attach it to this Pokémon.”:
 * attach_energy target SELF is the Pokémon whose Ability it is, Active or Benched; no host prompt is offered.
 * Ability conditions such as “if your opponent has 4 or fewer Prize cards remaining” or “if this Pokémon is on your
 * Bench” gate whether the Ability can be used. A Trainer has no “this Pokémon”.
 */
public final class AbilityAttachSelfTest {
    static final UUID A = EnergyTriggeredAbilityTest.A;
    static final String FROM_DECK = "{\"type\":\"attach_energy\",\"target\":\"SELF\",\"source\":\"DECK\",\"amount\":1,\"minimum\":0,"
        + "\"target_scope\":\"OWN_FIELD\",\"basic_energy_only\":true,\"energy_type\":\"GRASS\"}";
    public static void main(String[] args) {
        benchedAndActive(); prizeCondition(); benchOnly(); invalid();
        System.out.println("AbilityAttachSelfTest passed: 特性把能量附到这只宝可梦（战斗位或备战区），不再让玩家选目标");
    }
    static void benchedAndActive() {
        var ability = ResumableAbilityTest.ability("charge", FROM_DECK);
        check(ActivatedAbilityRules.unsupportedReason(ability).isEmpty(), "附到这只宝可梦被拒绝：" + ActivatedAbilityRules.unsupportedReason(ability).orElse(""));
        for (boolean bench : List.of(true, false)) {
            var m = EnergyTriggeredAbilityTest.fixture(List.of(ability), List.of(), null);
            UUID self = bench ? m.benchPokemonView(A).getFirst().pokemonId() : m.activePokemonView(A).pokemonId();
            UUID other = bench ? m.activePokemonView(A).pokemonId() : m.benchPokemonView(A).getFirst().pokemonId();
            int deck = m.privateView(A).deck().size();
            m.useAbility(A, self, ability.id());
            var pick = m.effectCardPrompt(A);
            m.submitEffectCards(A, pick.resolutionId(), pick.step(), List.of(pick.candidates().getFirst().instanceId()));
            check(!m.hasPendingAttachmentTarget(), "附到这只宝可梦不应再让玩家选目标");
            check(energy(m, self) == 1 && energy(m, other) == 0, (bench ? "备战区" : "战斗位") + "的这只宝可梦没有得到能量");
            check(m.privateView(A).deck().size() == deck - 1 && !m.hasPendingEffectChoice(), "牌库或续接状态不对：牌库 " + deck + "→" + m.privateView(A).deck().size() + " 续接 " + m.hasPendingEffectChoice());
            check(!m.canUseAbility(A, self, ability.id()), "每回合一次的特性被重复使用");
        }
    }
    /** Sandy Shocks ex: “if your opponent has 4 or fewer Prize cards remaining, you may attach … from your discard pile to this Pokémon.” */
    static void prizeCondition() {
        var ability = ResumableAbilityTest.ability("absorb", "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"PRIZE_COUNT\",\"target\":\"OPPONENT_ACTIVE\","
            + "\"value\":4,\"comparison\":\"<=\"}],\"effects\":[" + FROM_DECK + "]}");
        check(ActivatedAbilityRules.unsupportedReason(ability).isEmpty(), "带奖赏卡条件的附能特性被拒绝：" + ActivatedAbilityRules.unsupportedReason(ability).orElse(""));
        var m = EnergyTriggeredAbilityTest.fixture(List.of(ability), List.of(), null);
        check(!m.canUseAbility(A, m.activePokemonView(A).pokemonId(), ability.id()), "对手还有 6 张奖赏卡时不能使用");
    }
    /** Solgaleo: “if this Pokémon is on your Bench, you may use this Ability.” */
    static void benchOnly() {
        var ability = ResumableAbilityTest.ability("sunrise", "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"NOT\",\"conditions\":"
            + "[{\"type\":\"IS_ACTIVE\",\"target\":\"SELF\"}]}],\"effects\":[" + FROM_DECK + "]}");
        check(ActivatedAbilityRules.unsupportedReason(ability).isEmpty(), "只在备战区可用的附能特性被拒绝：" + ActivatedAbilityRules.unsupportedReason(ability).orElse(""));
        var m = EnergyTriggeredAbilityTest.fixture(List.of(ability), List.of(), null);
        check(!m.canUseAbility(A, m.activePokemonView(A).pokemonId(), ability.id()), "战斗位上不能使用");
        check(m.canUseAbility(A, m.benchPokemonView(A).getFirst().pokemonId(), ability.id()), "备战区可以使用");
    }
    static void invalid() {
        var trainer = new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED, FROM_DECK)));
        check(TrainerProgramRules.unsupportedReason(trainer).isPresent(), "训练家没有“这只宝可梦”");
        for (String bad : List.of(FROM_DECK.replace("OWN_FIELD", "OWN_ACTIVE"), FROM_DECK.replace("\"minimum\":0", "\"minimum\":0,\"distribution\":\"ANY\"")))
            check(ActivatedAbilityRules.unsupportedReason(ResumableAbilityTest.ability("bad", bad)).isPresent(), "非法附能数据被放行：" + bad);
    }
    static int energy(ServerMatch m, UUID pokemon) {
        var active = m.activePokemonView(A);
        if (active.pokemonId().equals(pokemon)) return active.attachedEnergyIds().size();
        return m.benchPokemonView(A).stream().filter(p -> p.pokemonId().equals(pokemon)).findFirst().orElseThrow().attachedEnergyIds().size();
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
