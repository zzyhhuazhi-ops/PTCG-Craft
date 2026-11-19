import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Printed attack costs and generated programs, including real private windows and public transfer continuations. */
public final class GeneratedRuleBatch15Test {
    static final UUID A=GeneratedRuleBatch13Test.A,B=GeneratedRuleBatch13Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return GeneratedRuleBatch13Test.active(m,p);}
    static UUID bench(ServerMatch m,UUID p){return GeneratedRuleBatch13Test.bench(m,p);}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch12Test.attack(id,slot);}
    static ServerMatch fixture(AttackDefinition a,EnergyType e){return GeneratedRuleBatch12Test.fixture(a,e);}
    static void pay(ServerMatch m,AttackDefinition a){GeneratedRuleBatch13Test.pay(m,a);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){GeneratedRuleBatch13Test.attach(m,owner,host,id);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void done(ServerMatch m){GeneratedRuleBatch13Test.done(m);}
    public static void main(String[] args){discharge();discardSwitch();healing();twoHeads();devolution();washOut();greatBall();System.out.println("GeneratedRuleBatch15Test passed: discard card counts, mandatory post-discard switch, whole-field/rest healing, two-head bonus, devolution, unlimited typed Energy moves and private top-seven search");}
    static void discharge(){
        var a=attack("rsv10-5_en_034",0);var m=fixture(a,EnergyType.LIGHTNING);pay(m,a);attach(m,A,active(m,A),"special");attach(m,A,active(m,A),"wrong");attach(m,A,bench(m,A),"energy");attach(m,B,active(m,B),"energy");m.attack(A,a.id());done(m);
        check(m.activePokemonView(B).damage()==100,"Discharge counted supplied units rather than discarded cards");check(m.activePokemonView(A).attachedEnergyIds().size()==1&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1&&m.activePokemonView(B).attachedEnergyIds().size()==1,"Discharge discarded wrong type, host or owner");
        check(m.privateView(A).discard().stream().filter(c->Set.of("energy","special").contains(c.definitionId())).count()==2,"Discharge did not discard both typed cards");
    }
    static void discardSwitch(){
        for(String id:List.of("sv4_en_050","swsh1_en_079"))for(boolean hasBench:List.of(false,true)){
            var a=attack(id,1);var m=fixture(a,id.startsWith("sv")?EnergyType.WATER:EnergyType.LIGHTNING);pay(m,a);UUID old=active(m,A),replacement=bench(m,A);if(!hasBench)m.movePokemonByEffect(A,A,replacement,LeavePlayDestination.HAND);
            m.attack(A,a.id());check(m.activePokemonView(B).damage()==a.baseDamage(),"switch or discard occurred before damage");var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1,"mandatory Energy discard missing");
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));UUID selected=p.candidates().getFirst().instanceId();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(selected)));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(selected));
            if(hasBench)GeneratedRuleBatch13Test.chooseHost(m,replacement);done(m);check(active(m,A).equals(hasBench?replacement:old),"mandatory switch or no-Bench continuation");
            var host=hasBench?m.benchPokemonView(A).getFirst():m.activePokemonView(A);check(host.attachedEnergyIds().size()==2&&!host.attachedEnergyIds().contains(selected),"wrong attacker Energy discarded");
        }
    }
    static void healing(){
        for(String id:List.of("me4_en_035","sm4_en_020")){
            boolean rest=id.startsWith("sm");var a=attack(id,rest?1:0);var m=fixture(a,EnergyType.PSYCHIC);pay(m,a);
            for(UUID owner:List.of(A,B)){m.placeDamageCountersByEffect(A,owner,active(m,owner),12);m.placeDamageCountersByEffect(A,owner,bench(m,owner),2);}
            int from=m.randomTranscript().size();m.attack(A,a.id());done(m);
            check(m.activePokemonView(A).damage()==(rest?30:90)&&m.benchPokemonView(A).getFirst().damage()==(rest?20:0)&&m.activePokemonView(B).damage()==(rest?120:90)&&m.benchPokemonView(B).getFirst().damage()==(rest?20:0),"healing amount, owner or scope changed");
            if(rest)check(AttackCoinGateTest.events(m,from,"").size()==1,"Rest did not apply sleep before checkup");
        }
    }
    static void twoHeads(){
        var outcomes=new HashSet<Integer>();
        for(int shift=0;shift<16&&outcomes.size()<3;shift++){
            var a=attack("me1_en_112",1);var m=fixture(a,EnergyType.GRASS);pay(m,a);if(shift>0)m.flipCoinsByEffect(A,shift);int from=m.randomTranscript().size();m.attack(A,a.id());done(m);var flips=AttackCoinGateTest.events(m,from,"attack-effect-coin:");int heads=(int)flips.stream().filter(e->e.result()==0).count();outcomes.add(heads);
            check(flips.size()==2&&m.activePokemonView(B).damage()==(heads==2?200:100),"bonus requires both heads");
        }check(outcomes.equals(Set.of(0,1,2)),"fixture did not exercise every heads count");
    }
    static void devolution(){
        for(String id:List.of("me4_en_047","swsh7_en_070"))for(boolean evolved:List.of(false,true)){
            var a=attack(id,id.startsWith("me")?0:1);var m=fixture(a,id.startsWith("me")?EnergyType.FIGHTING:EnergyType.PSYCHIC);pay(m,a);UUID defender=active(m,B);var evolution=hand(m,B,"evolved");attach(m,B,defender,"energy");
            if(evolved){m.endTurn(A);m.evolveByEffect(B,defender,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.applySpecialConditionByEffect(B,B,SpecialCondition.POISONED);m.endTurn(B);}
            int before=m.activePokemonView(B).damage();m.attack(A,a.id());done(m);var v=m.activePokemonView(B);
            check(v.topCardId().equals("basic")&&v.damage()==before+a.baseDamage()&&v.attachedEnergyIds().size()==1&&v.conditions().isEmpty(),"devolution damage/attachment/status preservation");check(m.privateView(B).hand().contains(evolution),"top Evolution not returned to owner");
        }
    }
    static void washOut(){
        for(String id:List.of("sm7-5_en_026","sv1_en_041")){
            var ability=GeneratedRuleBatch8Test.ability(id);var m=GeneratedRuleBatch8Test.fixture(List.of(ability),id.startsWith("sm")?EnergyType.WATER:EnergyType.FIRE,EnergyType.GRASS);UUID source=bench(m,A),destination=active(m,A);
            attach(m,A,source,"energy");attach(m,A,source,"special");attach(m,A,source,"wrong");attach(m,A,destination,"energy");attach(m,B,bench(m,B),"energy");
            for(String card:List.of("special","energy")){
                m.useAbility(A,source,ability.id());var p=m.effectCardPrompt(A);check(p.candidates().stream().noneMatch(c->c.definitionId().equals("wrong"))&&p.candidates().stream().allMatch(c->m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(c.instanceId())),"movement candidates ignored supplied type/source position/owner");
                UUID selected=p.candidates().stream().filter(c->c.definitionId().equals(card)).findFirst().orElseThrow().instanceId();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(selected)));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(selected));
                var target=m.effectAttachmentPrompt(A);check(target.targets().equals(List.of(destination)),"move offered a non-Active destination");reject(()->m.chooseEffectAttachmentTarget(A,target.resolutionId(),target.step(),source));m.chooseEffectAttachmentTarget(A,target.resolutionId(),target.step(),destination);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(selected)));
            }
            check(m.currentPlayer().equals(A)&&!m.energyAttached(A)&&m.activePokemonView(A).attachedEnergyIds().size()==3&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1,"unlimited movement spent manual attachment or moved wrong cards");reject(()->m.useAbility(A,source,ability.id()));
        }
    }
    static void greatBall(){
        for(String mode:List.of("take","decline","short","empty")){
            var trainer=GeneratedRuleBatch8Test.trainer("sm1_en_119");var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,trainer,GeneratedRuleBatch8Test.hit(10,false));
            if(mode.equals("short")||mode.equals("empty"))m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-(mode.equals("short")?1:0));var item=hand(m,A,"trainer");
            if(mode.equals("empty")){reject(()->m.playTrainer(A,item.instanceId(),null));continue;}
            var deck=m.privateView(A).deck();var expected=List.copyOf(deck.subList(Math.max(0,deck.size()-7),deck.size()));int random=m.randomTranscript().size(),before=m.privateView(A).hand().size();m.playTrainer(A,item.instanceId(),null);var p=m.effectCardPrompt(A);
            check(new HashSet<>(p.inspectedCards()).equals(new HashSet<>(expected))&&p.minimum()==0&&p.maximum()==1,"Great Ball inspection window or optional count");check(new HashSet<>(p.candidates()).equals(new HashSet<>(expected.stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON).toList())),"Great Ball filtered before taking top seven");if(mode.equals("take"))check(!p.candidates().isEmpty(),"fixture needs a Pokemon in the window");
            var ids=mode.equals("take")&&!p.candidates().isEmpty()?List.of(p.candidates().getFirst().instanceId()):List.<UUID>of();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));m.submitEffectCards(A,p.resolutionId(),p.step(),ids);
            if(!ids.isEmpty()){check(m.effectCardReveal()!=null,"selected Pokemon not revealed");m.acknowledgeEffectReveal(B,m.effectCardReveal().id());}
            check(m.privateView(A).hand().size()==before-1+ids.size()&&m.privateView(A).discard().contains(item)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Great Ball conservation or continuation");if(deck.size()>1)check(m.randomTranscript().size()>random,"remaining deck not shuffled");
        }
    }
}
