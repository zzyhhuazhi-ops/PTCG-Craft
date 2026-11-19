import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual ME5 fossils, their separate printed Abilities, named evolutions and Fossil Quarry. */
public final class GeneratedRuleBatch75Test {
    static final UUID A=FossilFormTest.A,B=FossilFormTest.B;
    static final String ARMOR="me5_en_072",SKULL="me5_en_073",QUARRY="me5_en_076",CRANIDOS="me5_en_044",SHIELDON="me5_en_061";
    static final List<String> IDS=List.of(ARMOR,SKULL,QUARRY,CRANIDOS,SHIELDON);
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){FossilFormTest.check(b,s);}
    static void reject(Runnable r){FossilFormTest.reject(r);}
    static CardInstance hand(ServerMatch m,UUID owner,String id){return m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static TrainerProfile trainer(String id){return DATA.trainer(id).orElseThrow();}
    static AbilityDefinition ability(String id){return trainer(id).inPlayPokemon().abilities().getFirst();}
    static ServerMatch game(AttackDefinition attack){return game(attack,EnergyType.FIGHTING);}
    static ServerMatch game(AttackDefinition attack,EnergyType energyType){
        var defs=new ArrayList<CardDefinition>();for(String id:IDS)defs.add(DB.find(id).orElseThrow());
        defs.add(FossilFormTest.definition("base",CardCategory.POKEMON,true));defs.add(FossilFormTest.definition("energy",CardCategory.ENERGY,false));
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY"));
        var base=new PokemonProfile(300,0,null,EnergyType.GRASS,2,EnergyType.GRASS,30,1,List.of(attack),List.of(guard),EnergyType.GRASS,"Base",null);
        var combat=new CombatCatalog(Map.of("base",base,CRANIDOS,DATA.pokemon(CRANIDOS).orElseThrow(),SHIELDON,DATA.pokemon(SHIELDON).orElseThrow()),
            Map.of("energy",energyType),Map.of(ARMOR,trainer(ARMOR),SKULL,trainer(SKULL),QUARRY,trainer(QUARRY)));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){
                var cards=new ArrayList<CardInstance>();
                for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.equals(A)?7571:7572,i+1),owner,
                    i<24?"base":owner.equals(B)?"energy":i<54?IDS.get((i-24)/6):"energy"));
                decks.add(cards);
            }
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);
            if(IDS.stream().anyMatch(id->m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            if(m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()<2)continue;
            if(List.of(ARMOR,SKULL).stream().anyMatch(id->m.privateView(A).deck().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            return m;
        }
        throw new AssertionError("No actual fossil fixture");
    }
    static UUID place(ServerMatch m,String id,boolean active){
        var card=hand(m,A,id);m.playTrainer(A,card.instanceId(),null);if(active)m.switchActiveByEffect(A,A,card.instanceId());return card.instanceId();
    }
    static AttackDefinition hit(int damage,boolean bench){return DynamicPassiveModifierTest.attack(damage,false,bench);}
    public static void main(String[] args){printed();armor();skull();evolution();quarry();lastPokemonDiscard();
        System.out.println("GeneratedRuleBatch75Test passed: all five real ME5 cards, printed metadata and costs, separate fossil Abilities including lethal reaction/protection/suppression, damage layers, named evolution, Quarry private placement and last-Pokemon voluntary discard.");}
    static void printed(){
        for(String id:IDS)check(DB.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Missing actual supported card "+id);
        for(String id:List.of(ARMOR,SKULL)){
            var card=DB.find(id).orElseThrow();var p=trainer(id).inPlayPokemon();
            check(card.category()==CardCategory.TRAINER&&card.trainerCategory()==TrainerCategory.ITEM&&!card.basicPokemon()&&card.copyLimit()==4&&card.regulationMark().equals("J"),"Fossil global classification/metadata changed");
            check(DATA.pokemon(id).isEmpty()&&p.hitPoints()==60&&p.pokemonType()==EnergyType.COLORLESS&&p.printedRules()==PokemonPrintedRules.FOSSIL_ITEM&&p.attacks().isEmpty()&&p.abilities().size()==1,"Printed fossil form/Ability omitted");
        }
        for(String id:List.of(CRANIDOS,SHIELDON)){
            var p=DATA.pokemon(id).orElseThrow();boolean skull=id.equals(CRANIDOS);
            check(p.hitPoints()==100&&p.prizeCards()==1&&p.retreatCost()==(skull?2:3)&&p.evolvesFromDefinitionId().equals(skull?SKULL:ARMOR)
                &&p.evolvesFromName().equals(trainer(skull?SKULL:ARMOR).inPlayPokemon().pokemonName()),"Real evolution metadata changed");
            var a=p.attacks().getFirst();check(a.baseDamage()==(skull?70:50)&&a.energyCost().equals(skull?Map.of(EnergyType.FIGHTING,2):Map.of(EnergyType.METAL,1,EnergyType.COLORLESS,1)),"Real printed attack/cost changed");
        }
        check(trainer(QUARRY).activation()!=null&&trainer(QUARRY).ongoingProgram().isEmpty(),"Quarry activation omitted or treated as passive");
    }
    static void armor(){
        for(boolean active:List.of(false,true))for(boolean bench:List.of(false,true))for(boolean disabled:List.of(false,true)){
            var m=game(hit(40,bench));UUID fossil=place(m,ARMOR,active);if(disabled)m.disableAbilityEffectsByEffect(A,A,fossil);
            m.endTurn(A);m.attack(B,"hit");int expected=active&&!disabled?30:(!active&&!bench?50:40);
            var target=bench?m.benchPokemonView(A).getFirst():m.activePokemonView(A);
            check(target.damage()==expected,"Protective Armor source position, target scope or suppression failed");
        }
        var m=game(hit(20,false));UUID fossil=place(m,ARMOR,true);m.placeDamageCountersByEffect(A,A,fossil,2);
        check(m.activePokemonView(A).damage()==20,"Protective Armor reduced effect counters");
        var a=ability(ARMOR);
        var layered=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.fixed(EnergyType.COLORLESS,2),hit(40,false),List.of(a),30);
        layered.attack(DynamicPassiveModifierTest.A,"hit");check(layered.activePokemonView(DynamicPassiveModifierTest.B).damage()==40,"Actual Armor program must reduce after Weakness AND Resistance");
        var own=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.fixed(EnergyType.COLORLESS,2),DamageTriggeredAbilityTest.attack(0,",\"selfDamage\":30"),List.of(a));
        own.attack(DynamicPassiveModifierTest.A,"hit");check(own.activePokemonView(DynamicPassiveModifierTest.A).damage()==30,"Armor reduced owner's own attack damage");
    }
    static void skull(){
        for(int damage:List.of(20,60))for(boolean disabled:List.of(false,true))for(boolean protectedAttacker:List.of(false,true)){
            var m=game(hit(damage,false));UUID fossil=place(m,SKULL,true);if(disabled)m.disableAbilityEffectsByEffect(A,A,fossil);
            m.endTurn(A);if(protectedAttacker)m.useAbility(B,active(m,B),"guard");m.attack(B,"hit");DamageTriggeredAbilityTest.finishTriggers(m);
            check(m.activePokemonView(B).damage()==(disabled||protectedAttacker?0:30),"Actual Spiny Skull lost attacker identity, lethal trigger, suppression or Ability protection");
            check((m.phase()==MatchPhase.PRIZE_SELECTION)==(damage==60),"Skull KO timing changed");
        }
        var bench=game(hit(20,true));place(bench,SKULL,false);bench.endTurn(A);bench.attack(B,"hit");check(bench.activePokemonView(B).damage()==0,"Benched Skull reflected");
        var counters=game(DamageTriggeredAbilityTest.attack(0,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}]}]"));
        place(counters,SKULL,true);counters.endTurn(A);counters.attack(B,"hit");check(counters.activePokemonView(A).damage()==20&&counters.activePokemonView(B).damage()==0,"Counters incorrectly triggered Skull");
        // The attack switches its user before the reaction; counters still belong on the captured attacker.
        var switched=game(DamageTriggeredAbilityTest.attack(20,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}]}]"));
        place(switched,SKULL,true);switched.endTurn(A);UUID attacker=active(switched,B);switched.attack(B,"hit");
        var p=switched.effectPokemonPrompt(B);switched.chooseEffectPokemon(B,p.resolutionId(),p.step(),p.targets().getFirst().pokemonId());DamageTriggeredAbilityTest.finishTriggers(switched);
        check(switched.activePokemonView(B).damage()==0&&switched.benchPokemonView(B).stream().anyMatch(v->v.pokemonId().equals(attacker)&&v.damage()==30),"Skull hit replacement Active instead of captured attacker");
    }
    static void evolution(){
        for(String id:List.of(CRANIDOS,SHIELDON)){
            boolean skull=id.equals(CRANIDOS);var m=game(hit(20,false),skull?EnergyType.FIGHTING:EnergyType.METAL);UUID source=place(m,skull?SKULL:ARMOR,true);
            reject(()->m.evolve(A,source,hand(m,A,id).instanceId()));m.endTurn(A);m.endTurn(B);m.evolve(A,source,hand(m,A,id).instanceId());
            check(m.activePokemonView(A).topCardId().equals(id)&&!m.canDiscardFossil(A,source),"Actual fossil evolution failed or retained printed discard");
            var attack=DATA.pokemon(id).orElseThrow().attacks().getFirst();reject(()->m.attack(A,attack.id()));
            // Actual typed Energy payment, never a free copy of the printed attack.
            for(int i=0;i<2;i++)m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,"energy").instanceId(),A,source);
            UUID defender=active(m,B);var opponentEnergy=hand(m,B,"energy");
            if(!skull)m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,opponentEnergy.instanceId(),B,defender);
            m.attack(A,attack.id());
            if(skull){
                var p=m.effectPokemonPrompt(B);m.chooseEffectPokemon(B,p.resolutionId(),p.step(),p.targets().getFirst().pokemonId());
                check(m.benchPokemonView(B).stream().anyMatch(v->v.pokemonId().equals(defender)&&v.damage()==70),"Printed Push Down damage/switch failed");
            } else {
                if(m.hasPendingCardSelection()){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(opponentEnergy.instanceId()));}
                check(m.activePokemonView(B).damage()==50&&m.privateView(B).discard().contains(opponentEnergy),"Printed Smithereen Smash damage/energy discard failed");
            }
        }
    }
    static void quarry(){
        var m=game(hit(20,false));var source=hand(m,A,QUARRY);m.playTrainer(A,source.instanceId(),null);
        m.activateStadium(A,source.instanceId(),m.stadiumEntrySequence());var p=m.effectCardPrompt(A);
        var selected=List.of(ARMOR,SKULL).stream().map(id->p.candidates().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow().instanceId()).toList();
        check(m.clientView(B).effectSelection()==null,"Actual Quarry leaked deck choices");m.submitEffectCards(A,p.resolutionId(),p.step(),selected);
        check(m.benchPokemonView(A).stream().filter(v->List.of(ARMOR,SKULL).contains(v.topCardId())&&v.hitPoints()==60).count()==2
            &&m.clientView(A).stadium().equals(source)&&!m.canActivateStadium(A),"Actual Quarry transfer/usage failed");
        m.discardFossil(A,selected.getFirst());check(m.privateView(A).discard().stream().anyMatch(c->c.instanceId().equals(selected.getFirst())),"Quarry lost physical Item identity");
        UUID skull=m.benchPokemonView(A).stream().filter(v->v.topCardId().equals(SKULL)).findFirst().orElseThrow().pokemonId();
        m.endTurn(A);m.endTurn(B);m.evolve(A,skull,hand(m,A,CRANIDOS).instanceId());
        check(m.benchPokemonView(A).stream().anyMatch(v->v.pokemonId().equals(skull)&&v.topCardId().equals(CRANIDOS)),"Quarry-created fossil could not evolve");
    }
    static void lastPokemonDiscard(){
        var m=game(hit(20,false));UUID source=place(m,SKULL,true);
        for(var p:List.copyOf(m.benchPokemonView(A)))m.movePokemonByEffect(A,A,p.pokemonId(),LeavePlayDestination.HAND);
        int prizes=m.privateView(B).prizes().size();m.discardFossil(A,source);
        check(m.phase()==MatchPhase.FINISHED&&m.privateView(B).prizes().size()==prizes&&m.privateView(A).discard().stream().anyMatch(c->c.instanceId().equals(source)),"Discarding final actual fossil must lose with no KO prize");
    }
}
