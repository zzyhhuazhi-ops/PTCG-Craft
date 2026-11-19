package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;

/**
 * A parameterized selection of public Pokémon Tools already attached to one
 * or more public Pokémon. The client may only choose a server-issued Tool
 * instance; the Battle Engine rechecks both the host entry and attachment
 * before moving the card.
 */
public record AttachedToolEffect(Mode mode, int minimum, int maximum) {
    public enum Mode { DISCARD, RETURN_TO_HAND }
    /** {@code bind}: an attack's cost records how many Tools it actually discarded (AttackCountBindings). */
    public static final Set<String> FIELDS = Set.of("amount", "minimum", "bind", "both_sides", "definition_ids");
    public static Set<String> definitionIds(EffectDefinition effect) {
        return effect.arguments().containsKey("definition_ids")?TurnHistoryCondition.values(effect.arguments().get("definition_ids")):Set.of();
    }

    public static boolean handles(EffectDefinition effect) {
        return effect.type().equals("discard_tool") || effect.type().equals("return_tool");
    }

    public static AttachedToolEffect from(EffectDefinition effect) {
        if (!handles(effect) || !effect.effects().isEmpty()) throw new IllegalArgumentException("无效的附着工具选择效果");
        if (!FIELDS.containsAll(effect.arguments().keySet())) throw new IllegalArgumentException("附着工具选择含未支持参数");
        if (!isFixedPokemonTarget(effect.target()))
            throw new IllegalArgumentException("附着工具选择必须指定已公开的固定场上宝可梦区域");
        if (effect.arguments().containsKey("both_sides") && (effect.target() != EffectTarget.ALL_OWN_POKEMON
            || !Set.of("true", "false").contains(effect.arguments().get("both_sides"))))
            throw new IllegalArgumentException("双方工具选择须以 ALL_OWN_POKEMON 为起点并明确布尔值 both_sides");
        int maximum = integer(effect.arguments(), "amount", 1, 60);
        if (!effect.arguments().containsKey("minimum"))
            throw new IllegalArgumentException("附着工具选择必须明确 minimum");
        int minimum = integer(effect.arguments(), "minimum", 0, maximum);
        Mode mode = effect.type().equals("discard_tool") ? Mode.DISCARD : Mode.RETURN_TO_HAND;
        if(effect.arguments().containsKey("definition_ids")) {
            if(mode!=Mode.DISCARD||effect.target()!=EffectTarget.SELF||minimum!=60||maximum!=60
                ||!effect.arguments().keySet().equals(Set.of("amount","minimum","bind","definition_ids")))
                throw new IllegalArgumentException("按编号弃工具只核验攻击者全部指定工具并记录实际弃置数");
            for(String id:definitionIds(effect))if(!id.matches("[a-z0-9][a-z0-9_:-]*"))throw new IllegalArgumentException("工具编号无效");
        }
        if (effect.arguments().containsKey("bind")) {
            if (mode != Mode.DISCARD) throw new IllegalArgumentException("只有弃置工具可以记录攻击计数 bind");
            AttackCountBindings.name(effect.arguments().get("bind"));
        }
        return new AttachedToolEffect(mode, minimum, maximum);
    }

    private static boolean isFixedPokemonTarget(EffectTarget target) {
        return switch (target) {
            // The server continuation keeps the selected host's entry sequence.
            case SELF, CHOSEN_POKEMON, OWN_ACTIVE, OPPONENT_ACTIVE, OWN_BENCH, OPPONENT_BENCH, ALL_OWN_POKEMON, ALL_OPPONENT_POKEMON -> true;
            default -> false;
        };
    }

    private static int integer(Map<String, String> values, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(values.get(key));
            if (value < minimum || value > maximum) throw new IllegalArgumentException("附着工具选择参数超出范围：" + key);
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("附着工具选择参数不是整数：" + key, invalid);
        }
    }
}
