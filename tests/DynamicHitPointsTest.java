import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic data tests common HP rules, not additional audited real-card bindings. */
public final class DynamicHitPointsTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static String hp(int n) {return "{\"type\":\"hit_points_modifier\",\"amount\":"+n+"}";}
    static TrainerProfile tool(int n) {return DynamicPassiveModifierTest.trainer("TOOL",hp(n));}
    static ServerMatch fixture() {return DynamicPassiveModifierTest.fixture(tool(100),DynamicPassiveModifierTest.plainStadium());}
    static CardInstance hand(ServerMatch m,UUID p,String id) {return DynamicPassiveModifierTest.hand(m,p,id);}
    static void check(boolean b,String s) {if(!b)throw new AssertionError(s);}
    public static void main(String[] args) {
        liveHpAndConditions(); sourceLoss(); energyAndStadium(); evolution(); damageAndCheckup(); overflow(); validation();
        sourceKnockoutCascade(); composedCheckpoint(); energyDependency(); perAttachedEnergy(); passiveAbilitySource();
        System.out.println("DynamicHitPointsTest passed: live HP, loss of source, KOs and energy-card dependency guards");
    }
    static void liveHpAndConditions() {
        var m=fixture();UUID host=m.activePokemonView(A).pokemonId();
        m.playTrainer(A,hand(m,A,"tool").instanceId(),host);
        check(m.activePokemonView(A).hitPoints()==400,"工具 HP 未进入公开场上视图");
        m.placeDamageCountersByEffect(A,A,host,32);
        check(m.phase()==MatchPhase.TURN&&m.activePokemonView(A).damage()==320,"仍按印刷 HP 提前击倒");
        var condition=new ConditionDefinition(ConditionType.HP_ABOVE,Map.of("value","70"),List.of());
        check(m.matchesEffectCondition(A,host,condition),"HP 条件未使用修改后的剩余 HP");
        int draws=m.randomTranscript().size();var before=m.clientView(A);
        for(int i=0;i<8;i++) {m.clientView(B);m.activeRetreatCost(A);m.currentToolCapacity(A,host);}
        check(before.equals(m.clientView(A))&&m.randomTranscript().size()==draws,"HP 查询改变状态或随机数");
    }
    static void sourceLoss() {
        for(String cause:List.of("discard","return","disable")) {
            var m=fixture();UUID host=m.activePokemonView(B).pokemonId();UUID physical=PokemonIdentityTestSupport.physical(m,B,host);var card=hand(m,B,"tool");
            m.attachToolByEffect(A,B,EffectSourceZone.HAND,card.instanceId(),B,host);
            m.placeDamageCountersByEffect(A,B,host,32);int turn=m.turnNumber();
            switch(cause) {
                case "discard" -> m.discardToolByEffect(A,B,host,card.instanceId());
                case "return" -> m.returnAttachedToolToHandByEffect(A,B,host,card.instanceId());
                case "disable" -> m.disableToolEffectsByEffect(A,B,host);
            }
            check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(A),"HP 来源失效没有进入拿奖："+cause);
            check(m.privateView(B).discard().stream().anyMatch(c->c.instanceId().equals(physical)),"击倒宝可梦未进入原主弃牌区");
            m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
            check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A)&&m.turnNumber()==turn,"非攻击 HP 击倒错误结束回合");
        }
    }
    static void energyAndStadium() {
        var energy=DynamicPassiveModifierTest.energy(hp(50));
        for(String cause:List.of("move","return","discard","override")) {
            var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"special_energy_override\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":1,\"energy_type\":\"COLORLESS\",\"suppress_other_effects\":true}");
            var m=DynamicPassiveModifierTest.fixture(tool(100),stadium,energy,DynamicPassiveModifierTest.attack(40,false,false));
            UUID host=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();var card=hand(m,A,"special");
            m.attachEnergy(A,card.instanceId(),host);check(m.activePokemonView(A).hitPoints()==350,"能量 HP 未生效");
            m.placeDamageCountersByEffect(A,A,host,32);
            switch(cause) {
                case "move" -> m.moveAttachedEnergyByEffect(A,A,host,A,bench,List.of(card.instanceId()));
                case "return" -> m.returnAttachedEnergyToHandByEffect(A,A,host,List.of(card.instanceId()));
                case "discard" -> m.discardAttachedEnergyByEffect(A,A,host,List.of(card.instanceId()));
                case "override" -> m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
            }
            check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(B),"能量来源变化漏判击倒："+cause);
            if(cause.equals("move"))check(m.benchPokemonView(A).getFirst().hitPoints()==350,"移动能量的新宿主未得到 HP");
        }
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"hit_points_modifier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":100}");
        var m=DynamicPassiveModifierTest.fixture(tool(100),stadium);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),32);
        m.placeDamageCountersByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId(),32);
        m.discardStadiumByEffect(A);
        check(m.privateView(B).discard().stream().filter(c->c.definitionId().equals("basic")).count()==2,"场地离场未同时检查战斗位与备战区");
    }
    static void evolution() {
        var t=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"amount\":100,\"conditions\":[{\"type\":\"IS_BASIC\"}]}");
        var m=DynamicPassiveModifierTest.fixture(t,DynamicPassiveModifierTest.plainStadium());UUID host=m.activePokemonView(A).pokemonId();
        m.playTrainer(A,hand(m,A,"tool").instanceId(),host);m.placeDamageCountersByEffect(A,A,host,38);
        m.evolve(A,host,hand(m,A,"evolved").instanceId());check(m.phase()==MatchPhase.PRIZE_SELECTION,"进化失去基础限定 HP 后没有击倒");
    }
    static void damageAndCheckup() {
        var m=DynamicPassiveModifierTest.fixture(tool(100),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(160,false,false));
        UUID host=m.activePokemonView(B).pokemonId();m.attachToolByEffect(A,B,EffectSourceZone.HAND,hand(m,B,"tool").instanceId(),B,host);
        m.attack(A,"hit");check(m.activePokemonView(B).damage()==320&&m.phase()==MatchPhase.TURN,"弱点后伤害未使用动态 HP");
        var p=fixture();UUID active=p.activePokemonView(A).pokemonId();p.playTrainer(A,hand(p,A,"tool").instanceId(),active);
        p.placeDamageCountersByEffect(A,A,active,39);p.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);p.endTurn(A);
        check(p.phase()==MatchPhase.PRIZE_SELECTION,"中毒达到动态 HP 时没有击倒");
    }
    static void overflow() {
        var m=DynamicPassiveModifierTest.fixture(tool(100),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(ToolCapacityResolutionTest.capacity(2)));
        UUID host=m.activePokemonView(A).pokemonId();for(int i=0;i<2;i++)m.playTrainer(A,hand(m,A,"tool").instanceId(),host);
        m.placeDamageCountersByEffect(A,A,host,45);m.disableAbilityEffectsByEffect(A,A,host);var prompt=m.toolOverflowPrompt();
        m.discardOverflowTool(A,prompt.id(),prompt.tools().getFirst().instanceId());
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(B),"容量清理丢失 HP 来源后没有击倒");
    }
    static void validation() {
        for(String condition:List.of("HP_ABOVE","HP_BELOW","ENERGY_COUNT","HAS_ENERGY","HAND_SIZE","DAMAGE_COUNTER_COUNT","PRIZE_COUNT")) {
            String text="{\"type\":\"conditional\",\"conditions\":[{\"type\":\""+condition+"\",\"value\":1}],\"effects\":["+hp(50)+"]}";
            DynamicPassiveModifierTest.reject(()->DynamicPassiveModifierTest.trainer("TOOL",text));
        }
        DynamicPassiveModifierTest.reject(()->tool(0));DynamicPassiveModifierTest.reject(()->tool(11));
        for(String bad:List.of("{\"type\":\"hit_points_modifier\",\"per_attached_energy\":0}",
            "{\"type\":\"hit_points_modifier\",\"per_attached_energy\":50,\"count_mode\":\"UNITS\"}",
            "{\"type\":\"hit_points_modifier\",\"amount\":100,\"max_energy\":1}"))
            DynamicPassiveModifierTest.reject(()->DynamicPassiveModifierTest.trainer("TOOL",bad));
        DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"per_attached_energy\":50,\"energy_type\":\"FIRE\"}");
        var e=EffectRegistry.standard().parse(JsonParser.parseString(hp(100)).getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(e).capability()==EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"HP 修正错误开放为一次性效果");
    }
    static void sourceKnockoutCascade() {
        var aura=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":100}");
        for (boolean hasReserve : List.of(false, true)) {
        var m=DynamicPassiveModifierTest.fixture(aura,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(200,false,false));
        if (hasReserve) {
            m.endTurn(A);
            m.playBasicPokemonToBench(B, hand(m,B,"basic").instanceId());
            m.endTurn(B);
        }
        UUID host=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();
        m.attachToolByEffect(A,B,EffectSourceZone.HAND,hand(m,B,"tool").instanceId(),B,host);m.placeDamageCountersByEffect(A,B,bench,32);
        m.attack(A,"hit");
        check(m.privateView(B).discard().stream().filter(c->c.definitionId().equals("basic")).count()==2,"HP 光环宿主被击倒后备战宝可梦未连锁击倒");
        if (!hasReserve) {
            check(m.phase()==MatchPhase.FINISHED && m.result().winner().equals(A)
                && m.result().reason()==MatchResult.Reason.NO_POKEMON,"连锁后无宝可梦未自动结束");
            continue;
        }
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"连锁击倒跳过奖赏");m.takePrizeAt(A,0);
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"连锁击倒少给奖赏");m.takePrizeAt(A,0);
        check(m.privateView(A).prizes().size()==4 && m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"连锁击倒的两张奖赏或替补错误");
        m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.phase()==MatchPhase.TURN && m.currentPlayer().equals(B),"连锁奖赏及替补后未继续对局");
        }
    }
    static void composedCheckpoint() {
        var ability=ResumableAbilityTest.ability("repair","{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":100}");
        var m=DynamicPassiveModifierTest.fixture(tool(100),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(ability));
        UUID host=m.activePokemonView(A).pokemonId();m.playTrainer(A,hand(m,A,"tool").instanceId(),host);m.placeDamageCountersByEffect(A,A,host,32);
        m.useAbility(A,host,"repair");check(m.phase()==MatchPhase.TURN&&m.activePokemonView(A).damage()==220,"回复错误使用印刷 HP 或提前击倒");
    }
    static void energyDependency() {
        var energy=BundledCombatData.specialEnergy(JsonParser.parseString("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":["+hp(100)+"],\"provisionRules\":[{\"conditions\":[{\"type\":\"HP_ABOVE\",\"value\":350}],\"energyUnits\":[[\"COLORLESS\"],[\"COLORLESS\"]]}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=DynamicPassiveModifierTest.fixture(tool(100),DynamicPassiveModifierTest.plainStadium(),energy,DynamicPassiveModifierTest.attack(40,false,false));
        UUID host=m.activePokemonView(A).pokemonId();m.attachEnergy(A,hand(m,A,"special").instanceId(),host);
        check(m.activePokemonView(A).hitPoints()==400,"HP 与供能分层发生循环");
        check(m.matchesEffectCondition(A,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("value","2","comparison","=="),List.of())),"条件供能未读取动态 HP");
    }
    static void perAttachedEnergy() {
        var perCard=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"per_attached_energy\":50}");
        var m=DynamicPassiveModifierTest.fixture(perCard,DynamicPassiveModifierTest.plainStadium());UUID host=m.activePokemonView(A).pokemonId();
        m.playTrainer(A,hand(m,A,"tool").instanceId(),host);
        var first=hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,first.instanceId(),A,host);
        check(m.activePokemonView(A).hitPoints()==350,"每张已附着能量没有提高HP");
        var second=hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,second.instanceId(),A,host);
        check(m.activePokemonView(A).hitPoints()==400,"第二张能量没有叠加HP");
        m.placeDamageCountersByEffect(A,A,host,38);m.returnAttachedEnergyToHandByEffect(A,A,host,List.of(first.instanceId(),second.instanceId()));
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"移除每张能量HP来源后没有检查击倒");
        var capped=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"per_attached_energy\":50,\"max_energy\":1,\"card_kind\":\"BASIC\"}");
        var n=DynamicPassiveModifierTest.fixture(capped,DynamicPassiveModifierTest.plainStadium());UUID target=n.activePokemonView(A).pokemonId();
        n.playTrainer(A,hand(n,A,"tool").instanceId(),target);
        n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(n,A,"special").instanceId(),A,target);
        check(n.activePokemonView(A).hitPoints()==300,"特殊能量错误计入基础能量HP修正");
        n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(n,A,"energy").instanceId(),A,target);
        n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(n,A,"energy").instanceId(),A,target);
        check(n.activePokemonView(A).hitPoints()==350,"基础能量筛选或上限错误");
    }
    static void passiveAbilitySource() {
        var ability=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"energy-vitality","nameZh":"能量活力夹具","kind":"PASSIVE","effectSupport":"SUPPORTED",
             "effects":[{"type":"hit_points_modifier","per_attached_energy":50}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.fixed(EnergyType.COLORLESS,2),DynamicPassiveModifierTest.attack(40,false,false),List.of(ability));
        UUID host=m.activePokemonView(A).pokemonId();
        check(!m.canUseAbility(A,host,ability.id()),"组合被动特性错误显示为可主动发动");
        m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,"energy").instanceId(),A,host);
        check(m.activePokemonView(A).hitPoints()==350,"组合被动特性没有进入持续HP来源");
        m.placeDamageCountersByEffect(A,A,host,32);m.disableAbilityEffectsByEffect(A,A,host);
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"被动特性失效后丢失HP未进入统一击倒检查");
        DynamicPassiveModifierTest.reject(()->BundledCombatData.ability(JsonParser.parseString("""
            {"id":"bad-passive","nameZh":"非法被动","kind":"PASSIVE","effectSupport":"SUPPORTED","effects":[{"type":"draw","amount":1}]}
            """).getAsJsonObject(),EffectRegistry.standard()));
        DynamicPassiveModifierTest.reject(()->BundledCombatData.ability(JsonParser.parseString("""
            {"id":"bad-suppress","nameZh":"循环压制","kind":"PASSIVE","effectSupport":"SUPPORTED","effects":[{"type":"suppress_abilities","scope":"ALL_IN_PLAY"}]}
            """).getAsJsonObject(),EffectRegistry.standard()));
    }
}
