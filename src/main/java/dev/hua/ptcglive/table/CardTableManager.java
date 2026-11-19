package dev.hua.ptcglive.table;

import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.MatchResult;
import dev.hua.ptcglive.engine.tournament.TournamentSeries;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.DeckValidationReport;
import dev.hua.ptcglive.rules.DeckValidator;
import dev.hua.ptcglive.rules.Ruleset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.rules.CardInstance;

/** Unlimited table registry. Match creation alone locks card boxes and creates the authoritative engine. */
public final class CardTableManager {
    private final Set<CardTableKey> temporaryTables=new HashSet<>();
    public synchronized CardTableLobby openTemporary(CardTableKey key,UUID host,UUID guest,String rulesetId){
        if(host.equals(guest)||busy(host)||busy(guest)||tables.containsKey(key))throw new IllegalStateException("无法建立临时卡桌");
        temporaryTables.add(key);
        var lobby=open(key,host,rulesetId);join(key,guest);return lobby;
    }
    public synchronized void removeTemporaryTable(CardTableKey key){
        if(!temporaryTables.remove(key))return;
        var lobby=tables.get(key);
        if(lobby!=null&&lobby.match().isPresent())endMatch(lobby.matchId());
        removePhysicalTable(key);
    }
    public record PracticeMatch(UUID matchId, UUID controller, UUID firstSeat, UUID secondSeat, ServerMatch match) { }
    /** Durable state for an unopened physical table; it deliberately contains no deck contents or spectator identity. */
    public record SavedWaitingTable(CardTableKey key, UUID host, String rulesetId, UUID guest, UUID hostBox, UUID guestBox) { }
    /** Public, privacy-safe audit record written when a restart or destroyed table interrupts a live game. */
    public record InterruptedMatch(UUID matchId, CardTableKey table, UUID firstPlayer, UUID secondPlayer, Instant recordedAt, String reasonZh) { }
    /**
     * Finished-match audit.  The transcript contains only public operation
     * labels and random audit numbers; it never stores card instances, deck
     * order, hand identity, prize identity, or effect-private selections.
     */
    public record CompletedMatch(UUID matchId, CardTableKey table, UUID firstPlayer, UUID secondPlayer, UUID winner,
                                 MatchResult.Reason reason, Instant finishedAt,
                                 List<dev.hua.ptcglive.engine.MatchReplayEvent> replay,
                                 List<dev.hua.ptcglive.engine.RandomAuditEvent> randomAudit) {
        public CompletedMatch {
            if (matchId == null || firstPlayer == null || reason == null || finishedAt == null)
                throw new IllegalArgumentException("已结束对局审计缺少必要字段");
            replay = List.copyOf(replay); randomAudit = List.copyOf(randomAudit);
        }
    }
    /**
     * Live match internals are intentionally excluded.  Reconstructing hidden zones,
     * random state or an unresolved effect stack from a partial save would violate
     * the server-authoritative and hidden-information guarantees.
     */
    public record Snapshot(List<SavedWaitingTable> waitingTables, List<InterruptedMatch> interruptedMatches,
                           List<CompletedMatch> completedMatches) {
        public Snapshot {
            waitingTables = List.copyOf(waitingTables);
            interruptedMatches = List.copyOf(interruptedMatches);
            completedMatches = List.copyOf(completedMatches);
        }
    }
    private final Map<String, Ruleset> rulesets;
    private final CardBoxService boxes;
    private final DeckValidator validator;
    private final CardDatabase cards;
    private final CombatCatalog combat;
    private final Supplier<ServerRandom> randomFactory;
    private final Map<CardTableKey, CardTableLobby> tables = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> matchBoxes = new LinkedHashMap<>();
    private final Map<UUID, PracticeMatch> practice = new LinkedHashMap<>();
    private final Map<UUID, MatchSession> sessions = new LinkedHashMap<>();
    private final Clock clock;
    private static final class AiRun {
        final UUID owner,box,bot,sourceBox;final CardTableKey table;final String name,rule;final long seed;final AiDeckAnalysis analysis;final AiDifficulty difficulty;
        final Set<String> used=new HashSet<>();final Map<Integer,Integer> seen=new HashMap<>();
        int turn=-1,steps;long nextTick,decisions,presentationRevision=-1;
        boolean awaitingPresentation;
        AiRun(UUID owner,UUID box,UUID bot,CardTableKey table,UUID sourceBox,String name,String rule,AiDeckAnalysis analysis,long seed,AiDifficulty difficulty){this.owner=owner;this.box=box;this.bot=bot;this.table=table;this.sourceBox=sourceBox;this.name=name;this.rule=rule;this.analysis=analysis;this.seed=seed;this.difficulty=difficulty;}
    }
    private record AiFinished(UUID owner,UUID box,CardTableKey table,UUID sourceBox,String rule,MatchSession.Snapshot own,
                              MatchSession.Snapshot publicView,Set<UUID> spectators,AiDifficulty difficulty){}
    private record BoxStamp(String name,String rule,List<CardInstance> cards){}
    private record AiPrepared(CardTableKey table,CardTableLobby lobby,AiConfiguration report,BoxStamp player,BoxStamp computer,long expires){}
    private final Map<UUID,AiPrepared> aiPrepared=new HashMap<>();
    public record AiUpdate(UUID match,String notice,boolean aborted){}
    private final Map<UUID,AiRun> aiRuns=new LinkedHashMap<>();
    private final Map<UUID,AiFinished> aiFinished=new LinkedHashMap<>();

    private void requireComputerSetup(CardTableKey key,UUID owner) {
        var lobby=require(key);
        if(!lobby.isHost(owner)||lobby.status()!=TableLobbyStatus.WAITING||lobby.guest().isPresent())throw new IllegalStateException("请使用自己创建且无对手的空闲卡桌");
        if(!tableExists.test(key)||!online.test(owner)||!nearby.test(owner,key)||busy(owner))throw new IllegalStateException("玩家须在线、空闲并位于有效卡桌附近");
    }
    private BoxStamp computerBoxStamp(UUID owner,UUID id){
        if(!boxPossession.test(owner,id))throw new IllegalStateException("请携带所选卡盒后重新检查");
        var box=boxes.getOwned(owner,id);if(boxes.isLocked(owner,id))throw new IllegalStateException("所选卡盒已被其他对局锁定");
        return new BoxStamp(box.nameZh(),box.selectedRulesetId(),boxes.contentsForValidation(owner,id));
    }
    public synchronized AiConfiguration checkComputer(CardTableKey key,UUID owner,UUID box,UUID sourceBox,String ruleId) {
        return checkComputer(key,owner,box,sourceBox,ruleId,AiDifficulty.NORMAL);
    }
    public synchronized AiConfiguration checkComputer(CardTableKey key,UUID owner,UUID box,UUID sourceBox,String ruleId,AiDifficulty difficulty) {
        Objects.requireNonNull(difficulty);
        aiPrepared.remove(owner);requireComputerSetup(key,owner);
        Ruleset rule=rulesets.get(ruleId);if(rule==null)throw new IllegalStateException("规则包未安装");
        var player=computerBoxStamp(owner,box);var computer=box.equals(sourceBox)?player:computerBoxStamp(owner,sourceBox);
        var ownRules=validator.validate(rule,player.cards());var botRules=validator.validate(rule,computer.cards());
        var issues=AiSupport.matchProblems(computer.cards(),player.cards(),cards,combat);
        var report=new AiConfiguration(UUID.randomUUID().toString(),box,sourceBox,ruleId,player.name(),computer.name(),player.cards().size(),computer.cards().size(),ownRules,botRules,issues,AiDeckAnalysis.analyze(computer.cards(),cards,combat),difficulty);
        if(report.allowed())aiPrepared.put(owner,new AiPrepared(key,require(key),report,player,computer,clock.millis()+300_000));
        return report;
    }
    public synchronized UUID startComputer(CardTableKey key,UUID owner,UUID box,UUID sourceBox,String ruleId,String token) {
        return startComputer(key,owner,box,sourceBox,ruleId,token,AiDifficulty.NORMAL);
    }
    public synchronized UUID startComputer(CardTableKey key,UUID owner,UUID box,UUID sourceBox,String ruleId,String token,AiDifficulty difficulty) {
        requireComputerSetup(key,owner);var prepared=aiPrepared.remove(owner);
        if(prepared==null||prepared.lobby()!=require(key)||!prepared.table().equals(key)||!prepared.report().token().equals(token)||prepared.expires()<clock.millis()
            ||!prepared.report().playerBox().equals(box)||!prepared.report().computerBox().equals(sourceBox)||!prepared.report().ruleset().equals(ruleId)||prepared.report().difficulty()!=difficulty)
            throw new IllegalStateException("开局检查已失效，请重新检查双方卡组");
        var own=computerBoxStamp(owner,box);var source=box.equals(sourceBox)?own:computerBoxStamp(owner,sourceBox);
        if(!own.equals(prepared.player())||!source.equals(prepared.computer()))throw new IllegalStateException("检查后卡盒已编辑，请刷新并重新检查");
        UUID id=UUID.randomUUID(),bot=UUID.randomUUID();var lobby=require(key);
        // Each physical source card becomes a new ephemeral match instance. Never mint into CardBoxService.
        var temporary=source.cards().stream().map(c->new CardInstance(UUID.randomUUID(),bot,c.definitionId())).toList();
        try {
            var match=new ServerMatch(id,owner,bot,boxes.snapshotForMatch(owner,box,id),temporary,rulesets.get(ruleId),cards,combat,randomFactory.get());
            var session=MatchSession.multiplayer(match,owner,bot);session.computerOpponent(new MatchSession.ComputerOpponent(bot,sourceBox.toString(),source.name(),"电脑对手 · "+difficulty.label(),key.dimensionId(),key.packedBlockPos()));
            lobby.spectators().forEach(session::addSpectator);sessions.put(id,session);matchBoxes.put(id,List.of(box));
            aiFinished.entrySet().removeIf(e->e.getValue().owner().equals(owner)||e.getValue().table().equals(key));
            aiRuns.put(id,new AiRun(owner,box,bot,key,sourceBox,source.name(),ruleId,prepared.report().analysis(),UUID.randomUUID().getMostSignificantBits(),difficulty));
            lobby.chooseRuleset(owner,ruleId);lobby.selectBox(owner,box);lobby.start(id,match);startHistory(match,owner);changed();return id;
        }catch(RuntimeException e){aiRuns.remove(id);sessions.remove(id);matchBoxes.remove(id);boxes.unlock(id);throw e;}
    }
    public synchronized UUID rematchComputer(UUID matchId,UUID owner) {
        var done=aiFinished.get(matchId);if(done==null||!done.owner().equals(owner))throw new IllegalStateException("找不到你的电脑练习结算");
        var lobby=require(done.table());if(lobby.status()!=TableLobbyStatus.ENDED||!lobby.isHost(owner))throw new IllegalStateException("卡桌已被占用");
        if(!tableExists.test(done.table())||!nearby.test(owner,done.table()))throw new IllegalStateException("请返回原卡桌旁");
        lobby.resetAfterEnd();
        try{var check=checkComputer(done.table(),owner,done.box(),done.sourceBox(),done.rule(),done.difficulty());
            if(!check.allowed())throw new IllegalStateException("卡组支持/合法性已改变，请更换卡组后重新检查");
            return startComputer(done.table(),owner,done.box(),done.sourceBox(),done.rule(),check.token(),done.difficulty());}catch(RuntimeException e){lobby.end();throw e;}
    }
    public synchronized CardTableKey computerTable(UUID matchId,UUID owner) {
        var run=aiRuns.get(matchId);if(run!=null&&run.owner.equals(owner))return run.table;
        var done=aiFinished.get(matchId);if(done!=null&&done.owner().equals(owner))return done.table();
        throw new IllegalArgumentException("这不是你的电脑练习");
    }
    public synchronized MatchSession.Snapshot matchView(UUID matchId,UUID viewer,UUID seat) {
        var done=aiFinished.get(matchId);if(done==null)return session(matchId).view(viewer,seat);
        if(done.owner().equals(viewer)&&viewer.equals(seat))return done.own();
        if(seat==null&&(done.owner().equals(viewer)||done.spectators().contains(viewer)))return done.publicView();
        throw new IllegalArgumentException("无权查看电脑练习的该席位");
    }
    public synchronized List<UUID> matchSeats(UUID matchId,UUID viewer) {
        var done=aiFinished.get(matchId);return done==null?session(matchId).seats(viewer):done.owner().equals(viewer)?List.of(viewer):List.of();
    }
    public synchronized boolean computerRunning(UUID matchId){return aiRuns.containsKey(matchId);}
    /** Called before sending the owner's snapshot. Spectators cannot hold or release AI actions. */
    public synchronized void beginComputerPresentation(UUID matchId,UUID viewer,long revision,boolean opening) {
        var run=aiRuns.get(matchId);
        if(run==null||!run.owner.equals(viewer)||revision!=session(matchId).publicRevision())return;
        if(revision>run.presentationRevision||opening&&revision==run.presentationRevision){
            run.presentationRevision=revision;run.awaitingPresentation=true;
        }
    }
    public synchronized void finishComputerPresentation(UUID matchId,UUID viewer,long revision) {
        var run=aiRuns.get(matchId);
        if(run==null||!run.owner.equals(viewer)||revision!=run.presentationRevision
            ||revision!=session(matchId).publicRevision())return;
        run.awaitingPresentation=false;
    }
    /** A closed owner panel cannot acknowledge future states; wait until it is reopened. */
    public synchronized void suspendComputerPresentation(UUID matchId,UUID viewer) {
        var run=aiRuns.get(matchId);
        if(run!=null&&run.owner.equals(viewer))run.awaitingPresentation=true;
    }
    public synchronized int activeComputerCount(){return aiRuns.size();}
    public synchronized List<AiUpdate> tickReadAcknowledgements(long tick) {
        var updates=new ArrayList<AiUpdate>();
        for(var entry:List.copyOf(sessions.entrySet())) {
            try {
                var computer=aiRuns.get(entry.getKey());
                if(computer!=null&&computer.awaitingPresentation)continue;
                if(entry.getValue().tickReadAcknowledgements(tick)) {
                    finishSubmittedAction(entry.getKey(),entry.getValue(),entry.getValue().match().currentPlayer());
                    updates.add(new AiUpdate(entry.getKey(),"展示确认已等待30秒，自动继续。",false));
                }
            } catch(RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger("ptcglive-match").error("展示自动确认结算异常：match={}",entry.getKey(),failure);
                endMatch(entry.getKey());
                updates.add(new AiUpdate(entry.getKey(),"展示确认结算异常，本局已安全中止，卡盒已解锁。",true));
            }
        }
        return List.copyOf(updates);
    }
    public synchronized boolean hasMatch(UUID id){return sessions.containsKey(id)||aiFinished.containsKey(id);}
    private void finishComputer(UUID matchId) {
        if(sessions.containsKey(matchId))updateHistory(session(matchId).match());
        var run=aiRuns.remove(matchId);if(run==null)return;var session=session(matchId);var lobby=tables.get(run.table);
        session.computerOpponent(new MatchSession.ComputerOpponent(run.bot,run.sourceBox.toString(),run.name,"电脑练习结束",run.table.dimensionId(),run.table.packedBlockPos()));
        aiFinished.put(matchId,new AiFinished(run.owner,run.box,run.table,run.sourceBox,run.rule,session.view(run.owner,run.owner),session.view(run.owner,null),lobby==null?Set.of():Set.copyOf(lobby.spectators()),run.difficulty));
        // Only filtered result snapshots survive. The engine and all temporary bot instances are released.
        sessions.remove(matchId);matchBoxes.remove(matchId);boxes.unlock(matchId);if(lobby!=null)lobby.end();changed();
    }
    /** Server ticks only; at most four due games. Stop scheduling more games after the 4 ms soft budget. */
    public synchronized List<AiUpdate> tickComputers(long tick) {
        var updates=new ArrayList<AiUpdate>();long deadline=System.nanoTime()+4_000_000;int handled=0;
        for(var entry:List.copyOf(aiRuns.entrySet())) {
            UUID id=entry.getKey();var run=entry.getValue();
            if(run.nextTick>tick)continue;
            if(handled>=4||handled>0&&System.nanoTime()>deadline)break;
            handled++;run.nextTick=tick+14;
            // Rotate due games so a busy table cannot starve later tables.
            aiRuns.remove(id);aiRuns.put(id,run);
            if(!online.test(run.owner)||!tableExists.test(run.table)||tableForMatch(id)==null){endMatch(id);updates.add(new AiUpdate(id,"电脑练习异常终止：玩家离线或卡桌失效；卡盒已解锁。",true));continue;}
            if(run.awaitingPresentation)continue;
            try {
                var session=session(id);var engine=session.match();
                // Scheduling checks the wait owner, not hidden cards. Do not regenerate options during the human's turn.
                boolean waiting=switch(engine.phase()) {
                    // A mulligan waits for the other side to finish its field and Prize setup.
                    case OPENING_POKEMON -> engine.openingReady(run.bot)||engine.needsMulligan(run.bot);
                    case MULLIGAN_REVEAL -> engine.mulliganAcknowledged(run.bot);
                    case MULLIGAN_DRAW -> engine.mulliganChosen(run.bot);
                    case MULLIGAN_BENCH -> engine.mulliganBenchReady(run.bot);
                    case FINISHED -> false;
                    default -> !run.bot.equals(engine.pendingChoicePlayer());
                };
                if(waiting)continue;
                var snapshot=session.view(run.bot,run.bot);
                if(snapshot.game().result()!=null){finishComputer(id);updates.add(new AiUpdate(id,"",false));continue;}
                if(run.turn!=snapshot.game().turnNumber()){run.turn=snapshot.game().turnNumber();run.used.clear();run.seen.clear();run.steps=0;}
                var own=snapshot.game().players().stream().filter(p->p.id().equals(run.bot)).findFirst().orElseThrow().active();
                var input=dev.hua.ptcglive.ai.AiView.filtered(snapshot,cards,combat,engine.decisionEnergy(run.bot),run.analysis,
                    new AiView.OwnTurn(engine.mayAttack(run.bot),engine.energyAttached(run.bot),own==null?0:engine.activeRetreatCost(run.bot)));
                var decision=dev.hua.ptcglive.ai.AiPolicy.choose(input,run.seed+run.decisions,run.used,run.difficulty);
                if(decision.isEmpty()) {
                    if(run.bot.equals(snapshot.game().choicePlayer())||snapshot.game().choicePlayer()==null&&run.bot.equals(snapshot.game().currentPlayer())&&snapshot.game().phase()==MatchPhase.TURN&&!snapshot.game().resolvingCardEffect())
                        throw new IllegalStateException("当前强制步骤没有 AI 可提交的动作");
                    continue;
                }
                int fingerprint=snapshot.game().hashCode();int repeats=run.seen.merge(fingerprint,1,Integer::sum);
                int option=decision.get().option();
                if(repeats>2||++run.steps>512)option=snapshot.options().stream().filter(o->o.enabled()&&o.labelZh().equals("结束回合")).map(MatchSession.Option::index).findFirst().orElseThrow(()->new IllegalStateException("强制选择没有进展"));
                var selected=snapshot.options().get(option);String used=dev.hua.ptcglive.ai.AiPolicy.optionalKey(selected);
                var publicBefore=session.view(run.owner,null);
                submit(id,run.bot,run.bot,decision.get().revision(),option);run.decisions++;if(!used.isEmpty())run.used.add(used);
                var publicAfter=matchView(id,run.owner,null);
                run.nextTick=tick+dev.hua.ptcglive.view.AiPresentationTiming.ticks(
                    dev.hua.ptcglive.view.MatchPresentation.changes(publicBefore,publicAfter));
                updates.add(new AiUpdate(id,"",false));
            } catch(RuntimeException failure) {
                // Re-read authoritative options; a forced choice can never be skipped.
                try {
                    var fresh=session(id).view(run.bot,run.bot);
                    var pass=fresh.options().stream().filter(o->o.enabled()&&o.labelZh().equals("结束回合")).findFirst();
                    if(pass.isPresent()) {
                        var before=matchView(id,run.owner,null);
                        submit(id,run.bot,run.bot,fresh.revision(),pass.get().index());
                        run.nextTick=tick+dev.hua.ptcglive.view.AiPresentationTiming.ticks(
                            dev.hua.ptcglive.view.MatchPresentation.changes(before,matchView(id,run.owner,null)));
                        updates.add(new AiUpdate(id,"电脑采用保守行动：结束回合。",false));continue;
                    }
                }catch(RuntimeException noSafeFallback){failure.addSuppressed(noSafeFallback);}
                endMatch(id);updates.add(new AiUpdate(id,"电脑练习异常终止：无法完成当前选择；卡盒已解锁，未判定胜负。",true));
                org.slf4j.LoggerFactory.getLogger("ptcglive-ai").warn("Computer practice aborted: {}",id,failure);
            }
        }return List.copyOf(updates);
    }
    public record Invitation(UUID id,UUID sender,UUID recipient,CardTableKey table,String rulesetId,UUID selectedBox,long expiresAt) {}
    private final Map<UUID,Invitation> invitations=new LinkedHashMap<>();
    private final Map<UUID,CardTableLobby> invitationLobbies=new HashMap<>();
    private final Map<UUID,Set<dev.hua.ptcglive.rules.CardInstance>> invitationDecks=new HashMap<>();
    private final Map<UUID,Long> lastInvitation=new HashMap<>();
    private final Map<UUID,Rematch> rematches=new LinkedHashMap<>();
    private record Rematch(CardTableLobby lobby,UUID firstBox,UUID secondBox,Set<UUID> agreed,
                           Set<dev.hua.ptcglive.rules.CardInstance> firstDeck,Set<dev.hua.ptcglive.rules.CardInstance> secondDeck) {}
    private java.util.function.Predicate<UUID> online=id->true;
    private java.util.function.Predicate<CardTableKey> tableExists=key->true;
    private java.util.function.BiPredicate<UUID,CardTableKey> nearby=(id,key)->true;
    public synchronized void socialPresence(java.util.function.Predicate<UUID> online,java.util.function.Predicate<CardTableKey> exists,
                                           java.util.function.BiPredicate<UUID,CardTableKey> nearby) {
        this.online=online;this.tableExists=exists;this.nearby=nearby;
    }
    public synchronized boolean busy(UUID player) {
        return sessions.values().stream().anyMatch(s->s.controls(player)&&s.match().phase()!=MatchPhase.FINISHED);
    }
    public synchronized List<Invitation> invitationsFor(UUID player) {
        expireInvitations();return invitations.values().stream().filter(i->i.sender().equals(player)||i.recipient().equals(player)).toList();
    }
    public synchronized Invitation invite(CardTableKey key,UUID sender,UUID recipient) {
        expireInvitations();
        if(sender.equals(recipient))throw new IllegalArgumentException("不能邀请自己");
        if(!online.test(sender)||!online.test(recipient))throw new IllegalStateException("玩家已离线");
        if(busy(sender)||busy(recipient))throw new IllegalStateException("玩家正在对局");
        if(!nearby.test(sender,key))throw new IllegalStateException("请先来到卡桌旁发起邀请");
        long now=clock.millis();
        if(now-lastInvitation.getOrDefault(sender,Long.MIN_VALUE/2)<10_000)throw new IllegalStateException("发送邀请需间隔 10 秒");
        if(invitations.values().stream().anyMatch(i->i.sender().equals(sender)))throw new IllegalStateException("已有待处理邀请，请先取消");
        var lobby=require(key);
        if(!tableExists.test(key)||!lobby.isHost(sender)||lobby.status()!=TableLobbyStatus.WAITING||lobby.guest().isPresent())throw new IllegalStateException("需要由空闲卡桌房主发起邀请");
        UUID box=lobby.boxFor(sender);validate(sender,box,rulesets.get(lobby.rulesetId()));
        var invitation=new Invitation(UUID.randomUUID(),sender,recipient,key,lobby.rulesetId(),box,now+60_000);
        invitations.put(invitation.id(),invitation);invitationLobbies.put(invitation.id(),lobby);
        invitationDecks.put(invitation.id(),Set.copyOf(boxes.contentsForValidation(sender,box)));lastInvitation.put(sender,now);return invitation;
    }
    private boolean invitationValid(Invitation i) {
        var lobby=tables.get(i.table());
        if(i.expiresAt()<=clock.millis()||!online.test(i.sender())||!online.test(i.recipient())||busy(i.sender())||busy(i.recipient())
            ||!tableExists.test(i.table())||lobby==null||lobby!=invitationLobbies.get(i.id())||lobby.status()!=TableLobbyStatus.WAITING||!lobby.isHost(i.sender())||lobby.guest().isPresent()
            ||!lobby.rulesetId().equals(i.rulesetId())||!lobby.boxOf(i.sender()).orElse(new UUID(0,0)).equals(i.selectedBox()))return false;
        try {validate(i.sender(),i.selectedBox(),rulesets.get(i.rulesetId()));return Set.copyOf(boxes.contentsForValidation(i.sender(),i.selectedBox())).equals(invitationDecks.get(i.id()));}catch(IllegalStateException|IllegalArgumentException e){return false;}
    }
    public synchronized List<Invitation> expireInvitations() {
        var removed=invitations.values().stream().filter(i->!invitationValid(i)).toList();
        removed.forEach(i->removeInvitation(i.id()));
        lastInvitation.entrySet().removeIf(e->clock.millis()-e.getValue()>=10_000);
        rematches.entrySet().removeIf(e->{var r=e.getValue();
            if(!r.agreed().isEmpty()&&r.lobby().players().stream().anyMatch(p->busy(p)||!nearby.test(p,r.lobby().key())))r.agreed().clear();
            return tables.get(r.lobby().key())!=r.lobby()||!tableExists.test(r.lobby().key())
                ||!online.test(r.lobby().host())||r.lobby().guest().isEmpty()||!online.test(r.lobby().guest().get());});
        return removed;
    }
    public synchronized Invitation answerInvitation(UUID player,UUID token,boolean accept) {
        var i=invitations.get(token);
        if(i==null)throw new IllegalStateException("邀请已处理、取消或过期");
        if(!i.recipient().equals(player))throw new IllegalStateException("这不是发给你的邀请");
        if(!invitationValid(i)){removeInvitation(token);throw new IllegalStateException("邀请已失效：玩家、卡桌或卡盒状态变化");}
        removeInvitation(token);
        if(accept){require(i.table()).join(player);changed();}
        return i;
    }
    public synchronized void cancelInvitation(UUID player,UUID token) {
        var i=invitations.get(token);if(i==null)return;
        if(!i.sender().equals(player))throw new IllegalStateException("只有发起者可取消邀请");
        removeInvitation(token);
    }
    private void removeInvitation(UUID token){invitations.remove(token);invitationLobbies.remove(token);invitationDecks.remove(token);}
    public synchronized CardTableKey rematchTable(UUID matchId,UUID player) {
        var r=rematches.get(matchId);
        if(r==null||!r.lobby().isParticipant(player))throw new IllegalStateException("本局没有可用的再战卡桌");
        if(tables.get(r.lobby().key())!=r.lobby()||!tableExists.test(r.lobby().key())||r.lobby().status()!=TableLobbyStatus.ENDED)
            throw new IllegalStateException("卡桌已被重新使用或移除");
        return r.lobby().key();
    }
    public synchronized Optional<ServerMatch> requestRematch(UUID matchId,UUID player) {
        var key=rematchTable(matchId,player);var r=rematches.get(matchId);var lobby=r.lobby();
        if(session(matchId).match().phase()!=MatchPhase.FINISHED)throw new IllegalStateException("对局尚未结束");
        for(UUID participant:lobby.players()) {
            if(!online.test(participant)||busy(participant)||!nearby.test(participant,key))throw new IllegalStateException("双方需在线、空闲并回到原卡桌旁");
            validate(participant,participant.equals(lobby.host())?r.firstBox():r.secondBox(),rulesets.get(lobby.rulesetId()));
            if(!Set.copyOf(boxes.contentsForValidation(participant,participant.equals(lobby.host())?r.firstBox():r.secondBox()))
                .equals(participant.equals(lobby.host())?r.firstDeck():r.secondDeck())) {r.agreed().clear();throw new IllegalStateException("卡组内容已变化，请选择更换卡组并重新准备");}
        }
        r.agreed().add(player);
        if(r.agreed().size()!=2)return Optional.empty();
        rematches.remove(matchId);lobby.resetAfterEnd();
        lobby.selectBox(lobby.host(),r.firstBox());lobby.selectBox(lobby.guest().orElseThrow(),r.secondBox());
        lobby.setReady(lobby.host(),true);lobby.setReady(lobby.guest().orElseThrow(),true);
        try {return Optional.of(start(key,lobby.host()));}catch(RuntimeException e){lobby.end();throw e;}
    }
    public synchronized CardTableKey changeRematchDeck(UUID matchId,UUID player) {
        var key=rematchTable(matchId,player);
        if(!nearby.test(player,key))throw new IllegalStateException("请先回到原卡桌旁");
        var r=rematches.remove(matchId);r.lobby().resetAfterEnd();changed();return key;
    }
    public synchronized void cancelRematch(UUID matchId,UUID player) {
        var r=rematches.get(matchId);if(r!=null&&r.lobby().isParticipant(player))rematches.remove(matchId);
    }
    private final List<InterruptedMatch> recoveryHistory = new ArrayList<>();
    private final List<CompletedMatch> completedHistory = new ArrayList<>();
    private Runnable changeListener = () -> { };
    // Standalone core has no Minecraft inventory; server binds the live adapter at startup.
    private java.util.function.BiPredicate<UUID, UUID> boxPossession = (owner, box) -> true;
    public synchronized void requireBoxPossession(java.util.function.BiPredicate<UUID, UUID> policy) {
        boxPossession = java.util.Objects.requireNonNull(policy);
    }
    /** A best-of-three retains one lock token across every individual game. */
    private final Map<CardTableKey, TournamentRun> tournaments = new LinkedHashMap<>();
    private final Map<UUID, TournamentRun> tournamentGames = new LinkedHashMap<>();
    private record TournamentRun(UUID seriesId, CardTableKey table, UUID firstPlayer, UUID secondPlayer,
                                 UUID firstBox, UUID secondBox, TournamentSeries series) { }

    public CardTableManager(Map<String, Ruleset> rulesets, CardBoxService boxes, CardDatabase cards, Supplier<ServerRandom> randomFactory) {
        this(rulesets, boxes, cards, CombatCatalog.empty(), randomFactory);
    }
    public CardTableManager(Map<String, Ruleset> rulesets, CardBoxService boxes, CardDatabase cards, CombatCatalog combat, Supplier<ServerRandom> randomFactory) {
        this(rulesets, boxes, cards, combat, randomFactory, Clock.systemUTC(), new Snapshot(List.of(), List.of(), List.of()));
    }
    private CardTableManager(Map<String, Ruleset> rulesets, CardBoxService boxes, CardDatabase cards, CombatCatalog combat,
                             Supplier<ServerRandom> randomFactory, Clock clock, Snapshot saved) {
        this.rulesets = Map.copyOf(rulesets); this.boxes = boxes; this.cards = cards; this.combat = combat;
        this.validator = new DeckValidator(cards, combat); this.randomFactory = randomFactory; this.clock = clock;
        recoveryHistory.addAll(saved.interruptedMatches());
        completedHistory.addAll(saved.completedMatches());
        for (SavedWaitingTable table : saved.waitingTables()) {
            String ruleId = this.rulesets.containsKey(table.rulesetId()) ? table.rulesetId()
                : dev.hua.ptcglive.rules.CurrentFormats.migrate(table.rulesetId());
            if (this.rulesets.containsKey(ruleId))
                tables.putIfAbsent(table.key(), CardTableLobby.restoreWaiting(table.key(), table.host(), ruleId, table.guest(), table.hostBox(), table.guestBox()));
        }
    }
    /** Recreates only safe lobby state; active matches become explicit interrupted-match audit records. */
    public static CardTableManager restore(Map<String, Ruleset> rulesets, CardBoxService boxes, CardDatabase cards, CombatCatalog combat,
                                           Supplier<ServerRandom> randomFactory, Clock clock, Snapshot saved) {
        return new CardTableManager(rulesets, boxes, cards, combat, randomFactory, clock,
            saved == null ? new Snapshot(List.of(), List.of(), List.of()) : saved);
    }
    public synchronized CardTableLobby open(CardTableKey key, UUID host, String rulesetId) {
        if (!rulesets.containsKey(rulesetId)) throw new IllegalArgumentException("未安装的规则包: " + rulesetId);
        CardTableLobby existing = tables.get(key);
        if (existing != null && existing.status() != TableLobbyStatus.ENDED) return existing;
        CardTableLobby created = new CardTableLobby(key, host, rulesetId); tables.put(key, created); changed(); return created;
    }
    public synchronized Optional<CardTableLobby> table(CardTableKey key) {
        CardTableLobby lobby = tables.get(key);
        if (lobby != null) refreshPossession(lobby);
        return Optional.ofNullable(lobby);
    }
    public synchronized CardTableLobby.PublicView publicTableView(CardTableKey key) { return require(key).publicView(); }
    public synchronized int tableCount() { return tables.size(); }
    public synchronized void join(CardTableKey key, UUID player) { require(key).join(player); changed(); }
    public synchronized void spectate(CardTableKey key, UUID player) {
        CardTableLobby lobby = require(key); lobby.spectate(player);
        lobby.match().ifPresent(match -> session(match.id()).addSpectator(player));
        changed();
    }
    public synchronized void leave(CardTableKey key, UUID player) {
        CardTableLobby lobby = require(key);
        aiPrepared.remove(player);
        lobby.match().ifPresent(match -> session(match.id()).removeSpectator(player));
        lobby.leave(player);
        changed();
    }
    public synchronized void chooseRuleset(CardTableKey key, UUID player, String rulesetId) {
        if (!rulesets.containsKey(rulesetId)) throw new IllegalArgumentException("未安装的规则包: " + rulesetId);
        require(key).chooseRuleset(player, rulesetId);
        changed();
    }
    public synchronized void chooseTournamentSetting(CardTableKey key, UUID player, String settingId) {
        TournamentSettingsCatalog.require(settingId);
        require(key).chooseTournamentSetting(player, settingId);
        changed();
    }
    /** Selection is validated immediately and once more at match start. */
    public synchronized void selectBox(CardTableKey key, UUID player, UUID boxId) {
        CardTableLobby lobby = require(key);
        validate(player, boxId, rulesets.get(lobby.rulesetId()));
        lobby.selectBox(player, boxId);
        changed();
    }
    public synchronized void setReady(CardTableKey key, UUID player, boolean ready) {
        CardTableLobby lobby = require(key);
        if (ready) validate(player, lobby.boxFor(player), rulesets.get(lobby.rulesetId()));
        lobby.setReady(player, ready);
        changed();
    }
    public synchronized ServerMatch start(CardTableKey key, UUID requestedBy) {
        CardTableLobby lobby = require(key); if (!lobby.host().equals(requestedBy)) throw new IllegalStateException("只有房主可开始对局");
        if (!lobby.bothReady()) throw new IllegalStateException("双方都必须选择合法卡盒并准备");
        if(!tableExists.test(key)||lobby.players().stream().anyMatch(p->!online.test(p)||!nearby.test(p,key)||busy(p)))throw new IllegalStateException("双方必须在线、空闲并位于卡桌附近");
        UUID first = lobby.host(), second = lobby.guest().orElseThrow(); Ruleset ruleset = rulesets.get(lobby.rulesetId());
        UUID firstBox = lobby.boxFor(first), secondBox = lobby.boxFor(second);
        validate(first, firstBox, ruleset); validate(second, secondBox, ruleset);
        // Public card tables are intentionally single-game only.  The historical
        // TournamentSeries core remains an internal migration boundary, but cannot
        // be selected or started from a player-facing table.
        TournamentSettingsCatalog.require(lobby.tournamentSettingId());
        UUID matchId = UUID.randomUUID();
        try {
            var firstDeck = boxes.snapshotForMatch(first, firstBox, matchId);
            var secondDeck = boxes.snapshotForMatch(second, secondBox, matchId);
            ServerMatch match = new ServerMatch(matchId, first, second, firstDeck, secondDeck, ruleset, cards, combat, randomFactory.get());
            MatchSession session = MatchSession.multiplayer(match, first, second);
            lobby.spectators().forEach(session::addSpectator); sessions.put(matchId, session);
            matchBoxes.put(matchId, List.of(firstBox, secondBox)); lobby.start(matchId, match);startHistory(match,first,second); changed(); return match;
        } catch (RuntimeException e) { boxes.unlock(matchId); throw e; }
    }
    /** Creates the next game only when the existing server-owned series permits it. */
    public synchronized ServerMatch startNextTournamentGame(CardTableKey key, UUID requestedBy) {
        throw new IllegalStateException("卡桌采用系统单局定胜负；本局结束后请重新创建对局");
    }
    public synchronized PracticeMatch startPractice(UUID controller, UUID firstBox, UUID secondBox, String rulesetId) {
        if (firstBox.equals(secondBox)) throw new IllegalArgumentException("单人练习必须选择两个不同卡盒"); Ruleset ruleset = rulesets.get(rulesetId);
        if (ruleset == null) throw new IllegalArgumentException("未安装的规则包: " + rulesetId);
        validate(controller, firstBox, ruleset); validate(controller, secondBox, ruleset);
        UUID matchId = UUID.randomUUID(); UUID firstSeat = UUID.nameUUIDFromBytes(("practice:" + matchId + ":first").getBytes(StandardCharsets.UTF_8));
        UUID secondSeat = UUID.nameUUIDFromBytes(("practice:" + matchId + ":second").getBytes(StandardCharsets.UTF_8));
        try {
            ServerMatch match = new ServerMatch(matchId, firstSeat, secondSeat,
                practiceSnapshot(controller, firstBox, matchId, firstSeat),
                practiceSnapshot(controller, secondBox, matchId, secondSeat), ruleset, cards, combat, randomFactory.get());
            sessions.put(matchId, MatchSession.practice(match, controller, firstSeat, secondSeat));
            PracticeMatch result = new PracticeMatch(matchId, controller, firstSeat, secondSeat, match); practice.put(matchId, result); matchBoxes.put(matchId, List.of(firstBox, secondBox)); changed(); return result;
        } catch (RuntimeException e) { boxes.unlock(matchId); throw e; }
    }
    /** Zone changes use match seats; persistent collection ownership must remain untouched. */
    private List<dev.hua.ptcglive.rules.CardInstance> practiceSnapshot(UUID controller, UUID box, UUID match, UUID seat) {
        return boxes.snapshotForMatch(controller, box, match).stream()
            .map(card -> new dev.hua.ptcglive.rules.CardInstance(card.instanceId(), seat, card.definitionId()))
            .toList();
    }
    public synchronized void endMatch(UUID matchId) {
        if(sessions.containsKey(matchId))updateHistory(session(matchId).match());historyRuns.remove(matchId);
        aiRuns.remove(matchId);aiFinished.remove(matchId);
        TournamentRun tournament = tournamentGames.remove(matchId);
        if (tournament != null) {
            boxes.unlock(tournament.seriesId()); tournaments.remove(tournament.table());
            sessions.remove(matchId); matchBoxes.remove(matchId);
            tables.values().stream().filter(table -> table.key().equals(tournament.table())).forEach(CardTableLobby::end);
            changed(); return;
        }
        boxes.unlock(matchId); matchBoxes.remove(matchId); practice.remove(matchId); sessions.remove(matchId);
        tables.values().stream().filter(table -> table.match().map(match -> match.id().equals(matchId)).orElse(false)).forEach(CardTableLobby::end);
        changed();
    }
    /** Transport disconnect is a safe cancellation, not a fabricated win or a saved live game. */
    public synchronized List<UUID> disconnectPlayer(UUID player) {
        aiPrepared.remove(player);
        aiFinished.entrySet().removeIf(e->e.getValue().owner().equals(player));
        invitations.values().stream().filter(i->i.sender().equals(player)||i.recipient().equals(player)).map(Invitation::id).toList().forEach(this::removeInvitation);
        rematches.entrySet().removeIf(e->e.getValue().lobby().isParticipant(player));
        List<UUID> ended = new ArrayList<>();
        for (CardTableLobby lobby : List.copyOf(tables.values())) {
            if (lobby.spectators().contains(player)) {
                lobby.match().ifPresent(match -> session(match.id()).removeSpectator(player));
                lobby.leave(player);
            }
            if (!lobby.isParticipant(player)) continue;
            if (lobby.match().isPresent()) {
                ServerMatch match = lobby.match().orElseThrow();
                if (match.phase() != MatchPhase.FINISHED)
                    addInterrupted(match.id(), lobby.key(), lobby.host(), lobby.guest().orElse(null),
                        "对局玩家已断线，本局安全中止，不判定胜负；双方卡盒已解除锁定。");
                ended.add(match.id()); endMatch(match.id());
            } else if (lobby.status() == TableLobbyStatus.WAITING) {
                if (lobby.isHost(player)) lobby.end(); else lobby.leave(player);
            }
        }
        for (PracticeMatch game : List.copyOf(practice.values())) if (game.controller().equals(player)) {
            if (game.match().phase() != MatchPhase.FINISHED)
                addInterrupted(game.matchId(), null, player, null, "练习玩家已断线，对局安全中止，两个卡盒已解除锁定。");
            ended.add(game.matchId()); endMatch(game.matchId());
        }
        // Completed sessions may outlive their lobby so the result panel can remain visible.
        for (var entry : List.copyOf(sessions.entrySet())) {
            entry.getValue().removeSpectator(player);
            if (entry.getValue().controls(player) && entry.getValue().match().phase() == MatchPhase.FINISHED) {
                ended.add(entry.getKey()); endMatch(entry.getKey());
            }
        }
        changed();
        return List.copyOf(ended);
    }
    /** Host-only safe cancellation. It releases both card boxes before ending the table state. */
    public synchronized void endTable(CardTableKey key, UUID requestedBy) {
        CardTableLobby lobby = require(key);
        if (!lobby.isHost(requestedBy)) throw new IllegalStateException("只有房主可以结束卡桌对局");
        aiPrepared.remove(requestedBy);
        if (lobby.match().isPresent()) endMatch(lobby.matchId()); else { lobby.end(); changed(); }
    }
    public synchronized List<UUID> authorizedPracticeSeats(UUID matchId, UUID controller) {
        PracticeMatch match = practice.get(matchId); if (match == null || !match.controller().equals(controller)) throw new IllegalArgumentException("找不到你的练习对局");
        return List.of(match.firstSeat(), match.secondSeat());
    }
    public synchronized MatchSession session(UUID matchId) {
        MatchSession session = sessions.get(matchId);
        if (session == null) throw new IllegalArgumentException("找不到该对局，可能已被房主结束");
        return session;
    }
    /** Only physical public tables are broadcast. Private two-seat practice is excluded. */
    public synchronized List<UUID> publicScreenMatches(){
        return tables.values().stream().filter(t->t.status()==TableLobbyStatus.IN_MATCH)
            .map(t->t.publicView().matchId()).filter(java.util.Objects::nonNull).filter(sessions::containsKey).distinct().toList();
    }
    private boolean screenAccessible(UUID id){
        return tables.values().stream().anyMatch(t->id.equals(t.publicView().matchId())&&tableExists.test(t.key()));
    }
    public synchronized long screenRevision(UUID id){
        if(!screenAccessible(id))return -1;
        var session=sessions.get(id);if(session!=null)return session.publicRevision();
        var finished=aiFinished.get(id);return finished==null?-1:finished.publicView().revision();
    }
    public synchronized boolean screenComputerSeat(UUID id,UUID seat){
        var run=aiRuns.get(id);if(run!=null)return run.bot.equals(seat);
        var done=aiFinished.get(id);return done!=null&&done.publicView().computer()!=null&&done.publicView().computer().seat().equals(seat);
    }
    public synchronized dev.hua.ptcglive.spectator.ScreenMatch screenView(UUID id){
        if(!screenAccessible(id))throw new IllegalArgumentException("该对局不可公开观战或卡桌已移除");
        var session=sessions.get(id);if(session!=null)return session.screenView();
        var finished=aiFinished.get(id);if(finished==null)throw new IllegalArgumentException("对局已不存在");
        var view=finished.publicView();
        return dev.hua.ptcglive.spectator.ScreenMatch.capture(view.revision(),view.game(),view.events());
    }
    public synchronized UUID latestPractice(UUID controller) {
        return practice.values().stream().filter(p -> p.controller().equals(controller)).map(PracticeMatch::matchId)
            .reduce((first, next) -> next).orElseThrow(() -> new IllegalStateException("你还没有已创建的练习对局"));
    }
    public synchronized void submit(UUID matchId, UUID player, UUID seat, long revision, int option) {
        MatchSession session = session(matchId);
        session.submit(player, seat, revision, option);
        finishSubmittedAction(matchId,session,player);
    }
    private record HistoryRun(Set<UUID> owners,Map<ServerMatch.HistorySource,Long> reported){}
    private final Map<UUID,HistoryRun> historyRuns=new HashMap<>();
    private void startHistory(ServerMatch match,UUID... owners){
        if(historyRuns.containsKey(match.id()))return;
        var run=new HistoryRun(Set.of(owners),new HashMap<>());historyRuns.put(match.id(),run);
        for(var owner:run.owners())for(String id:match.historyDecks().getOrDefault(owner,List.of()))boxes.addHistory(owner,id,1,0,0);
    }
    private void updateHistory(ServerMatch match){
        var run=historyRuns.get(match.id());if(run==null)return;
        for(var e:match.historyKnockouts().entrySet())if(run.owners().contains(e.getKey().owner())){
            long previous=run.reported().getOrDefault(e.getKey(),0L),delta=e.getValue()-previous;
            if(delta>0){boxes.addHistory(e.getKey().owner(),e.getKey().cardId(),0,0,delta);run.reported().put(e.getKey(),e.getValue());}
        }
        if(match.result()!=null){
            UUID winner=match.result().winner();
            for(UUID owner:run.owners())for(String id:match.historyDecks().getOrDefault(owner,List.of()))boxes.completeHistory(owner,id,owner.equals(winner));
            historyRuns.remove(match.id());
        }
    }
    private void finishSubmittedAction(UUID matchId,MatchSession session,UUID player) {
        updateHistory(session.match());
        if(aiRuns.containsKey(matchId)){if(session.match().phase()==MatchPhase.FINISHED)finishComputer(matchId);return;}
        if (session.match().phase() == MatchPhase.FINISHED) {
            TournamentRun tournament = tournamentGames.remove(matchId);
            if (tournament == null) {
                CardTableLobby table = tableForMatch(matchId);
                PracticeMatch practiceMatch = practice.get(matchId);
                UUID firstPlayer = table != null ? table.host() : practiceMatch != null ? practiceMatch.controller() : player;
                UUID secondPlayer = table == null ? null : table.guest().orElse(null);
                if(table!=null&&secondPlayer!=null)rematches.put(matchId,new Rematch(table,table.boxFor(firstPlayer),table.boxFor(secondPlayer),new LinkedHashSet<>(),
                    Set.copyOf(boxes.contentsForValidation(firstPlayer,table.boxFor(firstPlayer))),Set.copyOf(boxes.contentsForValidation(secondPlayer,table.boxFor(secondPlayer)))));
                recordCompleted(matchId, table == null ? null : table.key(), firstPlayer, secondPlayer, session.match());
                boxes.unlock(matchId); matchBoxes.remove(matchId);
                // A player-facing card table is always exactly one game.  Once
                // the engine resolves it, the physical table must be reusable;
                // retaining an IN_MATCH lobby would leave a dead, locked-looking
                // table even though its card boxes were released.
                tables.values().stream()
                    .filter(lobby -> lobby.match().map(match -> match.id().equals(matchId)).orElse(false))
                    .forEach(CardTableLobby::end);
            } else {
                finishTournamentGame(matchId, session.match(), tournament);
            }
            changed();
        }
    }
    /**
     * Called by the physical block when it is removed.  This is a server-side
     * cancellation, never a client-declared result; both selected boxes are
     * released before the table registry entry disappears.
     */
    public synchronized void removePhysicalTable(CardTableKey key) {
        aiPrepared.entrySet().removeIf(e->e.getValue().table().equals(key));
        aiFinished.entrySet().removeIf(e->e.getValue().table().equals(key));
        for(var entry:List.copyOf(aiRuns.entrySet()))if(entry.getValue().table.equals(key)){
            addInterrupted(entry.getKey(),key,entry.getValue().owner,null,"电脑练习异常终止：卡桌方块被移除，临时卡组已销毁，卡盒已解锁。");endMatch(entry.getKey());
        }
        invitations.values().stream().filter(i->i.table().equals(key)).map(Invitation::id).toList().forEach(this::removeInvitation);
        rematches.entrySet().removeIf(e->e.getValue().lobby().key().equals(key));
        CardTableLobby lobby = tables.remove(key);
        if (lobby == null) return;
        lobby.match().ifPresent(match -> {
            UUID matchId = match.id();
            updateHistory(match);historyRuns.remove(matchId);
            boxes.unlock(matchId); matchBoxes.remove(matchId); sessions.remove(matchId); practice.remove(matchId);
            addInterrupted(matchId, key, lobby.host(), lobby.guest().orElse(null), "卡桌方块被移除，对局已由服务器安全中止，卡盒已解除锁定。");
        });
        lobby.end();
        changed();
    }
    /** Returns only records involving this player; no opponent deck or hand data is retained. */
    public synchronized List<InterruptedMatch> recoveryHistoryFor(UUID player) {
        return recoveryHistory.stream().filter(record -> player.equals(record.firstPlayer()) || player.equals(record.secondPlayer())).toList();
    }
    /** Finished public audit records are visible only to their participants, never table spectators. */
    public synchronized List<CompletedMatch> completedHistoryFor(UUID player) {
        return completedHistory.stream().filter(record -> player.equals(record.firstPlayer()) || player.equals(record.secondPlayer())).toList();
    }
    /** A compact participant-only lobby summary; raw replay remains server-side for audit tools. */
    public synchronized String completedHistorySummaryFor(UUID player) {
        List<CompletedMatch> records = completedHistoryFor(player);
        if (records.isEmpty()) return "暂无已保存的公开对局记录。";
        StringBuilder summary = new StringBuilder("我的公开对局记录（最近 ").append(Math.min(records.size(), 8)).append(" 场）：");
        int start = Math.max(0, records.size() - 8);
        for (int index = records.size() - 1; index >= start; index--) {
            CompletedMatch record = records.get(index);
            String outcome = record.winner() == null ? "平局" : player.equals(record.winner()) ? "胜" : "负";
            summary.append("\n").append(record.matchId().toString(), 0, 8).append(" · ").append(outcome)
                .append(" · ").append(record.reason()).append(" · 公开动作 ").append(record.replay().size())
                .append(" · 随机审计 ").append(record.randomAudit().size());
        }
        return summary.toString();
    }
    /** Short Chinese notice intended for the table lobby, without revealing another table's information. */
    public synchronized String recoveryNoticeFor(UUID player) {
        int count = recoveryHistoryFor(player).size();
        return count == 0 ? "" : "检测到 " + count + " 场服务器中断的对局；这些对局未恢复隐藏区域，相关卡盒已安全解除锁定，请重新开局。";
    }
    /**
     * Saves idle lobbies and a privacy-safe interruption audit only.  A running
     * match is deliberately converted to a restart-cancel record rather than
     * reconstructed from an incomplete state.
     */
    public synchronized Snapshot snapshot() {
        List<SavedWaitingTable> waiting = new ArrayList<>();
        Map<UUID, InterruptedMatch> interrupted = new LinkedHashMap<>();
        for (InterruptedMatch record : recoveryHistory) interrupted.put(record.matchId(), record);
        for (CardTableLobby lobby : tables.values()) {
            if (lobby.status() == TableLobbyStatus.WAITING && !temporaryTables.contains(lobby.key())) {
                UUID hostBox = selectedBoxOrNull(lobby, lobby.host());
                UUID guest = lobby.guest().orElse(null);
                UUID guestBox = guest == null ? null : selectedBoxOrNull(lobby, guest);
                waiting.add(new SavedWaitingTable(lobby.key(), lobby.host(), lobby.rulesetId(), guest, hostBox, guestBox));
            } else if (lobby.status() == TableLobbyStatus.IN_MATCH && lobby.match().isPresent()) {
                ServerMatch match = lobby.match().orElseThrow();
                InterruptedMatch record = interruptedFor(match.id(), lobby.key(), lobby.host(), lobby.guest().orElse(null),
                    "服务器重启将中止这场对局；为防止伪造隐藏信息，不会重建手牌、牌库、奖赏卡或随机状态，卡盒会安全解锁。");
                interrupted.put(record.matchId(), record);
            }
        }
        for (PracticeMatch game : practice.values()) {
            if (game.match().phase() != MatchPhase.FINISHED) {
                InterruptedMatch record = interruptedFor(game.matchId(), null, game.controller(), null,
                    "服务器重启将中止这场练习对局；不会重建隐藏区域，两个卡盒会安全解锁。");
                interrupted.put(record.matchId(), record);
            }
        }
        return new Snapshot(waiting, List.copyOf(interrupted.values()), completedHistory);
    }
    public synchronized void onChanged(Runnable listener) { changeListener = java.util.Objects.requireNonNull(listener); }
    public synchronized TournamentSeries tournament(CardTableKey key, UUID requester) {
        TournamentRun run = tournaments.get(key);
        if (run == null) throw new IllegalArgumentException("本桌没有进行中的赛事记录");
        CardTableLobby lobby = require(key);
        if (!lobby.isParticipant(requester) && !lobby.spectators().contains(requester)) throw new IllegalArgumentException("你没有查看本桌赛事记录的权限");
        return run.series();
    }
    /** Public score only: it intentionally contains no hands, decks, prizes, or private audit entries. */
    public synchronized String tournamentSummary(CardTableKey key) {
        TournamentRun run = tournaments.get(key);
        if (run == null) return "赛制：系统单局定胜负；无裁判确认、无额外局";
        var score = run.series().score();
        return "赛事比分：房主 " + score.firstWins() + " 胜，对手 " + score.secondWins() + " 胜，平局 " + score.draws()
            + "；状态：" + run.series().status();
    }
    private ServerMatch createTournamentGame(CardTableLobby lobby, TournamentRun run, boolean next) {
        UUID matchId = UUID.randomUUID();
        try {
            var firstDeck = boxes.snapshotForMatch(run.firstPlayer(), run.firstBox(), matchId, run.seriesId());
            var secondDeck = boxes.snapshotForMatch(run.secondPlayer(), run.secondBox(), matchId, run.seriesId());
            ServerMatch match = new ServerMatch(matchId, run.firstPlayer(), run.secondPlayer(), firstDeck, secondDeck,
                rulesets.get(lobby.rulesetId()), cards, combat, randomFactory.get());
            run.series().startGame();
            MatchSession session = MatchSession.multiplayer(match, run.firstPlayer(), run.secondPlayer());
            lobby.spectators().forEach(session::addSpectator);
            sessions.put(matchId, session); matchBoxes.put(matchId, List.of(run.firstBox(), run.secondBox())); tournamentGames.put(matchId, run);
            if (next) lobby.startNext(matchId, match); else lobby.start(matchId, match);
            startHistory(match,run.firstPlayer(),run.secondPlayer());
            return match;
        } catch (RuntimeException e) { throw e; }
    }
    private void finishTournamentGame(UUID matchId, ServerMatch match, TournamentRun tournament) {
        sessions.remove(matchId); matchBoxes.remove(matchId);
        MatchResult result = match.result();
        if (result == null) throw new IllegalStateException("已结束对局缺少服务器结果");
        if (result.winner() == null) {
            if (tournament.series().settings().requiresDeclaredWinner())
                tournament.series().awaitJudgeForEngineDraw("第 " + (tournament.series().games().size() + 1) + " 局由规则引擎记录为平局；赛事设置要求裁判确认决胜局或裁定。");
            else tournament.series().recordGameDraw("规则引擎记录的平局：" + result.reason());
        } else {
            tournament.series().recordGameWinner(result.winner(), result, "服务器规则引擎结算：" + result.reason());
        }
        CardTableLobby lobby = require(tournament.table());
        if (tournament.series().isComplete()) {
            boxes.unlock(tournament.seriesId()); tournaments.remove(tournament.table()); lobby.end();
        } else {
            lobby.finishTournamentGame();
        }
    }
    private UUID selectedBoxOrNull(CardTableLobby lobby, UUID player) {
        try { return lobby.boxFor(player); }
        catch (IllegalStateException missingSelection) { return null; }
    }
    private CardTableLobby tableForMatch(UUID matchId) {
        return tables.values().stream().filter(table -> table.match().map(match -> match.id().equals(matchId)).orElse(false)).findFirst().orElse(null);
    }
    private void recordCompleted(UUID matchId, CardTableKey table, UUID firstPlayer, UUID secondPlayer, ServerMatch match) {
        if (completedHistory.stream().anyMatch(record -> record.matchId().equals(matchId))) return;
        MatchResult result = match.result();
        if (result == null) throw new IllegalStateException("已结束对局缺少服务器结果");
        completedHistory.add(new CompletedMatch(matchId, table, firstPlayer, secondPlayer, result.winner(), result.reason(),
            clock.instant(), match.replayTranscript(), match.randomTranscript()));
    }
    private InterruptedMatch interruptedFor(UUID matchId, CardTableKey key, UUID firstPlayer, UUID secondPlayer, String reasonZh) {
        return recoveryHistory.stream().filter(record -> record.matchId().equals(matchId)).findFirst()
            .orElseGet(() -> new InterruptedMatch(matchId, key, firstPlayer, secondPlayer, clock.instant(), reasonZh));
    }
    private void addInterrupted(UUID matchId, CardTableKey key, UUID firstPlayer, UUID secondPlayer, String reasonZh) {
        if (recoveryHistory.stream().noneMatch(record -> record.matchId().equals(matchId)))
            recoveryHistory.add(new InterruptedMatch(matchId, key, firstPlayer, secondPlayer, clock.instant(), reasonZh));
    }
    private void validate(UUID owner, UUID boxId, Ruleset ruleset) {
        if (!boxPossession.test(owner, boxId)) throw new IllegalStateException("卡盒已不在背包或副手中，请拾回后重新选择并准备");
        if (!boxes.missingCards(owner, boxId).isEmpty()) throw new IllegalStateException("导入卡组尚有缺卡，请先补齐半透明卡牌");
        DeckValidationReport report = validator.validate(ruleset, boxes.contentsForValidation(owner, boxId));
        if (!report.canStartMatch()) throw new IllegalStateException("卡盒不符合 “" + ruleset.displayNameZh() + "”："
            + report.issues().stream().filter(issue -> issue.severity() == dev.hua.ptcglive.rules.DeckIssue.Severity.ERROR).findFirst().orElseThrow().messageZh());
    }
    private CardTableLobby require(CardTableKey key) {
        CardTableLobby lobby = tables.get(key);
        if (lobby == null) throw new IllegalArgumentException("此卡桌尚未创建大厅");
        refreshPossession(lobby);
        return lobby;
    }
    private void refreshPossession(CardTableLobby lobby) {
        if (lobby.status() != TableLobbyStatus.WAITING) return;
        boolean cleared = false;
        for (UUID player : lobby.players()) {
            UUID selected = lobby.boxOf(player).orElse(null);
            if (selected != null && !boxPossession.test(player, selected)) {
                lobby.selectBox(player, null); // Also cancels readiness; pickup must not silently re-ready.
                cleared = true;
            }
        }
        if (cleared) changed();
    }
    private void changed() { changeListener.run(); }
}
