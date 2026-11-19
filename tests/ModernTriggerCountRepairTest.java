import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernTriggerCountRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    static ServerMatch game(List<AbilityDefinition> abilities,String trainer){return ModernCountRepairTest.game(trainer,abilities);}
    static AbilityDefinition ability(String id){var a=DATA.pokemon(id).orElseThrow().abilities().getFirst();if(a.kind()==AbilityDefinition.Kind.TRIGGERED)TriggeredAbilityRules.validate(a);return a;}
    public static void main(String[] args){brassius();lucian();dodrio();eternatus();tusk();minior();System.out.println("ModernTriggerCountRepairTest passed: 11 prints; actual counts, two independent coins, self counter draw, Stadium cost, end-turn timing, manual/effect attachment and owner/turn boundaries");}
    static void brassius(){
        for(String id:List.of("sv3_en_187","sv8-5_en_135"))for(boolean empty:List.of(false,true)){
            var m=game(List.of(),id);var card=hand(m,A,"trainer");
            if(empty)m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.equals(card)).map(CardInstance::instanceId).toList());
            var before=m.privateView(A);m.playTrainer(A,card.instanceId(),null);
            check(m.privateView(A).hand().size()==before.hand().size()&&m.privateView(A).discard().contains(card),"Brassius must draw moved count plus one, excluding itself");
            check(m.privateView(A).deck().size()+m.privateView(A).hand().size()==before.deck().size()+before.hand().size()-1&&!m.hasPendingEffectChoice(),"Brassius loss/stall");
        }
    }
    static void lucian(){
        for(String id:List.of("sv6_en_157","sv6_en_208"))for(boolean empty:List.of(false,true)){
            var m=game(List.of(),id);var card=hand(m,A,"trainer");
            if(empty)for(UUID p:List.of(A,B))m.discardHandCardsByEffect(A,p,m.privateView(p).hand().stream().filter(c->!c.equals(card)).map(CardInstance::instanceId).toList());
            var a=m.privateView(A);var b=m.privateView(B);var random=m.randomTranscript();m.playTrainer(A,card.instanceId(),null);
            check(!m.hasPendingEffectChoice(),"Lucian stalled");
            for(UUID p:List.of(A,B)){
                var old=p.equals(A)?a:b;var now=m.privateView(p);int count=now.hand().size();
                check(empty?count==0:count==3||count==6,"Lucian must draw independent coin amounts: "+id+" empty="+empty+" owner="+p+" actual="+count);
                check(now.hand().size()+now.deck().size()==old.hand().size()+old.deck().size()-(p.equals(A)?1:0),"Lucian lost/duplicated cards");
                if(!empty){var bottom=new HashSet<>(old.hand());bottom.remove(card);check(new HashSet<>(now.deck().subList(0,bottom.size())).equals(bottom),"Lucian shuffled original top deck");}
            }
            if(empty)check(m.randomTranscript().equals(random),"Lucian flipped despite both hands empty");
        }
    }
    static void dodrio(){
        for(String id:List.of("sv3-5_en_085","sv4-5_en_200")){
            var ability=ability(id);var m=game(List.of(ability),"swsh5_en_124");int before=m.privateView(A).hand().size();
            for(UUID source:List.of(m.activePokemonView(A).pokemonId(),m.benchPokemonView(A).getFirst().pokemonId())){
                m.useAbility(A,source,ability.id());check(!m.canUseAbility(A,source,ability.id()),"Dodrio reused source");
            }
            check(m.privateView(A).hand().size()==before+2&&m.activePokemonView(A).damage()==10&&m.benchPokemonView(A).getFirst().damage()==10&&!m.hasPendingEffectChoice(),"Dodrio draw/counters not per source");
            m.endTurn(A);m.endTurn(B);var source=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,source,48);before=m.privateView(A).hand().size();m.useAbility(A,source,ability.id());
            check(m.privateView(A).hand().size()==before+1&&m.phase()!=MatchPhase.TURN,"Dodrio must draw before self-KO processing");
        }
    }
    static void eternatus(){
        var attack=DATA.pokemon("sv8_en_141").orElseThrow().attacks().get(1);AttackProgramRules.validate(attack);
        for(boolean stadium:List.of(false,true)){
            var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),new EnergyProfile(List.of(new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class)),new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class)),new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class)))),attack);
            UUID a=DynamicPassiveModifierTest.A,b=DynamicPassiveModifierTest.B;var source=m.activePokemonView(a).pokemonId();m.attachEnergy(a,hand(m,a,"special").instanceId(),source);var card=hand(m,a,"stadium");if(stadium)m.playTrainer(a,card.instanceId(),null);
            m.attack(a,attack.id());check(m.lastAttack().finalDamage()==(stadium?230:0)&&m.clientView(a).stadium()==null&&!m.hasPendingEffectChoice(),"Eternatus damage must depend on actual Stadium discard");
            check(!stadium||m.privateView(a).discard().contains(card),"Eternatus lost Stadium");
        }
    }
    static void settle(ServerMatch m){
        for(int i=0;m.triggerOrderPrompt()!=null&&i<12;i++){
            var p=m.triggerOrderPrompt();if(p.optionalDecision())m.decideTriggeredEffect(p.choosingPlayer(),p.id(),true);else m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());
        }check(m.triggerOrderPrompt()==null,"Trigger did not finish");
    }
    static void tusk(){
        for(String id:List.of("sv4-5_en_053","svbsp_en_072"))for(boolean active:List.of(false,true)){
            var m=game(List.of(ability(id)),"swsh5_en_124");
            if(!active){var c=hand(m,A,"rulebox");m.playBasicPokemonToBench(A,c.instanceId());var host=m.benchPokemonView(A).stream().filter(v->v.topCardId().equals("rulebox")).findFirst().orElseThrow().pokemonId();m.switchActiveByEffect(A,A,host);}
            var before=m.privateView(A);m.endTurn(A);settle(m);
            check(m.privateView(A).deck().size()==Math.max(0,before.deck().size()-(active?5:0))&&m.currentPlayer().equals(B),"Great Tusk must mill only active at own turn end");
            int size=m.privateView(A).deck().size();m.endTurn(B);settle(m);check(m.privateView(A).deck().size()==size-1,"Great Tusk incorrectly triggered on opponent turn end");
        }
    }
    static void minior(){
        for(String id:List.of("sv4_en_099","sv4_en_201"))for(int mode=0;mode<5;mode++){
            var m=game(List.of(ability(id)),"swsh5_en_124");var old=m.activePokemonView(A).pokemonId();var source=m.benchPokemonView(A).getFirst().pokemonId();
            if(mode==3)m.endTurn(A);
            UUID energyOwner=mode==4?B:A;var energy=hand(m,energyOwner,"energy");
            if(mode==0)m.attachEnergy(A,energy.instanceId(),source);
            else {if(mode==2)m.discardHandCardsByEffect(A,A,List.of(energy.instanceId()));m.attachEnergyByEffect(mode==3?B:A,energyOwner,mode==2?EffectSourceZone.DISCARD:EffectSourceZone.HAND,energy.instanceId(),A,source);}
            if(mode<=1){check(m.triggerOrderPrompt()!=null,"Minior omitted manual or effect hand attachment");settle(m);check(m.activePokemonView(A).pokemonId().equals(source),"Minior switched wrong source");}
            else check(m.triggerOrderPrompt()==null&&m.activePokemonView(A).pokemonId().equals(old),"Minior triggered from discard, opponent turn or opponent hand");
        }
    }
}
