import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.*;
import java.nio.file.*;
import java.util.*;

/** Actual printed tools and in-play Items: UI route, authoritative submit, all four continuation policies. */
public final class BattleSpecialActionTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static final List<String> failures=new ArrayList<>(), entryFailures=new ArrayList<>();
    static int cases;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static boolean offered(MatchSession.Snapshot v,MatchSession.Option o) {
        return MatchActionMatcher.flowOptions(v).contains(o)||o.cardKeys().stream().anyMatch(k->MatchActionMatcher.cardFaceActions(v.options(),k,!v.game().resolvingCardEffect()).contains(o));
    }
    static void entry(MatchSession.Snapshot v,MatchSession.Option o,String name) {
        if(!offered(v,o))entryFailures.add(name+": no UI entry for "+o.type());
        var renamed=v.options().stream().map(x->new MatchSession.Option(x.index(),"显示文案改变 "+x.index(),x.cardKeys(),x.enabled(),x.type(),x.commandId())).toList();
        var copy=new MatchSession.Snapshot(v.revision(),v.practice(),v.controllableSeats(),v.game(),renamed,v.cardNames(),v.publicCues(),v.attackBlockZh(),v.guidance(),v.events(),v.computer());
        if(!offered(copy,renamed.stream().filter(x->x.index()==o.index()).findFirst().orElseThrow()))entryFailures.add(name+": label-dependent entry for "+o.type());
        try {
            Path out=Path.of(System.getProperty("ptcglive.auditDir","outputs/battle-operation-audit"));Files.createDirectories(out);
            String position="";
            if(Set.of(ActionType.TOOL_ABILITY,ActionType.FOSSIL_DISCARD,ActionType.DOLL_RETURN).contains(o.type())) {
                var own=v.game().players().stream().filter(p->p.id().equals(v.game().viewingSeat())).findFirst().orElseThrow();
                position=own.active()!=null&&o.cardKeys().contains("field:"+own.active().pokemonId())?"-active":"-bench";
            }
            Path file=out.resolve("special-"+o.type()+position+".json");
            if(!Files.exists(file))Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(v));
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    static void submit(MatchSession s,UUID p,ActionType type,AiDifficulty level,String name) {
        var v=s.view(p,p);var o=v.options().stream().filter(x->x.enabled()&&x.type()==type).findFirst().orElseThrow();entry(v,o,name);
        check(s.view(p.equals(A)?B:A,p.equals(A)?B:A).options().stream().noneMatch(x->x.enabled()&&x.type()==type),name+": opponent received action");
        s.submit(p,p,v.revision(),o.index());AiCardOperationTest.settle(s,level);
        GeneratedRuleBatch8Test.reject(()->s.submit(p,p,v.revision(),o.index()));
    }
    static void tool(String id,UUID p,boolean bench,AiDifficulty level) {
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=DATA.trainer(id).orElseThrow();
        c.ownFamilies=p.equals(A)?Set.of(PokemonFamily.V):Set.of(PokemonFamily.VSTAR);
        c.defenderFamilies=p.equals(B)?Set.of(PokemonFamily.V):Set.of(PokemonFamily.VSTAR);
        c.ownHp=100;c.defenderHp=100;c.ownPrizes=2;c.defenderPrizes=2;
        var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);
        var host=bench?m.benchPokemonView(p).getFirst():m.activePokemonView(p);
        var card=DynamicPassiveModifierTest.hand(m,p,"trainer");m.playTrainer(p,card.instanceId(),host.pokemonId());
        var s=MatchSession.multiplayer(m,A,B);int hand=m.privateView(p).hand().size();
        submit(s,p,ActionType.TOOL_ABILITY,level,id+"/"+p+"/"+bench+"/"+level);
        check(m.toolActivationOptions(p).isEmpty(),"VSTAR quota not consumed");
        if(id.equals("swsh12_en_156"))check(m.privateView(p).hand().size()==hand+1,"Forest Seal Stone did not complete one-card search");
        else {
            int before=m.privateView(p).prizes().size();m.attack(p,"hit");AiCardOperationTest.settle(s,level);
            check(before-m.privateView(p).prizes().size()==3,"Sky Seal Stone did not grant its extra Prize");
        }
    }
    static void item(String id,UUID p,boolean bench,AiDifficulty level) {
        var m=NonPokemonBatch61Test.fixture(id);if(p.equals(B))m.endTurn(A);
        var card=DynamicPassiveModifierTest.hand(m,p,"trainer");m.playTrainer(p,card.instanceId(),null);
        UUID host=m.benchPokemonView(p).getLast().pokemonId();if(!bench)m.switchActiveByEffect(p,p,host);
        var s=MatchSession.multiplayer(m,A,B);boolean doll=id.equals("sm12_en_197");ActionType type=doll?ActionType.DOLL_RETURN:ActionType.FOSSIL_DISCARD;
        if(doll&&bench){check(s.view(p,p).options().stream().noneMatch(o->o.type()==type),"Bench Doll illegally offered return");return;}
        int prizes=m.privateView(p.equals(A)?B:A).prizes().size();
        submit(s,p,type,level,id+"/"+p+"/"+bench+"/"+level);
        check(m.privateView(p.equals(A)?B:A).prizes().size()==prizes,"Voluntary Item departure awarded a Prize");
        check(doll?m.privateView(p).deck().getFirst().equals(card):m.privateView(p).discard().contains(card),"Item did not reach its printed destination");
    }
    static void discard(String id,UUID p,AiDifficulty level) {
        var m=NonPokemonBatch121Test.game(id);if(p.equals(B))m.endTurn(A);
        var card=DynamicPassiveModifierTest.hand(m,p,id);m.discardHandCardsByEffect(p,p,List.of(card.instanceId()));
        var s=MatchSession.multiplayer(m,A,B);submit(s,p,ActionType.DISCARD_ABILITY,level,id+"/"+p+"/"+level);
        check(m.privateView(p).deck().getFirst().equals(card),"Prehistoric Call failed bottom return");
    }
    static void opponentCopy(String id,AiDifficulty level) {
        var attack=DATA.pokemon(id).orElseThrow().attacks().stream().filter(a->a.id().equals("mimed-games")).findFirst().orElseThrow();
        var m=AiCardOperationTest.attackGame(attack,List.of());var s=MatchSession.multiplayer(m,A,B);
        var v=s.view(A,A);var declared=v.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK&&o.commandId().equals(attack.id())).findFirst().orElseThrow();
        s.submit(A,A,v.revision(),declared.index());
        var chooser=s.view(B,B);check(chooser.options().stream().anyMatch(o->o.type()==ActionType.CHOICE),"Mimed Games did not ask the opponent to choose");
        submit(s,B,ActionType.CHOICE,level,id+"/opponent-copy/"+level);
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.activePokemonView(B).damage()==30,"Opponent-selected attack failed to resolve");
    }
    static void run(String name,Runnable test){cases++;try{test.run();}catch(Exception|AssertionError e){failures.add(name+": "+e);}}
    public static void main(String[] args)throws Exception {
        for(var level:AiDifficulty.values())for(UUID p:List.of(A,B)) {
            for(boolean bench:List.of(false,true)) {
                for(String id:List.of("swsh12_en_156","swsh12-5_en_143"))run(id,()->tool(id,p,bench,level));
                for(String id:List.of("swsh3_en_167","xy4_en_102","sm12_en_197"))run(id,()->item(id,p,bench,level));
            }
            for(String id:NonPokemonBatch121Test.RESTORED.subList(0,3))run(id,()->discard(id,p,level));
        }
        for(var level:AiDifficulty.values())for(String id:List.of("sv4-5_en_031","sv4-5_en_157"))run(id,()->opponentCopy(id,level));
        var report=Map.of("cases",cases,"engineFailures",failures,"entryFailures",entryFailures);
        var out=Path.of(System.getProperty("ptcglive.auditDir","outputs/battle-operation-audit"));Files.createDirectories(out);
        Files.writeString(out.resolve("special-actions.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("BattleSpecialActionTest: cases="+cases+", engineFailures="+failures+", missingEntries="+entryFailures.size());
        check(failures.isEmpty()&&entryFailures.isEmpty(),"Special operation failures: see "+out.resolve("special-actions.json"));
        System.out.println("BattleSpecialActionTest passed");
    }
}
