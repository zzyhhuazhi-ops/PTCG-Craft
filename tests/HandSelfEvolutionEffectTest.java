import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic primitives: actual-card text/profile coverage belongs to GeneratedRuleBatch88Test. */
public final class HandSelfEvolutionEffectTest {
    static final UUID A=new UUID(0,88001),B=new UUID(0,88002);
    static final String EVOLVE="""
        {"type":"evolve_from_hand","target":"SELF","amount":1,"minimum":1,"card_category":"POKEMON","timing":"AFTER_OWNER_FIRST_TURN","bind":"evolved"}
        """.trim();
    static final String SUFFIX="""
        {"type":"conditional","conditions":[{"type":"BOUND_COUNT","bind":"evolved","value":1}],"effects":[{"type":"damage_counter","target":"SELF","amount":2}]}
        """.trim();
    static AbilityDefinition ability(String effects) {
        return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"ascend\",\"nameZh\":\"手牌进化夹具\",\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"ONCE_PER_TURN\",\"conditions\":[{\"type\":\"NOT\",\"conditions\":[{\"type\":\"TURN_STATE\",\"value\":\"OWN_FIRST_TURN\"}]}],\"effects\":["+effects+"]}]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static CardInstance hand(ServerMatch m,UUID p,String id){return m.privateView(p).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static BattlefieldState field(ServerMatch m){try{var f=ServerMatch.class.getDeclaredField("battlefield");f.setAccessible(true);return (BattlefieldState)f.get(m);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException|IllegalStateException e){return;}throw new AssertionError("Expected rejection");}
    static ServerMatch fixture(boolean second,List<AbilityDefinition> basic,List<AbilityDefinition> evolved,TrainerProfile trainer) {
        String[] ids={"basic","basic","basic","evolved","wrong","energy","fire","special","component"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean p=Set.of("basic","evolved","wrong").contains(id),e=Set.of("energy","fire","special").contains(id);
            defs.add(new CardDefinition(id,id,p?CardCategory.POKEMON:e?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("component")?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),Set.of("energy","fire").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        var catalog=new CombatCatalog(Map.of("basic",new PokemonProfile(70,1,null,null,0,null,0,1,List.of(),basic),
            "evolved",new PokemonProfile(130,1,"basic",null,0,null,0,1,List.of(),evolved),
            "wrong",new PokemonProfile(150,1,"absent",null,0,null,0,1,List.of())),
            Map.of("energy",EnergyType.WATER,"fire",EnergyType.FIRE),Map.of("component",trainer),Map.of("special",EnergyProfile.fixed(EnergyType.WATER,2)));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<3))continue;
            OpeningTestSupport.complete(m,A,B,2);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);
            if(second){m.endTurn(A);m.endTurn(B);}return m;
        }throw new AssertionError("fixture opening");
    }
    static ServerMatch game(boolean second){return fixture(second,List.of(ability(EVOLVE+","+SUFFIX)),List.of(),ComposedTrainerIntegrationTest.item("{\"type\":\"draw\",\"amount\":1}"));}
    static void submit(ServerMatch m,UUID p){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId()));}
    public static void main(String[] args){schema();firstTurns();evolvesAndContinues(false);evolvesAndContinues(true);noEffect();newAndDevolved();stale();reactions();System.out.println("HandSelfEvolutionEffectTest passed");}
    static void schema(){
        var good=ability(EVOLVE+","+SUFFIX);check(ActivatedAbilityRules.unsupportedReason(good).isEmpty(),"valid Ability rejected");
        for(String bad:List.of(EVOLVE.replace("\"minimum\":1","\"minimum\":0"),EVOLVE.replace("\"amount\":1","\"amount\":2"),EVOLVE.replace("SELF","OWN_ACTIVE"),EVOLVE.replace("POKEMON","TRAINER"),EVOLVE.replace("AFTER_OWNER_FIRST_TURN","ANY"),EVOLVE.replace("\"bind\"","\"skip_stage\":1,\"bind\""),EVOLVE.replace(",\"bind\":\"evolved\"","")))reject(()->EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject()));
        check(TrainerProgramRules.unsupportedReason(ComposedTrainerIntegrationTest.item(EVOLVE).effectProgram()).isPresent(),"trainer gained self evolution");
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","",EVOLVE)));
        var m=game(true);var effect=EffectRegistry.standard().parse(JsonParser.parseString(EVOLVE).getAsJsonObject());reject(()->m.openEffectZoneSelection(A,effect,0));
    }
    static void firstTurns(){
        var m=game(false);
        for(UUID p:List.of(A,B)){
            var source=m.activePokemonView(p).pokemonId();var before=m.privateView(p);var rng=m.randomTranscript();
            check(!m.canUseAbility(p,source,"ascend"),"own first turn allowed");reject(()->m.useAbility(p,source,"ascend"));
            check(before.equals(m.privateView(p))&&rng.equals(m.randomTranscript())&&!m.hasPendingEffectChoice(),"first turn denial mutated state");
            check(m.abilityOptions(p).stream().noneMatch(o->o.pokemonId().equals(source)&&o.usedThisTurn()),"first turn reserved use");m.endTurn(p);
        }
        check(m.canUseAbility(A,m.activePokemonView(A).pokemonId(),"ascend"),"first turn denial poisoned later use");
    }
    static void evolvesAndContinues(boolean bench){
        var m=game(true);UUID source=bench?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A,A,source,6);var energy=hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,source);
        if(!bench)m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        var rng=m.randomTranscript();int deck=m.privateView(A).deck().size();int start=m.replayTranscript().size();m.useAbility(A,source,"ascend");
        var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&q.candidates().stream().allMatch(c->c.definitionId().equals("evolved")),"hand candidate/direct evolution contract");
        reject(()->m.effectCardPrompt(B));var before=m.privateView(A);
        reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId())));
        reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
        reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(hand(m,A,"wrong").instanceId())));
        check(before.equals(m.privateView(A))&&m.abilityOptions(A).stream().anyMatch(o->o.pokemonId().equals(source)&&o.usedThisTurn()),"invalid response changed prompt/use contract");
        submit(m,A);var evolved=field(m).pokemonView(A,source);
        check(evolved.topCardId().equals("evolved")&&evolved.hitPoints()==130&&evolved.damage()==80,"evolution/counter continuation failed after old Ability disappeared");
        check(evolved.attachedEnergyIds().contains(energy.instanceId())&&evolved.conditions().isEmpty(),"evolution lost attachment or condition clearing");
        check(rng.equals(m.randomTranscript())&&deck==m.privateView(A).deck().size()&&!m.hasPendingEffectChoice(),"hand evolution shuffled or left prompt");
        var ops=m.replayTranscript().subList(start,m.replayTranscript().size()).stream().map(MatchReplayEvent::operation).toList();
        int evolve=ops.indexOf("已审核效果使宝可梦进化"),counters=ops.indexOf("已审核效果放置伤害指示物");check(evolve>=0&&counters>evolve,"evolution must precede counter placement");
        UUID other=bench?m.activePokemonView(A).pokemonId():m.benchPokemonView(A).getFirst().pokemonId();check(m.canUseAbility(A,other,"ascend"),"usage shared between Pokemon");
    }
    static void noEffect(){
        for(String mode:List.of("HAND","GENERAL","EMPTY","DISABLED")){
            var m=game(true);var source=m.activePokemonView(A).pokemonId();
            if(mode.equals("HAND"))field(m).preventHandEvolutionUntilOwnerNextTurn(B,A,source);
            if(mode.equals("GENERAL"))m.preventEvolutionByEffect(A,A,source);
            if(mode.equals("DISABLED"))m.disableAbilityEffectsByEffect(A,A,source);
            if(mode.equals("EMPTY"))m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->c.definitionId().equals("evolved")).map(CardInstance::instanceId).toList());
            var before=m.privateView(A);check(!m.canUseAbility(A,source,"ascend"),"guarded counters authorized no-effect "+mode);reject(()->m.useAbility(A,source,"ascend"));
            check(before.equals(m.privateView(A))&&m.activePokemonView(A).damage()==0&&!m.hasPendingEffectChoice(),"failed activation moved card/counters "+mode);
        }
    }
    static void newAndDevolved(){
        var m=game(true);var card=hand(m,A,"basic");m.playBasicPokemonToBench(A,card.instanceId());
        check(!m.canEvolve(A,card.instanceId(),"evolved")&&m.canUseAbility(A,card.instanceId(),"ascend"),"new-entry timing exception");m.useAbility(A,card.instanceId(),"ascend");submit(m,A);
        UUID source=m.activePokemonView(A).pokemonId();m.evolveByEffect(A,source,hand(m,A,"evolved").instanceId(),HandEvolutionEffect.permission());m.devolvePokemonToHandByEffect(A,A,source);
        check(m.canUseAbility(A,source,"ascend"),"fresh devolved source cannot use ability");m.useAbility(A,source,"ascend");submit(m,A);
    }
    static void stale(){
        for(boolean top:List.of(false,true)){
            var m=game(true);var source=m.activePokemonView(A).pokemonId();m.useAbility(A,source,"ascend");var q=m.effectCardPrompt(A);var pick=q.candidates().getFirst();
            if(top)field(m).evolve(A,source,hand(m,A,"evolved"),HandEvolutionEffect.permission());
            else field(m).preventHandEvolutionUntilOwnerNextTurn(B,A,source);
            var before=m.privateView(A);reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(pick.instanceId())));check(before.equals(m.privateView(A)),"stale evolution partially committed");
        }
    }
    static void reactions(){
        var trigger=DeckEvolutionEffectTest.triggered("observe","{\"trigger\":\"ON_EVOLVE\",\"watch\":\"SELF\",\"optional\":false,\"usage\":\"NONE\"}","ON_EVOLVE","{\"type\":\"draw\",\"amount\":1},{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10}");
        var m=fixture(true,List.of(ability(EVOLVE+","+SUFFIX)),List.of(trigger),ComposedTrainerIntegrationTest.item("{\"type\":\"draw\",\"amount\":1}"));int hand=m.privateView(A).hand().size();
        m.useAbility(A,m.activePokemonView(A).pokemonId(),"ascend");submit(m,A);DeckEvolutionEffectTest.resolveTriggers(m);
        check(m.privateView(A).hand().size()==hand&&m.activePokemonView(A).damage()==10,"evolution reaction did not observe completed counter suffix");
    }
}
