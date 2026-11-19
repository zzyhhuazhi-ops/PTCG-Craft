package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.CardDefinition;
import java.util.*;

/** One parameterized attachment contract, independent of printed card IDs and Java class names. */
public record AttachEnergyEffect(EffectTargetResolution.Zone source, ServerMatch.EnergyAttachmentPolicy policy,
                                  Set<EnergyType> basicEnergyTypes, Distribution distribution, boolean specialEnergyOnly) {
    public AttachEnergyEffect { basicEnergyTypes=Set.copyOf(basicEnergyTypes); }
    /** SAME_TARGET preserves the legacy one-host flow; ANY assigns each selected Energy to one audited public host. */
    public enum Distribution { SAME_TARGET, ANY }
    public static final Set<String> FIELDS = Set.of("coin", "amount", "minimum", "amount_mode", "source", "basic_energy_only", "special_energy_only", "energy_type", "energy_types", "target_scope", "definition_ids", "distribution", "bind", "bind_active", "top_count", "remainder", "invalid_host_policy", "source_owner", "chooser", "target_pokemon_type", "target_basic_only", "target_name_contains", "target_pokemon_names", "target_tool_definition_ids", "target_pokemon_name", "target_pokemon_families", "distinct_energy_types", "one_per_target", "prepared_bench");
    public static AttachEnergyEffect from(EffectDefinition effect) {
        if (!effect.type().equals("attach_energy") || !effect.effects().isEmpty()) throw new IllegalArgumentException("无效的能量附着原子效果");
        if (!FIELDS.containsAll(effect.arguments().keySet())) throw new IllegalArgumentException("能量附着含未支持参数");
        var args = effect.arguments();
        if(args.containsKey("prepared_bench")&&!effect.equals(DistinctBenchEnergy.attachment(Integer.parseInt(args.getOrDefault("amount","0")),args.containsKey("distinct_energy_types"))))
            throw new IllegalArgumentException("预选备战附能须完整保留已审核的每目标一张分配契约");
        if(args.containsKey("invalid_host_policy")&&(!"DISCARD".equals(args.get("invalid_host_policy"))
            ||effect.target()!=EffectTarget.SELF||!"DISCARD".equals(args.get("source"))||!"SAME_TARGET".equals(args.getOrDefault("distribution","SAME_TARGET"))))
            throw new IllegalArgumentException("附能后丢弃不合法宿主的能量目前限定弃牌区向自身同一目标附能");
        if (!args.containsKey("minimum")) throw new IllegalArgumentException("能量附着必须由审核数据明确 minimum");
        var source = EffectTargetResolution.Zone.valueOf(required(args, "source"));
        if (!Set.of(EffectTargetResolution.Zone.HAND,EffectTargetResolution.Zone.DECK,EffectTargetResolution.Zone.DISCARD).contains(source))
            throw new IllegalArgumentException("通用附能只接受手牌、牌库或弃牌区来源");
        var scope = ServerMatch.InPlayTargetScope.valueOf(required(args, "target_scope"));
        Distribution distribution;
        try { distribution = Distribution.valueOf(args.getOrDefault("distribution", "SAME_TARGET").trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("能量附着 distribution 须为 SAME_TARGET 或 ANY", invalid); }
        boolean compatible = switch (effect.target()) {
            // The enclosing select_pokemon scope supplies the exact public
            // host; its range is rechecked before any private source moves.
            case CHOSEN_POKEMON -> distribution == Distribution.SAME_TARGET;
            // “… attach it to this Pokémon.”: the source Pokémon itself, Active or Benched.
            case SELF -> scope == ServerMatch.InPlayTargetScope.OWN_FIELD && distribution == Distribution.SAME_TARGET;
            case SELECT_OWN_POKEMON -> scope == ServerMatch.InPlayTargetScope.OWN_FIELD || scope == ServerMatch.InPlayTargetScope.OWN_BENCH;
            case SELECT_OPPONENT_POKEMON -> scope == ServerMatch.InPlayTargetScope.OPPONENT_FIELD || scope == ServerMatch.InPlayTargetScope.OPPONENT_BENCH;
            case SELECT_ANY_POKEMON -> scope == ServerMatch.InPlayTargetScope.ANY_FIELD;
            case OWN_ACTIVE -> scope == ServerMatch.InPlayTargetScope.OWN_ACTIVE;
            case OPPONENT_ACTIVE -> scope == ServerMatch.InPlayTargetScope.OPPONENT_ACTIVE;
            default -> false;
        };
        if (!compatible) throw new IllegalArgumentException("能量附着目标与 target_scope 不一致；群体分配须独立分配语义");
        if (distribution == Distribution.ANY && !Set.of(EffectTarget.SELECT_OWN_POKEMON, EffectTarget.SELECT_OPPONENT_POKEMON,
            EffectTarget.SELECT_ANY_POKEMON).contains(effect.target()))
            throw new IllegalArgumentException("多目标能量分配必须明确选择公开场上范围，不能复用固定或已绑定单目标");
        if(args.containsKey("bind")) AttackCountBindings.name(args.get("bind"));
        if(args.containsKey("bind_active")) {
            AttackCountBindings.name(args.get("bind_active"));
            if(distribution!=Distribution.ANY||effect.target()!=EffectTarget.SELECT_OWN_POKEMON)
                throw new IllegalArgumentException("战斗位附能计数仅支持己方多目标分配");
        }
        String basicValue = args.getOrDefault("basic_energy_only", "false");
        if (!basicValue.equals("true") && !basicValue.equals("false")) throw new IllegalArgumentException("basic_energy_only 必须为 true 或 false");
        boolean basic = Boolean.parseBoolean(basicValue);
        boolean special = args.containsKey("special_energy_only");
        if (special && (!"true".equals(args.get("special_energy_only")) || basic))
            throw new IllegalArgumentException("特殊能量筛选须为 true，不能同时限定基本能量");
        if(args.containsKey("energy_type")&&args.containsKey("energy_types"))throw new IllegalArgumentException("能量类型不能同时指定单数和复数筛选");
        var types=EnumSet.noneOf(EnergyType.class);
        String raw=args.getOrDefault("energy_types",args.get("energy_type"));
        if(raw!=null)for(String value:raw.split("\\|",-1))types.add(EnergyType.valueOf(value));
        if (!types.isEmpty() && !basic) throw new IllegalArgumentException("按类型选择目前仅支持基础能量；不能把特殊能量的供给当成卡牌类型");
        var spec = EffectCardSelectionSpec.from(effect);
        if(args.containsKey("target_pokemon_names")) {
            if(TurnHistoryCondition.values(args.get("target_pokemon_names")).isEmpty()||!basic||scope!=ServerMatch.InPlayTargetScope.OWN_FIELD)
                throw new IllegalArgumentException("附能名称列表须限定己方场上基本能量");
        }
        if(args.containsKey("target_tool_definition_ids")) {
            if(TurnHistoryCondition.values(args.get("target_tool_definition_ids")).isEmpty()||!basic||source!=EffectTargetResolution.Zone.DECK
                ||scope!=ServerMatch.InPlayTargetScope.OWN_FIELD||distribution!=Distribution.ANY||!"true".equals(args.get("one_per_target")))
                throw new IllegalArgumentException("附有指定道具的每只宝可梦各附能须完整声明牌库、己方场上和每目标1张");
        }
        if(args.containsKey("target_pokemon_name")&&(args.get("target_pokemon_name").isBlank()
            ||source!=EffectTargetResolution.Zone.DECK||!basic||spec.minimum()!=0||distribution!=Distribution.ANY
            ||scope!=ServerMatch.InPlayTargetScope.OWN_BENCH||!"true".equals(args.get("one_per_target"))))
            throw new IllegalArgumentException("精确名称附能须从牌库向己方同名备战宝可梦各附着至多1张基本能量");
        if(args.containsKey("target_name_contains")&&(args.get("target_name_contains").isBlank()
            ||!Set.of(EffectTargetResolution.Zone.HAND,EffectTargetResolution.Zone.DISCARD,EffectTargetResolution.Zone.DECK).contains(source)
            ||!(effect.target()==EffectTarget.SELECT_OWN_POKEMON
                &&Set.of(ServerMatch.InPlayTargetScope.OWN_FIELD,ServerMatch.InPlayTargetScope.OWN_BENCH).contains(scope)
                ||effect.target()==EffectTarget.OWN_ACTIVE&&scope==ServerMatch.InPlayTargetScope.OWN_ACTIVE&&distribution==Distribution.SAME_TARGET)))
            throw new IllegalArgumentException("附能名称筛选须从手牌、弃牌区或牌库向己方指定名称宝可梦附着");
        if(args.containsKey("target_pokemon_type")) {
            EnergyType.valueOf(args.get("target_pokemon_type"));
            boolean handTarget = source==EffectTargetResolution.Zone.HAND && basic && distribution==Distribution.SAME_TARGET;
            if(!handTarget && !Set.of(EffectTargetResolution.Zone.DISCARD,EffectTargetResolution.Zone.DECK).contains(source)
                ||effect.target()!=EffectTarget.SELECT_OWN_POKEMON||!Set.of(ServerMatch.InPlayTargetScope.OWN_FIELD,ServerMatch.InPlayTargetScope.OWN_BENCH).contains(scope))
                throw new IllegalArgumentException("宿主属性筛选须为己方场上分配能量，或从手牌向单个己方目标附着基本能量");
        }
        if(args.containsKey("target_basic_only")&&(!"true".equals(args.get("target_basic_only"))
            ||effect.target()!=EffectTarget.SELECT_OWN_POKEMON))
            throw new IllegalArgumentException("附能基础宿主筛选须用于己方场上选择目标");
        if(args.containsKey("target_pokemon_families")) {
            PokemonFamilyFilter.fromWire(args.get("target_pokemon_families"),"ANY");
            if(!Set.of(EffectTargetResolution.Zone.DISCARD,EffectTargetResolution.Zone.DECK).contains(source)
                ||effect.target()!=EffectTarget.SELECT_OWN_POKEMON)
                throw new IllegalArgumentException("附能宿主家族筛选仅用于牌库或弃牌区向己方场上分配");
        }
        if(args.containsKey("distinct_energy_types")&&(!"true".equals(args.get("distinct_energy_types"))
            ||!basic||distribution!=Distribution.ANY||!Set.of(EffectTargetResolution.Zone.DECK,EffectTargetResolution.Zone.HAND).contains(source)||spec.minimum()!=0))
            throw new IllegalArgumentException("不同属性附能须为牌库或手牌基本能量、任意分配且可选0张");
        if(args.containsKey("one_per_target")&&(!"true".equals(args.get("one_per_target"))
            ||distribution!=Distribution.ANY||effect.target()!=EffectTarget.SELECT_OWN_POKEMON))
            throw new IllegalArgumentException("每目标一张附能须为己方多目标分配");
        if(args.containsKey("source_owner")||args.containsKey("chooser")) {
            boolean opponentDiscard=effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON&&args.equals(Map.of(
                "source","DISCARD","source_owner","OPPONENT","target_scope","OPPONENT_FIELD","amount","3","minimum","0","distribution","ANY"));
            if(!opponentDiscard&&(!"OPPONENT".equals(args.get("source_owner"))||!"OPPONENT".equals(args.get("chooser"))
                ||source!=EffectTargetResolution.Zone.HAND||!basic||distribution!=Distribution.ANY
                ||effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON||scope!=ServerMatch.InPlayTargetScope.OPPONENT_FIELD
                ||spec.minimum()!=0||!types.isEmpty()||!spec.allowedDefinitionIds().isEmpty()
                ||!Set.of("amount","minimum","source","basic_energy_only","target_scope","distribution","source_owner","chooser").containsAll(args.keySet())))
                throw new IllegalArgumentException("对手自主附能仅核验其手牌基本能量、对手全场任意分配、可选0张及明确来源和选择者");
        }
        if(coinHeads(effect)) {
            AttackCoinBindings.name(args.get("coin"));
            boolean benchRecovery=source==EffectTargetResolution.Zone.DISCARD&&effect.target()==EffectTarget.SELECT_OWN_POKEMON
                &&basic&&types.isEmpty()&&distribution==Distribution.ANY&&scope==ServerMatch.InPlayTargetScope.OWN_BENCH
                &&spec.minimum()==0&&spec.maximum()==3
                &&args.keySet().equals(Set.of("source","amount","minimum","amount_mode","coin","basic_energy_only","target_scope","distribution"));
            if(!benchRecovery&&(source!=EffectTargetResolution.Zone.DECK||effect.target()!=EffectTarget.SELF||!basic
                ||(!types.isEmpty()&&types.size()>1)||distribution!=Distribution.SAME_TARGET
                ||scope!=ServerMatch.InPlayTargetScope.OWN_FIELD||spec.minimum()!=0||spec.maximum()!=60
                ||args.containsKey("bind")||args.containsKey("bind_active")||args.containsKey("top_count")||args.containsKey("remainder")||args.containsKey("invalid_host_policy")||args.containsKey("definition_ids")))
                throw new IllegalArgumentException("按正面附能仅核验牌库基本雷能量向自身最多附着，amount:60 minimum:0并引用实际硬币");
        } else if(args.containsKey("coin"))throw new IllegalArgumentException("附能硬币引用必须配合COIN_HEADS");
        if(opponentFieldEnergy(effect)) {
            if(source!=EffectTargetResolution.Zone.DISCARD||!basic||distribution!=Distribution.ANY
                ||effect.target()!=EffectTarget.SELECT_OWN_POKEMON||scope!=ServerMatch.InPlayTargetScope.OWN_FIELD
                ||spec.minimum()!=0||spec.maximum()!=60||!types.equals(Set.of(EnergyType.LIGHTNING))
                ||!"LIGHTNING".equals(args.get("target_pokemon_type"))||args.containsKey("bind")||args.containsKey("bind_active")||args.containsKey("top_count"))
                throw new IllegalArgumentException("按对手全场能量附能须从弃牌区向己方雷宝可梦分配最多对应数量的基本雷能量");
        }
        if(opponentPrizesTaken(effect)&&(!args.equals(Map.of("source","DISCARD","amount","60","minimum","0","amount_mode","OPPONENT_PRIZES_TAKEN","basic_energy_only","true","energy_type","LIGHTNING","target_scope","OWN_FIELD","distribution","ANY"))
            ||effect.target()!=EffectTarget.SELECT_OWN_POKEMON))
            throw new IllegalArgumentException("按对手已取奖赏附能须从弃牌区向己方场上任意分配最多对应数量的基本雷能量");
        if(args.containsKey("amount_mode") && !opponentPrizesTaken(effect) && !coinHeads(effect) && !opponentFieldEnergy(effect) && !ownBenchCount(effect) && (!fieldEnergyDeficit(effect)
            ||source!=EffectTargetResolution.Zone.DISCARD||!basic||distribution!=Distribution.ANY
            ||effect.target()!=EffectTarget.SELECT_OWN_POKEMON||scope!=ServerMatch.InPlayTargetScope.OWN_FIELD
            ||spec.minimum()!=60||spec.maximum()!=60||args.containsKey("top_count")||args.containsKey("bind")||args.containsKey("bind_active")))
            throw new IllegalArgumentException("全场能量差额附能须从弃牌区选择基本能量、己方全场分配并尽可能补齐");
        if(args.containsKey("top_count")||args.containsKey("remainder")) {
            int look=topCount(effect);
            boolean discardWindow="DISCARD".equals(args.get("remainder"))&&!basic
                &&scope==ServerMatch.InPlayTargetScope.OWN_BENCH&&"FIGHTING".equals(args.get("target_pokemon_type"));
            boolean triWindow=effect.target()==EffectTarget.SELECT_OWN_POKEMON&&args.equals(Map.of(
                "source","DECK","amount","3","minimum","0","top_count","3","target_scope","OWN_FIELD","distribution","ANY","remainder","DISCARD"));
            boolean morganWindow=effect.target()==EffectTarget.SELECT_OWN_POKEMON&&args.equals(Map.of(
                "source","DECK","amount","12","minimum","0","top_count","12","target_scope","OWN_FIELD","distribution","ANY","remainder","DECK_SHUFFLED"));
            boolean rulerRoar=distribution==Distribution.SAME_TARGET&&effect.target()==EffectTarget.SELF
                &&scope==ServerMatch.InPlayTargetScope.OWN_FIELD&&basic&&look==4&&spec.maximum()==1&&spec.minimum()==0
                &&"SHUFFLE_BOTTOM".equals(args.get("remainder"));
            boolean oneHostPalette=distribution==Distribution.SAME_TARGET&&Set.of(EffectTarget.SELECT_OWN_POKEMON,EffectTarget.SELF).contains(effect.target())
                &&scope==ServerMatch.InPlayTargetScope.OWN_FIELD&&basic&&look==5&&spec.maximum()==5&&spec.minimum()==0
                &&"DECK_SHUFFLED".equals(args.get("remainder"));
            boolean horoscope=reordersRemainder(effect)&&effect.target()==EffectTarget.SELF&&args.equals(Map.of(
                "source","DECK","amount","3","minimum","0","top_count","3","remainder","REORDER_TOP","target_scope","OWN_FIELD"));
            if(look<1||look>60||source!=EffectTargetResolution.Zone.DECK||distribution!=Distribution.ANY&&!oneHostPalette&&!horoscope&&!rulerRoar
                ||!discardWindow&&!triWindow&&!morganWindow&&!horoscope&&(!basic||!Set.of(ServerMatch.InPlayTargetScope.OWN_FIELD,ServerMatch.InPlayTargetScope.OWN_BENCH).contains(scope))
                ||spec.minimum()!=0||spec.maximum()>look
                ||!horoscope&&!Set.of("HAND","DECK_SHUFFLED","SHUFFLE_BOTTOM","DISCARD").contains(args.getOrDefault("remainder","")))
                throw new IllegalArgumentException("查看牌库顶附能须明确1至60张、基本能量、己方任意分配、minimum:0及剩余牌去向");
        }
        return new AttachEnergyEffect(source, new ServerMatch.EnergyAttachmentPolicy(spec.minimum(), spec.maximum(), scope, basic, spec.allowedDefinitionIds()), types, distribution, special);
    }
    /** The attack controller remains the effect origin; only this private decision belongs to the opponent. */
    public static boolean opponentChooses(EffectDefinition effect) {return effect.type().equals("attach_energy")&&"OPPONENT".equals(effect.arguments().get("chooser"));}
    public static boolean coinHeads(EffectDefinition effect) {return effect.type().equals("attach_energy")&&"COIN_HEADS".equals(effect.arguments().get("amount_mode"));}
    public static int coinHeadLimit(EffectDefinition effect,EffectConditionContext context) {
        var result=context.namedCoinResult(AttackCoinBindings.name(effect.arguments().get("coin")));
        if(result==null)throw new IllegalStateException("附能缺少本次攻击已生成的硬币结果");
        return Math.min(60,result.heads());
    }
    public static int topCount(EffectDefinition effect) {return Integer.parseInt(effect.arguments().getOrDefault("top_count","0"));}
    public static boolean reordersRemainder(EffectDefinition effect) {return effect.type().equals("attach_energy")&&"REORDER_TOP".equals(effect.arguments().get("remainder"));}
    public static boolean fieldEnergyDeficit(EffectDefinition effect) {return "FIELD_ENERGY_DEFICIT".equals(effect.arguments().get("amount_mode"));}
    public static boolean opponentFieldEnergy(EffectDefinition effect) {return "OPPONENT_FIELD_ENERGY".equals(effect.arguments().get("amount_mode"));}
    public static boolean ownBenchCount(EffectDefinition effect) {return "OWN_BENCH_COUNT".equals(effect.arguments().get("amount_mode"));}
    public static boolean opponentPrizesTaken(EffectDefinition effect) {return effect.type().equals("attach_energy")&&"OPPONENT_PRIZES_TAKEN".equals(effect.arguments().get("amount_mode"));}
    public boolean allows(CardDefinition card) { return policy.allows(card) && (!specialEnergyOnly || !card.basicEnergy()); }
    private static String required(Map<String,String> args, String name) {
        String value = args.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("能量附着缺少参数：" + name);
        return value;
    }
}
