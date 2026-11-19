package dev.hua.ptcglive.engine.field;

import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import java.util.Objects;

/** Versioned, data-driven Pokémon Ability. It never carries GUI or client authority. */
public record AbilityDefinition(
    String id,
    String nameZh,
    AbilityEffect effect,
    EffectSupport effectSupport,
    String unsupportedReasonZh,
    Kind kind,
    EffectProgram effectProgram,
    dev.hua.ptcglive.engine.effect.AbilityEventPolicy eventPolicy,
    dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit,
    /** Printed activation consequence: finish the complete ability and its reactions, then end the turn. */
    boolean endsTurn
) implements dev.hua.ptcglive.engine.effect.AutomaticEffectDefinition {
    public AbilityDefinition(String id, String nameZh, AbilityEffect effect, EffectSupport effectSupport, String unsupportedReasonZh, Kind kind, EffectProgram effectProgram, dev.hua.ptcglive.engine.effect.AbilityEventPolicy eventPolicy, dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit) {
        this(id,nameZh,effect,effectSupport,unsupportedReasonZh,kind,effectProgram,eventPolicy,usageLimit,false);
    }
    public enum Kind { ACTIVATED_ONCE_PER_TURN, ACTIVATED_UNLIMITED, ACTIVATED_ONCE_PER_GAME, PASSIVE, TRIGGERED }
    public AbilityDefinition(String id, String nameZh, AbilityEffect effect, EffectSupport effectSupport, String unsupportedReasonZh, Kind kind, EffectProgram effectProgram, dev.hua.ptcglive.engine.effect.AbilityEventPolicy eventPolicy) {
        this(id,nameZh,effect,effectSupport,unsupportedReasonZh,kind,effectProgram,eventPolicy,null);
    }
    public AbilityDefinition(String id, String nameZh, AbilityEffect effect, EffectSupport effectSupport, String unsupportedReasonZh, Kind kind, EffectProgram effectProgram) {
        this(id, nameZh, effect, effectSupport, unsupportedReasonZh, kind, effectProgram, null);
    }
    public AbilityDefinition(String id, String nameZh, AbilityEffect effect, EffectSupport effectSupport, String unsupportedReasonZh) {
        this(id, nameZh, effect, effectSupport, unsupportedReasonZh, Kind.ACTIVATED_ONCE_PER_TURN, EffectProgram.EMPTY);
    }
    /** Existing audited opcode data remains a compatible empty EffectProgram during migration. */
    public AbilityDefinition(String id, String nameZh, AbilityEffect effect, EffectSupport effectSupport, String unsupportedReasonZh, Kind kind) {
        this(id, nameZh, effect, effectSupport, unsupportedReasonZh, kind, EffectProgram.EMPTY);
    }
    public AbilityDefinition {
        if (id == null || !id.matches("[a-z0-9-]+")) throw new IllegalArgumentException("特性 ID 无效");
        if (nameZh == null || nameZh.isBlank()) throw new IllegalArgumentException("特性名称不能为空");
        Objects.requireNonNull(effect, "特性效果");
        Objects.requireNonNull(effectSupport, "特性支持状态");
        Objects.requireNonNull(kind, "特性类型");
        if (endsTurn && (kind == Kind.PASSIVE || effect.opcode() != AbilityEffect.Opcode.COMPOSED))
            throw new IllegalArgumentException("发动后结束回合须使用可续接的主动或已审核触发特性");
        Objects.requireNonNull(effectProgram, "特性 Effect 程序");
        if (kind == Kind.ACTIVATED_ONCE_PER_GAME && (usageLimit == null
            || usageLimit.period() != dev.hua.ptcglive.engine.effect.SharedUsageLimit.Period.GAME))
            throw new IllegalArgumentException("整局一次主动特性必须明确玩家GAME共享组，不能用在场实例限次代替");
        if(usageLimit!=null && (kind==Kind.PASSIVE || usageLimit.group().equals(dev.hua.ptcglive.engine.effect.SharedUsageLimit.GX)
            || kind==Kind.TRIGGERED && usageLimit.group().equals(dev.hua.ptcglive.engine.effect.SharedUsageLimit.VSTAR)))
            throw new IllegalArgumentException("被动特性不能声明使用次数；GX仅限攻击，VSTAR须由玩家主动使用");
        if ((kind == Kind.TRIGGERED) != (eventPolicy != null)) throw new IllegalArgumentException("触发特性必须且只能声明 eventPolicy");
        if (kind == Kind.TRIGGERED && effect.opcode() != AbilityEffect.Opcode.COMPOSED) throw new IllegalArgumentException("触发特性只能使用组合效果");
        if ((effect.opcode() == AbilityEffect.Opcode.COMPOSED) == effectProgram.isEmpty())
            throw new IllegalArgumentException("组合特性必须仅使用 EffectProgram；不能与旧操作码混用或留空");
        if (effectSupport == EffectSupport.UNSUPPORTED && (unsupportedReasonZh == null || unsupportedReasonZh.isBlank()))
            throw new IllegalArgumentException("未支持特性必须说明原因");
        // Unsupported card text retains its declared kind for clear UI reporting,
        // but only an actually supported passive may alter server state.
        dev.hua.ptcglive.engine.effect.AbilitySuppressionProgram.validateZoneSource(effectProgram,id);
        if(dev.hua.ptcglive.engine.effect.AbilitySuppressionProgram.zoneSourceId(effectProgram)!=null&&kind!=Kind.PASSIVE)throw new IllegalArgumentException("跨区域特性移除必须为被动特性");
        boolean composedPassive = kind == Kind.PASSIVE && effect.opcode() == AbilityEffect.Opcode.COMPOSED && !effectProgram.isEmpty();
        if (effectSupport == EffectSupport.SUPPORTED && kind == Kind.PASSIVE && !effect.passiveOnly() && !composedPassive)
            throw new IllegalArgumentException("已支持的被动特性必须使用被动操作码或仅含PASSIVE的组合Effect");
        if (effectSupport == EffectSupport.SUPPORTED && composedPassive)
            dev.hua.ptcglive.engine.effect.PassiveModifierProgram.validate(effectProgram,
                dev.hua.ptcglive.engine.effect.PassiveModifierProgram.Source.ABILITY);
        if (kind != Kind.PASSIVE && kind != Kind.TRIGGERED && effect.passiveOnly())
            throw new IllegalArgumentException("被动特性操作码不能伪装成可点击特性");
    }

    public boolean oncePerTurn() { return kind == Kind.ACTIVATED_ONCE_PER_TURN; }
    public boolean composed() { return effect.opcode() == AbilityEffect.Opcode.COMPOSED; }
    public boolean activated() { return kind == Kind.ACTIVATED_ONCE_PER_TURN || kind == Kind.ACTIVATED_UNLIMITED || kind == Kind.ACTIVATED_ONCE_PER_GAME; }
    public EffectTrigger activationTrigger() {
        return switch (kind) {
            case ACTIVATED_ONCE_PER_TURN -> EffectTrigger.ONCE_PER_TURN;
            case ACTIVATED_UNLIMITED -> EffectTrigger.ON_ACTIVATE;
            case ACTIVATED_ONCE_PER_GAME -> EffectTrigger.ONCE_PER_GAME;
            default -> throw new IllegalStateException("被动／触发特性不具有主动发动入口");
        };
    }
    public String activationLabelZh() {
        String local = switch (kind) {
            case ACTIVATED_UNLIMITED -> "本回合可多次使用";
            case ACTIVATED_ONCE_PER_GAME -> "整局共享一次";
            case ACTIVATED_ONCE_PER_TURN -> "每只宝可梦每回合一次";
            default -> "不可主动发动";
        };
        if (usageLimit != null && kind != Kind.ACTIVATED_ONCE_PER_GAME) local += "，另受玩家共享次数限制";
        return endsTurn ? local + "，结算后结束回合" : local;
    }
    public boolean triggered() { return kind == Kind.TRIGGERED; }
}
