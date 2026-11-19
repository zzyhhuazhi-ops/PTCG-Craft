package dev.hua.ptcglive.engine.field;

import java.util.Objects;
import java.util.Locale;
import java.util.UUID;

/**
 * Server-owned temporary state attached to one in-play Pokémon.  It is not a
 * card definition and therefore cannot be fabricated by a client.  New
 * continuous-effect kinds must declare their expiry here before card data can
 * opt in to them.
 */
public record ContinuousEffect(
    Kind kind,
    UUID owner,
    UUID opposingPlayer,
    AttackDefinition.ProtectionScope protectionScope,
    int damageReduction,
    int retreatCostChange,
    Expiry expiry,
    String publicLabelZh,
    /** Normalized printed name for attack locks; printed attack id for a named damage bonus. Retains the snapshot component name. */
    String lockedAttackId,
    int expiresAfterOwnerTurn,
    dev.hua.ptcglive.engine.effect.EffectProtection effectProtection,
    dev.hua.ptcglive.engine.effect.AttackDamageReceivedModifier receivedModifier,
    dev.hua.ptcglive.engine.effect.EffectOrigin.Kind sourceKind
) {
    public enum Kind { ATTACK_PROTECTION, DAMAGE_REDUCTION, ATTACK_DAMAGE_MAX_GUARD, RETREAT_LOCK, ATTACK_LOCK, EVOLUTION_LOCK, HAND_EVOLUTION_LOCK, HAND_ENERGY_LOCK, RETREAT_COST_MODIFIER, ATTACK_COST_MODIFIER, ATTACK_ID_LOCK, NEXT_TURN_ATTACK_BASE, TOOL_EFFECTS_DISABLED, ABILITY_EFFECTS_DISABLED, EFFECT_PROTECTION, ATTACK_DAMAGE_PENALTY, RETALIATION, ATTACK_DAMAGE_BONUS, DAMAGE_TAKEN_INCREASE, ATTACK_COIN_GATE, ATTACK_DAMAGE_RECEIVED_MODIFIER }
    public enum Expiry { OWNER_NEXT_TURN_START, OWNER_TURN_END, OPPONENT_TURN_END, LEAVES_PLAY }

    public ContinuousEffect(Kind kind, UUID owner, UUID opposingPlayer, AttackDefinition.ProtectionScope protectionScope,
        int damageReduction, int retreatCostChange, Expiry expiry, String publicLabelZh, String lockedAttackId, int expiresAfterOwnerTurn) {
        this(kind,owner,opposingPlayer,protectionScope,damageReduction,retreatCostChange,expiry,publicLabelZh,lockedAttackId,expiresAfterOwnerTurn,null);
    }

    public ContinuousEffect(Kind kind,UUID owner,UUID opposingPlayer,AttackDefinition.ProtectionScope protectionScope,
        int damageReduction,int retreatCostChange,Expiry expiry,String publicLabelZh,String lockedAttackId,int expiresAfterOwnerTurn,
        dev.hua.ptcglive.engine.effect.EffectProtection effectProtection) {
        this(kind,owner,opposingPlayer,protectionScope,damageReduction,retreatCostChange,expiry,publicLabelZh,lockedAttackId,expiresAfterOwnerTurn,effectProtection,null);
    }

    public ContinuousEffect(Kind kind,UUID owner,UUID opposingPlayer,AttackDefinition.ProtectionScope protectionScope,
        int damageReduction,int retreatCostChange,Expiry expiry,String publicLabelZh,String lockedAttackId,int expiresAfterOwnerTurn,
        dev.hua.ptcglive.engine.effect.EffectProtection effectProtection,
        dev.hua.ptcglive.engine.effect.AttackDamageReceivedModifier receivedModifier) {
        this(kind,owner,opposingPlayer,protectionScope,damageReduction,retreatCostChange,expiry,publicLabelZh,lockedAttackId,
            expiresAfterOwnerTurn,effectProtection,receivedModifier,dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.RULE);
    }
    public ContinuousEffect withSourceKind(dev.hua.ptcglive.engine.effect.EffectOrigin.Kind sourceKind) {
        return new ContinuousEffect(kind,owner,opposingPlayer,protectionScope,damageReduction,retreatCostChange,expiry,
            publicLabelZh,lockedAttackId,expiresAfterOwnerTurn,effectProtection,receivedModifier,sourceKind);
    }
    public ContinuousEffect {
        Objects.requireNonNull(sourceKind, "持续效果来源");
        Objects.requireNonNull(kind, "持续效果类别");
        Objects.requireNonNull(owner, "持续效果拥有者");
        Objects.requireNonNull(expiry, "持续效果到期条件");
        if((kind==Kind.ATTACK_DAMAGE_RECEIVED_MODIFIER)!=(receivedModifier!=null))
            throw new IllegalArgumentException("临时受伤修正必须且只能携带明确修正策略");
        if(kind==Kind.ATTACK_DAMAGE_RECEIVED_MODIFIER&&(opposingPlayer==null||opposingPlayer.equals(owner)||protectionScope!=null
            ||damageReduction!=0||retreatCostChange!=0||expiry!=Expiry.OPPONENT_TURN_END||lockedAttackId!=null||expiresAfterOwnerTurn!=0))
            throw new IllegalArgumentException("临时受伤修正的来源、对手和到期条件不一致");
        if ((kind == Kind.EFFECT_PROTECTION) != (effectProtection != null))
            throw new IllegalArgumentException("来源防护必须且只能携带明确防护策略");
        if (kind == Kind.EFFECT_PROTECTION && (opposingPlayer != null || protectionScope != null || damageReduction != 0 || retreatCostChange != 0))
            throw new IllegalArgumentException("来源防护不能混用旧攻击防护参数");
        if (publicLabelZh == null || publicLabelZh.isBlank()) throw new IllegalArgumentException("持续效果公开说明不能为空");
        if (kind != Kind.ATTACK_ID_LOCK && kind != Kind.NEXT_TURN_ATTACK_BASE && kind != Kind.ATTACK_DAMAGE_BONUS && lockedAttackId != null
            || kind != Kind.ATTACK_ID_LOCK && kind != Kind.NEXT_TURN_ATTACK_BASE && kind != Kind.ATTACK_LOCK && kind != Kind.ATTACK_DAMAGE_BONUS && kind != Kind.DAMAGE_TAKEN_INCREASE
                && expiresAfterOwnerTurn != 0)
            throw new IllegalArgumentException("非指定攻击限制不能携带攻击冷却参数");
        if (kind == Kind.ATTACK_LOCK && (expiry == Expiry.OWNER_TURN_END) != (expiresAfterOwnerTurn >= 1)
            || kind == Kind.ATTACK_LOCK && expiry == Expiry.LEAVES_PLAY)
            throw new IllegalArgumentException("禁止攻击持续效果的到期回合与到期条件不一致");
        if (kind == Kind.ATTACK_PROTECTION) {
            Objects.requireNonNull(opposingPlayer, "攻击保护的对手");
            Objects.requireNonNull(protectionScope, "攻击保护范围");
            if (opposingPlayer.equals(owner)) throw new IllegalArgumentException("攻击保护不能指定自己");
            if (damageReduction != 0 || retreatCostChange != 0) throw new IllegalArgumentException("攻击保护不能携带额外数值");
        } else if (kind == Kind.DAMAGE_REDUCTION) {
            Objects.requireNonNull(opposingPlayer, "减伤效果的对手");
            if (opposingPlayer.equals(owner) || protectionScope != null || damageReduction < 10 || damageReduction % 10 != 0 || retreatCostChange != 0)
                throw new IllegalArgumentException("减伤持续效果参数无效");
        } else if (kind == Kind.ATTACK_DAMAGE_MAX_GUARD) {
            if(opposingPlayer!=null||protectionScope!=null||damageReduction<10||damageReduction>1000||damageReduction%10!=0
                ||retreatCostChange!=0||lockedAttackId!=null||expiresAfterOwnerTurn!=0||expiry!=Expiry.OWNER_NEXT_TURN_START)
                throw new IllegalArgumentException("小额攻击伤害防护参数无效");
        } else if (kind == Kind.RETREAT_LOCK || kind == Kind.ATTACK_LOCK || kind == Kind.EVOLUTION_LOCK || kind == Kind.HAND_EVOLUTION_LOCK || kind == Kind.HAND_ENERGY_LOCK) {
            if (opposingPlayer != null || protectionScope != null || damageReduction != 0 || retreatCostChange != 0)
                throw new IllegalArgumentException("行动限制持续效果参数无效");
        } else if (kind == Kind.ATTACK_COST_MODIFIER) {
            // The legacy cost-change slot is shared by both cost kinds; each query filters its own kind.
            if(opposingPlayer!=null||protectionScope!=null||damageReduction!=0||retreatCostChange<1||retreatCostChange>10
                ||expiry!=Expiry.OWNER_NEXT_TURN_START)throw new IllegalArgumentException("攻击费用增加持续效果参数无效");
        } else if (kind == Kind.RETREAT_COST_MODIFIER) {
            if (opposingPlayer != null || protectionScope != null || damageReduction != 0 || retreatCostChange == 0 || Math.abs(retreatCostChange) > 10)
                throw new IllegalArgumentException("撤退费用修正持续效果参数无效");
        } else if (kind == Kind.ATTACK_ID_LOCK) {
            // Either “during your next turn” (OWNER_TURN_END after a given owner turn) or “again until it leaves the
            // Active Spot” (LEAVES_PLAY: no turn expiry; switching to the Bench or evolving clears attack effects).
            boolean turnLimited = expiry == Expiry.OWNER_TURN_END && expiresAfterOwnerTurn >= 1;
            boolean untilLeavesActive = expiry == Expiry.LEAVES_PLAY && expiresAfterOwnerTurn == 0;
            // “During your opponent's next turn, that Pokémon can't use that attack.”: owned by the locking player.
            boolean untilOwnerNextTurn = expiry == Expiry.OWNER_NEXT_TURN_START && expiresAfterOwnerTurn == 0;
            if (opposingPlayer != null || protectionScope != null || damageReduction != 0 || retreatCostChange != 0
                || lockedAttackId == null || lockedAttackId.isBlank() || lockedAttackId.length() > 160 || !(turnLimited || untilLeavesActive || untilOwnerNextTurn))
                throw new IllegalArgumentException("指定招式名称限制持续效果参数无效");
        } else if(kind==Kind.NEXT_TURN_ATTACK_BASE) {
            if(opposingPlayer!=null||protectionScope!=null||damageReduction<10||damageReduction>1000||damageReduction%10!=0
                ||retreatCostChange!=0||lockedAttackId==null||lockedAttackId.isBlank()||lockedAttackId.length()>160
                ||expiresAfterOwnerTurn<1||expiry!=Expiry.OWNER_TURN_END)
                throw new IllegalArgumentException("下回合指定招式基础伤害参数无效");
        } else if (kind == Kind.ATTACK_DAMAGE_PENALTY) {
            // “The Defending Pokémon's attacks do N less damage”: damageReduction carries N, applied by the attacker side.
            if (opposingPlayer != null || protectionScope != null || damageReduction < 10 || damageReduction % 10 != 0
                || retreatCostChange != 0 || lockedAttackId != null || expiresAfterOwnerTurn != 0 || expiry != Expiry.OWNER_NEXT_TURN_START)
                throw new IllegalArgumentException("攻击伤害减少持续效果参数无效");
        } else if (kind == Kind.ATTACK_DAMAGE_BONUS || kind == Kind.DAMAGE_TAKEN_INCREASE) {
            // “During your next turn, this Pokémon's attacks do N more damage …” / “… the Defending Pokémon takes N more damage
            // from attacks.”: damageReduction carries N; both last through the effect owner's next turn.
            if (opposingPlayer != null || protectionScope != null || damageReduction < 10 || damageReduction > 1000 || damageReduction % 10 != 0
                || retreatCostChange != 0 || (kind == Kind.DAMAGE_TAKEN_INCREASE && lockedAttackId != null)
                || (kind == Kind.ATTACK_DAMAGE_BONUS && lockedAttackId != null
                    && (lockedAttackId.length() > 160 || !lockedAttackId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")))
                || expiresAfterOwnerTurn < 1 || expiry != Expiry.OWNER_TURN_END)
                throw new IllegalArgumentException("下回合伤害增加持续效果参数无效");
        } else if (kind == Kind.ATTACK_COIN_GATE) {
            // Legacy serialized0 means one flip;2 means two flips, all heads required.
            if (opposingPlayer != null || protectionScope != null || (damageReduction != 0 && damageReduction != 2) || retreatCostChange != 0
                || lockedAttackId != null || expiresAfterOwnerTurn != 0 || expiry != Expiry.OWNER_NEXT_TURN_START)
                throw new IllegalArgumentException("攻击前掷币持续效果参数无效");
        } else if (kind == Kind.RETALIATION) {
            // “… if this Pokémon is damaged by an attack, put N damage counters on the Attacking Pokémon”:
            // damageReduction carries N (1–30), or 0 for “damage counters equal to the damage done to this Pokémon”.
            Objects.requireNonNull(opposingPlayer, "受击反伤的对手");
            if (opposingPlayer.equals(owner) || protectionScope != null || damageReduction < 0 || damageReduction > 30
                || retreatCostChange != 0 || lockedAttackId != null || expiresAfterOwnerTurn != 0 || expiry != Expiry.OWNER_NEXT_TURN_START)
                throw new IllegalArgumentException("受击反伤持续效果参数无效");
        } else if (kind == Kind.TOOL_EFFECTS_DISABLED || kind == Kind.ABILITY_EFFECTS_DISABLED) {
            if (opposingPlayer != null || protectionScope != null || damageReduction != 0 || retreatCostChange != 0
                || lockedAttackId != null || expiresAfterOwnerTurn != 0)
                throw new IllegalArgumentException("卡牌效果失效持续效果参数无效");
        }
    }

    public static ContinuousEffect attackProtection(UUID owner, UUID opposingPlayer, AttackDefinition.ProtectionScope scope) {
        String label = scope == AttackDefinition.ProtectionScope.DAMAGE_ONLY
            ? "持续效果：下个对手回合免受攻击伤害"
            : "持续效果：下个对手回合免受攻击伤害与效果";
        return new ContinuousEffect(Kind.ATTACK_PROTECTION, owner, opposingPlayer, scope, 0, 0, Expiry.OWNER_NEXT_TURN_START, label, null, 0);
    }
    public static ContinuousEffect protection(UUID sourceController, dev.hua.ptcglive.engine.effect.ProtectionEffect protection) {
        String duration = switch (protection.expiry()) {
            case OWNER_NEXT_TURN_START -> "；至来源玩家下回合开始";
            case OWNER_TURN_END -> "；至来源玩家回合结束";
            case OPPONENT_TURN_END -> "；至对方回合结束";
            case LEAVES_PLAY -> "；至离场或场上效果清除";
        };
        return new ContinuousEffect(Kind.EFFECT_PROTECTION,sourceController,null,null,0,0,protection.expiry(),
            protection.policy().labelZh()+duration,null,0,protection.policy());
    }
    /**
     * “During your opponent's next turn, if this Pokémon is damaged by an attack (even if it is Knocked Out), put N damage
     * counters on the Attacking Pokémon.” {@code counters} 0 means “equal to the damage done to this Pokémon”.
     */
    public static ContinuousEffect retaliation(UUID owner, UUID opposingPlayer, int counters) {
        String amount = counters > 0 ? counters + " 个伤害指示物" : "与受到的伤害相同数量的伤害指示物";
        return new ContinuousEffect(Kind.RETALIATION, owner, opposingPlayer, null, counters, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：下个对手回合受到攻击伤害时（即使被击倒），在攻击的宝可梦身上放置" + amount, null, 0);
    }
    public static ContinuousEffect damageReduction(UUID owner, UUID opposingPlayer, int amount) {
        return new ContinuousEffect(Kind.DAMAGE_REDUCTION, owner, opposingPlayer, null, amount, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：下个对手回合受到的攻击伤害减少 " + amount, null, 0);
    }
    public static ContinuousEffect attackDamageMaximumGuard(UUID owner,int maximum) {
        return new ContinuousEffect(Kind.ATTACK_DAMAGE_MAX_GUARD,owner,null,null,maximum,0,Expiry.OWNER_NEXT_TURN_START,
            "持续效果：下个对手回合，若受到的最终攻击伤害不超过 "+maximum+"，则防止该伤害",null,0);
    }
    public static ContinuousEffect receivedModifier(UUID owner,UUID opponent,dev.hua.ptcglive.engine.effect.AttackDamageReceivedModifier policy) {
        return new ContinuousEffect(Kind.ATTACK_DAMAGE_RECEIVED_MODIFIER,owner,opponent,null,0,0,Expiry.OPPONENT_TURN_END,
            policy.labelZh(),null,0,null,policy);
    }
    /**
     * “During your opponent's next turn, the Defending Pokémon's attacks do N less damage (before applying
     * Weakness and Resistance).” Lives on the affected Pokémon and expires when the effect owner next starts a turn.
     */
    public static ContinuousEffect attackDamagePenalty(UUID owner, int amount) {
        return new ContinuousEffect(Kind.ATTACK_DAMAGE_PENALTY, owner, null, null, amount, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：在效果拥有者下个回合开始前，这只宝可梦的攻击伤害减少 " + amount + "（计算弱点和抵抗力前）", null, 0);
    }
    /**
     * “During your next turn, this Pokémon's attacks do N more damage to your opponent's Active Pokémon (before applying
     * Weakness and Resistance).” Lives on the attacker through its owner's next turn.
     */
    public static ContinuousEffect attackDamageBonus(UUID owner, int amount, int expiresAfterOwnerTurn) {
        return attackDamageBonus(owner, amount, expiresAfterOwnerTurn, null);
    }
    /** An optional printed attack id limits the bonus to that declared attack; null keeps the legacy all-attacks rule. */
    public static ContinuousEffect attackDamageBonus(UUID owner, int amount, int expiresAfterOwnerTurn, String attackId) {
        return new ContinuousEffect(Kind.ATTACK_DAMAGE_BONUS, owner, null, null, amount, 0, Expiry.OWNER_TURN_END,
            "持续效果：效果拥有者下个回合，这只宝可梦的" + (attackId == null ? "招式" : "「" + attackId + "」")
                + "对对方战斗宝可梦多造成 " + amount + " 点伤害（计算弱点和抵抗力前）", attackId, expiresAfterOwnerTurn);
    }
    /** “During your next turn, the Defending Pokémon takes N more damage from attacks (after applying Weakness and Resistance).” */
    public static ContinuousEffect damageTakenIncrease(UUID owner, int amount, int expiresAfterOwnerTurn) {
        return new ContinuousEffect(Kind.DAMAGE_TAKEN_INCREASE, owner, null, null, amount, 0, Expiry.OWNER_TURN_END,
            "持续效果：效果拥有者下个回合，这只宝可梦受到的招式伤害增加 " + amount + "（计算弱点和抵抗力后）", null, expiresAfterOwnerTurn);
    }
    public static ContinuousEffect nextTurnAttackBase(UUID owner,String attackId,int amount,int expiresAfterOwnerTurn) {
        return new ContinuousEffect(Kind.NEXT_TURN_ATTACK_BASE,owner,null,null,amount,0,Expiry.OWNER_TURN_END,
            "持续效果：下个自己回合，指定招式的基础伤害改为 "+amount,attackNameKey(attackId),expiresAfterOwnerTurn);
    }
    /** “If the Defending Pokémon tries to attack during your opponent's next turn, your opponent flips a coin. If tails, that attack does nothing.” */
    public static ContinuousEffect attackCoinGate(UUID owner) {return attackCoinGate(owner,1);}
    public static ContinuousEffect attackCoinGate(UUID owner,int flips) {
        new dev.hua.ptcglive.engine.effect.AttackCoinGate(flips);
        return new ContinuousEffect(Kind.ATTACK_COIN_GATE, owner, null, null, flips==1?0:2, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：在效果拥有者下个回合开始前，这只宝可梦攻击前掷"+flips+"次硬币，须全部正面", null, 0);
    }
    public static ContinuousEffect attackCostIncrease(UUID owner,int amount) {
        return new ContinuousEffect(Kind.ATTACK_COST_MODIFIER,owner,null,null,0,amount,Expiry.OWNER_NEXT_TURN_START,
            "持续效果：下个对手回合，此宝可梦的招式费用增加 "+amount+" 个无色能量",null,0);
    }
    /** A card effect can prohibit normal retreat until its controller next starts a turn. */
    public static ContinuousEffect retreatLock(UUID owner) {
        return new ContinuousEffect(Kind.RETREAT_LOCK, owner, null, null, 0, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：在效果拥有者下个回合开始前不能撤退", null, 0);
    }
    /** A card effect can prohibit attacks by this Pokémon until its controller's next turn. */
    public static ContinuousEffect attackLock(UUID owner) {
        return new ContinuousEffect(Kind.ATTACK_LOCK, owner, null, null, 0, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：在效果拥有者下个回合开始前不能攻击", null, 0);
    }
    /** “During your next turn, this Pokémon can't attack.”: lasts through the controller's next turn, then expires at its end. */
    public static ContinuousEffect attackLockThroughOwnerNextTurn(UUID owner, int expiresAfterOwnerTurn) {
        return new ContinuousEffect(Kind.ATTACK_LOCK, owner, null, null, 0, 0, Expiry.OWNER_TURN_END,
            "持续效果：在效果拥有者下个回合结束前不能攻击", null, expiresAfterOwnerTurn);
    }
    /** A reviewed effect can prohibit evolution until its controller's next turn. */
    public static ContinuousEffect handEnergyLock(UUID owner) {
        return new ContinuousEffect(Kind.HAND_ENERGY_LOCK,owner,null,null,0,0,Expiry.OPPONENT_TURN_END,
            "持续效果：对手回合不能从其手牌向此宝可梦附着能量",null,0);
    }
    public static ContinuousEffect handEvolutionLock(UUID owner) {
        return new ContinuousEffect(Kind.HAND_EVOLUTION_LOCK,owner,null,null,0,0,Expiry.OWNER_NEXT_TURN_START,
            "持续效果：在来源玩家下回合开始前，不能从手牌进化此宝可梦",null,0);
    }
    public static ContinuousEffect evolutionLock(UUID owner) {
        return new ContinuousEffect(Kind.EVOLUTION_LOCK, owner, null, null, 0, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：在效果拥有者下个回合开始前不能进化", null, 0);
    }
    /** Positive values increase retreat cost; negative values reduce it. */
    public static ContinuousEffect retreatCostModifier(UUID owner, int change) {
        String label = "持续效果：在效果拥有者下个回合开始前撤退费用" + (change > 0 ? "增加 " : "减少 ") + Math.abs(change);
        return new ContinuousEffect(Kind.RETREAT_COST_MODIFIER, owner, null, null, 0, change, Expiry.OWNER_NEXT_TURN_START, label, null, 0);
    }
    /** This attack remains unavailable through its owner's following turn, then expires at that turn's end. */
    /**
     * Pokémon TCG restrictions that say “this Pokémon can't use [name]” are
     * keyed by the printed attack name, not a database-local attack id.  This
     * also makes a copied attack produce the same future restriction as the
     * original printed attack.
     */
    public static ContinuousEffect attackCooldown(UUID owner, String attackNameZh, int expiresAfterOwnerTurn) {
        return new ContinuousEffect(Kind.ATTACK_ID_LOCK, owner, null, null, 0, 0, Expiry.OWNER_TURN_END,
            "持续效果：下个自己的回合不能使用攻击「" + attackNameZh + "」", attackNameKey(attackNameZh), expiresAfterOwnerTurn);
    }
    /** “This Pokémon can't use [name] again until it leaves the Active Spot.” */
    public static ContinuousEffect attackLockUntilLeavesActive(UUID owner, String attackNameZh) {
        return new ContinuousEffect(Kind.ATTACK_ID_LOCK, owner, null, null, 0, 0, Expiry.LEAVES_PLAY,
            "持续效果：离开战斗位前不能再使用攻击「" + attackNameZh + "」", attackNameKey(attackNameZh), 0);
    }
    /** “Choose 1 of your opponent's Active Pokémon's attacks. During your opponent's next turn, that Pokémon can't use that attack.” */
    public static ContinuousEffect opponentAttackLock(UUID owner, String attackNameZh) {
        return new ContinuousEffect(Kind.ATTACK_ID_LOCK, owner, null, null, 0, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：在效果拥有者下个回合开始前不能使用攻击「" + attackNameZh + "」", attackNameKey(attackNameZh), 0);
    }
    /** Attached Tools remain attached, but their effects and capacity contribution are inactive. */
    public static ContinuousEffect toolEffectsDisabled(UUID owner) {
        return new ContinuousEffect(Kind.TOOL_EFFECTS_DISABLED, owner, null, null, 0, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：附着的宝可梦工具失效", null, 0);
    }
    /** Printed Abilities remain visible on the card, but neither activated nor passive text may resolve. */
    public static ContinuousEffect abilityEffectsDisabled(UUID owner) {
        return new ContinuousEffect(Kind.ABILITY_EFFECTS_DISABLED, owner, null, null, 0, 0, Expiry.OWNER_NEXT_TURN_START,
            "持续效果：该宝可梦的特性失效", null, 0);
    }
    public boolean appliesToAttackFrom(UUID attacker) {
        return kind == Kind.ATTACK_PROTECTION && opposingPlayer.equals(attacker);
    }
    public boolean reducesDamageFrom(UUID attacker) { return kind == Kind.DAMAGE_REDUCTION && opposingPlayer.equals(attacker); }
    public boolean guardsAttackDamageMaximum(UUID attacker) {return kind==Kind.ATTACK_DAMAGE_MAX_GUARD&&!owner.equals(attacker);}
    public boolean penalizesAttackDamage() { return kind == Kind.ATTACK_DAMAGE_PENALTY; }
    public boolean boostsAttackDamage() { return kind == Kind.ATTACK_DAMAGE_BONUS; }
    public boolean boostsAttackDamage(String declaredAttackId) {
        return boostsAttackDamage() && (lockedAttackId == null || lockedAttackId.equals(declaredAttackId));
    }
    public boolean increasesDamageTaken() { return kind == Kind.DAMAGE_TAKEN_INCREASE; }
    public boolean overridesAttackBase(String attackId) {return kind==Kind.NEXT_TURN_ATTACK_BASE&&lockedAttackId.equals(attackNameKey(attackId));}
    public boolean gatesAttackWithCoin() { return kind == Kind.ATTACK_COIN_GATE; }
    public int attackGateFlips() {return gatesAttackWithCoin()?(damageReduction==0?1:damageReduction):0;}
    public boolean retaliatesAgainst(UUID attacker) { return kind == Kind.RETALIATION && opposingPlayer.equals(attacker); }
    /** Damage counters to put on the Attacking Pokémon for {@code damageDone} attack damage to this Pokémon. */
    public int retaliationCounters(int damageDone) {
        if (kind != Kind.RETALIATION) throw new IllegalStateException("不是受击反伤持续效果");
        return damageReduction > 0 ? damageReduction : damageDone / 10;
    }
    public boolean preventsRetreat() { return kind == Kind.RETREAT_LOCK; }
    public boolean preventsAttack() { return kind == Kind.ATTACK_LOCK; }
    public boolean preventsEvolution() { return kind == Kind.EVOLUTION_LOCK; }
    public boolean preventsAttackNamed(String attackNameZh) {
        return kind == Kind.ATTACK_ID_LOCK && lockedAttackId.equals(attackNameKey(attackNameZh));
    }
    private static String attackNameKey(String attackNameZh) {
        if (attackNameZh == null) throw new IllegalArgumentException("攻击名称不能为空");
        String normalized = attackNameZh.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("攻击名称不能为空");
        return normalized;
    }
    public boolean modifiesRetreatCost() { return kind == Kind.RETREAT_COST_MODIFIER; }
    public boolean disablesToolEffects() { return kind == Kind.TOOL_EFFECTS_DISABLED; }
    public boolean disablesAbilityEffects() { return kind == Kind.ABILITY_EFFECTS_DISABLED; }
}
