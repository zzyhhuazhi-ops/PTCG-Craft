package dev.hua.ptcglive.engine;

import java.util.*;

/** Owner-only draft. Public targets, no Effect code, hidden cards, random seed or executable client command. */
public record DamageAllocationPrompt(UUID resolutionId, long step, int budget, int minimumTargets, int maximumTargets,
                                     List<Target> candidates, Map<UUID,Integer> counters, boolean optional, String actionZh, Map<UUID,Integer> limits) {
    public DamageAllocationPrompt(UUID resolutionId,long step,int budget,int minimumTargets,int maximumTargets,List<Target> candidates,Map<UUID,Integer> counters) {
        this(resolutionId,step,budget,minimumTargets,maximumTargets,candidates,counters,false,"分配伤害指示物",Map.of());
    }
    public int limit(UUID pokemonId) { return limits.getOrDefault(pokemonId,budget); }
    public record Target(UUID owner, UUID pokemonId) { }
    public DamageAllocationPrompt { candidates = List.copyOf(candidates); counters = Map.copyOf(counters); limits=Map.copyOf(limits); }
    public int allocated() { return counters.values().stream().mapToInt(Integer::intValue).sum(); }
    public boolean canConfirm() { return (optional ? allocated() <= budget : allocated() == budget) && counters.entrySet().stream().allMatch(e->e.getValue()>0&&e.getValue()<=limit(e.getKey())) && counters.size() >= minimumTargets && counters.size() <= maximumTargets; }
}
