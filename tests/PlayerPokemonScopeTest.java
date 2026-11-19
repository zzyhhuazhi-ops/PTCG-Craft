import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/** Player-initiated programs must share the same scoped targets without spending invalid activations. */
public final class PlayerPokemonScopeTest {
    static final UUID A=ResumableAbilityTest.A,B=ResumableAbilityTest.B;
    static final UUID TA=new UUID(0,7561),TB=new UUID(0,7562);
    static final String HEAL="""
        {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","effects":[{"type":"heal","target":"CHOSEN_POKEMON","amount":10}]}
        """;
    public static void main(String[] args) {
        abilityEligibility(); trainerEligibility(); nestedTrainer(); attackAfterDamage(); sourceProtection(); coinPotential();
        legacySelectionPreserved(); sourceKinds(); unsupportedBranches(); boundedAnalysis();
        System.out.println("PlayerPokemonScopeTest passed: activation potential, scoped choices and source-kind isolation");
    }
    static void abilityEligibility() {
        var ability=ResumableAbilityTest.ability("scope",HEAL);var m=ResumableAbilityTest.fixture(ability);UUID source=m.activePokemonView(A).pokemonId();
        var view=m.clientView(A);var random=m.randomTranscript();
        check(!m.canUseAbility(A,source,ability.id()),"全场无伤害也允许使用纯回复特性");
        reject(()->m.useAbility(A,source,ability.id()));check(view.equals(m.clientView(A))&&random.equals(m.randomTranscript()),"无效特性消耗状态或次数");
        UUID bench=m.benchPokemonView(A).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,bench,2);
        check(m.canUseAbility(A,source,ability.id()),"合法目标出现后特性资格未恢复");
        m.useAbility(A,source,ability.id());check(m.hasPendingPokemonSelection(),"主动特性未等待场上选择");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,A,B),A,bench);
        check(m.benchPokemonView(A).getFirst().damage()==10&&!m.canUseAbility(A,source,ability.id()),"所选回复或成功后限次错误");
    }
    static void trainerEligibility() {
        var m=trainerGame(HEAL);var card=card(m);var view=m.clientView(TA);var random=m.randomTranscript();
        reject(()->m.playTrainer(TA,card.instanceId(),null));
        check(view.equals(m.clientView(TA))&&random.equals(m.randomTranscript()),"无效果训练家已离开手牌");
        UUID target=m.benchPokemonView(TA).getFirst().pokemonId();m.placeDamageCountersByEffect(TA,TA,target,2);
        m.playTrainer(TA,card.instanceId(),null);
        check(m.hasPendingPokemonSelection()&&!m.privateView(TA).discard().contains(card),"选择前训练家过早入弃牌区");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,TA,TB),TA,target);
        check(m.privateView(TA).discard().contains(card)&&m.benchPokemonView(TA).getFirst().damage()==10,"完成后未弃置训练家或正确回复");
    }
    static void nestedTrainer() {
        var m=trainerGame("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","effects":[
              {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
              {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":2}]},
              {"type":"conditional","conditions":[{"type":"DAMAGE_COUNTER_COUNT","target":"CHOSEN_POKEMON","value":1}],"effects":[{"type":"draw","amount":1}]}]}
            """);
        var s=MatchSession.multiplayer(m,TA,TB);var card=card(m);int hand=m.privateView(TA).hand().size();
        m.playTrainer(TA,card.instanceId(),null);AutomaticPokemonSelectionTest.choose(s,TA,m.benchPokemonView(TB).getFirst().pokemonId());
        AutomaticPokemonSelectionTest.choose(s,TA,m.benchPokemonView(TA).getFirst().pokemonId());
        check(m.benchPokemonView(TB).getFirst().damage()==10&&m.benchPokemonView(TA).getFirst().damage()==20&&m.privateView(TA).hand().size()==hand,"训练家嵌套作用域丢失对象或手牌投影");
    }
    static void attackAfterDamage() {
        var attack=ResumableAttackTest.attack("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":2}]}
            """);
        var m=ResumableAttackTest.fixture(attack);UUID actor=m.currentPlayer(),opponent=m.clientView(actor).players().stream().map(p->p.id()).filter(id->!id.equals(actor)).findFirst().orElseThrow();
        int turn=m.turnNumber();m.attack(actor,attack.id());
        check(m.hasPendingPokemonSelection()&&m.turnNumber()==turn,"攻击伤害后的选择过早结束回合");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,actor,opponent),actor,m.benchPokemonView(opponent).getFirst().pokemonId());
        check(m.benchPokemonView(opponent).getFirst().damage()==20&&m.turnNumber()==turn+1,"攻击后目标作用域没有恢复回合推进");
    }
    static void sourceProtection() {
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY"));
        var effect=ResumableAbilityTest.ability("scope","{\"type\":\"select_pokemon\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":2}]}");
        var m=ResumableAbilityTest.fixture(guard,effect);m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),"guard");m.endTurn(B);
        m.useAbility(A,m.activePokemonView(A).pokemonId(),"scope");
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,A,B),A,m.activePokemonView(B).pokemonId());
        check(m.activePokemonView(B).damage()==0,"主动作用域丢失ABILITY免疫来源");
    }
    static void coinPotential() {
        var ability=ResumableAbilityTest.ability("scope","""
            {"type":"coin_flip","flips":1,"effects":[{"type":"select_pokemon","target":"SELECT_OWN_POKEMON","effects":[
              {"type":"conditional","conditions":[{"type":"COIN_RESULT","value":"HEADS"}],"effects":[{"type":"heal","target":"CHOSEN_POKEMON","amount":10}]}]}]}
            """);
        var m=ResumableAbilityTest.fixture(ability);UUID source=m.activePokemonView(A).pokemonId();var random=m.randomTranscript();
        check(!m.canUseAbility(A,source,"scope")&&m.randomTranscript().equals(random),"可用性分析重掷硬币或允许无效果");
        m.placeDamageCountersByEffect(A,A,source,1);
        check(m.canUseAbility(A,source,"scope")&&m.randomTranscript().equals(random),"硬币分支下选择目标不能静态分析");
    }
    static void legacySelectionPreserved() {
        var m=trainerGame("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","effects":[
              {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
              {"type":"heal","target":"SELECT_OWN_POKEMON","amount":10}]},
            {"type":"heal","target":"SELECT_OWN_POKEMON","amount":10}
            """);
        UUID own=m.benchPokemonView(TA).getFirst().pokemonId(),opponent=m.benchPokemonView(TB).getFirst().pokemonId();
        m.placeDamageCountersByEffect(TA,TA,own,3);m.playTrainer(TA,card(m).instanceId(),own);
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,TA,TB),TA,opponent);
        check(m.benchPokemonView(TA).getFirst().damage()==10&&m.benchPokemonView(TB).getFirst().damage()==10,"子上下文覆盖了旧版明确预选目标");
    }
    static void sourceKinds() {
        for(String blocked:List.of("ABILITY","TRAINER")) {
            var m=trainerGame(EffectOriginProtectionTest.shield(blocked,"EFFECTS_ONLY","OPPONENT_ACTIVE","LEAVES_PLAY")+","+"""
                {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"ACTIVE","effects":[
                  {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":2}]}
                """);
            m.playTrainer(TA,card(m).instanceId(),null);AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,TA,TB),TA,m.activePokemonView(TB).pokemonId());
            check(m.activePokemonView(TB).damage()==(blocked.equals("TRAINER")?0:20),"训练家固定目标错误继承特性来源");
        }
        for(String blocked:List.of("ABILITY","ATTACK")) {
            var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield(blocked,"EFFECTS_ONLY","SELF","LEAVES_PLAY"));
            var attack=ResumableAttackTest.attack("""
                {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"ACTIVE","effects":[
                  {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":2}]}
                """);
            var m=EffectOriginProtectionTest.attackFixture(guard,attack);m.useAbility(A,m.activePokemonView(A).pokemonId(),"guard");m.endTurn(A);
            m.attack(B,attack.id());int damage=m.activePokemonView(A).damage();AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,A,B),B,m.activePokemonView(A).pokemonId());
            check(m.activePokemonView(A).damage()==damage+(blocked.equals("ATTACK")?0:20),"攻击固定目标没有保留ATTACK免疫类别");
        }
    }
    static void unsupportedBranches() {
        for(String invalid:List.of(
            "{\"type\":\"heal\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}",
            "{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"effects\":[{\"type\":\"heal\",\"target\":\"CHOSEN_POKEMON\",\"amount\":7}]}")) {
            var ability=ResumableAbilityTest.ability("bad","{\"type\":\"draw\",\"amount\":1},"+invalid);
            var m=ResumableAbilityTest.fixture(ability);var view=m.clientView(A);var random=m.randomTranscript();
            reject(()->m.useAbility(A,m.activePokemonView(A).pokemonId(),ability.id()));
            check(view.equals(m.clientView(A))&&random.equals(m.randomTranscript()),"非法绑定或分支未在抽牌前拒绝");
        }
        var scope=EffectRegistry.standard().parse(com.google.gson.JsonParser.parseString(HEAL).getAsJsonObject());
        var before=ResumableAttackTest.attack(HEAL).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(scope)))));
        check(AttackProgramRules.unsupportedReason(before).isPresent(),"攻击伤害前选择被错误宣称已支持");
    }
    static void boundedAnalysis() {
        String effects="{\"type\":\"heal\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}";
        for(int depth=0;depth<15;depth++)effects="{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"effects\":["+effects+"]}";
        var ability=ResumableAbilityTest.ability("bounded",effects);var m=ResumableAbilityTest.fixture(ability);var view=m.clientView(A);var random=m.randomTranscript();
        try {m.useAbility(A,m.activePokemonView(A).pokemonId(),ability.id());throw new AssertionError("指数候选分析未受限");}
        catch(IllegalArgumentException expected) {check(expected.getMessage().contains("安全步数"),"不是全局分析预算拒绝："+expected.getMessage());}
        check(view.equals(m.clientView(A))&&random.equals(m.randomTranscript()),"分析超过预算仍然消耗状态");
    }
    static ServerMatch trainerGame(String effects) {return ComposedTrainerIntegrationTest.fixture(ComposedTrainerIntegrationTest.item(effects));}
    static dev.hua.ptcglive.rules.CardInstance card(ServerMatch m) {return m.privateView(TA).hand().stream().filter(c->c.definitionId().equals("component")).findFirst().orElseThrow();}
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    static void reject(Runnable action) {EventTargetBindingTest.reject(action);}
}
