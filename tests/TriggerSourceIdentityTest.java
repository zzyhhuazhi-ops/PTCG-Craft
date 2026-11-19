import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Synthetic field transitions isolate captured SELF identity; they do not enable unreviewed card text. */
public final class TriggerSourceIdentityTest {
    static final String EFFECTS="{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10},{\"type\":\"draw\",\"amount\":1}";
    public static void main(String[] args) {
        for (String trigger:List.of("energy","damage","checkup","knockout"))
            for (String change:List.of("same","switch","left","reentered")) checkSource(trigger,change);
        bindingValidation(); usageIsolation(); protectionAfterDeparture();
        System.out.println("TriggerSourceIdentityTest passed: four event kinds retain source incarnation; departed SELF skipped, player effects preserved");
    }
    static void checkSource(String trigger,String change) {
        ServerMatch m; UUID owner; UUID source;
        switch(trigger) {
            case "energy" -> {
                owner=EnergyTriggeredAbilityTest.A;
                m=EnergyTriggeredAbilityTest.fixture(List.of(EnergyTriggeredAbilityTest.ability("source","SELF",true,"NONE",EFFECTS)),List.of(),null);
                source=m.activePokemonView(owner).pokemonId();
                m.placeDamageCountersByEffect(owner,owner,source,2);
                var energy=m.privateView(owner).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
                m.attachEnergy(owner,energy.instanceId(),source);
            }
            case "checkup" -> {
                owner=EnergyTriggeredAbilityTest.A;
                m=CheckupTriggeredAbilityTest.fixture(List.of(CheckupTriggeredAbilityTest.ability("source",true,EFFECTS)),List.of());
                source=m.activePokemonView(owner).pokemonId();m.placeDamageCountersByEffect(owner,owner,source,2);
                m.endTurn(owner);CheckupTriggeredAbilityTest.choose(m,"source");
            }
            case "damage" -> {
                owner=DamageTriggeredAbilityTest.B;
                m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10,""),DamageTriggeredAbilityTest.ability("source",true,false,true,EFFECTS));
                source=m.activePokemonView(owner).pokemonId();
                m.placeDamageCountersByEffect(DamageTriggeredAbilityTest.A,owner,source,1);m.attack(DamageTriggeredAbilityTest.A,"hit");
            }
            case "knockout" -> {
                owner=DamageTriggeredAbilityTest.B;
                m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(300,""),EventTargetBindingTest.watcher("source","OWN_FIELD","OPPONENT_ATTACK_DAMAGE",EFFECTS,""));
                source=m.benchPokemonView(owner).getFirst().pokemonId();
                m.placeDamageCountersByEffect(DamageTriggeredAbilityTest.A,owner,source,2);m.attack(DamageTriggeredAbilityTest.A,"hit");
            }
            default -> throw new AssertionError(trigger);
        }
        var prompt=m.triggerOrderPrompt();check(prompt!=null,"缺少待处理触发："+trigger);
        int hand=m.privateView(owner).hand().size();long random=m.randomTranscript().size();
        var field=EventTargetBindingTest.field(m);UUID physical=PokemonIdentityTestSupport.physical(m,owner,source);
        boolean wasActive=field.activeView(owner).pokemonId().equals(source);
        if(!change.equals("same")) {
            if(wasActive)field.switchActiveByEffect(owner,field.benchView(owner).getFirst().pokemonId());
            else if(change.equals("switch"))field.switchActiveByEffect(owner,source);
            if(change.equals("left")||change.equals("reentered")) {
                var removed=field.removePokemonByEffect(owner,source);
                if(change.equals("reentered")) {
                    field.placeOnBench(owner,removed.discardedCards().getFirst());field.placeDamageCounters(owner,PokemonIdentityTestSupport.logical(m,owner,physical),2);
                }
            }
        }
        var ownEntry=prompt.entries().stream().filter(e->e.owner().equals(owner)&&e.pokemonId().equals(source)).findFirst().orElseThrow();
        check(m.triggerSourceStillInPlay(ownEntry.id())==(!change.equals("left")&&!change.equals("reentered")),"来源存活检查没有按登场实例判断");
        UUID other=trigger.equals("energy")||trigger.equals("checkup")?EnergyTriggeredAbilityTest.B:DamageTriggeredAbilityTest.A;
        var session=MatchSession.multiplayer(m,owner,other);
        if(change.equals("left")||change.equals("reentered")) {
            var snapshot=session.view(prompt.choosingPlayer(),prompt.choosingPlayer());
            check(snapshot.options().stream().anyMatch(o->o.labelZh().contains("原特性来源已离场")),"中文操作把离场来源冒充新登场卡");
        }
        if(prompt.optionalDecision())m.decideTriggeredEffect(owner,prompt.id(),true);
        else {
            var entry=prompt.entries().stream().filter(e->e.owner().equals(owner)&&e.pokemonId().equals(source)).findFirst().orElseThrow();
            m.chooseTriggeredEffect(prompt.choosingPlayer(),prompt.id(),entry.id());
        }
        if(!change.equals("left"))check(field.pokemonView(owner,PokemonIdentityTestSupport.logical(m,owner,physical)).damage()==(change.equals("reentered")?20:10),
            "旧SELF效果错误影响新登场对象或未追随换位："+trigger+"/"+change);
        check(m.privateView(owner).hand().size()==hand+(trigger.equals("damage")||trigger.equals("knockout")?2:1),"来源离场使玩家级效果丢失或重复："+trigger+"/"+change);
        check(m.randomTranscript().size()==random,"来源解析不应额外消耗随机数");
    }
    static void bindingValidation() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),id=UUID.randomUUID();
        var ref=new EffectTargetResolution.PokemonTarget(a,id);
        var event=EffectEventBindings.NONE.withSource(ref,1);
        check(event.withSource(ref,1).equals(event),"同一来源重复登记不稳定");
        EventTargetBindingTest.reject(()->event.withSource(ref,2));
        EventTargetBindingTest.reject(()->event.withSource(new EffectTargetResolution.PokemonTarget(b,id),1));
        EventTargetBindingTest.reject(()->new EffectEventBindings(null,0,null,0,ref,0));
        EventTargetBindingTest.reject(()->new EffectEventBindings(null,0,null,0,null,1));
        for(long actual:List.of(0L,1L,2L)) {
            var target=EffectTargetResolver.resolve(EffectTarget.SELF,new EffectTargetContext(a,b,id,id,null,List.of(),List.of(),event,Map.of(ref,actual)));
            check(target.fixedTargets().size()==(actual==1?1:0),"SELF绑定没有检查服务器登场序号");
        }
        EventTargetBindingTest.reject(()->EffectTargetResolver.resolve(EffectTarget.SELF,
            new EffectTargetContext(b,a,id,id,null,List.of(),List.of(),event,Map.of(ref,1L))));
    }
    static void usageIsolation() {
        UUID a=EnergyTriggeredAbilityTest.A;
        for(boolean shared:List.of(false,true)) {
            var base=EnergyTriggeredAbilityTest.ability("usage","SELF",true,"ONCE_PER_TURN","{\"type\":\"draw\",\"amount\":1}");
            var ability=shared?new AbilityDefinition(base.id(),base.nameZh(),base.effect(),base.effectSupport(),null,base.kind(),base.effectProgram(),base.eventPolicy(),new SharedUsageLimit("fixture-shared",SharedUsageLimit.Period.GAME)):base;
            var m=EnergyTriggeredAbilityTest.fixture(List.of(ability),List.of(),null);UUID old=m.activePokemonView(a).pokemonId();
            var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergy(a,energy.instanceId(),old);
            var prompt=m.triggerOrderPrompt();var f=EventTargetBindingTest.field(m);f.switchActiveByEffect(a,f.benchView(a).getFirst().pokemonId());
            var removed=f.removePokemonByEffect(a,old);f.placeOnBench(a,removed.discardedCards().getFirst());UUID returned=f.printedPokemonView(a,removed.discardedCards().getFirst().instanceId()).pokemonId();check(!returned.equals(old),"Reentered trigger source reused previous stay identity");
            m.decideTriggeredEffect(a,prompt.id(),true);
            energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
            m.attachEnergyByEffect(a,a,EffectSourceZone.HAND,energy.instanceId(),a,returned);
            check((m.triggerOrderPrompt()!=null)==!shared,"原来源次数错误套用新来源，或重进场重置玩家整局资格");
            if(!shared) {
                var next=m.triggerOrderPrompt();m.decideTriggeredEffect(a,next.id(),true);
                energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
                m.attachEnergyByEffect(a,a,EffectSourceZone.HAND,energy.instanceId(),a,returned);
                check(m.triggerOrderPrompt()==null,"新登场来源自己的次数限制失效");
            }
        }
    }
    static void protectionAfterDeparture() {
        UUID a=DamageTriggeredAbilityTest.A,b=DamageTriggeredAbilityTest.B;
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY"));
        var reaction=DamageTriggeredAbilityTest.ability("source",true,false,true,"{\"type\":\"damage_counter\",\"target\":\"ATTACKING_POKEMON\",\"amount\":3}");
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10,""),guard,reaction);
        m.useAbility(a,m.activePokemonView(a).pokemonId(),"guard");UUID old=m.activePokemonView(b).pokemonId();m.attack(a,"hit");
        var prompt=m.triggerOrderPrompt();var f=EventTargetBindingTest.field(m);f.switchActiveByEffect(b,f.benchView(b).getFirst().pokemonId());f.removePokemonByEffect(b,old);
        m.decideTriggeredEffect(b,prompt.id(),true);check(m.activePokemonView(a).damage()==0,"来源离场后反击丢失ABILITY归属绕过免疫");
    }
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
