import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Generated Special Energy immunity cures continuously and prevents exactly its printed Special Conditions. */
public final class GeneratedRuleBatch32Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog CATALOG=BundledCombatData.load();
    static final CardDatabase CARDS=BundledRules.loadCardDefinitions();
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch16Test.card(m,p,id);}
    static EnergyProfile energy(String id){return CATALOG.energyProfile(id).orElseThrow();}
    static CardInstance attach(ServerMatch m,String id,UUID owner,UUID host){var e=hand(m,owner,id);m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,e.instanceId(),owner,host);return e;}
    static class Config {
        String energy;EnergyType type=EnergyType.COLORLESS,evolvedType=EnergyType.COLORLESS;
        Set<PokemonFamily> family=Set.of(),evolvedFamily=Set.of();Set<EnergyType> printedTypes;boolean unplayedTypedEnergy;
        AttackDefinition attack=GeneratedRuleBatch8Test.hit(100,false);
        boolean weakness;List<AbilityDefinition> abilities=List.of();
        TrainerProfile stadium=DynamicEnergyProvisionTest.stadium();
        Config(String id){energy=id;}
    }
    static ServerMatch game(String id){return game(new Config(id));}
    static ServerMatch game(Config c){
        String[] ids={"basic","basic","evolved",c.energy,"grass","stadium","wrong"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            if(id.equals(c.energy)){defs.add(CARDS.find(id).orElseThrow());continue;}
            boolean pokemon=Set.of("basic","evolved","wrong").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("grass")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("stadium")?TrainerCategory.STADIUM:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic")||id.equals("wrong"),id.equals("grass"),60,Map.of(),EffectSupport.SUPPORTED,null,pokemon?(id.equals("evolved")?c.evolvedFamily:id.equals("basic")?c.family:Set.of()):Set.of(),pokemon?(id.equals("basic")&&c.printedTypes!=null?c.printedTypes:Set.of(id.equals("evolved")?c.evolvedType:id.equals("basic")?c.type:EnergyType.WATER)):Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","wrong"))pokemon.put(id,new PokemonProfile(500,3,id.equals("evolved")?"basic":null,c.weakness?EnergyType.COLORLESS:null,c.weakness?2:0,null,0,1,List.of(c.attack),c.abilities,id.equals("evolved")?c.evolvedType:id.equals("basic")?c.type:EnergyType.WATER));
        var energies=new HashMap<>(Map.of(c.energy,energy(c.energy)));if(c.unplayedTypedEnergy)energies.put("unplayed-typed-energy",energy("xy4_en_112"));
        var catalog=new CombatCatalog(pokemon,Map.of("grass",EnergyType.GRASS),Map.of("stadium",c.stadium),energies);
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->x.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(x->x.definitionId().equals(id)))))return m;
        }
        throw new AssertionError("Special Energy fixture");
    }

    static Config config(String id){var c=new Config(id);if(id.equals("me4_en_084"))c.type=EnergyType.WATER;if(id.equals("swsh4_en_162"))c.type=EnergyType.GRASS;if(id.equals("swsh6_en_157"))c.family=Set.of(PokemonFamily.SINGLE_STRIKE);if(id.equals("swsh6_en_159"))c.family=Set.of(PokemonFamily.RAPID_STRIKE);return c;}
    static Set<SpecialCondition> immune(String id){return switch(id){case "sv2_en_193"->Set.of(SpecialCondition.ASLEEP,SpecialCondition.CONFUSED,SpecialCondition.PARALYZED);case "swsh6_en_157"->Set.of(SpecialCondition.POISONED);case "swsh6_en_159"->Set.of(SpecialCondition.PARALYZED);default->EnumSet.allOf(SpecialCondition.class);};}
    static List<String> ids(){return List.of("me4_en_084","sv2_en_193","swsh4_en_162","swsh6_en_157","swsh6_en_159");}
    static void suppress(ServerMatch m){m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);}
    public static void main(String[] args){cureAndPrevent();sources();typeAndSuppression();movement();evolution();attackPaths();midProgram();invalidHost();schema();System.out.println("GeneratedRuleBatch32Test passed: exact continuous Special Condition cures/immunity, hand/deck/discard/movement, suppression restoration, printed types, source removal/evolution and legacy/composed attack paths");}
    static void cureAndPrevent(){for(String id:ids())for(var status:SpecialCondition.values())for(UUID owner:List.of(A,B)){
        var m=game(config(id));m.applySpecialConditionByEffect(A,owner,status);attach(m,id,owner,active(m,owner));boolean blocked=immune(id).contains(status);check(m.activePokemonView(owner).conditions().contains(status)!=blocked,"Existing status cure scope "+id+status);if(!blocked)m.clearSpecialConditionsByEffect(A,owner,active(m,owner),Set.of(status));m.applySpecialConditionByEffect(A,owner,status);check(m.activePokemonView(owner).conditions().contains(status)!=blocked,"New status immunity scope "+id+status);
    }}
    static void sources(){for(String source:List.of("MANUAL","HAND","DECK","DISCARD")){
        String id="swsh4_en_162";var m=game(config(id));m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED,4);m.applySpecialConditionByEffect(A,A,SpecialCondition.BURNED);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);CardInstance card=source.equals("DECK")?m.privateView(A).deck().stream().filter(x->x.definitionId().equals(id)).findFirst().orElseThrow():hand(m,A,id);if(source.equals("DISCARD"))m.discardHandCardsByEffect(A,A,List.of(card.instanceId()));if(source.equals("MANUAL"))m.attachEnergy(A,card.instanceId(),active(m,A));else m.attachEnergyByEffect(A,A,EffectSourceZone.valueOf(source),card.instanceId(),A,active(m,A));check(m.activePokemonView(A).conditions().isEmpty(),"Continuous cure incorrectly depends on hand-only trigger "+source);m.endTurn(A);check(m.activePokemonView(A).damage()==0,"Cured conditions still caused Checkup damage "+source);
    }
        var m=game(config("sv2_en_193"));m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.applySpecialConditionByEffect(A,A,SpecialCondition.BURNED);m.applySpecialConditionByEffect(A,A,SpecialCondition.PARALYZED);attach(m,"sv2_en_193",A,active(m,A));check(new HashSet<>(m.activePokemonView(A).conditions()).equals(Set.of(SpecialCondition.POISONED,SpecialCondition.BURNED)),"Therapeutic cured Poison/Burn");
    }
    static void typeAndSuppression(){for(String id:List.of("swsh4_en_162","me4_en_084"))for(String mode:List.of("wrong","dual","suppressed","ability-disabled","tool-disabled")){
        var c=config(id);if(mode.equals("wrong"))c.type=EnergyType.COLORLESS;if(mode.equals("dual")){c.printedTypes=Set.of(c.type,EnergyType.COLORLESS);c.type=EnergyType.COLORLESS;}var m=game(c);if(mode.equals("suppressed"))suppress(m);if(mode.equals("ability-disabled"))m.disableAbilityEffectsByEffect(A,A,active(m,A));if(mode.equals("tool-disabled"))m.disableToolEffectsByEffect(A,A,active(m,A));m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);attach(m,id,A,active(m,A));boolean works=!Set.of("wrong","suppressed").contains(mode);check(m.activePokemonView(A).conditions().contains(SpecialCondition.CONFUSED)!=works,"Type or suppression cure "+mode);if(mode.equals("suppressed")){m.discardStadiumByEffect(A);check(m.activePokemonView(A).conditions().isEmpty(),"Removing Energy suppression did not restore continuous cure");}
    }}
    static void movement(){String id="swsh4_en_162";var m=game(config(id));var card=attach(m,id,A,bench(m,A));m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.moveAttachedEnergyByEffect(A,A,bench(m,A),A,active(m,A),List.of(card.instanceId()));check(m.activePokemonView(A).conditions().isEmpty(),"Moving immunity Energy did not cure host");m.moveAttachedEnergyByEffect(A,A,active(m,A),A,bench(m,A),List.of(card.instanceId()));m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Immunity lingered after source moved");m.switchActiveByEffect(A,A,bench(m,A));m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);check(m.activePokemonView(A).conditions().isEmpty(),"Promoted Energy host did not prevent condition");m.returnAttachedEnergyToHandByEffect(A,A,active(m,A),List.of(card.instanceId()));m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Returned source retained immunity");}
    static void evolution(){for(String id:List.of("swsh4_en_162","swsh6_en_157","swsh6_en_159")){
        var c=config(id);var m=game(c);var card=attach(m,id,A,active(m,A));m.evolveByEffect(A,active(m,A),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);var status=immune(id).iterator().next();m.applySpecialConditionByEffect(A,A,status);check(m.activePokemonView(A).conditions().contains(status),"Evolved host kept former immunity "+id);if(id.startsWith("swsh6"))check(m.privateView(A).discard().contains(card),"Invalid style Energy was not discarded");else check(m.activePokemonView(A).attachedEnergyIds().contains(card.instanceId()),"Aromatic incorrectly had Grass-only attachment restriction");
    }}
    static AttackDefinition attack(String effect,boolean legacy){String extra=legacy?",\"conditions\":[{\"target\":\"DEFENDER\",\"condition\":\"POISONED\"}]":",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\""+effect+"\",\"target\":\"OPPONENT_ACTIVE\"}]}]";return DamageTriggeredAbilityTest.attack(100,extra);}
    static void attackPaths(){for(boolean legacy:List.of(false,true))for(String id:List.of("swsh4_en_162","sv2_en_193","swsh6_en_157")){
        var c=config(id);c.attack=attack("poison",legacy);var m=game(c);attach(m,id,B,active(m,B));m.attack(A,"hit");boolean immune=immune(id).contains(SpecialCondition.POISONED);check(m.activePokemonView(B).damage()==(immune?100:110),"Attack immunity changed direct damage or leaked Poison Checkup "+id+legacy);check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)!=immune,"Legacy/composed status path "+id+legacy);
    }}
    static void midProgram(){var c=config("swsh4_en_162");c.attack=DamageTriggeredAbilityTest.attack(100,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"attach_energy\",\"target\":\"SELF\",\"source\":\"HAND\",\"target_scope\":\"OWN_FIELD\",\"minimum\":1,\"amount\":1},{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HAS_STATUS\",\"status\":\"POISONED\"}],\"effects\":[{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}]}]}]");var m=game(c);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);int hand=m.privateView(A).hand().size();var card=hand(m,A,c.energy);m.attack(A,"hit");var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(card.instanceId()));check(!m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==hand-1,"Continuous cure waited until the entire program finished");check(m.activePokemonView(A).conditions().isEmpty()&&m.activePokemonView(A).damage()==0,"Cure did not precede Checkup");}

    static void invalidHost(){var c=config("swsh6_en_157");c.family=Set.of();c.attack=DamageTriggeredAbilityTest.attack(100,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"attach_energy\",\"target\":\"SELF\",\"source\":\"DISCARD\",\"target_scope\":\"OWN_FIELD\",\"invalid_host_policy\":\"DISCARD\",\"minimum\":1,\"amount\":1}]}]");var m=game(c);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);var card=hand(m,A,c.energy);m.discardHandCardsByEffect(A,A,List.of(card.instanceId()));m.attack(A,"hit");var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(card.instanceId()));check(m.privateView(A).discard().contains(card)&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Invalid Impact Energy was not discarded");check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED)&&m.activePokemonView(A).damage()==10,"Invalid host received transient cure before immediate discard");}
    static void schema(){for(String fields:List.of("","\"statuses\":\"\"","\"statuses\":\"POISONED,POISONED\"","\"statuses\":\"UNKNOWN\"","\"statuses\":\"ALL\",\"amount\":1","\"statuses\":\"ALL\",\"target\":\"ALL_OWN_POKEMON\"","\"statuses\":\"ALL\",\"conditions\":[{\"type\":\"HAS_STATUS\",\"status\":\"POISONED\"}]"))reject(()->SpecialEnergyTemplateTest.energy("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{\"type\":\"special_condition_immunity\""+(fields.isEmpty()?"":","+fields)+"}]}"));}
}
