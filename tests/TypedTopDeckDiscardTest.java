import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Typed Basic Energy counts use exactly the physical cards milled by this attack, including empty/short decks. */
public final class TypedTopDeckDiscardTest {
    static final UUID A=new UUID(0,83001),B=new UUID(0,83002),WATCH=new UUID(0,83003);
    static final String TYPED="""
        {"type":"discard_top_deck","target":"SELF","amount":6,"bind":"milled",
         "card_category":"ENERGY","basic_energy_only":true,"energy_type":"WATER"}
        """;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("invalid typed mill accepted");}
    static AttackDefinition attack(boolean typed) {
        String before=typed?TYPED:TYPED.replace(",\"basic_energy_only\":true,\"energy_type\":\"WATER\"","");
        return BundledCombatData.attack(JsonParser.parseString("""
            {"id":"mill","nameZh":"弃顶计伤夹具","energyCost":{},"baseDamage":0,"damageType":"WATER","effectSupport":"SUPPORTED",
             "triggers":[{"trigger":"BEFORE_ATTACK","effects":[%s]},
                         {"trigger":"ON_ATTACK","effects":[{"type":"damage_per_bound","target":"OPPONENT_ACTIVE","bind":"milled","amount":60}]}]}
            """.formatted(before)).getAsJsonObject(),EffectRegistry.standard());
    }
    static List<CardInstance> deck(UUID owner) {
        String[] ids={"basic","water","water-alt","fire","special","item"};var cards=new ArrayList<CardInstance>();
        for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));return cards;
    }
    static ServerMatch fixture(boolean typed,int seed) {
        var defs=new ArrayList<CardDefinition>();
        for(String id:List.of("basic","water","water-alt","fire","special","item")) {
            boolean pokemon=id.equals("basic"),energy=!pokemon&&!id.equals("item");
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("item")?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),pokemon,energy&&!id.equals("special"),60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        var item=BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject(),EffectRegistry.standard());
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(attack(typed)))),
            Map.of("water",EnergyType.WATER,"water-alt",EnergyType.WATER,"fire",EnergyType.FIRE),Map.of("item",item),Map.of("special",EnergyProfile.fixed(EnergyType.WATER,2)));
        var m=new ServerMatch(A,B,deck(A),deck(B),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
        OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);return m;
    }
    static ServerMatch fixture(){return fixture(true,83);}
    static boolean water(CardInstance card){return card.definitionId().equals("water")||card.definitionId().equals("water-alt");}
    static List<CardInstance> retain(ServerMatch m,List<CardInstance> wanted) {
        var keep=new HashSet<>(wanted);var remove=m.privateView(A).deck().stream().filter(c->!keep.contains(c)).map(CardInstance::instanceId).toList();
        if(!remove.isEmpty())m.searchOwnDeckToHandByEffect(A,remove);
        check(new HashSet<>(m.privateView(A).deck()).equals(keep),"fixture retained incorrect cards");return m.privateView(A).deck();
    }
    static void oldDiscard(ServerMatch m) {
        var old=m.privateView(A).hand().stream().filter(TypedTopDeckDiscardTest::water).findFirst().orElseThrow();
        m.discardHandCardsByEffect(A,A,List.of(old.instanceId()));
    }
    static List<CardInstance> fromDeck(ServerMatch m,String id,int n) {
        var cards=m.privateView(A).deck().stream().filter(c->c.definitionId().equals(id)).limit(n).toList();
        check(cards.size()==n,"fixture lacks "+n+" "+id);return cards;
    }
    static void completeCountRange() {
        for(int n=0;n<=6;n++) {
            var m=fixture();var keep=new ArrayList<>(m.privateView(A).deck().stream().filter(TypedTopDeckDiscardTest::water).limit(n).toList());
            keep.addAll(m.privateView(A).deck().stream().filter(c->!water(c)).limit(6-n).toList());
            var top=retain(m,keep);oldDiscard(m);int discarded=m.privateView(A).discard().size(),turn=m.turnNumber();var rng=m.randomTranscript();
            m.attack(A,"mill");
            check(m.lastAttack().finalDamage()==60*n,"bound count "+n+" used old discard, units or incorrect base");
            check(m.privateView(A).deck().isEmpty()&&m.privateView(A).discard().size()==discarded+6&&m.privateView(A).discard().containsAll(top),"filter incorrectly changed which six cards were discarded");
            check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"self mill prematurely decked out or did not finish once");
            check(rng.equals(m.randomTranscript())&&!m.hasPendingEffectChoice(),"mandatory mill introduced randomness or a selection prompt");
        }
    }
    static void shortDecks() {
        for(int n=0;n<=5;n++) {
            var m=fixture();var keep=m.privateView(A).deck().stream().filter(TypedTopDeckDiscardTest::water).limit(n).toList();
            retain(m,keep);oldDiscard(m);int discarded=m.privateView(A).discard().size();m.attack(A,"mill");
            check(m.lastAttack().finalDamage()==60*n&&m.privateView(A).deck().isEmpty()&&m.privateView(A).discard().size()==discarded+n,"short/empty deck did not count actual moved cards: "+n);
            check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B),"short/empty self deck became an attack cost or immediate loss");
        }
    }
    static void mixedAndSeventh(boolean typed) {
        boolean exercised=false;
        for(int seed=0;seed<100&&!exercised;seed++) {
            var m=fixture(typed,seed);var wanted=new ArrayList<CardInstance>();
            for(String id:List.of("water","water-alt","fire","special","item","basic"))wanted.addAll(fromDeck(m,id,1));
            wanted.add(fromDeck(m,"water",2).getLast());var deck=retain(m,wanted);if(!water(deck.getFirst()))continue;
            oldDiscard(m);var top=List.copyOf(deck.subList(1,7));int discarded=m.privateView(A).discard().size();var seventh=deck.getFirst();
            int expected=typed?2:4; // all six include two Basic Water, one Basic Fire and one two-unit Special Water.
            var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);var rng=m.randomTranscript();m.attack(A,"mill");
            check(m.lastAttack().finalDamage()==expected*60,"typed/untyped counts confused Energy identity, supply, old discard or seventh card");
            check(m.privateView(A).deck().equals(List.of(seventh))&&m.privateView(A).discard().size()==discarded+6&&m.privateView(A).discard().containsAll(top),"mill changed seventh card or filtered actual discard movement");
            for(UUID viewer:List.of(A,B,WATCH)) {
                String view=new Gson().toJson(session.view(viewer,viewer.equals(WATCH)?null:viewer));
                for(var card:top)check(view.contains(card.instanceId().toString()),"discarded card not in public projection");
                check(!view.contains(seventh.instanceId().toString()),"remaining hidden deck identity leaked");
            }
            check(rng.equals(m.randomTranscript()),"mill or view refresh consumed RNG");exercised=true;
        }
        check(exercised,"fixture never put a Basic Water seventh");
    }
    static void rejectedDeclaration() {
        var m=fixture();var own=m.privateView(A);var opponent=m.privateView(B);var rng=m.randomTranscript();
        reject(()->m.attack(B,"mill"));reject(()->m.attack(A,"missing"));
        check(own.equals(m.privateView(A))&&opponent.equals(m.privateView(B))&&rng.equals(m.randomTranscript()),"rejected declaration altered hidden zones");
    }
    static void countDoesNotLeak() {
        var m=fixture();var deck=m.privateView(A).deck();int expected=(int)deck.subList(deck.size()-6,deck.size()).stream().filter(TypedTopDeckDiscardTest::water).count();
        m.attack(A,"mill");check(m.lastAttack().finalDamage()==60*expected,"first attack count wrong");m.endTurn(B);
        retain(m,m.privateView(A).deck().stream().filter(c->!water(c)).limit(6).toList());m.attack(A,"mill");
        check(m.lastAttack().finalDamage()==0,"previous attack bound count leaked into next attack");
    }
    static void schema() {
        var base=JsonParser.parseString(TYPED).getAsJsonObject();
        for(String removed:List.of("bind","card_category","basic_energy_only")) {var bad=base.deepCopy();bad.remove(removed);reject(()->EffectRegistry.standard().parse(bad));}
        for(String invalid:List.of("BAD","COLORLESS","DRAGON","water","")) {var bad=base.deepCopy();bad.addProperty("energy_type",invalid);reject(()->EffectRegistry.standard().parse(bad));}
        for(int amount:List.of(0,61)) {var bad=base.deepCopy();bad.addProperty("amount",amount);reject(()->EffectRegistry.standard().parse(bad));}
        var bad=base.deepCopy();bad.addProperty("basic_energy_only",false);reject(()->EffectRegistry.standard().parse(bad));
        var target=base.deepCopy();target.addProperty("target","OPPONENT_BENCH");reject(()->EffectRegistry.standard().parse(target));
        var unknown=base.deepCopy();unknown.addProperty("energy_types","WATER");reject(()->EffectRegistry.standard().parse(unknown));
        var child=base.deepCopy();child.add("effects",JsonParser.parseString("[{\"type\":\"draw\",\"amount\":1}]"));reject(()->EffectRegistry.standard().parse(child));
        for(String type:List.of("GRASS","FIRE","WATER","LIGHTNING","PSYCHIC","FIGHTING","DARKNESS","METAL","FAIRY")) {
            var valid=base.deepCopy();valid.addProperty("energy_type",type);TopDeckDiscardEffect.from(EffectRegistry.standard().parse(valid));
        }
        AttackProgramRules.validate(attack(true));AttackProgramRules.validate(attack(false));
    }
    public static void main(String[] args) {
        schema();completeCountRange();shortDecks();mixedAndSeventh(true);mixedAndSeventh(false);rejectedDeclaration();countDoesNotLeak();
        System.out.println("TypedTopDeckDiscardTest passed: 0..6 bound counts, short/empty decks, typed Basic identity, Special/old/seventh exclusions, public discard, no RNG, legacy any-Energy behavior and strict schema");
    }
}
