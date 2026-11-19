package dev.hua.ptcglive.engine;

import java.util.*;

/** Recipient-specific read-only guidance. No commands, hidden card facts or prediction RNG. */
public record BattleGuidance(List<String> turnUsage,Map<String,String> blockedCards,
                             Map<String,List<String>> fieldStatus,List<AttackPreview> attacks) {
    public BattleGuidance {turnUsage=List.copyOf(turnUsage);blockedCards=Map.copyOf(blockedCards);
        var copy=new LinkedHashMap<String,List<String>>();fieldStatus.forEach((key,value)->copy.put(key,List.copyOf(value)));fieldStatus=Map.copyOf(copy);attacks=List.copyOf(attacks);}
    public static final BattleGuidance EMPTY=new BattleGuidance(List.of(),Map.of(),Map.of(),List.of());
    public record AttackPreview(String sourceKey,String name,String cost,boolean payable,String certainty,Integer damage,String explanation) {}
}
