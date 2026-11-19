import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Paired, fixed-seed mirror matches. No claim of human tournament strength. */
public final class AiDifficultyBenchmark {
    static final UUID A=AiPracticeTest.A,B=AiPracticeTest.B;
    public static void main(String[] args)throws Exception{
        AiPracticeTest.cards=BundledRules.loadCardDefinitions();AiPracticeTest.combat=BundledCombatData.load();AiPracticeTest.rules=BundledRules.loadRulesets();
        var rows=new ArrayList<Map<String,Object>>();var summaries=new ArrayList<Map<String,Object>>();
        var levels=AiDifficulty.values();
        for(int i=1;i<levels.length;i++){
            int wins=0,losses=0,draws=0;
            for(var deck:AiPresets.all())for(long seed:new long[]{7,19,43,71})for(boolean strongerFirst:List.of(true,false)){
                var row=play(deck,seed,strongerFirst,levels[i-1],levels[i]);rows.add(row);
                String result=(String)row.get("result");if(result.equals("win"))wins++;else if(result.equals("loss"))losses++;else draws++;
            }
            var summary=Map.<String,Object>of("higher",levels[i],"lower",levels[i-1],"wins",wins,"losses",losses,"draws",draws,"games",wins+losses+draws);
            summaries.add(summary);System.out.println("PAIRED AI "+summary);
        }
        var report=Map.of("method","3 mirror decks × 4 seeds × both first-player assignments per adjacent difficulty pair; A always starts; no hidden-state access", "summaries",summaries,"games",rows,"humanStrengthValidated",false);
        java.nio.file.Files.writeString(java.nio.file.Path.of("outputs/ai-difficulty-benchmark.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("AiDifficultyBenchmark passed: "+rows.size()+" games; results are measurements, not a monotonic-strength assertion");
    }
    static Map<String,Object> play(AiPresets.Preset preset,long seed,boolean strongerFirst,AiDifficulty low,AiDifficulty high){
        var da=AiPracticeTest.deck(preset,A);var db=AiPracticeTest.deck(preset,B);
        var game=new ServerMatch(new UUID(seed,strongerFirst?1:2),A,B,da,db,AiPracticeTest.rules.get(preset.rule()),AiPracticeTest.cards,AiPracticeTest.combat,new ServerRandom(seed));
        OpeningTestSupport.decideOrder(game,A,B);
        var session=MatchSession.multiplayer(game,A,B);var used=new HashMap<UUID,Set<String>>();used.put(A,new HashSet<>());used.put(B,new HashSet<>());
        var analyses=Map.of(A,AiDeckAnalysis.analyze(da,AiPracticeTest.cards,AiPracticeTest.combat),B,AiDeckAnalysis.analyze(db,AiPracticeTest.cards,AiPracticeTest.combat));
        int turn=-1,steps=0;long maxNanos=0;UUID strong=strongerFirst?A:B;
        while(game.phase()!=MatchPhase.FINISHED&&steps<2500){
            if(turn!=game.turnNumber()){turn=game.turnNumber();used.values().forEach(Set::clear);}
            boolean moved=false;
            for(UUID seat:List.of(A,B)){
                var input=AiCustomDeckTest.view(session,seat,analyses.get(seat));long start=System.nanoTime();
                var decision=AiPolicy.choose(input,seed*10000+steps,used.get(seat),seat.equals(strong)?high:low);maxNanos=Math.max(maxNanos,System.nanoTime()-start);
                if(decision.isEmpty())continue;
                var d=decision.get();var option=input.snapshot().options().get(d.option());session.submit(seat,seat,d.revision(),d.option());
                var key=AiPolicy.optionalKey(option);if(!key.isEmpty())used.get(seat).add(key);steps++;moved=true;break;
            }
            if(!moved)throw new AssertionError("Benchmark stalled at "+game.phase());
        }
        if(game.result()==null)throw new AssertionError("Benchmark exceeded action budget: "+preset.id()+" "+seed+" "+high);
        String result=game.result().winner()==null?"draw":game.result().winner().equals(strong)?"win":"loss";
        return Map.of("deck",preset.id(),"seed",seed,"higherFirst",strongerFirst,"higher",high,"lower",low,"result",result,"turns",game.turnNumber(),"actions",steps,"maxDecisionMs",maxNanos/1_000_000.0);
    }
}
