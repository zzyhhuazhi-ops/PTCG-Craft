import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual bundled ability programs, exercised through the normal server/session choices. */
public final class PsychicEmbraceTest {
    static final UUID A=new UUID(0,17101), B=new UUID(0,17102), WATCH=new UUID(0,17103);
    static final List<String> PRINTS=List.of("sv1_en_086","sv1_en_228","sv1_en_245","sv4-5_en_029","sv4-5_en_217","sv4-5_en_233");
    static final CombatCatalog BUNDLED=BundledCombatData.load();
    static AbilityDefinition ability(String id) {return BUNDLED.pokemon(id).orElseThrow().abilities().stream()
        .filter(a->a.id().equals("psychic-embrace")).findFirst().orElseThrow();}
    public static void main(String[] args) {
        bundledDecks();
        for(String id:PRINTS) for(int seed:List.of(2,7,19)) resolves(id,seed);
        noEnergyAndNoTargets(); liveHpBonus(); schema();
        System.out.println("PsychicEmbraceTest passed: 6 bundled printings, 18 server scenarios, CN deck validation, target/type/HP boundaries, unlimited uses, authority and stale submissions");
    }
    static void bundledDecks() {
        var db=BundledRules.loadCardDefinitions();var rules=BundledRules.loadCurrentRulesets().get(CurrentFormats.CHINESE);
        for(String id:PRINTS) {
            ActivatedAbilityRules.validate(ability(id));
            check(ability(id).kind()==AbilityDefinition.Kind.ACTIVATED_UNLIMITED,"unlimited ability lost");
            var deck=new ArrayList<CardInstance>();
            for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(1,i+1),A,i<4?"sv1_en_084":i<8?"sv1_en_085":i<10?id:"sve_en_005"));
            var report=new DeckValidator(db,BUNDLED).validate(rules,deck);
            check(report.issues().stream().noneMatch(i->i.severity()==DeckIssue.Severity.ERROR),id+": "+report.issues());
        }
    }
    static ServerMatch fixture(String id,int start) {
        var defs=new ArrayList<CardDefinition>();
        for(String name:List.of("psychic","water","energy","fire","special","tool"))defs.add(new CardDefinition(name,"夹具"+name,
            Set.of("psychic","water").contains(name)?CardCategory.POKEMON:name.equals("tool")?CardCategory.TRAINER:CardCategory.ENERGY,
            name.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),Set.of("psychic","water").contains(name),
            Set.of("energy","fire").contains(name),60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),
            name.equals("psychic")?Set.of(EnergyType.PSYCHIC):name.equals("water")?Set.of(EnergyType.WATER):Set.of(),Set.of()));
        var profiles=Map.of("psychic",new PokemonProfile(70,0,null,null,0,null,0,1,List.of(),List.of(ability(id)),EnergyType.PSYCHIC),
            "water",new PokemonProfile(70,0,null,null,0,null,0,1,List.of(),List.of(),EnergyType.WATER));
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.PSYCHIC,"fire",EnergyType.FIRE),
            Map.of("tool",DynamicHitPointsTest.tool(100)),Map.of("special",EnergyProfile.fixed(EnergyType.PSYCHIC,1)));
        for(int seed=start;seed<start+100;seed++) {
            var m=new ServerMatch(A,B,deck(A),deck(B),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("psychic"))))continue;
            for(UUID p:List.of(A,B)) {m.chooseOpeningActive(p,hand(m,p,"psychic").instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);
            if(m.currentPlayer().equals(B))m.endTurn(B);
            m.drawCardsByEffect(A,A,35);
            m.playBasicPokemonToBench(A,hand(m,A,"psychic").instanceId());
            m.playBasicPokemonToBench(A,hand(m,A,"water").instanceId());
            return m;
        }
        throw new AssertionError("fixture could not open");
    }
    static List<CardInstance> deck(UUID owner) {
        var result=new ArrayList<CardInstance>();for(int i=0;i<60;i++)result.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,
            i<24?"psychic":i<30?"water":i<50?"energy":i<54?"fire":i<57?"special":"tool"));return result;
    }
    static void discardEnergy(ServerMatch m) {m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream()
        .filter(c->Set.of("energy","fire","special").contains(c.definitionId())).map(CardInstance::instanceId).toList());}
    static void resolves(String id,int seed) {
        var m=fixture(id,seed);discardEnergy(m);
        UUID source=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId(),water=m.benchPokemonView(A).getLast().pokemonId();
        m.placeDamageCountersByEffect(A,A,source,5); // Exactly 20 HP left: never eligible.
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
        var rng=m.randomTranscript();
        for(int n=0;n<3;n++) {
            check(m.canUseAbility(A,source,"psychic-embrace"),"source ability should be repeatable: "+id+" seed="+seed+" use="+n);
            var view=session.view(A,A);
            var action=view.options().stream().filter(o->o.labelZh().contains("精神拥抱")&&o.cardKeys().contains("field:"+source)).findFirst().orElseThrow();
            session.submit(A,A,view.revision(),action.index());
            rejects(()->session.submit(A,A,view.revision(),action.index()));
            var target=m.effectPokemonPrompt(A);var before=m.privateView(A);
            check(target.targets().size()==1&&target.targets().getFirst().pokemonId().equals(bench),"wrong type/20 HP/opponent target included");
            rejects(()->m.chooseEffectPokemon(B,target.resolutionId(),target.step(),bench));
            rejects(()->m.chooseEffectPokemon(A,target.resolutionId(),target.step(),source));
            rejects(()->m.chooseEffectPokemon(A,target.resolutionId(),target.step(),water));
            rejects(()->m.chooseEffectPokemon(A,target.resolutionId(),target.step(),m.activePokemonView(B).pokemonId()));
            check(before.equals(m.privateView(A)),"invalid target moved cards");
            m.chooseEffectPokemon(A,target.resolutionId(),target.step(),bench);
            var selection=m.effectCardPrompt(A);
            check(selection.minimum()==1&&selection.maximum()==1,"wrong energy count");
            check(selection.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"non-basic/non-Psychic source included");
            check(session.view(B,B).game().effectSelection()==null&&session.view(WATCH,null).game().effectSelection()==null,"private selection leaked");
            var energy=selection.candidates().getFirst();
            rejects(()->m.submitEffectCards(A,selection.resolutionId(),selection.step(),List.of()));
            rejects(()->m.submitEffectCards(A,selection.resolutionId(),selection.step(),List.of(energy.instanceId(),energy.instanceId())));
            rejects(()->m.submitEffectCards(B,selection.resolutionId(),selection.step(),List.of(energy.instanceId())));
            m.submitEffectCards(A,selection.resolutionId(),selection.step(),List.of(energy.instanceId()));
            rejects(()->m.submitEffectCards(A,selection.resolutionId(),selection.step(),List.of(energy.instanceId())));
            var result=m.benchPokemonView(A).getFirst();
            check(result.damage()==20*(n+1)&&result.attachedEnergyIds().size()==n+1,"attachment/counter count incorrect");
            check(!m.privateView(A).discard().contains(energy)&&!m.hasPendingEffectChoice(),"duplicated energy or stuck continuation");
            check(m.phase()==MatchPhase.TURN&&!m.energyAttached(A)&&rng.equals(m.randomTranscript()),"KO/turn/manual quota/RNG changed");
        }
        check(!m.canUseAbility(A,source,"psychic-embrace"),"10 HP target could be knocked out");
        rejects(()->m.useAbility(A,source,"psychic-embrace"));
        m.endTurn(A);check(m.currentPlayer().equals(B),"server did not resume turn actions");
    }
    static void noEnergyAndNoTargets() {
        var m=fixture(PRINTS.getFirst(),3);UUID source=m.activePokemonView(A).pokemonId();
        var before=m.privateView(A);check(!m.canUseAbility(A,source,"psychic-embrace"),"empty discard activated");
        rejects(()->m.useAbility(A,source,"psychic-embrace"));check(before.equals(m.privateView(A)),"failed activation changed cards");
        discardEnergy(m);m.placeDamageCountersByEffect(A,A,source,5);
        m.placeDamageCountersByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),5);
        check(!m.canUseAbility(A,source,"psychic-embrace"),"all Psychic hosts at20 HP still eligible");
    }
    static void liveHpBonus() {
        var m=fixture(PRINTS.getFirst(),4);discardEnergy(m);UUID source=m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A,A,source,5);
        m.playTrainer(A,hand(m,A,"tool").instanceId(),source);
        m.useAbility(A,source,"psychic-embrace");var target=m.effectPokemonPrompt(A);
        check(target.targets().stream().anyMatch(t->t.pokemonId().equals(source)),"live tool HP ignored");
        m.chooseEffectPokemon(A,target.resolutionId(),target.step(),source);
        var cards=m.effectCardPrompt(A);m.submitEffectCards(A,cards.resolutionId(),cards.step(),List.of(cards.candidates().getFirst().instanceId()));
        check(m.activePokemonView(A).hitPoints()==170&&m.activePokemonView(A).damage()==70,"HP bonus/own active attachment incorrect");
        check(!m.hasPendingEffectChoice(),"active-host flow stuck");
    }
    static void schema() {
        var e=ability(PRINTS.getFirst()).effectProgram().triggers().getFirst().effects().getFirst();
        for(String invalid:List.of("-10","0","25","1001","bad")) {
            var args=new HashMap<>(e.arguments());args.put("remaining_hp_min",invalid);
            rejects(()->PokemonSelectionScope.validate(new EffectDefinition(e.type(),e.target(),args,e.conditions(),e.effects())));
        }
        var args=new HashMap<>(e.arguments());args.put("remaining_hp_max","20");
        rejects(()->PokemonSelectionScope.validate(new EffectDefinition(e.type(),e.target(),args,e.conditions(),e.effects())));
    }
    static CardInstance hand(ServerMatch m,UUID owner,String id){return m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("invalid action accepted");}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
