package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.EvolutionPermission;
import java.util.*;

/** Audited Trainer searches keep timing and host restrictions through every selection step. */
public final class TrainerDeckEvolution {
    private TrainerDeckEvolution() { }
    public static EffectDefinition gxEvolution() {
        return new EffectDefinition("evolve_selected_from_deck",EffectTarget.SELECT_OWN_POKEMON,
            Map.of("target_count","1","minimum","1","evolution_timing","STRICT","evolution_family","GX"),List.of(),List.of());
    }
    public static boolean handles(EffectDefinition effect) {
        return effect.type().equals("evolve_selected_from_deck")&&effect.arguments().containsKey("evolution_timing");
    }
    public static void validate(EffectDefinition effect) {
        var a=effect.arguments();
        if(!handles(effect)||effect.target()!=EffectTarget.SELECT_OWN_POKEMON||!effect.conditions().isEmpty()||!effect.effects().isEmpty()
            ||!Set.of("target_count","minimum","evolution_timing","excluded_host_family","evolution_names","evolution_family","bind_pokemon").containsAll(a.keySet())
            ||!Set.of("STRICT","ANY").contains(a.get("evolution_timing"))
            ||!("1".equals(a.get("target_count"))&&"1".equals(a.get("minimum"))
                ||"2".equals(a.get("target_count"))&&"0".equals(a.get("minimum"))&&a.size()==3&&"STRICT".equals(a.get("evolution_timing"))))
            throw new IllegalArgumentException("训练家检索进化须明确已审核的目标数与进化时点");
        if(a.containsKey("excluded_host_family")&&(!"EX_LEGACY".equals(a.get("excluded_host_family"))||!"ANY".equals(a.get("evolution_timing"))||a.size()!=4))
            throw new IllegalArgumentException("满充仅排除宿主的旧版EX身份");
        if(a.containsKey("evolution_names")&&(!"Mismagius|Mismagius-GX|Honchkrow|Honchkrow-GX|Chandelure|Chandelure-GX|Aegislash|Aegislash-GX".equals(a.get("evolution_names"))
            ||!"ANY".equals(a.get("evolution_timing"))||a.size()!=4))
            throw new IllegalArgumentException("暗之石须限定四种印刷名称及其GX版本");
        if(a.containsKey("evolution_family")&&(!"GX".equals(a.get("evolution_family"))||!"STRICT".equals(a.get("evolution_timing"))
            ||!"1".equals(a.get("target_count"))||!"1".equals(a.get("minimum"))||a.size()!=(a.containsKey("bind_pokemon")?5:4)))
            throw new IllegalArgumentException("赤红青绿须按通常进化时点从牌库选择直接进化的GX");
        if(a.containsKey("bind_pokemon")&&(!"GX".equals(a.get("evolution_family"))||!"red-blue-evolved".equals(a.get("bind_pokemon"))))
            throw new IllegalArgumentException("检索进化绑定仅限赤红青绿实际进化成功的宝可梦");
    }
    public static String successBinding(EffectDefinition effect) {
        if(!handles(effect))return null;
        validate(effect);return effect.arguments().get("bind_pokemon");
    }
    public static EvolutionPermission permission(EffectDefinition effect) {
        return "STRICT".equals(effect.arguments().get("evolution_timing"))?EvolutionPermission.STRICT:EvolutionPermission.CARD_EFFECT_ALL_TIMING;
    }
    public static EffectDefinition search(EffectDefinition effect) {
        validate(effect);
        var args=new LinkedHashMap<String,String>(Map.of("amount","1","minimum","0","card_category","POKEMON","destination","EVOLVE_SELF",
            "evolution_timing",effect.arguments().get("evolution_timing")));
        if(effect.arguments().containsKey("evolution_names"))args.put("pokemon_names",effect.arguments().get("evolution_names"));
        if(effect.arguments().containsKey("evolution_family"))args.put("pokemon_families",effect.arguments().get("evolution_family"));
        return new EffectDefinition("search_deck",EffectTarget.DECK,args,List.of(),List.of());
    }
}
