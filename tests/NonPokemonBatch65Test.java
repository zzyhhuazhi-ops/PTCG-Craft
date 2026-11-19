import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch65Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("swsh3_en_170","swsh4_en_161");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static BattlefieldState.PokemonView pokemon(ServerMatch m,UUID p,UUID host){return m.activePokemonView(p).pokemonId().equals(host)?m.activePokemonView(p):m.benchPokemonView(p).stream().filter(v->v.pokemonId().equals(host)).findFirst().orElseThrow();}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.NORMAL);}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.ownFamilies=c.defenderFamilies=Set.of(PokemonFamily.V);c.ownEvolutionFamilies=c.defenderEvolutionFamilies=Set.of(PokemonFamily.V,PokemonFamily.VMAX);return GeneratedRuleBatch28Test.fixture(c);}
    static ServerMatch spike(List<AbilityDefinition> abilities,AttackDefinition attack){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),profile(IDS.getFirst()),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack,abilities,0,profile("swsh10_en_154"),Map.of("basic",EnergyType.COLORLESS,"evolved",EnergyType.COLORLESS),"Solrock");}
    public static void main(String[] args){movement();timingAndProtection();evolution();deckEvolution();schema();System.out.println("NonPokemonBatch65Test passed: both players and Stadium controllers, only actual outgoing Active during its owner's turn, retreat/effect/attack switching, repeated movement, forced opponent-turn exclusion, Switch Cart healing order, lethal Stadium counters and immunity; exact V-to-VMAX hand evolution versus other families/deck evolution, bounded healing and departure");}
    static void movement(){for(UUID p:List.of(A,B))for(UUID controller:List.of(A,B))for(String mode:List.of("retreat","switch","opponent-turn","removed")){
        var m=spike(List.of(),GeneratedRuleBatch8Test.hit(20,false));if(controller.equals(B))m.endTurn(A);m.playTrainer(controller,hand(m,controller,"stadium").instanceId(),null);if(!m.currentPlayer().equals(p))m.endTurn(m.currentPlayer());UUID host=active(m,p),next=bench(m,p);
        if(mode.equals("removed"))m.discardStadiumByEffect(p);
        if(mode.equals("opponent-turn")){m.endTurn(p);m.switchActiveByEffect(m.currentPlayer(),p,next);}
        else if(mode.equals("retreat")){var first=hand(m,p,"energy");m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,first.instanceId(),p,host);var second=hand(m,p,"special");m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,second.instanceId(),p,host);m.retreat(p,next,List.of(first.instanceId(),second.instanceId()));}
        else m.switchActiveByEffect(p,p,next);
        settle(m);check(pokemon(m,p,host).damage()==(Set.of("removed","opponent-turn").contains(mode)?0:20)&&m.activePokemonView(p).damage()==0,"Spikemuth wrong owner/target: "+p+controller+mode);
        if(mode.equals("switch")){m.switchActiveByEffect(p,p,host);m.switchActiveByEffect(p,p,next);settle(m);check(pokemon(m,p,host).damage()==40&&m.activePokemonView(p).damage()==20,"Spikemuth used once-per-turn limit");}
    }}
    static void timingAndProtection(){
        var m=spike(List.of(),GeneratedRuleBatch8Test.hit(20,false));UUID host=active(m,A);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.placeDamageCountersByEffect(A,A,host,29);m.playTrainer(A,hand(m,A,"item").instanceId(),null);settle(m);check(pokemon(m,A,host).damage()==280,"Stadium KO happened before Switch Cart healed");
        var n=spike(List.of(),GeneratedRuleBatch8Test.hit(20,false));UUID victim=active(n,A);n.playTrainer(A,hand(n,A,"stadium").instanceId(),null);n.placeDamageCountersByEffect(A,A,victim,29);int prizes=n.privateView(B).prizes().size();n.switchActiveByEffect(A,A,bench(n,A));settle(n);check(n.benchPokemonView(A).stream().noneMatch(v->v.pokemonId().equals(victim))&&n.privateView(B).prizes().size()==prizes-1&&n.currentPlayer().equals(A),"Lethal Stadium movement did not settle KO");
        var moon=NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities();var shield=spike(moon,GeneratedRuleBatch8Test.hit(20,false));UUID protectedHost=active(shield,A);shield.playTrainer(A,hand(shield,A,"stadium").instanceId(),null);shield.switchActiveByEffect(A,A,bench(shield,A));settle(shield);check(pokemon(shield,A,protectedHost).damage()==0,"New Moon did not prevent Stadium counters");
        var attack=BeforeDamageAttackTest.attack("","","{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}");var attacking=spike(List.of(),attack);UUID attacker=active(attacking,A);attacking.playTrainer(A,hand(attacking,A,"stadium").instanceId(),null);attacking.attack(A,attack.id());settle(attacking);check(pokemon(attacking,A,attacker).damage()==20&&attacking.currentPlayer().equals(B),"Attack switching missed Stadium event");
    }
    static GeneratedRuleBatch28Test.Config wyndon(boolean fromV,boolean vmax){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(IDS.getLast());c.ownFamilies=c.defenderFamilies=Set.of(fromV?PokemonFamily.V:PokemonFamily.EX);c.ownEvolutionFamilies=c.defenderEvolutionFamilies=Set.of(PokemonFamily.V,vmax?PokemonFamily.VMAX:PokemonFamily.VSTAR);return c;}
    static void evolution(){for(UUID p:List.of(A,B))for(UUID controller:List.of(A,B))for(boolean fromV:List.of(false,true))for(boolean vmax:List.of(false,true))for(int counters:List.of(0,4,15))for(boolean benched:List.of(false,true)){
        var m=GeneratedRuleBatch28Test.fixture(wyndon(fromV,vmax));if(controller.equals(B))m.endTurn(A);m.playTrainer(controller,hand(m,controller,"trainer").instanceId(),null);if(!m.currentPlayer().equals(p))m.endTurn(m.currentPlayer());UUID host=benched?bench(m,p):active(m,p);if(counters>0)m.placeDamageCountersByEffect(p,p,host,counters);
        m.evolve(p,host,hand(m,p,p.equals(A)?"evolved":"defender_evolved").instanceId());settle(m);check(pokemon(m,p,host).damage()==Math.max(0,counters*10-(fromV&&vmax?100:0)),"Wyndon wrong family/owner/healing amount");
    }
        var c=wyndon(true,true);c.ownName="Solrock";c.ownAbility=NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities().getFirst();c.source=c.ownAbility;c.names=Map.of("source","Solrock");
        // Evolution removes an old top card's Ability; use another in-play source for persistent immunity.
        var m=GeneratedRuleBatch28Test.fixture(c);m.playBasicPokemonToBench(A,hand(m,A,"source").instanceId());UUID host=active(m,A);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);m.placeDamageCountersByEffect(A,A,host,15);m.evolve(A,host,hand(m,A,"evolved").instanceId());settle(m);check(m.activePokemonView(A).damage()==150,"Wyndon ignored another source's New Moon");
        var gone=aiGame(IDS.getLast());UUID target=active(gone,A);gone.playTrainer(A,hand(gone,A,"trainer").instanceId(),null);gone.discardStadiumByEffect(A);gone.placeDamageCountersByEffect(A,A,target,15);gone.evolve(A,target,hand(gone,A,"evolved").instanceId());settle(gone);check(gone.activePokemonView(A).damage()==150,"Departed Wyndon healed");
    }
    static void deckEvolution(){var c=wyndon(true,true);c.ownAbility=ResumableAbilityTest.ability("deck-evolve",DeckEvolutionEffectTest.EVOLVE);var m=GeneratedRuleBatch28Test.fixture(c);UUID host=active(m,A);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);m.placeDamageCountersByEffect(A,A,host,15);NonPokemonBatch54Test.top(m,A,hand(m,A,"evolved"));m.useAbility(A,host,"deck-evolve");var q=m.effectCardPrompt(A);check(!q.candidates().isEmpty(),"No deck evolution candidate");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId()));settle(m);check(m.activePokemonView(A).topCardId().equals("evolved")&&m.activePokemonView(A).damage()==150,"Deck evolution triggered hand-only Stadium");}
    static void schema(){String base="{\"trigger\":\"ON_EVOLVE\",\"watch\":\"ANY_FIELD\",\"optional\":false,\"usage\":\"NONE\",\"source\":\"HAND\",\"subjectOwnTurnOnly\":true,\"fromPokemonFamily\":\"V\"}";for(String bad:List.of(base.replace("\"subjectOwnTurnOnly\":true","\"subjectOwnTurnOnly\":\"yes\""),base.replace("HAND","DECK"),base.replace("\"fromPokemonFamily\":\"V\"","\"fromPokemonFamily\":\"GX\"")))GeneratedRuleBatch8Test.reject(()->PokemonChangeTriggerPolicy.parse(JsonParser.parseString(bad).getAsJsonObject(),EffectTrigger.ON_EVOLVE));}
}
