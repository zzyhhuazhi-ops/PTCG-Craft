import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Shared server coin results, not separate card-specific attack implementations. */
public final class AttackCoinBindingsTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static final String FLIP="{\"type\":\"coin_flip\",\"flips\":1,\"bind\":\"punch\",\"effects\":[]}";
    static final String SEARCH="{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}";
    static String heads(String body){return "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"coin\":\"punch\",\"value\":\"HEADS\"}],\"effects\":["+body+"]}";}
    static final String DAMAGE=heads("{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}");
    static final String CONFUSE=heads("{\"type\":\"confuse\",\"target\":\"OPPONENT_ACTIVE\"}");
    static void check(boolean b,String s){BeforeDamageAttackTest.check(b,s);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    public static void main(String[] args){
        sameResultAcrossPhases();pausedSelections();malformedBeforeMutation();damagePhaseAndNestedCoins();exclusiveChoices();
        skippedBranchAndReset();otherEntrypoints();copiedAttack();multiCoinConditions();phaseOrdering();confusion();
        selectedPokemonContext();multipleNamesAndReplay();rootConditions();
        System.out.println("AttackCoinBindingsTest passed: shared attack memory, flow validation, pause, copy, missing branches and isolation");
    }
    static void sameResultAcrossPhases(){
        boolean heads=false,tails=false;
        for(int skip=0;skip<20&&!(heads&&tails);skip++){
            var a=BeforeDamageAttackTest.attack(FLIP,DAMAGE,CONFUSE);var m=BeforeDamageAttackTest.fixture(a);
            for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);
            int rng=m.randomTranscript().size();m.attack(A,a.id());
            check(m.randomTranscript().size()==rng+1,"一次攻击共用硬币却重复掷币");
            boolean head=m.randomTranscript().get(rng).result()==0;heads|=head;tails|=!head;
            check(m.activePokemonView(B).damage()==(head?120:80),"绑定结果追加伤害或弱点结算错误");
            check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==head,"伤害和混乱没有共用同一次硬币");
        }
        check(heads&&tails,"未覆盖正反结果");
    }
    static void pausedSelections(){
        var a=BeforeDamageAttackTest.attack(FLIP+","+SEARCH,DAMAGE,SEARCH+","+CONFUSE);var m=BeforeDamageAttackTest.fixture(a);
        int n=m.randomTranscript().size();m.attack(A,a.id());boolean head=m.randomTranscript().get(n).result()==0;
        var p=m.effectCardPrompt(A);var rng=m.randomTranscript();m.clientView(A);m.clientView(B);
        reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));
        BeforeDamageAttackTest.selectFirst(m,A);
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));
        BeforeDamageAttackTest.selectFirst(m,A);
        check(m.activePokemonView(B).damage()==(head?120:80)&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==head,"暂停恢复丢失结果");
        check(m.randomTranscript().stream().filter(e->e.operation().contains("coin")).count()==rng.stream().filter(e->e.operation().contains("coin")).count(),"选择恢复重掷硬币");
    }
    static void malformedBeforeMutation(){
        for(var a:List.of(BeforeDamageAttackTest.attack(FLIP+","+FLIP,DAMAGE,""),BeforeDamageAttackTest.attack("",DAMAGE,FLIP),
            BeforeDamageAttackTest.attack(FLIP,DAMAGE.replace("punch","typo"),""),
            BeforeDamageAttackTest.attack("{\"type\":\"repeat\",\"count\":2,\"effects\":["+FLIP+"]}",DAMAGE,""))){
            var m=BeforeDamageAttackTest.fixture(a);var view=m.clientView(A);var rng=m.randomTranscript();
            reject(()->m.attack(A,a.id()));check(view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"无效跨阶段绑定执行了前缀");
        }
        reject(()->BeforeDamageAttackTest.attack(FLIP.replace("punch","Bad.Name"),"",""));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("{\"type\":\"choice\",\"effects\":["+FLIP+","+CONFUSE+"]}","","")));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("",DAMAGE.replace(",\"coin\":\"punch\"",""),"")));
        var definitions=new ArrayList<String>();for(int i=0;i<65;i++)definitions.add(FLIP.replace("punch","coin"+i));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(String.join(",",definitions),"","")));
    }
    static void damagePhaseAndNestedCoins(){
        var a=BeforeDamageAttackTest.attack("",FLIP+","+DAMAGE,"{\"type\":\"coin_flip\",\"flips\":2,\"effects\":[{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":1}]} ,"+CONFUSE);
        var m=BeforeDamageAttackTest.fixture(a);int n=m.randomTranscript().size();m.attack(A,a.id());boolean head=m.randomTranscript().get(n).result()==0;
        check(m.randomTranscript().size()==n+3&&m.activePokemonView(A).damage()==10,"嵌套未命名硬币行为改变");
        check(m.activePokemonView(B).damage()==(head?120:80)&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==head,"内层硬币覆盖了命名结果");
    }
    static void exclusiveChoices(){
        for(int option=0;option<2;option++){
            var a=BeforeDamageAttackTest.attack("{\"type\":\"choice\",\"effects\":["+FLIP+","+FLIP+"]}",DAMAGE,CONFUSE);
            var m=BeforeDamageAttackTest.fixture(a);int n=m.randomTranscript().size();m.attack(A,a.id());var p=m.effectChoicePrompt(A);
            check(m.randomTranscript().size()==n,"未选择分支就掷币");m.chooseEffectOption(A,p.resolutionId(),p.step(),option);
            boolean head=m.randomTranscript().get(n).result()==0;
            check(m.randomTranscript().size()==n+1&&m.activePokemonView(B).damage()==(head?120:80),"互斥分支错误拒绝定义或重复掷币");
        }
    }
    static void skippedBranchAndReset(){
        String absent="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HP_ABOVE\",\"value\":999}],\"effects\":["+FLIP+"]}";
        String named="{\"type\":\"COIN_RESULT\",\"coin\":\"punch\",\"value\":\"HEADS\"}";
        String not="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"NOT\",\"conditions\":["+named+"]}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":100}]}";
        String or="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"OR\",\"conditions\":["+named+",{\"type\":\"IS_BASIC\"}]}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}";
        var a=BeforeDamageAttackTest.attack(absent,DAMAGE+","+not+","+or,CONFUSE);var m=BeforeDamageAttackTest.fixture(a);var rng=m.randomTranscript();
        m.attack(A,a.id());check(m.activePokemonView(B).damage()==100&&rng.equals(m.randomTranscript())&&!m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"未产生硬币被当作反面，或NOT把缺失反转成功");
        a=BeforeDamageAttackTest.attack(absent.replace("999","290"),DAMAGE,"{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":2}");
        m=BeforeDamageAttackTest.fixture(a);m.attack(A,a.id());m.endTurn(B);int damage=m.activePokemonView(B).damage();rng=m.randomTranscript();m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==damage+80&&rng.equals(m.randomTranscript()),"下一次攻击继承了上次硬币");
    }
    static void otherEntrypoints(){
        for(var trigger:List.of(EffectTrigger.ONCE_PER_TURN,EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ON_POKEMON_CHECKUP)){
            var program=new EffectProgram(List.of(BeforeDamageAttackTest.binding(trigger,"{\"type\":\"draw\",\"amount\":1},"+FLIP)));
            reject(()->ResumableProgramRules.validate(program,trigger!=EffectTrigger.ON_CARD_PLAYED,trigger));
            var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",""));var view=m.clientView(A);var rng=m.randomTranscript();
            reject(()->m.executeEffectProgramByRule(A,m.activePokemonView(A).pokemonId(),program,trigger));
            check(view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"非攻击入口在拒绝前改变了牌或随机数");
        }
        String delay="{\"type\":\"delayed_effect\",\"target\":\"OPPONENT_ACTIVE\",\"when\":\"OPPONENT_NEXT_TURN_END\",\"clear\":\"BENCH_EVOLVE_LEAVE\",\"effects\":[";
        reject(()->BeforeDamageAttackTest.attack(FLIP,"",delay+FLIP+"]}"));
        reject(()->BeforeDamageAttackTest.attack(FLIP,"",delay+"{\"type\":\"coin_flip\",\"flips\":1,\"effects\":["+CONFUSE.replace("OPPONENT_ACTIVE","TRIGGER_POKEMON")+"]}]}"));
        check(EffectExecutionCapabilityCatalog.assess(BeforeDamageAttackTest.binding(EffectTrigger.BEFORE_ATTACK,FLIP).effects().getFirst()).capability()==EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"空子树命名硬币被误标直接可执行");
    }
    static void copiedAttack(){
        var attack=BeforeDamageAttackTest.attack(FLIP,DAMAGE,CONFUSE);var m=CopyAttackTest.fixture(attack);CopyAttackTest.pay(m);var option=CopyAttackTest.choice(m);int n=m.randomTranscript().size();
        CopyAttackTest.copy(m,option,List.of(),List.of());boolean head=m.randomTranscript().get(n).result()==0;
        check(m.randomTranscript().size()==n+1&&m.activePokemonView(CopyAttackTest.B).damage()==(head?120:80)
            &&m.activePokemonView(CopyAttackTest.B).conditions().contains(SpecialCondition.CONFUSED)==head,"复制攻击未使用实际攻击检查点的硬币");
    }
    static void multiCoinConditions(){
        for(int heads=0;heads<=2;heads++){
            var result=new EffectExecutionContext.CoinResult(2,heads);
            check(result.matches("HEADS")== (heads>0)&&result.matches("TAILS")==(heads<2),"旧的至少一个正反面条件改变");
            check(result.matches("ALL_HEADS")== (heads==2)&&result.matches("ALL_TAILS")== (heads==0),"全正或全反条件错误");
        }
        var a=BeforeDamageAttackTest.attack(FLIP.replace("\"flips\":1","\"flips\":2"),DAMAGE.replace("HEADS","ALL_HEADS"),CONFUSE.replace("HEADS","ALL_HEADS"));
        var m=BeforeDamageAttackTest.fixture(a);int n=m.randomTranscript().size();m.attack(A,a.id());boolean both=m.randomTranscript().subList(n,n+2).stream().allMatch(e->e.result()==0);
        check(m.randomTranscript().size()==n+2&&m.activePokemonView(B).damage()==(both?120:80)&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==both,"两枚共用结果错误");
        var memory=new AttackCoinBindings.Memory();memory.put("punch",new EffectExecutionContext.CoinResult(1,1));reject(()->memory.put("punch",new EffectExecutionContext.CoinResult(1,0)));
    }
    static void phaseOrdering(){
        var a=BeforeDamageAttackTest.attack(FLIP,DAMAGE,CONFUSE);var reversed=new ArrayList<>(a.effectProgram().triggers());Collections.reverse(reversed);
        var shuffled=a.withEffectProgram(new EffectProgram(reversed));var m=BeforeDamageAttackTest.fixture(shuffled);int n=m.randomTranscript().size();m.attack(A,a.id());
        check(m.randomTranscript().size()==n+1,"JSON绑定排列改变实际阶段顺序");
    }
    static void confusion(){
        var a=BeforeDamageAttackTest.attack(FLIP,DAMAGE,CONFUSE);boolean pass=false,fail=false;
        for(int seed=0;seed<30&&!(pass&&fail);seed++){
            var m=SharedUsageLimitTest.attackFixture(seed,List.of(),List.of(a));UUID x=SharedUsageLimitTest.A;
            m.applySpecialConditionByEffect(x,x,SpecialCondition.CONFUSED);int n=m.randomTranscript().size();m.attack(x,a.id());
            if(m.lastAttack().selfDamage()==30){fail=true;check(m.randomTranscript().size()==n+1,"混乱失败仍执行命名硬币");}
            else {pass=true;check(m.randomTranscript().size()==n+2,"混乱通过后攻击硬币次数错误");}
        }
        check(pass&&fail,"未覆盖混乱两种结果");
    }
    static void selectedPokemonContext(){
        var a=BeforeDamageAttackTest.attack("{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"effects\":["+FLIP+","+heads("{\"type\":\"damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":1}")+"]}",DAMAGE,CONFUSE);
        var m=BeforeDamageAttackTest.fixture(a);int n=m.randomTranscript().size();m.attack(A,a.id());
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,A,B),A,m.benchPokemonView(A).getFirst().pokemonId());
        boolean head=m.randomTranscript().get(n).result()==0;
        check(m.randomTranscript().size()==n+1&&m.benchPokemonView(A).getFirst().damage()==(head?10:0)&&m.activePokemonView(B).damage()==(head?120:80),"已选目标上下文未共享攻击硬币");
    }
    static void multipleNamesAndReplay(){
        var a=BeforeDamageAttackTest.attack(FLIP+","+FLIP.replace("punch","other"),DAMAGE,CONFUSE+","+heads("{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":1}").replace("punch","other"));
        var first=BeforeDamageAttackTest.fixture(a);var second=BeforeDamageAttackTest.fixture(a);
        int n=first.randomTranscript().size();first.attack(A,a.id());second.attack(A,a.id());
        boolean other=first.randomTranscript().get(n+1).result()==0;
        check(first.randomTranscript().equals(second.randomTranscript())&&first.activePokemonView(B).damage()==second.activePokemonView(B).damage(),"同一服务器随机序列不能复盘");
        check(first.randomTranscript().size()==n+2&&first.activePokemonView(A).damage()==(other?10:0),"不同命名结果串用");
        check(first.replayTranscript().stream().filter(e->e.operation().startsWith("攻击硬币绑定：")).count()==2,"回放未记录命名结果对应关系");
    }
    static void rootConditions(){
        var coin=new ConditionDefinition(ConditionType.COIN_RESULT,Map.of("coin","punch","value","HEADS"),List.of());
        var program=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.BEFORE_ATTACK,FLIP),
            new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(coin),BeforeDamageAttackTest.binding(EffectTrigger.ON_ATTACK,"{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}").effects()),
            new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(coin),BeforeDamageAttackTest.binding(EffectTrigger.AFTER_ATTACK,"{\"type\":\"confuse\",\"target\":\"OPPONENT_ACTIVE\"}").effects())));
        var a=BeforeDamageAttackTest.attack("","","").withEffectProgram(program);var m=BeforeDamageAttackTest.fixture(a);int n=m.randomTranscript().size();m.attack(A,a.id());
        boolean head=m.randomTranscript().get(n).result()==0;
        check(m.activePokemonView(B).damage()==(head?120:80)&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==head,"阶段根条件未读共享硬币");
    }
}
