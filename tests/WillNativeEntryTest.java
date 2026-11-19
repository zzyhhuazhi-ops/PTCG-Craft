import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class WillNativeEntryTest {
    static final UUID A=WillCoinMatchTest.A,B=WillCoinMatchTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TrainerProfile will(){return WillCoinMatchTest.trainer(WillCoinMatchTest.WILL);}
    static ServerMatch dynamic(AttackDefinition attack,TrainerProfile tool,List<AbilityDefinition> abilities){return DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),attack,abilities,0,will(),Map.of("basic",EnergyType.COLORLESS,"evolved",EnergyType.COLORLESS));}
    static CardInstance hand(ServerMatch m,String id){return DynamicPassiveModifierTest.hand(m,A,id);}
    static void play(ServerMatch m){m.playTrainer(A,hand(m,"item").instanceId(),null);}
    static void choose(ServerMatch m,boolean heads){WillCoinMatchTest.choose(m,A,heads);}
    public static void main(String[] args){abilities();retry();blunder();forcedTails();copy();retreat();lucky();greedy();System.out.println("WillNativeEntryTest passed: activated and opponent automatic Ability ownership, actual attack reroll and Blunder Policy, forced tails, coin-copy, retreat gate, Lucky Bonus and Greedy Dice pre-hand coin continuations.");}
    static void abilities(){for(boolean heads:List.of(false,true)){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=will();c.ownAbility=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"coin","nameZh":"ability fixture","kind":"ACTIVATED_ONCE_PER_TURN","effectSupport":"SUPPORTED",
             "triggers":[{"trigger":"ONCE_PER_TURN","effects":[{"type":"coin_flip","flips":1,"heads":[{"type":"draw","amount":1}],"tails":[{"type":"draw","amount":2}]}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var m=GeneratedRuleBatch28Test.fixture(c);WillCoinMatchTest.play(m,A);int n=m.randomTranscript().size(),cards=m.privateView(A).hand().size();m.useAbility(A,m.activePokemonView(A).pokemonId(),"coin");
        check(m.randomTranscript().size()==n,"Activated ability coin bypassed choice");choose(m,heads);check(m.privateView(A).hand().size()==cards+(heads?1:2)&&m.randomTranscript().size()==n+1,"Ability branch wrong");
        WillCoinMatchTest.reject(()->m.useAbility(A,m.activePokemonView(A).pokemonId(),"coin"));
    }
        var ability=DamageTriggeredAbilityTest.ability("coin",false,false,false,"{\"type\":\"coin_flip\",\"flips\":1,\"effects\":[{\"type\":\"draw\",\"amount\":1}]}");
        var m=dynamic(GeneratedRuleBatch8Test.hit(40,false),DynamicPassiveModifierTest.plainTool(),List.of(ability));play(m);int n=m.randomTranscript().size(),cards=m.privateView(B).hand().size();m.attack(A,"hit");
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.randomTranscript().size()==n+1&&m.privateView(B).hand().size()==cards+2,"Opponent automatic coin was attributed to current player or consumed Will");
    }
    static void retry(){var m=dynamic(AttackCoinMatchTest.nativeAttack("\"coinDamage\":{\"flips\":1,\"perHeads\":30}"),AttackCoinMatchTest.tool(),List.of());
        m.playTrainer(A,hand(m,"tool").instanceId(),m.activePokemonView(A).pokemonId());play(m);int n=m.randomTranscript().size();m.attack(A,"hit");choose(m,true);check(m.randomTranscript().size()==n+1&&m.hasPendingEffectChoice(),"Chosen result skipped retry window");
        var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),0);check(m.randomTranscript().size()==n+2&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Retry reused Will or repeated original attack");
    }
    static void blunder(){var m=dynamic(NonPokemonBatch94Test.nativeAttack(1),NonPokemonBatch94Test.profile(),List.of());m.playTrainer(A,hand(m,"tool").instanceId(),m.activePokemonView(A).pokemonId());play(m);int cards=m.privateView(A).hand().size();m.attack(A,"hit");choose(m,false);NonPokemonBatch103Test.settle(m);check(m.privateView(A).hand().size()==cards+3&&NonPokemonBatch94Test.uses(m)==1,"Chosen tails did not trigger exactly one Blunder Policy draw");}
    static void forcedTails(){var c=new GeneratedRuleBatch28Test.Config();c.trainer=will();c.attack=NonPokemonBatch94Test.nativeAttack(1);c.defenderAttack=BeforeDamageAttackTest.attack("","","{\"type\":\"force_opponent_coin_tails\",\"target\":\"OPPONENT_ACTIVE\",\"duration\":\"OPPONENT_NEXT_TURN\"}");var m=GeneratedRuleBatch28Test.fixture(c);m.endTurn(A);m.attack(B,c.defenderAttack.id());WillCoinMatchTest.play(m,A);int n=m.randomTranscript().size();m.attack(A,"hit");choose(m,true);check(m.randomTranscript().get(n).result()==1&&m.activePokemonView(B).damage()==20,"All-tails effect lost precedence over chosen heads");}
    static void copy(){for(boolean heads:List.of(false,true)){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=will();c.attack=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"hit","nameZh":"coin copy fixture","energyCost":{},"baseDamage":0,"effectSupport":"SUPPORTED","triggers":[{"trigger":"ON_ATTACK","effects":[{"type":"coin_copy_attack","target":"OPPONENT_ACTIVE","cost_policy":"DECLARED_ATTACK"}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());c.defenderAttack=NonPokemonBatch94Test.nativeAttack(1);
        var m=GeneratedRuleBatch28Test.fixture(c);WillCoinMatchTest.play(m,A);int n=m.randomTranscript().size();m.attack(A,"hit");check(m.randomTranscript().size()==n,"Copy gate flipped before choice");choose(m,heads);NonPokemonBatch103Test.settle(m);check(m.currentPlayer().equals(B)&&m.randomTranscript().size()==n+(heads?2:1),"Coin-copy gate repeated or copied body's coin reused Will");
    }}
    static void retreat(){for(boolean heads:List.of(false,true)){
        var m=dynamic(GeneratedRuleBatch8Test.hit(40,false),DynamicPassiveModifierTest.plainTool(),List.of(RetreatAttemptCoinGateTest.gate()));var old=m.activePokemonView(A).pokemonId();var replacement=m.benchPokemonView(A).getFirst().pokemonId();var payment=new ArrayList<UUID>();
        for(int i=0;i<3;i++){var card=hand(m,"energy");payment.add(card.instanceId());m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,card.instanceId(),A,old);}play(m);int n=m.randomTranscript().size();m.retreat(A,replacement,payment);check(m.randomTranscript().size()==n&&m.activePokemonView(A).pokemonId().equals(old),"Retreat paid/moved before choice");choose(m,heads);
        check(m.randomTranscript().size()==n+1&&m.activePokemonView(A).pokemonId().equals(heads?replacement:old),"Retreat outcome wrong");check(m.privateView(A).discard().stream().filter(x->payment.contains(x.instanceId())).count()==(heads?3:0),"Retreat failure spent Energy");
    }}
    static void lucky(){for(boolean heads:List.of(false,true)){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=will();c.attack=GeneratedRuleBatch8Test.hit(1000,false);c.ownAbility=NonPokemonBatch2Test.DATA.pokemon("sv3-5_en_113").orElseThrow().ability("lucky-bonus").orElseThrow();var m=GeneratedRuleBatch28Test.fixture(c);
        var card=NonPokemonBatch128Test.prize(m,A,0,"attacker");NonPokemonBatch128Test.prize(m,A,1,"energy");WillCoinMatchTest.play(m,A);m.attack(A,"hit");m.takePrizeAt(A,0);int n=m.randomTranscript().size(),bench=m.benchPokemonView(A).size();m.decideLuckyBonus(A,true);
        check(m.benchPokemonView(A).size()==bench+1&&m.randomTranscript().size()==n&&!m.privateView(A).hand().contains(card),"Lucky placement must precede chosen coin without entering hand");choose(m,heads);NonPokemonBatch103Test.settle(m);check(m.randomTranscript().size()==n+1&&m.privateView(A).prizes().size()==(heads?4:5)&&m.currentPlayer().equals(B),"Lucky coin awarded wrong number or failed to finish original attack");
    }}
    static ServerMatch diceGame(){
        var definitions=new ArrayList<CardDefinition>();var trainers=Map.of("will",will(),"dice",NonPokemonBatch128Test.profile());
        for(String id:List.of("basic","energy","will","dice"))definitions.add(new CardDefinition(id,id,id.equals("basic")?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,
            trainers.containsKey(id)?trainers.get(id).category():TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(300,false)),List.of(),EnergyType.COLORLESS)),Map.of("energy",EnergyType.GRASS),trainers);
        for(int seed=0;seed<300;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){String[] ids={"basic","basic","energy","will","dice"};var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(definitions),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->x.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of("will","dice","energy").stream().allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id))))return m;
        }throw new AssertionError("No Will/Dice fixture");
    }
    static void greedy(){for(boolean heads:List.of(false,true)){
        var m=diceGame();var dice=NonPokemonBatch128Test.prize(m,A,0,"dice");NonPokemonBatch128Test.prize(m,A,1,"energy");m.playTrainer(A,hand(m,"will").instanceId(),null);m.attack(A,"hit");m.takePrizeAt(A,0);int n=m.randomTranscript().size();
        NonPokemonBatch128Test.choose(m,A,0);check(m.randomTranscript().size()==n&&!m.privateView(A).discard().contains(dice),"Greedy Dice flipped or discarded before side selection");
        choose(m,heads);NonPokemonBatch103Test.settle(m);check(m.randomTranscript().size()==n+1&&m.privateView(A).discard().contains(dice)&&m.privateView(A).prizes().size()==(heads?4:5)&&m.currentPlayer().equals(B),"Greedy Dice coin or parent attack resumed incorrectly");
    }}
}
