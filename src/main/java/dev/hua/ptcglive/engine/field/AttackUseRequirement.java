package dev.hua.ptcglive.engine.field;

import java.util.Objects;

/** Printed conditions that prevent an attack from being declared at all. */
public record AttackUseRequirement(Type type, String pokemonName, String attackId) {
    public enum Type { NO_SUPPORTER_IN_DISCARD, LOST_ZONE_TEN_CARDS, PREVIOUS_OWN_TURN_NAMED_ATTACK, PREVIOUS_OWN_TURN_ATTACK_NOT_USED, PREVIOUS_OWN_TURN_SOURCE_ATTACK }

    public AttackUseRequirement {
        Objects.requireNonNull(type, "招式使用条件类型");
        if (type == Type.NO_SUPPORTER_IN_DISCARD || type == Type.LOST_ZONE_TEN_CARDS) {
            if (pokemonName != null || attackId != null)
                throw new IllegalArgumentException("区域数量条件不能附带宝可梦或招式身份");
        } else if (type == Type.PREVIOUS_OWN_TURN_ATTACK_NOT_USED || type == Type.PREVIOUS_OWN_TURN_SOURCE_ATTACK) {
            if (pokemonName != null || attackId == null || !attackId.matches("[a-z0-9-]+"))
                throw new IllegalArgumentException("上回合己方招式禁用条件须只指定招式 ID");
        } else if (pokemonName == null || pokemonName.isBlank() || !pokemonName.equals(pokemonName.trim())
            || pokemonName.length() > 120 || attackId == null || !attackId.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("上回合招式条件必须指定完整宝可梦名称和招式 ID");
        }
    }
}
