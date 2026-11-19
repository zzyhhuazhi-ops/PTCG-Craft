import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.TrainerProfile;
import java.util.List;

/** Synthetic fixtures prove completed public card plays reach Ability and persistent listeners server-side. */
public final class CardPlayedTriggeredAbilityTest {
    private static final java.util.UUID A = DynamicPassiveModifierTest.A, B = DynamicPassiveModifierTest.B;
    private static final String OPPONENT_ITEM = "{\"trigger\":\"ON_CARD_PLAYED\",\"player\":\"OPPONENT\",\"card\":\"ITEM\",\"optional\":false,\"usage\":\"NONE\"}";

    public static void main(String[] args) {
        pokemonAbilityObservesOpponentItem();
        persistentStadiumObservesOpponentItem();
        pokemonAndEnergyClassifications();
        automaticSourceOwnsTemporaryLockDuration();
        malformedPoliciesStayRejected();
        System.out.println("CardPlayedTriggeredAbilityTest passed: completed card plays are server-filtered and reach field/persistent listeners");
    }

    private static void pokemonAbilityObservesOpponentItem() {
        var watcher = ability("item-watch", OPPONENT_ITEM);
        check(watcher.eventPolicy() instanceof dev.hua.ptcglive.engine.effect.CardPlayedTriggerPolicy policy
                && policy.matches(A, B, dev.hua.ptcglive.rules.CardCategory.TRAINER, dev.hua.ptcglive.rules.TrainerCategory.ITEM),
            "出牌策略没有识别对方Item分类");
        var match = fixture(watcher, DynamicPassiveModifierTest.plainStadium());
        check(EventTargetBindingTest.field(match).activeAbilities(A, match.activePokemonView(A).pokemonId()).stream()
            .anyMatch(ability -> ability.id().equals("item-watch")), "出牌观察特性没有安装到场上");
        match.endTurn(A);
        int hand = match.privateView(A).hand().size();
        match.playTrainer(B, DynamicPassiveModifierTest.hand(match, B, "item").instanceId(), null);
        resolveTriggers(match);
        check(match.privateView(A).hand().size() == hand + 2, "对方使用Item后场上宝可梦没有收到出牌触发");
        check(match.replayTranscript().stream().anyMatch(event -> event.operation().contains("结算触发特性：item-watch")),
            "出牌触发没有留下服务器公开回放事实");
    }

    private static void persistentStadiumObservesOpponentItem() {
        var stadium = PersistentSourceTriggerTest.trainer("STADIUM",
            PersistentSourceTriggerTest.trigger("stadium-watch", OPPONENT_ITEM, "{\"type\":\"draw\",\"target\":\"HAND\",\"amount\":1}"));
        var match = fixture(null, stadium);
        match.playTrainer(A, DynamicPassiveModifierTest.hand(match, A, "stadium").instanceId(), null);
        match.endTurn(A);
        int hand = match.privateView(A).hand().size();
        match.playTrainer(B, DynamicPassiveModifierTest.hand(match, B, "item").instanceId(), null);
        check(match.privateView(A).hand().size() == hand + 1, "场地持续来源没有收到对方Item出牌事件");
    }

    private static void pokemonAndEnergyClassifications() {
        var basic = fixture(ability("pokemon-watch", policy("OPPONENT", "POKEMON")), DynamicPassiveModifierTest.plainStadium());
        basic.endTurn(A);
        int hand = basic.privateView(A).hand().size();
        basic.playBasicPokemonToBench(B, DynamicPassiveModifierTest.hand(basic, B, "basic").instanceId());
        resolveTriggers(basic);
        check(basic.privateView(A).hand().size() == hand + 2, "基础宝可梦成功上场没有触发POKEMON分类事件");

        var energy = fixture(ability("energy-watch", policy("OPPONENT", "ENERGY")), DynamicPassiveModifierTest.plainStadium());
        energy.endTurn(A);
        hand = energy.privateView(A).hand().size();
        energy.attachEnergy(B, DynamicPassiveModifierTest.hand(energy, B, "energy").instanceId(), energy.activePokemonView(B).pokemonId());
        resolveTriggers(energy);
        check(energy.privateView(A).hand().size() == hand + 2, "从手牌附着能量没有触发ENERGY分类事件");
    }

    /** B's turn has execution authority, but A's printed source owns the temporary lock lifetime. */
    private static void automaticSourceOwnsTemporaryLockDuration() {
        var watcher = ability("source-owned-lock", OPPONENT_ITEM,
            "{\"type\":\"prevent_attack\",\"target\":\"OPPONENT_ACTIVE\"}");
        var match = fixture(watcher, DynamicPassiveModifierTest.plainStadium());
        match.endTurn(A);
        match.playTrainer(B, DynamicPassiveModifierTest.hand(match, B, "item").instanceId(), null);
        resolveTriggers(match);
        check(match.activePokemonView(B).continuousEffectZh().contains("不能攻击") && !match.mayAttack(B),
            "对手回合自动特性没有以当前回合服务器权限施加攻击限制");
        check(match.replayTranscript().stream().anyMatch(event -> event.operation().contains("结算触发特性：source-owned-lock")),
            "自动持续效果没有保留特性来源的回放归因");
        match.endTurn(B);
        check(match.activePokemonView(B).continuousEffectZh().isBlank(),
            "自动持续效果错误按当前回合玩家而非来源控制者计算到期");
    }

    private static void malformedPoliciesStayRejected() {
        rejects(() -> ability("bad-kind", policy("OPPONENT", "MISSING")));
        rejects(() -> BundledCombatData.ability(JsonParser.parseString("""
            {"id":"bad-card-policy","nameZh":"非法出牌事件","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_CARD_PLAYED","player":"ANY","card":"ITEM","optional":false,"usage":"NONE","extra":true},
             "triggers":[{"trigger":"ON_CARD_PLAYED","effects":[{"type":"draw","amount":1}]}]}
            """).getAsJsonObject(), EffectRegistry.standard()));
    }

    private static String policy(String player, String card) {
        return "{\"trigger\":\"ON_CARD_PLAYED\",\"player\":\"" + player + "\",\"card\":\"" + card + "\",\"optional\":false,\"usage\":\"NONE\"}";
    }

    private static AbilityDefinition ability(String id, String policy) {
        return ability(id, policy, "{\"type\":\"draw\",\"target\":\"HAND\",\"amount\":1}");
    }

    private static AbilityDefinition ability(String id, String policy, String effects) {
        return BundledCombatData.ability(JsonParser.parseString("""
            {"id":"%s","nameZh":"出牌观察夹具","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":%s,"triggers":[{"trigger":"ON_CARD_PLAYED","effects":[%s]}]}
            """.formatted(id, policy, effects)).getAsJsonObject(), EffectRegistry.standard());
    }

    private static TrainerProfile item() {
        return BundledCombatData.trainer(JsonParser.parseString("""
            {"trainerCategory":"ITEM","effect":{"opcode":"DRAW","amount":1}}
            """).getAsJsonObject(), EffectRegistry.standard());
    }

    private static ServerMatch fixture(AbilityDefinition ability, TrainerProfile stadium) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(), stadium,
            EnergyProfile.fixed(EnergyType.COLORLESS, 2), DynamicPassiveModifierTest.attack(40, false, false),
            ability == null ? List.of() : List.of(ability), 0, item());
    }

    private static void resolveTriggers(ServerMatch match) {
        for (int guard = 0; guard < 12 && match.triggerOrderPrompt() != null; guard++) {
            var prompt = match.triggerOrderPrompt();
            if (prompt.optionalDecision()) match.decideTriggeredEffect(prompt.choosingPlayer(), prompt.id(), true);
            else match.chooseTriggeredEffect(prompt.choosingPlayer(), prompt.id(), prompt.entries().getFirst().id());
        }
        check(match.triggerOrderPrompt() == null, "出牌触发排序没有在有限步骤内结束");
    }

    private static void rejects(Runnable action) { DynamicPassiveModifierTest.reject(action); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
