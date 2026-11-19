import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Same selected incarnation across steps and phases; synthetic data, no per-card implementation. */
public final class AttackPokemonBindingsTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static String select(String name,String side,String position,String body){return "{\"type\":\"select_pokemon\",\"target\":\"SELECT_"+side+"_POKEMON\",\"position\":\""+position+"\",\"bind\":\""+name+"\",\"effects\":["+body+"]}";}
    static String use(String name,String body){return use(name,"ANY",body);}
    static String use(String name,String position,String body){return "{\"type\":\"with_pokemon\",\"ref\":\""+name+"\",\"position\":\""+position+"\",\"effects\":["+body+"]}";}
    static String counters(int n){return "{\"type\":\"damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":"+n+"}";}
    static void check(boolean b,String s){BeforeDamageAttackTest.check(b,s);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    static void choose(ServerMatch m,UUID target){AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,A,B),A,target);}
    public static void main(String[] args){
        acrossPhases();invalidBeforePrefix();switchedAndReentered(false);switchedAndReentered(true);nestedReferences();
        missingAndReset();exclusiveBranches();conditionsAfterDeparture();energyFacts();otherEntrypoints();copyAndCoin();statusPosition();knockoutAndCleanup();phaseAndRepeat();
        System.out.println("AttackPokemonBindingsTest passed: named incarnation, staged facts, nested scopes, choices, privacy and fail-closed validation");
    }
    static void acrossPhases(){
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","BENCH",counters(2)),
            use("victim","{\"type\":\"damage_per_damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}"),
            AttackCoinBindingsTest.SEARCH+","+use("victim",counters(3)));
        var m=BeforeDamageAttackTest.fixture(a);UUID target=m.benchPokemonView(B).getFirst().pokemonId(),targetPhysical=PokemonIdentityTestSupport.physical(m,B,target);m.attack(A,a.id());choose(m,target);
        check(m.activePokemonView(B).damage()==120&&m.benchPokemonView(B).getFirst().damage()==20,"伤害阶段没有读取前一步同一目标指示物");
        BeforeDamageAttackTest.selectFirst(m,A);
        check(m.benchPokemonView(B).getFirst().damage()==50&&m.currentPlayer().equals(B),"伤害后未引用原目标或未推进回合");
    }
    static void invalidBeforePrefix(){
        String bind=select("victim","OPPONENT","BENCH","");
        for(var a:List.of(BeforeDamageAttackTest.attack("{\"type\":\"draw\",\"amount\":1}","",use("missing",counters(1))),
            BeforeDamageAttackTest.attack(bind+","+bind,"",""),
            BeforeDamageAttackTest.attack(bind,"",use("victim","ACTIVE","{\"type\":\"confuse\",\"target\":\"CHOSEN_POKEMON\"}")))){
            var m=BeforeDamageAttackTest.fixture(a);var view=m.clientView(A);var rng=m.randomTranscript();
            reject(()->m.attack(A,a.id()));check(view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"非法绑定在拒绝前执行前缀");
        }
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("{\"type\":\"repeat\",\"count\":2,\"effects\":["+bind+"]}","","")));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("{\"type\":\"choice\",\"effects\":["+bind+","+use("victim",counters(1))+"]}","","")));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(bind,"",use("typo",counters(1)))));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("",use("victim","{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}"),bind)));
        reject(()->BeforeDamageAttackTest.attack(bind.replace("victim","Bad.Name"),"",""));
        var many=new ArrayList<String>();for(int i=0;i<65;i++)many.add(bind.replace("victim","slot"+i));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(String.join(",",many),"","")));
        // Binding a target does not authorize replacing the unified primary damage destination.
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(bind,use("victim","{\"type\":\"damage\",\"target\":\"CHOSEN_POKEMON\",\"amount\":20}"),"")));
    }
    static final String CHOICE="{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}";
    static void option(ServerMatch m,int index){var p=m.effectChoicePrompt(A);m.chooseEffectOption(A,p.resolutionId(),p.step(),index);}
    static void switchedAndReentered(boolean reenter){
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","ACTIVE","")+","+CHOICE,
            use("victim","{\"type\":\"damage_per_damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}"),use("victim",counters(3)));
        var m=BeforeDamageAttackTest.fixture(a);UUID old=m.activePokemonView(B).pokemonId(),physical=PokemonIdentityTestSupport.physical(m,B,old);m.placeDamageCountersByEffect(A,B,old,2);m.attack(A,a.id());choose(m,old);
        // Server-only fixture transition while choices are locked to ordinary players.
        var f=EventTargetBindingTest.field(m);f.switchActiveByEffect(B,f.benchView(B).getFirst().pokemonId());
        if(reenter){var removed=f.removePokemonByEffect(B,old);f.placeOnBench(B,removed.discardedCards().getFirst());}
        option(m,0);
        check(m.activePokemonView(B).damage()==(reenter?80:120)&&f.pokemonView(B,PokemonIdentityTestSupport.logical(m,B,physical)).damage()==(reenter?0:50),"引用追踪了新战斗位或同卡重新登场");
    }
    static void nestedReferences(){
        var a=BeforeDamageAttackTest.attack(select("first","OPPONENT","BENCH","")+","+select("second","OWN","BENCH",""),"",
            use("first",counters(1)+","+use("second",counters(2))+","+counters(3)));
        var m=BeforeDamageAttackTest.fixture(a);m.attack(A,a.id());choose(m,m.benchPokemonView(B).getFirst().pokemonId());choose(m,m.benchPokemonView(A).getFirst().pokemonId());
        check(m.benchPokemonView(B).getFirst().damage()==40&&m.benchPokemonView(A).getFirst().damage()==20,"内层引用污染外层作用域");
    }
    static void missingAndReset(){
        String binding=select("victim","OPPONENT","BENCH","");
        String sometimes="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HP_ABOVE\",\"value\":290}],\"effects\":["+binding+"]}";
        var a=BeforeDamageAttackTest.attack(sometimes,"",use("victim",counters(2))+",{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":2}");
        var m=BeforeDamageAttackTest.fixture(a);m.attack(A,a.id());choose(m,m.benchPokemonView(B).getFirst().pokemonId());m.endTurn(B);m.attack(A,a.id());
        check(m.benchPokemonView(B).getFirst().damage()==20&&!m.hasPendingPokemonSelection(),"下一次攻击继承旧目标或缺失绑定仍选择");
        var empty=BeforeDamageAttackTest.fixture(a);EventTargetBindingTest.field(empty).removePokemonByEffect(B,empty.benchPokemonView(B).getFirst().pokemonId());
        empty.attack(A,a.id());check(empty.currentPlayer().equals(B)&&empty.activePokemonView(B).damage()==80,"无候选未跳过命名目标作用域");
    }
    static void exclusiveBranches(){
        var a=BeforeDamageAttackTest.attack("{\"type\":\"choice\",\"effects\":["+select("victim","OPPONENT","BENCH","")+","+select("victim","OWN","BENCH","")+"]}","",use("victim",counters(2)));
        for(int branch=0;branch<2;branch++){
            var m=BeforeDamageAttackTest.fixture(a);m.attack(A,a.id());option(m,branch);UUID owner=branch==0?B:A;choose(m,m.benchPokemonView(owner).getFirst().pokemonId());
            check(m.benchPokemonView(owner).getFirst().damage()==20,"互斥分支无法独立产生目标");
        }
        var conflicting=BeforeDamageAttackTest.attack("{\"type\":\"choice\",\"effects\":["+select("victim","OPPONENT","ACTIVE","")+","+select("victim","OPPONENT","BENCH","")+"]}","",use("victim","ACTIVE","{\"type\":\"confuse\",\"target\":\"CHOSEN_POKEMON\"}"));
        reject(()->AttackProgramRules.validate(conflicting));
    }
    static void conditionsAfterDeparture(){
        String cond="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"NOT\",\"conditions\":[{\"type\":\"IS_ACTIVE\",\"target\":\"CHOSEN_POKEMON\"}]}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":100}]}";
        String or=cond.replace("\"NOT\"","\"OR\"").replace("\"target\":\"CHOSEN_POKEMON\"}]","\"target\":\"CHOSEN_POKEMON\"},{\"type\":\"IS_BASIC\"}]").replace("\"amount\":100","\"amount\":10");
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","BENCH","")+","+CHOICE,use("victim",cond+","+or),use("victim",counters(2)+",{\"type\":\"draw\",\"amount\":1}"));
        var m=BeforeDamageAttackTest.fixture(a);UUID old=m.benchPokemonView(B).getFirst().pokemonId();m.attack(A,a.id());choose(m,old);var f=EventTargetBindingTest.field(m);f.removePokemonByEffect(B,old);int hand=m.privateView(A).hand().size();option(m,0);
        check(m.activePokemonView(B).damage()==100&&m.privateView(A).hand().size()==hand+2,"旧对象条件变成真，或无关玩家后续抽牌被跳过");
    }
    static void energyFacts(){
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","BENCH",""),
            use("victim","{\"type\":\"damage_per_energy\",\"target\":\"CHOSEN_POKEMON\",\"energy_type\":\"WATER\",\"amount\":10}"),"");
        var m=TypedEnergyCountTest.fixture(a);UUID target=m.benchPokemonView(B).getFirst().pokemonId();TypedEnergyCountTest.attach(m,B,target,"special");m.attack(A,a.id());choose(m,target);
        check(m.activePokemonView(B).damage()==120,"命名目标能量计数没有用当前供能单位");
    }
    static void otherEntrypoints(){
        String binding=select("victim","OWN","ANY","");
        for(var trigger:List.of(EffectTrigger.ONCE_PER_TURN,EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ON_POKEMON_CHECKUP)){
            var program=new EffectProgram(List.of(BeforeDamageAttackTest.binding(trigger,"{\"type\":\"draw\",\"amount\":1},"+binding+","+use("victim",counters(1)))));
            if(trigger==EffectTrigger.ON_POKEMON_CHECKUP) reject(()->ResumableProgramRules.validate(program,true,trigger));
            else ResumableProgramRules.validate(program,trigger!=EffectTrigger.ON_CARD_PLAYED,trigger);
            var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",""));var before=m.clientView(A);
            reject(()->m.executeEffectProgramByRule(A,m.activePokemonView(A).pokemonId(),program,trigger));check(before.equals(m.clientView(A)),"非攻击入口部分执行了命名程序");
        }
        reject(()->BeforeDamageAttackTest.attack(binding,"",DelayedPokemonEffectsTest.delayed(use("victim",counters(1)))));
    }
    static void copyAndCoin(){
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","BENCH",AttackCoinBindingsTest.FLIP),AttackCoinBindingsTest.DAMAGE,
            use("victim",AttackCoinBindingsTest.heads(counters(2))));
        var m=CopyAttackTest.fixture(a);CopyAttackTest.pay(m);int n=m.randomTranscript().size();CopyAttackTest.copy(m,CopyAttackTest.choice(m),List.of(),List.of());
        AutomaticPokemonSelectionTest.choose(MatchSession.multiplayer(m,CopyAttackTest.A,CopyAttackTest.B),CopyAttackTest.A,m.benchPokemonView(CopyAttackTest.B).getFirst().pokemonId());
        boolean head=m.randomTranscript().get(n).result()==0;
        check(m.randomTranscript().size()==n+1&&m.activePokemonView(CopyAttackTest.B).damage()==(head?120:80)&&m.benchPokemonView(CopyAttackTest.B).getFirst().damage()==(head?20:0),"复制攻击未共用目标和硬币上下文");
    }
    static void statusPosition(){
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","ACTIVE","")+","+CHOICE,"",use("victim","ACTIVE","{\"type\":\"confuse\",\"target\":\"CHOSEN_POKEMON\"}"));
        for(boolean switched:List.of(false,true)){
            var m=BeforeDamageAttackTest.fixture(a);UUID old=m.activePokemonView(B).pokemonId();m.attack(A,a.id());choose(m,old);
            if(switched)EventTargetBindingTest.field(m).switchActiveByEffect(B,m.benchPokemonView(B).getFirst().pokemonId());option(m,0);
            check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==!switched&&m.benchPokemonView(B).stream().allMatch(p->p.conditions().isEmpty()),"状态错误施加给新战斗位或备战位");
        }
    }
    static void knockoutAndCleanup(){
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","BENCH",""),"",use("victim","{\"type\":\"knock_out\",\"target\":\"CHOSEN_POKEMON\"}"));
        var m=BeforeDamageAttackTest.fixture(a);UUID target=m.benchPokemonView(B).getFirst().pokemonId(),targetPhysical=PokemonIdentityTestSupport.physical(m,B,target);m.attack(A,a.id());choose(m,target);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(B).discard().stream().anyMatch(c->c.instanceId().equals(targetPhysical)),"命名目标直接击倒绕过弃牌或拿奖检查点");
        m.takePrizeAt(A,0);check(m.currentPlayer().equals(B)&&m.privateView(A).prizes().size()==5,"命名目标击倒后多推进或奖赏错误");
        var pending=BeforeDamageAttackTest.fixture(a);pending.attack(A,a.id());pending.concede(A);
        check(pending.phase()==MatchPhase.FINISHED&&!pending.hasPendingPokemonSelection(),"认输后命名选择仍能继续");
    }
    static void phaseAndRepeat(){
        var a=BeforeDamageAttackTest.attack(select("victim","OPPONENT","BENCH",counters(2)),
            use("victim","{\"type\":\"damage_per_damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}"),
            "{\"type\":\"repeat\",\"count\":2,\"effects\":["+use("victim",counters(1))+"]}");
        var reversed=new ArrayList<>(a.effectProgram().triggers());Collections.reverse(reversed);
        a=a.withEffectProgram(new EffectProgram(reversed));var m=BeforeDamageAttackTest.fixture(a);int turn=m.turnNumber();
        m.attack(A,a.id());choose(m,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.activePokemonView(B).damage()==120&&m.benchPokemonView(B).getFirst().damage()==40
            &&m.turnNumber()==turn+1&&!m.hasPendingPokemonSelection(),"JSON阶段倒序或重复引用导致重选、错序或重复推进");
    }
}
