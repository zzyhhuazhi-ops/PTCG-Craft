import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Decisions must change with visible Energy/counters/conditions, without consulting hidden order. */
public final class AiPublicDamageStrategyTest {
    static final UUID A=BattleExperienceTest.A,B=BattleExperienceTest.B;
    static AttackDefinition attack(String id,int base,String extra){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\""+id+"\",\"energyCost\":{},\"baseDamage\":"+base+",\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\""+extra+"}").getAsJsonObject(),EffectRegistry.standard());}
    static AiView view(AttackDefinition variable,int units,boolean prepare){
        var m=BattleExperienceTest.fixture(null,null,attack("original",40,""));var v=AiPracticeScenarios.view(MatchSession.multiplayer(m,A,B),A);var mon=m.activePokemonView(A);var facts=new HashMap<>(v.cards());var stable=attack("stable",90,"");
        facts.put(mon.topCardId(),new AiView.Card(facts.get(mon.topCardId()).definition(),new PokemonProfile(500,0,null,null,0,null,0,1,List.of(variable,stable)),null,null));
        var options=new ArrayList<MatchSession.Option>();options.add(new MatchSession.Option(0,"攻击："+variable.nameZh(),List.of("field:"+mon.pokemonId()),true,ActionType.ATTACK,variable.id()));options.add(new MatchSession.Option(1,"攻击：stable",List.of("field:"+mon.pokemonId()),true,ActionType.ATTACK,"stable"));options.add(new MatchSession.Option(2,"结束回合",List.of(),true,ActionType.END_TURN,""));
        var hand=new ArrayList<CardInstance>();if(prepare){var card=new CardInstance(new UUID(987,1),A,"grass");hand.add(card);facts.put("grass",new AiView.Card(AiPracticeScenarios.def("grass",CardCategory.ENERGY,true),null,null,EnergyProfile.basic(EnergyType.GRASS)));options.add(new MatchSession.Option(3,"附着基本能量",List.of("hand:"+card.instanceId(),"field:"+mon.pokemonId()),true,ActionType.ATTACH,""));}
        var changed=AiPracticeScenarios.changed(v,hand,options,facts,null,null);var energy=new HashMap<>(changed.energy());var supply=new ArrayList<EnergyProfile.Unit>();for(int i=0;i<units;i++)supply.add(new EnergyProfile.Unit(Set.of(EnergyType.GRASS)));energy.put(mon.pokemonId(),new AiView.Energy(supply,Map.of(),Map.of()));return new AiView(changed.snapshot(),changed.cards(),energy,changed.deck(),new AiView.OwnTurn(true,false,0));
    }
    static void check(boolean b,String why){AiPracticeTest.check(b,why);}
    public static void main(String[] args){
        var nativeMultiplier=attack("native",0,",\"damageModifiers\":[{\"basis\":\"ATTACKER_ATTACHED_ENERGY_COUNT\",\"amountPerUnit\":80}]");
        var genericMultiplier=attack("generic",0,",\"effects\":[{\"type\":\"damage_per_energy\",\"target\":\"SELF\",\"amount\":80}]");
        var conditional=attack("conditional",20,",\"effects\":[{\"type\":\"conditional\",\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"target\":\"SELF\",\"value\":2}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":180}]}]");
        for(var level:List.of(AiDifficulty.NORMAL,AiDifficulty.HARD,AiDifficulty.MASTER))for(var a:List.of(nativeMultiplier,genericMultiplier,conditional))for(int units:List.of(0,2)){
            var v=view(a,units,false);var before=v;var first=AiPolicy.choose(v,37,Set.of(),level).orElseThrow();check(first.option()==(units==0?1:0),"AI ignored public multiplier/condition: "+a.id()+" "+units+" "+level);check(first.equals(AiPolicy.choose(v,37,Set.of(),level).orElseThrow())&&before.equals(v),"Estimate mutated snapshot or fixed-seed choice");
        }
        var unknown=attack("unknown",0,",\"effects\":[{\"type\":\"conditional\",\"conditions\":[{\"type\":\"DISCARD_COUNT\",\"target\":\"SELF\",\"card_category\":\"TRAINER\",\"value\":1}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":500}]}]");
        check(AiPolicy.choose(view(unknown,2,false),37,Set.of(),AiDifficulty.HARD).orElseThrow().option()==1,"Unknown filtered predicate became guaranteed damage");
        for(var level:List.of(AiDifficulty.HARD,AiDifficulty.MASTER)){check(AiPolicy.choose(view(conditional,1,true),37,Set.of(),level).orElseThrow().option()==3,"AI missed attach-then-conditional-attack preparation");check(AiPolicy.choose(view(conditional,2,false),37,Set.of(),level).orElseThrow().option()==0,"AI failed to re-evaluate the conditional attack after preparation");}
        for(var level:AiDifficulty.values()) {
            var m=NonPokemonCnPrintingTest.game("sc_csm2cc_179_sr");var actor=m.currentPlayer();
            m.playTrainer(actor,DynamicPassiveModifierTest.hand(m,actor,"trainer").instanceId(),null);
            var session=MatchSession.multiplayer(m,GeneratedRuleBatch8Test.A,GeneratedRuleBatch8Test.B);
            AiCardOperationTest.settle(session,level);
            check(!m.hasPendingEffectChoice(),"AI repeated an optional opponent-hand Supporter copy cycle: "+level);
            AiCardOperationTest.finish(session,level);
        }
        System.out.println("AiPublicDamageStrategyTest passed: visible unit multipliers, generic damage, true/false/unknown conditions, deterministic decisions, preparation then attack, four-tier Supporter copy-cycle exit and completed games; no hidden state or game RNG access");
    }
}
