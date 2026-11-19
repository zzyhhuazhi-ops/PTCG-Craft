package dev.hua.ptcglive.engine.effect;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import dev.hua.ptcglive.engine.field.EnergyType;

/**
 * Data-only query for printed Abilities that are suppressed by a persistent
 * card.  This is deliberately separate from {@code disable_abilities}: that
 * effect creates a temporary lock on one Pokémon, whereas this program is
 * recalculated from the currently in-play Tool, Stadium, or Energy source.
 */
public final class AbilitySuppressionProgram {
    public enum Scope {
        ALL_IN_PLAY, ALL_BENCH, OWN_IN_PLAY, OPPONENT_IN_PLAY, OPPONENT_ACTIVE, FIELD_HAND_DISCARD;

        static Scope parse(String value) {
            try { return valueOf(Objects.requireNonNull(value, "scope").trim().toUpperCase(Locale.ROOT)); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("特性压制范围无效：" + value, invalid); }
        }
    }

    private AbilitySuppressionProgram() { }

    public static boolean handles(String type) { return "suppress_abilities".equals(type); }
    public static boolean contains(EffectDefinition effect){return handles(effect.type())||effect.effects().stream().anyMatch(AbilitySuppressionProgram::contains);}
    public static void validateDependency(ConditionDefinition condition) {
        if(condition.type()==ConditionType.DISCARD_COUNT&&condition.arguments().containsKey("ability_id"))throw new IllegalArgumentException("特性移除不能依赖弃牌区当前特性数量");
        if(condition.type()==ConditionType.HAS_ABILITIES)throw new IllegalArgumentException("特性移除不能反过来依赖当前是否拥有特性");
        condition.children().forEach(AbilitySuppressionProgram::validateDependency);
    }

    /** Schema validation shared by the registry and the persistent-source validator. */
    public static void validateShape(EffectDefinition effect) {
        if(zoneSource(effect)||basicZoneStadium(effect))return;
        if (!handles(effect.type()) || effect.target() != EffectTarget.SELF || !effect.effects().isEmpty()
            || !java.util.Set.of("scope", "pokemon_type", "pokemon_stage", "evolution_only", "basic_only", "exclude_rule_box", "source_active_only", "source_benched_only", "source_rule_box_only", "rule_box_only", "pokemon_families", "family_match", "except_pokemon_families", "damage_counters_min", "except_ability_id").containsAll(effect.arguments().keySet())
            || !effect.arguments().containsKey("scope"))
            throw new IllegalArgumentException("特性压制必须声明 scope，可选 pokemon_type，且不能替换目标或包含子效果");
        if(Scope.parse(effect.arguments().get("scope"))==Scope.FIELD_HAND_DISCARD)throw new IllegalArgumentException("跨区域移除必须完整声明独立来源契约");
        for(String flag:java.util.List.of("evolution_only","basic_only","exclude_rule_box","source_active_only","source_benched_only","source_rule_box_only","rule_box_only"))
            if(effect.arguments().containsKey(flag)&&!"true".equals(effect.arguments().get(flag)))throw new IllegalArgumentException("压制筛选开关须明确true："+flag);
        if(effect.arguments().containsKey("source_active_only")&&effect.arguments().containsKey("source_benched_only"))
            throw new IllegalArgumentException("特性压制来源位置不能同时为战斗位和备战区");
        if(effect.arguments().containsKey("pokemon_stage")) {
            int stage=Integer.parseInt(effect.arguments().get("pokemon_stage"));
            if(stage<0||stage>2)throw new IllegalArgumentException("特性压制阶段须为0至2");
        }
        if (effect.arguments().containsKey("pokemon_type")) EnergyType.valueOf(effect.arguments().get("pokemon_type"));
        if(effect.arguments().containsKey("pokemon_families"))PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),effect.arguments().get("family_match"));
        else if(effect.arguments().containsKey("family_match"))throw new IllegalArgumentException("家族匹配模式必须与pokemon_families一起声明");
        if(effect.arguments().containsKey("except_pokemon_families"))PokemonFamilyFilter.fromWire(effect.arguments().get("except_pokemon_families"),"ANY");
        if(effect.arguments().containsKey("damage_counters_min")) {
            int minimum=Integer.parseInt(effect.arguments().get("damage_counters_min"));
            if(minimum<1||minimum>60)throw new IllegalArgumentException("特性压制伤害指示物门槛须为1至60");
        }
    }

    public static boolean basicZoneStadium(EffectDefinition effect) {
        return handles(effect.type())&&effect.target()==EffectTarget.SELF&&effect.conditions().isEmpty()&&effect.effects().isEmpty()
            &&effect.arguments().equals(java.util.Map.of("scope","FIELD_HAND_DISCARD","basic_only","true"));
    }
    public static boolean basicZoneStadium(EffectProgram program) {
        return program.triggers().size()==1&&program.triggers().getFirst().trigger()==EffectTrigger.PASSIVE
            &&program.triggers().getFirst().conditions().isEmpty()&&program.triggers().getFirst().effects().size()==1
            &&basicZoneStadium(program.triggers().getFirst().effects().getFirst());
    }
    /** Tool-bearing evolved source; its own printed Ability is exempt in every affected zone. */
    public static boolean zoneSource(EffectDefinition effect) {
        return handles(effect.type())&&effect.target()==EffectTarget.SELF&&effect.conditions().isEmpty()&&effect.effects().isEmpty()
            &&effect.arguments().keySet().equals(java.util.Set.of("scope","source_has_tool","source_evolved_only","except_ability_id"))
            &&"FIELD_HAND_DISCARD".equals(effect.arguments().get("scope"))&&"true".equals(effect.arguments().get("source_has_tool"))
            &&"true".equals(effect.arguments().get("source_evolved_only"))&&effect.arguments().get("except_ability_id").matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }
    public static String zoneSourceId(EffectProgram program) {
        for(var b:program.triggers())for(var e:b.effects())if(zoneSource(e))return e.arguments().get("except_ability_id");
        return null;
    }
    public static void validateZoneSource(EffectProgram program,String abilityId) {
        String id=zoneSourceId(program);if(id==null)return;
        if(!id.equals(abilityId)||program.triggers().size()!=1||program.triggers().getFirst().trigger()!=EffectTrigger.PASSIVE
            ||!program.triggers().getFirst().conditions().isEmpty()||program.triggers().getFirst().effects().size()!=1)
            throw new IllegalArgumentException("跨区域移除须为唯一被动效果，并豁免来源自身特性ID");
    }
    /** Acyclic source contract: Rule-Box sources cannot be recipients of this Basic non-Rule-Box suppression. */
    public static boolean ruleBoxSource(EffectDefinition effect) {
        return handles(effect.type())&&effect.arguments().equals(java.util.Map.of("scope","OPPONENT_IN_PLAY","basic_only","true","exclude_rule_box","true","source_active_only","true","source_rule_box_only","true"))
            &&effect.conditions().isEmpty();
    }
    /** Active-only sources whose declared recipient filters exclude the source, preventing suppression recursion. */
    public static boolean acyclicActiveSource(EffectDefinition effect) {
        if(!handles(effect.type())||!effect.conditions().isEmpty())return false;
        boolean evolvedBenchSource="ALL_BENCH".equals(effect.arguments().get("scope"))
            &&"true".equals(effect.arguments().get("source_benched_only"))&&"2".equals(effect.arguments().get("pokemon_stage"));
        if(evolvedBenchSource)return true;
        boolean legacyV="ALL_IN_PLAY".equals(effect.arguments().get("scope"))&&"V".equals(effect.arguments().get("pokemon_families"));
        if(legacyV)return true;
        if(!"true".equals(effect.arguments().get("source_active_only")))return false;
        boolean futureExempt="ALL_IN_PLAY".equals(effect.arguments().get("scope"))
            &&"true".equals(effect.arguments().get("rule_box_only"))&&"FUTURE".equals(effect.arguments().get("except_pokemon_families"));
        boolean opposingActive="OPPONENT_ACTIVE".equals(effect.arguments().get("scope"))
            &&effect.arguments().getOrDefault("except_ability_id","").matches("[a-z0-9]+(?:-[a-z0-9]+)*");
        boolean basicField="ALL_IN_PLAY".equals(effect.arguments().get("scope"))&&"true".equals(effect.arguments().get("basic_only"))
            &&effect.arguments().getOrDefault("except_ability_id","").matches("[a-z0-9]+(?:-[a-z0-9]+)*");
        boolean opposingField="OPPONENT_IN_PLAY".equals(effect.arguments().get("scope"));
        return futureExempt||opposingActive||basicField||opposingField;
    }
    public static boolean hasRuleBoxSource(EffectProgram program) {
        return program.triggers().stream().anyMatch(b->b.trigger()==EffectTrigger.PASSIVE&&b.effects().stream().anyMatch(AbilitySuppressionProgram::ruleBoxSource));
    }
    /**
     * Evaluates only PASSIVE bindings.  Conditions are read against the
     * affected public Pokémon, never a client supplied representation.
     */
    public static boolean suppresses(EffectProgram program, EffectConditionContext context,
                                     UUID sourceController, UUID affectedOwner, EnergyType affectedPokemonType) {
        Objects.requireNonNull(program, "EffectProgram");
        Objects.requireNonNull(context, "EffectConditionContext");
        Objects.requireNonNull(sourceController, "sourceController");
        Objects.requireNonNull(affectedOwner, "affectedOwner");
        for (var binding : program.triggers()) {
            if (binding.trigger() != EffectTrigger.PASSIVE) continue;
            if(binding.effects().stream().noneMatch(AbilitySuppressionProgram::contains))continue;
            if (!binding.conditions().stream().allMatch(condition -> ConditionEvaluator.matches(condition, context))) continue;
            for (var effect : binding.effects()) if (suppresses(effect, context, sourceController, affectedOwner, affectedPokemonType)) return true;
        }
        return false;
    }

    private static boolean suppresses(EffectDefinition effect, EffectConditionContext context,
                                      UUID sourceController, UUID affectedOwner, EnergyType affectedPokemonType) {
        if(!contains(effect)||zoneSource(effect))return false;
        if (!effect.conditions().stream().allMatch(condition -> ConditionEvaluator.matches(condition, context))) return false;
        if (effect.type().equals("conditional")) {
            for (var child : effect.effects()) if (suppresses(child, context, sourceController, affectedOwner, affectedPokemonType)) return true;
            return false;
        }
        if (!handles(effect.type())) return false;
        if(effect.arguments().containsKey("basic_only")&&!context.flag(ConditionType.IS_BASIC,EffectTarget.SELF,java.util.Map.of()))return false;
        if(effect.arguments().containsKey("evolution_only")&&context.flag(ConditionType.IS_BASIC,EffectTarget.SELF,java.util.Map.of()))return false;
        if(effect.arguments().containsKey("pokemon_stage")&&!context.flag(ConditionType.IS_STAGE,EffectTarget.SELF,
            java.util.Map.of("stage",effect.arguments().get("pokemon_stage"))))return false;
        if(effect.arguments().containsKey("exclude_rule_box")&&context.flag(ConditionType.HAS_RULE_BOX,EffectTarget.SELF,java.util.Map.of()))return false;
        if(effect.arguments().containsKey("rule_box_only")&&!context.flag(ConditionType.HAS_RULE_BOX,EffectTarget.SELF,java.util.Map.of()))return false;
        if(effect.arguments().containsKey("pokemon_families")) {
            var families=PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),effect.arguments().get("family_match"));
            boolean matched=families.families().stream().anyMatch(family->context.flag(ConditionType.IS_FAMILY,EffectTarget.SELF,java.util.Map.of("pokemon_family",family.name())));
            if(families.match()==PokemonFamilyFilter.Match.ALL?families.families().stream().anyMatch(family->!context.flag(ConditionType.IS_FAMILY,EffectTarget.SELF,java.util.Map.of("pokemon_family",family.name()))):!matched)return false;
        }
        if(effect.arguments().containsKey("except_pokemon_families")) {
            var excluded=PokemonFamilyFilter.fromWire(effect.arguments().get("except_pokemon_families"),"ANY");
            if(excluded.families().stream().anyMatch(family->context.flag(ConditionType.IS_FAMILY,EffectTarget.SELF,java.util.Map.of("pokemon_family",family.name()))))return false;
        }
        if(effect.arguments().containsKey("damage_counters_min")&&context.number(ConditionType.DAMAGE_COUNTER_COUNT,EffectTarget.SELF,java.util.Map.of())<Integer.parseInt(effect.arguments().get("damage_counters_min")))return false;
        if (effect.arguments().containsKey("pokemon_type")
            && affectedPokemonType != EnergyType.valueOf(effect.arguments().get("pokemon_type"))) return false;
        Scope scope = Scope.parse(effect.arguments().get("scope"));
        return switch (scope) {
            case FIELD_HAND_DISCARD -> basicZoneStadium(effect);
            case ALL_IN_PLAY -> true;
            case ALL_BENCH -> !context.flag(ConditionType.IS_ACTIVE,EffectTarget.SELF,java.util.Map.of());
            case OWN_IN_PLAY -> sourceController.equals(affectedOwner);
            case OPPONENT_IN_PLAY -> !sourceController.equals(affectedOwner);
            case OPPONENT_ACTIVE -> !sourceController.equals(affectedOwner)&&context.flag(ConditionType.IS_ACTIVE,EffectTarget.SELF,java.util.Map.of());
        };
    }
    public static boolean requiresSourceActive(EffectProgram program){return positionFlag(program,"source_active_only");}
    public static boolean requiresSourceBench(EffectProgram program){return positionFlag(program,"source_benched_only");}
    private static boolean positionFlag(EffectProgram program,String flag){
        return program.triggers().stream().flatMap(b->b.effects().stream()).anyMatch(e->positionFlag(e,flag));
    }
    private static boolean positionFlag(EffectDefinition effect,String flag){
        return effect.arguments().containsKey(flag)||effect.effects().stream().anyMatch(e->positionFlag(e,flag));
    }
}
