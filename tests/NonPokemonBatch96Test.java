import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch96Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("sm10_en_168");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.MASTER);}
    static ServerMatch game(String item,List<AbilityDefinition> abilities,AttackDefinition attack,EnergyProfile energy){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),profile(IDS.getFirst()),energy,attack,abilities,0,profile(item),Map.of("basic",EnergyType.COLORLESS,"evolved",EnergyType.COLORLESS),"Solrock");}
    static ServerMatch game(String item){return game(item,List.of(),GeneratedRuleBatch8Test.hit(20,false),EnergyProfile.fixed(EnergyType.COLORLESS,2));}
    static void stadium(ServerMatch m,UUID controller,UUID actor){if(!m.currentPlayer().equals(controller))m.endTurn(m.currentPlayer());m.playTrainer(controller,hand(m,controller,"stadium").instanceId(),null);if(!m.currentPlayer().equals(actor))m.endTurn(m.currentPlayer());}
    static void play(ServerMatch m,UUID p,String id){var t=profile(id);m.playTrainer(p,hand(m,p,"item").instanceId(),t.composed()?null:bench(m,p));settle(m);}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.attack=GeneratedRuleBatch8Test.hit(200,false);c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){ownTrainers();opponentTrainers();exclusions();immunity();blockedSwitch();repeatAndClear();ai();schema();System.out.println("NonPokemonBatch96Test passed: own Trainer-only poison inheritance, both players/controllers, enhanced counter strength and checkup, Guzma/Escape Rope only user's side, opponent Repel/retreat/Ability/attack/return exclusions, Stadium immunity and incoming condition immunity, failed switches and all AI difficulties");}
    static void ownTrainers(){for(UUID p:List.of(A,B))for(UUID controller:List.of(A,B))for(int counters:List.of(1,3,8))for(String id:List.of("swsh1_en_183","swsh10_en_154","sm3_en_115","sm3_en_114")){
        var m=game(id);stadium(m,controller,p);UUID old=active(m,p),next=bench(m,p);m.applySpecialConditionByEffect(p,p,SpecialCondition.POISONED,counters);m.applySpecialConditionByEffect(p,p,SpecialCondition.BURNED);m.applySpecialConditionByEffect(p,p,SpecialCondition.CONFUSED);play(m,p,id);
        check(active(m,p).equals(next)&&m.activePokemonView(p).conditions().equals(List.of(SpecialCondition.POISONED))&&EventTargetBindingTest.field(m).checkupCounters(p,SpecialCondition.POISONED)==counters,"Trainer did not inherit exactly poison strength: "+id);check(m.benchPokemonView(p).stream().filter(v->v.pokemonId().equals(old)).findFirst().orElseThrow().conditions().isEmpty(),"Old Active retained conditions");m.endTurn(p);check(m.activePokemonView(p).damage()==10*counters,"Inherited poison used wrong Checkup amount");
    }}
    static void opponentTrainers(){for(UUID p:List.of(A,B))for(String id:List.of("sm1_en_130","sm3_en_115","sm3_en_114")){
        var m=game(id);stadium(m,A,p);m.applySpecialConditionByEffect(p,p,SpecialCondition.POISONED,5);m.applySpecialConditionByEffect(p,other(p),SpecialCondition.POISONED,5);UUID old=active(m,other(p));play(m,p,id);
        check(!active(m,other(p)).equals(old)&&m.activePokemonView(other(p)).conditions().isEmpty(),"Opponent Trainer switch spread poison: "+id);if(!id.equals("sm1_en_130"))check(m.activePokemonView(p).conditions().contains(SpecialCondition.POISONED),"Own Guzma/Rope switch missed poison");
    }}
    static void exclusions(){for(String mode:List.of("retreat","ability","attack","removed","return","rule")){
        var move=ResumableAbilityTest.ability("move","{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}");var attack=BeforeDamageAttackTest.attack("","","{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}");var m=game("swsh1_en_183",List.of(move),attack,EnergyProfile.fixed(EnergyType.COLORLESS,2));stadium(m,A,A);UUID old=active(m,A);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,4);
        switch(mode){
            case "retreat" -> {var one=hand(m,A,"energy");var two=hand(m,A,"special");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,one.instanceId(),A,old);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,two.instanceId(),A,old);m.retreat(A,bench(m,A),List.of(one.instanceId(),two.instanceId()));}
            case "ability" -> m.useAbility(A,old,"move");
            case "attack" -> m.attack(A,attack.id());
            case "removed" -> {m.discardStadiumByEffect(A);play(m,A,"swsh1_en_183");}
            case "return" -> m.movePokemonByEffect(A,A,old,LeavePlayDestination.HAND);
            default -> m.switchActiveByEffect(A,A,bench(m,A));
        }settle(m);check(!active(m,A).equals(old)&&m.activePokemonView(A).conditions().isEmpty(),"Non-Trainer-owner switch inherited poison: "+mode);
    }}
    static void immunity(){for(UUID controller:List.of(A,B)){
        var moon=NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities();var m=game("swsh1_en_183",moon,GeneratedRuleBatch8Test.hit(20,false),EnergyProfile.fixed(EnergyType.COLORLESS,2));stadium(m,controller,A);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,6);play(m,A,"swsh1_en_183");check(m.activePokemonView(A).conditions().isEmpty(),"New Moon failed Stadium-origin poison block");
        var immune=DynamicPassiveModifierTest.energy("{\"type\":\"special_condition_immunity\",\"target\":\"SELF\",\"statuses\":\"ALL\"}");var n=game("swsh1_en_183",List.of(),GeneratedRuleBatch8Test.hit(20,false),immune);stadium(n,controller,A);n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(n,A,"special").instanceId(),A,bench(n,A));n.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,6);play(n,A,"swsh1_en_183");check(n.activePokemonView(A).conditions().isEmpty(),"Incoming condition immunity failed after switch");
    }}
    static void blockedSwitch(){var m=game("swsh1_en_183");stadium(m,A,A);UUID old=active(m,A);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,7);EventTargetBindingTest.field(m).addProtection(A,A,old,DiscardPokemonReplacementTest.protection(false,"TRAINER","OWN"));var card=hand(m,A,"item");var before=m.clientView(A);if(m.canPlayTrainer(A,card.instanceId(),bench(m,A)))play(m,A,"swsh1_en_183");else GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,card.instanceId(),bench(m,A)));check(active(m,A).equals(old)&&m.benchPokemonView(A).getFirst().conditions().isEmpty()&&EventTargetBindingTest.field(m).checkupCounters(A,SpecialCondition.POISONED)==7,"Failed switch transferred poison");}
    static void repeatAndClear(){var m=game("swsh1_en_183");stadium(m,A,A);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,3);play(m,A,"swsh1_en_183");play(m,A,"swsh1_en_183");check(EventTargetBindingTest.field(m).checkupCounters(A,SpecialCondition.POISONED)==3,"Repeated inheritance multiplied poison");m.clearSpecialConditionsByEffect(A,A,active(m,A),Set.of(SpecialCondition.POISONED));m.recoverDiscardCardByEffect(A,A,m.privateView(A).discard().stream().filter(c->c.definitionId().equals("item")).findFirst().orElseThrow().instanceId(),CardEffectDestination.HAND);play(m,A,"swsh1_en_183");check(m.activePokemonView(A).conditions().isEmpty(),"Cured poison remained attached to Stadium");}
    static void ai(){for(var d:AiDifficulty.values()){var m=game("swsh10_en_154");stadium(m,A,A);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,2);m.playTrainer(A,hand(m,A,"item").instanceId(),null);AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),d);check(m.phase()==MatchPhase.TURN&&!m.hasPendingEffectChoice()&&m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"AI stalled poison-inheriting switch "+d);}}
    static void schema(){var e=profile(IDS.getFirst()).ongoingProgram().triggers().getFirst().effects().getFirst();GeneratedRuleBatch8Test.reject(()->PassiveModifierProgram.validate(profile(IDS.getFirst()).ongoingProgram(),PassiveModifierProgram.Source.TOOL));GeneratedRuleBatch8Test.reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),EffectTarget.OPPONENT_ACTIVE,Map.of(),List.of(),List.of())));GeneratedRuleBatch8Test.reject(()->TrainerProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e))))));}
}
