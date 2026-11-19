import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Printed Power Pinch: select Energy units once from the final coin result, moving whole cards. */
public final class CoinEnergyDiscardTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static JsonObject raw(){try{return JsonParser.parseString(Files.readString(Path.of("outputs/pokemon-power-pinch-programs.jsonl"))).getAsJsonObject().getAsJsonObject("conversion").getAsJsonObject("combatEntry").getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();}catch(Exception e){throw new RuntimeException(e);}}
    static AttackDefinition attack(boolean returned){var a=returned?GeneratedRuleBatch8Test.entry("sv4_en_002").getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy():raw();a.add("energyCost",new JsonObject());return BundledCombatData.attack(a,EffectRegistry.standard());}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    record Started(ServerMatch match,List<CardInstance> energy,CardInstance own,CardInstance bench) { }
    static CardInstance attach(ServerMatch m,UUID p,UUID host,String id){var c=DynamicPassiveModifierTest.hand(m,p,id);m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,c.instanceId(),p,host);return c;}
    static Started start(UUID p,int heads,int units,List<String> ids,boolean returned){for(int skip=0;skip<80;skip++){
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),units==0?new EnergyProfile(List.of()):EnergyProfile.fixed(EnergyType.COLORLESS,units),attack(returned));if(p.equals(B))m.endTurn(A);
        var chosen=new ArrayList<CardInstance>();for(String id:ids)chosen.add(attach(m,other(p),active(m,other(p)),id));var own=attach(m,p,active(m,p),"special");var bench=attach(m,other(p),m.benchPokemonView(other(p)).getFirst().pokemonId(),"special");
        for(int i=0;i<skip;i++)m.flipCoinsByEffect(p,1);int from=m.randomTranscript().size();m.attack(p,attack(returned).id());
        var flips=m.randomTranscript().subList(from,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("effect-coin:")).toList();
        if(flips.stream().filter(e->e.result()==0).count()==heads){check(returned||flips.size()==2,"Power Pinch did not toss exactly two coins");return new Started(m,chosen,own,bench);}
    }throw new AssertionError("No deterministic coin fixture for "+heads);}
    static void select(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void atomic(ServerMatch m,Runnable r){AttackDiscardUnitsTest.atomic(m,r);}
    static void unchangedDecoys(Started s,UUID p){var m=s.match();check(m.activePokemonView(p).attachedEnergyIds().contains(s.own().instanceId())&&m.benchPokemonView(other(p)).getFirst().attachedEnergyIds().contains(s.bench().instanceId()),"Discard escaped defending Active");}
    public static void main(String[] args){partial();choices();returnCards();schema();ai();System.out.println("CoinEnergyDiscardTest passed: actual Power Pinch both seats, 0/1/2 heads, one or two multi-unit cards, mixed/zero units, partial/empty field, excess/duplicate/foreign/stale rejection, legacy coin-return physical-card semantics, coin binding and eight four-tier matches");}
    static void partial(){for(UUID p:List.of(A,B))for(int heads:List.of(0,1,2))for(var ids:List.of(List.<String>of(),List.of("energy"),List.of("special","energy"))){
        var s=start(p,heads,0,ids,false);var m=s.match();var expected=heads==0?List.<CardInstance>of():s.energy().stream().filter(c->c.definitionId().equals("energy")).toList();
        if(m.hasPendingCardSelection())select(m,p,expected);else check(expected.isEmpty(),"Missing available partial discard");
        check(m.privateView(other(p)).discard().containsAll(expected)&&m.currentPlayer().equals(other(p)),"Partial/zero result stranded attack");
        check(s.energy().stream().filter(c->c.definitionId().equals("special")).allMatch(c->m.activePokemonView(other(p)).attachedEnergyIds().contains(c.instanceId())),"Zero-unit card was discarded as Energy");unchangedDecoys(s,p);
    }}
    static void choices(){for(UUID p:List.of(A,B))for(int heads:List.of(1,2))for(boolean twoCards:List.of(false,true)){
        if(heads==1&&twoCards)continue;var s=start(p,heads,2,List.of("special","special","energy"),false);var m=s.match();var first=s.energy().getFirst();var basic=s.energy().getLast();
        atomic(m,()->select(m,p,List.of()));atomic(m,()->select(m,p,List.of(s.own())));atomic(m,()->select(m,p,List.of(s.bench())));atomic(m,()->select(m,p,List.of(first,first)));atomic(m,()->select(m,p,s.energy()));
        if(heads==2)atomic(m,()->select(m,p,List.of(basic)));else atomic(m,()->select(m,p,s.energy().subList(0,2)));
        var q=m.effectCardPrompt(p);var chosen=twoCards?s.energy().subList(0,2):List.of(first);select(m,p,chosen);
        check(m.privateView(other(p)).discard().containsAll(chosen)&&m.activePokemonView(other(p)).attachedEnergyIds().size()==3-chosen.size(),"Coin units were counted as cards or selected cards moved partially");
        atomic(m,()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(first.instanceId())));unchangedDecoys(s,p);
        if(heads==2&&twoCards){var mixed=start(p,2,2,List.of("special","energy"),false);select(mixed.match(),p,mixed.energy());check(mixed.match().activePokemonView(other(p)).attachedEnergyIds().isEmpty(),"Official mixed Basic/Double Colorless choice rejected");}
    }}
    static void returnCards(){for(UUID p:List.of(A,B)){var s=start(p,2,2,List.of("special","special"),true);var m=s.match();atomic(m,()->select(m,p,List.of(s.energy().getFirst())));select(m,p,s.energy());var v=m.privateView(other(p));var returned=java.util.stream.Stream.concat(v.deck().stream(),v.hand().stream()).toList();check(returned.containsAll(s.energy())&&v.discard().stream().noneMatch(s.energy()::contains),"Daunting Eyes changed from physical cards to units or discarded instead of shuffling");}}
    static void schema(){var a=raw();var bindings=a.getAsJsonArray("triggers").get(0).getAsJsonObject();var effects=bindings.getAsJsonArray("effects");var original=effects.get(1).getAsJsonObject();
        for(String key:List.of("count_mode","amount_mode","amount","coin")){var invalid=original.deepCopy();invalid.addProperty(key,key.equals("count_mode")?"CARDS":key.equals("amount_mode")?"COIN_TAILS":key.equals("amount")?"2":"");GeneratedRuleBatch8Test.reject(()->AttachedEnergyEffect.from(EffectRegistry.standard().parse(invalid)));}
        var without=a.deepCopy();without.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects").remove(0);check(AttackProgramRules.unsupportedReason(BundledCombatData.attack(without,EffectRegistry.standard())).isPresent(),"Unbound coin accepted");
    }
    static void ai(){for(UUID p:List.of(A,B))for(var level:AiDifficulty.values()){var s=start(p,2,2,List.of("special","energy"),false);var session=MatchSession.multiplayer(s.match(),A,B);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(s.match().phase()==MatchPhase.FINISHED,"Coin-unit selection stalled "+level);}}
}
