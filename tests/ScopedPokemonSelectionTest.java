import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Lexical chosen-Pokemon identity survives later choices without becoming client authority. */
public final class ScopedPokemonSelectionTest {
    static final UUID A=EnergyTriggeredAbilityTest.A,B=EnergyTriggeredAbilityTest.B;
    public static void main(String[] args) {
        nestedScopes(); identityAndConditions(false); identityAndConditions(true); activeAndEmpty(); invalidPrograms(); otherEventsAndCoins();
        System.out.println("ScopedPokemonSelectionTest passed: lexical target scope, live conditions and incarnation safety");
    }
    static ServerMatch game(String effects) {
        return EnergyTriggeredAbilityTest.fixture(List.of(EnergyTriggeredAbilityTest.ability("scope","SELF",false,"NONE",effects)),List.of(),null);
    }
    static void nestedScopes() {
        var m=game("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","effects":[
              {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
              {"type":"choice","effects":[{"type":"heal","target":"CHOSEN_POKEMON","amount":10},{"type":"draw","amount":1}]},
              {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":2}]},
              {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":3},
              {"type":"conditional","conditions":[{"type":"AND","conditions":[
                {"type":"NOT","conditions":[{"type":"IS_ACTIVE","target":"CHOSEN_POKEMON"}]},
                {"type":"NOT","conditions":[{"type":"IS_EVOLVED","target":"CHOSEN_POKEMON"}]},
                {"type":"DAMAGE_COUNTER_COUNT","target":"CHOSEN_POKEMON","value":3,"comparison":"=="}]}],"effects":[{"type":"draw","amount":1}]}]},
            {"type":"heal","target":"SELF","amount":10}
            """);
        var s=MatchSession.multiplayer(m,A,B);m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),1);
        int hand=m.privateView(A).hand().size();AutomaticPokemonSelectionTest.attach(m);
        check(m.effectPokemonPrompt(A).targets().size()==1,"BENCH范围未过滤战斗位");
        AutomaticPokemonSelectionTest.choose(s,A,m.benchPokemonView(B).getFirst().pokemonId());chooseBranch(m,0);
        AutomaticPokemonSelectionTest.choose(s,A,m.benchPokemonView(A).getFirst().pokemonId());
        check(m.benchPokemonView(B).getFirst().damage()==30&&m.benchPokemonView(A).getFirst().damage()==20,"内层目标覆盖了外层目标");
        check(m.activePokemonView(A).damage()==0&&m.activePokemonView(B).damage()==0,"作用域污染了原来源或战斗位");
        check(m.privateView(A).hand().size()==hand&&!m.hasPendingEffectChoice(),"选择后条件未读取已选对象的实时伤害");
    }
    static void identityAndConditions(boolean reenter) {
        var m=game("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","effects":[
              {"type":"choice","effects":[
                {"type":"conditional","conditions":[{"type":"NOT","conditions":[{"type":"IS_ACTIVE","target":"CHOSEN_POKEMON"}]}],"effects":[{"type":"draw","amount":2}]},
                {"type":"draw","amount":1}]},
              {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":2},
              {"type":"draw","amount":1}]}
            """);
        var s=MatchSession.multiplayer(m,A,B);AutomaticPokemonSelectionTest.attach(m);UUID old=m.activePokemonView(B).pokemonId(),physical=PokemonIdentityTestSupport.physical(m,B,old);
        AutomaticPokemonSelectionTest.choose(s,A,old);int hand=m.privateView(A).hand().size();var random=m.randomTranscript();
        // Test-only transition. Normal actions are locked while the chosen-target program waits.
        var f=EventTargetBindingTest.field(m);f.switchActiveByEffect(B,f.benchView(B).getFirst().pokemonId());
        if(reenter) {var removed=f.removePokemonByEffect(B,old);f.placeOnBench(B,removed.discardedCards().getFirst());}
        chooseBranch(m,0);
        check(f.pokemonView(B,PokemonIdentityTestSupport.logical(m,B,physical)).damage()==(reenter?0:20),"固定目标错误跟随新战斗位或新登场实例");
        check(m.privateView(A).hand().size()==hand+(reenter?1:3),"已离场目标被NOT条件当作有效数据，或玩家后续抽牌丢失");
        check(random.equals(m.randomTranscript())&&!m.hasPendingEffectChoice(),"目标续接改变随机或未结束");
    }
    static void activeAndEmpty() {
        var m=game("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"ACTIVE","effects":[
              {"type":"poison","target":"CHOSEN_POKEMON"},
              {"type":"conditional","conditions":[{"type":"TARGET_HAS_STATUS","target":"CHOSEN_POKEMON","status":"POISONED"}],"effects":[{"type":"draw","amount":1}]}]}
            """);
        int hand=m.privateView(A).hand().size();AutomaticPokemonSelectionTest.attach(m);
        check(m.effectPokemonPrompt(A).targets().size()==1,"ACTIVE范围包含备战");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,A,B),A,m.activePokemonView(B).pokemonId());
        check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)&&m.privateView(A).hand().size()==hand,"已选目标的状态条件错误");
        var empty=game("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","effects":[{"type":"draw","amount":2}]},
            {"type":"draw","amount":1}
            """);
        var f=EventTargetBindingTest.field(empty);f.removePokemonByEffect(A,f.benchView(A).getFirst().pokemonId());
        int before=empty.privateView(A).hand().size();AutomaticPokemonSelectionTest.attach(empty);
        check(!empty.hasPendingEffectChoice()&&empty.privateView(A).hand().size()==before,"无目标未跳过作用域或丢失外层后续");
    }
    static void invalidPrograms() {
        var bound=EffectRegistry.standard().parse(com.google.gson.JsonParser.parseString("{\"type\":\"heal\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}").getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(bound).capability()==EffectExecutionCapability.REQUIRES_SELECTION,"孤立绑定目标误报为无需上下文即可直接执行");
        for(String text:List.of(
            "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_BASIC\",\"target\":\" chosen_pokemon \"}],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}",
            "{\"type\":\"heal\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}",
            "{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"effects\":[]}",
            "{\"type\":\"select_pokemon\",\"target\":\"SELF\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}",
            "{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"effects\":[{\"type\":\"poison\",\"target\":\"CHOSEN_POKEMON\"}]}",
            "{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"conditions\":[{\"type\":\"IS_BASIC\",\"target\":\"CHOSEN_POKEMON\"}],\"effects\":[{\"type\":\"draw\",\"amount\":1}]}"))
            EventTargetBindingTest.reject(()->game("{\"type\":\"draw\",\"amount\":1},"+text));
    }
    static void chooseBranch(ServerMatch m,int index) {var p=m.effectChoicePrompt(A);m.chooseEffectOption(A,p.resolutionId(),p.step(),index);}
    static void otherEventsAndCoins() {
        UUID a=DamageTriggeredAbilityTest.A,b=DamageTriggeredAbilityTest.B;
        var rescue=DamageTriggeredAbilityTest.ability("scope",true,false,false,"""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","effects":[{"type":"heal","target":"CHOSEN_POKEMON","mode":"MINIMUM_HP","amount":10}]}
            """);
        var damaged=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(500,""),rescue);damaged.attack(a,"hit");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(damaged,a,b),b,damaged.activePokemonView(b).pokemonId());
        check(damaged.activePokemonView(b).damage()==290&&damaged.turnNumber()==4,"固定目标回复破坏伤害检查点");
        var reaction=KnockoutTriggeredAbilityTest.ability("scope","OPPONENT_ATTACK_DAMAGE",true,false,"""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","effects":[{"type":"knock_out","target":"CHOSEN_POKEMON"}]}
            """);
        var ko=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(300,""),reaction);ko.attack(a,"hit");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(ko,a,b),b,ko.benchPokemonView(a).getFirst().pokemonId());
        check(ko.phase()==MatchPhase.PRIZE_SELECTION&&ko.privateView(a).discard().size()==1,"作用域内直接KO没有参与统一检查点");
        var checkup=CheckupTriggeredAbilityTest.ability("scope",false,"""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1}]}
            """);
        var c=CheckupTriggeredAbilityTest.fixture(List.of(checkup),List.of());c.endTurn(A);CheckupTriggeredAbilityTest.choose(c,"scope");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(c,A,B),A,c.benchPokemonView(B).getFirst().pokemonId());
        check(c.turnNumber()==2&&c.benchPokemonView(B).getFirst().damage()==10,"回合间目标作用域未恢复回合");
        var coins=game("""
            {"type":"coin_flip","flips":1,"effects":[{"type":"repeat","count":2,"effects":[
              {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","effects":[
                {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
                {"type":"conditional","conditions":[{"type":"COIN_RESULT","value":"HEADS"}],"effects":[{"type":"draw","amount":1}]}]}]}]}
            """);
        AutomaticPokemonSelectionTest.attach(coins);var random=coins.randomTranscript();int hand=coins.privateView(A).hand().size();var s=MatchSession.multiplayer(coins,A,B);
        AutomaticPokemonSelectionTest.choose(s,A,coins.benchPokemonView(B).getFirst().pokemonId());int once=coins.privateView(A).hand().size()-hand;
        AutomaticPokemonSelectionTest.choose(s,A,coins.activePokemonView(B).pokemonId());
        check(coins.benchPokemonView(B).getFirst().damage()==10&&coins.activePokemonView(B).damage()==10&&coins.privateView(A).hand().size()==hand+2*once&&coins.randomTranscript().equals(random),"重复作用域目标或外层硬币失效");
    }
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
