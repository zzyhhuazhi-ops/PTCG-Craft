import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.ResumableProgramRules;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.engine.field.LeavePlayDestination;
import java.util.List;
import java.util.UUID;

/** Data-driven SwitchEffect and ForceSwitchEffect use only issued public Bench choices. */
public final class ComposedSwitchEffectTest {
    static final UUID A=ResumableAbilityTest.A,B=ResumableAbilityTest.B;
    public static void main(String[] args) {
        ownSwitch(); forcedSwitchUsesAffectedOwner(); noBenchAndMalformedDataFailClosed();
        System.out.println("ComposedSwitchEffectTest passed: JSON switch choices, affected-owner force switch and stale/no-bench safety");
    }
    static AbilityDefinition ability(String id,String effect) { return ResumableAbilityTest.ability(id,effect); }
    static void ownSwitch() {
        var effect=ability("switch","{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}");
        var m=ResumableAbilityTest.fixture(effect);UUID former=m.activePokemonView(A).pokemonId(),replacement=m.benchPokemonView(A).getFirst().pokemonId();
        m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        m.useAbility(A,former,effect.id());
        check(m.pendingChoicePlayer().equals(A)&&m.hasPendingPokemonSelection(),"己方换位没有等待发动方选择备战位");
        var prompt=m.effectPokemonPrompt(A);check(prompt.targets().size()==1&&prompt.targets().getFirst().pokemonId().equals(replacement),"SwitchEffect 候选包含战斗位或错误阵营");
        rejects(()->m.effectPokemonPrompt(B));
        m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),replacement);
        check(m.activePokemonView(A).pokemonId().equals(replacement)
            &&m.benchPokemonView(A).stream().anyMatch(pokemon->pokemon.pokemonId().equals(former))
            &&m.benchPokemonView(A).stream().filter(pokemon->pokemon.pokemonId().equals(former)).findFirst().orElseThrow().conditions().isEmpty(),
            "SwitchEffect 没有交换场位或清除离开战斗位的异常状态");
    }
    static void forcedSwitchUsesAffectedOwner() {
        var effect=ability("force","{\"type\":\"force_switch\",\"target\":\"SELECT_OPPONENT_POKEMON\"}");
        var m=ResumableAbilityTest.fixture(effect);var session=MatchSession.multiplayer(m,A,B);
        UUID former=m.activePokemonView(B).pokemonId(),replacement=m.benchPokemonView(B).getFirst().pokemonId();
        m.useAbility(A,m.activePokemonView(A).pokemonId(),effect.id());
        check(m.pendingChoicePlayer().equals(B)&&m.hasPendingPokemonSelection(),"ForceSwitchEffect 没有把选择权交给受影响方");
        rejects(()->m.effectPokemonPrompt(A));
        var prompt=m.effectPokemonPrompt(B);check(prompt.targets().size()==1&&prompt.targets().getFirst().pokemonId().equals(replacement),"强制换位候选错误泄露或包含战斗位");
        rejects(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),replacement));
        var view=session.view(B,B);check(view.options().stream().anyMatch(option->option.labelZh().startsWith("选择效果目标")
            &&option.cardKeys().contains("field:"+replacement)),"受影响方没有收到服务器签发的强制换位操作");
        m.chooseEffectPokemon(B,prompt.resolutionId(),prompt.step(),replacement);
        check(m.activePokemonView(B).pokemonId().equals(replacement)
            &&m.benchPokemonView(B).stream().anyMatch(pokemon->pokemon.pokemonId().equals(former)),"ForceSwitchEffect 没有交换对方公开场位");
    }
    static void noBenchAndMalformedDataFailClosed() {
        var effect=ability("force","{\"type\":\"force_switch\",\"target\":\"SELECT_OPPONENT_POKEMON\"}");
        var m=ResumableAbilityTest.fixture(effect);UUID source=m.activePokemonView(A).pokemonId();
        m.movePokemonByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId(),LeavePlayDestination.HAND);
        check(!m.canUseAbility(A,source,effect.id()),"没有对方备战位仍允许发动无作用 ForceSwitchEffect");
        rejects(()->m.useAbility(A,source,effect.id()));
        for(String invalid:List.of(
            "{\"type\":\"switch\",\"target\":\"SELECT_OPPONENT_POKEMON\"}",
            "{\"type\":\"force_switch\",\"target\":\"SELECT_OWN_POKEMON\"}",
            "{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\",\"cost\":1}")) {
            var bad=ability("bad",invalid);
            check(ResumableProgramRules.unsupportedReason(bad.effectProgram(),true).isPresent(),"非法通用换位数据被静默接受："+invalid);
        }
    }
    static void rejects(Runnable op) { try {op.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("非法换位选择未被服务器拒绝"); }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
