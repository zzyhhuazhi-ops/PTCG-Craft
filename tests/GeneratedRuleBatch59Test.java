import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual ME5 Trainer programs: eligibility is checked before consuming the physical Trainer. */
public final class GeneratedRuleBatch59Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> BELLS=List.of("me5_en_075","me5_en_106"),GLADIONS=List.of("me5_en_077","me5_en_108","me5_en_118"),GRUNTS=List.of("me5_en_081","me5_en_112");
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static CardInstance play(ServerMatch m){var c=hand(m,A,"trainer");m.playTrainer(A,c.instanceId(),null);return c;}
    static void lastCard(ServerMatch m){var keep=hand(m,A,"trainer");m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.equals(keep)).map(CardInstance::instanceId).toList());}
    static CardInstance recover(ServerMatch m,String id){var c=m.privateView(A).discard().stream().filter(x->x.definitionId().equals(id)).findFirst().orElseThrow();m.recoverDiscardCardByEffect(A,A,c.instanceId(),CardEffectDestination.HAND);return c;}
    static AttackDefinition hit(EnergyType type,boolean field,int damage){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"test\",\"energyCost\":{},\"baseDamage\":"+damage+",\"damageType\":\""+type+"\",\"effectSupport\":\"SUPPORTED\""+(field?",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"":"")+"}").getAsJsonObject(),EffectRegistry.standard());}
    static class Config {String card;Set<EnergyType> own=Set.of(EnergyType.FIRE),opponent=Set.of(EnergyType.WATER);Set<PokemonFamily> family=Set.of(),evolvedFamily=Set.of();List<AbilityDefinition> abilities=List.of();AttackDefinition attack=hit(EnergyType.COLORLESS,false,40);Config(String card){this.card=card;}}
    static ServerMatch game(String card){return game(new Config(card));}
    static ServerMatch game(Config c){
        var trainer=GeneratedRuleBatch8Test.trainer(c.card);String[] names={"basic","basic","basic","basic","evolved","energy","energy","special","trainer","trainer"};
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","opponent","opponent_evolved","energy","special","trainer")){
            boolean pokemon=!Set.of("energy","special","trainer").contains(id),opponent=id.startsWith("opponent"),evolved=id.endsWith("evolved");var types=opponent?c.opponent:c.own;
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("trainer")?CardCategory.TRAINER:CardCategory.ENERGY,id.equals("trainer")?trainer.category():TrainerCategory.NONE,"J",Set.of("international"),pokemon&&!evolved,id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,pokemon&&!opponent?(evolved?c.evolvedFamily:c.family):Set.of(),pokemon?types:Set.of()));
            if(pokemon)profiles.put(id,new PokemonProfile(500,0,evolved?(opponent?"opponent":"basic"):null,EnergyType.GRASS,2,EnergyType.FIGHTING,30,1,List.of(c.attack),opponent?List.of():c.abilities,types.iterator().next()));
        }
        var catalog=new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS),Map.of("trainer",trainer),Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++){String id=names[i%names.length];if(owner.equals(B)){if(id.equals("basic"))id="opponent";if(id.equals("evolved"))id="opponent_evolved";}cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,id));}decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->m.cardDefinition(x.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->List.of("energy","special","trainer",p.equals(A)?"basic":"opponent",p.equals(A)?"evolved":"opponent_evolved").stream().allMatch(id->m.privateView(p).hand().stream().anyMatch(x->x.definitionId().equals(id)))))return m;
        }throw new AssertionError("No Trainer fixture");
    }
    static CardInstance attach(ServerMatch m,UUID from,UUID owner,UUID host,String id){var c=hand(m,from,id);m.attachEnergyByEffect(m.currentPlayer(),from,EffectSourceZone.HAND,c.instanceId(),owner,host);return c;}
    static void shield(ServerMatch m,String target,String from,String source){var e=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"prevent_effects\",\"target\":\""+target+"\",\"source\":\""+source+"\",\"from\":\""+from+"\",\"scope\":\"EFFECTS_ONLY\",\"duration\":\"LEAVES_PLAY\"}").getAsJsonObject());m.executeEffectProgramByRule(A,null,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e)))),EffectTrigger.ON_CARD_PLAYED);}
    static void unavailable(ServerMatch m){var c=hand(m,A,"trainer");check(!m.canPlayTrainer(A,c.instanceId(),null),"Illegal Trainer offered");atomic(m,()->m.playTrainer(A,c.instanceId(),null));}
    static void unavailable(ServerMatch m,String caseName){var c=hand(m,A,"trainer");check(!m.canPlayTrainer(A,c.instanceId(),null),"Illegal Trainer offered: "+caseName);atomic(m,()->m.playTrainer(A,c.instanceId(),null));}
    static boolean confused(ServerMatch m,UUID p){return m.activePokemonView(p).conditions().contains(SpecialCondition.CONFUSED);}
    static void bells(){for(String id:BELLS)for(String mode:List.of("BOTH","OWN_DARK","OPPONENT_DARK","BOTH_DARK","DUAL","OWN_CONFUSED","BOTH_CONFUSED","POSITIONAL","PROTECTED")){
        var c=new Config(id);if(Set.of("OWN_DARK","BOTH_DARK").contains(mode))c.own=Set.of(EnergyType.DARKNESS);if(Set.of("OPPONENT_DARK","BOTH_DARK").contains(mode))c.opponent=Set.of(EnergyType.DARKNESS);if(mode.equals("DUAL"))c.own=Set.of(EnergyType.FIRE,EnergyType.DARKNESS);var m=game(c);
        if(Set.of("OWN_CONFUSED","BOTH_CONFUSED").contains(mode))m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);if(mode.equals("BOTH_CONFUSED"))m.applySpecialConditionByEffect(A,B,SpecialCondition.CONFUSED);
        if(mode.equals("POSITIONAL")){m.applySpecialConditionByEffect(A,A,SpecialCondition.PARALYZED);m.applySpecialConditionByEffect(A,B,SpecialCondition.ASLEEP);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);}
        if(mode.equals("PROTECTED"))shield(m,"OPPONENT_ACTIVE","OPPONENT","TRAINER");
        if(Set.of("BOTH_DARK","BOTH_CONFUSED").contains(mode)){unavailable(m,id+"/"+mode);continue;}
        int turn=m.turnNumber();var card=play(m);check(confused(m,A)==!Set.of("OWN_DARK","DUAL").contains(mode)&&confused(m,B)==!Set.of("OPPONENT_DARK","PROTECTED").contains(mode),"Dark Bell target/type/status/protection "+id+"/"+mode);
        check(m.benchPokemonView(A).getFirst().conditions().isEmpty()&&m.benchPokemonView(B).getFirst().conditions().isEmpty()&&m.turnNumber()==turn&&m.privateView(A).discard().contains(card),"Bell affected Bench/ended turn/lost card");
        if(mode.equals("POSITIONAL"))check(!m.activePokemonView(A).conditions().contains(SpecialCondition.PARALYZED)&&!m.activePokemonView(B).conditions().contains(SpecialCondition.ASLEEP)&&m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED),"Confusion incorrectly replaced Poison or retained positional status");
    }
        for(boolean suppressed:List.of(false,true)){
            var c=new Config(BELLS.getFirst());c.opponent=Set.of(EnergyType.DARKNESS);c.abilities=List.of(new AbilityDefinition("immune","immune",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ALL_SPECIAL_CONDITIONS,0),EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE));var m=game(c);
            if(suppressed){m.disableAbilityEffectsByEffect(A,A,active(m,A));play(m);check(confused(m,A),"Suppressed immunity still blocked Bell");}else unavailable(m);
        }
    }
    static void gladions(){for(String id:GLADIONS)for(String mode:List.of("PLAIN","BOX","EVOLVE_BOX","EVOLVE_PLAIN","NEW","BENCH","WEAK","RESIST","ZERO")){
        var c=new Config(id);if(Set.of("BOX","EVOLVE_PLAIN").contains(mode))c.family=Set.of(PokemonFamily.RULE_BOX,PokemonFamily.EX);if(mode.equals("EVOLVE_BOX"))c.evolvedFamily=Set.of(PokemonFamily.RULE_BOX,PokemonFamily.EX);
        c.attack=hit(mode.equals("WEAK")?EnergyType.GRASS:mode.equals("RESIST")?EnergyType.FIGHTING:EnergyType.COLORLESS,mode.equals("BENCH"),mode.equals("ZERO")?0:40);var m=game(c);unavailable(m);lastCard(m);check(m.canPlayTrainer(A,hand(m,A,"trainer").instanceId(),null),"Last card not usable");var used=play(m);check(m.privateView(A).hand().isEmpty()&&m.currentPlayer().equals(A),"Last-card Supporter did not resolve");
        if(mode.startsWith("EVOLVE"))m.evolveByEffect(A,active(m,A),recover(m,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(mode.equals("NEW")){var late=recover(m,"basic");m.playBasicPokemonToBench(A,late.instanceId());m.switchActiveByEffect(A,A,PokemonIdentityTestSupport.logical(m,A,late.instanceId()));}
        m.recoverDiscardCardByEffect(A,A,used.instanceId(),CardEffectDestination.HAND);unavailable(m);m.attack(A,"hit");int expected=switch(mode){case "BOX","EVOLVE_BOX"->40;case "WEAK"->240;case "RESIST"->90;case "ZERO"->0;default->120;};check(m.lastAttack().finalDamage()==expected,"Live RuleBox/before W-R bonus "+id+"/"+mode+" "+m.lastAttack());if(mode.equals("BENCH"))check(m.benchPokemonView(B).getFirst().damage()==40,"Active-only bonus leaked to Bench");
        m.endTurn(B);m.attack(A,"hit");check(m.lastAttack().finalDamage()==(mode.equals("ZERO")?0:mode.equals("WEAK")?80:mode.equals("RESIST")?10:40),"This-turn bonus survived expiry");
    }}
    static void history(ServerMatch m,String cause){
        UUID host=active(m,A);if(Set.of("ATTACK","COUNTERS","CHECKUP").contains(cause))m.placeDamageCountersByEffect(A,A,host,cause.equals("ATTACK")?47:49);
        if(cause.equals("OWN")){m.knockOutPokemonByEffect(A,A,bench(m,A));StarterDeck100CombatTest.settleKnockouts(m);}
        m.endTurn(A);
        switch(cause){
            case "ATTACK"->{m.attack(B,"hit");StarterDeck100CombatTest.settleKnockouts(m);}
            case "COUNTERS"->{m.placeDamageCountersByEffect(B,A,host,1);StarterDeck100CombatTest.settleKnockouts(m);m.endTurn(B);}
            case "DIRECT"->{m.knockOutPokemonByEffect(B,A,bench(m,A));StarterDeck100CombatTest.settleKnockouts(m);m.endTurn(B);}
            case "CHECKUP"->{m.applySpecialConditionByEffect(B,A,SpecialCondition.POISONED);m.endTurn(B);StarterDeck100CombatTest.settleKnockouts(m);}
            default->m.endTurn(B);
        }check(m.currentPlayer().equals(A),"History fixture did not return to owner");
    }
    static void grunts(){for(String id:GRUNTS)for(String cause:List.of("NONE","OWN","CHECKUP","ATTACK","COUNTERS","DIRECT")){
        var m=game(id);attach(m,B,B,active(m,B),"energy");var selected=attach(m,B,B,bench(m,B),"special");history(m,cause);
        if(!Set.of("ATTACK","COUNTERS","DIRECT").contains(cause)){unavailable(m);continue;}
        check(m.canPlayTrainer(A,hand(m,A,"trainer").instanceId(),null),"Any KO gate failed "+cause);int count=m.privateView(B).discard().size();play(m);var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&p.candidates().size()==2&&p.candidates().contains(selected),"Energy field candidates wrong");
        atomic(m,()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(selected.instanceId())));atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(hand(m,A,"energy").instanceId())));atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().map(CardInstance::instanceId).toList()));
        m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(selected.instanceId()));check(m.privateView(B).discard().contains(selected)&&m.privateView(B).discard().size()==count+1&&m.activePokemonView(B).attachedEnergyIds().size()==1&&m.benchPokemonView(B).getFirst().attachedEnergyIds().isEmpty(),"Special Energy discard used units/wrong host/owner");atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(selected.instanceId())));m.endTurn(A);m.endTurn(B);unavailable(m);
    }
        for(String mode:List.of("EMPTY","PROTECTED","FOREIGN_OWNER","ACTIVE")){
            var m=game(GRUNTS.getFirst());history(m,"DIRECT");if(mode.equals("EMPTY")){unavailable(m);continue;}
            var selected=attach(m,mode.equals("FOREIGN_OWNER")?A:B,B,active(m,B),"energy");if(mode.equals("PROTECTED"))shield(m,"OPPONENT_ACTIVE","OPPONENT","TRAINER");play(m);var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(selected.instanceId()));
            check(m.activePokemonView(B).attachedEnergyIds().contains(selected.instanceId())==mode.equals("PROTECTED"),"Protection or Active discard mismatch");if(!mode.equals("PROTECTED"))check(m.privateView(selected.ownerId()).discard().contains(selected),"Foreign Energy did not return to actual owner");
        }
    }
    static void printed(){var db=BundledRules.loadCardDefinitions();for(var group:List.of(BELLS,GLADIONS,GRUNTS))for(String id:group){check(db.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Missing full card "+id);var trainer=GeneratedRuleBatch8Test.trainer(id);check(trainer.category()==(BELLS.contains(id)?TrainerCategory.ITEM:TrainerCategory.SUPPORTER)&&TrainerProgramRules.unsupportedReason(trainer.effectProgram()).isEmpty(),"Actual Trainer program rejected");}}
    public static void main(String[] args){printed();bells();gladions();grunts();System.out.println("GeneratedRuleBatch59Test passed: all seven real Trainer prints; independent non-Darkness confusion/no-op legality, dual types and immunity, last-Hand-card preflight, live RuleBox/new/evolved recipients, Weakness/Resistance/Bench/expiry, Supporter quota, exact previous opponent-turn KO causes, atomic physical Energy selections, protection and ownership");}
}
