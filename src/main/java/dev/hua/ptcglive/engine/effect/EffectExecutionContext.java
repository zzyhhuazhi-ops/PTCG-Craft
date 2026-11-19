package dev.hua.ptcglive.engine.effect;

/** Server-only bridge used by the generic interpreter to resolve randomness, choices and atomic effects. */
public interface EffectExecutionContext extends EffectConditionContext {
    /** Non-mutating, server-owned validation of an atomic choice before consuming its prompt. */
    default boolean canAffect(EffectDefinition effect) { return false; }
    /** The server performs every flip and records it in the match random transcript. */
    CoinResult flipCoins(EffectDefinition source, int flips);
    /** A retry is a distinct server RNG event while preserving compatibility with existing contexts. */
    default CoinResult flipCoins(EffectDefinition source, int flips, String operation) {
        return flipCoins(source, flips);
    }
    /** A reviewed effect may replace the next coin; selection occurs before that coin, not when the effect was played. */
    default boolean canChooseNextCoinOutcome(EffectDefinition source) { return false; }
    /** Read-only check for a coin caused inside an atomic effect, such as a self-damage prevention Ability. */
    default boolean canChooseAtomicCoinOutcome(EffectDefinition source) { return false; }
    /** Reserve one result. The next eligible flip consumes it; no random draw or downstream effect occurs here. */
    default void chooseNextCoinOutcome(EffectDefinition source, boolean heads) {
        throw new IllegalStateException("此入口没有选择硬币结果的效果");
    }
    /** Server-only permission for a complete, reviewed attack coin sequence. */
    default boolean attackCoinRetryAvailable(EffectDefinition source) { return false; }
    /** Revalidate and reserve the physical source's usage before any retry randomness. */
    default void consumeAttackCoinRetry(EffectDefinition source) {
        throw new IllegalStateException("此入口没有可选攻击重投来源");
    }
    /** Null only when no confirmed plan exists. A plan mismatch must throw, never spend new randomness. */
    default CoinResult preparedAttackCoin(EffectDefinition source) { return null; }
    /** Called once after all retry decisions for this coin instruction, before its effects execute. */
    default void coinResultCommitted(EffectDefinition source,CoinResult result) { }
    default boolean supportsCoinBindings() { return false; }
    default boolean supportsPokemonBindings() { return false; }
    default void rememberPokemon(String name,EffectTargetResolution.PokemonTarget target) {
        throw new IllegalStateException("此入口未接入命名宝可梦绑定");
    }
    /** Null means no selection occurred on this path. A captured departed object is not a new selection. */
    default EffectExecutionContext withNamedPokemon(String name) {
        throw new IllegalStateException("此入口未接入命名宝可梦引用");
    }
    /** Current live target stored under a server-owned name, or null after it leaves play. */
    default EffectTargetResolution.PokemonTarget namedPokemonTarget(String name) { return null; }
    default void rememberCoinResult(String name,CoinResult result) {
        throw new IllegalStateException("此效果入口未接入攻击硬币绑定");
    }
    /** Records an attack's named count (AttackCountBindings); only a declared attack's server contexts accept it. */
    default void rememberCount(String name,int count) {
        throw new IllegalStateException("此效果入口未接入攻击计数");
    }
    /** Selection must already be server-generated and revalidated by the bridge. */
    int choose(EffectDefinition source, int optionCount);
    /** Executes one non-control Effect through the unified Battle Engine. */
    void executeAtomic(EffectDefinition effect);
    /** Public positions only; no Prize identities are exposed before choosing to reveal one. */
    default java.util.List<Integer> faceDownPrizePositions() {
        throw new IllegalStateException("此入口未接入暗面奖赏位置选择");
    }
    /** Mandatory server rule choice between committed steps; never re-execute the committed prefix. */
    default boolean hasPendingRuleChoice() {return false;}
    /** Only an integration with a server-owned selection controller may opt in. */
    default boolean requiresCardSelection(EffectDefinition effect) { return false; }
    /** HAND_REVEAL: “Your opponent reveals their hand.” pauses until the effect's controller has looked at it. */
    enum InteractionKind { CARD_SELECTION, DAMAGE_COUNTER_ALLOCATION, POKEMON_SELECTION, HAND_REVEAL, RANDOM_HAND_SUPPORTER_REVEAL, TOP_ENERGY_REVEAL, DISCARDED_ENERGY_ATTACHMENT, DECK_WINDOW_REVEAL, TOP_POKEMON_REVEAL, SUPPORTER_SEARCH_REVEAL, TOP_DECK_ATTACK_COPY, PRIZE_INSPECT, PRIZE_HAND_SWAP, PRIZE_RANDOM_HAND_SWAP, PRIZE_SOURCE_REVEAL, PRIZE_TAKE, TOP_SUPPORTER }
    /** Only a server-issued, revalidated target may reach this integration hook. */
    default void executeSelectedPokemon(EffectDefinition effect,EffectTargetResolution.PokemonTarget target) {
        throw new IllegalStateException("此效果入口未接入宝可梦选择续接");
    }
    /** Several different issued targets of one step (“… to 2 of your opponent's Benched Pokémon”), validated together. */
    default void executeSelectedPokemons(EffectDefinition effect,java.util.List<EffectTargetResolution.PokemonTarget> targets) {
        if (targets.size() != 1) throw new IllegalStateException("此效果入口未接入多目标选择续接");
        executeSelectedPokemon(effect, targets.getFirst());
    }
    /** Immutable lexical child context, created only after an issued target has been validated. */
    default EffectExecutionContext withChosenPokemon(EffectTargetResolution.PokemonTarget target) {
        throw new IllegalStateException("此入口尚未接入已选宝可梦作用域");
    }
    /**
     * The current server-captured public target, if this lexical scope has
     * one and that exact in-play incarnation still exists. This is never a
     * client request and is intentionally absent from ordinary UI contexts.
     */
    default EffectTargetResolution.PokemonTarget chosenPokemonTarget() { return null; }
    /**
     * “This Pokémon”: the scope's own source Pokémon while that same in-play object remains (evolving keeps it,
     * leaving play does not). Null for Trainers, rule contexts and departed sources.
     */
    default EffectTargetResolution.PokemonTarget liveSourcePokemon() { return null; }
    /** Captured event identity, never the occupant of a mutable Active slot. Null means that incarnation departed. */
    default EffectTargetResolution.PokemonTarget liveEventPokemon(EffectTarget target) {
        throw new IllegalStateException("此入口没有服务器事件目标上下文");
    }
    /** An absent chosen object is not false data that NOT may invert into a successful condition. */
    default boolean conditionAvailable(ConditionDefinition condition) { return true; }
    /** Null is synchronous. The continuation carries this typed request without knowing Minecraft or zones. */
    default InteractionKind interactionKind(EffectDefinition effect) {
        return requiresCardSelection(effect) ? InteractionKind.CARD_SELECTION : null;
    }

    record CoinResult(int flips, int heads) {
        public CoinResult {
            if (flips < 0 || heads < 0 || heads > flips) throw new IllegalArgumentException("硬币结果无效");
        }
        public boolean matches(String expected) {
            return switch (expected.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "HEADS" -> flips > 0 && heads > 0;
                case "TAILS" -> flips > 0 && heads < flips;
                case "ALL_HEADS" -> flips > 0 && heads == flips;
                case "ALL_TAILS" -> flips > 0 && heads == 0;
                default -> throw new IllegalArgumentException("未知硬币条件：" + expected);
            };
        }
    }
}
