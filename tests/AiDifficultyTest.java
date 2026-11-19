import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.util.*;

public final class AiDifficultyTest {
    static void check(boolean b,String message){AiPracticeTest.check(b,message);}
    public static void main(String[] args){
        AiPracticeTest.cards=BundledRules.loadCardDefinitions();AiPracticeTest.combat=BundledCombatData.load();AiPracticeTest.rules=BundledRules.loadRulesets();
        decisions();counterattack();configuration();
        for(var difficulty:AiDifficulty.values())for(long seed:new long[]{7,19})
            AiPracticeTest.play(AiPresets.all().get(0),AiPresets.all().get(1),seed,true,difficulty);
        System.out.println("AiDifficultyTest passed: four policies, hidden-zone isolation, bound difficulty token, rematch preservation, 8 complete games");
    }
    static void counterattack(){
        UUID a=BattleExperienceTest.A,b=BattleExperienceTest.B;
        var game=BattleExperienceTest.fixture(null,null,new AttackDefinition("hit","原招式",Map.of(),100,EnergyType.FIRE,EffectSupport.SUPPORTED,null));
        var v=AiPracticeScenarios.view(MatchSession.multiplayer(game,a,b),a);var own=game.activePokemonView(a);var foe=game.activePokemonView(b);
        var aggressive=new AttackDefinition("aggressive","全力攻击",Map.of(),100,EnergyType.FIRE,EffectSupport.SUPPORTED,null);
        var defensive=BundledCombatData.attack(com.google.gson.JsonParser.parseString("{\"id\":\"defensive\",\"nameZh\":\"防守攻击\",\"energyCost\":{},\"baseDamage\":60,\"damageType\":\"FIRE\",\"effectSupport\":\"SUPPORTED\",\"damageReductionFromOpponentNextTurn\":100}").getAsJsonObject(),dev.hua.ptcglive.engine.effect.EffectRegistry.standard());
        var facts=new HashMap<>(v.cards());
        facts.put(own.topCardId(),new AiView.Card(facts.get(own.topCardId()).definition(),new PokemonProfile(500,0,null,null,0,null,0,1,List.of(aggressive,defensive)),null,null));
        facts.put(foe.topCardId(),new AiView.Card(facts.get(foe.topCardId()).definition(),new PokemonProfile(500,0,null,null,0,null,0,1,List.of(new AttackDefinition("reply","反击",Map.of(),500,EnergyType.FIRE,EffectSupport.SUPPORTED,null))),null,null));
        var options=List.of(new MatchSession.Option(0,"攻击：全力攻击",List.of("field:"+own.pokemonId())),new MatchSession.Option(1,"攻击：防守攻击",List.of("field:"+own.pokemonId())),new MatchSession.Option(2,"结束回合"));
        var input=AiPracticeScenarios.changed(v,List.of(),options,facts,null,null);
        check(AiPolicy.choose(input,86,Set.of(),AiDifficulty.NORMAL).orElseThrow().option()==0,"普通档应优先当前伤害");
        check(AiPolicy.choose(input,86,Set.of(),AiDifficulty.MASTER).orElseThrow().option()==1,"大师没有用减伤避免公开反击击倒");
        boolean choseLower=false;for(int seed=0;seed<32;seed++)choseLower|=AiPolicy.choose(input,seed,Set.of(),AiDifficulty.EASY).orElseThrow().option()==1;
        check(choseLower,"简单档意外使用完整伤害优化");
    }
    static void decisions(){
        UUID a=BattleExperienceTest.A,b=BattleExperienceTest.B;
        var game=BattleExperienceTest.fixture(null,null,new AttackDefinition("hit","主攻",Map.of(),300,EnergyType.FIRE,EffectSupport.SUPPORTED,null));
        var session=MatchSession.multiplayer(game,a,b);var v=AiPracticeScenarios.view(session,a);var mon=game.activePokemonView(a);
        var facts=new HashMap<>(v.cards());var grass=new CardInstance(new UUID(85,1),a,"grass");var evo=new CardInstance(new UUID(85,2),a,"evo");
        facts.put("grass",new AiView.Card(AiPracticeScenarios.def("grass",CardCategory.ENERGY,true),null,null,EnergyProfile.basic(EnergyType.GRASS)));
        facts.put("evo",new AiView.Card(AiPracticeScenarios.def("evo",CardCategory.POKEMON,false),new PokemonProfile(550,0,mon.topCardId(),null,0,null,0,1,List.of(new AttackDefinition("later","进化攻击",Map.of(EnergyType.GRASS,2),400,EnergyType.GRASS,EffectSupport.SUPPORTED,null))),null,null));
        var options=List.of(new MatchSession.Option(0,"附着 草 → 主攻手",List.of("hand:"+grass.instanceId(),"field:"+mon.pokemonId())),new MatchSession.Option(1,"攻击：主攻",List.of("field:"+mon.pokemonId())),new MatchSession.Option(2,"结束回合"));
        var input=AiPracticeScenarios.changed(v,List.of(grass,evo),options,facts,null,null);
        var renamed=v.snapshot().options().stream().map(o->new MatchSession.Option(o.index(),o.type()==ActionType.OTHER?o.labelZh():"Display text changed",o.cardKeys(),o.enabled(),o.type(),o.commandId())).toList();
        var relabeled=AiPracticeScenarios.changed(v,v.snapshot().game().hand(),renamed,v.cards(),null,null);
        for(var d:AiDifficulty.values()){
            check(AiPolicy.choose(v,85,Set.of(),d).equals(AiPolicy.choose(relabeled,85,Set.of(),d)),"Display label changed typed AI behavior: "+d);
            var decision=AiPolicy.choose(input,85,Set.of(),d).orElseThrow();
            check(decision.option()==(d.states==0?1:0),"难度没有区分立即攻击与回合资源准备："+d);
            check(decision.equals(AiPolicy.choose(input,85,Set.of(),d).orElseThrow()),"固定种子不复现："+d);
            var before=AiPolicy.choose(AiPracticeScenarios.view(session,a),85,Set.of(),d);
            Collections.reverse(AiPracticeScenarios.zone(game,b,"hand"));Collections.reverse(AiPracticeScenarios.zone(game,b,"deck"));
            check(before.equals(AiPolicy.choose(AiPracticeScenarios.view(session,a),85,Set.of(),d)),"读取隐藏顺序："+d);
        }
        check(AiDifficulty.MASTER.states>AiDifficulty.HARD.states,"大师没有扩大搜索预算");
        AiPracticeTest.rejects(()->AiDifficulty.parse("INVALID"));
    }
    static void configuration(){
        var clock=new BattleExperienceTest.Time();var boxes=new CardBoxService(clock);var tables=CardTableManager.restore(AiPracticeTest.rules,boxes,AiPracticeTest.cards,AiPracticeTest.combat,()->new ServerRandom(19),clock,new CardTableManager.Snapshot(List.of(),List.of(),List.of()));
        UUID a=AiPracticeTest.A;var p=AiPresets.all().getFirst();var box=AiPracticeLifecycle.box(boxes,a,p);
        int pos=8600;
        for(var difficulty:AiDifficulty.values()){
            var key=new CardTableKey("minecraft:overworld",pos++);tables.open(key,a,p.rule());
            var report=tables.checkComputer(key,a,box.id(),box.id(),p.rule(),difficulty);
            check(report.allowed()&&report.difficulty()==difficulty,"检查未保存难度");
            var other=AiDifficulty.values()[(difficulty.ordinal()+1)%4];
            String token=report.token();AiPracticeTest.rejects(()->tables.startComputer(key,a,box.id(),box.id(),p.rule(),token,other));
            report=tables.checkComputer(key,a,box.id(),box.id(),p.rule(),difficulty);
            UUID id=tables.startComputer(key,a,box.id(),box.id(),p.rule(),report.token(),difficulty);
            var view=tables.matchView(id,a,a);
            check(view.computer().toString().contains(difficulty.label()),"对局未标明难度");
            var resign=view.options().stream().filter(o->o.labelZh().startsWith("认输")).findFirst().orElseThrow();tables.submit(id,a,a,view.revision(),resign.index());
            UUID again=tables.rematchComputer(id,a);
            check(tables.matchView(again,a,a).computer().toString().contains(difficulty.label()),"再战遗失难度");
            tables.removePhysicalTable(key);
        }
    }
}
