package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.Map;
import java.util.Set;

/**
 * Declarative attachment of one Pokémon Tool from an owner-private zone to
 * that owner's public Pokémon. The one-card limit is deliberate: repeated
 * attachment must use RepeatEffect so each Tool's capacity modifier is
 * recomputed by the server before the next attachment.
 */
public record AttachToolEffect(EffectTargetResolution.Zone source, int minimum, Set<String> allowedDefinitionIds,
                               ServerMatch.InPlayTargetScope targetScope) {
    public static final Set<String> FIELDS = Set.of("amount", "minimum", "source", "target_scope", "definition_ids");

    public static boolean handles(EffectDefinition effect) { return effect.type().equals("attach_tool"); }

    public static AttachToolEffect from(EffectDefinition effect) {
        if (!handles(effect) || !effect.effects().isEmpty()) throw new IllegalArgumentException("无效的工具附着原子效果");
        if (!FIELDS.containsAll(effect.arguments().keySet())) throw new IllegalArgumentException("工具附着含未支持参数");
        Map<String, String> args = effect.arguments();
        if (!args.containsKey("minimum")) throw new IllegalArgumentException("工具附着必须明确 minimum");
        var source = zone(required(args, "source"));
        if (!Set.of(EffectTargetResolution.Zone.HAND,EffectTargetResolution.Zone.DECK,EffectTargetResolution.Zone.DISCARD).contains(source))
            throw new IllegalArgumentException("通用道具附着只接受手牌、牌库或弃牌区来源");
        int amount = integer(args, "amount", 1, 1);
        int minimum = integer(args, "minimum", 0, amount);
        var scope = scope(required(args, "target_scope"));
        boolean compatible = switch (effect.target()) {
            case SELF -> scope == ServerMatch.InPlayTargetScope.OWN_FIELD;
            case CHOSEN_POKEMON -> scope == ServerMatch.InPlayTargetScope.OWN_FIELD || scope == ServerMatch.InPlayTargetScope.OWN_BENCH;
            case SELECT_OWN_POKEMON -> scope == ServerMatch.InPlayTargetScope.OWN_FIELD || scope == ServerMatch.InPlayTargetScope.OWN_BENCH;
            case OWN_ACTIVE -> scope == ServerMatch.InPlayTargetScope.OWN_ACTIVE;
            default -> false;
        };
        if (!compatible) throw new IllegalArgumentException("工具附着仅可选择己方宝可梦，且 target_scope 必须一致");
        var selection = EffectCardSelectionSpec.from(effect);
        return new AttachToolEffect(source, minimum, selection.allowedDefinitionIds(), scope);
    }

    public boolean allows(CardDefinition card, dev.hua.ptcglive.rules.TrainerClassificationRules classification) {
        return card.category() == dev.hua.ptcglive.rules.CardCategory.TRAINER
            && classification.matches(card, TrainerCategory.TOOL)
            && (allowedDefinitionIds.isEmpty() || allowedDefinitionIds.contains(card.id()));
    }

    private static EffectTargetResolution.Zone zone(String value) {
        try { return EffectTargetResolution.Zone.valueOf(value); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("工具附着来源区域无效", invalid); }
    }
    private static ServerMatch.InPlayTargetScope scope(String value) {
        try { return ServerMatch.InPlayTargetScope.valueOf(value); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("工具附着目标范围无效", invalid); }
    }
    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("工具附着缺少参数：" + key);
        return value;
    }
    private static int integer(Map<String, String> values, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(required(values, key));
            if (value < minimum || value > maximum) throw new IllegalArgumentException("工具附着参数超出范围：" + key);
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("工具附着参数不是整数：" + key, invalid);
        }
    }
}
