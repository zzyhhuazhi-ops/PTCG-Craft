import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Celebration takes actual blind Prizes before shuffling the enlarged hand, with immediate last-Prize victory. */
public final class GeneratedRuleBatch47Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B,WATCH=new UUID(0,4700);
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static JsonObject json(String id){return GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();}
    static AttackDefinition attack(String id){return BundledCombatData.attack(json(id),EffectRegistry.standard());}
    static AttackDefinition attack(){return attack("me5-5_en_108");}
    static ServerMatch fixture(AttackDefinition attack,int handSize){
        var m=GeneratedRuleBatch46Test.baseFixture(attack,0,EnergyType.METAL);
        var energy=GeneratedRuleBatch13Test.hand(m,A,"energy");
        m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,m.activePokemonView(A).pokemonId());
        setHand(m,A,handSize);return m;
    }
    static void setHand(ServerMatch m,UUID player,int size){
        int n=m.privateView(player).hand().size();
        if(n>size)m.discardHandCardsByEffect(m.currentPlayer(),player,m.privateView(player).hand().subList(0,n-size).stream().map(CardInstance::instanceId).toList());
        else if(n<size)m.drawCardsByEffect(m.currentPlayer(),player,size-n);
        check(m.privateView(player).hand().size()==size,"Hand-size fixture wrong");
    }
    /** Controlled zone moves preserve card conservation while isolating unavailable/stale Prize edges. */
    @SuppressWarnings("unchecked") static List<CardInstance> zone(ServerMatch m,UUID owner,String name){
        try {var f=ServerMatch.class.getDeclaredField("players");f.setAccessible(true);Object p=((Map<?,?>)f.get(m)).get(owner);
            var z=p.getClass().getDeclaredField(name);z.setAccessible(true);return (List<CardInstance>)z.get(p);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    static void remainingPrizes(ServerMatch m,int n){var prizes=zone(m,A,"prizes");var discard=zone(m,A,"discard");while(prizes.size()>n)discard.add(prizes.removeLast());}
    static void take(ServerMatch m,UUID player,int slot){var p=m.prizeTakePrompt(player);m.takeEffectPrizeAt(player,p.resolutionId(),p.step(),p.selection(),slot);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    static Set<CardInstance> combined(ServerMatch m,UUID player){var p=m.privateView(player);var s=new HashSet<CardInstance>();s.addAll(p.hand());s.addAll(p.deck());s.addAll(p.prizes());s.addAll(p.discard());return s;}
    public static void main(String[] args){exactHandAndBothPrints();authorityAndStaleGeneration();privacyAndSession();lastPrizeWin();zeroPrizes();staleZone();concession();reuseAndCopy();schema();System.out.println("GeneratedRuleBatch47Test passed: both printed Metal-cost Celebration attacks, exact 30-card own hand, actual blind Prize choices and generation/revision authority, taken Prizes included in one entire-hand shuffle, immediate last-Prize victory, empty/stale zones, no fake KO, cleanup, reuse and copied actual actor, strict schema and phase validation");}
    static void exactHandAndBothPrints(){
        for(String id:List.of("me5-5_en_108","me5-5_en_142"))for(int n:List.of(29,30,31)){
            var attack=attack(id);check(attack.energyCost().equals(Map.of(EnergyType.METAL,1)),"Printed Metal cost removed");
            var m=fixture(attack,n);var before=m.privateView(A);var other=m.privateView(B);var all=combined(m,A);int rng=m.randomTranscript().size(),turn=m.turnNumber();
            m.attack(A,attack.id());
            if(n!=30){check(!m.hasPendingEffectChoice()&&m.privateView(A).equals(before)&&m.randomTranscript().size()==rng&&m.currentPlayer().equals(B),"Non-30 hand took Prizes or shuffled");continue;}
            check(m.hasPendingPrizeTakeSelection()&&m.phase()==MatchPhase.TURN&&m.pendingChoicePlayer().equals(A)&&m.currentPlayer().equals(A)&&m.turnNumber()==turn,"Effect Prizes entered KO phase or advanced turn");
            var first=before.prizes().get(4);take(m,A,4);
            check(m.privateView(A).hand().size()==30&&!m.privateView(A).hand().contains(first)&&m.randomTranscript().size()==rng&&m.hasPendingPrizeTakeSelection(),"First Prize was exposed before the complete award was selected");
            var second=m.privateView(A).prizes().get(1);take(m,A,1);
            var expected=new HashSet<>(before.hand());expected.addAll(before.deck());expected.add(first);expected.add(second);
            check(m.privateView(A).hand().isEmpty()&&new HashSet<>(m.privateView(A).deck()).equals(expected)&&m.privateView(A).deck().size()==before.deck().size()+32,"All original and newly taken hand cards were not shuffled together");
            var untaken=new ArrayList<>(before.prizes());untaken.remove(first);untaken.remove(second);
            check(m.privateView(A).prizes().equals(untaken)&&combined(m,A).equals(all),"Untaken Prize order or card conservation changed");
            var events=m.randomTranscript().subList(rng,m.randomTranscript().size());
            check(events.size()==m.privateView(A).deck().size()-1&&events.stream().allMatch(e->e.operation().equals("effect-shuffle-hand:"+A)),"Hand was not shuffled exactly once");
            check(!m.hasPendingEffectChoice()&&!m.hasPendingPrizeTakeSelection()&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.lastAttack().finalDamage()==0,"Attack failed to finish once or generated a fake KO");
            check(m.activePokemonView(A).damage()==0&&m.activePokemonView(B).damage()==0&&m.privateView(B).prizes().equals(other.prizes())&&m.privateView(B).discard().equals(other.discard()),"Prize effect affected opposing Prizes/field/discard");
        }
        var m=fixture(attack(),29);setHand(m,B,30);m.attack(A,attack().id());check(m.privateView(A).prizes().size()==6&&!m.hasPendingEffectChoice(),"Opponent's 30-card hand qualified actor");
    }
    static void authorityAndStaleGeneration(){
        var m=fixture(attack(),30);m.attack(A,attack().id());var p=m.prizeTakePrompt(A);
        for(Runnable r:List.<Runnable>of(()->m.takeEffectPrizeAt(B,p.resolutionId(),p.step(),p.selection(),0),()->m.takeEffectPrizeAt(A,UUID.randomUUID(),p.step(),p.selection(),0),()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step()+1,p.selection(),0),()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step(),p.selection()+1,0),()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step(),p.selection(),-1),()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step(),p.selection(),p.slotCount()),()->m.takePrizeAt(A,0),()->m.endTurn(A)))atomic(m,r);
        check(p.equals(m.prizeTakePrompt(A)),"Rejected request changed Prize prompt");take(m,A,0);
        atomic(m,()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step(),p.selection(),0));
        check(m.prizeTakePrompt(A).selection()==1&&m.prizeTakePrompt(A).remaining()==1&&m.prizeTakePrompt(A).slotCount()==5,"Next blind choice did not get a fresh generation");take(m,A,4);
        atomic(m,()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step(),1,0));
    }
    static void privacyAndSession(){
        var m=fixture(attack(),30);var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);var prizes=m.privateView(A).prizes();
        m.attack(A,attack().id());var p=m.prizeTakePrompt(A);var actor=session.view(A,A);
        check(actor.options().stream().filter(o->o.labelZh().startsWith("拿取背面朝上的奖赏卡：位置 ")).count()==6,"Blind positions lack clickable Prize actions");
        check(actor.options().stream().noneMatch(o->o.labelZh().equals("结束回合")),"Turn-end action available while taking effect Prizes");
        for(var v:List.of(actor,session.view(B,B),session.view(WATCH,null))){String network=new Gson().toJson(v);for(var c:prizes)check(!network.contains(c.instanceId().toString()),"Face-down Prize physical identity leaked to a client");check(!network.contains("effectProgram")&&!network.contains("effectiveAttack"),"Executable attack leaked to client");}
        check(session.view(B,B).options().stream().noneMatch(o->o.labelZh().startsWith("拿取背面"))&&session.view(WATCH,null).options().isEmpty(),"Opponent/spectator received actor's Prize choices");
        reject(()->m.prizeTakePrompt(B));reject(()->m.prizeTakePrompt(WATCH));
        var option=actor.options().stream().filter(o->o.labelZh().equals("拿取背面朝上的奖赏卡：位置 6")).findFirst().orElseThrow();
        atomic(m,()->session.submit(B,A,actor.revision(),option.index()));atomic(m,()->session.submit(WATCH,A,actor.revision(),option.index()));
        session.submit(A,A,actor.revision(),option.index());atomic(m,()->session.submit(A,A,actor.revision(),option.index()));
        var second=session.view(A,A);var action=second.options().stream().filter(o->o.labelZh().equals("拿取背面朝上的奖赏卡：位置 1")).findFirst().orElseThrow();session.submit(A,A,second.revision(),action.index());
        check(m.privateView(A).prizes().size()==4&&m.privateView(A).hand().isEmpty()&&m.currentPlayer().equals(B),"Session could not complete both blind choices and tail shuffle");
    }
    static void lastPrizeWin(){
        for(int n:List.of(1,2,3)){
            var m=fixture(attack(),30);remainingPrizes(m,n);var before=m.privateView(A);var other=m.privateView(B);int rng=m.randomTranscript().size(),turn=m.turnNumber();
            m.attack(A,attack().id());check(m.prizeTakePrompt(A).remaining()==Math.min(n,2),"Short Prize zone requested unavailable cards");take(m,A,0);
            if(n==2){check(m.phase()!=MatchPhase.FINISHED&&m.hasPendingPrizeTakeSelection(),"Won before final Prize was taken");take(m,A,0);}
            if(n<=2){check(m.phase()==MatchPhase.FINISHED&&m.result().winner().equals(A)&&m.result().reason()==MatchResult.Reason.PRIZES,"All-Prize victory did not finish immediately");check(m.privateView(A).hand().size()==30+n&&m.privateView(A).deck().equals(before.deck())&&m.randomTranscript().size()==rng&&m.turnNumber()==turn,"Card tail or next turn ran after game end");check(m.privateView(A).hand().containsAll(before.prizes())&&!m.hasPendingEffectChoice()&&!m.hasPendingPrizeTakeSelection()&&m.privateView(B).equals(other),"Final Prizes not taken or match cleanup affected opponent");}
            else{take(m,A,0);check(m.phase()!=MatchPhase.FINISHED&&m.privateView(A).prizes().size()==1&&m.privateView(A).hand().isEmpty(),"Three remaining Prizes incorrectly won or skipped shuffle");}
        }
    }
    static void zeroPrizes(){var m=fixture(attack(),30);remainingPrizes(m,0);var hand=m.privateView(A).hand();var deck=m.privateView(A).deck();int rng=m.randomTranscript().size();m.attack(A,attack().id());check(!m.hasPendingPrizeTakeSelection()&&!m.hasPendingEffectChoice()&&m.privateView(A).hand().equals(hand)&&m.privateView(A).deck().equals(deck)&&m.randomTranscript().size()==rng,"Unavailable Prize step treated zero as successful or stranded continuation");}
    static void staleZone(){var m=fixture(attack(),30);m.attack(A,attack().id());var p=m.prizeTakePrompt(A);var c=zone(m,A,"prizes").removeLast();zone(m,A,"discard").add(c);atomic(m,()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step(),p.selection(),0));zone(m,A,"discard").remove(c);zone(m,A,"prizes").add(c);take(m,A,0);take(m,A,0);check(!m.hasPendingEffectChoice(),"Stale-zone rejection damaged valid continuation");}
    static void concession(){var m=fixture(attack(),30);var held=m.privateView(A).prizes().get(2);m.attack(A,attack().id());take(m,A,2);var p=m.prizeTakePrompt(A);var before=m.privateView(A);int rng=m.randomTranscript().size();m.concede(A);check(m.phase()==MatchPhase.FINISHED&&!m.hasPendingPrizeTakeSelection()&&!m.hasPendingEffectChoice()&&m.privateView(A).deck().equals(before.deck())&&m.privateView(A).prizes().equals(before.prizes())&&m.privateView(A).hand().contains(held)&&m.privateView(A).hand().size()==before.hand().size()+1&&m.randomTranscript().size()==rng,"Concession shuffled/took more cards or left a pending Prize prompt");atomic(m,()->m.takeEffectPrizeAt(A,p.resolutionId(),p.step(),p.selection(),0));}
    static void reuseAndCopy(){
        var m=fixture(attack(),30);m.attack(A,attack().id());var first=m.prizeTakePrompt(A);take(m,A,0);take(m,A,0);m.endTurn(B);setHand(m,A,30);m.attack(A,attack().id());var next=m.prizeTakePrompt(A);check(!next.resolutionId().equals(first.resolutionId())&&next.selection()==0,"Later attack inherited old Prize generation/count memory");take(m,A,0);take(m,A,0);check(m.privateView(A).prizes().size()==2&&m.privateView(A).hand().isEmpty(),"Reused count caused duplicate-binding failure or missed shuffle");
        var copied=CopyAttackTest.fixture(attack());CopyAttackTest.pay(copied);setHand(copied,CopyAttackTest.A,30);var choice=copied.copyAttackOptions(CopyAttackTest.A,"copy").stream().filter(c->c.attackId().equals(attack().id())).findFirst().orElseThrow();var prizes=copied.privateView(CopyAttackTest.B).prizes();CopyAttackTest.copy(copied,choice,List.of(),List.of());check(copied.pendingChoicePlayer().equals(CopyAttackTest.A),"Copied Prize choices belonged to printed source owner");take(copied,CopyAttackTest.A,0);take(copied,CopyAttackTest.A,0);check(copied.privateView(CopyAttackTest.A).prizes().size()==4&&copied.privateView(CopyAttackTest.A).hand().isEmpty()&&copied.privateView(CopyAttackTest.B).prizes().equals(prizes)&&copied.lastAttack().attacker().equals(CopyAttackTest.A),"Copy took or shuffled the original source owner's cards");
    }
    static void schema(){
        var effect=json("me5-5_en_108").getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject();
        for(var kv:Map.of("amount","3","bind","bad name","minimum","0").entrySet()){var bad=effect.deepCopy();bad.addProperty(kv.getKey(),kv.getValue());reject(()->EffectRegistry.standard().parse(bad));}
        var missing=effect.deepCopy();missing.remove("bind");reject(()->EffectRegistry.standard().parse(missing));var child=effect.deepCopy();child.add("effects",JsonParser.parseString("[{\"type\":\"draw\",\"amount\":1}]"));reject(()->EffectRegistry.standard().parse(child));
        var e=EffectRegistry.standard().parse(effect);for(var t:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ONCE_PER_TURN)){var p=new EffectProgram(List.of(new TriggeredEffects(t,List.of(),List.of(e))));reject(()->ResumableProgramRules.validate(p,t!=EffectTrigger.ON_CARD_PLAYED,t));}
        var bad=json("me5-5_en_108");var body=bad.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().getAsJsonArray("effects");var producer=body.remove(0);body.add(producer);reject(()->AttackProgramRules.validate(BundledCombatData.attack(bad,EffectRegistry.standard())));
        var duplicate=json("me5-5_en_108");var es=duplicate.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().getAsJsonArray("effects");es.add(es.get(0).deepCopy());reject(()->AttackProgramRules.validate(BundledCombatData.attack(duplicate,EffectRegistry.standard())));
    }
}
