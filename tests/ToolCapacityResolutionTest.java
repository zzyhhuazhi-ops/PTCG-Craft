import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Capacity cleanup is a mandatory server rule choice, not an opponent-authored card effect. */
public final class ToolCapacityResolutionTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    public static void main(String[] args) {
        lossOfAbility(); sourceRemovalAndPrivacy(); conditionAndAttack(); interruptedProgram(); trainerContinuation(); prohibitionIsNotRetention();
        System.out.println("ToolCapacityResolutionTest passed: mandatory owner choice, atomic rejection and rule resume");
    }
    static AbilityDefinition capacity(int amount) {
        return new AbilityDefinition("capacity","工具容量验证",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_OWN_TOOL_CAPACITY,amount),EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE);
    }
    static ServerMatch fixture() {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(capacity(2)));
    }
    static void lossOfAbility() {
        var m=fixture();UUID host=m.activePokemonView(A).pokemonId();var cards=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).limit(2).toList();
        for(var c:cards)m.playTrainer(A,c.instanceId(),host);
        m.disableAbilityEffectsByEffect(A,A,host);
        var session=MatchSession.multiplayer(m,A,B);var view=session.view(A,A);
        var choices=view.options().stream().filter(o->o.labelZh().startsWith("弃置超量工具：")).toList();
        check(choices.size()==2,"容量特性失效后仍保留超量工具，没有服务器选择");
        check(m.activePokemonView(A).attachedTools().size()==2,"尚未选择便自动丢弃工具");
        reject(()->m.endTurn(A));reject(()->m.attack(A,"hit"));
        reject(()->session.submit(B,A,view.revision(),choices.getFirst().index()));
        session.submit(A,A,view.revision(),choices.getFirst().index());
        check(m.activePokemonView(A).attachedTools().size()==1&&m.privateView(A).discard().contains(cards.getFirst()),"选择未按实体工具移入原主弃牌区");
        reject(()->session.submit(A,A,view.revision(),choices.getFirst().index()));
        m.endTurn(A);check(m.currentPlayer().equals(B),"清理工具后没有恢复普通回合");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void attachTwo(ServerMatch m,UUID owner) {
        UUID host=m.activePokemonView(owner).pokemonId();
        for(var card:m.privateView(owner).hand().stream().filter(c->c.definitionId().equals("tool")).limit(2).toList())
            m.attachToolByEffect(A,owner,EffectSourceZone.HAND,card.instanceId(),owner,host);
    }
    static void clear(ServerMatch m) {
        int safety=24;
        while(m.toolOverflowPrompt()!=null&&safety-->0){var p=m.toolOverflowPrompt();m.discardOverflowTool(p.owner(),p.id(),p.tools().getFirst().instanceId());}
        check(safety>0,"清理没有收敛");
    }
    static void sourceRemovalAndPrivacy() {
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"tool_capacity\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":2}");
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium);
        m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);attachTwo(m,B);m.discardStadiumByEffect(A);
        var prompt=m.toolOverflowPrompt();check(prompt!=null&&prompt.owner().equals(B)&&m.pendingChoicePlayer().equals(B),"非当前玩家容量下降没有交还工具拥有者");
        var s=MatchSession.multiplayer(m,A,B);UUID watcher=UUID.randomUUID();s.addSpectator(watcher);var random=m.randomTranscript();
        for(UUID viewer:List.of(A,B,watcher)) {
            var view=s.view(viewer,viewer.equals(watcher)?null:viewer);String json=new com.google.gson.Gson().toJson(view);
            check(view.game().toolOverflow().equals(prompt),"公开清理提示不同步");
            check(dev.hua.ptcglive.view.EffectCardPage.from(view,0).cards().size()==2,"工具没有作为卡图候选展示");
            if(!viewer.equals(B))check(view.options().stream().noneMatch(o->o.labelZh().startsWith("弃置超量工具：")),"非拥有者收到清理意图");
            for(UUID owner:List.of(A,B)) {
                var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());
                if(!viewer.equals(owner))hidden.addAll(m.privateView(owner).hand());
                for(var card:hidden)check(!json.contains(card.instanceId().toString()),"容量提示泄露隐藏区域");
            }
        }
        check(random.equals(m.randomTranscript()),"读取容量提示消耗随机");
        reject(()->m.discardOverflowTool(A,prompt.id(),prompt.tools().getFirst().instanceId()));
        reject(()->m.discardOverflowTool(B,prompt.id(),m.privateView(B).hand().getFirst().instanceId()));
        clear(m);check(m.currentPlayer().equals(A)&&m.activePokemonView(B).attachedTools().size()==1,"处理别人工具时推进了当前回合");

        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2}");
        var n=DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium());attachTwo(n,A);n.disableToolEffectsByEffect(A,A,n.activePokemonView(A).pokemonId());
        check(n.toolOverflowPrompt()!=null,"关闭扩容工具后仍保留两张");clear(n);
        var energy=DynamicPassiveModifierTest.energy("{\"type\":\"tool_capacity\",\"amount\":2}");
        var e=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),energy,DynamicPassiveModifierTest.attack(40,false,false));
        UUID host=e.activePokemonView(A).pokemonId();var card=DynamicPassiveModifierTest.hand(e,A,"special");e.attachEnergy(A,card.instanceId(),host);attachTwo(e,A);
        e.returnAttachedEnergyToHandByEffect(A,A,host,List.of(card.instanceId()));check(e.toolOverflowPrompt()!=null,"扩容能量离场未清理");clear(e);
    }
    static void conditionAndAttack() {
        var basicOnly=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":[{\"type\":\"IS_BASIC\"}]}");
        var m=DynamicPassiveModifierTest.fixture(basicOnly,DynamicPassiveModifierTest.plainStadium());attachTwo(m,A);
        m.evolve(A,m.activePokemonView(A).pokemonId(),DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId());check(m.toolOverflowPrompt()!=null,"进化后保留基础限定容量");clear(m);
        var hp=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":[{\"type\":\"HP_ABOVE\",\"value\":290}]}");
        var n=DynamicPassiveModifierTest.fixture(hp,DynamicPassiveModifierTest.plainStadium());attachTwo(n,B);int turn=n.turnNumber(),hand=n.privateView(B).hand().size();n.attack(A,"hit");
        check(n.toolOverflowPrompt()!=null&&n.currentPlayer().equals(A)&&n.turnNumber()==turn,"攻击导致容量下降后抢先开始下一回合");
        clear(n);check(n.currentPlayer().equals(B)&&n.turnNumber()==turn+1&&n.privateView(B).hand().size()==hand+1,"攻击清理后回合或抽牌推进次数错误");
        var p=DynamicPassiveModifierTest.fixture(hp,DynamicPassiveModifierTest.plainStadium());attachTwo(p,B);
        p.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);p.endTurn(A);
        check(p.phase()==MatchPhase.POKEMON_CHECKUP&&p.toolOverflowPrompt()!=null,"回合间容量下降没有暂停");clear(p);
        check(p.phase()==MatchPhase.TURN&&p.currentPlayer().equals(B),"回合间清理未恢复");
    }
    static void interruptedProgram() {
        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":[{\"type\":\"DAMAGE_COUNTER_COUNT\",\"value\":1,\"comparison\":\">=\"}]}");
        var heal=ResumableAbilityTest.ability("repair","{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10},{\"type\":\"draw\",\"amount\":1}");
        var m=DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(heal));
        m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),1);attachTwo(m,A);int hand=m.privateView(A).hand().size();
        m.useAbility(A,m.activePokemonView(A).pokemonId(),"repair");
        check(m.toolOverflowPrompt()!=null&&m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==hand,"规则暂停没有截住组合程序后续抽牌");
        clear(m);check(!m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==hand+1&&m.activePokemonView(A).damage()==0,"恢复游标重复或跳过了效果");
    }
    static void trainerContinuation() {
        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":[{\"type\":\"DAMAGE_COUNTER_COUNT\",\"value\":1}]}");
        var item=BundledCombatData.trainer(com.google.gson.JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[{\"type\":\"heal\",\"target\":\"OWN_ACTIVE\",\"amount\":10},{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(),0,item);
        m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),1);attachTwo(m,A);var played=DynamicPassiveModifierTest.hand(m,A,"item");int hand=m.privateView(A).hand().size();
        m.playTrainer(A,played.instanceId(),null);check(m.toolOverflowPrompt()!=null&&m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==hand-1&&!m.privateView(A).discard().contains(played),"无显式Choice的训练家未暂停或提前弃置");
        var prompt=m.toolOverflowPrompt();clear(m);check(m.privateView(A).hand().size()==hand&&m.privateView(A).discard().contains(played)&&!m.hasPendingEffectChoice(),"训练家恢复不完整");
        reject(()->m.discardOverflowTool(A,prompt.id(),prompt.tools().getFirst().instanceId()));
    }
    static void prohibitionIsNotRetention() {
        var deny=new AbilityDefinition("no-new-tools","禁止新附工具",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_POKEMON_TOOLS,0),EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE);
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(deny));
        UUID host=m.activePokemonView(A).pokemonId();m.disableAbilityEffectsByEffect(A,A,host);var card=DynamicPassiveModifierTest.hand(m,A,"tool");m.playTrainer(A,card.instanceId(),host);
        m.endTurn(A);m.endTurn(B);check(m.toolOverflowPrompt()==null&&m.activePokemonView(A).attachedTools().contains(card),"禁止新附工具被错误解释为清空已有工具");
    }
    static void reject(Runnable action){EventTargetBindingTest.reject(action);}
}
