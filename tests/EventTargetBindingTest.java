import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** An automatic effect binds its event participant, not whatever later occupies the Active Spot. */
public final class EventTargetBindingTest {
    static final UUID A=DamageTriggeredAbilityTest.A,B=DamageTriggeredAbilityTest.B;
    public static void main(String[] args) {
        attachedRecipient(); actualAttacker(); knockoutWatch(); stableAttackerAcrossFieldChanges();
        recipientReentry(); conditionsLatched(); relativeAttackCause(); statusBoundTarget(); invalidBindings(); beforeDamageCaptureReentry(); boundProtection();
        System.out.println("EventTargetBindingTest passed: bound attachment recipient, actual attacker and field KO listeners");
    }
    static AbilityDefinition watcher(String id,String watch,String cause,String effects,String conditions) {
        return BundledCombatData.ability(JsonParser.parseString("""
            {"id":"%s","nameZh":"事件监听%s","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_KNOCKOUT","watch":"%s","cause":"%s","activeOnly":true,"optional":false,"usage":"NONE"},
             "triggers":[{"trigger":"ON_KNOCKOUT","conditions":[%s],"effects":[%s]}]}
            """.formatted(id,id,watch,cause,conditions,effects)).getAsJsonObject(),EffectRegistry.standard());
    }
    static void attachedRecipient() {
        UUID a=EnergyTriggeredAbilityTest.A,b=EnergyTriggeredAbilityTest.B;
        var reaction=EnergyTriggeredAbilityTest.ability("heal","OPPONENT_FIELD",false,"NONE","{\"type\":\"heal\",\"target\":\"TRIGGER_POKEMON\",\"amount\":10}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(),List.of(reaction),null);
        UUID bench=m.benchPokemonView(a).getFirst().pokemonId();
        m.placeDamageCountersByEffect(a,a,bench,3);m.placeDamageCountersByEffect(a,a,m.activePokemonView(a).pokemonId(),3);
        var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.attachEnergy(a,energy.instanceId(),bench);DamageTriggeredAbilityTest.finishTriggers(m);
        check(m.benchPokemonView(a).getFirst().damage()==10&&m.activePokemonView(a).damage()==30&&m.activePokemonView(b).damage()==0,"附能触发没有绑定实际附能对象");
        reject(()->m.resolveEffectTarget(a,m.activePokemonView(a).pokemonId(),EffectTarget.fromWire("TRIGGER_POKEMON")));
    }
    static void actualAttacker() {
        String counter="{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":3}";
        for(boolean ko:List.of(false,true)) {
            var reaction=ko?KnockoutTriggeredAbilityTest.ability("reflect","OPPONENT_ATTACK_DAMAGE",true,false,counter)
                :DamageTriggeredAbilityTest.ability("reflect",true,false,false,counter);
            var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(ko?300:10,""),reaction);m.attack(A,"hit");
            check(m.activePokemonView(A).damage()==30&&m.benchPokemonView(A).getFirst().damage()==0,"反击未绑定服务器实际攻击者");
        }
    }
    static void knockoutWatch() {
        for(String scope:List.of("SELF","OWN_FIELD","OPPONENT_FIELD","ANY_FIELD")) {
            var reaction=watcher("watch",scope,"ANY",DamageTriggeredAbilityTest.DRAW,"{\"type\":\"IS_ACTIVE\",\"target\":\"TRIGGER_POKEMON\"}");
            var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(300,""),reaction);
            int a=m.privateView(A).hand().size(),b=m.privateView(B).hand().size();m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);
            check(m.privateView(A).hand().size()==a+(Set.of("OPPONENT_FIELD","ANY_FIELD").contains(scope)?2:0),"对方场地监听错误："+scope);
            check(m.privateView(B).hand().size()==b+(scope.equals("SELF")?1:Set.of("OWN_FIELD","ANY_FIELD").contains(scope)?2:0),"己方场地监听错误："+scope);
        }
    }
    static BattlefieldState field(ServerMatch match) {
        try { var value=ServerMatch.class.getDeclaredField("battlefield");value.setAccessible(true);return (BattlefieldState)value.get(match); }
        catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static void stableAttackerAcrossFieldChanges() {
        // Synthetic server field changes isolate target identity semantics; not a claim that attack-switch text is enabled.
        for(String mode:List.of("bench","left","reentered")) {
            var reaction=DamageTriggeredAbilityTest.ability("reflect",true,false,true,"{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":3}");
            var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10,""),reaction);
            UUID old=m.activePokemonView(A).pokemonId(),physical=PokemonIdentityTestSupport.physical(m,A,old),replacement=m.benchPokemonView(A).getFirst().pokemonId();m.attack(A,"hit");var prompt=m.triggerOrderPrompt();
            reject(()->m.switchActiveByEffect(A,A,replacement));
            var field=field(m);field.switchActiveByEffect(A,replacement);
            if(!mode.equals("bench")) {var removed=field.removePokemonByEffect(A,old);if(mode.equals("reentered"))field.placeOnBench(A,removed.discardedCards().getFirst());}
            m.decideTriggeredEffect(B,prompt.id(),true);
            check(m.activePokemonView(A).pokemonId().equals(replacement)&&m.activePokemonView(A).damage()==0,"反击改打新战斗位");
            if(!mode.equals("left"))check(m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(PokemonIdentityTestSupport.logical(m,A,physical))).findFirst().orElseThrow().damage()==(mode.equals("bench")?30:0),
                "原攻击者换位／同一卡重新入场后绑定错误："+mode);
        }
    }
    static void recipientReentry() {
        UUID a=EnergyTriggeredAbilityTest.A;
        var reaction=EnergyTriggeredAbilityTest.ability("counter","OPPONENT_FIELD",true,"NONE","{\"type\":\"damage_counter\",\"target\":\"TRIGGER_POKEMON\",\"amount\":1}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(),List.of(reaction),null);UUID target=m.benchPokemonView(a).getFirst().pokemonId();
        var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergy(a,energy.instanceId(),target);
        var f=field(m);var removed=f.removePokemonByEffect(a,target);f.placeOnBench(a,removed.discardedCards().getFirst());
        var session=MatchSession.multiplayer(m,a,EnergyTriggeredAbilityTest.B);
        check(session.view(a,a).options().stream().filter(o->o.labelZh().startsWith("优先结算触发")).allMatch(o->o.labelZh().contains("已离场")),"旧对象标签伪装为同一卡的新实例");
        DamageTriggeredAbilityTest.finishTriggers(m);
        check(m.benchPokemonView(a).getFirst().damage()==0,"附能对象重新入场仍受到旧反应");
    }
    static void conditionsLatched() {
        UUID a=EnergyTriggeredAbilityTest.A;
        var base=EnergyTriggeredAbilityTest.ability("conditional","OPPONENT_FIELD",false,"NONE","{\"type\":\"heal\",\"target\":\"TRIGGER_POKEMON\",\"amount\":10}");
        var predicates=List.of(new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("target","TRIGGER_POKEMON","value","1","comparison","=="),List.of()),
            new ConditionDefinition(ConditionType.HP_BELOW,Map.of("target","TRIGGER_POKEMON","value","110"),List.of()));
        var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ENERGY_ATTACHED,predicates,base.effectProgram().triggers().getFirst().effects())));
        var ability=new AbilityDefinition(base.id(),base.nameZh(),base.effect(),base.effectSupport(),null,base.kind(),program,base.eventPolicy());
        var m=EnergyTriggeredAbilityTest.fixture(List.of(),List.of(ability),null);UUID target=m.benchPokemonView(a).getFirst().pokemonId();
        m.placeDamageCountersByEffect(a,a,target,2);var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.attachEnergy(a,energy.instanceId(),target);check(m.triggerOrderPrompt()!=null,"触发条件未在附能后按实际对象求值");
        DamageTriggeredAbilityTest.finishTriggers(m);check(m.benchPokemonView(a).getFirst().damage()==0,"第二个已捕获事件错误重新检查HP条件");
    }
    static void relativeAttackCause() {
        var reaction=watcher("relative","ANY_FIELD","OPPONENT_ATTACK_DAMAGE",DamageTriggeredAbilityTest.DRAW,"");
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(300,""),reaction);
        int ah=m.privateView(A).hand().size(),bh=m.privateView(B).hand().size();m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);
        check(m.privateView(A).hand().size()==ah&&m.privateView(B).hand().size()==bh+2,"由你的对手攻击的条件未相对监听特性拥有者判断");
    }
    static void statusBoundTarget() {
        UUID a=EnergyTriggeredAbilityTest.A;
        for(boolean active:List.of(true,false)) {
            var reaction=EnergyTriggeredAbilityTest.ability("poison","OPPONENT_FIELD",false,"NONE","{\"type\":\"poison\",\"target\":\"TRIGGER_POKEMON\"}");
            var m=EnergyTriggeredAbilityTest.fixture(List.of(),List.of(reaction),null);
            UUID target=active?m.activePokemonView(a).pokemonId():m.benchPokemonView(a).getFirst().pokemonId();
            var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergy(a,energy.instanceId(),target);DamageTriggeredAbilityTest.finishTriggers(m);
            check(m.activePokemonView(a).conditions().contains(SpecialCondition.POISONED)==active&&m.benchPokemonView(a).getFirst().conditions().isEmpty(),"事件对象在备战区仍被施加异常状态或状态错误转移战斗位");
        }
    }
    static void invalidBindings() {
        var bad=EnergyTriggeredAbilityTest.ability("bad","SELF",false,"NONE","{\"type\":\"heal\",\"target\":\"ATTACKING_POKEMON\",\"amount\":10}");
        reject(()->TriggeredAbilityRules.validate(bad));reject(()->EnergyTriggeredAbilityTest.fixture(List.of(bad),List.of(),null));
        var ko=watcher("bad","ANY_FIELD","ANY","{\"type\":\"knock_out\",\"target\":\"ATTACKING_POKEMON\"}","");reject(()->TriggeredAbilityRules.validate(ko));
        var checkup=CheckupTriggeredAbilityTest.ability("bad",false,"{\"type\":\"damage_counter\",\"target\":\"TRIGGER_POKEMON\",\"amount\":1}");reject(()->TriggeredAbilityRules.validate(checkup));
        var trainer=ComposedTrainerIntegrationTest.item("{\"type\":\"heal\",\"target\":\"TRIGGER_POKEMON\",\"amount\":10}");reject(()->TrainerProgramRules.validate(trainer.effectProgram()));
        var missing=new EffectTargetContext(A,B,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),List.of(),List.of());
        reject(()->EffectTargetResolver.resolve(EffectTarget.TRIGGER_POKEMON,missing));
        reject(()->new EffectEventBindings(new EffectTargetResolution.PokemonTarget(A,UUID.randomUUID()),0,null,0));
        var outsider=new EffectTargetResolution.PokemonTarget(UUID.randomUUID(),UUID.randomUUID());
        var foreign=new EffectTargetContext(A,B,null,null,null,List.of(),List.of(),new EffectEventBindings(outsider,1,null,0),Map.of(outsider,1L));
        reject(()->EffectTargetResolver.resolve(EffectTarget.TRIGGER_POKEMON,foreign));
    }
    static void beforeDamageCaptureReentry() {
        String draw=DamageTriggeredAbilityTest.DRAW;
        var reaction=DamageTriggeredAbilityTest.ability("old",true,false,false,"{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":3}");
        var attack=DamageTriggeredAbilityTest.attack(10,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"choice\",\"options\":["+draw+","+draw+"]}]}]");
        var m=DamageTriggeredAbilityTest.fixture(attack,reaction);UUID old=m.activePokemonView(B).pokemonId();m.attack(A,"hit");
        var f=field(m);f.switchActiveByEffect(B,m.benchPokemonView(B).getFirst().pokemonId());var removed=f.removePokemonByEffect(B,old);f.placeOnBench(B,removed.discardedCards().getFirst());
        var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(m.activePokemonView(A).damage()==0,"攻击全文暂停后新实例继承了旧实例的受伤事件");
    }
    static void boundProtection() {
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY"));
        var reaction=watcher("protected","OWN_FIELD","OPPONENT_ATTACK_DAMAGE","{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":1}","");
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(300,""),guard,reaction);
        m.useAbility(A,m.activePokemonView(A).pokemonId(),"guard");m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);
        check(m.activePokemonView(A).damage()==0&&m.phase()==MatchPhase.PRIZE_SELECTION,"绑定攻击者目标绕过原特性来源免疫");
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void reject(Runnable action) { try { action.run(); } catch(IllegalArgumentException|IllegalStateException expected) { return; } throw new AssertionError("非法事件绑定未拒绝"); }
}
