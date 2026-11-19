import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Item classification outside play, printed fossil rules, and evolution of the same physical card. */
public final class FossilFormTest {
    static final UUID A=new UUID(0,7501),B=new UUID(0,7502);
    static final String NAME="Antique Skull Fossil";
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Invalid action accepted");}
    static CardInstance card(UUID owner,String id){return new CardInstance(UUID.randomUUID(),owner,id);}
    static JsonObject json(){return JsonParser.parseString("""
        {"id":"fossil","kind":"TRAINER","trainerCategory":"ITEM","effect":{"opcode":"PLAY_AS_POKEMON","amount":0},
         "inPlayPokemon":{"hitPoints":60,"retreatCost":0,"prizeCards":1,"pokemonType":"COLORLESS",
           "pokemonName":"Antique Skull Fossil","printedRules":"FOSSIL_ITEM","attacks":[]}}
        """).getAsJsonObject();}
    static TrainerProfile fossil(){return BundledCombatData.trainer(json(),EffectRegistry.standard());}
    static PokemonProfile named(int hp,String parent,String name,String parentName){
        return new PokemonProfile(hp,0,parent,null,0,null,0,1,List.of(),List.of(),EnergyType.FIGHTING,name,parentName);
    }
    static CombatCatalog catalog(){return new CombatCatalog(Map.of("base",named(200,null,"Base",null),
        "cranidos",named(100,"fossil","Cranidos",NAME),"rampardos",named(180,"cranidos","Rampardos","Cranidos")),
        Map.of("energy",EnergyType.FIGHTING),Map.of("fossil",fossil(),"fossil_other_print",fossil()));}
    static CardDefinition definition(String id,CardCategory category,boolean basic){return new CardDefinition(id,id,category,
        category==CardCategory.TRAINER?TrainerCategory.ITEM:TrainerCategory.NONE,"J",Set.of("international"),basic,
        category==CardCategory.ENERGY,60,Map.of(),EffectSupport.SUPPORTED,null);}
    public static void main(String[] args){
        model();fieldRules();matchEntry();discardRule();
        System.out.println("FossilFormTest passed: Item-only hidden zones, strict form loader, field Basic Colorless identity, immutable printed rules, free-retreat rejection, switching, named evolution/devolution and real Trainer placement.");
    }
    static void model(){
        var data=catalog();var item=definition("fossil",CardCategory.TRAINER,false);
        check(data.pokemon("fossil").isEmpty()&&!data.pokemonProfiles().containsKey("fossil"),"Fossil leaked into hidden-zone Pokemon profiles");
        check(data.trainer("fossil").isPresent()&&data.fieldPokemon("fossil").orElseThrow().hitPoints()==60,"Item/form missing");
        var field=data.fieldDefinition(item);
        check(field.category()==CardCategory.POKEMON&&field.basicPokemon()&&field.pokemonTypes().equals(Set.of(EnergyType.COLORLESS)),"Field classification incorrect");
        check(item.category()==CardCategory.TRAINER&&!item.basicPokemon()&&item.pokemonTypes().isEmpty(),"Original Item definition mutated");
        for(var opcode:List.of(TrainerEffect.Opcode.SEARCH_POKEMON_TO_HAND,TrainerEffect.Opcode.SEARCH_BASIC_POKEMON_TO_HAND))
            check(!new TrainerEffect(opcode,1,0).allowsDeckSearchCandidate(item),"Pokemon search selected an Item");
        reject(()->new CombatCatalog(Map.of("fossil",fossil().inPlayPokemon()),Map.of()));
        reject(()->new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(TrainerEffect.Opcode.PLAY_AS_POKEMON,0,0)));
        reject(()->new TrainerEffect(TrainerEffect.Opcode.PLAY_AS_POKEMON,1,0));
        for(String key:List.of("trainerCategory","effect","printedRules","pokemonType","attacks")) {
            var bad=json();
            switch(key){
                case "trainerCategory" -> bad.addProperty(key,"SUPPORTER");
                case "effect" -> bad.getAsJsonObject(key).addProperty("opcode","DRAW");
                case "printedRules" -> bad.getAsJsonObject("inPlayPokemon").addProperty(key,"STANDARD");
                case "pokemonType" -> bad.getAsJsonObject("inPlayPokemon").addProperty(key,"FIGHTING");
                case "attacks" -> bad.getAsJsonObject("inPlayPokemon").addProperty("evolvesFromDefinitionId","base");
            }
            reject(()->BundledCombatData.trainer(bad,EffectRegistry.standard()));
        }
    }
    static void fieldRules(){
        var f=new BattlefieldState(A,B,catalog().fieldPokemonProfiles(),new ServerRandom(75),(o,p)->99,1,1,5,(o,p)->0,(o,p)->true);
        var skull=card(A,"fossil_other_print");var base=card(A,"base");
        f.placeInitialActive(A,skull);f.placeOnBench(A,base);f.placeInitialActive(B,card(B,"base"));f.beginTurn(A);
        var energy=card(A,"energy");f.attachEnergy(A,skull.instanceId(),energy);
        for(var condition:SpecialCondition.values())check(!f.applySpecialCondition(A,condition),"Printed immunity failed: "+condition);
        check(f.activeView(A).conditions().isEmpty(),"Condition appeared on fossil");
        f.disableAbilityEffectsUntilOwnerNextTurn(B,A,skull.instanceId());
        check(!f.applySpecialCondition(A,SpecialCondition.POISONED,4),"Ability suppression removed printed immunity");
        check(f.activeRetreatCost(A)==0,"Free retreat fixture not active");
        var before=f.activeView(A);reject(()->f.retreat(A,base.instanceId(),List.of()));
        check(f.activeView(A).equals(before)&&!f.retreated(),"Rejected retreat consumed state");
        f.switchActiveByEffect(A,base.instanceId());f.switchActiveByEffect(A,skull.instanceId());
        check(!f.canEvolve(A,skull.instanceId(),"cranidos"),"First-turn evolution allowed");
        f.beginTurn(B);f.beginTurn(A);
        check(f.canEvolve(A,skull.instanceId(),"cranidos"),"Named evolution rejected another fossil printing");
        check(f.canEvolveSkippingOneStage(A,skull.instanceId(),"rampardos",EvolutionPermission.CARD_EFFECT_SKIP_ONE_STAGE_STRICT_TIMING),"Fossil ancestry did not support strict Rare Candy depth");
        f.evolve(A,skull.instanceId(),card(A,"cranidos"));
        check(f.pokemonProfile(A,skull.instanceId()).printedRules()==PokemonPrintedRules.STANDARD,"Evolution retained fossil-only rules");
        check(f.applySpecialCondition(A,SpecialCondition.POISONED),"Evolution retained immunity");
        f.retreat(A,base.instanceId(),List.of());f.switchActiveByEffect(A,skull.instanceId());
        f.devolveByEffect(A,skull.instanceId());
        check(f.activeView(A).topCardId().equals("fossil_other_print")&&!f.applySpecialCondition(A,SpecialCondition.BURNED),"Devolution failed to restore printed rules");
        check(f.activeView(A).attachedEnergyIds().equals(List.of(energy.instanceId())),"Evolution or rejected retreat lost attached energy");
    }
    static ServerMatch game(){
        var defs=new CardDatabase(List.of(definition("base",CardCategory.POKEMON,true),definition("fossil",CardCategory.TRAINER,false),
            definition("cranidos",CardCategory.POKEMON,false),definition("rampardos",CardCategory.POKEMON,false),definition("energy",CardCategory.ENERGY,false)));
        for(int seed=0;seed<100;seed++){
            var own=new ArrayList<CardInstance>();var opponent=new ArrayList<CardInstance>();
            for(int i=0;i<60;i++){own.add(card(A,i<30?"base":i<34?"fossil":i<38?"cranidos":"energy"));opponent.add(card(B,"base"));}
            var m=new ServerMatch(A,B,own,opponent,BundledRules.loadRulesets().get("standard-international-2026"),defs,catalog(),new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("base")))continue;
            for(var c:m.privateView(A).hand())if(c.definitionId().equals("fossil")){
                var view=m.privateView(A);reject(()->m.chooseOpeningActive(A,c.instanceId()));
                check(view.equals(m.privateView(A)),"Rejected fossil opening mutated the hand");
            }
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,25);
            if(m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals("fossil")))return m;
        }
        throw new AssertionError("No fossil fixture");
    }
    static CardInstance hand(ServerMatch m,String id){return m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static void matchEntry(){
        var m=game();var item=hand(m,"fossil");var before=m.clientView(A);
        reject(()->m.playBasicPokemonToBench(A,item.instanceId()));reject(()->m.playTrainer(B,item.instanceId(),null));
        check(m.clientView(A).equals(before),"Rejected field action mutated state");
        check(m.canPlayTrainer(A,item.instanceId(),null),"Fossil Item could not be played");
        m.playTrainer(A,item.instanceId(),null);
        var fossil=m.benchPokemonView(A).stream().filter(p->p.topCardId().equals("fossil")).findFirst().orElseThrow();
        check(fossil.hitPoints()==60&&m.privateView(A).hand().stream().noneMatch(c->c.instanceId().equals(item.instanceId()))
            &&m.privateView(A).discard().stream().noneMatch(c->c.instanceId().equals(item.instanceId())),"Played fossil duplicated/discarded");
        check(!m.cardDefinition("fossil").basicPokemon()&&m.cardDefinition("fossil").category()==CardCategory.TRAINER,"Server changed global classification");
        m.attachEnergy(A,hand(m,"energy").instanceId(),fossil.pokemonId());
        m.endTurn(A);m.endTurn(B);m.evolve(A,fossil.pokemonId(),hand(m,"cranidos").instanceId());
        check(m.benchPokemonView(A).stream().anyMatch(p->p.pokemonId().equals(fossil.pokemonId())&&p.topCardId().equals("cranidos")),"Real Trainer entry could not evolve");
        check(!m.canDiscardFossil(A,fossil.pokemonId()),"Evolved Pokemon retained fossil discard action");
    }
    static void discardRule(){
        for(boolean active:List.of(false,true)){
            var m=game();var item=hand(m,"fossil");m.playTrainer(A,item.instanceId(),null);
            var fossil=m.benchPokemonView(A).stream().filter(p->p.topCardId().equals("fossil")).findFirst().orElseThrow();
            var energy=hand(m,"energy");m.attachEnergy(A,energy.instanceId(),fossil.pokemonId());
            m.disableAbilityEffectsByEffect(A,A,fossil.pokemonId());
            if(active){m.switchActiveByEffect(A,A,fossil.pokemonId());check(m.suggestedRetreatPayment(A).isEmpty(),"Fossil advertised free retreat");}
            int prizes=m.privateView(B).prizes().size();var view=m.clientView(A);
            reject(()->m.discardFossil(B,fossil.pokemonId()));check(view.equals(m.clientView(A)),"Wrong-owner discard changed state");
            check(m.canDiscardFossil(A,fossil.pokemonId()),"Suppressed Ability removed printed discard permission");
            check(MatchSession.multiplayer(m,A,B).view(A,A).options().stream().anyMatch(o->o.labelZh().startsWith("自行弃置化石：")),"No fossil discard action in match UI");
            m.discardFossil(A,fossil.pokemonId());
            check(m.privateView(A).discard().containsAll(List.of(item,energy)),"Fossil discard did not move its attachments");
            check(m.privateView(B).prizes().size()==prizes,"Voluntary discard granted a Prize");
            reject(()->m.discardFossil(A,fossil.pokemonId()));
            check(active?m.phase()==MatchPhase.CHOOSE_REPLACEMENT:m.phase()==MatchPhase.TURN,"Empty Active was not resolved as a non-KO departure");
        }
    }
}
