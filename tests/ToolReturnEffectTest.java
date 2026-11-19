import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.view.MatchPresentation;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.UUID;

/** Returning a public Tool must preserve the card owner and reject invalid repeated requests atomically. */
public final class ToolReturnEffectTest {
    public static void main(String[] args) {
        for(var owner:java.util.List.of(PersistentTrainerTest.A,PersistentTrainerTest.B))for(var hostOwner:java.util.List.of(PersistentTrainerTest.A,PersistentTrainerTest.B))publicReturn(owner,hostOwner);
        redirectedReturn();
        blockedReturn();
        discardReplacedWithReturn();
        hostLeavesDuringReturn();
        System.out.println("ToolReturnEffectTest passed: public destination, exact instance, private hand and replacement routing");
    }
    static void publicReturn(UUID owner,UUID hostOwner) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID original = match.activePokemonView(owner).pokemonId(),active=match.activePokemonView(hostOwner).pokemonId();
        CardInstance tool = PersistentTrainerTest.hand(match, owner, "balloon");
        match.attachToolByEffect(PersistentTrainerTest.A,owner,EffectSourceZone.HAND,tool.instanceId(),owner,original);
        if(!owner.equals(hostOwner)){
            // Deliberately reconstruct an old-save/control-change state with a foreign-owned public attachment.
            var field=EventTargetBindingTest.field(match);field.detachTool(owner,original,tool.instanceId());
            field.attachToolByEffect(hostOwner,active,tool,1);
        }
        match.enablePublicEvents();

        match.returnAttachedToolToHandByEffect(PersistentTrainerTest.A, hostOwner, active, tool.instanceId());
        if (match.activePokemonView(hostOwner).attachedTools().contains(tool)
            || !match.privateView(owner).hand().contains(tool))
            throw new AssertionError("工具回手没有从公开战场原子移入其拥有者手牌");
        var events=match.publicEvents();
        if(events.size()!=1)throw new AssertionError("工具回手必须只生成一次公开事件");
        var cue=events.getFirst().cue();
        if(cue.kind()!=MatchPresentation.Kind.TOOL||cue.amount()!=-1||!hostOwner.equals(cue.seat())||!active.equals(cue.pokemon())
            ||!cue.text().equals("工具回手·"+owner+"·"+tool.instanceId())||!tool.definitionId().equals(cue.cardId()))
            throw new AssertionError("回手来源、目标拥有者或公开实例标识错误");
        if(!match.clientView(null).hand().isEmpty()||events.getFirst().text().contains(tool.instanceId().toString()))
            throw new AssertionError("公开日志不应展示内部标识或旁观者手牌");
        var before = match.clientView(PersistentTrainerTest.B);
        try {
            match.returnAttachedToolToHandByEffect(PersistentTrainerTest.A, hostOwner, active, tool.instanceId());
            throw new AssertionError("已回手工具被再次选择");
        } catch (IllegalArgumentException expected) {
            if (!before.equals(match.clientView(PersistentTrainerTest.B)))
                throw new AssertionError("无效工具回手请求泄露了隐藏手牌信息");
        }
        if(!match.publicEvents().equals(events))throw new AssertionError("无效回手不得重播动画");
        if (!match.replayTranscript().getLast().operation().equals("公开工具回手·"+hostOwner+"·"+active+"·"+tool.instanceId()+"·"+owner))
            throw new AssertionError("工具回手没有进入服务器公开回放");
        PersistentTrainerTest.conserve(match);
    }
    static void redirectedReturn(){
        var owner=NonPokemonBatch122Test.A;var hostOwner=NonPokemonBatch122Test.B;
        var match=NonPokemonBatch122Test.game(NonPokemonBatch122Test.IDS.getFirst());
        var host=NonPokemonBatch122Test.active(match,hostOwner);
        var tool=NonPokemonBatch122Test.equip(match,owner,host);match.enablePublicEvents();
        match.returnAttachedToolToHandByEffect(owner,hostOwner,host,tool.instanceId());
        NonPokemonBatch122Test.discarded(match,tool);
        if(match.publicEvents().stream().anyMatch(e->e.cue().text().startsWith("工具回手·")))
            throw new AssertionError("替代为弃置的道具不能播放回手动画");
        var tools=match.publicEvents().stream().map(e->e.cue()).filter(c->c.kind()==MatchPresentation.Kind.TOOL).toList();
        if(tools.size()!=1||!tools.getFirst().text().equals("工具弃置·"+owner)
            ||match.publicEvents().stream().anyMatch(e->e.cue().kind()==MatchPresentation.Kind.DISCARD))
            throw new AssertionError("跨方道具弃置必须只播放一次，并指向实际拥有者弃牌区");
    }
    static void discardReplacedWithReturn(){
        var match=NonPokemonBatch109Test.game();var owner=NonPokemonBatch109Test.A;
        var host=NonPokemonBatch109Test.active(match,owner);
        var tool=NonPokemonBatch109Test.attach(match,owner,owner,host,"tool");match.enablePublicEvents();
        match.discardToolByEffect(owner,owner,host,tool.instanceId());
        NonPokemonBatch109Test.returned(match,tool,true);
        var cues=match.publicEvents().stream().map(e->e.cue()).toList();
        if(cues.size()!=1||!cues.getFirst().text().equals("工具回手·"+owner+"·"+tool.instanceId()))
            throw new AssertionError("U-Turn Board实际回手不能误播弃置或无落点的移除");
    }
    static void blockedReturn(){
        var ability=DiscardAbilityCountTest.passive("guard","{\"type\":\"prevent_return_to_hand\",\"target\":\"ALL_OPPONENT_POKEMON\"}");
        var match=NonPokemonBatch64Test.fixture(java.util.List.of(ability),null);
        var owner=NonPokemonBatch64Test.A;NonPokemonBatch64Test.install(match,owner,true,false);
        var host=NonPokemonBatch64Test.host(match,owner);var tool=match.activePokemonView(owner).attachedTools().getFirst();
        match.enablePublicEvents();match.returnAttachedToolToHandByEffect(owner,owner,host,tool.instanceId());
        if(!match.activePokemonView(owner).attachedTools().contains(tool)||!match.publicEvents().isEmpty())
            throw new AssertionError("被阻止的回手不能移除道具或产生演出");
    }
    static void hostLeavesDuringReturn(){
        for(boolean knockout:new boolean[]{false,true}){
            var match=NonPokemonBatch109Test.game();var owner=NonPokemonBatch109Test.A;
            var host=NonPokemonBatch109Test.bench(match,owner);
            var tool=NonPokemonBatch109Test.attach(match,owner,owner,host,"tool");match.enablePublicEvents();
            if(knockout)match.knockOutPokemonByEffect(owner,owner,host);
            else match.movePokemonByEffect(owner,owner,host,dev.hua.ptcglive.engine.field.LeavePlayDestination.DISCARD);
            NonPokemonBatch109Test.settle(match);NonPokemonBatch109Test.returned(match,tool,true);
            var returned=match.publicEvents().stream().map(e->e.cue()).filter(c->c.kind()==MatchPresentation.Kind.TOOL).toList();
            if(returned.size()!=1||!returned.getFirst().text().equals("工具回手·"+owner+"·"+tool.instanceId()))
                throw new AssertionError("宿主离场时仍需保留真实道具回手事件："+knockout);
        }
    }
}
