package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;

/**
 * Moves an exact count of existing damage counters between two already public
 * in-play Pokémon. The source is the Effect target and the destination is a
 * second, fixed data target; interactive targets must be bound by the server.
 */
public record MoveDamageCounterEffect(int counters, EffectTarget destination,String sourceRef,boolean upTo,boolean all) {
    public MoveDamageCounterEffect(int counters,EffectTarget destination,String sourceRef,boolean upTo){this(counters,destination,sourceRef,upTo,false);}
    public MoveDamageCounterEffect(int counters,EffectTarget destination){this(counters,destination,null,false);}
    public static final Set<String> FIELDS = Set.of("amount", "destination","source_ref","up_to","all","exclude_self","opponent_transfer");
    public static boolean handles(EffectDefinition effect) { return effect.type().equals("move_damage_counter"); }
    public static boolean opponentTransfer(EffectDefinition effect) { return "true".equals(effect.arguments().get("opponent_transfer")); }

    public static MoveDamageCounterEffect from(EffectDefinition effect) {
        if (!handles(effect) || !effect.effects().isEmpty()) throw new IllegalArgumentException("无效的伤害指示物移动效果");
        if (!FIELDS.containsAll(effect.arguments().keySet())) throw new IllegalArgumentException("移动伤害指示物包含未支持参数");
        if(effect.arguments().containsKey("all")) {
            boolean legacy=effect.arguments().equals(Map.of("all","true","destination","OPPONENT_ACTIVE"))&&effect.target()==EffectTarget.CHOSEN_POKEMON;
            boolean bound=effect.target()==EffectTarget.SELF&&effect.arguments().keySet().equals(Set.of("all","destination","source_ref"))
                &&"true".equals(effect.arguments().get("all"))&&"CHOSEN_POKEMON".equals(effect.arguments().get("destination"));
            if(!legacy&&!bound)throw new IllegalArgumentException("全量转移须为已选备战来源至对方已选宝可梦");
            String ref=bound?effect.arguments().get("source_ref"):null;if(ref!=null)AttackPokemonBindings.name(ref);
            return new MoveDamageCounterEffect(0,bound?EffectTarget.CHOSEN_POKEMON:EffectTarget.OPPONENT_ACTIVE,ref,false,true);
        }
        String ref=effect.arguments().get("source_ref");
        if(ref!=null){AttackPokemonBindings.name(ref);if(effect.target()!=EffectTarget.SELF)throw new IllegalArgumentException("具名来源移动须以SELF占位且由source_ref明确来源");}
        else if (!fixedPokemonTarget(effect.target())&&effect.target()!=EffectTarget.SELECT_OWN_POKEMON) throw new IllegalArgumentException("移动伤害指示物必须指定一个已绑定、选择或固定的公开来源宝可梦");
        EffectTarget destination = EffectTarget.fromWire(required(effect.arguments(), "destination"));
        if (!fixedPokemonTarget(destination)&&destination!=EffectTarget.SELF) throw new IllegalArgumentException("移动伤害指示物必须指定一个已绑定或固定的公开目的地宝可梦");
        if(effect.arguments().containsKey("exclude_self")&&(!"true".equals(effect.arguments().get("exclude_self"))
            ||effect.target()!=EffectTarget.SELECT_OWN_POKEMON||destination!=EffectTarget.SELF))throw new IllegalArgumentException("排除自身只用于选择己方来源并移至特性来源自身");
        int amount;
        try { amount = Integer.parseInt(required(effect.arguments(), "amount")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("移动伤害指示物 amount 不是整数", invalid); }
        if (amount < 1 || amount > 60) throw new IllegalArgumentException("移动伤害指示物 amount 超出范围");
        boolean upTo=effect.arguments().containsKey("up_to");
        boolean opponentTransfer=effect.arguments().containsKey("opponent_transfer");
        if(opponentTransfer&&(!opponentTransfer(effect)||ref==null||effect.target()!=EffectTarget.SELF
            ||destination!=EffectTarget.CHOSEN_POKEMON||amount>3||effect.arguments().containsKey("exclude_self")))
            throw new IllegalArgumentException("对方指示物转移须绑定来源、另一已选目标，且最多移动3个");
        if(upTo&&(!"true".equals(effect.arguments().get("up_to"))
            ||!(ref==null&&effect.target()==EffectTarget.OWN_ACTIVE&&destination==EffectTarget.OPPONENT_ACTIVE
                ||opponentTransfer&&amount<=3
                ||ref!=null&&!opponentTransfer&&effect.target()==EffectTarget.SELF&&destination==EffectTarget.CHOSEN_POKEMON&&amount<=3)))
            throw new IllegalArgumentException("可选移动数量仅支持战斗位转移、具名来源最多3个转移或对方双目标最多2个转移");
        return new MoveDamageCounterEffect(amount, destination,ref,upTo);
    }

    public int countersForDamage(int damage) {
        if(damage<0)throw new IllegalArgumentException("来源伤害不能为负");
        return all?damage/10:counters;
    }
    private static boolean fixedPokemonTarget(EffectTarget target) {
        return switch (target) {
            case CHOSEN_POKEMON, TRIGGER_POKEMON, ATTACKING_POKEMON, OWN_ACTIVE, OPPONENT_ACTIVE -> true;
            default -> false;
        };
    }
    private static String required(Map<String, String> arguments, String key) {
        String value = arguments.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("移动伤害指示物缺少 " + key);
        return value;
    }
}
