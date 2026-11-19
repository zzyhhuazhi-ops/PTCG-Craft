import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class SupporterTurnConditionTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AttackDefinition attack(){return BundledCombatData.attack(JsonParser.parseString("""
        {"id":"buddies","nameZh":"支援者增伤","energyCost":{},"baseDamage":30,"damageType":"COLORLESS","effectSupport":"SUPPORTED",
         "triggers":[{"trigger":"ON_ATTACK","effects":[{"type":"conditional","conditions":[
          {"type":"CARD_PLAYED_THIS_TURN","target":"SELF","trainer_category":"SUPPORTER"}],"effects":[
          {"type":"damage","target":"OPPONENT_ACTIVE","amount":70}]}]}]}
        """).getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch fixture(AttackDefinition attack){
        return fixture(attack,"supporter","item");
    }
    static ServerMatch fixture(AttackDefinition attack,String supporterName,String itemName){
        String[] ids={"basic","basic","energy","supporter","item"};var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList())defs.add(new CardDefinition(id,id.equals("supporter")?supporterName:id.equals("item")?itemName:id,id.equals("basic")?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,
            id.equals("supporter")?TrainerCategory.SUPPORTER:id.equals("item")?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var registry=EffectRegistry.standard();
        var supporter=BundledCombatData.trainer(JsonParser.parseString("""
            {"trainerCategory":"SUPPORTER","effects":[{"type":"search_discard_pile","target":"DISCARD","destination":"HAND","card_category":"ENERGY","amount":1,"minimum":1}]}
            """).getAsJsonObject(),registry);
        var item=BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject(),registry);
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,1,null,null,0,null,0,1,List.of(attack))),Map.of("energy",EnergyType.GRASS),Map.of("supporter",supporter,"item",item));
        for(int seed=0;seed<200;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,25);m.drawCardsByEffect(A,B,25);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("No supporter fixture");
    }
    static void play(ServerMatch m,UUID owner,AttackDefinition attack){
        m.discardHandCardsByEffect(owner,owner,List.of(DynamicPassiveModifierTest.hand(m,owner,"energy").instanceId()));
        m.playTrainer(owner,DynamicPassiveModifierTest.hand(m,owner,"supporter").instanceId(),null);
        check(m.hasPendingCardSelection(),"Supporter selection missing");
        BeforeDamageAttackTest.reject(()->m.attack(owner,attack.id()));
        BeforeDamageAttackTest.selectFirst(m,owner);
    }
    public static void main(String[] args){
        for(boolean named:List.of(false,true))for(String mode:List.of("present","discarded","failed","item","opponent","played","expired")){
            var attack=named?namedAttack():attack();var m=fixture(attack,"火箭队支援者","火箭队物品");
            switch(mode){
                case "discarded" -> m.discardHandCardsByEffect(A,A,List.of(DynamicPassiveModifierTest.hand(m,A,"supporter").instanceId()));
                case "failed" -> BeforeDamageAttackTest.reject(()->m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"supporter").instanceId(),null));
                case "item" -> m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"item").instanceId(),null);
                case "opponent" -> {m.endTurn(A);play(m,B,attack);m.endTurn(B);}
                case "played","expired" -> {play(m,A,attack);if(mode.equals("expired")){m.endTurn(A);m.endTurn(B);}}
            }
            m.attack(A,attack.id());check(m.lastAttack().finalDamage()==(mode.equals("played")?100:30),"Supporter history boundary: "+mode+" named="+named);
        }
        var named=namedAttack();var mismatch=fixture(named,"普通支援者","火箭队物品");play(mismatch,A,named);
        mismatch.attack(A,named.id());check(mismatch.lastAttack().finalDamage()==30,"Name and Supporter category must both match");
        for(var argsMap:List.of(Map.of("trainer_category","ITEM"),Map.of("trainer_category","SUPPORTER","definition_ids","supporter"),Map.of("trainer_category","SUPPORTER","target","OPPONENT_ACTIVE"),Map.<String,String>of()))
            BeforeDamageAttackTest.reject(()->TurnHistoryCondition.validate(new ConditionDefinition(ConditionType.CARD_PLAYED_THIS_TURN,argsMap,List.of())));
        System.out.println("SupporterTurnConditionTest passed: completed hand play, category/owner, pending/failed/discard exclusions and turn expiry");
    }
    static AttackDefinition namedAttack(){return BundledCombatData.attack(JsonParser.parseString("""
        {"id":"named-supporter","nameZh":"火箭队支援者增伤","energyCost":{},"baseDamage":30,"damageType":"COLORLESS","effectSupport":"SUPPORTED",
         "effects":[{"type":"conditional","conditions":[{"type":"CARD_PLAYED_THIS_TURN","target":"SELF",
           "trainer_category":"SUPPORTER","trainer_name_contains":"火箭队"}],"effects":[{"type":"damage","target":"OPPONENT_ACTIVE","amount":70}]}]}
        """).getAsJsonObject(),EffectRegistry.standard());}
}
