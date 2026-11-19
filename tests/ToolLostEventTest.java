import dev.hua.ptcglive.view.MatchPresentation;
import java.util.List;

/** Real Lost Vacuum resolution must emit one Tool route to its physical owner's pile. */
public final class ToolLostEventTest {
    public static void main(String[] args){
        for(boolean foreign:List.of(false,true)){
            var match=NonPokemonBatch40Test.game("swsh11_en_162");
            var owner=NonPokemonBatch40Test.A;
            var hostOwner=foreign?NonPokemonBatch40Test.B:owner;
            var original=NonPokemonBatch40Test.active(match,owner);
            var host=NonPokemonBatch40Test.active(match,hostOwner);
            var tool=NonPokemonBatch40Test.equip(match,owner,"tool",original);
            if(foreign){
                var field=EventTargetBindingTest.field(match);
                field.detachTool(owner,original,tool.instanceId());field.attachToolByEffect(hostOwner,host,tool,1);
            }
            match.enablePublicEvents();NonPokemonBatch40Test.play(match);
            var cost=match.effectCardPrompt(owner).candidates().getFirst();
            NonPokemonBatch40Test.select(match,List.of(cost.instanceId()));
            NonPokemonBatch40Test.select(match,List.of(tool.instanceId()));NonPokemonBatch40Test.settle(match);
            if(!match.privateView(owner).lostZone().containsAll(List.of(tool,cost))||match.activePokemonView(hostOwner).attachedTools().contains(tool))
                throw new AssertionError("Lost Vacuum did not remove exact Tool to its owner");
            var cues=match.publicEvents().stream().map(e->e.cue()).toList();
            var route=cues.stream().filter(c->c.kind()==MatchPresentation.Kind.TOOL).toList();
            String expected="工具放逐"+(foreign?"·"+owner:"");
            if(route.size()!=1||!route.getFirst().text().equals(expected)||!route.getFirst().pokemon().equals(host))
                throw new AssertionError("Lost Tool event missing, duplicated or routed to host owner");
            if(cues.stream().anyMatch(c->c.kind()==MatchPresentation.Kind.LOST&&tool.definitionId().equals(c.cardId())))
                throw new AssertionError("Generic Lost event duplicates Tool transport");
            for(var viewer:List.of(owner,hostOwner)){
                var visible=match.clientView(viewer).players().stream().filter(p->p.id().equals(owner)).findFirst().orElseThrow();
                if(!visible.lostZone().contains(tool))throw new AssertionError("Public Lost Tool not synchronized");
            }
        }
        System.out.println("ToolLostEventTest passed: real Lost Vacuum, exact owner, foreign host, one route, public synchronized pile");
    }
}
