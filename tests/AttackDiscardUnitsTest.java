import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Official advanced rules C-01: Energy units, physical cards and partial resolution differ. */
public final class AttackDiscardUnitsTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static AttackDefinition attack(boolean composed){return BundledCombatData.attack(JsonParser.parseString(
        "{\"id\":\"hit\",\"nameZh\":\"弃置两个能量\",\"energyCost\":{},\"baseDamage\":40,\"effectSupport\":\"SUPPORTED\","+
        (composed?"\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"discard_energy\",\"target\":\"SELF\",\"amount\":2,\"minimum\":2,\"count_mode\":\"UNITS\"}]}]":"\"discardSelectedAttachedEnergyAfterAttack\":2")+"}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(UUID p,EnergyProfile energy,boolean composed){
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicEnergyProvisionTest.stadium(),energy,attack(composed));
        if(p.equals(B))m.endTurn(A);return m;
    }
    static CardInstance attach(ServerMatch m,UUID p,String id){var c=DynamicPassiveModifierTest.hand(m,p,id);m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,c.instanceId(),p,active(m,p));return c;}
    static void select(ServerMatch m,UUID p,List<UUID> ids){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),ids);}
    static void atomic(ServerMatch m,Runnable action){var before=m.clientView(null);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(action);check(before.equals(m.clientView(null))&&rng.equals(m.randomTranscript()),"Rejected Energy selection mutated state/RNG");}
    static void perform(ServerMatch m,UUID p,boolean composed,List<UUID> ids){if(composed){m.attack(p,"hit");if(m.hasPendingCardSelection())select(m,p,ids);else check(ids.isEmpty(),"Missing nonempty discard prompt");}else m.attackWithEnergyDiscards(p,"hit",List.of(),ids);}
    public static void main(String[] args){units();invalid();liveProvision();actualBallista();ai();System.out.println("AttackDiscardUnitsTest passed: both seats, multi-unit/zero-unit/basic Energy, 1 or 2 physical cards for 2 units, partial/empty resolution, live Stadium change, atomic invalid selections, actual Black Ballista and 16 four-tier AI matches");}
    static void units(){for(UUID p:List.of(A,B))for(boolean composed:List.of(false,true))for(int selected:List.of(0,1,2)){
        var m=game(p,EnergyProfile.fixed(EnergyType.COLORLESS,2),composed);var cards=new ArrayList<CardInstance>();
        if(selected>0){cards.add(attach(m,p,"special"));cards.add(attach(m,p,"special"));}
        var chosen=cards.subList(0,selected);perform(m,p,composed,chosen.stream().map(CardInstance::instanceId).toList());
        check(m.currentPlayer().equals(other(p))&&m.activePokemonView(other(p)).damage()==40,"Discard instruction incorrectly prevented attack");
        check(m.privateView(p).discard().containsAll(chosen)&&m.activePokemonView(p).attachedEnergyIds().size()==cards.size()-selected,"Multi-unit Energy was not discarded as whole chosen cards");
    }
        for(UUID p:List.of(A,B))for(boolean composed:List.of(false,true)){
            var m=game(p,new EnergyProfile(List.of()),composed);var zero=attach(m,p,"special");var basic=attach(m,p,"energy");
            perform(m,p,composed,List.of(basic.instanceId()));check(m.privateView(p).discard().contains(basic)&&m.activePokemonView(p).attachedEnergyIds().contains(zero.instanceId()),"Partial unit instruction selected a zero-provision card or failed");
        }
    }
    static void invalid(){for(UUID p:List.of(A,B))for(boolean composed:List.of(false,true)){
        var m=game(p,EnergyProfile.fixed(EnergyType.COLORLESS,2),composed);var one=attach(m,p,"energy");var two=attach(m,p,"energy");var three=attach(m,p,"energy");var foreign=attach(m,other(p),"special");
        if(composed)m.attack(p,"hit");
        for(var invalid:List.of(List.<UUID>of(),List.of(one.instanceId()),List.of(one.instanceId(),one.instanceId()),List.of(foreign.instanceId()),List.of(one.instanceId(),two.instanceId(),three.instanceId())))
            atomic(m,()->{if(composed)select(m,p,invalid);else m.attackWithEnergyDiscards(p,"hit",List.of(),invalid);});
        if(composed)select(m,p,List.of(one.instanceId(),two.instanceId()));else m.attackWithEnergyDiscards(p,"hit",List.of(),List.of(one.instanceId(),two.instanceId()));
        check(m.activePokemonView(p).attachedEnergyIds().equals(List.of(three.instanceId())),"Invalid selection consumed or replaced correct attachments");
    }}
    static void liveProvision(){for(UUID p:List.of(A,B)){
        var m=game(p,DynamicEnergyProvisionTest.conditional(),false);var special=attach(m,p,"special");var basic=attach(m,p,"energy");
        check(m.attackDiscardEnergyUnits(p,active(m,p)).get(special.instanceId())==3,"Initial conditional supply wrong");
        m.playTrainer(p,DynamicPassiveModifierTest.hand(m,p,"stadium").instanceId(),null);
        check(m.attackDiscardEnergyUnits(p,active(m,p)).get(special.instanceId())==1,"Stadium supply was cached");
        atomic(m,()->m.attackWithEnergyDiscards(p,"hit",List.of(),List.of(special.instanceId())));
        var s=MatchSession.multiplayer(m,A,B);var v=s.view(p,p);var options=v.options().stream().filter(o->o.type()==ActionType.ATTACK&&o.enabled()).toList();
        check(options.size()==1&&options.getFirst().labelZh().contains("2 张"),"UI retained insufficient unit selection after Stadium change");
        s.submit(p,p,v.revision(),options.getFirst().index());check(m.privateView(p).discard().containsAll(List.of(special,basic)),"Server/UI disagreed about current provision");
    }}
    static void actualBallista(){for(String id:List.of("bw8_en_095","bwalt_en_098")){
        var a=BundledCombatData.load().pokemon(id).orElseThrow().attacks().stream().filter(x->x.id().equals("black-ballista")).findFirst().orElseThrow();
        var m=AiCardOperationTest.attackGame(a,List.of());var attached=m.activePokemonView(AiCardOperationTest.A).attachedEnergyIds();
        check(attached.size()==2,"Regression fixture must have fewer cards than required units");
        m.attackWithEnergyDiscards(AiCardOperationTest.A,a.id(),List.of(),List.of(attached.getFirst()));
        check(m.activePokemonView(AiCardOperationTest.B).damage()==200&&m.activePokemonView(AiCardOperationTest.A).attachedEnergyIds().equals(List.of(attached.getLast())),"Black Ballista discarded cards rather than three units");
    }}
    static void ai(){for(UUID p:List.of(A,B))for(boolean composed:List.of(false,true))for(var level:AiDifficulty.values()){
        var m=game(p,EnergyProfile.fixed(EnergyType.COLORLESS,2),composed);attach(m,p,"special");attach(m,p,"special");
        var session=MatchSession.multiplayer(m,A,B);var v=session.view(p,p);var choices=v.options().stream().filter(o->o.type()==ActionType.ATTACK&&o.enabled()).toList();
        check(choices.size()==(composed?1:3),"UI omitted valid one-card/two-card unit selections");
        session.submit(p,p,v.revision(),choices.getFirst().index());AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);
        check(m.phase()==MatchPhase.FINISHED,"AI stalled on unit discard "+level);
    }}
}
