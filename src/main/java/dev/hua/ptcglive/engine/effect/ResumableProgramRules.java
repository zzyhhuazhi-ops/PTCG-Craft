package dev.hua.ptcglive.engine.effect;

import java.util.*;
import dev.hua.ptcglive.engine.field.SpecialCondition;

/** Shared static contract for resumable Trainers, activated Abilities and after-damage attack programs. */
public final class ResumableProgramRules {
    private static final Set<EffectTarget> SINGLE = Set.of(EffectTarget.OWN_ACTIVE, EffectTarget.OPPONENT_ACTIVE,
        EffectTarget.SELECT_OWN_POKEMON, EffectTarget.SELECT_OPPONENT_POKEMON, EffectTarget.CHOSEN_POKEMON);
    private static final Set<EffectTarget> GROUP = Set.of(EffectTarget.OWN_BENCH, EffectTarget.OPPONENT_BENCH,
        EffectTarget.ALL_OWN_POKEMON, EffectTarget.ALL_OPPONENT_POKEMON);
    private ResumableProgramRules() { }
    public static Optional<String> unsupportedReason(EffectProgram program, boolean pokemonSource) {
        try { validate(program, pokemonSource); return Optional.empty(); }
        catch (IllegalArgumentException | IllegalStateException e) { return Optional.of(e.getMessage()); }
    }
    public static void validate(EffectProgram program, boolean pokemonSource) {
        EffectTrigger trigger = pokemonSource ? EffectTrigger.ONCE_PER_TURN : EffectTrigger.ON_CARD_PLAYED;
        validate(program, pokemonSource, trigger);
    }
    /** Attack entry supplies an explicit timing; it does not turn attack text into an Ability. */
    public static void validate(EffectProgram program, boolean pokemonSource, EffectTrigger trigger) {
        ActivationCostRules.validate(program, trigger);
        AttackDamageKoProtection.validateProgram(program);
        OpponentHandBottom.validateBindings(program);
        if (program.triggers().size() != 1 || program.triggers().getFirst().trigger() != trigger)
            throw new IllegalArgumentException("此组合须为一个 " + trigger + " 入口，其他触发时机尚未接入");
        // Trainers and activated Abilities have the same resumable server
        // transaction as attack follow-up text, but do not gain named coin
        // results. Attacks use their dedicated cross-phase data-flow check
        // below; a single-phase check would incorrectly reject a reference
        // first bound in BEFORE_ATTACK.
        boolean attackContinuation = pokemonSource && Set.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.AFTER_ATTACK).contains(trigger);
        boolean manualBinding = trigger == EffectTrigger.ONCE_PER_TURN || trigger == EffectTrigger.ONCE_PER_GAME
            || trigger == EffectTrigger.ON_CARD_PLAYED || trigger == EffectTrigger.ON_ACTIVATE;
        if (!attackContinuation && !manualBinding && AttackPokemonBindings.usesNamedBindings(program))
            throw new IllegalArgumentException("命名宝可梦绑定仅允许攻击、主动特性或训练家可续接入口");
        if (!attackContinuation) AttackPokemonBindings.validate(program, trigger);
        if (!attackContinuation && manualBinding) AttackCountBindings.validateManual(program, trigger);
        // BEFORE_ATTACK legacy SELECT_* is one declaration-time target. In
        // contrast, AFTER_ATTACK already owns a resumable server cursor: each
        // legacy SELECT_* atomic step can issue its own fresh public prompt.
        // Do not collapse two sequential post-damage choices into one
        // declaration parameter.
        if (trigger != EffectTrigger.AFTER_ATTACK) selectionTarget(program);
        else validateNoLegacySelectedConditions(program);
        validateDeferredTargetConditions(program);
        EffectResolutionPreflight.validate(program, trigger, c -> {
            if(c.type()==ConditionType.RETREAT_COST)
                throw new IllegalArgumentException("实时撤退费用条件目前仅核验ON_ATTACK伤害计算");
            if(attackContinuation) AttackProgramRules.attackCondition(c);
            else condition(c,pokemonSource);
            if(c.type()==ConditionType.ATTACK_DID_DAMAGE&&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))
                throw new IllegalArgumentException("攻击实际伤害只能在AFTER_ATTACK读取");
        }, effect -> {
            var target = effect.target();
            if(target==EffectTarget.CHOSEN_POKEMON)PokemonSelectionScope.validateBoundEffect(effect);
            switch (effect.type()) {
                case "extra_turn" -> {
                    if(!pokemonSource || trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("额外回合只能在攻击后的效果结算中登记");
                    EffectSchemaValidator.validate(effect);
                }
                case "discard_pokemon_to_bench_self" -> {
                    if (!pokemonSource || trigger != EffectTrigger.ONCE_PER_TURN
                        || !RevivingFlameAbility.reviveStep(effect))
                        throw new IllegalArgumentException("弃牌区自复活只核验复活火焰主动特性");
                }
                case "protect_on_attack_damage_ko" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("击倒后防护只能由攻击的 AFTER_ATTACK 登记");
                    AttackDamageKoProtection.from(effect);
                }
                case "delayed_effect" -> {
                    if(!pokemonSource || !Set.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.AFTER_ATTACK).contains(trigger))
                        throw new IllegalArgumentException("延迟攻击效果只能由统一攻击结算施加，其他来源语义尚未核验");
                    DelayedEffectProgram.from(effect);
                }
                case "no_prizes_if_ko_next_opponent_turn" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("奖赏压制只能由攻击后的原来源登记");
                    EffectSchemaValidator.validate(effect);
                }
                case "survive_next_turn_attack_damage" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("延迟生存替代只能由攻击后的原来源登记");
                    EffectSchemaValidator.validate(effect);
                }
                case "select_pokemon" -> PokemonSelectionScope.validate(effect);
                case "rock_paper_scissors" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("猜拳只接受训练家使用入口");
                    EffectSchemaValidator.validate(effect);
                }
                case RestoredPokemonRules.BENCH -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_ACTIVATE)throw new IllegalArgumentException("复原手牌上场只核验场地主动操作");
                    RestoredPokemonRules.validate(effect);
                }
                case RestoredPokemonRules.BOTTOM -> {
                    if(!pokemonSource||trigger!=EffectTrigger.ONCE_PER_TURN)throw new IllegalArgumentException("原始呼唤只核验弃牌区主动特性");
                    RestoredPokemonRules.validate(effect);
                }
                case DistinctBenchEnergy.TYPE -> {
                    DistinctBenchEnergy.validate(effect);
                    if(DistinctBenchEnergy.cheerfulCharge(effect)?!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK
                        :pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("预选备战附能须来自对应训练家或攻击伤害后入口");
                }
                case TopPrizeSwap.INSPECT,TopPrizeSwap.RESOLVE -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("顶牌奖赏交换只接受训练家使用入口");
                    TopPrizeSwap.validate(effect);
                }
                case PrizeSourceExchange.TYPE -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("奖赏交换只接受训练家使用入口");
                    PrizeSourceExchange.validate(effect);
                }
                case EnergyDeckSwap.SELECT, EnergyDeckSwap.RESOLVE -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("能量交换只接受训练家使用入口");
                    EnergyDeckSwap.validate(effect);
                }
                case "split_basic_energy_search", "discard_reorder_top", "guess_pokemon_hp", "top_distinct_category_search" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("此跨步骤效果只接受训练家使用入口");
                    Sv85TrainerEffects.validate(effect);
                }
                case BlainesQuiz.SELECT, CedricQuiz.SELECT -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("问答只能由训练家使用入口触发");
                    EffectSchemaValidator.validate(effect);
                }
                case TrainerRecoveryBargain.TYPE -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("弃牌训练家许可回收只能由训练家出牌入口触发");
                    TrainerRecoveryBargain.validate(effect);
                }
                case "tool_or_stadium_to_lost_zone", FieldToolStadiumDiscard.TYPE -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("放逐吸尘器只能由训练家出牌入口选择公开道具或场地");
                    EffectSchemaValidator.validate(effect);
                }
                case "opponent_top_basic_to_bench", "opponent_choose_deck_top" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("对手顶牌上场仅核验训练家续接");
                    EffectSchemaValidator.validate(effect);
                }
                case "replace_basic_from_discard" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("弃牌基础替换仅核验训练家使用入口");
                    PokemonDiscardReplacement.validate(effect);
                }
                case "replace_pokemon_from_deck" -> {
                    if(PokemonDeckReplacement.selectedBasic(effect)?pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED:!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("牌库替换须为训练家已选基础或攻击伤害后的原来源");
                    PokemonDeckReplacement.validate(effect);
                }
                case "replace_basic_from_deck" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.ONCE_PER_GAME)throw new IllegalArgumentException("变身启动仅核验整局一次的宝可梦主动特性");
                    BasicDeckReplacement.validate(effect);
                }
                case "evolve_from_hand" -> {
                    HandEvolutionEffect.validate(effect);
                    if(!pokemonSource||trigger!=EffectTrigger.ONCE_PER_TURN)
                        throw new IllegalArgumentException("手牌自我进化目前只核验每回合一次的宝可梦主动特性");
                }
                case "reveal_hand_cards" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.BEFORE_ATTACK)throw new IllegalArgumentException("选手牌展示计伤只允许攻击伤害前");
                    SelectedHandReveal.validate(effect);
                }
                case "shuffle_revealed_hand_cards" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("所展示手牌只能在同次攻击伤害后洗回");
                    ShuffleRevealedHandCards.binding(effect);
                }
                case "swap_hand_top_deck", "inspect_top_deck", "reorder_top_deck", "search_deck", "discard", "search_discard_pile", "attach_energy", "attach_tool" -> {
                    var zone = ZoneSelectionEffect.forProgram(effect, attackContinuation);
                    if(ZoneSelectionEffect.researchLabPlacement(effect)&&(pokemonSource||trigger!=EffectTrigger.ON_ACTIVATE))
                        throw new IllegalArgumentException("化石直接进化卡检索上场仅允许竞技场主动操作");
                    if(AttachEnergyEffect.opponentPrizesTaken(effect)&&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("按对手已取奖赏附能只核验攻击后续接");
                    if(ZoneSelectionEffect.opponentDiscardToLostZone(effect)&&(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("Opponent discard banishment requires a Trainer program");
                    if(ZoneSelectionEffect.scrapTools(effect)&&(!pokemonSource||trigger!=EffectTrigger.BEFORE_ATTACK))
                        throw new IllegalArgumentException("洛托姆V弃牌工具放逐和计伤只允许攻击伤害前的服务器续接");
                    if(ZoneSelectionEffect.summoningStar(effect)
                        &&(!pokemonSource||trigger!=EffectTrigger.ONCE_PER_GAME))
                        throw new IllegalArgumentException("星耀汇聚只能由整局一次的宝可梦主动特性执行");
                    if(ZoneSelectionEffect.newlyBenchedPokemonBinding(effect)!=null
                        &&!(pokemonSource&&trigger==EffectTrigger.AFTER_ATTACK||!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("新入场宝可梦绑定仅核验训练家或攻击后步骤");
                    if(zone.itemPokemonToBench() && (pokemonSource || trigger!=(effect.arguments().containsKey("trainer_name_equals")?EffectTrigger.ON_CARD_PLAYED:EffectTrigger.ON_ACTIVATE)))
                        throw new IllegalArgumentException("化石物品检索上场须由对应的训练家使用或竞技场主动操作执行");
                    if(AttachEnergyEffect.opponentChooses(effect)&&(!attackContinuation||trigger!=EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("对手自主附能只能通过招式伤害后的服务器续接");
                    if(AttachEnergyEffect.coinHeads(effect)&&trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("按正面附能仅核验攻击后的选择");
                    if(zone.discardTopCount()>0&&trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("弃顶回收只能在招式伤害后执行");
                    // “… put it onto this Pokémon to evolve it.” A Trainer has no “this Pokémon”; an attack evolves after its damage.
                    if (zone.evolvesSelf() && (!pokemonSource || trigger == EffectTrigger.BEFORE_ATTACK))
                        throw new IllegalArgumentException("检索进化只能由宝可梦的招式（AFTER_ATTACK）或主动特性使用");
                    // A Trainer's SELF is its player, never “this Pokémon”.
                    if (effect.type().equals("attach_energy") && target == EffectTarget.SELF && !pokemonSource)
                        throw new IllegalArgumentException("训练家没有“这只宝可梦”，不能把能量附到 SELF");
                }
                case "reveal_deck_window", "resolve_deck_window" -> {
                    if(!attackContinuation||trigger!=(AttackDeckWindow.reveal(effect)?EffectTrigger.BEFORE_ATTACK:EffectTrigger.AFTER_ATTACK))throw new IllegalArgumentException("公开计伤只能在伤害前，整理只能在同次攻击伤害后");
                    AttackDeckWindow.validate(effect);
                }
                case "plan_energy_discard", "commit_energy_discard" -> {
                    if (!attackContinuation || trigger != (PlannedEnergyDiscard.plan(effect) ? EffectTrigger.BEFORE_ATTACK : EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("选能只能用于伤害计算前，提交弃能只能用于同次攻击伤害后");
                    PlannedEnergyDiscard.validate(effect);
                }
                case "recover_all_discard" -> DiscardPileEffects.from(effect);
                case "discard_energy", "return_energy", "attached_energy_to_lost_zone", "move_energy" -> {
                    var energySelection=AttachedEnergyEffect.from(effect);
                    if(effect.arguments().containsKey("destination_pokemon_names")
                        &&(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED||!"1".equals(effect.arguments().get("minimum"))))
                        throw new IllegalArgumentException("指定名称的水能量分配须来自训练家使用且至少移动1张");
                    if(AttachedEnergyEffect.coinHeads(effect)&&(!attackContinuation||trigger!=EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("按正面选能仅核验攻击后的能量选择");
                    if(effect.type().equals("attached_energy_to_lost_zone")
                        &&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("附着能量放逐只能来自招式伤害后的服务器选牌续接");
                    if(TypedEnergyDiscard.handles(effect)
                        &&(!pokemonSource||trigger!=(energySelection.singleTypeBasicPayment()
                            ? EffectTrigger.BEFORE_ATTACK : EffectTrigger.AFTER_ATTACK)))
                        throw new IllegalArgumentException("类型弃能须在对应的攻击伤害计算阶段选择");
                    if(target==EffectTarget.SELF&&!pokemonSource)throw new IllegalArgumentException("训练家没有自身宝可梦可移动能量");
                    if(target.eventBound())throw new IllegalArgumentException("主动效果没有已捕获的事件宝可梦");
                }
                case "take_prize_cards" -> {
                    if(trigger!=(pokemonSource?EffectTrigger.AFTER_ATTACK:EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("直接拿奖须来自招式伤害后或训练家使用续接");
                    TakePrizeCards.validate(effect);
                }
                case "opponent_hand_to_bottom" -> {
                    if(!(pokemonSource&&trigger==EffectTrigger.AFTER_ATTACK)
                        &&!(!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("对手手牌处理须来自攻击伤害后或训练家使用续接");
                    OpponentHandBottom.validate(effect);
                }
                case "opponent_hand_to_deck" -> {
                    if(!(pokemonSource&&(trigger==EffectTrigger.AFTER_ATTACK||trigger==EffectTrigger.ON_EVOLVE))
                        &&!(!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("展示手牌洗回仅用于攻击伤害后或进化特性的续接");
                    OpponentHandShuffle.validate(effect);
                }
                case "opponent_choose_discard" -> OpponentHandDiscard.amount(effect);
                case "inspect_prize" -> EffectSchemaValidator.validate(effect);
                case "swap_prizes_hand" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("奖赏交换目前仅支持训练家使用入口");
                    PrizeHandSwap.maximum(effect);
                }
                case "prize_random_hand_swap" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("随机手牌奖赏交换只支持训练家使用入口");
                    EffectSchemaValidator.validate(effect);
                }
                case "turn_damage_reduction" -> {
                    if(pokemonSource?trigger!=EffectTrigger.ONCE_PER_GAME:trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("玩家下回合减伤须来自训练家使用或一次性 VSTAR 特性");
                    TurnDamageReduction.from(effect);
                }
                case "turn_attack_lock" -> {
                    var lock=TurnAttackLock.from(effect);
                    if(lock.ownerCurrentTurn()?(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        :(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("攻击限制须来自对应的训练家使用或招式结算阶段");
                }
                case "sleep_recovery_two_coins_until_cleared" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("睡眠双硬币恢复标记只支持招式AFTER_ATTACK结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "turn_choose_next_coin" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("一树投币选择须来自训练家结算");
                    TurnCoinChoice.validate(effect);
                }
                case "turn_gx_permission" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("本回合GX许可须来自训练家结算");
                    TurnGxPermission.from(effect);
                }
                case "remove_attack_effects" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("清除招式效果须来自训练家结算");
                    RemoveAttackEffects.both(effect);
                }
                case "turn_ability_suppression" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("本回合特性压制须来自训练家结算");
                    TurnAbilitySuppression.validate(effect);
                }
                case "turn_ignore_defender_damage_effects" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("玩家本回合穿透须来自训练家结算");
                    TurnAttackBypass.from(effect);
                }
                case "field_attack_protection" -> {
                    var protection=FieldAttackProtection.from(effect);
                    if(protection.sourceActiveBound()?(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK):(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("全场防护须按绑定方式来自招式或训练家结算");
                }
                case "turn_attack_bonus" -> {
                    if(!(pokemonSource&&trigger==EffectTrigger.ONCE_PER_TURN)
                        &&!(!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("本回合玩家加伤仅接受训练家使用或主动特性发动");
                    TurnAttackBonus.from(effect);
                }
                case "turn_extra_prize_on_attack_ko" -> {
                    if(pokemonSource||!Set.of(EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ON_ACTIVATE).contains(trigger))
                        throw new IllegalArgumentException("本回合额外奖赏只接受训练家使用或场上主动阶段");
                    TurnExtraPrizeOnAttackKo.from(effect);
                }
                case "move_damage_counter" -> {
                    var move=MoveDamageCounterEffect.from(effect);
                    if(move.all()&&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))throw new IllegalArgumentException("全量转移仅核验攻击伤害后的续接");
                    if(move.upTo()&&!manualBinding)throw new IllegalArgumentException("可选移动数量仅供训练家或主动特性的可续接入口");
                    if(move.sourceRef()==null)single(target, pokemonSource);
                }
                case "discard_tool", "return_tool" -> AttachedToolEffect.from(effect);
                case "switch_source_with_active" -> {
                    if(!pokemonSource||target!=EffectTarget.SELF||!effect.arguments().keySet().stream().allMatch(Set.of("bind")::contains))
                        throw new IllegalArgumentException("来源换位只支持场上宝可梦的SELF效果");
                }
                case "discard_tools_and_special_energy" -> {
                    if (!(pokemonSource&&Set.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.AFTER_ATTACK).contains(trigger)
                        ||!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("同时弃置工具和特殊能量只支持招式伤害前后或训练家使用结算");
                    DiscardToolsAndSpecialEnergy.validate(effect);
                }
                case "prevent_damage", "prevent_effects" -> { ProtectionEffect.from(effect); single(target,pokemonSource); }
                case "draw" -> {
                    DrawCount.from(effect);
                    if(DrawCount.upTo(effect)&&(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("按奖赏数可选抽牌只核验训练家使用入口");
                    number(effect, 1, 60);
                    if (target != EffectTarget.SELF && target != EffectTarget.HAND && target != EffectTarget.DECK
                        && target != EffectTarget.OWN_ACTIVE && target != EffectTarget.OPPONENT_ACTIVE)
                        throw new IllegalArgumentException("卡牌抽牌必须明确指定一名玩家");
                }
                case "discard_top_deck", "draw_bottom", "discard_random_hand", "shuffle_random_hand", "draw_until" -> {
                    if(effect.type().equals("discard_top_deck")&&effect.arguments().containsKey("recover_items")
                        &&(pokemonSource||!Set.of(EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ON_ACTIVATE).contains(trigger)))
                        throw new IllegalArgumentException("弃牌后回收物品须为训练家使用或场地发动");
                    if(effect.type().equals("discard_top_deck")&&effect.arguments().containsKey("per_source_energy_type")
                        &&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("按来源能量弃牌库顶只核验攻击伤害后");
                    number(effect, 1, 60);
                    if (target != EffectTarget.SELF && target != EffectTarget.HAND && target != EffectTarget.DECK && target != EffectTarget.OPPONENT_ACTIVE)
                        throw new IllegalArgumentException("弃牌库顶／随机弃手牌必须明确指定一名玩家");
                }
                case "discard_top_attach_energy" -> {
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("弃牌库顶附能只接受攻击后的实际攻击者");
                    DeckTopEnergyAttachmentEffect.validate(effect);
                }
                case "heal" -> {
                    if (HealingEffect.from(effect).readsAttackDamage()) {
                        if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                            throw new IllegalArgumentException("“回复与本次攻击伤害相同的量”只能由攻击的 AFTER_ATTACK 阶段结算");
                    } else if (HealingEffect.from(effect).mode()!=HealingEffect.Mode.ALL&&number(effect, 1, 1000) % 10 != 0) throw new IllegalArgumentException("回复量必须为 10 的倍数");
                    if (!SINGLE.contains(target) && !GROUP.contains(target) && !(pokemonSource && target == EffectTarget.SELF)) throw new IllegalArgumentException("卡牌回复需指定场上宝可梦，SELF 不是宝可梦");
                }
                case "clear_status" -> {
                    ClearSpecialConditionsEffect.from(effect);
                    if (target == EffectTarget.SELECT_OWN_POKEMON || target == EffectTarget.SELECT_OPPONENT_POKEMON)
                        PokemonEffectSelection.validate(effect);
                    else single(target, pokemonSource);
                }
                case "damage_counter" -> {
                    if (DamageCounterDistribution.handles(effect)) DamageCounterDistribution.from(effect);
                    else { var counters=DamageCounterEffect.from(effect);
                        boolean abilityField=Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(target)
                            &&effect.arguments().keySet().equals(Set.of("amount","has_abilities"));
                        if(abilityField&&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))throw new IllegalArgumentException("双方特性宝可梦指示物仅核验攻击后续接");
                        if(counters.sourceEnergyType()!=null)throw new IllegalArgumentException("按来源能量反击仅核验自身受伤特性");
                        if(DamageCounterEffect.expandedAttack(effect)&&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))throw new IllegalArgumentException("全体或倍增指示物仅审核攻击后续接");
                        if(DamageCounterEffect.wholeOpponentField(effect)&&!effect.arguments().keySet().equals(Set.of("amount"))
                            &&!effect.arguments().keySet().equals(Set.of("amount","status"))
                            &&!effect.arguments().equals(Map.of("amount","1","per_own_pokemon_names","Maushold"))
                            &&!abilityField
                            &&!effect.arguments().keySet().equals(Set.of("amount","damage_counters_min"))
                            &&!effect.arguments().keySet().equals(Set.of("amount","damage_counters_min","position"))
                            &&!effect.arguments().keySet().equals(Set.of("remaining_hp","position"))
                            &&!effect.arguments().keySet().equals(Set.of("remaining_hp","pokemon_families"))
                            &&!effect.arguments().equals(Map.of("counter_multiplier","2")))throw new IllegalArgumentException("全体对方攻击指示物须为固定数量或翻倍");
                        if(counters.extraTurnOnKo()&&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))throw new IllegalArgumentException("额外回合只能由攻击后指示物昏厥触发"); if(counters.fromTriggerDamage())throw new IllegalArgumentException("主动效果没有伤害事件数量"); if(!DamageCounterEffect.wholeOpponentField(effect)&&!abilityField)single(target, pokemonSource);if(PokemonEffectSelection.handles(effect))PokemonEffectSelection.validate(effect); }
                }
                case "discard_hand_keep", "return_source_to_bottom", "return_source_to_hand", "shuffle_source_into_deck", "discard_hand_at_turn_end", "draw_until_at_turn_end", "shuffle_hand_keep_at_turn_end" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("训练家后续效果须来自实际出牌");
                    TrainerEndEffects.validate(effect);
                }
                case "shuffle_named_bench_then_damage" -> {
                    if(!attackContinuation||trigger!=EffectTrigger.BEFORE_ATTACK)throw new IllegalArgumentException("突袭指令仅限攻击伤害前回库备战");
                    EffectSchemaValidator.validate(effect);
                }
                case DevolutionSpray.TYPE, DiscardedDragonEnergy.TYPE, DamagePump.TYPE, "discard_bench_until", "reveal_until_supporter", "reveal_top_take_pokemon", RandomHandSupporterReveal.TYPE, TopEnergyReveal.TYPE -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("逐张公开至支援者仅用于训练家使用流程");
                    EffectSchemaValidator.validate(effect);
                }
                case WitchDomain.TYPE -> {
                    if(!pokemonSource||trigger!=EffectTrigger.ONCE_PER_TURN)
                        throw new IllegalArgumentException("魔女领域须来自每回合一次的主动特性");
                    WitchDomain.validate(effect);
                }
                case "turn_prize_face_up" -> {
                    boolean allTrainer=!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED&&"true".equals(effect.arguments().get("all"));
                    if(!allTrainer&&(!attackContinuation||trigger!=EffectTrigger.BEFORE_ATTACK))throw new IllegalArgumentException("翻面奖赏须为招式伤害前步骤或训练家公开全部奖赏");
                    EffectSchemaValidator.validate(effect);
                }
                case "shuffle_unchosen_opponent_bench" -> {
                    boolean playerChoice=PokemonEffectSelection.opponentKeepsBench(effect);
                    if(playerChoice?(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED):(!attackContinuation||trigger!=EffectTrigger.AFTER_ATTACK))
                        throw new IllegalArgumentException("Bench retention must use the declared chooser and Trainer/attack timing");
                    PokemonEffectSelection.validate(effect);
                }
                case "use_opponent_hand_supporter", "move_all_bench_damage_to_active", "redistribute_opponent_damage_counters", "next_turn_extra_prize_if_defender_ko", "force_opponent_coin_tails", "shuffle_both_active_into_deck" -> {
                    if(!(attackContinuation&&trigger==EffectTrigger.AFTER_ATTACK)
                        &&!(effect.type().equals("use_opponent_hand_supporter")&&!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED))
                        throw new IllegalArgumentException("此效果只允许攻击后或已审核的训练家主动结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "unrecoverable_from_discard" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED||target!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                        throw new IllegalArgumentException("此回收禁止标记只用于训练家卡自身");
                    EffectSchemaValidator.validate(effect);
                }
                case "bench_damage" -> {
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("BenchDamageEffect 只能由攻击的 AFTER_ATTACK 阶段结算");
                    PokemonEffectSelection.validateBenchDamage(effect);
                }
                case DragonLauncherEffect.TYPE -> {
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("龙之发射器只允许攻击后的宝可梦续接");
                    DragonLauncherEffect.validate(effect);
                }
                case KnockoutCardsToHand.TYPE -> {
                    if(!pokemonSource||trigger!=EffectTrigger.ON_KNOCKOUT)throw new IllegalArgumentException("KO card return requires an attached host KO event");
                    KnockoutCardsToHand.validate(effect);
                }
                case "knockout_top_to_hand" -> {
                    if (!pokemonSource || trigger != EffectTrigger.ON_KNOCKOUT)
                        throw new IllegalArgumentException("击倒顶层卡回手仅限宝可梦自身的击倒触发");
                    KnockoutTopToHand.validate(effect);
                }
                case "self_damage", "recoil" -> {
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("自伤／反冲组件只能由攻击的 AFTER_ATTACK 阶段结算");
                    if (target != EffectTarget.SELF)
                        throw new IllegalArgumentException("自伤／反冲组件必须指定攻击者 SELF");
                    if (number(effect, 10, 1_000) % 10 != 0)
                        throw new IllegalArgumentException("自伤／反冲量必须为 10 的倍数");
                }
                case "lock_this_attack" -> {
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("锁定本招式只能由攻击的 AFTER_ATTACK 阶段结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "attack_coin_gate" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("攻击前掷币限制仅供AFTER_ATTACK结算");
                    AttackCoinGate.from(effect);
                }
                case "lock_opponent_attack" -> {
                    // “If the Defending Pokémon tries to attack during your opponent's next turn, your opponent flips a coin. …” /
                    // “Choose 1 of your opponent's Active Pokémon's attacks. During your opponent's next turn, that Pokémon can't use that attack.”
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException(effect.type() + " 只能由攻击的 AFTER_ATTACK 阶段结算");
                    if (target != EffectTarget.OPPONENT_ACTIVE || !effect.arguments().isEmpty())
                        throw new IllegalArgumentException(effect.type() + " 必须指定对方战斗宝可梦 OPPONENT_ACTIVE 且不带参数");
                }
                case "boost_attack_damage", "increase_damage_taken" -> {
                    // “During your next turn, this Pokémon's attacks do N more damage …” / “… the Defending Pokémon takes N more damage.”
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("下回合伤害增加只能由攻击的 AFTER_ATTACK 阶段结算");
                    if (target != (effect.type().equals("boost_attack_damage") ? EffectTarget.SELF : EffectTarget.OPPONENT_ACTIVE))
                        throw new IllegalArgumentException("下回合招式伤害增加必须指定攻击者 SELF；多受伤害必须指定对方战斗宝可梦 OPPONENT_ACTIVE");
                    boolean namedBoost = effect.type().equals("boost_attack_damage") && effect.arguments().containsKey("attack_id");
                    if (namedBoost) {
                        String attackId = effect.arguments().get("attack_id");
                        if (attackId == null || attackId.length() > 160 || !attackId.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
                            throw new IllegalArgumentException("指定招式伤害增加必须使用有效的印刷招式 ID");
                    }
                    if (!effect.arguments().keySet().equals(namedBoost ? Set.of("amount", "attack_id") : Set.of("amount")))
                        throw new IllegalArgumentException("下回合伤害增加含无效参数");
                    if (number(effect, 10, 1_000) % 10 != 0)
                        throw new IllegalArgumentException("下回合伤害增加量必须为 10 的倍数");
                }
                case "prevent_small_attack_damage", "next_turn_attack_base" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK||target!=EffectTarget.SELF)
                        throw new IllegalArgumentException("下回合自身攻击规则只能由招式 AFTER_ATTACK 作用于 SELF");
                    EffectSchemaValidator.validate(effect);
                }
                case "weakness_override" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("弱点改写只能由攻击的AFTER_ATTACK结算");
                    WeaknessOverride.validate(effect);
                }
                case "grant_star_cipher_ability" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("星之密码特性只能由攻击的AFTER_ATTACK赋予");
                    EffectSchemaValidator.validate(effect);
                }
                case "increase_attack_cost", "increase_retreat_cost" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("攻击费用增加只能由攻击的AFTER_ATTACK结算");
                    AttackCostIncrease.amount(effect);
                }
                case "reduce_attack_damage" -> {
                    // “During your opponent's next turn, the Defending Pokémon's attacks do N less damage.”
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("防守宝可梦攻击伤害减少只能由攻击的 AFTER_ATTACK 阶段结算");
                    if (target != EffectTarget.OPPONENT_ACTIVE)
                        throw new IllegalArgumentException("防守宝可梦攻击伤害减少必须指定对方战斗宝可梦 OPPONENT_ACTIVE");
                    if (number(effect, 10, 1_000) % 10 != 0)
                        throw new IllegalArgumentException("攻击伤害减少量必须为 10 的倍数");
                }
                case "reduce_damage" -> {
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("攻击减伤组件只能由攻击的 AFTER_ATTACK 阶段结算");
                    if (target != EffectTarget.SELF)
                        throw new IllegalArgumentException("攻击减伤组件必须指定攻击者 SELF");
                    if (number(effect, 10, 1_000) % 10 != 0)
                        throw new IllegalArgumentException("攻击减伤量必须为 10 的倍数");
                }
                case "modify_damage_taken" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("临时受伤修正只能由攻击AFTER_ATTACK结算");
                    AttackDamageReceivedModifier.from(effect);
                }
                case "switch", "force_switch", "gust", "retreat" -> {
                    if(effect.arguments().containsKey("source_only_if_benched")&&!pokemonSource)
                        throw new IllegalArgumentException("按来源位置换位需要场上宝可梦来源");
                    // A deck search may already bind its sole newly Benched Pokémon; do not ask for a second target.
                    if(target!=EffectTarget.CHOSEN_POKEMON)PokemonEffectSelection.validate(effect);
                    else if(!Set.of("switch","gust").contains(effect.type()))throw new IllegalArgumentException("已绑定换位仅支持己方换位或操控方选择对手备战");
                }
                case "discard_recover_named_supporter", "discard_stadium", "shuffle_hand", "discard_hand", "reveal_hand", "hand_to_deck_bottom", "opponent_hand_basic_to_bench", "shuffle_both_hands_bottom_draw_prizes" -> EffectSchemaValidator.validate(effect);
                case "turn_supporter_limit" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("本回合支援者总数只核验训练家使用入口");
                    EffectSchemaValidator.validate(effect);
                }
                case "win_game" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("直接胜利只支持招式伤害后结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "reset_prizes" -> {
                    if(pokemonSource||trigger!=EffectTrigger.ON_CARD_PLAYED)throw new IllegalArgumentException("重置奖赏只支持训练家使用入口");
                    EffectSchemaValidator.validate(effect);
                }
                case "evolve_each_bench_from_deck" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("逐只备战进化只能由攻击AFTER_ATTACK结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "evolve_selected_from_deck" -> {
                    boolean stadium=!pokemonSource&&trigger==EffectTrigger.ON_ACTIVATE&&effect.arguments().containsKey("evolutions");
                    boolean supporter=!pokemonSource&&trigger==EffectTrigger.ON_CARD_PLAYED&&(effect.arguments().containsKey("has_abilities")||TrainerDeckEvolution.handles(effect));
                    if(!stadium&&!supporter&&(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK))throw new IllegalArgumentException("选择逐只进化须由攻击、已审核竞技场或支援者结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "flustered_leap" -> {
                    if(!pokemonSource)throw new IllegalArgumentException("慌张跳跃必须来自场上宝可梦特性");
                    EffectSchemaValidator.validate(effect);
                }
                case "extra_prize_on_attack_ko", "prevent_hand_energy", "prevent_trainer_play", "trainer_attempt_coin_gate" -> {
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("限制使用训练家卡只能由攻击的 AFTER_ATTACK 阶段结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "discard_opponent_stadium_and_lock", "hand_energy_counter_trap", "hand_energy_end_turn_trap", "use_top_supporter" -> {
                    if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("此招式专属效果只能由攻击伤害后续接结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "retaliate" -> {
                    // A temporary reaction on the attacking Pokémon itself, installed after the attack's damage.
                    if (!pokemonSource || trigger != EffectTrigger.AFTER_ATTACK)
                        throw new IllegalArgumentException("受击反伤只能由攻击的 AFTER_ATTACK 阶段结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "opt_in" -> {
                    // “You may do N more damage. If you do, …”: decided before the damage is calculated.
                    if (!pokemonSource || trigger != EffectTrigger.BEFORE_ATTACK)
                        throw new IllegalArgumentException("opt_in 只能由攻击的 BEFORE_ATTACK 阶段结算");
                    EffectSchemaValidator.validate(effect);
                }
                case "devolve" -> {
                    if(target==EffectTarget.ALL_OPPONENT_POKEMON){
                        if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)throw new IllegalArgumentException("整方退化仅核验了攻击后的对手全场范围");
                    } else single(target,pokemonSource);
                }
                case "knock_out" -> {
                    if(target==EffectTarget.ALL_OPPONENT_POKEMON) {
                        if(!pokemonSource||trigger!=EffectTrigger.AFTER_ATTACK)
                            throw new IllegalArgumentException("整方击倒仅支持攻击效果结算");
                    } else single(target,pokemonSource);
                }
                case "poison", "burn", "paralyze", "sleep", "confuse", "return_to_hand", "shuffle_into_deck", "discard_pokemon", "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools" -> single(target, pokemonSource);
                case "prevent_retreat_poisoned" -> {
                    if(pokemonSource || trigger!=EffectTrigger.ON_CARD_PLAYED)
                        throw new IllegalArgumentException("中毒撤退限制须来自训练家使用");
                    EffectSchemaValidator.validate(effect);
                }
                default -> throw new IllegalArgumentException("卡牌组合尚未接入此效果的完整结算：" + effect.type());
            }
        }, true, attackContinuation, true, attackContinuation || manualBinding);
    }
    public static boolean hasChoices(EffectProgram program) {
        return program.triggers().stream().flatMap(binding -> binding.effects().stream()).anyMatch(ResumableProgramRules::hasChoice);
    }
    private static boolean hasChoice(EffectDefinition effect) {
        return effect.type().equals("choice") || DrawCount.upTo(effect) || PokemonSelectionScope.handles(effect) || ZoneSelectionEffect.handles(effect)
            || effect.type().equals("opponent_choose_discard") || effect.type().equals("opponent_choose_deck_top") || MoveDamageCounterEffect.handles(effect)&&MoveDamageCounterEffect.from(effect).upTo()
            || AttachedEnergyEffect.handles(effect) && AttachedEnergyEffect.from(effect).requiresSelection()
            || AttachedToolEffect.handles(effect) || DamageCounterDistribution.handles(effect) || PokemonEffectSelection.handles(effect)
            || effect.effects().stream().anyMatch(ResumableProgramRules::hasChoice);
    }
    /** Legacy bare SELECT targets are preselected; select_pokemon containers own independent continuation choices. */
    public static EffectTarget selectionTarget(EffectProgram program) {
        var targets = EnumSet.noneOf(EffectTarget.class);
        for (var binding : program.triggers()) {
            binding.conditions().forEach(c -> collect(c, targets));
            binding.effects().forEach(e -> collect(e, targets));
        }
        if (targets.size() > 1) throw new IllegalArgumentException("卡牌组合的多方独立目标选择尚未接入");
        return targets.isEmpty() ? null : targets.iterator().next();
    }
    /** A bare SELECT_* condition has no selected target before its atomic prompt. */
    private static void validateNoLegacySelectedConditions(EffectProgram program) {
        for (var binding : program.triggers()) {
            for (var condition : binding.conditions()) rejectLegacySelectedCondition(condition);
            for (var effect : binding.effects()) rejectLegacySelectedCondition(effect);
        }
    }
    private static void rejectLegacySelectedCondition(EffectDefinition effect) {
        for (var condition : effect.conditions()) rejectLegacySelectedCondition(condition);
        for (var child : effect.effects()) rejectLegacySelectedCondition(child);
    }
    private static void rejectLegacySelectedCondition(ConditionDefinition condition) {
        var target = EffectTarget.fromWire(condition.arguments().getOrDefault("target", "SELF"));
        if (target == EffectTarget.SELECT_OWN_POKEMON || target == EffectTarget.SELECT_OPPONENT_POKEMON)
            throw new IllegalArgumentException("伤害后独立目标条件必须使用select_pokemon作用域，不能引用尚未选择的旧式SELECT目标");
        for (var child : condition.children()) rejectLegacySelectedCondition(child);
    }
    private static void collect(EffectDefinition effect, Set<EffectTarget> targets) {
        // Attachment targets are selected at this effect's own continuation step, after any preceding coin/choice.
        if (!PokemonSelectionScope.handles(effect) && !PokemonEffectSelection.defersSelection(effect)
            && !effect.type().equals("attach_energy") && !effect.type().equals("attach_tool") && !AttachedToolEffect.handles(effect) && !DamageCounterDistribution.handles(effect)) add(effect.target(), targets);
        effect.conditions().forEach(c -> collect(c, targets));
        effect.effects().forEach(child -> collect(child, targets));
    }
    /** A legacy preselected condition cannot stand in for a target chosen later at an attachment step. */
    private static void validateDeferredTargetConditions(EffectProgram program) {
        var deferred = EnumSet.noneOf(EffectTarget.class);
        var conditional = EnumSet.noneOf(EffectTarget.class);
        for (var binding : program.triggers()) {
            binding.conditions().forEach(c -> collect(c, conditional));
            binding.effects().forEach(e -> collectDeferredConditions(e, deferred, conditional));
        }
        deferred.retainAll(conditional);
        if (!deferred.isEmpty()) throw new IllegalArgumentException("待选目标条件尚未接入附能／分配的续接绑定，不能用出牌前所选目标代替最终目标");
    }
    private static void collectDeferredConditions(EffectDefinition effect, Set<EffectTarget> deferred, Set<EffectTarget> conditional) {
        if (effect.type().equals("attach_energy") || effect.type().equals("attach_tool") || AttachedToolEffect.handles(effect) || DamageCounterDistribution.handles(effect)
            || PokemonEffectSelection.defersSelection(effect)) add(effect.target(), deferred);
        effect.conditions().forEach(c -> collect(c, conditional));
        effect.effects().forEach(child -> collectDeferredConditions(child, deferred, conditional));
    }
    private static void collect(ConditionDefinition condition, Set<EffectTarget> targets) {
        add(EffectTarget.fromWire(condition.arguments().getOrDefault("target", "SELF")), targets);
        condition.children().forEach(child -> collect(child, targets));
    }
    private static void add(EffectTarget target, Set<EffectTarget> targets) {
        if (target == EffectTarget.SELECT_OWN_POKEMON || target == EffectTarget.SELECT_OPPONENT_POKEMON) targets.add(target);
    }
    private static int number(EffectDefinition effect, int min, int max) {
        int amount = Integer.parseInt(effect.arguments().get("amount"));
        if (amount < min || amount > max) throw new IllegalArgumentException("卡牌 Effect 数量超出支持范围");
        return amount;
    }
    private static void single(EffectTarget target, boolean pokemonSource) {
        if (!SINGLE.contains(target) && !(pokemonSource && target == EffectTarget.SELF)) throw new IllegalArgumentException("卡牌此效果须指定一个公开宝可梦，不能把卡牌 SELF 当成宝可梦");
    }
    static void condition(ConditionDefinition condition, boolean pokemonSource) {
        if(condition.type()==ConditionType.OPPONENT_USED_LIMIT_GROUP||condition.type()==ConditionType.OWN_USED_LIMIT_GROUP) {EffectSchemaValidator.validate(condition);return;}
        if(condition.type()==ConditionType.ATTACK_DID_DAMAGE) {EffectSchemaValidator.validate(condition);if(!pokemonSource)throw new IllegalArgumentException("卡牌入口没有攻击伤害上下文");return;}
        if(TurnHistoryCondition.handles(condition.type())) {TurnHistoryCondition.validate(condition);return;}
        if(condition.type()==ConditionType.HAS_RESISTANCE||condition.type()==ConditionType.WEAK_TO_OPPONENT_ACTIVE) {
            EffectSchemaValidator.validate(condition);
            single(EffectTarget.fromWire(condition.arguments().getOrDefault("target","SELF")),pokemonSource);
            return;
        }
        var args = condition.arguments();
        Set<String> allowed = switch (condition.type()) {
            case HAS_STATUS, TARGET_HAS_STATUS -> Set.of("target", "status");
            case HAS_ENERGY -> Set.of("target","energy_type","count_mode","card_kind","definition_ids");
            case ENERGY_COUNT -> NumericConditionPolicy.allowedArguments(condition.type());
            case IS_EVOLVED, IS_BASIC, IS_EX, IS_V, IS_GX, IS_VSTAR, IS_LEGACY_EX, IS_VMAX, IS_TERA, BECAME_ACTIVE_THIS_TURN, BECAME_ACTIVE_LAST_OPPONENT_TURN, HEALED_THIS_TURN, HAS_ABILITIES, HAS_OTHER_SPECIAL_ENERGY, FIELDS_SHARE_POKEMON_TYPE, ALL_BENCH_DAMAGED, TOOL_ATTACHED_THIS_TURN, HAS_TOOL, HAS_RULE_BOX, IS_ACTIVE -> Set.of("target");
            case EVOLVED_THIS_TURN -> Set.of("target","from_pokemon_name");
            case HAS_TOOL_DEFINITION -> Set.of("target","definition_ids");
            case HAS_ABILITY -> Set.of("target","ability_id");
            case STADIUM_IN_PLAY -> Set.of("target","stadium_definition_ids","own_stadium");
            case KNOCKED_OUT_BY_ATTACK_LAST_TURN, KNOCKED_OUT_LAST_TURN -> Set.of("target","name_contains","pokemon_type");
            case IS_STAGE -> Set.of("target","stage");
            case HAS_ATTACK -> Set.of("target","attack_id");
            case IS_TYPE -> Set.of("target", "pokemon_type");
            case IS_FAMILY, ALL_POKEMON_FAMILY -> Set.of("target", "pokemon_family");
            case RETREAT_COST, POKEMON_COUNT, DAMAGE_COUNTER_COUNT, BENCH_COUNT, HAND_SIZE, DECK_SIZE, PRIZE_COUNT, DISCARD_COUNT, LOST_ZONE_COUNT -> NumericConditionPolicy.allowedArguments(condition.type());
            case NAME_EQUALS, NAME_CONTAINS, ALL_POKEMON_NAME_CONTAINS, HP_BELOW, HP_ABOVE, TURN_STATE -> Set.of("target", "value");
            case COIN_RESULT -> Set.of("target", "value", "coin", "tails");
            // Attack-only: the preflight rejects BOUND_COUNT wherever named counts are not available.
            case BOUND_COUNT -> Set.of("bind", "value", "comparison");
            default -> throw new IllegalArgumentException("卡牌组合尚未支持该条件：" + condition.type());
        };
        if (!allowed.containsAll(args.keySet())) throw new IllegalArgumentException("卡牌条件含未支持参数：" + condition.type());
        if (NumericConditionPolicy.handles(condition.type())) NumericConditionPolicy.validate(condition);
        if(condition.type()==ConditionType.HAS_ENERGY||condition.type()==ConditionType.ENERGY_COUNT)EnergyCountQuery.from(args);
        var target = EffectTarget.fromWire(args.getOrDefault("target", "SELF"));
        if(target.eventBound())throw new IllegalArgumentException("此入口没有服务器事件对象，不能使用事件绑定条件目标");
        switch (condition.type()) {
            case HAS_ENERGY, ENERGY_COUNT -> {if(!EnergyCountQuery.groupTarget(target))single(target,pokemonSource);}
            // A Bench / whole-side count is a public group total (“your Benched Pokémon have any damage counters”).
            case DAMAGE_COUNTER_COUNT -> {if(!EnergyCountQuery.groupTarget(target))single(target,pokemonSource);}
            case RETREAT_COST, NAME_EQUALS, NAME_CONTAINS, HP_BELOW, HP_ABOVE, HAS_STATUS, TARGET_HAS_STATUS,
                IS_EVOLVED, EVOLVED_THIS_TURN, IS_STAGE, IS_BASIC, IS_EX, IS_V, IS_GX, IS_VSTAR, IS_LEGACY_EX, IS_VMAX, IS_TERA, IS_TYPE, IS_FAMILY, BECAME_ACTIVE_THIS_TURN, BECAME_ACTIVE_LAST_OPPONENT_TURN, HEALED_THIS_TURN, HAS_ABILITIES, HAS_ABILITY, HAS_OTHER_SPECIAL_ENERGY, HAS_ATTACK, HAS_TOOL, HAS_RULE_BOX, IS_ACTIVE -> single(target, pokemonSource);
            default -> { }
        }
        if ((condition.type() == ConditionType.HAS_STATUS || condition.type() == ConditionType.TARGET_HAS_STATUS)
            && !ConditionEvaluator.ANY_STATUS.equals(args.get("status")))
            SpecialCondition.valueOf(args.get("status"));
        if (args.containsKey("comparison") && !Set.of("=", "==", "EQUALS", ">", "GREATER_THAN", ">=", "AT_LEAST", "<", "LESS_THAN", "<=", "AT_MOST")
            .contains(args.get("comparison").trim().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("未知卡牌条件比较符");
        if (condition.type() == ConditionType.TURN_STATE && !Set.of("FIRST_TURN", "SECOND_PLAYER_FIRST_TURN", "OWN_FIRST_TURN", "ACTOR_TURN", "OPPONENT_TURN", "WATER_GX_PERMISSION_UNUSED", "GX_DECK_EVOLUTION_AVAILABLE").contains(args.getOrDefault("value", "")))
            throw new IllegalArgumentException("未知卡牌回合条件");
        if(condition.type()==ConditionType.HAS_ABILITY)AttackCountBindings.name(args.get("ability_id"));
        if(condition.type()==ConditionType.STADIUM_IN_PLAY&&args.containsKey("stadium_definition_ids"))TurnHistoryCondition.values(args.get("stadium_definition_ids"));
        if(condition.type()==ConditionType.STADIUM_IN_PLAY&&args.containsKey("own_stadium")&&!Set.of("true","false").contains(args.get("own_stadium")))
            throw new IllegalArgumentException("竞技场归属条件必须为布尔值");
        if(condition.type()==ConditionType.EVOLVED_THIS_TURN&&args.containsKey("from_pokemon_name")&&args.get("from_pokemon_name").isBlank())
            throw new IllegalArgumentException("进化来源卡名不能为空");
    }
}
