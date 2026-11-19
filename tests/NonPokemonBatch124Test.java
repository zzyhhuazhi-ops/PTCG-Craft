import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real Supporter profiles against attributed temporary state and actual attack continuations. */
public final class NonPokemonBatch124Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm11_en_190","sc_csm2_5c_080_sr","xy11_en_104");
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static BattlefieldState field(ServerMatch m){return EventTargetBindingTest.field(m);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static void settle(ServerMatch m){NonPokemonBatch103Test.settle(m);}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);settle(m);}
    static ServerMatch game(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);return GeneratedRuleBatch28Test.fixture(c);}
    static void attributed(ServerMatch m,UUID actor,EffectOrigin.Kind kind,Runnable action){try{
        var method=ServerMatch.class.getDeclaredMethod("withOrigin",EffectOrigin.class,Runnable.class);method.setAccessible(true);
        method.invoke(m,new EffectOrigin(kind,actor,active(m,actor)),action);
    }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    static void seed(ServerMatch m,UUID owner,UUID host,EffectOrigin.Kind kind){attributed(m,owner,kind,()->{
        field(m).preventAttackUntilOwnerNextTurn(other(owner),owner,host);
        field(m).preventRetreatUntilOwnerNextTurn(other(owner),owner,host);
        field(m).preventHandEnergy(other(owner),owner,host);
        field(m).preventHandEvolutionUntilOwnerNextTurn(other(owner),owner,host);
        field(m).penalizeAttackDamageUntilOwnerNextTurn(other(owner),owner,host,30);
    });}
    static AttackDefinition attack(String after){return GeneratedRuleBatch8Test.hit(100,false).withEffectProgram(
        new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.AFTER_ATTACK,after))));}
    static ServerMatch attacking(String id,String after,UUID actor){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.attack=attack(after);c.defenderAttack=c.attack;
        var m=GeneratedRuleBatch28Test.fixture(c);if(actor.equals(A))m.endTurn(A);m.attack(other(actor),c.attack.id());settle(m);check(m.currentPlayer().equals(actor),"Attack failed to finish");return m;}
    static ServerMatch aiGame(String id){return attacking(id,"{\"type\":\"prevent_attack\",\"target\":\"OPPONENT_ACTIVE\"}",A);}
    public static void main(String[] args){scope();origin();actualAttacks();nativeTail();delayed();statusesAndHistory();immunity();authority();ai();schema();
        System.out.println("NonPokemonBatch124Test passed: Channeler/Ranger own/both players and Active/Bench, source attribution, native/composed attack effects, beneficial and harmful state, delayed markers and traps, player locks, Trainer/Ability preservation, damage/status/history, immunity, no-op and authority, four AI difficulties");}
    static void scope(){for(String id:IDS)for(UUID actor:List.of(A,B)){
        var m=game(id);if(actor.equals(B))m.endTurn(A);for(UUID p:List.of(A,B))for(UUID host:List.of(active(m,p),bench(m,p)))seed(m,p,host,EffectOrigin.Kind.ATTACK);
        var card=hand(m,actor,"trainer");play(m,actor);check(m.privateView(actor).discard().contains(card),"Supporter not consumed");
        for(UUID p:List.of(A,B))for(UUID host:List.of(active(m,p),bench(m,p))){boolean removed=p.equals(actor)||id.equals("xy11_en_104");
            check(field(m).hasAttackEffects(p,host)!=removed,"Wrong removal owner/position "+id);
            check(field(m).energyFromHandAllowed(p,host)==removed,"Hand Energy lock scope");}
        check(!m.mayPlaySupporter(actor),"Supporter quota was cleared");
    }}
    static void origin(){for(EffectOrigin.Kind kind:List.of(EffectOrigin.Kind.ABILITY,EffectOrigin.Kind.TRAINER,EffectOrigin.Kind.RULE)){
        var m=game("xy11_en_104");UUID host=active(m,A);seed(m,A,host,kind);seed(m,A,host,EffectOrigin.Kind.ATTACK);play(m,A);
        check(!field(m).hasAttackEffects(A,host)&&field(m).pokemonAttackLocked(A,host)&&!field(m).energyFromHandAllowed(A,host),"Removal erased non-attack effects "+kind);
    }}
    static void actualAttacks(){String effects="{\"type\":\"prevent_attack\",\"target\":\"OPPONENT_ACTIVE\"},"
        +"{\"type\":\"reduce_damage\",\"target\":\"SELF\",\"amount\":30},"
        +"{\"type\":\"prevent_trainer_play\",\"target\":\"OPPONENT_ACTIVE\",\"trainer_category\":\"ITEM\"},"
        +"{\"type\":\"turn_attack_lock\",\"target\":\"SELF\",\"energy_max\":60,\"duration\":\"OPPONENT_NEXT_TURN\"},"
        +"{\"type\":\"force_opponent_coin_tails\",\"target\":\"OPPONENT_ACTIVE\",\"duration\":\"OPPONENT_NEXT_TURN\"}";
        for(String id:IDS)for(UUID actor:List.of(A,B)){var m=attacking(id,effects,actor);UUID opponent=other(actor);
            check(m.activeAttacks(actor).isEmpty()&&field(m).attackDamageReduction(opponent,active(m,opponent),actor)==30,"Attack precondition");
            play(m,actor);check(!m.activeAttacks(actor).isEmpty(),"Player/Pokémon attack restriction survived removal");
            check(field(m).attackDamageReduction(opponent,active(m,opponent),actor)==(id.equals("xy11_en_104")?0:30),"Beneficial opponent effect scope");
            check(!playerFlag(m,actor,"coinTailsThisTurn"),"Player coin lock survived");
        }
    }
    static boolean playerFlag(ServerMatch m,UUID p,String name){try{var method=ServerMatch.class.getDeclaredMethod("state",UUID.class);method.setAccessible(true);var player=method.invoke(m,p);var f=player.getClass().getDeclaredField(name);f.setAccessible(true);return f.getBoolean(player);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void nativeTail(){for(String id:IDS){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);
        c.attack=DamageTriggeredAbilityTest.attack(100,",\"damageReductionFromOpponentNextTurn\":30,\"cannotUseSameAttackDuringNextTurn\":true");c.defenderAttack=c.attack;
        var m=GeneratedRuleBatch28Test.fixture(c);m.endTurn(A);m.attack(B,c.attack.id());settle(m);
        check(field(m).hasAttackEffects(B,active(m,B)),"Native tail missing provenance");
        seed(m,A,active(m,A),EffectOrigin.Kind.ATTACK);play(m,A);
        check(field(m).hasAttackEffects(B,active(m,B))!=id.equals("xy11_en_104"),"Native tail not removed with correct scope");
    }}
    static void delayed(){for(String id:IDS){
        var m=attacking(id,DelayedPokemonEffectsTest.delayed(DelayedPokemonEffectsTest.COUNTERS),A);
        int damage=m.activePokemonView(A).damage();check(m.activePokemonView(A).continuousEffectZh().contains("延迟"),"Delayed marker missing");
        attributed(m,A,EffectOrigin.Kind.ATTACK,()->field(m).overwriteWeaknessUntilLeavesActive(A,A,active(m,A),EnergyType.LIGHTNING));
        check(field(m).effectiveWeaknessType(A,active(m,A))==EnergyType.LIGHTNING,"Weakness fixture");play(m,A);
        check(field(m).effectiveWeaknessType(A,active(m,A))!=EnergyType.LIGHTNING,"Attack weakness override survived");
        check(!m.activePokemonView(A).continuousEffectZh().contains("延迟"),"Delayed marker survived");
        m.endTurn(A);settle(m);check(m.activePokemonView(A).damage()==damage,"Removed delayed attack still fired");
    }}
    static void statusesAndHistory(){var m=game("xy11_en_104");UUID host=active(m,A);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,4);m.placeDamageCountersByEffect(A,A,host,3);
        var energy=hand(m,A,"energy");m.attachEnergy(A,energy.instanceId(),host);int damage=m.activePokemonView(A).damage();seed(m,A,host,EffectOrigin.Kind.ATTACK);play(m,A);
        check(m.activePokemonView(A).damage()==damage&&m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Damage/status undone");
        GeneratedRuleBatch8Test.reject(()->m.attachEnergy(A,hand(m,A,"energy").instanceId(),host));m.endTurn(A);settle(m);
        check(m.activePokemonView(A).damage()==damage+40,"Enhanced poison counter count erased");
    }
    static void immunity(){var m=game("xy11_en_104");UUID own=active(m,A),opp=active(m,B);seed(m,A,own,EffectOrigin.Kind.ATTACK);seed(m,B,opp,EffectOrigin.Kind.ATTACK);
        attributed(m,B,EffectOrigin.Kind.ABILITY,()->field(m).addProtection(B,B,opp,new ProtectionEffect(new EffectProtection(EffectProtection.Source.TRAINER,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY)));
        play(m,A);check(!field(m).hasAttackEffects(A,own)&&field(m).hasAttackEffects(B,opp),"Trainer immunity bypassed");
    }
    static void authority(){for(String id:IDS){var m=game(id);var card=hand(m,A,"trainer");var before=m.clientView(A);
        check(!m.canPlayTrainer(A,card.instanceId(),null),"No-change Supporter was playable");GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,card.instanceId(),null));check(before.equals(m.clientView(A)),"Rejected no-op mutated match");
        seed(m,A,active(m,A),EffectOrigin.Kind.ATTACK);before=m.clientView(A);GeneratedRuleBatch8Test.reject(()->m.playTrainer(B,card.instanceId(),null));check(before.equals(m.clientView(A)),"Wrong actor mutated match");
    }}
    static void ai(){for(String id:IDS)for(var level:AiDifficulty.values()){var m=aiGame(id);var session=MatchSession.multiplayer(m,A,B);play(m,A);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"AI stalled "+id+level);}}
    static void schema(){for(String json:List.of("{\"type\":\"remove_attack_effects\",\"target\":\"SELF\"}","{\"type\":\"remove_attack_effects\",\"target\":\"SELF\",\"scope\":\"OPPONENT\"}","{\"type\":\"remove_attack_effects\",\"target\":\"OPPONENT_ACTIVE\",\"scope\":\"BOTH\"}"))GeneratedRuleBatch8Test.reject(()->GeneratedRuleBatch28Test.effect(json));}
}
