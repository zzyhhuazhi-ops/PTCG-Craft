import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch25Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch game(String id){return NonPokemonBatch2Test.game(id,EnergyType.GRASS,1);}
    static void play(ServerMatch m){m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);}
    static void choose(ServerMatch m,UUID player,CardInstance card){var q=m.effectCardPrompt(player);m.submitEffectCards(player,q.resolutionId(),q.step(),List.of(card.instanceId()));}
    public static void main(String[] args){jessie();recovery();gates();seas();belt();scheme();ticket();radar();System.out.println("NonPokemonBatch25Test passed: chooser and ownership, empty/short hands, prize and knockout gates, typed healing, evolution-dependent HP/damage, choice branches, private shuffled-deck top and GX/legacy-EX search");}
    static void radar(){
        for(PokemonFamily family:List.of(PokemonFamily.GX,PokemonFamily.EX_LEGACY,PokemonFamily.EX)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("sm10_en_169").orElseThrow();c.ownFamilies=Set.of(family,PokemonFamily.RULE_BOX);c.ownTypes=Set.of(EnergyType.LIGHTNING);var m=GeneratedRuleBatch28Test.fixture(c);play(m);var q=m.effectCardPrompt(A);m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().limit(2).map(CardInstance::instanceId).toList());
            if(family!=PokemonFamily.EX){q=m.effectCardPrompt(A);check(!q.candidates().isEmpty()&&q.candidates().stream().allMatch(x->m.cardDefinition(x.definitionId()).pokemonTypes().contains(EnergyType.LIGHTNING)&&m.cardDefinition(x.definitionId()).pokemonFamilies().contains(family)),"Radar accepted wrong type/family");}
            else if(m.hasPendingCardSelection())check(m.effectCardPrompt(A).candidates().isEmpty(),"Radar confused modern ex with legacy EX");NonPokemonBatch2Test.settle(m);
        }
    }
    static void jessie(){
        for(int count:List.of(0,1,4)){
            var m=game("sm11-5_en_058");var card=NonPokemonBatch2Test.trainer(m);NonPokemonBatch2Test.trim(m,A,4,card);NonPokemonBatch2Test.trim(m,B,count,null);play(m);
            if(count>0){check(m.pendingChoicePlayer().equals(B),"Jessie & James did not choose opponent first");var q=m.effectCardPrompt(B);check(q.minimum()==Math.min(2,count),"Short hand discard not clamped");GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().limit(q.minimum()).map(CardInstance::instanceId).toList()));m.submitEffectCards(B,q.resolutionId(),q.step(),q.candidates().stream().limit(q.minimum()).map(CardInstance::instanceId).toList());}
            NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().size()==1&&m.privateView(B).hand().size()==Math.max(0,count-2),"Jessie & James discarded wrong number");
        }
    }
    static void recovery(){
        for(boolean buddy:List.of(false,true)){
            var m=game(buddy?"xy8_en_135":"sm10_en_187");var own=DynamicPassiveModifierTest.hand(m,A,"basic");var other=DynamicPassiveModifierTest.hand(m,B,"basic");var energy=DynamicPassiveModifierTest.hand(m,B,"energy");
            m.discardHandCardsByEffect(A,A,List.of(own.instanceId()));m.discardHandCardsByEffect(A,B,List.of(other.instanceId(),energy.instanceId()));play(m);
            UUID chooser=buddy?B:A;check(m.pendingChoicePlayer().equals(chooser),"Recovery gave choice to wrong player");var q=m.effectCardPrompt(chooser);check(q.candidates().contains(energy)!=buddy,"Recovery ignored category filter");choose(m,chooser,buddy?other:energy);
            if(buddy)choose(m,A,own);NonPokemonBatch2Test.settle(m);
            check(m.privateView(B).hand().contains(buddy?other:energy)&&!m.privateView(A).hand().contains(other)&&!m.privateView(A).hand().contains(energy),"Opponent card returned to wrong owner");
            check(m.privateView(A).hand().contains(own)==buddy,"Recovery changed unrelated own discard");
        }
        var m=game("xy8_en_135");var own=DynamicPassiveModifierTest.hand(m,A,"evolved");m.discardHandCardsByEffect(A,A,List.of(own.instanceId()));play(m);choose(m,A,own);NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().contains(own),"Buddy-Buddy Rescue failed when opponent has no Pokemon");
    }
    static void gates(){
        for(String id:List.of("xy5_en_141","xy7_en_069")){
            var m=game(id);check(!m.canPlayTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null),"Ungated Supporter accepted");m.endTurn(A);m.knockOutPokemonByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());UnrestrictedAttackPrizeTest.take(m,B);m.endTurn(B);
            check(m.canPlayTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null),"Effect KO/prize deficit gate rejected");play(m);
            if(id.equals("xy5_en_141")){var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==2,"Teammates must select one or two unrestricted cards");}
            NonPokemonBatch2Test.settle(m);if(id.equals("xy7_en_069"))check(m.privateView(A).hand().size()==6&&m.privateView(B).hand().size()==3,"Ace Trainer wrong reset amounts");
            m.endTurn(A);m.endTurn(B);if(id.equals("xy5_en_141"))check(!m.canPlayTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null),"Teammates retained stale KO");
        }
    }
    static void seas(){
        for(EnergyType type:List.of(EnergyType.WATER,EnergyType.LIGHTNING,EnergyType.GRASS)){
            var m=NonPokemonBatch2Test.game("xy5_en_137",type,1);play(m);for(UUID p:List.of(A,B))m.placeDamageCountersByEffect(A,p,m.activePokemonView(p).pokemonId(),5);m.placeDamageCountersByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),2);
            if(type==EnergyType.GRASS){check(!m.canActivateStadium(A),"Rough Seas healed wrong type");continue;}
            m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());NonPokemonBatch2Test.settle(m);
            check(m.activePokemonView(A).damage()==20&&m.benchPokemonView(A).getFirst().damage()==0&&m.activePokemonView(B).damage()==50&&!m.canActivateStadium(A),"Rough Seas healed opponent or ignored per-player use");
            m.endTurn(A);check(m.canActivateStadium(B),"Rough Seas shared use limit across players");
        }
    }
    static void belt(){
        for(boolean evolve:List.of(false,true)){
            var m=game("xy9_en_099");var host=m.activePokemonView(A).pokemonId();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),host);check(m.activePokemonView(A).hitPoints()==540,"Belt missed Basic HP");
            if(evolve)m.evolveByEffect(A,host,DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            check(m.activePokemonView(A).hitPoints()==(evolve?500:540),"Belt did not update after evolution");m.attack(A,"hit");check(m.activePokemonView(B).damage()==(evolve?10:20),"Belt boost did not follow Basic stage");
        }
    }
    static void scheme(){
        for(int option:List.of(0,1)){
            var m=game("xy8_en_138");var card=NonPokemonBatch2Test.trainer(m);NonPokemonBatch2Test.trim(m,A,2,card);play(m);var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),option);NonPokemonBatch2Test.settle(m);
            check(m.privateView(A).hand().size()==(option==0?5:1),"Giovanni mixed mutually exclusive options");m.attack(A,"hit");check(m.activePokemonView(B).damage()==(option==0?10:30),"Giovanni wrong damage option");m.endTurn(B);m.attack(A,"hit");check(m.activePokemonView(B).damage()==(option==0?20:40),"Giovanni bonus survived turn end");
        }
    }
    static void ticket(){
        boolean heads=false,tails=false;for(int seed=0;seed<40&&!(heads&&tails);seed++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("xy8_en_147").orElseThrow(),seed,EnergyType.GRASS);int before=m.randomTranscript().size();play(m);boolean head=GeneratedRuleBatch23Test.coinResult(m,before)==0;heads|=head;tails|=!head;
            if(head){var q=m.effectCardPrompt(A);var card=q.candidates().getFirst();choose(m,A,card);NonPokemonBatch2Test.settle(m);check(m.privateView(A).deck().getLast().equals(card),"Reserved Ticket shuffled chosen card away from top");}else NonPokemonBatch2Test.settle(m);
        }check(heads&&tails,"Ticket missed a coin branch");
    }
}
