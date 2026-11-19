package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;

/** Ether and Gutsy Pickaxe reveal one card, then attach it or retain its exact position. */
public final class TopEnergyReveal {
    private TopEnergyReveal() { }
    public static final String TYPE="reveal_top_attach_energy";
    public static final Set<String> FIELDS=Set.of("basic_energy_only","energy_type","destination","remainder");
    public static boolean benchOnly(EffectDefinition e){return "OWN_BENCH".equals(e.arguments().get("destination"));}
    public static void validate(EffectDefinition e) {
        boolean ether=e.arguments().equals(Map.of("basic_energy_only","true","destination","OWN_FIELD","remainder","KEEP"));
        boolean pickaxe=e.arguments().equals(Map.of("basic_energy_only","true","energy_type","FIGHTING","destination","OWN_BENCH","remainder","HAND"));
        if(!e.type().equals(TYPE)||e.target()!=EffectTarget.DECK||!e.conditions().isEmpty()||!e.effects().isEmpty()||!ether&&!pickaxe)
            throw new IllegalArgumentException("Top Energy reveal must use the reviewed Ether or Gutsy Pickaxe rule");
    }
}
