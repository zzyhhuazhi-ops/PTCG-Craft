import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch68Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm6_en_110");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static ServerMatch fixture(UUID p,int fires,int discard,boolean dual){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();c.trainerLimits=Map.of("棱镜之星",1);
        var types=fires==0?Set.of(EnergyType.WATER):dual?Set.of(EnergyType.FIRE,EnergyType.WATER):Set.of(EnergyType.FIRE);
        if(p.equals(A))c.ownTypes=types;else c.defenderTypes=types;
        var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);
        if(fires==1)m.movePokemonByEffect(p,p,m.benchPokemonView(p).getFirst().pokemonId(),LeavePlayDestination.HAND);
        for(int i=2;i<fires;i++)m.playBasicPokemonToBench(p,hand(m,p,p.equals(A)?"attacker":"defender").instanceId());
        UUID opp=other(p);var cards=m.privateView(opp).hand().stream().filter(x->!x.definitionId().equals("trainer")).limit(discard).map(CardInstance::instanceId).toList();check(cards.size()==discard,"Discard fixture short");if(!cards.isEmpty())m.discardHandCardsByEffect(p,opp,cards);return m;
    }
    static ServerMatch aiGame(String id){return fixture(A,2,4,false);}
    public static void main(String[] args){banish();live();copied();schema();System.out.println("NonPokemonBatch68Test passed: live own Fire count, dual types count once, exact mandatory selection and depleted discard, both owners, no-Fire/no-discard rejection, physical owner Lost Zones and Prism disposal, wrong owner/stale/under/over-selection atomic");}
    static void banish(){for(UUID p:List.of(A,B))for(int fires:List.of(0,1,2,4))for(int size:List.of(0,1,3,6))for(boolean dual:List.of(false,true)){
        var m=fixture(p,fires,size,dual);UUID opp=other(p);var source=hand(m,p,"trainer");boolean allowed=fires>0&&size>0;var before=m.privateView(p);check(m.canPlayTrainer(p,source.instanceId(),null)==allowed,"Wrong preflight "+fires+"/"+size);
        if(!allowed){GeneratedRuleBatch8Test.reject(()->m.playTrainer(p,source.instanceId(),null));check(m.privateView(p).equals(before)&&m.mayPlaySupporter(p),"Rejected Lysandre consumed source/Supporter");continue;}
        var pool=m.privateView(opp).discard();m.playTrainer(p,source.instanceId(),null);var q=m.effectCardPrompt(p);int count=Math.min(fires,size);check(q.minimum()==count&&q.maximum()==fires&&new HashSet<>(q.candidates()).equals(new HashSet<>(pool)),"Wrong mandatory count or discarded cards "+fires+"/"+size+" -> "+q.minimum()+"/"+q.maximum());
        var selected=q.candidates().stream().limit(count).map(CardInstance::instanceId).toList();var ownBefore=m.privateView(p);var oppBefore=m.privateView(opp);
        GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(opp,q.resolutionId(),q.step(),selected));GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),selected.subList(0,count-1)));
        if(size>fires){var excessive=q.candidates().stream().limit(fires+1).map(CardInstance::instanceId).toList();GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),excessive));}
        check(m.privateView(p).equals(ownBefore)&&m.privateView(opp).equals(oppBefore),"Invalid banishment changed cards");m.submitEffectCards(p,q.resolutionId(),q.step(),selected);NonPokemonBatch67Test.settle(m);GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),selected));
        check(m.privateView(opp).lostZone().size()==count&&m.privateView(opp).lostZone().stream().allMatch(x->selected.contains(x.instanceId()))&&m.privateView(opp).discard().size()==size-count,"Incorrect opponent Lost Zone");check(m.privateView(p).lostZone().equals(List.of(source))&&!m.privateView(p).discard().contains(source)&&!m.mayPlaySupporter(p),"Prism source disposal or Supporter allowance wrong");
    }}
    static void live(){var m=fixture(A,2,4,false);m.evolveByEffect(A,m.activePokemonView(A).pokemonId(),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1,"Fire count used pre-evolution printed type");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId()));NonPokemonBatch67Test.settle(m);}
    static void copied(){var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();c.attack=DeckDiscardTriggeredAbilityTest.attack(10,"{\"type\":\"use_top_supporter\",\"target\":\"DECK\"}");var m=GeneratedRuleBatch28Test.fixture(c);m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().limit(4).map(CardInstance::instanceId).toList());NonPokemonBatch37Test.top(m,A,hand(m,A,"trainer"));m.attack(A,"hit");NonPokemonBatch67Test.settle(m);check(m.privateView(B).lostZone().size()==2,"Attack-copied Lysandre could not resolve opponent discard banishment");}
    static void schema(){String valid="{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":60,\"minimum\":60,\"source_owner\":\"OPPONENT\",\"destination\":\"LOST_ZONE\",\"amount_mode\":\"OWN_FIRE_POKEMON\"}";
        for(String bad:List.of(valid.replace("OPPONENT","SELF"),valid.replace("OWN_FIRE_POKEMON","OWN_WATER_POKEMON"),valid.replace("\"minimum\":60","\"minimum\":0"),valid.replace("\"amount\":60","\"amount\":2"),valid.replace("\"destination\":\"LOST_ZONE\"","\"destination\":\"HAND\"")))GeneratedRuleBatch8Test.reject(()->ZoneSelectionEffect.forProgram(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject())));
    }
}
