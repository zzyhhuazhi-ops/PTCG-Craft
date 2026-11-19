package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** One attack's confirmed opposing damage KO can protect its original user on the next opposing turn. */
public record AttackDamageKoProtection(ProtectionEffect protection) {
    public static final String TYPE="protect_on_attack_damage_ko";
    private static final Map<String,String> ARGUMENTS=Map.of(
        "source","ATTACK","from","ANY","scope","DAMAGE_AND_EFFECTS","duration","OPPONENT_TURN_END",
        "when","OPPONENT_NEXT_TURN_START","clear","BENCH_EVOLVE_LEAVE");
    public static final Set<String> FIELDS=ARGUMENTS.keySet();
    public static AttackDamageKoProtection from(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.SELF||!effect.arguments().equals(ARGUMENTS)
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("击倒后防护须明确原攻击者、对手下回合与换位进化离场清除合同，不接受子步骤");
        var fields=new LinkedHashMap<>(effect.arguments());fields.remove("when");fields.remove("clear");
        return new AttackDamageKoProtection(ProtectionEffect.from(new EffectDefinition("prevent_effects",EffectTarget.SELF,fields,List.of(),List.of())));
    }
    /** No Prize parameter: a real zero-Prize KO will qualify once such Pokémon are supported. */
    public static boolean qualifies(UUID attackerOwner,UUID defeatedOwner,boolean confirmed,boolean attackDamage) {
        return confirmed&&attackDamage&&!Objects.requireNonNull(attackerOwner).equals(Objects.requireNonNull(defeatedOwner));
    }
    /** Registration is a single root AFTER_ATTACK operation, never an automatic KO program or repeat body. */
    public static void validateProgram(EffectProgram program) {
        int count=0;
        for(var binding:program.triggers())for(var effect:binding.effects()) {
            if(effect.type().equals(TYPE)) {
                from(effect);
                if(binding.trigger()!=EffectTrigger.AFTER_ATTACK||!binding.conditions().isEmpty()||++count>1)
                    throw new IllegalArgumentException("击倒后防护只能在 AFTER_ATTACK 根部登记一次");
            }
            for(var child:effect.effects())if(contains(child))throw new IllegalArgumentException("击倒后防护不能嵌套或重复登记");
        }
    }
    private static boolean contains(EffectDefinition effect) {
        return effect.type().equals(TYPE)||effect.effects().stream().anyMatch(AttackDamageKoProtection::contains);
    }
}
