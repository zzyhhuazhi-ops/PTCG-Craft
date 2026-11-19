import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Cedric program and bundled printing metadata on a prepared battle board. */
public final class NonPokemonBatch133Test {
    static final List<String> IDS=List.of("bw11_en_110");
    static final UUID A=CedricQuizTest.A,B=CedricQuizTest.B;
    static final CardDatabase PRINTINGS=BundledRules.loadCardDefinitions();
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();}
    static void check(boolean ok,String why){CedricQuizTest.check(ok,why);}
    static ServerMatch aiGame(String id){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();
        c.attack=GeneratedRuleBatch8Test.hit(300,false);c.defenderAttack=c.attack;
        var m=GeneratedRuleBatch28Test.fixture(c);
        var definitions=new ArrayList<>(PRINTINGS.all());definitions.addAll(CedricQuizTest.database(m).all());
        var heights=new LinkedHashMap<String,PrintedHeight>();
        PRINTINGS.all().forEach(d->PRINTINGS.printedHeight(d.id()).ifPresent(h->heights.put(d.id(),h)));
        heights.put("attacker",PRINTINGS.printedHeight("xy12_en_044").orElseThrow());
        heights.put("defender",PRINTINGS.printedHeight("sc_cs5ac_093_r").orElseThrow());
        CedricQuizTest.replace(m,new CardDatabase(definitions,heights));
        return m;
    }
    static CardInstance put(ServerMatch m,UUID player,String id){
        var card=new CardInstance(UUID.randomUUID(),player,id);
        NonPokemonBatch127Test.zone(m,player,"hand").add(card);return card;
    }
    static void height(String id,PrintedHeight.Unit unit,int value){
        check(PRINTINGS.printedHeight(id).orElseThrow().equals(new PrintedHeight(unit,value)),"Wrong printed height: "+id);
    }
    public static void main(String[] args){
        check(profile().category()==TrainerCategory.SUPPORTER&&profile().composed(),"Cedric profile missing");
        check(profile().effectProgram().triggers().getFirst().effects().getFirst().type().equals("quiz_select_printed_height"),"Wrong Cedric operation");
        height("xy12_en_044",PrintedHeight.Unit.INCH,35);
        height("xy12_en_058",PrintedHeight.Unit.INCH,59);
        height("swsh7-5r_en_005",PrintedHeight.Unit.INCH,256);
        height("sc_cs5ac_093_r",PrintedHeight.Unit.CM,210);
        check(PRINTINGS.printedHeight("sd100_cn_002").equals(PRINTINGS.printedHeight("swsh8_en_001")),"Explicit display alias differs");
        for(String id:List.of("bw1_en_113","bw1_en_114","sd100_cn_001"))check(PRINTINGS.printedHeight(id).isEmpty(),"Height invented for full-art/V card: "+id);
        for(UUID owner:List.of(A,B))for(String id:List.of("xy12_en_044","sc_cs5ac_093_r","sd100_cn_002","swsh7-5r_en_005"))for(boolean correct:List.of(false,true)){
            var m=aiGame(IDS.getFirst());if(owner.equals(B))m.endTurn(A);
            var card=put(m,owner,id);var absent=put(m,owner,"bw1_en_113");
            int own=m.privateView(owner).hand().size(),opp=m.privateView(NonPokemonBatch127Test.other(owner)).hand().size();
            WillCoinMatchTest.play(m,owner);var q=m.effectCardPrompt(owner);
            check(q.candidates().contains(card)&&!q.candidates().contains(absent),"Printing-specific eligibility lost");
            m.submitEffectCards(owner,q.resolutionId(),q.step(),List.of(card.instanceId()));
            check(!m.privateView(owner).hand().contains(card),"Quiz card not hidden");
            var expected=PRINTINGS.printedHeight(id).orElseThrow();
            CedricQuizTest.answer(m,NonPokemonBatch127Test.other(owner),(correct?expected:new PrintedHeight(PrintedHeight.Unit.INCH,4)).labelZh());
            check(m.privateView(owner).hand().size()==own-1+(correct?0:3),"Owner draw count");
            check(m.privateView(NonPokemonBatch127Test.other(owner)).hand().size()==opp+(correct?3:0),"Opponent draw count");
            check(m.privateView(owner).hand().stream().filter(x->x.instanceId().equals(card.instanceId())).count()==1,"Physical card lost or duplicated");
        }
        for(var level:AiDifficulty.values())for(UUID owner:List.of(A,B)){
            var m=aiGame(IDS.getFirst());if(owner.equals(B))m.endTurn(A);WillCoinMatchTest.play(m,owner);
            var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);
            check(m.phase()==MatchPhase.FINISHED,"Cedric AI stalled: "+level+" "+owner);
        }
        System.out.println("NonPokemonBatch133Test passed: actual Cedric profile, exact English/Chinese/alias/retro heights, full-art exclusions, both seats and outcomes, physical return, four AI difficulties through complete matches.");
    }
}
