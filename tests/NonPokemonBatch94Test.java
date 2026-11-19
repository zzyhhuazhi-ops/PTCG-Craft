import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch94Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("swsh9_en_131");
    static final String COIN="{\"type\":\"coin_flip\",\"flips\":3,\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}]}]}";
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();}
    static AttackDefinition nativeAttack(int flips){return AttackCoinMatchTest.nativeAttack("\"coinDamage\":{\"flips\":"+flips+",\"perHeads\":10}");}
    static ServerMatch game(AttackDefinition attack,TrainerProfile stadium,List<AbilityDefinition> abilities){return DynamicPassiveModifierTest.fixture(profile(),stadium,EnergyProfile.basic(EnergyType.COLORLESS),attack,abilities,0,null,Map.of("basic",EnergyType.GRASS,"evolved",EnergyType.GRASS));}
    static ServerMatch game(AttackDefinition attack){return game(attack,DynamicPassiveModifierTest.plainStadium(),List.of());}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static void attach(ServerMatch m,UUID p,UUID host){m.playTrainer(p,hand(m,p,"tool").instanceId(),host);}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.MASTER);}
    static boolean tails(ServerMatch m,int start){return m.randomTranscript().subList(start,m.randomTranscript().size()).stream().anyMatch(r->r.result()==1);}
    static long uses(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().startsWith("已触发打空保险")).count();}
    static void skip(ServerMatch m,UUID p,int count){for(int i=0;i<count;i++)m.flipCoinsByEffect(p,1);}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.attack=nativeAttack(3);c.defenderAttack=GeneratedRuleBatch8Test.hit(200,false);return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){nativeAndPhases();retry();multipleToolsAndAttacks();turnsAndExclusions();shortDecks();lateRemoval();ai();schema();System.out.println("NonPokemonBatch94Test passed: final attack coins across native/program phases, retry discard, until tails, one draw per physical Tool per turn, two Tools/Festival, both owners and turn reset, nonattack/confusion/bench/suppressed exclusions, delayed source removal, short decks and all AI difficulties");}
    static void nativeAndPhases(){
        var attacks=new ArrayList<AttackDefinition>(List.of(nativeAttack(1),nativeAttack(3),
            AttackCoinMatchTest.nativeAttack("\"conditions\":[{\"target\":\"DEFENDER\",\"condition\":\"CONFUSED\",\"coinCondition\":{\"flips\":3,\"requiredHeads\":3}}]"),
            AttackCoinMatchTest.nativeAttack("\"coinProtection\":{\"flips\":3,\"requiredHeads\":3,\"scope\":\"DAMAGE_AND_EFFECTS\"}"),
            BeforeDamageAttackTest.attack(COIN,"",""),BeforeDamageAttackTest.attack("",COIN.replace("damage_counter","damage").replace("amount\":1","amount\":10"),""),BeforeDamageAttackTest.attack("","",COIN),
            BeforeDamageAttackTest.attack(COIN,"",COIN),certainTail()));
        for(var attack:attacks)for(UUID p:List.of(A,B)){
            boolean sawTails=false,sawHeads=false;
            for(int offset=0;offset<20;offset++){
                var m=game(attack);if(p.equals(B))m.endTurn(A);attach(m,p,m.activePokemonView(p).pokemonId());skip(m,p,offset);
                int held=m.privateView(p).hand().size(),n=m.randomTranscript().size();m.attack(p,attack.id());boolean tail=tails(m,n);settle(m);
                check(m.privateView(p).hand().size()==held+(tail?3:0)&&uses(m)==(tail?1:0),"Attack coin draw wrong: "+attack.id()+" "+offset+" "+p);sawTails|=tail;sawHeads|=!tail;
                if(sawTails&&sawHeads)break;
            }
            check(sawTails,"Missing attack tails coverage");
        }
    }
    static void retry(){Set<String> seen=new HashSet<>();for(int offset=0;offset<32&&seen.size()<4;offset++){
        var m=game(nativeAttack(1),NonPokemonBatch2Test.DATA.trainer("swsh3_en_162").orElseThrow(),List.of());attach(m,A,m.activePokemonView(A).pokemonId());m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);skip(m,A,offset);
        int held=m.privateView(A).hand().size(),n=m.randomTranscript().size();m.attack(A,"hit");check(m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==held&&uses(m)==0,"Provisional coins triggered Tool");boolean original=tails(m,n);var q=m.effectChoicePrompt(A);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),0));check(rng.equals(m.randomTranscript()),"Foreign retry rolled coins");
        NonPokemonBatch63Test.choose(m,A,0);boolean finalTails=tails(m,n+1);settle(m);seen.add(original+"/"+finalTails);
        check(m.randomTranscript().size()==n+2&&m.privateView(A).hand().size()==held+(finalTails?3:0)&&uses(m)==(finalTails?1:0),"Discarded retry outcome used");GeneratedRuleBatch8Test.reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),0));
    }check(seen.size()==4,"Retry branch coverage: "+seen);}
    static List<AbilityDefinition> capacity(){return List.of(BundledCombatData.ability(JsonParser.parseString("{\"id\":\"capacity\",\"nameZh\":\"Capacity fixture\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"tool_capacity\",\"amount\":2}]}").getAsJsonObject(),EffectRegistry.standard()));}
    static AttackDefinition certainTail(){return BeforeDamageAttackTest.attack("","","{\"type\":\"coin_flip\",\"until\":\"TAILS\",\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}]}]}");}
    static void multipleToolsAndAttacks(){for(int copies:List.of(1,2))for(boolean festival:List.of(false,true)){
        var abilities=new ArrayList<>(capacity());if(festival)abilities.addAll(NonPokemonBatch2Test.DATA.pokemon("sv6_en_018").orElseThrow().abilities());
        var m=game(certainTail(),festival?NonPokemonBatch2Test.DATA.trainer("sv6_en_149").orElseThrow():DynamicPassiveModifierTest.plainStadium(),abilities);for(int i=0;i<copies;i++)attach(m,A,m.activePokemonView(A).pokemonId());if(festival)m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        int held=m.privateView(A).hand().size();m.attack(A,"hit");if(festival){check(m.currentPlayer().equals(A)&&m.privateView(A).hand().size()==held&&uses(m)==copies,"Draw occurred before end of double-attack turn");m.attack(A,"hit");}settle(m);
        check(m.currentPlayer().equals(B)&&m.privateView(A).hand().size()==held+3*copies&&uses(m)==copies,"Tool multiplicity or repeated attack draw incorrect");
    }}
    static void turnsAndExclusions(){var m=game(certainTail());attach(m,A,m.activePokemonView(A).pokemonId());m.attack(A,"hit");settle(m);m.endTurn(B);int held=m.privateView(A).hand().size();m.attack(A,"hit");settle(m);check(uses(m)==2&&m.privateView(A).hand().size()==held+3,"Tool turn limit failed reset");
        for(String mode:List.of("BENCH","DISABLED","REMOVED","NONATTACK")){
            var n=game(certainTail());UUID host=mode.equals("BENCH")?n.benchPokemonView(A).getFirst().pokemonId():n.activePokemonView(A).pokemonId();attach(n,A,host);if(mode.equals("DISABLED"))n.disableToolEffectsByEffect(A,A,host);if(mode.equals("REMOVED"))n.discardToolByEffect(A,A,host,n.activePokemonView(A).attachedTools().getFirst().instanceId());int count=n.privateView(A).hand().size();
            if(mode.equals("NONATTACK")){skip(n,A,20);n.endTurn(A);}else n.attack(A,"hit");settle(n);check(uses(n)==0&&n.privateView(A).hand().size()==count,"Ineligible tails triggered: "+mode);
        }
        boolean failed=false;for(int offset=0;offset<20&&!failed;offset++){var n=game(certainTail());attach(n,A,n.activePokemonView(A).pokemonId());skip(n,A,offset);n.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);int count=n.privateView(A).hand().size(),rng=n.randomTranscript().size();n.attack(A,"hit");settle(n);if(n.randomTranscript().size()==rng+1){failed=true;check(uses(n)==0&&n.privateView(A).hand().size()==count,"Confusion tails qualified");}}check(failed,"Missing failed confusion branch");
    }
    static void shortDecks(){for(int left:List.of(0,1,2,3)){var m=game(certainTail());attach(m,A,m.activePokemonView(A).pokemonId());m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-left);int held=m.privateView(A).hand().size();m.attack(A,"hit");settle(m);check(m.phase()==MatchPhase.TURN&&m.privateView(A).deck().isEmpty()&&m.privateView(A).hand().size()==held+left,"Short draw incorrectly loses or invents cards");}}
    static void lateRemoval(){var abilities=NonPokemonBatch2Test.DATA.pokemon("sv6_en_018").orElseThrow().abilities();var m=game(certainTail(),NonPokemonBatch2Test.DATA.trainer("sv6_en_149").orElseThrow(),abilities);attach(m,A,m.activePokemonView(A).pokemonId());m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);int held=m.privateView(A).hand().size();m.attack(A,"hit");var host=m.activePokemonView(A);m.discardToolByEffect(A,A,host.pokemonId(),host.attachedTools().getFirst().instanceId());m.attack(A,"hit");settle(m);check(m.privateView(A).hand().size()==held+3,"Registered delayed draw lost with Tool removal");}
    static void ai(){for(var d:AiDifficulty.values()){var m=game(certainTail(),DynamicPassiveModifierTest.plainStadium(),capacity());for(int i=0;i<2;i++)attach(m,A,m.activePokemonView(A).pokemonId());int held=m.privateView(A).hand().size();m.attack(A,"hit");AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),d);check(m.currentPlayer().equals(B)&&m.privateView(A).hand().size()==held+6,"AI stalled delayed draw order "+d);}}
    static void schema(){var e=profile().ongoingProgram().triggers().getFirst().effects().getFirst();check(AttackTailsDraw.fromProgram(profile().ongoingProgram()),"Missing printed Tool marker");for(String amount:List.of("0","2","6"))GeneratedRuleBatch8Test.reject(()->AttackTailsDraw.validate(new EffectDefinition(e.type(),e.target(),Map.of("amount",amount),List.of(),List.of())));GeneratedRuleBatch8Test.reject(()->DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"draw_at_turn_end_after_attack_tails\",\"target\":\"SELF\",\"amount\":3}"));GeneratedRuleBatch8Test.reject(()->AttackTailsDraw.fromProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(e))))));}
}
