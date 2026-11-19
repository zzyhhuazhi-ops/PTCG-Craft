import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch125Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("sm10_en_186");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String key){return DynamicPassiveModifierTest.hand(m,p,key);}
    static BattlefieldState field(ServerMatch m){return EventTargetBindingTest.field(m);}
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();}
    static ServerMatch game(){return game(List.of(NonPokemonBatch105Test.draw()));}
    static ServerMatch game(List<AbilityDefinition> abilities){return DynamicPassiveModifierTest.fixture(profile(),NonPokemonBatch2Test.DATA.trainer("sm6_en_111").orElseThrow(),
        EnergyProfile.fixed(EnergyType.COLORLESS,2),GeneratedRuleBatch8Test.hit(40,false),abilities,0,NonPokemonBatch2Test.DATA.trainer("swsh5_en_136").orElseThrow(),Map.of("basic",EnergyType.GRASS,"evolved",EnergyType.GRASS));}
    static CardInstance attach(ServerMatch m,UUID p,UUID host){var card=hand(m,p,"tool");m.attachToolByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,card.instanceId(),p,host);return card;}
    static void attributed(ServerMatch m,UUID p,EffectOrigin.Kind kind,Runnable action){NonPokemonBatch124Test.attributed(m,p,kind,action);}
    static void hit(ServerMatch m,UUID controller,UUID p,UUID host,EffectOrigin.Kind kind){attributed(m,controller,kind,()->m.placeDamageCountersByEffect(m.currentPlayer(),p,host,2));}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.attack=GeneratedRuleBatch8Test.hit(300,false);c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){impactScope();existing();toolSuppression();abilityContinuation();abilitySuppression();globalSuppression();defenderGuard();ai();schema();
        System.out.println("NonPokemonBatch125Test passed: Stealthy Hood both owners/positions, opposing-Ability impacts and existing-effect removal, own/attack/Trainer/Rule exclusions, live Tool disable and Jammer/Lab, continuation, active/zone Ability suppression, defender guard exclusion and four AI difficulties");}
    static void impactScope(){for(UUID p:List.of(A,B))for(boolean onBench:List.of(false,true))for(var kind:EffectOrigin.Kind.values())for(boolean own:List.of(false,true)){
        var m=game();UUID host=onBench?bench(m,p):active(m,p),source=own?p:other(p);attach(m,p,host);
        if(kind==EffectOrigin.Kind.RULE)m.placeDamageCountersByEffect(m.currentPlayer(),p,host,2);else hit(m,source,p,host,kind);
        check(field(m).pokemonView(p,host).damage()==(kind==EffectOrigin.Kind.ABILITY&&!own?0:20),"Ability impact scope "+kind+own+onBench);
    }}
    static void existing(){for(UUID p:List.of(A,B))for(var kind:List.of(EffectOrigin.Kind.ABILITY,EffectOrigin.Kind.ATTACK,EffectOrigin.Kind.TRAINER))for(boolean own:List.of(false,true)){
        var m=game();UUID host=active(m,p),source=own?p:other(p);
        attributed(m,source,kind,()->{field(m).preventAttackUntilOwnerNextTurn(source,p,host);field(m).preventHandEnergy(source,p,host);field(m).disableAbilityEffectsUntilOwnerNextTurn(source,p,host);});
        m.placeDamageCountersByEffect(m.currentPlayer(),p,host,2);m.applySpecialConditionByEffect(m.currentPlayer(),p,SpecialCondition.POISONED,4);
        var hood=attach(m,p,host);boolean removed=kind==EffectOrigin.Kind.ABILITY&&!own;
        check(field(m).pokemonAttackLocked(p,host)!=removed&&field(m).energyFromHandAllowed(p,host)==removed&&field(m).abilityEffectsDisabled(p,host)!=removed,"Existing effect attribution "+kind+own);
        check(field(m).pokemonView(p,host).damage()==20&&field(m).pokemonView(p,host).conditions().contains(SpecialCondition.POISONED),"Hood undid prior results");
        m.discardToolByEffect(m.currentPlayer(),p,host,hood.instanceId());check(field(m).pokemonAttackLocked(p,host)!=removed,"Removed effect resurrected after Hood left");
    }}
    static void toolSuppression(){for(String mode:List.of("ability","own-ability","attack","trainer","lab","jammer")){
        var m=game();UUID host=active(m,A);
        if(mode.equals("lab"))m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        else if(mode.equals("jammer"))m.attachToolByEffect(A,B,EffectSourceZone.HAND,hand(m,B,"item").instanceId(),B,active(m,B));
        else {UUID source=mode.equals("own-ability")?A:B;var kind=mode.contains("ability")?EffectOrigin.Kind.ABILITY:mode.equals("attack")?EffectOrigin.Kind.ATTACK:EffectOrigin.Kind.TRAINER;
            attributed(m,source,kind,()->field(m).disableToolEffectsUntilOwnerNextTurn(source,A,host));}
        attach(m,A,host);hit(m,B,A,host,EffectOrigin.Kind.ABILITY);check(m.activePokemonView(A).damage()==(mode.equals("ability")?0:20),"Tool suppression priority "+mode);
        if(mode.equals("lab")){m.discardStadiumByEffect(A);hit(m,B,A,host,EffectOrigin.Kind.ABILITY);check(m.activePokemonView(A).damage()==20,"Hood did not resume after Lab left");}
        if(mode.equals("jammer")){m.switchActiveByEffect(A,A,bench(m,A));hit(m,B,A,host,EffectOrigin.Kind.ABILITY);check(field(m).pokemonView(A,host).damage()==20,"Benched Hood stayed disabled by Jammer");}
    }}
    static void abilityContinuation(){var ability=ResumableAbilityTest.ability("counter-draw","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2},{\"type\":\"draw\",\"amount\":1}");
        var m=game(List.of(ability));attach(m,B,active(m,B));int cards=m.privateView(A).hand().size();m.useAbility(A,active(m,A),ability.id());NonPokemonBatch103Test.settle(m);
        check(m.activePokemonView(B).damage()==0&&m.privateView(A).hand().size()==cards+1,"Hood swallowed independent Ability suffix");
    }
    static boolean has(ServerMatch m,UUID p,UUID pokemon){return NonPokemonBatch105Test.has(m,p,pokemon);}
    static PokemonProfile pokemon(String from,List<AbilityDefinition> abilities){return new PokemonProfile(1000,1,from,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(100,false)),abilities,EnergyType.GRASS);}
    static void abilitySuppression(){var gas=DiscardAbilityCountTest.passive("gas","{\"type\":\"suppress_abilities\",\"target\":\"SELF\",\"scope\":\"OPPONENT_ACTIVE\",\"source_active_only\":true,\"except_ability_id\":\"gas\"}");
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.ownAbility=NonPokemonBatch105Test.draw();c.restoredProfiles=Map.of("defender",pokemon(null,List.of(gas)));
        var m=GeneratedRuleBatch28Test.fixture(c);UUID a=GeneratedRuleBatch8Test.A,b=GeneratedRuleBatch8Test.B,host=active(m,a);
        check(!has(m,a,host),"Active suppression fixture");var hood=hand(m,a,"trainer");m.playTrainer(a,hood.instanceId(),host);check(has(m,a,host)&&m.canUseAbility(a,host,"draw"),"Hood failed active Ability suppression");
        m.discardToolByEffect(a,a,host,hood.instanceId());check(!has(m,a,host),"Passive suppression did not resume after Hood left");
    }
    static void globalSuppression(){for(boolean opposing:List.of(false,true)){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.ownAbility=NonPokemonBatch105Test.draw();
        var garbo=DiscardAbilityCountTest.passive("garbotoxin",DiscardAbilityCountTest.GLOBAL);
        c.evolvedAbility=garbo;c.restoredProfiles=Map.of("defender",pokemon(null,List.of(NonPokemonBatch105Test.draw())),"defender_evolved",pokemon("defender",List.of(garbo)));
        var m=GeneratedRuleBatch28Test.fixture(c);UUID a=GeneratedRuleBatch8Test.A,b=GeneratedRuleBatch8Test.B,sourceOwner=opposing?b:a,host=active(m,a);
        m.playTrainer(a,hand(m,a,"trainer").instanceId(),host);if(opposing)m.endTurn(a);UUID source=bench(m,sourceOwner);
        m.evolveByEffect(sourceOwner,source,hand(m,sourceOwner,opposing?"defender_evolved":"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        m.playTrainer(sourceOwner,hand(m,sourceOwner,"trainer").instanceId(),source);
        check(has(m,a,host)==opposing,"Hood cross-zone own/opponent suppression scope");
        check(!has(m,a,bench(m,a))||sourceOwner.equals(a)&&bench(m,a).equals(source),"Unprotected Pokemon escaped global suppression");
        if(opposing){m.disableToolEffectsByEffect(b,a,host);check(!has(m,a,host),"Disabled Hood retained global immunity");}
    }}
    static void defenderGuard(){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.ownFamilies=Set.of(PokemonFamily.GX);c.attack=GeneratedRuleBatch8Test.hit(100,false);
        var guard=NonPokemonBatch2Test.DATA.pokemon("sm3-5_en_055").orElseThrow().ability("scoundrel-guard").orElseThrow();c.restoredProfiles=Map.of("defender",pokemon(null,List.of(guard)));
        var m=GeneratedRuleBatch28Test.fixture(c);UUID a=GeneratedRuleBatch8Test.A;m.playTrainer(a,hand(m,a,"trainer").instanceId(),active(m,a));m.attack(a,c.attack.id());NonPokemonBatch103Test.settle(m);
        check(m.lastAttack().finalDamage()==0,"Attacker's Hood bypassed defender's own Ability guard");
    }
    static void ai(){for(var level:AiDifficulty.values()){var m=aiGame(IDS.getFirst());UUID p=m.currentPlayer();m.playTrainer(p,hand(m,p,"trainer").instanceId(),active(m,p));var seats=NonPokemonBatch103Test.seats(m);var session=MatchSession.multiplayer(m,seats.get(0),seats.get(1));AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"Hood AI stalled");}}
    static void schema(){var e=profile().ongoingProgram().triggers().getFirst().effects().getFirst();check(PassiveRuleProgram.selfAbilityEffectsGuard(e),"Hood profile missing strict guard");
        for(String value:List.of("OWN","ANY")){var args=new HashMap<>(e.arguments());args.put("from",value);var bad=new EffectDefinition(e.type(),e.target(),args,List.of(),List.of());GeneratedRuleBatch8Test.reject(()->new TrainerProfile(TrainerCategory.TOOL,new TrainerEffect(TrainerEffect.Opcode.PASSIVE_TOOL,0,0),new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.PASSIVE,List.of(),List.of(bad))))));}
    }
}
