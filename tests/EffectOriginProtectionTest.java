import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real card programs must honor source-kind protection, without treating counters as attack damage. */
public final class EffectOriginProtectionTest {
    static final UUID A=ResumableAbilityTest.A,B=ResumableAbilityTest.B;
    public static void main(String[]args){ matrix(); abilityAndTrainerSources(); allocationAndContinuation(); durationAndMalformed(); attackScopes(); benchTargetsIndependently(); guardedAtomicEffects(); blockedDeckAttachmentStillShuffles();
        System.out.println("EffectOriginProtectionTest passed: source/impact matrix, actual abilities/trainers, counters, expiry and preflight"); }
    static void matrix(){
        var opponentAbility=new EffectOrigin(EffectOrigin.Kind.ABILITY,B,new UUID(0,1));
        var opponentAttack=new EffectOrigin(EffectOrigin.Kind.ATTACK,B,new UUID(0,2));
        var policy=new EffectProtection(EffectProtection.Source.ABILITY,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY);
        check(policy.blocks(opponentAbility,A,EffectProtection.Impact.OTHER_EFFECTS),"来源特性免疫未应用");
        check(!policy.blocks(opponentAttack,A,EffectProtection.Impact.OTHER_EFFECTS),"特性免疫错误阻止攻击效果");
        check(!policy.blocks(opponentAbility,B,EffectProtection.Impact.OTHER_EFFECTS),"对手免疫错误阻止己方特性");
        check(!policy.blocks(opponentAbility,A,EffectProtection.Impact.DAMAGE),"效果免疫错误阻止伤害");
        var damage=new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.DAMAGE_ONLY);
        check(damage.blocks(opponentAttack,A,EffectProtection.Impact.DAMAGE)&&!damage.blocks(opponentAttack,A,EffectProtection.Impact.OTHER_EFFECTS),"伤害保护覆盖错误");
    }
    static String shield(String source,String scope,String target,String duration){return "{\"type\":\"prevent_effects\",\"target\":\""+target+"\",\"source\":\""+source+"\",\"from\":\"OPPONENT\",\"scope\":\""+scope+"\",\"duration\":\""+duration+"\"}";}
    static void abilityAndTrainerSources(){
        var guard=ResumableAbilityTest.ability("guard",shield("ABILITY","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var hit=ResumableAbilityTest.ability("hit","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2},{\"type\":\"draw\",\"amount\":1}");
        var m=ResumableAbilityTest.fixture(guard,hit);UUID target=m.activePokemonView(A).pokemonId();
        m.useAbility(A,target,guard.id());m.endTurn(A);int hand=m.privateView(B).hand().size();
        m.useAbility(B,m.activePokemonView(B).pokemonId(),hit.id());
        check(m.activePokemonView(A).damage()==0&&m.privateView(B).hand().size()==hand+1,"免疫没有逐目标阻止特性，或吞掉后续抽牌");
        // Rule hooks without an attributed card are not mislabelled as opponent Abilities.
        m.placeDamageCountersByEffect(B,A,target,1);check(m.activePokemonView(A).damage()==10,"来源从特性泄露到了随后规则动作");
        check(m.activePokemonView(A).continuousEffectZh().contains("特性"),"公共说明未显示来源类型");
        m.endTurn(B);check(m.activePokemonView(A).continuousEffectZh().isBlank(),"防护未按来源回合到期");
        var trainer=ComposedTrainerIntegrationTest.item(shield("ABILITY","EFFECTS_ONLY","OPPONENT_ACTIVE","SOURCE_NEXT_TURN_START")+",{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}");
        var tm=ComposedTrainerIntegrationTest.fixture(trainer);UUID a=new UUID(0,7561),b=new UUID(0,7562);
        var card=tm.privateView(a).hand().stream().filter(c->c.definitionId().equals("component")).findFirst().orElseThrow();
        tm.playTrainer(a,card.instanceId(),null);check(tm.activePokemonView(b).damage()==20,"训练家效果冒充特性而被错误拦截");
    }
    static void allocationAndContinuation(){
        var guard=ResumableAbilityTest.ability("guard",shield("ABILITY","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var split=ResumableAbilityTest.ability("split","""
            {"type":"damage_counter","amount":4,"distribution":"EXACT","target":"SELECT_OPPONENT_POKEMON","target_scope":"OPPONENT_FIELD","min_targets":2,"max_targets":2},
            {"type":"draw","amount":1}
            """);
        var m=ResumableAbilityTest.fixture(guard,split);UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.useAbility(A,active,guard.id());m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),split.id());
        var p=m.damageAllocationPrompt(B);
        m.submitDamageAllocation(B,p.resolutionId(),p.step(),List.of(new ServerMatch.DamageCounterPlacement(A,active,2),new ServerMatch.DamageCounterPlacement(A,bench,2)));
        check(m.activePokemonView(A).damage()==0&&m.benchPokemonView(A).getFirst().damage()==20,"暂停后的来源丢失，或一目标免疫取消了整组");
    }
    static void durationAndMalformed(){
        var guard=ResumableAbilityTest.ability("guard",shield("ANY_CARD","DAMAGE_AND_EFFECTS","SELF","LEAVES_PLAY"));
        var m=ResumableAbilityTest.fixture(guard);UUID target=m.activePokemonView(A).pokemonId();m.useAbility(A,target,guard.id());
        m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());
        check(m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(target)).findFirst().orElseThrow().continuousEffectZh().isBlank(),"换到备战未清除临时防护");
        var bad=ResumableAbilityTest.ability("bad","{\"type\":\"draw\",\"amount\":1},"+shield("FAKE","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var rejected=ResumableAbilityTest.fixture(bad);var before=rejected.privateView(A);
        ResumableAbilityTest.rejects(()->rejected.useAbility(A,rejected.activePokemonView(A).pokemonId(),bad.id()));
        check(before.equals(rejected.privateView(A)),"错误防护参数先执行了抽牌前缀");
    }
    static void attackScopes(){
        for(String scope:List.of("DAMAGE_ONLY","EFFECTS_ONLY","DAMAGE_AND_EFFECTS")) {
            var guard=ResumableAbilityTest.ability("guard",shield("ATTACK",scope,"SELF","SOURCE_NEXT_TURN_START"));
            var strike=new AttackDefinition("strike","中毒攻击",Map.of(),30,EnergyType.GRASS,EffectSupport.SUPPORTED,null,
                List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER,SpecialCondition.POISONED)));
            var m=attackFixture(guard,strike);m.useAbility(A,m.activePokemonView(A).pokemonId(),guard.id());m.endTurn(A);m.attack(B,"strike");
            int damage=scope.equals("EFFECTS_ONLY")?60:0;
            check(m.lastAttack().finalDamage()==damage,"伤害与弱点被效果防护错误抹掉："+scope);
            check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED)==scope.equals("DAMAGE_ONLY"),"伤害与状态防护混淆："+scope);
        }
    }
    static void benchTargetsIndependently(){
        var guard=ResumableAbilityTest.ability("guard",shield("ATTACK","DAMAGE_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var spread=new AttackDefinition("spread","备战攻击",Map.of(),30,EnergyType.GRASS,EffectSupport.SUPPORTED,null,
            List.of(),null,null,List.of(),AttackDefinition.DamageTarget.ALL_OPPONENT_BENCH,0);
        var m=attackFixture(guard,spread);UUID first=m.benchPokemonView(A).getFirst().pokemonId();
        m.useAbility(A,first,guard.id());m.endTurn(A);m.attack(B,"spread");
        check(m.benchPokemonView(A).getFirst().damage()==0&&m.benchPokemonView(A).get(1).damage()==30,"首个备战免疫错误取消其他目标伤害");
        check(!m.lastAttack().weaknessApplied(),"备战伤害错误计算弱点");
    }
    static ServerMatch attackFixture(AbilityDefinition guard,AttackDefinition attack){
        var card=new CardDefinition("basic","来源防护夹具",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null);
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(200,0,null,EnergyType.GRASS,2,EnergyType.FIRE,30,1,List.of(attack),List.of(guard))),Map.of());
        var m=new ServerMatch(A,B,ResumableAbilityTest.deck(A),ResumableAbilityTest.deck(B),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(List.of(card)),combat,new ServerRandom(92));
        OpeningTestSupport.complete(m,A,B,2);if(m.currentPlayer().equals(B))m.endTurn(B);return m;
    }
    static void guardedAtomicEffects(){
        for(String effect:List.of("{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}",
            "{\"type\":\"return_to_hand\",\"target\":\"OPPONENT_ACTIVE\"}",
            "{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"}")) {
            var guard=ResumableAbilityTest.ability("guard",shield("ABILITY","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
            var attack=ResumableAbilityTest.ability("effect",effect);var m=ResumableAbilityTest.fixture(guard,attack);
            UUID protectedId=m.activePokemonView(A).pokemonId();m.useAbility(A,protectedId,guard.id());m.endTurn(A);
            m.useAbility(B,m.activePokemonView(B).pokemonId(),attack.id());
            check(m.phase()==MatchPhase.TURN&&m.activePokemonView(A).pokemonId().equals(protectedId)
                &&m.activePokemonView(A).conditions().isEmpty(),"非伤害原子效果绕过来源免疫："+effect);
            var before=m.clientView(B);ResumableAbilityTest.rejects(()->m.useAbility(B,m.activePokemonView(B).pokemonId(),attack.id()));
            check(before.equals(m.clientView(B)),"被防护阻止后可重复发动");
        }
        var guard=ResumableAbilityTest.ability("guard",shield("ATTACK","EFFECTS_ONLY","SELF","SOURCE_TURN_END"));
        var m=ResumableAbilityTest.fixture(guard);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        m.useAbility(A,m.activePokemonView(A).pokemonId(),guard.id());
        check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"新防护错误清除既有状态");
        m.endTurn(A);check(m.activePokemonView(A).continuousEffectZh().isBlank(),"回合末防护未到期");
    }
    static void blockedDeckAttachmentStillShuffles(){
        UUID actor=EnergyTriggeredAbilityTest.A;
        var guard=ResumableAbilityTest.ability("own-guard",shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY").replace("OPPONENT","OWN"));
        var attach=ResumableAbilityTest.ability("attach","{\"type\":\"attach_energy\",\"target\":\"OWN_ACTIVE\",\"source\":\"DECK\",\"amount\":1,\"minimum\":1,\"target_scope\":\"OWN_ACTIVE\"}");
        var trigger=EnergyTriggeredAbilityTest.ability("must-not-trigger","SELF",false,"NONE","{\"type\":\"draw\",\"amount\":1}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(guard,attach,trigger),List.of(),null);
        UUID active=m.activePokemonView(actor).pokemonId();m.useAbility(actor,active,guard.id());
        var before=m.privateView(actor);int randomBefore=m.randomTranscript().size();
        m.useAbility(actor,active,attach.id());var pick=m.effectCardPrompt(actor);
        UUID energy=pick.candidates().getFirst().instanceId();
        m.submitEffectCards(actor,pick.resolutionId(),pick.step(),List.of(energy));
        var target=m.effectAttachmentPrompt(actor);m.chooseEffectAttachmentTarget(actor,target.resolutionId(),target.step(),active);
        check(m.activePokemonView(actor).attachedEnergyIds().isEmpty(),"被防护阻止的附能仍发生");
        check(m.privateView(actor).deck().size()==before.deck().size()&&m.privateView(actor).deck().stream().anyMatch(c->c.instanceId().equals(energy)),"被阻止的能量离开牌库");
        check(m.privateView(actor).hand().equals(before.hand())&&m.triggerOrderPrompt()==null,"未实际附着也触发了抽牌");
        check(m.randomTranscript().size()>randomBefore,"被防护阻止附着后，检索牌库仍须洗牌");
        check(!m.hasPendingEffectChoice()&&!m.canUseAbility(actor,active,attach.id()),"被阻止后没有结束续接或消耗使用次数");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
