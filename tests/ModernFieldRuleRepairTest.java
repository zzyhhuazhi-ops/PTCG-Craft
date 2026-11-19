import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernFieldRuleRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    static ServerMatch game(AttackDefinition a,List<AbilityDefinition> abilities,EnergyType type){return ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),abilities,a,type,0);}
    static void pay(ServerMatch m,UUID p,UUID host,int n){ModernRuleRepair2Test.pay(m,p,host,"energy",n);}
    static void select(ServerMatch m,UUID p,UUID host){var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),host);}
    static void settle(ServerMatch m,boolean accept){for(int i=0;m.triggerOrderPrompt()!=null&&i<16;i++){var q=m.triggerOrderPrompt();if(q.optionalDecision())m.decideTriggeredEffect(q.choosingPlayer(),q.id(),accept);else m.chooseTriggeredEffect(q.choosingPlayer(),q.id(),q.entries().getFirst().id());}check(m.triggerOrderPrompt()==null,"Unfinished trigger ordering");}
    public static void main(String[] args){costs();transfer();pep();salt();evolutionHeal();giacomo();drum();simisage();retrieval();System.out.println("ModernFieldRuleRepairTest passed: alternate attack costs, scoped counter transfer, both-Bench draws, bound Energy/heal, optional hand-evolution healing, per-host Special Energy discard, Ancient count draw, Ability/name protection and physical-cost exclusion");}
    static void retrieval(){
        for(String id:List.of("sv2_en_189","sv2_en_277")){
            var m=ModernCountRepairTest.game(id,List.of());
            var energies=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).limit(3).toList();check(energies.size()==3,"Retrieval fixture");
            var old=energies.getFirst();m.discardHandCardsByEffect(A,A,List.of(old.instanceId()));
            var special=hand(m,A,"special");m.discardHandCardsByEffect(A,A,List.of(special.instanceId()));
            m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var cost=m.effectCardPrompt(A);
            CopyAttackTest.reject(()->m.submitEffectCards(A,cost.resolutionId(),cost.step(),List.of(energies.get(1).instanceId())));
            var paid=List.of(energies.get(1).instanceId(),energies.get(2).instanceId());m.submitEffectCards(A,cost.resolutionId(),cost.step(),paid);
            var q=m.effectCardPrompt(A);check(q.candidates().equals(List.of(old)),"Retrieval must exclude actual paid cards, not all identical printings; Special Energy is ineligible");
            check(q.minimum()==1&&q.maximum()==4,"Public recovery must select one to four, excluding paid cards");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(paid.getFirst())));
            m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(old.instanceId()));check(m.privateView(A).hand().contains(old)&&!m.hasPendingEffectChoice(),"Retrieval stalled or failed");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(old.instanceId())));
            // A later copy gets a fresh cost memory: the first copy's discarded Energy is now recoverable.
            m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var cost2=m.effectCardPrompt(A);
            var laterCost=cost2.candidates().stream().filter(c->!c.definitionId().equals("energy")).limit(2).map(CardInstance::instanceId).toList();
            m.submitEffectCards(A,cost2.resolutionId(),cost2.step(),laterCost);var q2=m.effectCardPrompt(A);
            check(q2.candidates().stream().map(CardInstance::instanceId).toList().containsAll(paid),"Cost memory leaked into later trainer");
            m.submitEffectCards(A,q2.resolutionId(),q2.step(),paid);check(!m.hasPendingEffectChoice(),"Second recovery stalled");
        }
    }
    static void simisage(){
        var a=DATA.pokemon("sv4_en_005").orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        var policy=ProtectionEffect.from(a.effectProgram().triggers().getFirst().effects().getFirst()).policy();
        var filter=policy.attackerFilter();var profile=DATA.pokemon("sv4_en_005").orElseThrow();
        var card=CopyAttackTest.card("proof",true);
        check(!filter.matches(new AttackSourceFilter.Facts(card,"Simisage",false,profile,3,Set.of(),true)),"Simisage name exception ignored");
        check(filter.matches(new AttackSourceFilter.Facts(card,"Other",false,profile,3,Set.of(),true))&&!filter.matches(new AttackSourceFilter.Facts(card,"Other",false,profile,3,Set.of(),false)),"Simisage Ability possession filter lost");
        for(boolean ability:List.of(false,true)){
            var m=game(a,ability?List.of(PossessedAbilityAttackSourceFilterTest.ability()):List.of(),EnergyType.GRASS);
            pay(m,A,m.activePokemonView(A).pokemonId(),3);pay(m,B,m.activePokemonView(B).pokemonId(),1);
            m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,hand(m,B,"special").instanceId(),B,m.activePokemonView(B).pokemonId());
            m.attack(A,a.id());m.attack(B,a.id());check(m.lastAttack().finalDamage()==(ability?0:100),"Actual Simisage protection failed");
        }
    }
    static void costs(){
        for(String id:List.of("sv2_en_056","sv4-5_en_125","sv6_en_105","sv8_en_113"))for(boolean enabled:List.of(false,true)){
            var a=DATA.pokemon(id).orElseThrow().attacks().get(1);AttackProgramRules.validate(a);var m=game(a,List.of(),id.contains("056")||id.contains("125")||id.contains("049")?EnergyType.WATER:EnergyType.FIGHTING);UUID host=m.activePokemonView(A).pokemonId();boolean free=a.id().equals("gutsy-swing");
            if(!free)pay(m,A,host,1);
            if(enabled){switch(a.id()){
                case "slim-screw"->m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().map(CardInstance::instanceId).toList());
                case "raging-tentacles"->m.placeDamageCountersByEffect(A,A,host,1);
                case "gutsy-swing"->m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
                default->throw new AssertionError("unexpected cost card");
            }}
            if(enabled){m.attack(A,a.id());check(m.lastAttack().finalDamage()==a.baseDamage()&&m.currentPlayer().equals(B),"Conditional cost failed");}
            else CopyAttackTest.reject(()->m.attack(A,a.id()));
        }
    }
    static void transfer(){
        for(String id:List.of("sv2_en_093","sv2_en_239","sv4_en_115"))for(int counters:List.of(0,1,7)){
            var a=DATA.pokemon(id).orElseThrow().attacks().getFirst();var m=ModernShuffleScopeRepairTest.game(a,0);m.endTurn(A);m.endTurn(B);ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());UUID host=m.benchPokemonView(A).getFirst().pokemonId();if(counters>0)m.placeDamageCountersByEffect(A,A,host,counters);m.attack(A,a.id());
            if(counters>0){var p=m.effectPokemonPrompt(A);check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(host),"Transfer offered Active or opponent source");select(m,A,host);}
            check(m.benchPokemonView(A).getFirst().damage()==0&&m.activePokemonView(B).damage()==counters*10&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Transfer changed count or stalled");
        }
    }
    static void pep(){
        var a=DATA.pokemon("sv4_en_067").orElseThrow().attacks().getFirst();
        for(int own:List.of(0,1))for(int foe:List.of(0,1)){
            var m=game(a,List.of(),EnergyType.LIGHTNING);if(own==0)m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);if(foe==0)m.movePokemonByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId(),LeavePlayDestination.HAND);pay(m,A,m.activePokemonView(A).pokemonId(),1);int total=m.privateView(A).deck().size()+m.privateView(A).hand().size();m.attack(A,a.id());
            check(m.privateView(A).hand().size()==own+foe&&m.privateView(A).deck().size()==total-own-foe&&!m.hasPendingEffectChoice(),"Pep Up must count both benches including zero");
        }
    }
    static void salt(){
        for(String id:List.of("sv4_en_104","sv4_en_202"))for(int attach:List.of(0,1)){
            var a=DATA.pokemon(id).orElseThrow().abilities().getFirst();var m=game(GeneratedRuleBatch8Test.hit(10,false),List.of(a),EnergyType.FIGHTING);UUID host=m.benchPokemonView(A).getFirst().pokemonId(),source=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,host,5);m.placeDamageCountersByEffect(A,A,source,5);
            var energy=hand(m,A,"energy");var wrong=hand(m,A,"wrong");m.discardHandCardsByEffect(A,A,List.of(energy.instanceId(),wrong.instanceId()));m.useAbility(A,source,a.id());select(m,A,host);var p=m.effectCardPrompt(A);check(p.candidates().equals(List.of(energy)),"Rock Salt accepted non-Fighting or Special Energy");m.submitEffectCards(A,p.resolutionId(),p.step(),attach==0?List.of():List.of(energy.instanceId()));
            check(m.benchPokemonView(A).getFirst().damage()==(attach==1?20:50)&&m.activePokemonView(A).damage()==50&&!m.hasPendingEffectChoice(),"Rock Salt healed wrong host or healed after declining Energy");check(!m.canUseAbility(A,source,a.id()),"Rock Salt reused source");
        }
    }
    static void evolutionHeal(){
        for(String id:List.of("sv5_en_015","sv8-5_en_008","sv7_en_067","sv7_en_160","sv7_en_169"))for(boolean accept:List.of(false,true)){
            var a=DATA.pokemon(id).orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(a);boolean group=a.id().equals("time-to-chow-down");var m=game(GeneratedRuleBatch8Test.hit(10,false),List.of(a),EnergyType.GRASS);UUID front=m.activePokemonView(A).pokemonId(),source=m.benchPokemonView(A).getFirst().pokemonId();
            // A healthy evolved recipient must retain its Energy; a Basic does not qualify for the group heal.
            if(group){m.evolveByEffect(A,front,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);settle(m,false);m.placeDamageCountersByEffect(A,A,source,4);}
            else m.placeDamageCountersByEffect(A,A,front,4);
            pay(m,A,front,1);pay(m,A,source,1);
            m.evolveByEffect(A,source,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(m.triggerOrderPrompt()!=null,"Missing hand evolution trigger");settle(m,accept);
            var healed=group?m.benchPokemonView(A).getFirst():m.activePokemonView(A);var untouched=group?m.activePokemonView(A):m.benchPokemonView(A).getFirst();
            check(healed.damage()==(accept?0:40)&&healed.attachedEnergyIds().size()==(accept?0:1)&&untouched.attachedEnergyIds().size()==1&&!m.hasPendingEffectChoice(),"Evolution heal/decline discarded wrong Energy or wrong target");
        }
    }
    static void giacomo(){
        var m=ModernCountRepairTest.game("sv8-5_en_138",List.of());UUID first=m.activePokemonView(B).pokemonId(),second=m.benchPokemonView(B).getFirst().pokemonId();var one=hand(m,B,"special");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,one.instanceId(),B,first);var two=hand(m,B,"special");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,two.instanceId(),B,second);pay(m,B,first,1);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);
        var p=m.effectCardPrompt(A);CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(one.instanceId())));
        m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(one.instanceId(),two.instanceId()));
        check(m.privateView(B).discard().containsAll(List.of(one,two))&&m.activePokemonView(B).attachedEnergyIds().size()==1&&m.benchPokemonView(B).getFirst().attachedEnergyIds().isEmpty(),"Giacomo lost Basic Energy or missed one host");
    }
    static void drum(){
        // Exercise the actual trainer against a public family count with the ordinary turn and draw pipeline.
        var attack=GeneratedRuleBatch8Test.hit(10,false);var trainer=DATA.trainer("sv5_en_141").orElseThrow();var base=StadiumActivationTest.definition("basic",CardCategory.POKEMON,TrainerCategory.NONE,true);
        var ancient=new CardDefinition(base.id(),base.nameZh(),base.category(),base.trainerCategory(),"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(PokemonFamily.ANCIENT),Set.of(EnergyType.GRASS));
        var defs=new CardDatabase(List.of(ancient,StadiumActivationTest.definition("trainer",CardCategory.TRAINER,TrainerCategory.ITEM,false)));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack),List.of(),EnergyType.GRASS)),Map.of(),Map.of("trainer",trainer));
        var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,i%2==0?"basic":"trainer"));decks.add(cards);}
        var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,defs,combat,new ServerRandom(0));OpeningTestSupport.decideOrder(m,A,B);OpeningTestSupport.complete(m,A,B,1);int before=m.privateView(A).hand().size();m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);check(m.privateView(A).hand().size()==before+1&&!m.hasPendingEffectChoice(),"Awakening Drum must draw for both own Ancient Pokemon only");
    }
}
