import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Each automatic atomic effect owns a fresh, server-issued public Pokémon choice. */
public final class AutomaticPokemonSelectionTest {
    static final UUID A=EnergyTriggeredAbilityTest.A,B=EnergyTriggeredAbilityTest.B;
    public static void main(String[] args) {
        independentSteps(); namedBindingAcrossAutomaticSteps(); statusTargets(); damageAndCheckup(); chosenKnockout(); staleAndForged(); protectionAndConcede(); nestedAndPreflight();
        System.out.println("AutomaticPokemonSelectionTest passed: independent issued field targets and Active-only status choices");
    }
    static void independentSteps() {
        var ability=EnergyTriggeredAbilityTest.ability("choose","SELF",false,"NONE","""
            {"type":"heal","target":"SELECT_OWN_POKEMON","amount":10},
            {"type":"damage_counter","target":"SELECT_OPPONENT_POKEMON","amount":1},
            {"type":"heal","target":"SELECT_OWN_POKEMON","amount":10},
            {"type":"draw","amount":1}
            """);
        var m=EnergyTriggeredAbilityTest.fixture(List.of(ability),List.of(),null);var session=MatchSession.multiplayer(m,A,B);
        UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId(),opponent=m.benchPokemonView(B).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,active,3);m.placeDamageCountersByEffect(A,A,bench,2);
        int hand=m.privateView(A).hand().size();attach(m);check(m.hasPendingEffectChoice(),"目标选择没有暂停");
        choose(session,A,bench);check(m.benchPokemonView(A).getFirst().damage()==10,"第一步没有选中备战来源");
        choose(session,A,opponent);check(m.benchPokemonView(B).getFirst().damage()==10,"第二步沿用第一步所属方／目标");
        choose(session,A,active);
        check(m.activePokemonView(A).damage()==20&&m.benchPokemonView(A).getFirst().damage()==10,"同一SELECT类型的不同步骤不能独立选择");
        check(!m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==hand,"目标完成没有恢复后续抽牌");
    }
    /** A triggered Ability may name the server-issued target once, then use exactly that incarnation in a later step. */
    static void namedBindingAcrossAutomaticSteps() {
        var ability=EnergyTriggeredAbilityTest.ability("bound","SELF",false,"NONE","""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","bind":"recipient"},
            {"type":"with_pokemon","ref":"recipient","effects":[{"type":"heal","target":"CHOSEN_POKEMON","amount":10}]}
            """);
        var m=EnergyTriggeredAbilityTest.fixture(List.of(ability),List.of(),null);var session=MatchSession.multiplayer(m,A,B);
        UUID bench=m.benchPokemonView(A).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,bench,2);attach(m);
        check(m.hasPendingEffectChoice(),"命名自动目标未进入服务器选择等待");
        choose(session,A,bench);
        check(!m.hasPendingEffectChoice()&&m.benchPokemonView(A).getFirst().damage()==10,"自动特性没有使用已绑定的原登场实例");
        EventTargetBindingTest.reject(()->EnergyTriggeredAbilityTest.fixture(List.of(EnergyTriggeredAbilityTest.ability("missing","SELF",false,"NONE",
            "{\"type\":\"with_pokemon\",\"ref\":\"not_chosen\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}")),List.of(),null));
    }
    static void statusTargets() {
        var ability=EnergyTriggeredAbilityTest.ability("status","SELF",false,"NONE","{\"type\":\"poison\",\"target\":\"SELECT_OPPONENT_POKEMON\"}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(ability),List.of(),null);var session=MatchSession.multiplayer(m,A,B);attach(m);
        var view=session.view(A,A);var options=view.options().stream().filter(o->o.labelZh().startsWith("选择效果目标")).toList();
        check(!dev.hua.ptcglive.view.MatchActionMatcher.hasPokemonChoice(session.view(B,B)),"对手界面获得选择模式");
        check(options.size()==1&&options.getFirst().cardKeys().contains("field:"+m.activePokemonView(B).pokemonId()),"异常状态候选错误包含备战");
        choose(session,A,m.activePokemonView(B).pokemonId());
        check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)&&m.benchPokemonView(B).getFirst().conditions().isEmpty(),"状态选择错误作用于备战");
    }
    static void damageAndCheckup() {
        UUID a=DamageTriggeredAbilityTest.A,b=DamageTriggeredAbilityTest.B;
        var reaction=DamageTriggeredAbilityTest.ability("select",true,false,false,"{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"mode\":\"MINIMUM_HP\",\"amount\":10}");
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(500,""),reaction);m.attack(a,"hit");
        var s=MatchSession.multiplayer(m,a,b);check(m.pendingChoicePlayer().equals(b)&&m.activePokemonView(b).damage()==500,"受伤选择没有交给拥有者或过早KO");
        choose(s,b,m.activePokemonView(b).pokemonId());check(m.turnNumber()==4&&m.activePokemonView(b).damage()==290&&!m.lastAttack().knockedOut(),"逐步目标回复没有保留致命伤来源");
        var checkup=CheckupTriggeredAbilityTest.ability("select",false,"{\"type\":\"damage_counter\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":1}");
        var c=CheckupTriggeredAbilityTest.fixture(List.of(checkup),List.of());c.endTurn(A);CheckupTriggeredAbilityTest.choose(c,"select");
        UUID controller=UUID.randomUUID();var practice=MatchSession.practice(c,controller,A,B);var view=practice.view(controller,A);
        var option=view.options().stream().filter(o->o.labelZh().startsWith("选择效果目标")&&o.cardKeys().contains("field:"+c.benchPokemonView(B).getFirst().pokemonId())).findFirst().orElseThrow();
        check(practice.view(controller,B).options().stream().noneMatch(o->o.labelZh().startsWith("选择效果目标")),"练习错误席位能代选目标");
        practice.submit(controller,A,view.revision(),option.index());
        check(c.turnNumber()==2&&c.benchPokemonView(B).getFirst().damage()==10,"回合间目标步骤没有推进且只结算一次");
    }
    static void chosenKnockout() {
        UUID a=DamageTriggeredAbilityTest.A,b=DamageTriggeredAbilityTest.B;
        var reaction=KnockoutTriggeredAbilityTest.ability("select","OPPONENT_ATTACK_DAMAGE",true,false,"{\"type\":\"knock_out\",\"target\":\"SELECT_OPPONENT_POKEMON\"}");
        var m=DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(300,""),reaction);m.attack(a,"hit");
        choose(MatchSession.multiplayer(m,a,b),b,m.benchPokemonView(a).getFirst().pokemonId());
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(a).discard().size()==1&&m.privateView(b).discard().size()==1,"所选直接击倒未加入同一KO检查点");
    }
    static void staleAndForged() {
        for(boolean status:List.of(false,true)) {
            String effect=status?"{\"type\":\"poison\",\"target\":\"SELECT_OPPONENT_POKEMON\"}":"{\"type\":\"damage_counter\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":1}";
            var m=EnergyTriggeredAbilityTest.fixture(List.of(EnergyTriggeredAbilityTest.ability("bad","SELF",false,"NONE",effect)),List.of(),null);attach(m);
            var prompt=m.effectPokemonPrompt(A);var random=m.randomTranscript();var log=m.replayTranscript();UUID target=prompt.targets().getFirst().pokemonId();
            EventTargetBindingTest.reject(()->m.effectPokemonPrompt(B));
            EventTargetBindingTest.reject(()->m.chooseEffectPokemon(B,prompt.resolutionId(),prompt.step(),target));
            EventTargetBindingTest.reject(()->m.chooseEffectPokemon(A,UUID.randomUUID(),prompt.step(),target));
            EventTargetBindingTest.reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step()+1,target));
            EventTargetBindingTest.reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),m.activePokemonView(A).pokemonId()));
            EventTargetBindingTest.reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),m.privateView(B).hand().getFirst().instanceId()));
            check(random.equals(m.randomTranscript())&&log.equals(m.replayTranscript()),"非法目标改变随机或回放状态");
            var f=EventTargetBindingTest.field(m);f.switchActiveByEffect(B,f.benchView(B).getFirst().pokemonId());
            if(!status) {var removed=f.removePokemonByEffect(B,target);f.placeOnBench(B,removed.discardedCards().getFirst());}
            EventTargetBindingTest.reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),target));
            check(m.effectPokemonPrompt(A).targets().stream().noneMatch(t->t.pokemonId().equals(target)),"刷新把移至备战／重入场的新实例冒充旧目标");
            m.concede(A);check(!m.hasPendingPokemonSelection()&&!m.hasPendingEffectChoice(),"认输没有清理目标等待");
        }
    }
    static void protectionAndConcede() {
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","LEAVES_PLAY"));
        var reaction=EnergyTriggeredAbilityTest.ability("reflect","OPPONENT_FIELD",false,"NONE","{\"type\":\"damage_counter\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":2}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(guard),List.of(reaction),null);UUID target=m.activePokemonView(A).pokemonId();
        m.useAbility(A,target,"guard");attach(m);
        var order=m.triggerOrderPrompt();m.chooseTriggeredEffect(A,order.id(),order.entries().getFirst().id());
        choose(MatchSession.multiplayer(m,A,B),B,target);
        check(m.activePokemonView(A).damage()==0,"选择目标步骤丢失ABILITY来源免疫");
        m.concede(A);check(!m.hasPendingEffectChoice()&&!m.hasPendingPokemonSelection(),"第二个来源等待时认输未清理");
    }
    static void attach(ServerMatch m) {
        var energy=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergy(A,energy.instanceId(),m.activePokemonView(A).pokemonId());
    }
    static void nestedAndPreflight() {
        var ability=EnergyTriggeredAbilityTest.ability("nested","SELF",false,"NONE","""
            {"type":"coin_flip","flips":1,"effects":[{"type":"repeat","count":2,"effects":[
                {"type":"choice","effects":[
                    {"type":"damage_counter","target":"SELECT_OPPONENT_POKEMON","amount":1},
                    {"type":"draw","amount":1}]},
                {"type":"conditional","conditions":[{"type":"COIN_RESULT","value":"HEADS"}],"effects":[{"type":"draw","amount":1}]}]}]}
            """);
        var m=EnergyTriggeredAbilityTest.fixture(List.of(ability),List.of(),null);attach(m);
        var s=MatchSession.multiplayer(m,A,B);var random=m.randomTranscript();int hand=m.privateView(A).hand().size();long lastStep=0;int once=0;
        for(int n=0;n<2;n++) {
            var choice=m.effectChoicePrompt(A);check(choice.step()>lastStep,"嵌套目标恢复复用了步骤号");
            m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
            var prompt=m.effectPokemonPrompt(A);check(prompt.step()>choice.step(),"目标步骤未与方案选择分开");lastStep=prompt.step();
            UUID target=n==0?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
            choose(s,A,target);if(n==0)once=m.privateView(A).hand().size()-hand;
        }
        check(!m.hasPendingEffectChoice()&&m.activePokemonView(B).damage()==10&&m.benchPokemonView(B).getFirst().damage()==10,"重复嵌套错误沿用上一目标");
        check(random.equals(m.randomTranscript())&&m.privateView(A).hand().size()==hand+2*once,"选目标暂停丢失外层硬币或重复掷币");
        for(String invalid:List.of(
            "{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":7}",
            "{\"type\":\"knock_out\",\"target\":\"SELECT_OPPONENT_POKEMON\"}",
            "{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":10,\"conditions\":[{\"type\":\"IS_BASIC\",\"target\":\"SELECT_OWN_POKEMON\"}]}")) {
            // Even an unchosen branch must fail before a valid drawing prefix can commit.
            EventTargetBindingTest.reject(()->EnergyTriggeredAbilityTest.fixture(List.of(EnergyTriggeredAbilityTest.ability("invalid","SELF",false,"NONE",
                "{\"type\":\"draw\",\"amount\":1},{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},"+invalid+"]}")),List.of(),null));
        }
    }
    static void choose(MatchSession session,UUID actor,UUID target) {
        var view=session.view(actor,actor);var option=view.options().stream().filter(o->o.labelZh().startsWith("选择效果目标")&&o.cardKeys().contains("field:"+target)).findFirst().orElseThrow();
        var clicks=dev.hua.ptcglive.view.MatchActionMatcher.forCards(view,List.of("pokemon:choose","field:"+target));
        check(dev.hua.ptcglive.view.MatchActionMatcher.hasPokemonChoice(view),"卡桌未进入宝可梦点选模式");
        check(clicks.size()==1&&clicks.getFirst().index()==option.index(),"卡桌没有服务器发行的独立点选锚点");
        session.submit(actor,actor,view.revision(),option.index());EventTargetBindingTest.reject(()->session.submit(actor,actor,view.revision(),option.index()));
    }
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
