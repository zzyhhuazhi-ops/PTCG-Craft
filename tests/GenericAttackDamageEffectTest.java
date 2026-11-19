import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A generic nested DamageEffect participates in normal attack damage, Weakness and server coin audit. */
public final class GenericAttackDamageEffectTest {
    private static final UUID A = new UUID(0, 7491), B = new UUID(0, 7492);
    public static void main(String[] args) {
        ServerMatch heads = null, tails = null;
        for (long seed = 1; seed < 8_000 && (heads == null || tails == null); seed++) {
            ServerMatch candidate = fixture(seed);
            candidate.attack(A, "component-coin");
            if (candidate.lastAttack().finalDamage() == 80) heads = candidate;
            else if (candidate.lastAttack().finalDamage() == 20) tails = candidate;
            else throw new AssertionError("组合 DamageEffect 没有进入统一弱点伤害计算：" + candidate.lastAttack().finalDamage());
        }
        if (heads == null || tails == null) throw new AssertionError("无法建立正反面均可复现的组合 DamageEffect 测试");
        for (ServerMatch match : List.of(heads, tails)) {
            if (match.randomTranscript().stream().noneMatch(event -> event.operation().startsWith("attack-effect-coin:component-coin")))
                throw new AssertionError("组合攻击硬币没有写入服务器随机审计");
        }
        countAndMultiplierTemplatesShareAttackPipeline();
        singleBenchTargetSharesDamageComponents();
        multipleBenchTargetsUseIndependentDamageContexts();
        unsupportedProgramsCannotConsumeRandomness();
        System.out.println("GenericAttackDamageEffectTest passed: nested DamageEffect is server-rolled and passes through weakness/resistance timing");
    }

    private static void countAndMultiplierTemplatesShareAttackPipeline() {
        ServerMatch match = countingFixture();
        UUID active = match.activePokemonView(A).pokemonId();
        match.placeDamageCountersByEffect(A, A, active, 1);
        match.attack(A, "counting");
        // (10 base x 2) + (1 own Benched x 10) + (1 damage counter x 10), then Grass weakness x2.
        if (match.lastAttack().finalDamage() != 80)
            throw new AssertionError("倍率、备战数和伤害指示物模板没有在统一攻击伤害阶段顺序结算：" + match.lastAttack().finalDamage());
    }

    private static ServerMatch fixture(long seed) {
        var registry = EffectRegistry.standard();
        EffectProgram effects = new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), List.of(
            registry.parse(JsonParser.parseString("""
                {"type":"coin_flip","flips":1,"heads":[
                  {"type":"damage","target":"OPPONENT_ACTIVE","amount":30}
                ]}
                """).getAsJsonObject())))));
        AttackDefinition attack = new AttackDefinition("component-coin", "组件硬币伤害", Map.of(), 10, EnergyType.GRASS,
            EffectSupport.SUPPORTED, null).withEffectProgram(effects);
        return fixture(seed, attack, 0);
    }
    private static ServerMatch fixture(long seed, AttackDefinition attack, int benches) {
        CardDefinition attacker = definition("attacker"), defender = definition("defender");
        CombatCatalog combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(120, 0, null, EnergyType.GRASS, 2, null, 0, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            new CardDatabase(List.of(attacker, defender)), combat, new ServerRandom(seed));
        OpeningTestSupport.complete(match, A, B, benches);
        for (int turns = 0; turns < 4; turns++) {
            if (match.currentPlayer().equals(A) && match.mayAttack(A)) return match;
            match.endTurn(match.currentPlayer());
        }
        throw new AssertionError("测试夹具未到达甲方可攻击回合");
    }
    private static void singleBenchTargetSharesDamageComponents() {
        var registry=EffectRegistry.standard();
        EffectProgram effects=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(
            registry.parse(JsonParser.parseString("{\"type\":\"damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":30}").getAsJsonObject()),
            registry.parse(JsonParser.parseString("{\"type\":\"damage_per_damage_counter\",\"target\":\"OPPONENT_BENCH\",\"amount\":10}").getAsJsonObject())))));
        var benchAttack=new AttackDefinition("bench-component","备战组件伤害",Map.of(),10,EnergyType.GRASS,
            EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),AttackDefinition.DamageTarget.OPPONENT_BENCH)
            .withEffectProgram(effects);
        var match=fixture(128,benchAttack,1);UUID bench=match.benchPokemonView(B).getFirst().pokemonId();
        match.placeDamageCountersByEffect(A,B,bench,2);match.attack(A,benchAttack.id(),bench);
        // The two pre-existing counters remain, then this attack adds
        // 10 base + 30 component + (2 counters x 10): no Bench weakness.
        if(match.activePokemonView(B).damage()!=0||match.benchPokemonView(B).getFirst().damage()!=80||match.lastAttack().finalDamage()!=60)
            throw new AssertionError("单一备战目标未复用攻击组件或错误结算弱点抗性");
        var invalidMultiplier=new AttackDefinition("bench-invalid-multiplier","备战错误倍率",Map.of(),10,EnergyType.GRASS,
            EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),AttackDefinition.DamageTarget.OPPONENT_BENCH)
            .withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(
                registry.parse(JsonParser.parseString("{\"type\":\"damage_multiplier\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}").getAsJsonObject()))))));
        var invalid=fixture(130,invalidMultiplier,1);var invalidBefore=invalid.clientView(A);
        rejects(()->invalid.attack(A,invalidMultiplier.id(),invalid.benchPokemonView(B).getFirst().pokemonId()));
        if(!invalidBefore.equals(invalid.clientView(A)))throw new AssertionError("备战攻击的任意目标倍率在拒绝前改变对局");
    }
    private static void multipleBenchTargetsUseIndependentDamageContexts() {
        var registry=EffectRegistry.standard();
        var counters=registry.parse(JsonParser.parseString("{\"type\":\"damage_per_damage_counter\",\"target\":\"OPPONENT_BENCH\",\"amount\":10}").getAsJsonObject());
        EffectProgram effects=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(counters))));
        var all=new AttackDefinition("all-bench-components","全体备战组件",Map.of(),10,EnergyType.GRASS,
            EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),AttackDefinition.DamageTarget.ALL_OPPONENT_BENCH)
            .withEffectProgram(effects);
        var match=fixture(131,all,2);UUID first=match.benchPokemonView(B).get(0).pokemonId(),second=match.benchPokemonView(B).get(1).pokemonId();
        match.placeDamageCountersByEffect(A,B,first,1);match.placeDamageCountersByEffect(A,B,second,2);match.attack(A,all.id());
        if(match.activePokemonView(B).damage()!=0||match.benchPokemonView(B).get(0).damage()!=30||match.benchPokemonView(B).get(1).damage()!=50
            ||match.lastAttack().finalDamage()!=20)
            throw new AssertionError("全体备战组件没有按每个实际目标独立读取伤害指示物，或错误套用弱点抗性");
        var chosen=new AttackDefinition("two-bench-components","双备战组件",Map.of(),10,EnergyType.GRASS,
            EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),AttackDefinition.DamageTarget.OPPONENT_BENCH,0,2)
            .withEffectProgram(effects);
        var selected=fixture(132,chosen,2);UUID one=selected.benchPokemonView(B).get(0).pokemonId(),two=selected.benchPokemonView(B).get(1).pokemonId();
        selected.placeDamageCountersByEffect(A,B,one,1);selected.placeDamageCountersByEffect(A,B,two,2);
        selected.attackTargets(A,chosen.id(),List.of(one,two));
        if(selected.benchPokemonView(B).get(0).damage()!=30||selected.benchPokemonView(B).get(1).damage()!=50)
            throw new AssertionError("选定多个备战目标未获得独立攻击组件上下文");
        var randomEffect=registry.parse(JsonParser.parseString("{\"type\":\"coin_flip\",\"flips\":1,\"heads\":[{\"type\":\"damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":10}]}").getAsJsonObject());
        var random=new AttackDefinition("all-bench-random","全体备战随机组件",Map.of(),10,EnergyType.GRASS,
            EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),AttackDefinition.DamageTarget.ALL_OPPONENT_BENCH)
            .withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(randomEffect)))));
        var rejected=fixture(133,random,2);var before=rejected.clientView(A);var transcript=rejected.randomTranscript();
        rejects(()->rejected.attack(A,random.id()));
        if(!before.equals(rejected.clientView(A))||!transcript.equals(rejected.randomTranscript()))
            throw new AssertionError("多目标备战随机组件在拒绝前消耗随机数或改变对局");
    }
    private static void unsupportedProgramsCannotConsumeRandomness() {
        var choice = dev.hua.ptcglive.engine.effect.EffectRegistry.standard().parse(com.google.gson.JsonParser.parseString("""
            {"type":"choice","options":[{"type":"damage","target":"OPPONENT_ACTIVE","amount":10},
              {"type":"damage","target":"OPPONENT_ACTIVE","amount":20}]}
            """).getAsJsonObject());
        var choiceAttack = new AttackDefinition("unsupported-choice", "尚未接线的攻击选择", Map.of(), 10, EnergyType.GRASS,
            EffectSupport.SUPPORTED, null).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), List.of(choice)))));
        var choiceMatch = fixture(11, choiceAttack, 0);
        var beforeChoice = choiceMatch.clientView(A); var randomChoice = choiceMatch.randomTranscript();
        rejects(() -> choiceMatch.attack(A, choiceAttack.id()));
        if (!beforeChoice.equals(choiceMatch.clientView(A)) || !randomChoice.equals(choiceMatch.randomTranscript()))
            throw new AssertionError("训练家 Choice 接线意外放行了攻击选择并产生副作用");
        var draw = new dev.hua.ptcglive.engine.effect.EffectDefinition("draw",
            dev.hua.ptcglive.engine.effect.EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of());
        // BEFORE_ATTACK has its own authoritative continuation now; evolution events still cannot be attack text.
        for (EffectTrigger trigger : List.of(EffectTrigger.ON_EVOLVE, EffectTrigger.ON_ATTACK)) {
            var program = new EffectProgram(List.of(new TriggeredEffects(trigger, List.of(), List.of(draw))));
            var attack = new AttackDefinition("not-supported", "不完整攻击", Map.of(), 10, EnergyType.GRASS,
                EffectSupport.SUPPORTED, null, List.of(), new AttackDefinition.CoinDamage(1, 10), null, List.of()).withEffectProgram(program);
            ServerMatch match = fixture(11, attack, 0);
            var before = match.clientView(A);
            int random = match.randomTranscript().size(), replay = match.replayTranscript().size();
            rejects(() -> match.attack(A, attack.id()));
            if (!before.equals(match.clientView(A)) || random != match.randomTranscript().size() || replay != match.replayTranscript().size())
                throw new AssertionError("未支持的攻击程序在被拒绝前已消耗随机数或改变对局");
        }
        var damage = new dev.hua.ptcglive.engine.effect.EffectDefinition("damage",
            dev.hua.ptcglive.engine.effect.EffectTarget.OPPONENT_ACTIVE, Map.of("amount", "10"), List.of(), List.of());
        var attack = new AttackDefinition("wrong-target", "错误目标", Map.of(), 10, EnergyType.GRASS, EffectSupport.SUPPORTED,
            null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_BENCH)
            .withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), List.of(damage)))));
        ServerMatch match = fixture(11, attack, 1);
        UUID bench = match.benchPokemonView(B).getFirst().pokemonId();
        var before = match.clientView(A);
        rejects(() -> match.attack(A, attack.id(), bench));
        if (!before.equals(match.clientView(A))) throw new AssertionError("战斗位组件被错算到了备战位");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException | IllegalArgumentException expected) { return; }
        throw new AssertionError("攻击静默丢弃了未支持的效果/触发器");
    }
    private static ServerMatch countingFixture() {
        CardDefinition attacker = definition("attacker"), defender = definition("defender");
        EffectProgram effects = new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(), List.of(
            new dev.hua.ptcglive.engine.effect.EffectDefinition("damage_multiplier", dev.hua.ptcglive.engine.effect.EffectTarget.SELF,
                Map.of("amount", "2"), List.of(), List.of()),
            new dev.hua.ptcglive.engine.effect.EffectDefinition("damage_per_bench", dev.hua.ptcglive.engine.effect.EffectTarget.SELF,
                Map.of("amount", "10"), List.of(), List.of()),
            new dev.hua.ptcglive.engine.effect.EffectDefinition("damage_per_damage_counter", dev.hua.ptcglive.engine.effect.EffectTarget.SELF,
                Map.of("amount", "10"), List.of(), List.of())))));
        AttackDefinition attack = new AttackDefinition("counting", "组件计数伤害", Map.of(), 10, EnergyType.GRASS,
            EffectSupport.SUPPORTED, null).withEffectProgram(effects);
        CombatCatalog combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(120, 0, null, EnergyType.GRASS, 2, null, 0, 1, List.of())), Map.of());
        for (long seed = 1; seed < 5_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
                new CardDatabase(List.of(attacker, defender)), combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 1);
            for (int turns = 0; turns < 4; turns++) {
                if (match.currentPlayer().equals(A) && match.mayAttack(A)) return match;
                match.endTurn(match.currentPlayer());
            }
        }
        throw new AssertionError("测试夹具未到达可执行计数攻击的甲方回合");
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"),
            true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int index = 0; index < 60; index++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
}
