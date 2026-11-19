import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.*;
import java.time.*;
import java.util.*;

public final class CardHistoryTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var owner=UUID.randomUUID();var other=UUID.randomUUID();
        var clock=Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"),ZoneOffset.UTC);
        var cards=new CardBoxService(clock);
        cards.mint(owner,"a");cards.mint(owner,"a");cards.mint(other,"a");
        cards.addHistory(owner,"a",1,1,2);
        check(cards.history(owner,"a").firstAcquired()==clock.millis()&&cards.history(owner,"a").matches()==1,"Shared history / timestamp");
        check(cards.history(other,"a").matches()==0&&cards.history(owner,"b")==null,"Player or print histories mixed");
        var snapshot=cards.snapshot();
        try{cards.transaction(()->{cards.addHistory(owner,"a",5,5,5);cards.mint(owner,"b");throw new IllegalStateException("rollback");});}catch(IllegalStateException expected){}
        check(cards.history(owner,"a").equals(snapshot.histories().stream().filter(h->h.owner().equals(owner)).findFirst().orElseThrow())&&cards.history(owner,"b")==null,"Transaction leaked history");
        var reload=CardBoxService.restore(Clock.offset(clock,Duration.ofDays(5)),cards.snapshot());reload.mint(owner,"a");
        check(reload.history(owner,"a").equals(cards.history(owner,"a")),"Reload / duplicate reset history");
        var legacy=CardBoxService.restore(clock,new CardBoxService.Snapshot(List.of(),List.of(),List.of(new CardBoxService.Discovery(owner,"old"))));legacy.mint(owner,"old");
        check(legacy.history(owner,"old").firstAcquired()==0,"Invented legacy date");
        cards.addHistory(owner,"a",Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE);
        check(cards.history(owner,"a").matches()==Long.MAX_VALUE&&cards.history(owner,"a").knockouts()==Long.MAX_VALUE,"Counter overflow");
        attackCounts(); reactions(); unknownSource();
        System.out.println("CardHistoryTest passed: shared print/player isolation, immutable date, rollback, legacy migration, saturation, confirmed KO, replay rejection, attack effects and triggered KO attribution");
    }
    static void attackCounts(){
        var a=KnockoutTriggeredAbilityTest.A;var b=KnockoutTriggeredAbilityTest.B;
        for(String extra:List.of("",",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]")){
            var m=KnockoutTriggeredAbilityTest.match(extra.isEmpty()?300:0,extra);
            var id=PokemonIdentityTestSupport.physical(m,a,m.activePokemonView(a).pokemonId());
            var def=EventTargetBindingTest.field(m).topCard(a,m.activePokemonView(a).pokemonId()).definitionId();
            m.attack(a,"hit");var source=new ServerMatch.HistorySource(a,def);
            check(m.historyKnockouts().getOrDefault(source,0L)==1,"Attack KO source missing: "+m.historyKnockouts());
            try{m.attack(a,"hit");throw new AssertionError("Repeated attack accepted");}catch(IllegalStateException|IllegalArgumentException expected){}
            m.takePrizeAt(a,0);m.chooseReplacement(b,m.benchPokemonView(b).getFirst().pokemonId());
            check(m.historyKnockouts().get(source)==1,"Prize / replacement duplicated KO");
        }
    }
    static void reactions(){
        var a=KnockoutTriggeredAbilityTest.A;var b=KnockoutTriggeredAbilityTest.B;
        var ability=KnockoutTriggeredAbilityTest.ability("history-chain","ANY",true,true,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}");
        var m=KnockoutTriggeredAbilityTest.match(300,"",ability);
        String da=EventTargetBindingTest.field(m).topCard(a,m.activePokemonView(a).pokemonId()).definitionId();
        String db=EventTargetBindingTest.field(m).topCard(b,m.activePokemonView(b).pokemonId()).definitionId();
        m.attack(a,"hit");check(m.historyKnockouts().isEmpty(),"KO counted before mandatory reactions");
        var p=m.triggerOrderPrompt();m.decideTriggeredEffect(b,p.id(),true);p=m.triggerOrderPrompt();m.decideTriggeredEffect(a,p.id(),false);
        check(m.historyKnockouts().getOrDefault(new ServerMatch.HistorySource(a,da),0L)==1&&m.historyKnockouts().getOrDefault(new ServerMatch.HistorySource(b,db),0L)==1,"Reaction source incorrectly credited to turn player: "+m.historyKnockouts());
    }
    static void unknownSource(){
        var m=KnockoutTriggeredAbilityTest.match(0,"");var a=KnockoutTriggeredAbilityTest.A;var b=KnockoutTriggeredAbilityTest.B;
        m.knockOutPokemonByEffect(a,ServerMatch.InPlayTargetScope.OPPONENT_BENCH,b,m.benchPokemonView(b).getFirst().pokemonId());
        check(m.historyKnockouts().isEmpty(),"Source-less rule effect guessed a card");
    }
}
