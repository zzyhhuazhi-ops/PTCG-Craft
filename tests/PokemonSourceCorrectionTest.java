import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Actual corrected programs, including the VSTAR entry point and later recipients. */
public final class PokemonSourceCorrectionTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Map<String,PokemonProfile> PROFILES=load();
    static Map<String,PokemonProfile> load(){try{var result=new HashMap<String,PokemonProfile>();for(String line:Files.readAllLines(Path.of("outputs/pokemon-source-correction-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();result.put(r.get("id").getAsString(),BundledCombatData.pokemon(r.getAsJsonObject("conversion").getAsJsonObject("combatEntry"),EffectRegistry.standard()));}return result;}catch(Exception e){throw new RuntimeException(e);}}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static AbilityDefinition shield(){return BundledCombatData.load().pokemon("swsh12-5_en_099").orElseThrow().abilities().getFirst();}
    public static void main(String[] args){kricketune();abyss();shieldStar();schema();ai();System.out.println("PokemonSourceCorrectionTest passed: exact Kricketune exclusion/nonstacking; public Star Abyss selection and shared VSTAR; Shield Star damage order, current/future recipients, source departure, expiry, bypass, both seats and four-tier complete matches");}
    static void kricketune(){for(String id:List.of("swsh12-5a_en_002","swsh10_en_010")){
        var ability=PROFILES.get(id).abilities().getFirst();
        var m=ModernEnergyRepairTest.game(DynamicPassiveModifierTest.plainTool(),List.of(ability),GeneratedRuleBatch8Test.hit(100,false),EnergyType.GRASS,0,Map.of("basic","Kricketune","wrongmon","Kricketune V"),Map.of("wrongmon",Set.of(EnergyType.GRASS)));
        var card=ModernRuleRepair2Test.hand(m,A,"wrongmon");m.playBasicPokemonToBench(A,card.instanceId());
        var v=m.benchPokemonView(A).stream().filter(p->p.topCardId().equals("wrongmon")).findFirst().orElseThrow();
        check(m.activePokemonView(A).hitPoints()==500&&m.benchPokemonView(A).getFirst().hitPoints()==500,"Swelling Tune boosted exact Kricketune");
        check(v.hitPoints()==540,"Swelling Tune excluded Kricketune V or stacked twice");
    }}
    static void abyss(){for(String id:List.of("swsh12-5a_en_050","swsh10_en_099"))for(int count:List.of(1,2)){
        var ability=PROFILES.get(id).abilities().getFirst();var other=LegacyPokemonProgramRepairTest.ability("swsh12-5a_en_070");
        var item=DeckDiscardTriggeredAbilityTest.trainer("ITEM","{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}");
        var m=ModernEnergyRepairTest.game(DynamicPassiveModifierTest.plainTool(),List.of(ability,other),GeneratedRuleBatch8Test.hit(100,false),EnergyType.DARKNESS,0,Map.of(),Map.of(),Map.of("item",item));
        check(!m.canUseAbility(A,active(m,A),ability.id()),"Star Abyss usable with empty public discard");
        var stock=new ArrayList<CardInstance>();for(String zone:List.of("hand","deck"))for(var card:List.copyOf(PairedTrainerPlayTest.zone(m,A,zone)))if(card.definitionId().equals("item")&&stock.size()<2){PairedTrainerPlayTest.zone(m,A,zone).remove(card);stock.add(card);}
        check(stock.size()==2,"Missing two Item fixture cards");PairedTrainerPlayTest.zone(m,A,"hand").addAll(stock);
        var wrong=ModernRuleRepair2Test.hand(m,A,"trainer");var discard=new ArrayList<>(stock);discard.add(wrong);m.discardHandCardsByEffect(A,A,discard.stream().map(CardInstance::instanceId).toList());
        m.useAbility(A,active(m,A),ability.id());var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==2&&new HashSet<>(q.candidates()).equals(new HashSet<>(stock)),"Star Abyss selection range or Item filter wrong");
        reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));var chosen=stock.subList(0,count);m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList());
        check(m.privateView(A).hand().containsAll(chosen)&&m.privateView(A).discard().contains(wrong)&&!m.hasPendingEffectChoice(),"Star Abyss physical recovery failed");
        check(!m.canUseAbility(A,m.benchPokemonView(A).getFirst().pokemonId(),other.id()),"Star Abyss did not consume shared VSTAR");
    }}
    static ServerMatch shieldGame(UUID owner,String mode){var c=new TurnDamageReductionTest.Config();c.abilities=List.of(shield());c.types=Set.of(EnergyType.FIRE);c.evolvedTypes=Set.of(EnergyType.WATER);
        EnergyType type=mode.equals("WEAK")?EnergyType.GRASS:mode.equals("RESIST")?EnergyType.FIGHTING:EnergyType.COLORLESS;
        String extra=mode.equals("BYPASS")?",\"ignoresActiveDefenderDamageEffects\":true":mode.equals("BENCH")?",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"":"";
        c.hit=TurnDamageReductionTest.hit(180,type,extra);var m=TurnDamageReductionTest.game(c);if(owner.equals(B))m.endTurn(A);return m;}
    static void shieldStar(){for(UUID owner:List.of(A,B))for(String mode:List.of("PLAIN","WEAK","RESIST","BYPASS","BENCH","LATE","EVOLVE","SOURCE_LEAVES","EXPIRED")){
        var m=shieldGame(owner,mode);UUID opponent=owner.equals(A)?B:A;UUID source=m.benchPokemonView(owner).getFirst().pokemonId();m.useAbility(owner,source,shield().id());
        check(!m.canUseAbility(owner,active(m,owner),shield().id()),"Shield Star reused on another source");
        if(mode.equals("SOURCE_LEAVES"))m.movePokemonByEffect(owner,owner,source,LeavePlayDestination.HAND);
        if(mode.equals("LATE")){var card=TurnDamageReductionTest.hand(m,owner,owner.equals(A)?"own":"opponent");m.playBasicPokemonToBench(owner,card.instanceId());m.switchActiveByEffect(owner,owner,PokemonIdentityTestSupport.logical(m,owner,card.instanceId()));}
        if(mode.equals("EVOLVE"))TurnDamageReductionTest.evolve(m,owner);
        m.endTurn(owner);if(mode.equals("EXPIRED")){m.endTurn(opponent);m.endTurn(owner);}m.attack(opponent,"hit");
        int expected=switch(mode){case "WEAK"->260;case "RESIST"->50;case "BYPASS","EXPIRED"->180;default->80;};
        check(m.activePokemonView(owner).damage()==expected,"Shield Star wrong layer/recipient/expiry: "+mode);
        if(mode.equals("BENCH"))check(m.benchPokemonView(owner).getFirst().damage()==80,"Shield Star missed Bench");
    }}
    static void schema(){ActivatedAbilityRules.validate(shield());var entry=GeneratedRuleBatch8Test.entry("swsh12-5_en_099").deepCopy();var ability=entry.getAsJsonArray("abilities").get(0).getAsJsonObject();ability.remove("usageLimit");reject(()->ActivatedAbilityRules.validate(BundledCombatData.pokemon(entry,EffectRegistry.standard()).abilities().getFirst()));}
    static void ai(){for(UUID owner:List.of(A,B))for(var level:AiDifficulty.values()){var m=shieldGame(owner,"PLAIN");m.useAbility(owner,active(m,owner),shield().id());var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"Shield Star AI stalled: "+level);}}
}
