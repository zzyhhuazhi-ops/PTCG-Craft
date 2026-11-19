package dev.hua.ptcglive.engine.effect;
import java.util.*;

/** Public hosts are fixed before the private deck search and simultaneous one-per-host distribution. */
public final class DistinctBenchEnergy {
    public static final String TYPE="prepare_distinct_bench_energy";
    private DistinctBenchEnergy() { }
    public static boolean cheerfulCharge(EffectDefinition effect) {
        return effect.arguments().equals(Map.of("target_count","2","distinct_energy_types","false"));
    }
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.OWN_BENCH||!effect.arguments().isEmpty()&&!cheerfulCharge(effect)
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("预选备战附能只接受咖啡厅大师的3只不同属性，或愉快充能的2只任意基本能量");
    }
    public static EffectDefinition attachment(int hosts) {
        return attachment(hosts,true);
    }
    public static EffectDefinition attachment(int hosts,boolean distinctTypes) {
        if(hosts<1||hosts>(distinctTypes?3:2))throw new IllegalArgumentException("预选备战数量超出卡文上限");
        var args=new HashMap<>(Map.of(
            "amount",Integer.toString(hosts),"minimum","0","source","DECK","basic_energy_only","true",
            "target_scope","OWN_BENCH","distribution","ANY","one_per_target","true","prepared_bench","true"));
        if(distinctTypes)args.put("distinct_energy_types","true");
        return new EffectDefinition("attach_energy",EffectTarget.SELECT_OWN_POKEMON,args,List.of(),List.of());
    }
}
