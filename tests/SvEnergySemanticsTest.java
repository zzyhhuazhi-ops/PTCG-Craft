import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** SV-and-later printed Energy is counted as units; coin results share one final selection. */
public final class SvEnergySemanticsTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final Set<String> COINS=Set.of("me1_en_030","sv9_en_090","me5-5_en_099","sv1_en_117","sv8-5_en_082","svalt_en_228");
    static final Map<String,JsonObject> RAW=load();
    static Map<String,JsonObject> load(){try{var out=new HashMap<String,JsonObject>();for(String line:Files.readAllLines(Path.of("outputs/pokemon-sv-energy-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();out.put(r.get("id").getAsString(),r.getAsJsonObject("conversion").getAsJsonObject("combatEntry"));}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void select(ServerMatch m,UUID p,List<CardInstance> cards){CoinEnergyDiscardTest.select(m,p,cards);}
    static ServerMatch game(AttackDefinition a,UUID p,int units,EnergyType type){var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),units==0?new EnergyProfile(List.of()):EnergyProfile.fixed(type,units),a);if(p.equals(B))m.endTurn(A);return m;}
    static AttackDefinition coinAttack(String id){var a=RAW.get(id).getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();a.add("energyCost",new JsonObject());a.addProperty("damageType","COLORLESS");return BundledCombatData.attack(a,EffectRegistry.standard());}
    public static void main(String[] args)throws Exception{leaves();coinCases();largeResult();System.out.println("SvEnergySemanticsTest passed: 59 source-reviewed leaf corrections, both seats, zero/multi-unit cards, original source after switching; six printed coin attacks with 0/1/2/3 heads, partial payment and whole-card selection; >60-unit and 120-card transaction bounds; 48 four-tier completed matches");}
    static void leaves()throws Exception{int checked=0;for(var value:JsonParser.parseString(Files.readString(Path.of("outputs/pokemon-energy-semantics-diff.json"))).getAsJsonArray()){
        var row=value.getAsJsonObject();if(COINS.contains(row.get("id").getAsString()))continue;checked++;
        for(var change:row.getAsJsonArray("changes")){var effect=change.getAsJsonObject().getAsJsonObject("after");String type=effect.get("type").getAsString();boolean move=type.equals("move_energy"),opponent=effect.get("target").getAsString().equals("OPPONENT_ACTIVE");EnergyType energy=effect.has("energy_type")?EnergyType.valueOf(effect.get("energy_type").getAsString()):EnergyType.COLORLESS;
            for(UUID p:List.of(A,B))for(int units:List.of(0,2))for(boolean swap:opponent?List.of(false):List.of(false,true)){
                var raw=JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"能量操作\",\"energyCost\":{},\"baseDamage\":20,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+(swap?"{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"},":"")+effect+"]}]}").getAsJsonObject();var m=game(BundledCombatData.attack(raw,EffectRegistry.standard()),p,units,energy);UUID owner=opponent?other(p):p,source=active(m,owner),otherHost=bench(m,owner);var chosen=CoinEnergyDiscardTest.attach(m,owner,source,"special");var decoy=CoinEnergyDiscardTest.attach(m,owner,otherHost,"special");if(move&&swap)m.playBasicPokemonToBench(p,DynamicPassiveModifierTest.hand(m,p,"basic").instanceId());m.attack(p,"hit");
                if(swap){var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),otherHost);}
                boolean eligible=units>0||effect.has("all")&&!effect.has("energy_type");
                if(m.hasPendingCardSelection()){if(eligible){AttackDiscardUnitsTest.atomic(m,()->select(m,p,List.of(decoy)));select(m,p,List.of(chosen));}else select(m,p,List.of());}
                if(move&&eligible){var q=m.effectAttachmentPrompt(p);UUID destination=swap?m.benchPokemonView(p).stream().map(v->v.pokemonId()).filter(v->!v.equals(source)).findFirst().orElseThrow():otherHost;m.chooseEffectAttachmentTarget(p,q.resolutionId(),q.step(),destination);}
                if(!move)check(m.privateView(owner).discard().contains(chosen)==eligible,"Discard source/unit mismatch "+row.get("id"));
                else if(eligible)check(m.benchPokemonView(owner).stream().anyMatch(v->v.attachedEnergyIds().contains(chosen.instanceId())),"Move did not retain original source after switching");
                check(!m.privateView(owner).discard().contains(decoy)&&m.currentPlayer().equals(other(p)),"Operation touched unrelated host or stalled");
            }
        }
    }check(checked==59,"Unexpected narrow correction count");}
    static ServerMatch started(String id,UUID p,int heads,int units,boolean scarce){var attack=coinAttack(id);for(int skip=0;skip<120;skip++){
        var m=game(attack,p,units,EnergyType.COLORLESS);CoinEnergyDiscardTest.attach(m,other(p),active(m,other(p)),"special");if(!scarce)CoinEnergyDiscardTest.attach(m,other(p),active(m,other(p)),"special");CoinEnergyDiscardTest.attach(m,other(p),bench(m,other(p)),"special");for(int i=0;i<skip;i++)m.flipCoinsByEffect(p,1);int from=m.randomTranscript().size();m.attack(p,attack.id());var flips=m.randomTranscript().subList(from,m.randomTranscript().size()).stream().filter(r->r.operation().startsWith("effect-coin:")).toList();if(flips.stream().filter(r->r.result()==0).count()==heads){boolean until=id.startsWith("sv1_")||id.startsWith("sv8-5_")||id.startsWith("svalt_");check(until?flips.size()==heads+1:flips.size()==(id.startsWith("me5-5")?3:2),"Coin count changed");return m;}
    }throw new AssertionError("Coin fixture unavailable "+id+" "+heads);}
    static void coinCases(){for(String id:COINS)for(UUID p:List.of(A,B)){
        for(int heads:List.of(0,1,2)){var m=started(id,p,heads,2,false);if(heads>0){var q=m.effectCardPrompt(p);check(q.candidates().size()==2,"Coin discard included bench/own card");AttackDiscardUnitsTest.atomic(m,()->select(m,p,List.of()));select(m,p,List.of(q.candidates().getFirst()));}check(m.privateView(other(p)).discard().size()==(heads==0?0:1)&&m.currentPlayer().equals(other(p)),"Multi-unit card paid per-head repeats");}
        for(int units:List.of(0,1)){var m=started(id,p,2,units,true);if(m.hasPendingCardSelection())select(m,p,units==0?List.of():List.of(m.effectCardPrompt(p).candidates().getFirst()));check(m.currentPlayer().equals(other(p)),"Partial/zero Energy coin discard stalled");}
        if(id.startsWith("me5-5")||id.startsWith("sv1_")){var m=started(id,p,3,2,false);var cards=m.effectCardPrompt(p).candidates();AttackDiscardUnitsTest.atomic(m,()->select(m,p,List.of(cards.getFirst())));select(m,p,cards);check(m.currentPlayer().equals(other(p)),"Three heads failed");}
        for(var level:AiDifficulty.values()){var m=started(id,p,2,2,false);var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"Coin selection stalled "+level);}
    }}
    static void largeResult(){var e=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"discard_energy\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":60,\"minimum\":121,\"count_mode\":\"UNITS\",\"amount_mode\":\"COIN_HEADS\",\"coin\":\"heads\"}").getAsJsonObject());check(AttachedEnergyEffect.from(e).minimum()==121,"Dynamic unit minimum was capped at physical card count");var candidates=new ArrayList<EffectSelectionTransaction.Candidate>();for(int i=0;i<120;i++)candidates.add(new EffectSelectionTransaction.Candidate(new UUID(965,i),new EffectTargetResolution.PokemonTarget(B,new UUID(0,5))));var tx=EffectSelectionTransaction.openForCoinEnergy(A,1,1,120,e,candidates);check(tx.submit(A,tx.transactionId(),1,candidates.stream().map(EffectSelectionTransaction.Candidate::id).toList()).size()==120,"Conserved foreign-owner Energy cannot be selected");GeneratedRuleBatch8Test.reject(()->EffectSelectionTransaction.openForCoinEnergy(A,1,1,121,e,candidates));GeneratedRuleBatch8Test.reject(()->EffectSelectionTransaction.openForCoinEnergy(B,1,1,120,e,candidates));}
}
