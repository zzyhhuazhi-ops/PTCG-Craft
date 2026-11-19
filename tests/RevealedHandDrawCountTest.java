import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic programs exercise immutable revealed-hand counts without enabling any extra real cards. */
public final class RevealedHandDrawCountTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String REVEAL="{\"type\":\"reveal_hand\",\"target\":\"OPPONENT_ACTIVE\",\"bind\":\"seen\",\"card_category\":\"POKEMON\"}";
    static final String DRAW="{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1,\"count_bind\":\"seen\"}";
    static final String FLAT="{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}";
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static EffectDefinition effect(String s){return EffectRegistry.standard().parse(JsonParser.parseString(s).getAsJsonObject());}
    static EffectProgram program(String s){return new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,s)));}
    static TrainerProfile trainer(String s){return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":["+s+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static boolean pokemon(CardInstance c){return Set.of("basic","evolved").contains(c.definitionId());}
    static int count(ServerMatch m){return (int)m.privateView(B).hand().stream().filter(RevealedHandDrawCountTest::pokemon).count();}
    static void leavePokemon(ServerMatch m,int n){var ids=m.privateView(B).hand().stream().filter(RevealedHandDrawCountTest::pokemon).skip(n).map(CardInstance::instanceId).toList();if(!ids.isEmpty())m.discardHandCardsByEffect(A,B,ids);check(count(m)==n,"Fixture lacks requested Pokemon count");}
    static ServerMatch game(String text){
        String[] ids={"basic","evolved","fossil","item","energy"};var definitions=new ArrayList<CardDefinition>();
        for(String id:ids)definitions.add(FossilFormTest.definition(id,Set.of("basic","evolved").contains(id)?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("basic")));
        var catalog=new CombatCatalog(Map.of("basic",FossilFormTest.named(300,null,"Basic",null),"evolved",FossilFormTest.named(300,"basic","Evolved","Basic")),Map.of("energy",EnergyType.COLORLESS),Map.of("fossil",FossilFormTest.fossil(),"item",trainer(text)));
        var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
        for(int seed=0;seed<1000;seed++){
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(definitions),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("basic"))))continue;
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,22);m.drawCardsByEffect(A,B,22);m.endTurn(A);m.endTurn(B);
            if(count(m)>=7&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("item")).count()>=2&&m.privateView(B).hand().stream().anyMatch(c->c.definitionId().equals("fossil")))return m;
        }throw new AssertionError("No revealed-hand fixture");
    }
    static void play(ServerMatch m){m.playTrainer(A,hand(m,A,"item").instanceId(),null);}
    static void ack(ServerMatch m){m.acknowledgeEffectReveal(A,m.effectCardReveal().id());}
    public static void main(String[] args){contracts();countsAndPause();snapshotAndActualDraw();emptyAndShort();isolatedMemories();
        System.out.println("RevealedHandDrawCountTest passed:0..6 frozen Pokemon counts, hand Fossil exclusion, public reveal/authorized ack, no pre-ack draw, post-reveal hand mutation, actual-draw output, zero/short decks, isolated memories, strict schema and consumer flow");}
    static void countsAndPause(){
        for(int n=0;n<=6;n++){
            var m=game(REVEAL+","+DRAW);leavePokemon(m,n);int before=m.privateView(A).hand().size(),deck=m.privateView(A).deck().size();var opponent=m.privateView(B);var rng=m.randomTranscript();
            check(m.canPlayTrainer(A,hand(m,A,"item").instanceId(),null),"Nonempty revealed hand rejected merely because Pokemon count is zero");
            play(m);var reveal=m.effectCardReveal();
            check(reveal!=null&&reveal.cardIds().equals(opponent.hand().stream().map(CardInstance::definitionId).sorted().toList()),"Reveal/count snapshot mismatch");
            check(m.privateView(A).deck().size()==deck&&m.privateView(A).hand().size()==before-1,"Drew before acknowledgement");
            check(m.clientView(A).effectReveal().equals(m.clientView(B).effectReveal()),"Explicit public reveal not shared");
            reject(()->m.acknowledgeEffectReveal(B,reveal.id()));reject(()->m.acknowledgeEffectReveal(A,UUID.randomUUID()));
            ack(m);check(m.privateView(A).hand().size()==before-1+n&&m.privateView(A).deck().size()==deck-n,"Incorrect Pokemon count, including hidden-zone Fossil/evolution identity: "+n);
            check(m.privateView(B).equals(opponent)&&m.randomTranscript().equals(rng),"Reveal altered opponent cards or RNG");reject(()->m.acknowledgeEffectReveal(A,reveal.id()));
        }
    }
    static void snapshotAndActualDraw(){
        var m=game(REVEAL+",{\"type\":\"discard_hand\",\"target\":\"OPPONENT_ACTIVE\"},"+DRAW);leavePokemon(m,3);int before=m.privateView(A).hand().size();play(m);ack(m);
        check(m.privateView(B).hand().isEmpty()&&m.privateView(A).hand().size()==before+2,"Draw recomputed from changed hand instead of revealed snapshot");
        String output=DRAW.replace("\"amount\":1","\"amount\":1,\"bind\":\"drawn\"");
        var actual=game(REVEAL+","+output+",{\"type\":\"draw\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"count_bind\":\"drawn\"}");leavePokemon(actual,3);
        actual.discardTopDeckCardsByEffect(A,A,actual.privateView(A).deck().size()-1);int their=actual.privateView(B).hand().size();play(actual);ack(actual);
        check(actual.privateView(A).deck().isEmpty()&&actual.privateView(B).hand().size()==their+1,"draw.bind overwrote input or recorded requested instead of actually drawn cards");
    }
    static void emptyAndShort(){
        var empty=game(REVEAL+","+DRAW);empty.discardHandCardsByEffect(A,B,empty.privateView(B).hand().stream().map(CardInstance::instanceId).toList());var before=empty.privateView(A);
        check(!empty.canPlayTrainer(A,hand(empty,A,"item").instanceId(),null),"Empty hand exact reveal/draw should have no effect");reject(()->play(empty));check(before.equals(empty.privateView(A)),"No-effect play mutated hand/deck");
        // A separate fixed draw makes the program meaningful and proves empty reveals record zero without a prompt.
        var zero=game(REVEAL+","+DRAW+","+FLAT);zero.discardHandCardsByEffect(A,B,zero.privateView(B).hand().stream().map(CardInstance::instanceId).toList());int size=zero.privateView(A).hand().size();play(zero);
        check(zero.effectCardReveal()==null&&zero.privateView(A).hand().size()==size&&zero.replayTranscript().stream().anyMatch(e->e.operation().contains("seen；数量=0")),"Empty reveal failed explicit zero binding or introduced a pause");
        for(int left:List.of(0,1,2)){
            var m=game(REVEAL+","+DRAW);leavePokemon(m,3);m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size()-left);int held=m.privateView(A).hand().size();
            check(m.canPlayTrainer(A,hand(m,A,"item").instanceId(),null),"Empty own deck hid a meaningful reveal");play(m);ack(m);
            check(m.privateView(A).deck().isEmpty()&&m.privateView(A).hand().size()==held-1+left&&m.phase()==MatchPhase.TURN,"Effect draw failed short/empty deck behavior");
        }
    }
    static void isolatedMemories(){var m=game(REVEAL+","+DRAW);leavePokemon(m,3);play(m);ack(m);leavePokemon(m,1);int before=m.privateView(A).hand().size();play(m);ack(m);check(m.privateView(A).hand().size()==before,"Count leaked or duplicate binding across independent Item resolutions");}
    static void contracts(){
        for(String target:List.of("SELF","HAND","OPPONENT_ACTIVE"))HandRevealEffect.validate(effect("{\"type\":\"reveal_hand\",\"target\":\""+target+"\"}"));
        for(String bad:List.of(REVEAL.replace(",\"bind\":\"seen\"",""),REVEAL.replace(",\"card_category\":\"POKEMON\"",""),REVEAL.replace("OPPONENT_ACTIVE","SELF"),REVEAL.replace("seen","Bad"),REVEAL.replace("\"bind\"","\"count_bind\""),REVEAL.replace("\"POKEMON\"","\"POKEMON\",\"amount\":1")))reject(()->EffectSchemaValidator.validate(effect(bad)));
        EffectSchemaValidator.validate(effect(REVEAL.replace("POKEMON","TRAINER")));
        for(String key:List.of("count_type","count_target","count_offset","pokemon_families","family_match"))reject(()->DrawCount.from(effect(DRAW.replace("\"count_bind\":\"seen\"","\"count_bind\":\"seen\",\""+key+"\":\"0\""))));
        for(String name:List.of("","Bad","a/b","a".repeat(65)))reject(()->DrawCount.from(effect(DRAW.replace("seen",name))));
        check(new DrawCount(2,null,null,0,Map.of()).resolve(context(0))==2,"Old constructor/static draw changed");
        check(DrawCount.from(effect(DRAW.replace("\"amount\":1","\"amount\":2"))).resolve(context(40))==60&&DrawCount.from(effect(DRAW)).resolve(context(0))==0,"Bound draw zero or cap incorrect");
        check(AttackCountBindings.references(effect(DRAW))&&AttackCountBindings.usesCounts(program(DRAW)),"Consumer omitted from count dependency detection");
        check(EffectExecutionCapabilityCatalog.assess(effect(DRAW)).capability()==EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"Consumer falsely advertised as context-free");
        for(String invalid:List.of(DRAW, DRAW+","+REVEAL, REVEAL+","+REVEAL+","+DRAW,REVEAL+","+DRAW.replace("\"amount\":1","\"amount\":1,\"bind\":\"seen\""),
            "{\"type\":\"choice\",\"effects\":["+REVEAL+","+DRAW+"]}","{\"type\":\"repeat\",\"count\":2,\"effects\":["+REVEAL+"]},"+DRAW))reject(()->TrainerProgramRules.validate(program(invalid)));
        reject(()->EffectResolutionPreflight.validate(program(DRAW),EffectTrigger.ON_CARD_PLAYED,c->{},e->{}));
        var delayed=new EffectDefinition("delayed_effect",EffectTarget.SELF,Map.of(),List.of(),List.of(effect(DRAW)));
        reject(()->AttackCountBindings.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(effect(REVEAL),delayed))))));
        // Optional prior branches retain the preexisting absent=0 contract, without granting sibling-branch reads.
        TrainerProgramRules.validate(program("{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HAND_SIZE\",\"value\":60}],\"effects\":["+REVEAL+"]},"+DRAW));
    }
    static EffectConditionContext context(int count){return new EffectConditionContext(){
        public int number(ConditionType t,EffectTarget target,Map<String,String> a){return 0;}
        public boolean hasStatus(EffectTarget t,String s){return false;}public boolean flag(ConditionType t,EffectTarget p,Map<String,String> a){return false;}
        public boolean coinResult(String s){return false;}public int boundCount(String name){return count;}public boolean customCondition(ConditionDefinition c){return false;}
    };}
}
