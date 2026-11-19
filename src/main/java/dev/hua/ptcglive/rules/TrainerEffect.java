package dev.hua.ptcglive.rules;

/** Closed, data-driven opcode set. Unknown effects are rejected by the loader, never ignored. */
public record TrainerEffect(Opcode opcode, int amount, int sixPrizesAmount, int toolCapacity, int discardHandCost,
                            int minimumDeckSelections) {
    public enum Opcode { COMPOSED, DRAW, DRAW_UNTIL_HAND_SIZE, SEARCH_BASIC_POKEMON_TO_HAND, SEARCH_POKEMON_TO_HAND, SEARCH_CARD_TO_HAND, DISCARD_HAND_DRAW, SHUFFLE_HAND_DRAW, DISCARD_OPPONENT_RANDOM_HAND, DISCARD_STADIUM, CURE_OWN_ACTIVE_SPECIAL_CONDITIONS, HEAL, SWITCH_ACTIVE, SWITCH_OPPONENT_ACTIVE, EVOLVE_OWN_POKEMON_FROM_HAND, EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND,
        TOOL_RETREAT_REDUCTION, TOOL_RETREAT_REDUCTION_ALLOW_TWO, STADIUM_BASIC_RETREAT_REDUCTION, PASSIVE_TOOL, PASSIVE_STADIUM,
        PLAY_AS_POKEMON, DRAW_ON_TURN_DRAW, PRIZE_COIN_EXTRA_PRIZE, PRIZE_SEARCH_POKEMON, SETUP_FIRST_TICKET }
    public TrainerEffect {
        java.util.Objects.requireNonNull(opcode);
        if((opcode==Opcode.PRIZE_COIN_EXTRA_PRIZE||opcode==Opcode.PRIZE_SEARCH_POKEMON||opcode==Opcode.SETUP_FIRST_TICKET)&&(amount!=1||sixPrizesAmount!=0||toolCapacity!=0||discardHandCost!=0||minimumDeckSelections!=0))
            throw new IllegalArgumentException("专用时机物品只接受原生程序及固定数量1");
        if(opcode==Opcode.DRAW_ON_TURN_DRAW&&(amount!=3||sixPrizesAmount!=0||toolCapacity!=0||discardHandCost!=0||minimumDeckSelections!=0))
            throw new IllegalArgumentException("金珠只允许回合开始抽到且入手前使用并抽3张，不能混用其它参数");
        if ((opcode == Opcode.COMPOSED || opcode == Opcode.PLAY_AS_POKEMON) && (amount != 0 || sixPrizesAmount != 0 || toolCapacity != 0 || discardHandCost != 0 || minimumDeckSelections != 0))
            throw new IllegalArgumentException("组合训练家仅声明 EffectProgram，不能携带未执行的旧操作码参数");
        if((opcode==Opcode.PASSIVE_TOOL||opcode==Opcode.PASSIVE_STADIUM)
            &&(amount!=0||sixPrizesAmount!=0||discardHandCost!=0||minimumDeckSelections!=0||toolCapacity!=(opcode==Opcode.PASSIVE_TOOL?1:0)))
            throw new IllegalArgumentException("持续组合只允许基础附着容量，其它参数须放入PASSIVE效果");
        if (amount < 0 || amount > 60 || sixPrizesAmount < 0 || sixPrizesAmount > 60 || discardHandCost < 0 || discardHandCost > 59)
            throw new IllegalArgumentException("训练家效果数量超出支持范围");
        if (opcode == Opcode.HEAL && (amount == 0 || amount % 10 != 0)) throw new IllegalArgumentException("回复量须为正的 10 的倍数");
        if ((opcode == Opcode.DISCARD_STADIUM || opcode == Opcode.CURE_OWN_ACTIVE_SPECIAL_CONDITIONS || opcode == Opcode.SWITCH_ACTIVE || opcode == Opcode.SWITCH_OPPONENT_ACTIVE || opcode == Opcode.EVOLVE_OWN_POKEMON_FROM_HAND || opcode == Opcode.EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND) && (amount != 0 || sixPrizesAmount != 0)) throw new IllegalArgumentException("此效果不接受抽牌参数");
        if (opcode != Opcode.SHUFFLE_HAND_DRAW && sixPrizesAmount != 0) throw new IllegalArgumentException("该操作码不支持奖赏条件");
        if (opcode != Opcode.COMPOSED && opcode != Opcode.PLAY_AS_POKEMON && opcode != Opcode.PASSIVE_TOOL && opcode != Opcode.PASSIVE_STADIUM && opcode != Opcode.DISCARD_STADIUM && opcode != Opcode.CURE_OWN_ACTIVE_SPECIAL_CONDITIONS && opcode != Opcode.SWITCH_ACTIVE && opcode != Opcode.SWITCH_OPPONENT_ACTIVE && opcode != Opcode.EVOLVE_OWN_POKEMON_FROM_HAND && opcode != Opcode.EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND && amount == 0) throw new IllegalArgumentException("效果数量不能为空");
        if (isDeckSearchOpcode(opcode)) {
            if (minimumDeckSelections < 0 || minimumDeckSelections > amount)
                throw new IllegalArgumentException("牌库检索的最少选择数必须在 0 至检索上限之间");
        } else if (minimumDeckSelections != 0) {
            throw new IllegalArgumentException("非牌库检索效果不能声明最少牌库选择数");
        }
        if (isToolOpcode(opcode)) {
            if (toolCapacity < 1 || toolCapacity > 4) throw new IllegalArgumentException("宝可梦工具容量必须在 1 至 4 之间");
        } else if (toolCapacity != 0) throw new IllegalArgumentException("非工具效果不能声明工具容量");
        // A compact record constructor has not assigned this.opcode yet.
        if (discardHandCost > 0 && (isPersistent(opcode) || hasPokemonTarget(opcode)))
            throw new IllegalArgumentException("当前持续／指定场上目标训练家效果不能声明弃置手牌成本");
    }
    /** Compatibility constructor for existing data without a hand-discard cost. */
    public TrainerEffect(Opcode opcode, int amount, int sixPrizesAmount, int toolCapacity) {
        this(opcode, amount, sixPrizesAmount, toolCapacity, 0, defaultMinimumDeckSelections(opcode, amount));
    }
    /** Compatibility constructor: existing searches remain exact-count unless their data explicitly opts into a lower minimum. */
    public TrainerEffect(Opcode opcode, int amount, int sixPrizesAmount, int toolCapacity, int discardHandCost) {
        this(opcode, amount, sixPrizesAmount, toolCapacity, discardHandCost, defaultMinimumDeckSelections(opcode, amount));
    }
    /** Compatibility constructor for existing data. Only the legacy explicit second-tool opcode contributes capacity two. */
    public TrainerEffect(Opcode opcode, int amount, int sixPrizesAmount) {
        this(opcode, amount, sixPrizesAmount, opcode == Opcode.TOOL_RETREAT_REDUCTION_ALLOW_TWO ? 2 : isToolOpcode(opcode) ? 1 : 0, 0,
            defaultMinimumDeckSelections(opcode, amount));
    }
    private static boolean isToolOpcode(Opcode opcode) {
        return opcode == Opcode.TOOL_RETREAT_REDUCTION || opcode == Opcode.TOOL_RETREAT_REDUCTION_ALLOW_TWO || opcode == Opcode.PASSIVE_TOOL;
    }
    public boolean persistent() { return isPersistent(opcode); }
    private static boolean isPersistent(Opcode opcode) { return isToolOpcode(opcode) || opcode == Opcode.STADIUM_BASIC_RETREAT_REDUCTION || opcode == Opcode.PASSIVE_STADIUM; }
    public boolean needsPokemonTarget() { return hasPokemonTarget(opcode); }
    private static boolean hasPokemonTarget(Opcode opcode) { return opcode == Opcode.HEAL || opcode == Opcode.SWITCH_ACTIVE || opcode == Opcode.SWITCH_OPPONENT_ACTIVE || opcode == Opcode.EVOLVE_OWN_POKEMON_FROM_HAND || opcode == Opcode.EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND || isToolOpcode(opcode); }
    /** A forced switch exposes only the opponent's already-public Benched Pokémon as legal targets. */
    public boolean targetsOpponentPokemon() { return opcode == Opcode.SWITCH_OPPONENT_ACTIVE; }
    /** This reviewed Trainer also needs one evolution Pokémon already in its owner's hand. */
    public boolean needsEvolutionCardSelection() { return opcode == Opcode.EVOLVE_OWN_POKEMON_FROM_HAND || opcode == Opcode.EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND; }
    /** This effect is the strict-timing, exactly-one-stage-skip variation; it is not a general evolution override. */
    public boolean requiresSkippedEvolutionStage() { return opcode == Opcode.EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND; }
    public dev.hua.ptcglive.engine.field.EvolutionPermission evolutionPermission() {
        return switch (opcode) {
            case EVOLVE_OWN_POKEMON_FROM_HAND -> dev.hua.ptcglive.engine.field.EvolutionPermission.CARD_EFFECT_ALL_TIMING;
            case EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND -> dev.hua.ptcglive.engine.field.EvolutionPermission.CARD_EFFECT_SKIP_ONE_STAGE_STRICT_TIMING;
            default -> throw new IllegalStateException("此训练家效果不提供进化时点许可");
        };
    }
    /** This effect has owner-private deck choices, always resolved by the server. */
    public boolean needsDeckSelection() { return isDeckSearchOpcode(opcode); }
    /** Legacy one-click UI is safe only for a mandatory one-card search. */
    public boolean singleDeckSelection() { return needsDeckSelection() && amount == 1 && minimumDeckSelections == 1; }
    /** Closed selector set for Trainer deck searches. */
    public boolean allowsDeckSearchCandidate(CardDefinition definition) {
        return switch (opcode) {
            case SEARCH_BASIC_POKEMON_TO_HAND -> definition.basicPokemon();
            case SEARCH_POKEMON_TO_HAND -> definition.category() == CardCategory.POKEMON;
            case SEARCH_CARD_TO_HAND -> true;
            default -> false;
        };
    }
    /** Capacity contributed while this Tool remains attached; ordinary Pokémon start at one. */
    public int toolCapacityOnAttach() { return toolCapacity; }
    public int drawCount(int prizes) { return prizes == 6 && sixPrizesAmount > 0 ? sixPrizesAmount : amount; }
    private static boolean isDeckSearchOpcode(Opcode opcode) {
        return opcode == Opcode.SEARCH_BASIC_POKEMON_TO_HAND || opcode == Opcode.SEARCH_POKEMON_TO_HAND || opcode == Opcode.SEARCH_CARD_TO_HAND;
    }
    private static int defaultMinimumDeckSelections(Opcode opcode, int amount) {
        return isDeckSearchOpcode(opcode) ? amount : 0;
    }
}
