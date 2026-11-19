package dev.hua.ptcglive.engine.effect;

/** Counter adds the actual source Pokémon's last opponent-turn attack damage during base damage computation. */
public final class PreviousAttackDamage {
    private PreviousAttackDamage() { }
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals("damage_from_last_opponent_attack")||effect.target()!=EffectTarget.SELF
            ||!effect.arguments().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("上个对手回合受伤加成只读取SELF实际受击历史，不接受自定义数值或子效果");
    }
}
