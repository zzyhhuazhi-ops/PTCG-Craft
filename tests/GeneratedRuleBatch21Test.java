import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Public discard queries read exact printed identities, physical cards, owner and timing. */
public final class GeneratedRuleBatch21Test {
    static final UUID A=GeneratedRuleBatch19Test.A,B=GeneratedRuleBatch19Test.B;
    static final Ruleset RULES=GeneratedRuleBatch19Test.RULES;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch6Test.attack(id,slot);}
    static ServerMatch game(AttackDefinition attack){return fixture(attack,Map.of(),Map.of());}
    static List<CardInstance> discard(ServerMatch m,UUID owner,String id,int count){var cards=m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).limit(count).toList();check(cards.size()==count,"Fixture lacks discard cards "+id);m.discardHandCardsByEffect(A,owner,cards.stream().map(CardInstance::instanceId).toList());return cards;}
    public static void main(String[] args){namedAttacks();exactNames();typedCards();familyCards();ownersAndBasicEnergy();caps();countBeforeRecovery();schema();System.out.println("GeneratedRuleBatch21Test passed: exact discarded attacks/names/types/families, physical Basic Energy identity, owner, count caps and damage-before-recovery");}
    static void namedAttacks(){for(var id:List.of("swsh3_en_078","sv2_en_080","xy4_en_026")){var a=attack(id,id.equals("swsh3_en_078")?0:1);var m=game(a);discard(m,A,"basic",2);discard(m,A,"evolved",1);discard(m,A,"rulebox",1);discard(m,A,"tool",1);discard(m,B,"basic",2);m.attack(A,a.id());check(m.lastAttack().finalDamage()==60,"Named discard attack counted opponent/field/wrong attack "+id);}}
    static void exactNames(){var a=attack("sv1_en_061",0);var m=fixture(a,Map.of("basic","Tatsugiri","evolved","Team Rocket's Tatsugiri","rulebox","Tatsugiri ex"),Map.of());discard(m,A,"basic",2);discard(m,A,"evolved",1);discard(m,A,"rulebox",1);m.attack(A,a.id());check(m.lastAttack().finalDamage()==100,"Exact printed name confused prefix/ex with Tatsugiri");
        var union=attack("swsh10-5_en_021",1);var n=fixture(union,Map.of("basic","Magikarp","evolved","Gyarados","rulebox","Gyarados ex"),Map.of());discard(n,A,"basic",2);discard(n,A,"evolved",1);discard(n,A,"rulebox",1);n.attack(A,union.id());check(n.lastAttack().finalDamage()==100,"Name union lost base, omitted name or included ex");
    }
    static void typedCards(){var a=attack("sv1_en_104",0);for(boolean fallback:List.of(false,true)){var tags=Map.of("basic",fallback?Set.<EnergyType>of():Set.of(EnergyType.PSYCHIC,EnergyType.FIRE),"evolved",Set.of(EnergyType.PSYCHIC),"rulebox",Set.of(EnergyType.FIRE));var m=fixture(a,Map.of(),tags);discard(m,A,"basic",2);discard(m,A,"evolved",1);discard(m,A,"rulebox",1);discard(m,A,"special",1);m.attack(A,a.id());check(m.lastAttack().finalDamage()==30,"Discard type counted dual twice, Special Energy, wrong type or omitted legacy fallback");}}
    static void familyCards(){var a=attack("swsh6_en_087",0);var m=game(a);discard(m,A,"basic",2);discard(m,A,"evolved",1);discard(m,A,"rulebox",1);m.attack(A,a.id());check(m.lastAttack().finalDamage()==100,"Single Strike discard query used names, Rule Boxes or ignored printed label");}
    static void ownersAndBasicEnergy(){var a=attack("me2-5_en_033",0);var m=game(a);discard(m,A,"energy",2);discard(m,B,"energy",2);discard(m,B,"special",1);discard(m,B,"tool",1);m.attack(A,a.id());check(m.lastAttack().finalDamage()==60,"Opponent Basic Energy count read own pile or supplied Special units");
        var typed=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"typed\",\"nameZh\":\"类型计数\",\"baseDamage\":0,\"energyCost\":{},\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"damage_per_discard_card\",\"target\":\"SELF\",\"amount\":30,\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"energy_type\":\"GRASS\"}]}").getAsJsonObject(),EffectRegistry.standard());var n=game(typed);discard(n,A,"energy",2);discard(n,A,"wrong",1);discard(n,A,"special",1);n.attack(A,typed.id());check(n.lastAttack().finalDamage()==60,"Typed Basic Energy count inferred Special Energy provision");
    }
    static void caps(){var a=attack("sm9_en_091",1);for(int count:List.of(9,10,11)){var m=game(a);m.drawCardsByEffect(A,A,m.privateView(A).deck().size());var cards=m.privateView(A).hand().stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON).limit(count).map(CardInstance::instanceId).toList();check(cards.size()==count,"Cap fixture missing Pokemon");m.discardHandCardsByEffect(A,A,cards);m.attack(A,a.id());check(m.lastAttack().finalDamage()==20*Math.min(count,10),"Count cap applied to wrong damage portion");}}
    static void countBeforeRecovery(){for(boolean tools:List.of(false,true)){var a=attack(tools?"swsh4_en_111":"sm7-5_en_007",0);var m=game(a);var recovered=discard(m,A,tools?"tool":"energy",2);var retained=discard(m,A,tools?"trainer":"special",1);discard(m,B,tools?"tool":"energy",1);int before=m.privateView(A).deck().size();m.attack(A,a.id());check(m.lastAttack().finalDamage()==(tools?60:40)&&m.privateView(A).deck().size()==before+2,"Damage was counted after recovery or wrong cards recovered");check(m.privateView(A).discard().containsAll(retained)&&m.privateView(A).discard().stream().noneMatch(recovered::contains)&&m.privateView(B).discard().size()==1,"Recovery swept wrong kind or wrong player's discard");}}
    static void schema(){for(String extra:List.of("\"card_category\":\"TRAINER\",\"attack_id\":\"mad-party\"","\"card_category\":\"POKEMON\",\"pokemon_names\":\"\"","\"card_category\":\"POKEMON\",\"pokemon_names\":\"Magikarp|Magikarp\"","\"card_category\":\"POKEMON\",\"pokemon_names\":\" Magikarp\"","\"card_category\":\"POKEMON\",\"limit\":0","\"card_category\":\"POKEMON\",\"limit\":61","\"card_category\":\"ENERGY\",\"energy_type\":\"GRASS\"","\"card_category\":\"POKEMON\",\"pokemon_types\":\"NOPE\""))reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"damage_per_discard_card\",\"target\":\"SELF\",\"amount\":20,"+extra+"}").getAsJsonObject()));
        reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"recover_all_discard\",\"target\":\"DISCARD\",\"destination\":\"DECK_SHUFFLED\",\"card_category\":\"POKEMON\",\"attack_id\":\"mad-party\"}").getAsJsonObject()));
    }
    static ServerMatch fixture(AttackDefinition attack,Map<String,String> names,Map<String,Set<EnergyType>> tags){
        var abilities=List.<AbilityDefinition>of();var energy=EnergyType.GRASS;var type=EnergyType.PSYCHIC;var trainer=GeneratedRuleBatch8Test.trainer("xy2_en_093");
        String[] ids={"basic","basic","basic","basic","energy","energy","wrong","special","tool","tool","tool","tool","trainer","evolved","rulebox"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","rulebox").contains(id),isEnergy=Set.of("energy","wrong","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:isEnergy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("trainer")?trainer.category():id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),
                id.equals("basic")||id.equals("rulebox"),id.equals("energy")||id.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null,
                id.equals("rulebox")?Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX):id.equals("basic")?Set.of(PokemonFamily.SINGLE_STRIKE):Set.of(),pokemon?tags.getOrDefault(id,Set.of(id.equals("evolved")?EnergyType.PSYCHIC:type)):Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","rulebox"))pokemon.put(id,new PokemonProfile(500,1,id.equals("evolved")?"basic":null,null,0,null,0,
            id.equals("rulebox")?2:1,List.of(id.equals("rulebox")?GeneratedRuleBatch8Test.hit(100,false):attack),id.equals("rulebox")?List.of():abilities,id.equals("evolved")?EnergyType.PSYCHIC:type,names.get(id),null));
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

}
