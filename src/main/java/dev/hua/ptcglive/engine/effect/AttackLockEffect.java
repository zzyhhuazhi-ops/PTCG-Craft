package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/**
 * prevent_attack's printed duration. The default lasts until the source
 * player's next turn starts (“The Defending Pokémon can't attack during your
 * opponent's next turn.”); SOURCE_NEXT_TURN_END also covers the source
 * player's own next turn (“During your next turn, this Pokémon can't attack.”).
 */
public enum AttackLockEffect {
    SOURCE_NEXT_TURN_START, SOURCE_NEXT_TURN_END;

    public static final Set<String> FIELDS = Set.of("duration");

    public static AttackLockEffect from(EffectDefinition effect) {
        if (!effect.type().equals("prevent_attack")) throw new IllegalArgumentException("不是禁止攻击效果");
        if (!effect.effects().isEmpty() || !FIELDS.containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("prevent_attack 只接受 duration，不接受其它参数或子效果");
        String raw = effect.arguments().get("duration");
        if (raw == null) return SOURCE_NEXT_TURN_START;
        try { return valueOf(raw.trim()); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("prevent_attack 的 duration 只能是 SOURCE_NEXT_TURN_START 或 SOURCE_NEXT_TURN_END", invalid);
        }
    }
}
