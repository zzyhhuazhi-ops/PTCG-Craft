import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.util.*;

/** Tests real rules/actions. Automated seats are NOT a Minecraft client acceptance test. */
public final class AiPracticeTest {
    static final UUID A=new UUID(0,14001),B=new UUID(0,14002),C=new UUID(0,14003);
    static CardDatabase cards;static CombatCatalog combat;static Map<String,Ruleset> rules;
    static final Map<String,Integer> coverage=new TreeMap<>();
    static final List<Map<String,Object>> games=new ArrayList<>();static long policyNanos,policyCalls,maxPolicyNanos;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("请求应拒绝");}
    public static void main(String[] args){
        cards=BundledRules.loadCardDefinitions();combat=BundledCombatData.load();rules=BundledRules.loadRulesets();
        var presets=AiPresets.all();check(presets.size()==3,"应有三种不同预设");
        for(var p:presets){var deck=deck(p,A);var report=new DeckValidator(cards,combat).validate(rules.get(p.rule()),deck);check(report.canStartMatch(),p.id()+" 不合法："+report.issues());check(AiSupport.problems(deck,cards,combat).isEmpty(),p.id()+" 选择未支持");}
        for(var p:presets)for(long seed:new long[]{7,19,43})for(boolean first:List.of(true,false))play(p,first?presets.get(0):presets.get(1),seed,first);
        lifecycle();AiPracticeScenarios.run();AiPracticeLifecycle.run();
        System.out.println("AI COVERAGE "+coverage);
        for(var category:List.of("攻击","附能","进化","训练家","特性","领奖","替补"))check(coverage.getOrDefault(category,0)>0,"缺少实际流程："+category);
        for(var phase:List.of("MULLIGAN_DRAW","MULLIGAN_REVEAL","TURN_ORDER"))check(coverage.getOrDefault("phase:"+phase,0)>0,"缺少开局流程："+phase);
        check(coverage.getOrDefault("first:"+A,0)>0&&coverage.getOrDefault("first:"+B,0)>0,"未覆盖双方分别先手");
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("outputs/ai-practice-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("games",games,"coverage",coverage,"policyCalls",policyCalls,"averagePolicyMs",policyNanos/1_000_000.0/policyCalls,"maxPolicyMs",maxPolicyNanos/1_000_000.0,"minecraftClientVerified",false)),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new AssertionError(e);}
        System.out.println("AiPracticeTest passed");
    }
    static List<CardInstance> deck(AiPresets.Preset p,UUID owner){var list=new ArrayList<CardInstance>();p.cards().forEach((id,n)->{for(int i=0;i<n;i++)list.add(new CardInstance(UUID.nameUUIDFromBytes((owner+":"+id+":"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),owner,id));});return list;}
    static final Map<UUID,AiDeckAnalysis> analyses=new HashMap<>();
    static AiView input(MatchSession s,UUID seat){return AiCustomDeckTest.view(s,seat,analyses.getOrDefault(seat,AiDeckAnalysis.EMPTY));}
    static void play(AiPresets.Preset p,AiPresets.Preset other,long seed,boolean first){
        play(p,other,seed,first,AiDifficulty.HARD);
    }
    static void play(AiPresets.Preset p,AiPresets.Preset other,long seed,boolean first,AiDifficulty difficulty){
        var game=new ServerMatch(new UUID(seed,first?1:2),A,B,deck(first?p:other,A),deck(first?other:p,B),rules.get(p.rule()),cards,combat,new ServerRandom(seed));
        var session=MatchSession.multiplayer(game,A,B);session.addSpectator(C);
        analyses.put(A,AiDeckAnalysis.analyze(deck(first?p:other,A),cards,combat));analyses.put(B,AiDeckAnalysis.analyze(deck(first?other:p,B),cards,combat));
        var used=new HashMap<UUID,Set<String>>();used.put(A,new HashSet<>());used.put(B,new HashSet<>());
        int turn=-1,steps=0,attacks=0;var tail=new ArrayDeque<String>();
        try {
            while(game.phase()!=MatchPhase.FINISHED&&steps<2000){
                if(turn!=game.turnNumber()){turn=game.turnNumber();used.values().forEach(Set::clear);}
                boolean moved=false;
                for(UUID seat:List.of(A,B)){
                    var input=input(session,seat);var random=game.randomTranscript();
                    long started=System.nanoTime();var decision=AiPolicy.choose(input,seed*10000+steps,used.get(seat),difficulty);long duration=System.nanoTime()-started;policyNanos+=duration;policyCalls++;maxPolicyNanos=Math.max(maxPolicyNanos,duration);
                    check(random.equals(game.randomTranscript()),"评分消耗了对局 RNG");
                    if(decision.isEmpty())continue;
                    var d=decision.get();var o=input.snapshot().options().get(d.option());
                    check(d.equals(AiPolicy.choose(input,seed*10000+steps,used.get(seat),difficulty).orElseThrow()),"固定种子不复现");
                    tail.add(seat+" T"+game.turnNumber()+" "+o.labelZh());if(tail.size()>14)tail.removeFirst();
                    session.submit(seat,seat,d.revision(),d.option());
                    if(steps==0)rejects(()->session.submit(seat,seat,d.revision(),d.option()));
                    coverage.merge(d.category(),1,Integer::sum);if(d.category().equals("攻击"))attacks++;
                    coverage.merge("phase:"+input.snapshot().game().phase(),1,Integer::sum);
                    if(input.snapshot().game().phase()==MatchPhase.TURN&&input.snapshot().game().turnNumber()==1)coverage.merge("first:"+seat,1,Integer::sum);
                    String key=AiPolicy.optionalKey(o);if(!key.isEmpty())used.get(seat).add(key);
                    steps++;moved=true;break;
                }
                check(moved,"双方没有可执行动作 "+game.phase());
            }
            check(game.phase()==MatchPhase.FINISHED,"超过步骤预算");check(attacks>0,"没有真正攻击的对局不算通过");
            var observer=session.view(C,null);check(observer.game().hand().isEmpty(),"观战手牌泄漏");
            System.out.println("AI GAME preset="+p.id()+" seed="+seed+" seat="+(first?"A":"B")+" turns="+game.turnNumber()+" actions="+steps+" attacks="+attacks+" result="+game.result());
            games.add(Map.of("preset",p.id(),"seed",seed,"seat",first?"A":"B","turns",game.turnNumber(),"actions",steps,"attacks",attacks,"result",game.result().reason().name()));
        }catch(RuntimeException|AssertionError e){throw new AssertionError(p.id()+" seed="+seed+" first="+first+" phase="+game.phase()+"\n"+String.join("\n",tail)+"\nA="+session.view(A,A).options()+"\nB="+session.view(B,B).options(),e);}
    }
    static void lifecycle(){
        var boxes=new CardBoxService(Clock.systemUTC());var p=AiPresets.all().getFirst();var box=boxes.create(A,"AI 测试卡盒",p.rule());
        for(var card:deck(p,A)){boxes.mint(A,card.definitionId());}
        // Test assets belong to the human fixture only; manager never mints the bot preset.
        for(var card:List.copyOf(boxes.unboxedForOwner(A)))boxes.put(A,box.id(),card.instanceId());
        var before=boxes.contentsForValidation(A,box.id());var tables=new CardTableManager(rules,boxes,cards,combat,()->new ServerRandom(7));var key=new CardTableKey("minecraft:overworld",1414);
        tables.open(key,A,p.rule());UUID id=AiPracticeLifecycle.start(tables,key,A,box.id(),box.id(),p.rule());
        check(tables.matchSeats(id,A).equals(List.of(A)),"玩家可控制电脑席位");
        var bot=tables.matchView(id,A,A).computer().seat();rejects(()->tables.matchView(id,A,bot));
        check(boxes.boxesForOwner(bot).isEmpty()&&boxes.unboxedForOwner(bot).isEmpty(),"电脑卡写入了玩家资产");
        rejects(()->boxes.take(A,box.id(),box.contents().getFirst()));
        for(int tick=0;tick<30;tick++)tables.tickComputers(tick);
        var view=tables.matchView(id,A,A);var concede=view.options().stream().filter(o->o.labelZh().startsWith("认输")).findFirst().orElseThrow();
        tables.submit(id,A,A,view.revision(),concede.index());check(tables.activeComputerCount()==0,"结束后仍有 AI 任务");rejects(()->tables.session(id));
        check(tables.matchView(id,A,A).game().result()!=null,"结算视图丢失");check(before.equals(boxes.contentsForValidation(A,box.id())),"真实收藏被改变");
        UUID again=tables.rematchComputer(id,A);check(!again.equals(id),"再战复用了旧局");check(tables.matchView(again,A,A).game().turnNumber()==0,"继承旧回合");
        tables.disconnectPlayer(A);check(tables.activeComputerCount()==0,"断线残留任务");check(tables.tickComputers(1000).isEmpty(),"断线继续行动");
        boxes.take(A,box.id(),box.contents().getFirst());
    }
}
