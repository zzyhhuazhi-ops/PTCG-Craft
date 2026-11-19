import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Exact repaired typed-discard operations; source binding remains the attacker after a switch. */
public final class TypedEnergyDiscardSourceTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    record Case(String id,JsonObject effect) { }
    static final List<Case> CASES=load();
    static List<Case> load(){try{var out=new ArrayList<Case>();for(var value:JsonParser.parseString(Files.readString(Path.of("outputs/pokemon-typed-discard-diff.json"))).getAsJsonArray()){var r=value.getAsJsonObject();out.add(new Case(r.get("id").getAsString(),r.getAsJsonObject("after")));}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static AttackDefinition attack(Case c,boolean switchFirst){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"指定属性弃能\",\"energyCost\":{},\"baseDamage\":20,\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+(switchFirst?"{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"},":"")+c.effect+"]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(Case c,UUID p,boolean switchFirst,int units){var config=new GeneratedRuleBatch28Test.Config();config.attack=attack(c,switchFirst);config.defenderAttack=config.attack;config.specialEnergy=EnergyProfile.fixed(EnergyType.valueOf(c.effect.get("energy_type").getAsString()),units);var m=GeneratedRuleBatch28Test.fixture(config);if(p.equals(B))m.endTurn(A);return m;}
    static CardInstance attach(ServerMatch m,UUID p,UUID host){var card=GeneratedRuleBatch28Test.hand(m,p,"energy");m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,card.instanceId(),p,host);return card;}
    static void select(ServerMatch m,UUID p,List<UUID> ids){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),ids);}
    static void atomic(ServerMatch m,Runnable action){var before=m.clientView(null);var random=m.randomTranscript();GeneratedRuleBatch8Test.reject(action);check(before.equals(m.clientView(null))&&random.equals(m.randomTranscript()),"Invalid typed payment mutated state/RNG");}
    public static void main(String[] args){printedUnits();sourceAfterSwitch();cardCount();ai();System.out.println("TypedEnergyDiscardSourceTest passed: "+CASES.size()+" source-bound repairs, both seats, 1/2/3 units on one physical card, partial/empty resolution, exact Energy types, foreign/duplicate rejection, original source after switching, explicit card-count distinction and 16 four-tier matches");}
    static void printedUnits(){for(var c:CASES)for(UUID p:List.of(A,B))for(int units:List.of(0,1,2,3)){
        var m=game(c,p,false,Math.max(1,units));CardInstance selected=units==0?null:attach(m,p,active(m,p));var otherCard=attach(m,p,m.benchPokemonView(p).getFirst().pokemonId());
        m.attack(p,"hit");
        if(m.hasPendingCardSelection()){
            atomic(m,()->select(m,p,List.of(otherCard.instanceId())));
            if(selected!=null){atomic(m,()->select(m,p,List.of(selected.instanceId(),selected.instanceId())));select(m,p,List.of(selected.instanceId()));}
            else select(m,p,List.of());
        }else check(selected==null,"Typed discard failed to issue source prompt "+c.id);
        check(selected==null||m.privateView(p).discard().contains(selected),"One physical multi-unit card could not pay printed units "+c.id);
        check(m.benchPokemonView(p).getFirst().attachedEnergyIds().contains(otherCard.instanceId())&&m.currentPlayer().equals(other(p)),"Typed payment used another host or failed to end turn "+c.id);
    }}
    static void sourceAfterSwitch(){var c=CASES.stream().filter(x->x.effect.get("amount").getAsInt()==2).findFirst().orElseThrow();for(UUID p:List.of(A,B)){
        var m=game(c,p,true,3);UUID old=active(m,p),replacement=m.benchPokemonView(p).getFirst().pokemonId();var sourceEnergy=attach(m,p,old);var otherEnergy=attach(m,p,replacement);m.attack(p,"hit");
        var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),replacement);
        atomic(m,()->select(m,p,List.of(otherEnergy.instanceId())));select(m,p,List.of(sourceEnergy.instanceId()));
        check(active(m,p).equals(replacement)&&m.activePokemonView(p).attachedEnergyIds().contains(otherEnergy.instanceId())&&m.privateView(p).discard().contains(sourceEnergy),"This Pokemon incorrectly rebound to the new Active");
    }}
    static void cardCount(){var original=CASES.stream().filter(x->x.effect.get("amount").getAsInt()==2).findFirst().orElseThrow();var e=original.effect.deepCopy();e.addProperty("count_mode","CARDS");var c=new Case("explicit-two-energy-cards",e);
        var m=game(c,A,false,3);var first=attach(m,A,active(m,A));var second=attach(m,A,active(m,A));m.attack(A,"hit");atomic(m,()->select(m,A,List.of(first.instanceId())));select(m,A,List.of(first.instanceId(),second.instanceId()));check(m.privateView(A).discard().containsAll(List.of(first,second)),"Explicit two-card instruction was treated as two units");
    }
    static void ai(){var c=CASES.stream().filter(x->x.effect.get("amount").getAsInt()==3).findFirst().orElseThrow();for(UUID p:List.of(A,B))for(var level:AiDifficulty.values())for(boolean swap:List.of(false,true)){
        var m=game(c,p,swap,3);attach(m,p,active(m,p));attach(m,p,m.benchPokemonView(p).getFirst().pokemonId());var s=MatchSession.multiplayer(m,A,B);var v=s.view(p,p);var option=v.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK).findFirst().orElseThrow();s.submit(p,p,v.revision(),option.index());AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Typed discard AI stalled "+level);
    }}
}
