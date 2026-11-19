package dev.hua.ptcglive.engine.effect;

import java.util.Map;

/**
 * Read-only server-owned state exposed to generic Condition evaluation. A
 * client implementation is deliberately impossible: match code supplies it.
 */
public interface EffectConditionContext {
    /** Only a declared attack may query units beyond its current payable cost. */
    default boolean hasExtraAttackEnergy(ExtraAttackEnergyRequirement requirement) {
        throw new IllegalStateException("此上下文没有当前攻击费用");
    }
    /** Authoritative current opposing Active identity, without HP/provision dependencies. */
    default boolean opponentActiveMatches(AttackDefenderFilter filter) {throw new IllegalStateException("此上下文不支持对手战斗位筛选");}
    /** Numeric state such as Energy count, Bench count, hand size or current HP. */
    int number(ConditionType type, EffectTarget target, Map<String, String> arguments);
    /** The other player's equivalent public count, supplied only by the server context. */
    default int opponentNumber(ConditionType type, Map<String, String> arguments) {
        throw new IllegalStateException("此效果上下文不支持对手数量比较");
    }
    /** Pure live queries used by passive modifiers; these never resolve HP or Energy provision. */
    default int opponentPrizeCardsTaken() { throw new IllegalStateException("此上下文不支持已获奖赏查询"); }
    default int ownPrizeCardsTaken() { throw new IllegalStateException("此上下文不支持己方已获奖赏查询"); }
    /** A live confirmed KO caused by opposing attack damage, not counters, checkup or reduced maximum HP. */
    default boolean selfKnockedOutByOpponentAttackDamage() { return false; }
    default int opponentPokemonCount(PokemonFamilyFilter family) { throw new IllegalStateException("此上下文不支持对手场上家族查询"); }
    default int opponentBenchCount() { throw new IllegalStateException("此上下文不支持对手备战区数量查询"); }
    default int discardDefinitionCount(java.util.Set<String> definitionIds) { throw new IllegalStateException("此上下文不支持弃牌身份计数"); }
    /** Status state is separate so a status name is never treated as an arbitrary client string. */
    boolean hasStatus(EffectTarget target, String status);
    /** Card-kind and turn predicates, including IS_BASIC / IS_V / IS_ACTIVE / TURN_STATE. */
    boolean flag(ConditionType type, EffectTarget target, Map<String, String> arguments);
    /** Explicit last-resolved server coin result; never a client-provided value. */
    boolean coinResult(String expected);
    /**
     * Physical cards currently attached to the host Pokémon. This stays
     * separate from {@link #number}: a persistent HP modifier must not query
     * resolved Special-Energy units when those units may depend on current HP.
     * Only the authoritative server exposes this fact for an in-play host.
     */
    default int printedAttachedEnergyCards(EnergyCountQuery query) {
        throw new IllegalStateException("此效果上下文不支持已附着能量卡张数查询");
    }
    /** Live provision for HP thresholds; the server proves the provision graph has no HP dependency. */
    default int energyUnitsForHitPoints(EnergyCountQuery query) { throw new IllegalStateException("此上下文不支持HP供能门槛"); }
    /** Null means this branch has not produced the named result; NOT must not invert it into true. */
    default EffectExecutionContext.CoinResult namedCoinResult(String name) { return null; }
    /** A declared attack's named count (AttackCountBindings); a never-recorded name is 0. Other contexts have none. */
    default int boundCount(String name) { throw new IllegalStateException("此效果上下文没有攻击计数"); }
    /** An audited, named last-resort condition only. */
    boolean customCondition(ConditionDefinition condition);
}
