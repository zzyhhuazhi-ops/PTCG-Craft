package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import java.util.*;

/** Reject an unsupported HP/provision cycle before setup, never replace live provision with printed units. */
public final class HitPointEnergyDependencies {
    private HitPointEnergyDependencies() { }
    public static boolean readsEnergy(EffectDefinition effect) {
        return effect.type().equals("hit_points_modifier")&&effect.arguments().containsKey("minimum_energy_units")
            ||effect.effects().stream().anyMatch(HitPointEnergyDependencies::readsEnergy);
    }
    private static boolean readsHp(ConditionDefinition condition) {
        return Set.of(ConditionType.HP_ABOVE,ConditionType.HP_BELOW).contains(condition.type())
            ||condition.children().stream().anyMatch(HitPointEnergyDependencies::readsHp);
    }
    public static boolean readsHp(EnergyProfile energy) {
        return energy.provisionRules().stream().flatMap(r->r.conditions().stream()).anyMatch(HitPointEnergyDependencies::readsHp);
    }
    public static Optional<String> unsupportedReason(CombatCatalog combat,Collection<CardInstance> cards) {
        boolean energyHp=false,hpEnergy=false;
        for(String id:cards.stream().map(CardInstance::definitionId).distinct().toList()) {
            var energy=combat.energyProfile(id).orElse(null);energyHp|=energy!=null&&readsHp(energy);
            var pokemon=combat.pokemon(id).orElse(null);
            if(pokemon!=null)hpEnergy|=pokemon.abilities().stream().flatMap(a->a.effectProgram().triggers().stream())
                .flatMap(b->b.effects().stream()).anyMatch(HitPointEnergyDependencies::readsEnergy);
        }
        return energyHp&&hpEnergy?Optional.of("能量供给依赖剩余HP，同时HP又依赖能量供给；此循环组合尚未核验，不能开始对局"):Optional.empty();
    }
}
