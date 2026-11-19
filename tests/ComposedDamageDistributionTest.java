import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Allocation is an interaction of the shared program, not a client-authored damage operation. */
public final class ComposedDamageDistributionTest {
    static final UUID A = new UUID(0,7561), B = new UUID(0,7562), WATCH = new UUID(0,9153);
    static final String SPLIT = "{\"type\":\"damage_counter\",\"amount\":12,\"distribution\":\"EXACT\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"target_scope\":\"OPPONENT_FIELD\",\"min_targets\":1,\"max_targets\":6}";
    public static void main(String[] args) {
        allocationAuthorityAndPrivacy(); simultaneousKnockouts(); repeatedChoices();
        invalidCommits(); limitsAndNoTargets(); mixedChoicesAndCoins(); unsupportedDataAndOldEntry(); practiceAndConcession();
        System.out.println("ComposedDamageDistributionTest passed: actual JSON/session allocation, draft isolation, exact budget and complete-card KO");
    }
    static void allocationAuthorityAndPrivacy() {
        var m = game(SPLIT + ",{\"type\":\"draw\",\"amount\":1}"); var played = trainer(m);
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCH);
        m.playTrainer(A, played.instanceId(), null); var p = m.damageAllocationPrompt(A);
        check(p.budget() == 12 && p.candidates().size() == 2 && p.allocated() == 0, "分配候选或额度错误");
        var active = m.activePokemonView(B).pokemonId(); var bench = m.benchPokemonView(B).getFirst().pokemonId();
        var before = m.privateView(A); var random = m.randomTranscript();
        rejects(() -> m.adjustDamageAllocation(B, p.resolutionId(), p.step(), active, 1));
        rejects(() -> m.adjustDamageAllocation(A, UUID.randomUUID(), p.step(), active, 1));
        rejects(() -> m.adjustDamageAllocation(A, p.resolutionId(), p.step(), m.activePokemonView(A).pokemonId(), 1));
        rejects(() -> m.adjustDamageAllocation(A, p.resolutionId(), p.step(), active, -1));
        rejects(() -> m.adjustDamageAllocation(A, p.resolutionId(), p.step(), active, 2));
        for (int i = 0; i < 4; i++) m.adjustDamageAllocation(A, p.resolutionId(), p.step(), active, 1);
        for (int i = 0; i < 8; i++) m.adjustDamageAllocation(A, p.resolutionId(), p.step(), bench, 1);
        rejects(() -> m.adjustDamageAllocation(A, p.resolutionId(), p.step(), active, 1));
        check(m.activePokemonView(B).damage() == 0 && m.benchPokemonView(B).getFirst().damage() == 0
            && before.equals(m.privateView(A)) && random.equals(m.randomTranscript()), "草案提前造成伤害或抽牌");
        for (UUID receiver : List.of(B,WATCH)) check(session.view(receiver, receiver.equals(B) ? B : null).game().damageAllocation() == null, "分配草案提前发给对手或旁观者");
        rejects(() -> m.endTurn(A));
        var view = session.view(A,A); var reopen = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(view), MatchSession.Snapshot.class);
        check(reopen.game().damageAllocation().equals(m.damageAllocationPrompt(A)), "刷新／传输丢失分配");
        var confirm = view.options().stream().filter(o -> o.labelZh().startsWith("确认分配")).findFirst().orElseThrow();
        session.submit(A, A, view.revision(), confirm.index()); rejects(() -> session.submit(A, A, view.revision(), confirm.index()));
        check(m.activePokemonView(B).damage() == 40 && m.benchPokemonView(B).getFirst().damage() == 80, "未按4/8放置");
        check(m.privateView(A).hand().size() == before.hand().size()+1 && m.privateView(A).discard().contains(played)
            && !m.hasPendingEffectChoice(), "未续接整卡抽牌和弃置");
    }
    static void simultaneousKnockouts() {
        var m = game(SPLIT.replace(":12", ":24") + ",{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
        m.playTrainer(A, trainer(m).instanceId(), null); var p = m.damageAllocationPrompt(A);
        var placements = p.candidates().stream().map(t -> new ServerMatch.DamageCounterPlacement(t.owner(), t.pokemonId(),12)).toList();
        m.submitDamageAllocation(A,p.resolutionId(),p.step(),placements);
        check(m.phase() == MatchPhase.TURN && m.activePokemonView(B).damage() == 120 && m.benchPokemonView(B).getFirst().damage() == 120, "未完成原卡已提前击倒");
        var choice = m.effectChoicePrompt(A); m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(m.phase() == MatchPhase.FINISHED && m.result() != null && A.equals(m.result().winner())
            && m.result().reason() == MatchResult.Reason.NO_POKEMON
            && m.clientView(A).players().stream().filter(seat -> seat.id().equals(B)).findFirst().orElseThrow().active() == null
            && m.benchPokemonView(B).isEmpty(), "未统一移除所有击倒目标并在无替补时立即判负");
        check(m.privateView(A).prizes().size() == 6, "无宝可梦自动败北错误开放了奖赏选择");
    }
    static void repeatedChoices() {
        var m = game("{\"type\":\"repeat\",\"count\":2,\"effects\":[" + SPLIT.replace(":12",":1") + "]}");
        m.playTrainer(A,trainer(m).instanceId(),null); var first = m.damageAllocationPrompt(A);
        m.submitDamageAllocation(A,first.resolutionId(),first.step(),List.of(new ServerMatch.DamageCounterPlacement(B,m.activePokemonView(B).pokemonId(),1)));
        var second = m.damageAllocationPrompt(A); check(second.step() != first.step() && second.allocated() == 0,"Repeat 复用了旧草案");
        rejects(() -> m.adjustDamageAllocation(A,first.resolutionId(),first.step(),first.candidates().getFirst().pokemonId(),1));
        m.submitDamageAllocation(A,second.resolutionId(),second.step(),List.of(new ServerMatch.DamageCounterPlacement(B,m.benchPokemonView(B).getFirst().pokemonId(),1)));
        check(m.activePokemonView(B).damage() == 10 && m.benchPokemonView(B).getFirst().damage() == 10,"不能逐步另选目标");
    }
    static void invalidCommits() {
        var m=game(SPLIT); m.playTrainer(A,trainer(m).instanceId(),null); var p=m.damageAllocationPrompt(A);
        UUID active=m.activePokemonView(B).pokemonId(), bench=m.benchPokemonView(B).getFirst().pokemonId();
        var state=m.clientView(A); var random=m.randomTranscript(); var replay=m.replayTranscript();
        for(var entries:List.of(List.<ServerMatch.DamageCounterPlacement>of(),List.of(placement(B,active,11)),
            List.of(placement(B,active,13)),List.of(placement(B,active,6),placement(B,active,6)),
            List.of(placement(B,active,6),placement(A,bench,6)),List.of(placement(B,active,6),placement(B,UUID.randomUUID(),6)))) {
            rejects(()->m.submitDamageAllocation(A,p.resolutionId(),p.step(),entries));
            check(state.equals(m.clientView(A)) && random.equals(m.randomTranscript()) && replay.equals(m.replayTranscript()),"非法确认已部分扣血、消费随机或改草案");
        }
        m.adjustDamageAllocation(A,p.resolutionId(),p.step(),active,1); m.adjustDamageAllocation(A,p.resolutionId(),p.step(),active,-1);
        check(m.damageAllocationPrompt(A).allocated()==0 && m.damageAllocationPrompt(A).counters().isEmpty(),"撤回零值仍计入目标数量");
    }
    static void limitsAndNoTargets() {
        var m=game(SPLIT.replace("\"max_targets\":6","\"max_targets\":1")); m.playTrainer(A,trainer(m).instanceId(),null); var p=m.damageAllocationPrompt(A);
        m.adjustDamageAllocation(A,p.resolutionId(),p.step(),p.candidates().getFirst().pokemonId(),1);
        rejects(()->m.adjustDamageAllocation(A,p.resolutionId(),p.step(),p.candidates().getLast().pokemonId(),1));
        var partial=game(SPLIT.replace("\"min_targets\":1","\"min_targets\":3")); partial.playTrainer(A,trainer(partial).instanceId(),null);
        var q=partial.damageAllocationPrompt(A); check(q.minimumTargets()==2,"不足目标未缩减为实际可分配数量");
        rejects(()->partial.submitDamageAllocation(A,q.resolutionId(),q.step(),List.of(placement(B,q.candidates().getFirst().pokemonId(),12))));
        partial.submitDamageAllocation(A,q.resolutionId(),q.step(),q.candidates().stream().map(t->placement(t.owner(),t.pokemonId(),6)).toList());
        String benchOnly=SPLIT.replace("OPPONENT_FIELD","OPPONENT_BENCH");
        var empty=game(benchOnly); empty.movePokemonByEffect(A,B,empty.benchPokemonView(B).getFirst().pokemonId(),dev.hua.ptcglive.engine.field.LeavePlayDestination.HAND);
        var before=empty.privateView(A); rejects(()->empty.playTrainer(A,trainer(empty).instanceId(),null)); check(before.equals(empty.privateView(A)),"无作用训练家已出手");
        var draw=game(benchOnly+",{\"type\":\"draw\",\"amount\":1}"); draw.movePokemonByEffect(A,B,draw.benchPokemonView(B).getFirst().pokemonId(),dev.hua.ptcglive.engine.field.LeavePlayDestination.HAND);
        int hand=draw.privateView(A).hand().size(); draw.playTrainer(A,trainer(draw).instanceId(),null);
        check(!draw.hasPendingEffectChoice() && draw.privateView(A).hand().size()==hand,"空目标没有跳过并继续有作用的后续效果");
    }
    static void mixedChoicesAndCoins() {
        String one=SPLIT.replace(":12",":1");
        var m=game("{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1},"+one+",{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"destination\":\"HAND\",\"amount\":1,\"minimum\":1}");
        m.playTrainer(A,trainer(m).instanceId(),null); var cards=m.effectCardPrompt(A); var chosen=cards.candidates().getFirst();
        m.submitEffectCards(A,cards.resolutionId(),cards.step(),List.of(chosen.instanceId())); var allocation=m.damageAllocationPrompt(A);
        m.submitDamageAllocation(A,allocation.resolutionId(),allocation.step(),List.of(placement(B,allocation.candidates().getFirst().pokemonId(),1)));
        var search=m.effectCardPrompt(A); check(search.step()>allocation.step() && search.candidates().contains(chosen),"区域选择与分配游标互相覆盖");
        m.submitEffectCards(A,search.resolutionId(),search.step(),List.of(chosen.instanceId())); check(m.privateView(A).hand().contains(chosen),"分配后未续接弃牌检索");
        boolean heads=false,tails=false;
        for(int advance=0;advance<12 && !(heads&&tails);advance++) {
            var coin=game("{\"type\":\"coin_flip\",\"flips\":1,\"heads\":["+one+",{\"type\":\"draw\",\"amount\":1}],\"tails\":[{\"type\":\"draw\",\"amount\":2}]},{\"type\":\"draw\",\"amount\":1}");
            if(advance>0) coin.flipCoinsByEffect(A,advance); var before=coin.randomTranscript(); var source=trainer(coin); int hand=coin.privateView(A).hand().size();
            check(coin.canPlayTrainer(A,source.instanceId(),null) && before.equals(coin.randomTranscript()),"预检消费硬币");
            coin.playTrainer(A,source.instanceId(),null);
            if(coin.hasPendingDamageAllocation()) {
                heads=true; var p=coin.damageAllocationPrompt(A); var afterFlip=coin.randomTranscript();
                coin.submitDamageAllocation(A,p.resolutionId(),p.step(),List.of(placement(B,p.candidates().getFirst().pokemonId(),1)));
                check(coin.privateView(A).hand().size()==hand+1 && afterFlip.equals(coin.randomTranscript()),"硬币作用域丢失或重掷");
            } else { tails=true; check(coin.privateView(A).hand().size()==hand+2,"反面分支仍要求分配或抽错牌"); }
        }
        check(heads&&tails,"硬币正反分支未覆盖");
    }
    static void unsupportedDataAndOldEntry() {
        for(String bad:List.of(SPLIT.replace("EXACT","UP_TO"),SPLIT.replace("OPPONENT_FIELD","OWN_FIELD"),SPLIT.replace(":12",":61"),
            SPLIT.replace("\"min_targets\":1","\"min_targets\":0"),SPLIT.replace("\"min_targets\":1","\"min_targets\":13"),
            SPLIT.replace("\"distribution\":\"EXACT\",",""),SPLIT.replace("\"distribution\":\"EXACT\"","\"distribution\":\"EXACT\",\"cost\":1"))) {
            var profile=ComposedTrainerIntegrationTest.item("{\"type\":\"draw\",\"amount\":1},"+bad);
            check(TrainerProgramRules.unsupportedReason(profile.effectProgram()).isPresent(),"检查器误接受非法分配");
            var m=ComposedTrainerIntegrationTest.fixture(profile); var before=m.privateView(A); var random=m.randomTranscript();
            rejects(()->m.playTrainer(A,trainer(m).instanceId(),null)); check(before.equals(m.privateView(A))&&random.equals(m.randomTranscript()),"错误程序先执行了前缀");
        }
        var profile=ComposedTrainerIntegrationTest.item(SPLIT); var m=ComposedTrainerIntegrationTest.fixture(profile);
        rejects(()->m.executeEffectProgramByRule(A,null,profile.effectProgram(),EffectTrigger.ON_CARD_PLAYED));
    }
    static void practiceAndConcession() {
        var m=game(SPLIT); var session=MatchSession.practice(m,WATCH,A,B); m.playTrainer(A,trainer(m).instanceId(),null);
        check(session.view(WATCH,B).game().damageAllocation()==null,"练习乙方看到甲方草案");
        var view=session.view(WATCH,A); var add=view.options().stream().filter(o->o.cardKeys().contains("counter:add")).findFirst().orElseThrow();
        session.submit(WATCH,A,view.revision(),add.index()); check(m.damageAllocationPrompt(A).allocated()==1,"练习无法操作分配");
        m.concede(A); check(m.clientView(A).damageAllocation()==null && m.activePokemonView(B).damage()==0,"认输提交草案或残留提示");
    }
    static ServerMatch.DamageCounterPlacement placement(UUID owner,UUID target,int amount) { return new ServerMatch.DamageCounterPlacement(owner,target,amount); }
    static ServerMatch game(String effects) { return ComposedTrainerIntegrationTest.fixture(ComposedTrainerIntegrationTest.item(effects)); }
    static CardInstance trainer(ServerMatch m) { return m.privateView(A).hand().stream().filter(c->c.definitionId().equals("component")).findFirst().orElseThrow(); }
    static void rejects(Runnable r) { try { r.run(); } catch (IllegalStateException | IllegalArgumentException expected) { return; } throw new AssertionError("非法分配未拒绝"); }
    static void check(boolean v,String message) { if (!v) throw new AssertionError(message); }
}
