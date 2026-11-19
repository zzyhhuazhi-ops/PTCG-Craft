package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.ServerMatch;
import java.util.Map;
import java.util.Set;

/**
 * A parameterized selection of Energy already attached to one or more public
 * Pokémon.  It deliberately covers only one-source operations here: moving
 * Energy has a second public target and is handled by its own two-step
 * continuation rather than guessing a destination from card text.
 */
public record AttachedEnergyEffect(Mode mode, boolean all, int minimum, int maximum, ServerMatch.InPlayTargetScope destinationScope,
                                   dev.hua.ptcglive.engine.field.EnergyType energyType, EnergyCountQuery.CardKind cardKind,
                                   EnergyCountQuery.Mode countMode, boolean distributed, String energyTypes) {
    public AttachedEnergyEffect(Mode mode,boolean all,int minimum,int maximum,ServerMatch.InPlayTargetScope destinationScope,
        dev.hua.ptcglive.engine.field.EnergyType energyType,EnergyCountQuery.CardKind cardKind,EnergyCountQuery.Mode countMode,boolean distributed) {
        this(mode,all,minimum,maximum,destinationScope,energyType,cardKind,countMode,distributed,null);
    }
    public boolean typedPayment() {return energyTypes!=null;}
    public boolean jointTypedPayment() {return TypedEnergyDiscard.TYPES.equals(energyTypes);}
    public boolean singleTypeBasicPayment() {return TypedEnergyDiscard.SINGLE_TYPE_BASIC_TYPES.equals(energyTypes);}
    public AttachedEnergyEffect(Mode mode,boolean all,int minimum,int maximum,ServerMatch.InPlayTargetScope destinationScope,dev.hua.ptcglive.engine.field.EnergyType energyType,EnergyCountQuery.CardKind cardKind,EnergyCountQuery.Mode countMode) {this(mode,all,minimum,maximum,destinationScope,energyType,cardKind,countMode,false);}
    public AttachedEnergyEffect(Mode mode, boolean all, int minimum, int maximum, ServerMatch.InPlayTargetScope destinationScope,
                                dev.hua.ptcglive.engine.field.EnergyType energyType, EnergyCountQuery.CardKind cardKind) {
        this(mode,all,minimum,maximum,destinationScope,energyType,cardKind,EnergyCountQuery.Mode.CARDS);
    }
    public enum Mode { DISCARD, RETURN_TO_HAND, SHUFFLE_INTO_DECK, DECK_TOP, DECK_BOTTOM, LOST_ZONE, MOVE }
    public AttachedEnergyEffect(Mode mode, boolean all, int minimum, int maximum, ServerMatch.InPlayTargetScope destinationScope) {
        this(mode, all, minimum, maximum, destinationScope, null, EnergyCountQuery.CardKind.ANY);
    }
    public AttachedEnergyEffect {
        if(energyTypes!=null) {
            boolean joint=TypedEnergyDiscard.TYPES.equals(energyTypes)&&mode==Mode.DISCARD&&!all&&minimum==3&&maximum==3
                &&destinationScope==null&&energyType==null&&cardKind==EnergyCountQuery.CardKind.ANY&&countMode==EnergyCountQuery.Mode.UNITS&&!distributed;
            boolean single=TypedEnergyDiscard.SINGLE_TYPE_BASIC_TYPES.equals(energyTypes)&&mode==Mode.DISCARD&&!all&&minimum==0&&maximum>=1&&maximum<=60
                &&destinationScope==null&&energyType==null&&cardKind==EnergyCountQuery.CardKind.BASIC&&countMode==EnergyCountQuery.Mode.CARDS&&!distributed;
            if(!joint&&!single)throw new IllegalArgumentException("类型弃能参数组合无效");
        }
        java.util.Objects.requireNonNull(cardKind, "附着能量卡类别");
        java.util.Objects.requireNonNull(countMode, "附着能量计数方式");
        if(countMode==EnergyCountQuery.Mode.DISTINCT_TYPES)throw new IllegalArgumentException("种类计数只读，不能用于附着能量的支付或移动");
    }
    /**
     * {@code all:true} is deliberately a distinct, non-interactive operation:
     * it takes every Energy currently attached to each fixed public target.
     * A card may never combine it with a client-facing amount/minimum choice.
     */
    /** {@code bind}: an attack's cost records how many Energy cards it actually discarded (AttackCountBindings). */
    public static final Set<String> FIELDS = Set.of("amount", "minimum", "destination_scope", "destination_ref", "destination_pokemon_names", "all", "energy_type", "card_kind", "bind", "count_mode", "distribution", "energy_types", "single_type_choice", "destination", "definition_ids", "one_per_target", "amount_mode", "coin");
    public static final String WATER_COMMAND_NAMES="Psyduck|Horsea|Staryu|Starmie-GX|Magikarp|Gyarados|Lapras";

    /** The captured effect-source host, never the Pokémon that happens to be Active later. */
    public static boolean destinationChosen(EffectDefinition effect) { return "CHOSEN_POKEMON".equals(effect.arguments().get("destination_ref")); }
    public static boolean destinationSelf(EffectDefinition effect) { return "SELF".equals(effect.arguments().get("destination_ref")); }
    /** The actor's current Active Pokémon at the time the already-selected Energy is moved. */
    public static boolean destinationOwnActive(EffectDefinition effect) { return "OWN_ACTIVE".equals(effect.arguments().get("destination_ref")); }

    /** Exact reviewed other-own physical-energy transfer; 120 covers both conserved 60-card decks. */
    public static boolean otherOwnMetalToSelf(EffectDefinition effect) {
        return effect.type().equals("move_energy") && effect.target()==EffectTarget.ALL_OWN_POKEMON
            && effect.conditions().isEmpty() && effect.effects().isEmpty()
            && effect.arguments().keySet().equals(Set.of("amount","minimum","destination_scope","destination_ref","energy_type","count_mode"))
            &&"120".equals(effect.arguments().get("amount"))&&"1".equals(effect.arguments().get("minimum"))
            &&"OWN_FIELD".equals(effect.arguments().get("destination_scope"))&&"SELF".equals(effect.arguments().get("destination_ref"))
            &&"CARDS".equals(effect.arguments().get("count_mode"))
            &&Set.of("FIRE","METAL").contains(effect.arguments().get("energy_type"));
    }

    public static boolean handles(EffectDefinition effect) {
        return PlannedEnergyDiscard.plan(effect) || effect.type().equals("discard_energy") || effect.type().equals("return_energy")
            || effect.type().equals("attached_energy_to_lost_zone") || effect.type().equals("move_energy");
    }

    public static AttachedEnergyEffect from(EffectDefinition effect) {
        if(effect.arguments().containsKey("destination_pokemon_names")) {
            var expected=new java.util.HashMap<>(Map.of("amount","120","minimum",effect.arguments().getOrDefault("minimum",""),
                "energy_type","WATER","destination_scope","OWN_FIELD","distribution","ANY","destination_pokemon_names",WATER_COMMAND_NAMES));
            if(!effect.type().equals("move_energy")||effect.target()!=EffectTarget.ALL_OWN_POKEMON||!effect.effects().isEmpty()
                ||!effect.arguments().equals(expected)||!Set.of("0","1").contains(expected.get("minimum")))
                throw new IllegalArgumentException("指定名称的水能量分配参数无效");
            return new AttachedEnergyEffect(Mode.MOVE,false,Integer.parseInt(expected.get("minimum")),120,ServerMatch.InPlayTargetScope.OWN_FIELD,
                dev.hua.ptcglive.engine.field.EnergyType.WATER,EnergyCountQuery.CardKind.ANY,EnergyCountQuery.Mode.CARDS,true);
        }
        if(effect.type().equals("move_energy")&&effect.target()==EffectTarget.ALL_OWN_POKEMON
            &&effect.effects().isEmpty()&&effect.arguments().equals(Map.of("amount","120","minimum","0","energy_type","PSYCHIC","destination_scope","OWN_FIELD","distribution","ANY")))
            return new AttachedEnergyEffect(Mode.MOVE,false,0,120,ServerMatch.InPlayTargetScope.OWN_FIELD,
                dev.hua.ptcglive.engine.field.EnergyType.PSYCHIC,EnergyCountQuery.CardKind.ANY,EnergyCountQuery.Mode.CARDS,true);
        if(coinHeads(effect)) {
            if(effect.type().equals("discard_energy")&&effect.target()==EffectTarget.OPPONENT_ACTIVE
                &&effect.arguments().keySet().equals(Set.of("amount","minimum","amount_mode","coin","count_mode"))
                &&"UNITS".equals(effect.arguments().get("count_mode"))&&"COIN_HEADS".equals(effect.arguments().get("amount_mode"))
                &&"60".equals(effect.arguments().get("amount"))&&effect.effects().isEmpty()) {
                AttackCoinBindings.name(effect.arguments().get("coin"));
                // The minimum is replaced by the final coin result, bounded by available units.
                // Physical card limits do not cap units supplied by multi-unit Energy cards.
                return new AttachedEnergyEffect(Mode.DISCARD,false,integer(effect.arguments(),"minimum",0,Integer.MAX_VALUE),Integer.MAX_VALUE,null,
                    null,EnergyCountQuery.CardKind.ANY,EnergyCountQuery.Mode.UNITS);
            }
            if(!effect.type().equals("return_energy")||effect.target()!=EffectTarget.OPPONENT_ACTIVE
                ||!effect.arguments().keySet().equals(Set.of("amount","minimum","destination","amount_mode","coin"))
                ||!"DECK".equals(effect.arguments().get("destination"))||!"COIN_HEADS".equals(effect.arguments().get("amount_mode"))
                ||!"60".equals(effect.arguments().get("amount"))||!effect.effects().isEmpty())
                throw new IllegalArgumentException("按正面选能仅支持对方战斗宝可梦按张数回库或按能量个数弃置");
            AttackCoinBindings.name(effect.arguments().get("coin"));
            return new AttachedEnergyEffect(Mode.SHUFFLE_INTO_DECK,false,integer(effect.arguments(),"minimum",0,60),60,null);
        }
        if (otherOwnMetalToSelf(effect))
            return new AttachedEnergyEffect(Mode.MOVE,false,1,120,ServerMatch.InPlayTargetScope.OWN_FIELD,
                dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type")),EnergyCountQuery.CardKind.ANY,EnergyCountQuery.Mode.CARDS);
        if (PlannedEnergyDiscard.plan(effect)) return PlannedEnergyDiscard.selection(effect);
        if (!handles(effect) || !effect.effects().isEmpty()) throw new IllegalArgumentException("无效的附着能量选择效果");
        if(TypedEnergyDiscard.handles(effect)) {
            TypedEnergyDiscard.validate(effect);
            if(TypedEnergyDiscard.SINGLE_TYPE_BASIC_TYPES.equals(effect.arguments().get("energy_types")))
                return new AttachedEnergyEffect(Mode.DISCARD,false,Integer.parseInt(effect.arguments().get("minimum")),Integer.parseInt(effect.arguments().get("amount")),null,null,EnergyCountQuery.CardKind.BASIC,EnergyCountQuery.Mode.CARDS,false,TypedEnergyDiscard.SINGLE_TYPE_BASIC_TYPES);
            return new AttachedEnergyEffect(Mode.DISCARD,false,3,3,null,null,EnergyCountQuery.CardKind.ANY,EnergyCountQuery.Mode.UNITS,false,TypedEnergyDiscard.TYPES);
        }
        if (!FIELDS.containsAll(effect.arguments().keySet())) throw new IllegalArgumentException("附着能量选择含未支持参数");
        if(effect.arguments().containsKey("definition_ids")) {
            var query=EnergyCountQuery.from(Map.of("count_mode","CARDS","definition_ids",effect.arguments().get("definition_ids")));
            if(effect.type().equals("move_energy")||effect.arguments().containsKey("all")||effect.arguments().containsKey("energy_type")
                ||effect.arguments().containsKey("card_kind")||effect.arguments().containsKey("count_mode")||query.definitionIds().isEmpty())
                throw new IllegalArgumentException("指定印刷能量卡只支持按实体选择弃置或回手，不能混用其它筛选");
        }
        boolean shuffleIntoDeck="DECK".equals(effect.arguments().get("destination"));
        boolean deckTop="DECK_TOP".equals(effect.arguments().get("destination"));
        boolean deckBottom="DECK_BOTTOM".equals(effect.arguments().get("destination"));
        if(effect.arguments().containsKey("destination")) {
            boolean reviewedTop=deckTop&&effect.type().equals("return_energy")&&effect.target()==EffectTarget.OPPONENT_ACTIVE
                &&(effect.arguments().keySet().equals(Set.of("amount","minimum","destination"))
                    ||effect.arguments().equals(Map.of("amount","1","minimum","1","destination","DECK_TOP","count_mode","UNITS")));
            boolean reviewedBottom=deckBottom&&effect.type().equals("return_energy")&&effect.target()==EffectTarget.ALL_OPPONENT_POKEMON
                &&effect.arguments().equals(Map.of("amount","1","minimum","1","destination","DECK_BOTTOM","card_kind","SPECIAL"));
            if(!reviewedTop&&!reviewedBottom&&(!shuffleIntoDeck||!effect.type().equals("return_energy")||effect.target()!=EffectTarget.SELF))
                throw new IllegalArgumentException("能量回到牌库只支持自身全部洗回，或对方战斗宝可梦1张置顶");
            if(shuffleIntoDeck&&effect.arguments().containsKey("all")) {
                if(!effect.arguments().equals(Map.of("all","true","destination","DECK")))
                    throw new IllegalArgumentException("全部洗回能量不能混用选择参数");
                return new AttachedEnergyEffect(Mode.SHUFFLE_INTO_DECK,true,0,0,null);
            }
        }
        if (!isFixedPokemonTarget(effect.target()))
            throw new IllegalArgumentException("附着能量选择必须指定已公开的固定场上宝可梦区域");
        Mode mode = switch (effect.type()) {
            case "discard_energy" -> Mode.DISCARD;
            case "return_energy" -> shuffleIntoDeck?Mode.SHUFFLE_INTO_DECK:deckTop?Mode.DECK_TOP:deckBottom?Mode.DECK_BOTTOM:Mode.RETURN_TO_HAND;
            case "attached_energy_to_lost_zone" -> Mode.LOST_ZONE;
            default -> Mode.MOVE;
        };
        boolean distributed=effect.arguments().containsKey("distribution");
        if(effect.arguments().containsKey("one_per_target")&&(!"true".equals(effect.arguments().get("one_per_target"))
            ||!effect.type().equals("discard_energy")||effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON
            ||!"SPECIAL".equals(effect.arguments().get("card_kind"))))
            throw new IllegalArgumentException("逐只弃置仅支持对方全场每只宝可梦1张特殊能量");
        if(distributed && (mode!=Mode.MOVE||!"ANY".equals(effect.arguments().get("distribution"))
            ||!Set.of("OWN_BENCH","OWN_FIELD").contains(effect.arguments().get("destination_scope"))))
            throw new IllegalArgumentException("多目标转移仅支持己方场上ANY分配");
        if(effect.arguments().containsKey("destination_ref")) {
            boolean sourceHost=destinationSelf(effect)&&(effect.target()==EffectTarget.TRIGGER_POKEMON||effect.target()==EffectTarget.ALL_OWN_POKEMON);
            boolean activeAfterSwitch=destinationOwnActive(effect)&&effect.target()==EffectTarget.CHOSEN_POKEMON;
            boolean chosen=destinationChosen(effect)&&effect.target()==EffectTarget.SELF&&"1".equals(effect.arguments().get("amount"))&&"1".equals(effect.arguments().get("minimum"));
            if((!sourceHost&&!activeAfterSwitch&&!chosen)||mode!=Mode.MOVE||!"OWN_FIELD".equals(effect.arguments().get("destination_scope")))
                throw new IllegalArgumentException("固定能量目的地只支持事件宝可梦移至来源SELF，或已绑定宝可梦移至当前己方战斗位");
        }
        // “{R} Energy” = any attached Energy card currently providing {R}; “Special Energy” / “basic Energy” = the card kind.
        dev.hua.ptcglive.engine.field.EnergyType energyType = null;
        EnergyCountQuery.CardKind cardKind = EnergyCountQuery.CardKind.ANY;
        EnergyCountQuery.Mode countMode = EnergyCountQuery.Mode.CARDS;
        try {
            if (effect.arguments().containsKey("count_mode")) countMode=EnergyCountQuery.Mode.valueOf(effect.arguments().get("count_mode"));
            if (effect.arguments().containsKey("energy_type"))
                energyType = dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type"));
            if (effect.arguments().containsKey("card_kind"))
                cardKind = EnergyCountQuery.CardKind.valueOf(effect.arguments().get("card_kind"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("附着能量筛选的 energy_type 或 card_kind 无效", invalid);
        }
        if (effect.arguments().containsKey("bind")) {
            if (mode != Mode.DISCARD && mode != Mode.SHUFFLE_INTO_DECK && mode != Mode.RETURN_TO_HAND) throw new IllegalArgumentException("只有弃置、洗回或回手附着能量可以记录实际移动计数 bind");
            AttackCountBindings.name(effect.arguments().get("bind"));
        }
        boolean all = Boolean.parseBoolean(effect.arguments().getOrDefault("all", "false"));
        if (effect.arguments().containsKey("all") && !effect.arguments().get("all").equalsIgnoreCase("true")
            && !effect.arguments().get("all").equalsIgnoreCase("false"))
            throw new IllegalArgumentException("附着能量 all 必须为 true 或 false");
        if (all) {
            if(effect.arguments().containsKey("count_mode"))throw new IllegalArgumentException("全部能量不需要选择计数方式");
            if(effect.arguments().containsKey("minimum") && integer(effect.arguments(), "minimum", 0, 0) != 0)
                throw new IllegalArgumentException("全部能量的兼容 minimum 只能为 0");
            if(mode==Mode.MOVE) {
                if(effect.arguments().containsKey("amount"))throw new IllegalArgumentException("全部转移不能混用选择数量");
                String raw=effect.arguments().get("destination_scope");
                if(raw==null||raw.isBlank())throw new IllegalArgumentException("移动全部附着能量必须明确 destination_scope");
                try {return new AttachedEnergyEffect(mode,true,0,60,ServerMatch.InPlayTargetScope.valueOf(raw),energyType,cardKind,countMode,distributed);}
                catch(IllegalArgumentException invalid){throw new IllegalArgumentException("移动全部附着能量目的地范围无效",invalid);}
            }
            if (effect.arguments().containsKey("amount") || effect.arguments().containsKey("destination_scope"))
                throw new IllegalArgumentException("全部附着能量不能同时声明 amount 或 destination_scope");
            return new AttachedEnergyEffect(mode, true, 0, 0, null, energyType, cardKind);
        }
        int maximum = integer(effect.arguments(), "amount", 1, 60);
        if (!effect.arguments().containsKey("minimum"))
            throw new IllegalArgumentException("附着能量选择必须明确 minimum");
        int minimum = integer(effect.arguments(), "minimum", 0, maximum);
        ServerMatch.InPlayTargetScope destination = null;
        if (mode == Mode.MOVE) {
            String raw = effect.arguments().get("destination_scope");
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException("移动附着能量必须明确 destination_scope");
            try { destination = ServerMatch.InPlayTargetScope.valueOf(raw); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("移动附着能量目的地范围无效", invalid); }
        } else if (effect.arguments().containsKey("destination_scope"))
            throw new IllegalArgumentException("弃置、回手或放逐附着能量不能包含 destination_scope");
        return new AttachedEnergyEffect(mode, false, minimum, maximum, destination, energyType, cardKind, countMode,distributed);
    }

    public boolean requiresSelection() { return !all||mode==Mode.MOVE; }
    public static boolean coinHeads(EffectDefinition effect) {
        return handles(effect)&&(effect.arguments().containsKey("amount_mode")||effect.arguments().containsKey("coin"));
    }
    public boolean filtered() { return typedPayment() || energyType != null || cardKind != EnergyCountQuery.CardKind.ANY; }
    /** Reads the card's current, server-resolved provision; a filter never reserves or spends a payment unit. */
    public boolean allows(dev.hua.ptcglive.engine.field.EnergyProfile currentProfile, boolean basicEnergy) {
        if (cardKind == EnergyCountQuery.CardKind.BASIC && !basicEnergy || cardKind == EnergyCountQuery.CardKind.SPECIAL && basicEnergy) return false;
        return jointTypedPayment()?TypedEnergyDiscard.eligible(currentProfile)
            :singleTypeBasicPayment()?TypedEnergyDiscard.eligibleSingleType(currentProfile)
            :energyType == null || currentProfile.unitsOfType(energyType) > 0;
    }

    private static boolean isFixedPokemonTarget(EffectTarget target) {
        return switch (target) {
            // CHOSEN_POKEMON is a server-captured public incarnation from an
            // enclosing select_pokemon/with_pokemon scope, never a client target.
            case SELF, TRIGGER_POKEMON, ATTACKING_POKEMON, CHOSEN_POKEMON, OWN_ACTIVE, OPPONENT_ACTIVE, OWN_BENCH, OPPONENT_BENCH, ALL_OWN_POKEMON, ALL_OPPONENT_POKEMON -> true;
            default -> false;
        };
    }
    private static int integer(Map<String, String> values, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(values.get(key));
            if (value < minimum || value > maximum) throw new IllegalArgumentException("附着能量选择参数超出范围：" + key);
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("附着能量选择参数不是整数：" + key, invalid);
        }
    }
}
