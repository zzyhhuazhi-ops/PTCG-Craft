import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Generated public field queries and healing, with live typed selection and unrelated operation boundaries. */
public final class GeneratedRuleBatch19Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Ruleset RULES=GeneratedRuleBatch8Test.RULES;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static TrainerProfile plain(){return GeneratedRuleBatch8Test.trainer("swsh5_en_124");}
    static ServerMatch game(AttackDefinition atk,TrainerProfile trainer,List<AbilityDefinition> abilities,Map<String,Set<EnergyType>> tags){return fixture(abilities,EnergyType.GRASS,EnergyType.GRASS,trainer,atk,tags);}
    static ServerMatch game(AttackDefinition atk){return game(atk,plain(),List.of(),Map.of());}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch6Test.attack(id,slot);}
    static ServerMatch trainer(String id){return game(GeneratedRuleBatch8Test.hit(100,false),GeneratedRuleBatch8Test.trainer(id),List.of(),Map.of());}
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy,EnergyType type,TrainerProfile trainer,AttackDefinition attack,Map<String,Set<EnergyType>> tags){
        String[] ids={"basic","basic","basic","basic","energy","energy","wrong","special","tool","tool","tool","tool","trainer","evolved","rulebox"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","rulebox").contains(id),isEnergy=Set.of("energy","wrong","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:isEnergy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("trainer")?trainer.category():id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),
                id.equals("basic")||id.equals("rulebox"),id.equals("energy")||id.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null,
                id.equals("rulebox")?Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX):Set.of(),pokemon?tags.getOrDefault(id,Set.of(id.equals("evolved")?EnergyType.PSYCHIC:type)):Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","rulebox"))pokemon.put(id,new PokemonProfile(500,1,id.equals("evolved")?"basic":null,null,0,null,0,
            id.equals("rulebox")?2:1,List.of(id.equals("rulebox")?GeneratedRuleBatch8Test.hit(100,false):attack),id.equals("rulebox")?List.of():abilities,id.equals("evolved")?EnergyType.PSYCHIC:type));
        var combat=new CombatCatalog(pokemon,Map.of("energy",energy,"wrong",energy==EnergyType.FIRE?EnergyType.WATER:EnergyType.FIRE),
            Map.of("trainer",trainer,"tool",DynamicPassiveModifierTest.plainTool()),Map.of("special",EnergyProfile.fixed(energy,2)));
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).toList();return basics.size()<2||basics.subList(0,2).stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))&&
                m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).count()>=5)return m;
        }throw new AssertionError("No batch 8 fixture");
    }

    public static void main(String[] args){typedQueries();distinctTypes();rollout();healingAndStatus();benchHealingDoesNotCureActive();typedSelection();fullHealing();activeAbility();recover();opponentDiscard();remainingPrizes();moveOpponentEnergy();recentKnockout();boundHealingTarget();System.out.println("GeneratedRuleBatch19Test passed: live typed/dual/fallback queries, distinct Bench types, named attacks, scoped healing/status, preflight selection, actual discard and private opponent choice");}
    static void typedQueries(){for(boolean fallback:List.of(false,true)){
        var tags=Map.of("basic",fallback?Set.<EnergyType>of():Set.of(EnergyType.GRASS,EnergyType.FIRE),"rulebox",Set.of(EnergyType.FIRE));
        var heal=attack("bw1_en_005",1);var m=game(heal,plain(),List.of(),tags);var wrong=hand(m,A,"rulebox");m.playBasicPokemonToBench(A,wrong.instanceId());m.placeDamageCountersByEffect(A,A,active(m,A),5);m.placeDamageCountersByEffect(A,A,bench(m,A),5);m.placeDamageCountersByEffect(A,A,wrong.instanceId(),5);m.placeDamageCountersByEffect(A,B,bench(m,B),5);m.attack(A,heal.id());
        check(m.activePokemonView(A).damage()==30&&m.benchPokemonView(A).getFirst().damage()==30&&m.benchPokemonView(A).getLast().damage()==50&&m.benchPokemonView(B).getFirst().damage()==50,"Typed whole-field heal wrong host/owner/fallback "+fallback);
        var count=attack("sv5_en_012",0);var n=game(count,plain(),List.of(),tags);n.playBasicPokemonToBench(A,hand(n,A,"rulebox").instanceId());n.attack(A,count.id());check(n.lastAttack().finalDamage()==60,"Typed count double-counted dual type, included mismatch or skipped fallback");
        var x=game(count,plain(),List.of(),tags);x.evolveByEffect(A,bench(x,A),hand(x,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);x.attack(A,count.id());check(x.lastAttack().finalDamage()==30,"Typed count used covered Basic card");
    }}
    static void distinctTypes(){var atk=attack("sm5_en_105",0);for(boolean evolved:List.of(false,true)){
        var m=game(atk,plain(),List.of(),Map.of("basic",Set.of(EnergyType.GRASS,EnergyType.FIRE)));if(evolved)m.evolveByEffect(A,bench(m,A),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);else m.playBasicPokemonToBench(A,hand(m,A,"basic").instanceId());m.attack(A,atk.id());check(m.lastAttack().finalDamage()==(evolved?10:20),"Distinct Bench types counted duplicates/Active/covered card");
    }}
    static void rollout(){var atk=attack("swsh8_en_058",1);var m=game(atk);m.playBasicPokemonToBench(A,hand(m,A,"rulebox").instanceId());m.attack(A,atk.id());check(m.lastAttack().finalDamage()==20,"Rollout included Active, wrong attack or opponent Bench");}
    static void healingAndStatus(){for(boolean damaged:List.of(false,true))for(boolean status:List.of(false,true)){
        var m=trainer("xy2_en_093");if(damaged)m.placeDamageCountersByEffect(A,A,active(m,A),8);if(status)m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);var card=hand(m,A,"trainer");
        if(!damaged&&!status){GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,card.instanceId(),null));check(m.privateView(A).hand().contains(card),"No-effect heal/status Trainer consumed");continue;}
        m.playTrainer(A,card.instanceId(),null);var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,A));check(m.activePokemonView(A).damage()==(damaged?20:0)&&m.activePokemonView(A).conditions().isEmpty(),"Heal/status Trainer lost one effect");
    }}
    static void benchHealingDoesNotCureActive(){var m=trainer("xy2_en_093");m.placeDamageCountersByEffect(A,A,bench(m,A),8);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench(m,A));check(m.benchPokemonView(A).getFirst().damage()==20&&m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Bench heal/cure cleared the unrelated Active Pokemon");
        for(String status:List.of("poison","burn","sleep","paralyze","confuse"))GeneratedRuleBatch8Test.reject(()->TrainerProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,"{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"effects\":[{\"type\":\""+status+"\",\"target\":\"CHOSEN_POKEMON\"}]}")))));
    }
    static void typedSelection(){var t=GeneratedRuleBatch8Test.trainer("me3_en_075");var m=game(GeneratedRuleBatch8Test.hit(100,false),t,List.of(),Map.of());
        m.placeDamageCountersByEffect(A,A,active(m,A),20);var card=hand(m,A,"trainer");GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,card.instanceId(),active(m,A)));check(m.privateView(A).hand().contains(card),"Wrong type target consumed Trainer");m.evolveByEffect(A,bench(m,A),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.placeDamageCountersByEffect(A,A,bench(m,A),20);GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,card.instanceId(),active(m,A)));m.playTrainer(A,card.instanceId(),bench(m,A));check(m.benchPokemonView(A).getFirst().damage()==50&&m.activePokemonView(A).damage()==200,"Typed heal amount/target");
    }
    static void fullHealing(){var m=trainer("sm2_en_128");GeneratedRuleBatch13Test.attach(m,A,active(m,A),"special");m.placeDamageCountersByEffect(A,A,active(m,A),20);m.playTrainer(A,hand(m,A,"trainer").instanceId(),active(m,A));check(m.activePokemonView(A).damage()==0&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Max Potion did not discard after actual healing");
        for(int counters:List.of(46,47)){
            var n=trainer("sv5_en_142");n.placeDamageCountersByEffect(A,A,active(n,A),counters);var card=hand(n,A,"trainer");if(counters==46){GeneratedRuleBatch8Test.reject(()->n.playTrainer(A,card.instanceId(),null));continue;}n.playTrainer(A,card.instanceId(),null);var q=n.effectPokemonPrompt(A);n.chooseEffectPokemon(A,q.resolutionId(),q.step(),active(n,A));check(n.activePokemonView(A).damage()==0,"Low remaining HP full heal failed at threshold");
        }
    }
    static void activeAbility(){var a=GeneratedRuleBatch8Test.ability("sv3-5_en_003");var m=game(GeneratedRuleBatch8Test.hit(100,false),plain(),List.of(a),Map.of());m.placeDamageCountersByEffect(A,A,bench(m,A),8);check(!m.canUseAbility(A,bench(m,A),a.id(),bench(m,A))&&m.canUseAbility(A,active(m,A),a.id(),bench(m,A)),"Active-only healing source gate");m.useAbility(A,active(m,A),a.id(),bench(m,A));check(m.benchPokemonView(A).getFirst().damage()==20&&!m.canUseAbility(A,active(m,A),a.id(),bench(m,A)),"Active-only heal scope/use limit");}
    static void recover(){var atk=attack("swsh8_en_132",0);var m=game(atk);var card=hand(m,A,"wrong");m.discardHandCardsByEffect(A,A,List.of(card.instanceId()));m.attack(A,atk.id());var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&p.candidates().equals(List.of(card)),"Any-card recovery did not use exact discard");m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(card.instanceId()));check(m.privateView(A).hand().contains(card),"Recovery did not transfer to hand");}
    static void opponentDiscard(){var atk=attack("sm9_en_094",0);var m=game(atk);int size=m.privateView(B).hand().size();m.attack(A,atk.id());var p=m.effectCardPrompt(B);check(p.minimum()==2&&p.maximum()==2&&m.clientView(A).effectSelection()==null,"Opponent discard privacy/count");var chosen=p.candidates().stream().limit(2).map(CardInstance::instanceId).toList();GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),chosen));m.submitEffectCards(B,p.resolutionId(),p.step(),chosen);check(m.privateView(B).hand().size()==size-1&&m.privateView(B).discard().stream().map(CardInstance::instanceId).toList().containsAll(chosen),"Opponent discard wrong number");}
    static void remainingPrizes(){var atk=attack("sm4_en_057",2);for(int taken:List.of(0,2)){
        var m=game(atk);GeneratedRuleBatch9Test.take(m,A,taken);m.attack(A,atk.id());check(m.lastAttack().finalDamage()==40*(6-taken),"Absorption GX remaining vs taken Prize count");
    }}

    static void moveOpponentEnergy(){var atk=attack("sv1_en_026",0);for(boolean move:List.of(false,true)){
        var m=game(atk);var card=hand(m,B,"special");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,card.instanceId(),B,active(m,B));m.attack(A,atk.id());var option=m.effectChoicePrompt(A);m.chooseEffectOption(A,option.resolutionId(),option.step(),move?0:1);
        if(move){var p=m.effectCardPrompt(A);check(p.candidates().equals(List.of(card)),"Movement selected wrong attached Energy");m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(card.instanceId()));var dest=m.effectAttachmentPrompt(A);GeneratedRuleBatch8Test.reject(()->m.chooseEffectAttachmentTarget(A,dest.resolutionId(),dest.step(),active(m,A)));GeneratedRuleBatch8Test.reject(()->m.chooseEffectAttachmentTarget(B,dest.resolutionId(),dest.step(),bench(m,B)));m.chooseEffectAttachmentTarget(A,dest.resolutionId(),dest.step(),bench(m,B));}
        check(m.activePokemonView(B).attachedEnergyIds().contains(card.instanceId())!=move&&m.benchPokemonView(B).getFirst().attachedEnergyIds().contains(card.instanceId())==move,"Move Energy lost card, units or optional choice");
    }}
    static void recentKnockout(){var atk=attack("bw1_en_091",0);for(boolean byAttack:List.of(false,true)){
        var m=game(atk);m.endTurn(A);m.placeDamageCountersByEffect(B,A,active(m,A),byAttack?48:50);if(byAttack)m.attack(B,atk.id());m.takePrizeAt(B,0);m.chooseReplacement(A,bench(m,A));if(m.currentPlayer().equals(B))m.endTurn(B);m.attack(A,atk.id());check(m.lastAttack().finalDamage()==(byAttack?90:20),"Revenge confused counters with attack damage");
        m.endTurn(B);m.attack(A,atk.id());check(m.lastAttack().finalDamage()==20,"Revenge retained older KO");
    }}
    static void boundHealingTarget(){var m=trainer("sm2_en_128");m.placeDamageCountersByEffect(A,A,bench(m,A),8);var card=hand(m,A,"trainer");GeneratedRuleBatch13Test.attach(m,A,active(m,A),"special");var before=m.clientView(A);GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,card.instanceId(),active(m,A)));check(before.equals(m.clientView(A)),"Preselected healthy target was replaced by another damaged host during preflight");m.playTrainer(A,card.instanceId(),bench(m,A));check(m.benchPokemonView(A).getFirst().damage()==0&&m.activePokemonView(A).attachedEnergyIds().size()==1,"Full heal touched unrelated Energy");}
}
