package dev.hua.ptcglive.view;

import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ActionType;
import java.util.*;

/**
 * Presentation-only matching for card-table clicks and drops. It narrows the
 * server-sent option list; it never creates options or interprets card text.
 */
public final class MatchActionMatcher {
    private MatchActionMatcher() { }
    /** Shared by the table and the operation-path regression audit. */
    public static List<MatchSession.Option> flowOptions(MatchSession.Snapshot snapshot) {
        var choices = EffectChoicePage.all(snapshot);
        if (!choices.isEmpty()) return choices;
        return snapshot.options().stream().filter(option -> (option.cardKeys().isEmpty() || option.type()==ActionType.DISCARD_ABILITY)
            && option.type()!=ActionType.END_TURN && option.type()!=ActionType.CONCEDE && option.type()!=ActionType.PRIZE_PICK).toList();
    }
    /** Labels are presentation hints only; execution still revalidates the server-issued option. */
    public static boolean isAttackOption(MatchSession.Option option) {
        return !option.cardKeys().isEmpty()&&option.type()==dev.hua.ptcglive.engine.ActionType.ATTACK;
    }
    /** Availability is issued by the server, independently of the translated explanation. */
    public static boolean isAvailableAttackOption(MatchSession.Option option) {
        return option.enabled() && isAttackOption(option);
    }
    /** Same card-face panel for attacks, activated Abilities and paid retreat. Targets are not action sources. */
    public static List<MatchSession.Option> cardFaceActions(List<MatchSession.Option> options,String key,boolean allowAttacks) {
        return options.stream().filter(o->o.enabled()&&!o.cardKeys().isEmpty()&&o.cardKeys().getFirst().equals(key))
            .filter(o->switch(o.type()) {
                case ATTACK -> allowAttacks && isAvailableAttackOption(o);
                case ABILITY, RETREAT, TOOL_ABILITY, FOSSIL_DISCARD, DOLL_RETURN -> true;
                default -> false;
            }).toList();
    }
    public static boolean hasPokemonChoice(MatchSession.Snapshot snapshot) {
        return snapshot.options().stream().anyMatch(option -> option.cardKeys().contains(MatchSession.POKEMON_SELECTION_KEY));
    }
    public static boolean isArenaPlay(MatchSession.Option option) {
        return option.enabled() && (option.labelZh().startsWith("使用：") || option.labelZh().startsWith("放置场地："));
    }
    /** Replacing the shared Stadium is a one-card play, not an action targeting the old card. */
    public static List<MatchSession.Option> forDrop(MatchSession.Snapshot snapshot,String source,String target) {
        if(source.startsWith("hand:")&&target.startsWith("stadium:"))
            return forCards(snapshot,List.of(source)).stream().filter(o->o.labelZh().startsWith("放置场地：")).toList();
        return forCards(snapshot,List.of(source,target));
    }
    public static List<MatchSession.Option> forCards(MatchSession.Snapshot snapshot, Collection<String> visibleCardKeys) {
        Objects.requireNonNull(snapshot, "对局快照"); Objects.requireNonNull(visibleCardKeys, "卡面锚点");
        Set<String> required = new LinkedHashSet<>();
        for (String key : visibleCardKeys) if (key != null && !key.isBlank()) required.add(key);
        if (required.isEmpty()) return List.of();
        return snapshot.options().stream().filter(option -> option.enabled() && option.cardKeys().containsAll(required)).toList();
    }
}
