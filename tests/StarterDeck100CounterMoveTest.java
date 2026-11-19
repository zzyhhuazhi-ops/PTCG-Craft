import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

public final class StarterDeck100CounterMoveTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    static void shield(ServerMatch m,UUID owner,UUID host,String origin){var effect=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield(origin,"EFFECTS_ONLY","SELF","LEAVES_PLAY").replace("OPPONENT","ANY")).getAsJsonObject());m.executeEffectProgramByRule(owner,host,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(effect)))),EffectTrigger.ON_CARD_PLAYED);}
    static void notHealed(ServerMatch m,UUID source){var effect=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"draw\",\"amount\":1,\"conditions\":[{\"type\":\"HEALED_THIS_TURN\"}]}").getAsJsonObject());int hand=m.privateView(A).hand().size();m.executeEffectProgramByRule(A,source,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(effect)))),EffectTrigger.ON_CARD_PLAYED);check(m.privateView(A).hand().size()==hand,"moving counters incorrectly marked healing");}
    public static void main(String[] args){meowstic();agatha();knockout();empty();malformed();System.out.println("StarterDeck100CounterMoveTest passed: separately bound hosts, feasible optional quantities, immunity, non-healing semantics, KO and authority");}
    static void meowstic(){for(String mode:List.of("NORMAL","SOURCE_SHIELD","DESTINATION_SHIELD")){
        var m=StarterDeck100PokemonTest.game(190);UUID host=m.activePokemonView(A).pokemonId(),from=m.benchPokemonView(A).getFirst().pokemonId(),to=m.benchPokemonView(B).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,from,2);
        if(mode.equals("SOURCE_SHIELD"))shield(m,A,from,"ABILITY");
        if(mode.equals("DESTINATION_SHIELD")){m.endTurn(A);shield(m,B,to,"ABILITY");m.endTurn(B);}
        StarterDeck100PokemonTest.ability(m,host);var p=m.effectPokemonPrompt(A);check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(from),"Ear Moves offered own Pokemon without counters");
        TopDeckLookTest.rejects(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),from));TopDeckLookTest.rejects(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),host));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),from);
        var q=m.effectPokemonPrompt(A);check(q.targets().size()==2,"Ear Moves did not offer both opposing positions");TopDeckLookTest.rejects(()->m.chooseEffectPokemon(A,q.resolutionId(),q.step(),from));m.chooseEffectPokemon(A,q.resolutionId(),q.step(),to);
        check(StarterDeck100PokemonTest.view(m,A,from).damage()==(mode.equals("SOURCE_SHIELD")?20:10),"Ear Moves source removal failed: "+mode);
        check(StarterDeck100PokemonTest.view(m,B,to).damage()==(mode.equals("NORMAL")?10:0),"Ear Moves destination protection failed: "+mode);
        TopDeckLookTest.rejects(()->StarterDeck100PokemonTest.ability(m,host));notHealed(m,from);
    }}
    static void agatha(){for(int available:List.of(1,2,5))for(int chosen=1;chosen<=Math.min(3,available);chosen++){
        var m=StarterDeck100PokemonTest.game(1,id(390));UUID host=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,host,available);
        m.playTrainer(A,StarterDeck100PokemonTest.hand(m,id(390)).instanceId(),null);int limit=Math.min(3,available);
        if(limit>1){var p=m.effectChoicePrompt(A);check(p.optionsZh().size()==limit,"Agatha offered zero or unpayable counter quantities");var before=m.clientView(A);TopDeckLookTest.rejects(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),0));TopDeckLookTest.rejects(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),limit));check(before.equals(m.clientView(A)),"forged amount or actor mutated state");m.chooseEffectOption(A,p.resolutionId(),p.step(),chosen-1);TopDeckLookTest.rejects(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),0));}
        check(m.activePokemonView(A).damage()==(available-chosen)*10&&m.activePokemonView(B).damage()==chosen*10,"Agatha moved wrong count");check(!m.mayPlaySupporter(A),"choice did not consume Supporter");notHealed(m,host);
    }
        var protectedGame=StarterDeck100PokemonTest.game(1,id(390));UUID host=protectedGame.activePokemonView(A).pokemonId();protectedGame.placeDamageCountersByEffect(A,A,host,3);protectedGame.endTurn(A);shield(protectedGame,B,protectedGame.activePokemonView(B).pokemonId(),"TRAINER");protectedGame.endTurn(B);
        protectedGame.playTrainer(A,StarterDeck100PokemonTest.hand(protectedGame,id(390)).instanceId(),null);var p=protectedGame.effectChoicePrompt(A);protectedGame.chooseEffectOption(A,p.resolutionId(),p.step(),2);check(protectedGame.activePokemonView(A).damage()==0&&protectedGame.activePokemonView(B).damage()==0,"immune destination put moved counters back on source");notHealed(protectedGame,host);
    }
    static void knockout(){var m=StarterDeck100PokemonTest.game(190);UUID host=m.activePokemonView(A).pokemonId(),target=m.benchPokemonView(B).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,host,1);var victim=StarterDeck100PokemonTest.view(m,B,target);m.placeDamageCountersByEffect(A,B,target,(victim.hitPoints()-10)/10);int prizes=m.publicView().get(A).prizeCount();StarterDeck100PokemonTest.ability(m,host);StarterDeck100PokemonTest.choosePokemon(m,host);StarterDeck100PokemonTest.choosePokemon(m,target);StarterDeck100CombatTest.settleKnockouts(m);check(m.publicView().get(A).prizeCount()==prizes-2,"counter move KO did not award V prizes");}
    static void empty(){var m=StarterDeck100PokemonTest.game(190);TopDeckLookTest.rejects(()->StarterDeck100PokemonTest.ability(m,m.activePokemonView(A).pokemonId()));var a=StarterDeck100PokemonTest.game(1,id(390));var card=StarterDeck100PokemonTest.hand(a,id(390));var before=a.clientView(A);TopDeckLookTest.rejects(()->a.playTrainer(A,card.instanceId(),null));check(before.equals(a.clientView(A)),"Agatha played with no counters");}
    static void malformed(){for(String json:List.of("{\"type\":\"move_damage_counter\",\"target\":\"OWN_ACTIVE\",\"destination\":\"OPPONENT_ACTIVE\",\"amount\":3,\"up_to\":false}","{\"type\":\"move_damage_counter\",\"target\":\"SELF\",\"destination\":\"OPPONENT_ACTIVE\",\"amount\":1,\"source_ref\":\"BAD\"}"))TopDeckLookTest.rejects(()->EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject()));
        var effect=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"move_damage_counter\",\"target\":\"SELF\",\"destination\":\"OPPONENT_ACTIVE\",\"amount\":1,\"source_ref\":\"missing\"}").getAsJsonObject());TopDeckLookTest.rejects(()->AttackPokemonBindings.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ONCE_PER_TURN,List.of(),List.of(effect)))),EffectTrigger.ONCE_PER_TURN));
    }
}
