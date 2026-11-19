package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;

/** Printed draw amounts derived from a public player count, resolved when this step executes. */
public record DrawCount(int amount, ConditionType countType, EffectTarget countTarget, int offset, Map<String,String> countArguments,String countBinding) {
    public DrawCount(int amount,ConditionType countType,EffectTarget countTarget,int offset,Map<String,String> countArguments){this(amount,countType,countTarget,offset,countArguments,null);}
    public DrawCount {countArguments=Map.copyOf(countArguments);}
    public static final Set<String> FIELDS = Set.of("amount", "bind", "count_bind", "count_type", "count_target", "count_offset", "pokemon_families", "family_match", "pokemon_types", "pokemon_stage", "definition_ids", "up_to");
    public static boolean upTo(EffectDefinition effect){return effect.type().equals("draw")&&"true".equals(effect.arguments().get("up_to"));}
    public static DrawCount from(EffectDefinition effect) {
        var args = effect.arguments();
        if (!Set.of("draw","draw_until").contains(effect.type()) || !FIELDS.containsAll(args.keySet()) || !effect.effects().isEmpty())
            throw new IllegalArgumentException("无效的抽牌数量规则");
        int amount = integer(args.get("amount"));
        if (amount < 1 || amount > 60) throw new IllegalArgumentException("抽牌 amount 必须为 1 至 60");
        if(args.containsKey("up_to")&&(!upTo(effect)||amount!=1
            ||!args.keySet().equals(Set.of("amount","count_type","count_target","up_to"))
            ||!"PRIZE_COUNT".equals(args.get("count_type"))||!effect.target().name().equals(args.get("count_target"))
            ||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())))
            throw new IllegalArgumentException("可选抽牌仅核验各自决定抽至多自身剩余奖赏数");
        if(args.containsKey("definition_ids")) {
            if(!effect.type().equals("draw")||effect.target()!=EffectTarget.SELF
                ||!args.keySet().equals(Set.of("amount","count_type","count_target","definition_ids"))
                ||amount!=2||!"DISCARD_COUNT".equals(args.get("count_type"))||!"SELF".equals(args.get("count_target")))
                throw new IllegalArgumentException("同名弃牌抽牌须为自身每张指定身份卡抽2张");
            TurnHistoryCondition.values(args.get("definition_ids"));
            return new DrawCount(amount,ConditionType.DISCARD_COUNT,EffectTarget.SELF,0,Map.of("definition_ids",args.get("definition_ids")));
        }
        if(args.containsKey("count_bind")) {
            if(Set.of("count_type","count_target","count_offset","pokemon_families","family_match","pokemon_types","pokemon_stage").stream().anyMatch(args::containsKey))
                throw new IllegalArgumentException("绑定抽牌计数不能同时声明实时计数或筛选");
            return new DrawCount(amount,null,null,0,Map.of(),AttackCountBindings.name(args.get("count_bind")));
        }
        boolean pokemonFilter=args.containsKey("pokemon_families")||args.containsKey("family_match")||args.containsKey("pokemon_types")||args.containsKey("pokemon_stage");
        if(pokemonFilter&&!"POKEMON_COUNT".equals(args.get("count_type")))
            throw new IllegalArgumentException("宝可梦筛选仅用于场上宝可梦计数");
        if (!args.containsKey("count_type")) {
            if (args.containsKey("count_target") || args.containsKey("count_offset"))
                throw new IllegalArgumentException("抽牌计数目标缺少 count_type");
            return new DrawCount(amount, null, null, 0, Map.of());
        }
        var type = ConditionType.fromWire(args.get("count_type"));
        var target = EffectTarget.fromWire(args.get("count_target"));
        if(type==ConditionType.POKEMON_COUNT) {
            var families=PokemonFamilyFilter.fromWire(args.get("pokemon_families"),args.get("family_match"));
            var types=PokemonTypeFilter.fromWire(args.get("pokemon_types"));
            if(families.families().isEmpty()&&types.types().isEmpty()&&!args.containsKey("pokemon_stage")||args.containsKey("count_offset")
                ||!Set.of(EffectTarget.OWN_BENCH,EffectTarget.OPPONENT_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(target))
                throw new IllegalArgumentException("场上宝可梦计数抽牌须提供非空家族或属性筛选和明确范围，不接受偏移量");
            var filters=new java.util.LinkedHashMap<String,String>();
            if(args.containsKey("pokemon_families")){filters.put("pokemon_families",args.get("pokemon_families"));filters.put("family_match",families.match().name());}
            if(args.containsKey("pokemon_types"))filters.put("pokemon_types",args.get("pokemon_types"));
            if(args.containsKey("pokemon_stage")) {
                int stage=integer(args.get("pokemon_stage"));
                if(stage<0||stage>2)throw new IllegalArgumentException("宝可梦阶段必须为0至2");
                filters.put("pokemon_stage",args.get("pokemon_stage"));
            }
            return new DrawCount(amount,type,target,0,filters);
        }
        int offset = integer(args.getOrDefault("count_offset", "0"));
        if (!Set.of(ConditionType.HAND_SIZE, ConditionType.BENCH_COUNT, ConditionType.PRIZE_COUNT).contains(type)
            || !Set.of(EffectTarget.SELF, EffectTarget.OWN_ACTIVE, EffectTarget.OPPONENT_ACTIVE).contains(target)
            || offset < 0 || offset > 1 || offset == 1 && type != ConditionType.BENCH_COUNT
                && !(type==ConditionType.HAND_SIZE&&effect.type().equals("draw_until")&&target==EffectTarget.OPPONENT_ACTIVE))
            throw new IllegalArgumentException("抽牌仅能读取公开手牌数、备战数或奖赏数；可为备战数加1，或抽到比对手手牌多1张");
        return new DrawCount(amount, type, target, offset, Map.of());
    }
    public int resolve(EffectConditionContext context) {
        // A deck contains at most 60 cards; effect draws take what remains and never cause a deck-out loss.
        if(countBinding!=null) {
            int count=context.boundCount(countBinding);
            if(count<0||count>60)throw new IllegalArgumentException("绑定抽牌计数须为0至60");
            return Math.min(60,amount*count);
        }
        return countType == null ? amount : Math.min(60, amount * (context.number(countType, countTarget, countArguments) + offset));
    }
    private static int integer(String raw) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("抽牌计数参数必须为整数", invalid); }
    }
}
