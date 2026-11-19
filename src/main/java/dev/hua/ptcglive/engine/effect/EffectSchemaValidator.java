package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Fails closed on malformed component data before it can enter an authoritative match. */
public final class EffectSchemaValidator {
    private static final Set<String> AMOUNT_EFFECTS = Set.of(
        "damage", "heal", "draw", "draw_bottom", "discard", "search_deck", "search_discard_pile", "attach_energy", "attach_tool", "move_damage_counter", "discard_tool", "return_tool", "damage_counter", "bench_damage", "self_damage", "recoil", "damage_multiplier", "prevent_small_attack_damage", "next_turn_attack_base",
        "damage_per_energy", "damage_per_damage_counter", "damage_per_bench", "damage_per_prize", "damage_per_bound", "damage_per_tails", "damage_per_special_condition", "damage_per_hand_card", "damage_per_pokemon", "damage_per_discard_card", "reduce_damage", "reduce_attack_damage", "boost_attack_damage", "increase_damage_taken", "discard_top_deck", "discard_random_hand", "shuffle_random_hand", "draw_until");
    private static final Set<String> POKEMON_TARGET_EFFECTS = Set.of(
        "damage", "heal", "switch", "force_switch", "gust", "retreat", "poison", "burn", "paralyze", "sleep", "confuse", "clear_status",
        "damage_counter", "move_damage_counter", "bench_damage", "self_damage", "recoil", "damage_per_prize", "damage_per_bound", "prevent_damage", "prevent_small_attack_damage", "next_turn_attack_base", "prevent_effects", "reduce_damage", "reduce_attack_damage", "boost_attack_damage", "increase_damage_taken", "attack_coin_gate", "lock_opponent_attack", "knock_out", "extra_prize_for_trigger_ko",
        "return_to_hand", "shuffle_into_deck", "discard_pokemon", "devolve", "prevent_evolution", "prevent_attack", "prevent_retreat", "disable_abilities", "disable_tools");

    private EffectSchemaValidator() { }

    public static void validate(EffectDefinition effect) {
        if(LegendaryStadiumRule.handles(effect.type())) {LegendaryStadiumRule.validate(effect);return;}
        if(effect.type().equals("return_after_own_attack_discard")) {
            if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of("pokemon_type","FIRE"))
                ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())throw new IllegalArgumentException("招式弃能回手须限定火宝可梦自身");
            return;
        }
        if(effect.type().equals("discard_hand")&&effect.arguments().containsKey("trainer_categories")
            &&(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(java.util.Map.of("trainer_categories","ITEM|TOOL"))))
            throw new IllegalArgumentException("筛选整手弃牌只核验对手手牌中的全部物品和工具");
        if(effect.type().equals("damage_per_damage_counter")&&effect.arguments().containsKey("pokemon_names")) {
            if(effect.target()!=EffectTarget.ALL_OWN_POKEMON)
                throw new IllegalArgumentException("按同名宝可梦伤害指示物计伤只支持己方全场");
            TurnHistoryCondition.values(effect.arguments().get("pokemon_names"));
        }
        if(effect.type().equals("trainer_attempt_coin_gate"))TrainerAttemptCoinGate.validate(effect);
        if(BorrowedAttackProgram.handles(effect.type()))BorrowedAttackProgram.validate(effect);
        // Only the attack-damage healing mode has no printed amount; other heal amounts are checked by each entry point.
        boolean healFromAttackDamage = effect.type().equals("heal") && Set.of("DAMAGE_DEALT","ALL").contains(effect.arguments().getOrDefault("mode","DAMAGE"));
        if (healFromAttackDamage || effect.type().equals("heal")&&effect.arguments().containsKey("discard_source_if_healed")) HealingEffect.from(effect);
        if (AMOUNT_EFFECTS.contains(effect.type()) && !healFromAttackDamage && !(effect.type().equals("move_damage_counter")&&effect.arguments().containsKey("all")) && !(effect.type().equals("damage_counter")&&(effect.arguments().containsKey("remaining_hp")||effect.arguments().containsKey("from_trigger_damage")||effect.arguments().containsKey("counter_multiplier")))) {
            if(effect.type().equals("damage_multiplier"))integer(effect.arguments().get("amount"),"damage_multiplier 的 amount",0,10);
            else positiveAmount(effect, "amount", 1, 1_000);
        }
        if (POKEMON_TARGET_EFFECTS.contains(effect.type()) && !isPokemonTarget(effect.target()))
            throw new IllegalArgumentException(effect.type() + " 必须以公开场上宝可梦为目标");
        if(AttackDeckWindow.handles(effect))AttackDeckWindow.validate(effect);
        if (PlannedEnergyDiscard.handles(effect)) PlannedEnergyDiscard.validate(effect);
        switch (effect.type()) {
            case "self_damage", "recoil" -> {
                if(effect.arguments().containsKey("amount_mode")
                    &&(!effect.type().equals("self_damage")||effect.target()!=EffectTarget.SELF
                       ||!"SELF_DAMAGE_COUNTERS".equals(effect.arguments().get("amount_mode"))))
                    throw new IllegalArgumentException("按指示物自伤只支持攻击者自身的当前伤害指示物");
            }
            case RestoredPokemonRules.BENCH,RestoredPokemonRules.BOTTOM -> RestoredPokemonRules.validate(effect);
            case TrainerRecoveryBargain.TYPE -> TrainerRecoveryBargain.validate(effect);
            case TrainerRecoveryBargain.RESOLVE -> TrainerRecoveryBargain.validateResolution(effect);
            case KnockoutCardsToHand.TYPE -> KnockoutCardsToHand.validate(effect);
            case "knockout_top_to_hand" -> KnockoutTopToHand.validate(effect);
            case DragonLauncherEffect.TYPE -> DragonLauncherEffect.validate(effect);
            case "discard_pokemon_to_bench_self" -> {
                if (!RevivingFlameAbility.reviveStep(effect))
                    throw new IllegalArgumentException("弃牌区自复活只允许无参数的 SELF 原子效果");
            }
            case "damage" -> {
                if(effect.arguments().containsKey("mode")&&!"REPLACE".equals(effect.arguments().get("mode")))
                    throw new IllegalArgumentException("攻击伤害替换模式只接受 REPLACE");
            }
            case "discard_recover_named_supporter" -> {
                if(effect.target()!=EffectTarget.DISCARD||!effect.arguments().keySet().equals(Set.of("definition_ids","hand_discard_cost"))
                    ||!"2".equals(effect.arguments().get("hand_discard_cost"))||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("弃牌区支援者回收须明确自身印刷编号及弃2张手牌成本");
                TurnHistoryCondition.values(effect.arguments().get("definition_ids"));
            }
            case "extra_turn" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of("skip_checkup","true"))
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("额外回合须为攻击后无条件SELF效果并明确跳过本次宝可梦检查");
            }
            case "reveal_hand_cards" -> SelectedHandReveal.validate(effect);
            case "shuffle_revealed_hand_cards" -> ShuffleRevealedHandCards.binding(effect);
            case "bench_attack_damage_bonus" -> PassiveRuleProgram.validate(effect);
            case "per_target_coin_damage" -> PerTargetCoinDamage.validate(effect);
            case "repeat_target_damage" -> {
                if(effect.target()!=EffectTarget.OPPONENT_BENCH||!effect.arguments().keySet().equals(Set.of("count","amount"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("重复选目标计伤只接受当前对方伤害目标、count与amount");
                positiveAmount(effect,"count",1,60);positiveAmount(effect,"amount",10,1000);
                if(Integer.parseInt(effect.arguments().get("amount"))%10!=0)throw new IllegalArgumentException("重复选目标伤害须为10的倍数");
            }
            case "grant_attack" -> ToolGrantedAttack.validate(effect);
            case "knock_out" -> {
                if(effect.target()==EffectTarget.ALL_OPPONENT_POKEMON) {
                    if(!effect.arguments().equals(java.util.Map.of("remaining_hp_max","50"))||!effect.effects().isEmpty())
                        throw new IllegalArgumentException("整方击倒仅支持剩余HP不超过50的固定筛选");
                } else if(!effect.arguments().keySet().stream().allMatch(Set.of("bind")::contains)||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("单体击倒不接受额外参数");
                if(effect.arguments().containsKey("bind"))AttackCountBindings.name(effect.arguments().get("bind"));
            }
            case "reroll_attack_coins" -> AttackCoinRetry.from(effect);
            case AttackTailsDraw.TYPE -> AttackTailsDraw.validate(effect);
            case "modify_damage_taken" -> AttackDamageReceivedModifier.from(effect);
            case "replace_pokemon_from_deck" -> PokemonDeckReplacement.validate(effect);
            case "replace_basic_from_deck" -> BasicDeckReplacement.validate(effect);
            case "replace_basic_from_discard" -> PokemonDiscardReplacement.validate(effect);
            case "prize_pokemon_to_bench_coin_extra_prize" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("幸运奖励须为无参数SELF奖赏入场效果");
            }
            case "split_basic_energy_search", "discard_reorder_top", "guess_pokemon_hp", "top_distinct_category_search" -> Sv85TrainerEffects.validate(effect);
            case FieldToolStadiumDiscard.TYPE -> FieldToolStadiumDiscard.validate(effect);
            case "tool_or_stadium_to_lost_zone" -> {
                boolean vacuum=effect.target()==EffectTarget.SELF&&effect.arguments().isEmpty();
                boolean opponentAttachments=effect.target()==EffectTarget.ALL_OPPONENT_POKEMON
                    &&effect.arguments().equals(java.util.Map.of("include_special_energy","true"));
                if(!vacuum&&!opponentAttachments
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("Lost Vacuum须从公开场选择1张工具或场地放逐");
            }
            case "sleep_recovery_two_coins_until_cleared" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().isEmpty()
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("熟睡歌声须在对手战斗宝可梦睡眠后登记双硬币恢复");
            }
            case BlainesQuiz.SELECT -> BlainesQuiz.validate(effect);
            case CedricQuiz.SELECT -> CedricQuiz.validate(effect);
            case "resolve_hp_guess" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("guessed_hp","opponent_draw","owner_draw"))
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())throw new IllegalArgumentException("Tyme猜测结算参数无效");
                for(String key:Set.of("guessed_hp","opponent_draw","owner_draw"))positiveAmount(effect,key,1,1_000);
            }
            case "rock_paper_scissors" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of("winner_draw","2"))
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("猜拳须为双方秘密选择、平局重来、己方获胜抽2张的完整流程");
            }
            case DistinctBenchEnergy.TYPE -> DistinctBenchEnergy.validate(effect);
            case TopPrizeSwap.INSPECT,TopPrizeSwap.RESOLVE -> TopPrizeSwap.validate(effect);
            case PrizeSourceExchange.TYPE -> PrizeSourceExchange.validate(effect);
            case EnergyDeckSwap.SELECT, EnergyDeckSwap.RESOLVE -> EnergyDeckSwap.validate(effect);
            case "prevent_damage", "prevent_effects" -> { if(effect.arguments().containsKey("bound_to_top_card"))ProtectionEffect.from(effect); }
            case "ignore_resistance" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("无视抗性组件须为SELF，不接受参数或子效果");
            }
            case "damage_per_discard_card", "recover_all_discard" -> DiscardPileEffects.from(effect);
            case "discard_top_deck" -> TopDeckDiscardEffect.from(effect);
            case "discard_top_attach_energy" -> DeckTopEnergyAttachmentEffect.validate(effect);
            case "inspect_top_deck" -> TopCardInspection.validate(effect);
            case "inspect_prize" -> {
                boolean opponentOne=effect.target()==EffectTarget.OPPONENT_ACTIVE&&effect.arguments().equals(java.util.Map.of("amount","1"));
                boolean ownAll=effect.target()==EffectTarget.SELF&&effect.arguments().equals(java.util.Map.of("amount","6","minimum","0"));
                if(!opponentOne&&!ownAll
                    ||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("查看奖赏只支持对手1张或自己的全部暗面奖赏");
            }
            case "swap_hand_top_deck" -> HandTopDeckSwap.validate(effect);
            case "swap_prizes_hand" -> PrizeHandSwap.maximum(effect);
            case "prize_random_hand_swap" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().keySet().equals(Set.of("amount"))
                    ||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("随机手牌奖赏交换只接受对手目标与amount");
                if(integer(effect.arguments().get("amount"),"随机手牌奖赏交换数量",1,1)!=1)
                    throw new IllegalArgumentException("随机手牌奖赏交换目前只支持1张奖赏");
            }
            case "win_game" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("直接胜利须为无参数SELF效果");
            }
            case "reset_prizes" -> {
                if(effect.target()!=EffectTarget.SELF||!Set.of("destination").containsAll(effect.arguments().keySet())
                    ||!Set.of("DECK_BOTTOM","DECK_SHUFFLED").contains(effect.arguments().getOrDefault("destination","DECK_BOTTOM"))
                    ||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("重置奖赏须为无条件SELF效果，原奖赏只能洗入牌库或洗牌置底");
            }
            case "turn_supporter_limit" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of("amount","3"))
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("本回合支援者上限须为SELF固定总数3，不能累加");
            }
            case "discard_hand_keep", "shuffle_hand_keep", "return_source_to_bottom", "return_source_to_hand", "shuffle_source_into_deck", "discard_hand_at_turn_end", "draw_until_at_turn_end", "shuffle_hand_keep_at_turn_end" -> TrainerEndEffects.validate(effect);
            case "unrecoverable_from_discard" -> {
                boolean stadium=effect.target()==EffectTarget.SELF&&effect.arguments().isEmpty();
                boolean ability=DiscardRecoveryRule.abilityMarker(effect);
                if(!stadium&&!ability||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("弃牌不可回收标记须为无条件无参数SELF效果");
            }
            case "coin_copy_attack" -> CopyAttackProgram.validateCopyEffect(effect);
            case "copy_attack" -> CopyAttackProgram.validateCopyEffect(effect);
            case "copy_attack_from_opponent_deck_top", "copy_attack_from_own_deck_top" -> CopyAttackProgram.validateCopyEffect(effect);
            case "opponent_choose_discard" -> OpponentHandDiscard.amount(effect);
            case "extra_prize_on_attack_ko" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty()||!effect.arguments().keySet().equals(Set.of("amount","basic_only"))
                    ||!Set.of("true","false").contains(effect.arguments().get("basic_only")))throw new IllegalArgumentException("额外奖赏须为当前攻击结算且明确是否限定基础宝可梦");
                positiveAmount(effect,"amount",1,3);
            }
            case "extra_prize_for_trigger_ko" -> {
                if(effect.target()!=EffectTarget.TRIGGER_POKEMON||!effect.effects().isEmpty()
                    ||!Set.of(Set.of("amount"),Set.of("amount","nonstack_group")).contains(effect.arguments().keySet())
                    ||effect.arguments().containsKey("nonstack_group")&&effect.arguments().get("nonstack_group").isBlank())
                    throw new IllegalArgumentException("击倒事件额外奖赏须指定触发宝可梦、数量及不叠加组");
                positiveAmount(effect,"amount",1,3);
            }
            case "prevent_prize_take" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of(
                    "all_prizes","true","attacker_filter","V"))||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("击倒时零奖赏须限定自身、全部奖赏及实际攻击者 Pokémon V");
            }
            case "damage_from_last_opponent_attack" -> PreviousAttackDamage.validate(effect);
            case "take_prize_cards" -> TakePrizeCards.validate(effect);
            case "opponent_hand_to_bottom" -> OpponentHandBottom.validate(effect);
            case "opponent_hand_to_deck" -> OpponentHandShuffle.validate(effect);
            case "opponent_hand_basic_to_bench" -> {
                boolean small=effect.arguments().equals(java.util.Map.of("hp_max","70"));
                boolean any=effect.arguments().keySet().equals(Set.of("amount","minimum"));
                boolean switchIn=effect.arguments().equals(java.util.Map.of("amount","1","minimum","1","switch_in","true"));
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!small&&!any&&!switchIn||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("从对手手牌放置基础宝可梦参数无效");
                if(any) {
                    int maximum=integer(effect.arguments().get("amount"),"手牌基础宝可梦上场上限",1,60);
                    integer(effect.arguments().get("minimum"),"手牌基础宝可梦上场下限",0,maximum);
                }
            }
            case "hand_to_deck_bottom" -> HandToDeckBottom.validate(effect);
            case "shuffle_both_hands_bottom_draw_prizes" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("双方手牌洗至牌库底须为SELF效果");
                if(!effect.arguments().isEmpty()) {
                    if(!effect.arguments().keySet().equals(java.util.Set.of("own_draw", "opponent_draw")))
                        throw new IllegalArgumentException("固定抽牌须同时指定双方数量");
                    integer(effect.arguments().get("own_draw"),"己方抽牌数",0,60);
                    integer(effect.arguments().get("opponent_draw"),"对手抽牌数",0,60);
                }
            }
            case "prevent_retreat_poisoned" -> {
                if (effect.target() != EffectTarget.OPPONENT_ACTIVE || !effect.effects().isEmpty()
                    || !effect.arguments().isEmpty())
                    throw new IllegalArgumentException("中毒宝可梦禁止撤退须指定对手场上并且不接受额外参数");
            }
            case "special_red_card" -> {
                if (effect.target() != EffectTarget.SELF || !effect.effects().isEmpty()
                    || !effect.arguments().keySet().equals(Set.of("bind")))
                    throw new IllegalArgumentException("特殊红牌效果参数无效");
            }
            case "turn_choose_next_coin" -> TurnCoinChoice.validate(effect);
            case "turn_gx_permission" -> TurnGxPermission.from(effect);
            case "remove_attack_effects" -> RemoveAttackEffects.both(effect);
            case "turn_ability_suppression" -> TurnAbilitySuppression.validate(effect);
            case "turn_attack_bonus" -> TurnAttackBonus.from(effect);
            case "turn_damage_reduction" -> TurnDamageReduction.from(effect);
            case "turn_attack_lock" -> TurnAttackLock.from(effect);
            case "force_opponent_coin_tails" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(java.util.Map.of("duration","OPPONENT_NEXT_TURN"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("对手投币视为反面仅核验下个对手回合的玩家限制");
            }
            case "use_opponent_hand_supporter" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(java.util.Map.of("optional","true"))||!effect.effects().isEmpty())throw new IllegalArgumentException("魔仿秀须选择已公开的对手手牌支援者，可以不选");
            }
            case "move_all_bench_damage_to_active", "redistribute_opponent_damage_counters" -> {
                if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON||!effect.arguments().equals(java.util.Map.of("optional","true"))||!effect.effects().isEmpty())throw new IllegalArgumentException("任意指示物移动仅核验对手场上可选招式");
            }
            case "shuffle_named_bench_then_damage", "shuffle_unchosen_opponent_bench" -> PokemonEffectSelection.validate(effect);
            case "opponent_choose_deck_top" -> {
                if(effect.target()!=EffectTarget.DECK||!effect.arguments().equals(java.util.Map.of("top_count","5","amount","2"))
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("公开顶牌须由对手选2张弃置，顶5张其余加入手牌");
            }
            case "opponent_top_basic_to_bench" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(java.util.Map.of("top_count","5","minimum","0","amount","5"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("对手牌库顶上场只核验公开顶5张中的任意基础宝可梦");
            }
            case "next_turn_extra_prize_if_defender_ko" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(java.util.Map.of("amount","2"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("下个自己回合额外奖赏仅核验防御宝可梦昏厥多取2张");
            }
            case "shuffle_both_active_into_deck" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().equals(java.util.Map.of("acting_player_selects_first","true"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("双方战斗位回库须由攻击方先选替补");
            }
            case "field_attack_protection" -> FieldAttackProtection.from(effect);
            case "turn_ignore_defender_damage_effects" -> TurnAttackBypass.from(effect);
            case "turn_extra_prize_on_attack_ko" -> TurnExtraPrizeOnAttackKo.from(effect);
            case "weakness_override" -> WeaknessOverride.validate(effect);
            case "grant_star_cipher_ability" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("星之密码仅能无条件赋予招式使用者自身特性");
            }
            case "attack_coin_gate" -> AttackCoinGate.from(effect);
            case "increase_attack_cost", "increase_retreat_cost" -> AttackCostIncrease.amount(effect);
            case "attack_cost_override" -> AttackCostOverride.from(effect);
            case "damage_per_pokemon" -> PokemonCountEffect.from(effect);
            case "passive_attack_guard" -> PassiveAttackGuard.from(effect);
            case "prevent_small_attack_damage" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("amount"))||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("小额攻击伤害防护须为无条件SELF并指定amount");
                positiveAmount(effect,"amount",10,1000);
            }
            case "next_turn_attack_base" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("attack_id","amount"))||!effect.effects().isEmpty()||!effect.conditions().isEmpty()
                    ||!effect.arguments().get("attack_id").matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
                    throw new IllegalArgumentException("下回合基础伤害覆盖须指定SELF、attack_id与amount");
                positiveAmount(effect,"amount",10,1000);
            }
            case "evolve_each_bench_from_deck" -> {
                if(effect.target()!=EffectTarget.OWN_BENCH||!effect.arguments().isEmpty()||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("逐只检索进化只接受无条件OWN_BENCH");
            }
            case "evolve_selected_from_deck" -> {
                if(TrainerDeckEvolution.handles(effect)){TrainerDeckEvolution.validate(effect);break;}
                boolean typed=effect.arguments().keySet().equals(Set.of("target_count","minimum","pokemon_type"));
                boolean chained=effect.arguments().keySet().equals(Set.of("target_count","minimum","target_stage","evolutions"));
                boolean noAbilityBench=effect.arguments().keySet().equals(Set.of("target_count","minimum","target_stage","evolutions","has_abilities","bench_only"))
                    &&"false".equals(effect.arguments().get("has_abilities"))&&"true".equals(effect.arguments().get("bench_only"));
                boolean bench=effect.arguments().keySet().equals(Set.of("target_count","minimum","evolutions","bench_only"))
                    &&"true".equals(effect.arguments().get("bench_only"));
                if(effect.target()!=EffectTarget.SELECT_OWN_POKEMON||!typed&&!chained&&!noAbilityBench&&!bench
                    ||!"0".equals(effect.arguments().get("minimum"))||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("选择宝可梦逐只进化参数无效");
                int count=Integer.parseInt(effect.arguments().get("target_count"));
                if(count<1||count>6)throw new IllegalArgumentException("选择进化目标上限须为1至6");
                if(typed)dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("pokemon_type"));
                else if(bench) {
                    if(count!=2||!"1".equals(effect.arguments().get("evolutions")))
                        throw new IllegalArgumentException("备战区批量进化须最多选择2只并各进化1次");
                } else if(!"BASIC".equals(effect.arguments().get("target_stage"))||count!=1
                    ||chained&&!"2".equals(effect.arguments().get("evolutions"))
                    ||noAbilityBench&&!"1".equals(effect.arguments().get("evolutions")))
                    throw new IllegalArgumentException("连续进化须选择1只基础宝可梦并声明审核过的进化次数");
            }
            case "flustered_leap" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
                    throw new IllegalArgumentException("慌张跳跃只接受无条件SELF");
            }
            case "flare_gear","cafe_master_turn_continues","inherit_poison_on_own_trainer_switch","discard_replacing_stadium","add_pokemon_families","prize_energy_attach","knockout_prizes_to_discard","knockout_pokemon_to_lost_zone","discard_from_field_to_hand","retreat_energy_to_hand","skip_evolution_turn_end","attack_while_asleep_or_paralyzed", "retreat_while_asleep_or_paralyzed", "retreat_attempt_coin_gate", "prevent_damage_counter_move", "prevent_healing", "prevent_return_to_hand", "checkup_poison_bonus", "sleep_recovery_two_coins", "attack_weakness_multiplier","bench_weakness_resistance","weakness_multiplier", "passive_effect_guard", "prevent_checkup_recovery", "no_weakness","no_resistance","prevent_item_supporter_effects","prevent_self_attack_damage", "special_condition_immunity", "evolution_timing_override", "stadium_attack_guard", "passive_trainer_play_lock", "passive_pokemon_play_lock", "first_turn_attack_permission", "disable_all_tools","disable_opponent_active_tools", "prevent_opponent_attack_ability_counters","prevent_bench_damage_counters", "retain_confusion_on_evolution", "retain_conditions_on_evolution", "retain_poison_on_evolution", "setup_active_override", "basic_energy_multiplier_aura", "bench_entry_damage_counters", "tool_hand_play_guard", "pokemon_type_override", "add_pokemon_types", "weakness_override_aura", "suppress_self_knockout_abilities", "prize_reduction_aura", "coin_prize_reduction" -> PassiveRuleProgram.validate(effect);
            case "draw" -> DrawCount.from(effect);
            case "draw_bottom" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().equals(Set.of("amount"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("从牌库底抽牌只接受SELF与amount");
                positiveAmount(effect,"amount",1,60);
            }
            case "delayed_effect" -> DelayedEffectProgram.from(effect);
            case "protect_on_attack_damage_ko" -> AttackDamageKoProtection.from(effect);
            case "no_prizes_if_ko_next_opponent_turn" -> {
                if(effect.target()!=EffectTarget.SELF
                    ||!effect.arguments().equals(java.util.Map.of("duration","OPPONENT_NEXT_TURN"))
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("下个对手回合昏厥不取奖赏须为自身且限定到下个对手回合");
            }
            case "survive_next_turn_attack_damage" -> {
                if(effect.target()!=EffectTarget.SELF
                    ||!effect.arguments().equals(java.util.Map.of("remaining_hp","10","requires_full_hp","true","duration","OPPONENT_NEXT_TURN"))
                    ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("下个对手回合生存替代须为满HP自身、致命攻击伤害与剩余10HP");
            }
            case "discard_pokemon" -> {
                if(!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("整只宝可梦弃置不接受参数或子步骤");
            }
            case "discard_source" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("来源卡自弃不接受目标替换、参数或子效果");
            }
            // A player's hidden zone: SELF/HAND/DECK = the source's controller, OPPONENT_ACTIVE = the opponent (same convention as draw).
            case "discard_random_hand", "shuffle_random_hand", "draw_until" -> {
                if (!java.util.Set.of(EffectTarget.SELF, EffectTarget.HAND, EffectTarget.DECK, EffectTarget.OPPONENT_ACTIVE).contains(effect.target())
                    || !effect.effects().isEmpty() || !(effect.type().equals("discard_random_hand") ? Set.of("amount","mode","destination")
                        : effect.type().equals("draw_until") ? DrawCount.FIELDS : Set.of("amount")).containsAll(effect.arguments().keySet()))
                    throw new IllegalArgumentException(effect.type() + " 只接受 amount，目标须为己方（SELF/HAND/DECK）或对手（OPPONENT_ACTIVE）");
                if(effect.arguments().containsKey("mode")&&!"UNTIL_HAND_SIZE".equals(effect.arguments().get("mode")))
                    throw new IllegalArgumentException("随机弃牌模式只支持UNTIL_HAND_SIZE");
                if(effect.arguments().containsKey("destination")&&!"LOST_ZONE".equals(effect.arguments().get("destination")))
                    throw new IllegalArgumentException("随机手牌的非弃牌区目的地只支持LOST_ZONE");
                if(effect.type().equals("draw_until"))DrawCount.from(effect);else positiveAmount(effect, "amount", 1, 60);
            }
            // “Shuffle your hand into your deck.”: SELF/HAND/DECK = the source's controller, OPPONENT_ACTIVE = the opponent.
            // “This Pokémon can't use [this attack] again until it leaves the Active Spot.”
            case "lock_this_attack" -> {
                if (effect.target() != EffectTarget.SELF || !effect.arguments().isEmpty() || !effect.effects().isEmpty())
                    throw new IllegalArgumentException("lock_this_attack 只能指定攻击者 SELF，不接受参数或子效果");
            }
            // “Your opponent reveals their hand. This attack does N damage for each Trainer card you find there.”:
            // OPPONENT_ACTIVE counts the opponent's hand, SELF the attacker's own; card_category is required.
            case "damage_per_hand_card" -> {
                if (!java.util.Set.of(EffectTarget.SELF, EffectTarget.OPPONENT_ACTIVE).contains(effect.target()) || !effect.effects().isEmpty()
                    || !java.util.Set.of("amount", "card_category", "trainer_category", "limit").containsAll(effect.arguments().keySet()))
                    throw new IllegalArgumentException("damage_per_hand_card 只接受 amount、card_category、trainer_category，目标须为 SELF 或 OPPONENT_ACTIVE");
                if(effect.arguments().containsKey("limit"))integer(effect.arguments().get("limit"),"手牌计数上限",1,60);
                var category = dev.hua.ptcglive.rules.CardCategory.valueOf(effect.arguments().getOrDefault("card_category", ""));
                if (effect.arguments().containsKey("trainer_category")) {
                    var trainer = dev.hua.ptcglive.rules.TrainerCategory.valueOf(effect.arguments().get("trainer_category"));
                    if (category != dev.hua.ptcglive.rules.CardCategory.TRAINER || trainer == dev.hua.ptcglive.rules.TrainerCategory.NONE)
                        throw new IllegalArgumentException("trainer_category 只能用于训练家卡");
                }
            }
            // “Your opponent reveals their hand.”: SELF/HAND = the source's controller, OPPONENT_ACTIVE = the opponent.
            case "reveal_hand" -> HandRevealEffect.validate(effect);
            case "shuffle_hand", "discard_hand" -> {
                if (!java.util.Set.of(EffectTarget.SELF, EffectTarget.HAND, EffectTarget.DECK, EffectTarget.OPPONENT_ACTIVE).contains(effect.target())
                    || !effect.effects().isEmpty() || !effect.arguments().keySet().stream().allMatch(key->key.equals("bind")||effect.type().equals("discard_hand")&&key.equals("trainer_categories")))
                    throw new IllegalArgumentException("手牌整体处理参数无效，目标须为己方（SELF/HAND/DECK）或对手（OPPONENT_ACTIVE）");
                if(effect.arguments().containsKey("bind"))AttackCountBindings.name(effect.arguments().get("bind"));
            }
            case "discard_stadium" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().stream().allMatch("bind"::equals)||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("DiscardStadiumEffect 只接受可选成功数量绑定，不接受目标替换或子效果");
                if(effect.arguments().containsKey("bind"))AttackCountBindings.name(effect.arguments().get("bind"));
            }
            case "discard_opponent_stadium_and_lock", "use_top_supporter" -> {
                if(!effect.arguments().isEmpty()||!effect.effects().isEmpty()
                    ||effect.target()!=(effect.type().equals("use_top_supporter")?EffectTarget.DECK:EffectTarget.OPPONENT_ACTIVE))
                    throw new IllegalArgumentException(effect.type()+" 的目标或参数无效");
            }
            case "hand_energy_counter_trap" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(java.util.Map.of("amount","8"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("手牌附能陷阱须指定对方战斗宝可梦及8个伤害指示物");
            }
            case "hand_energy_end_turn_trap" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("手牌附能结束回合陷阱须指定对方战斗宝可梦且不带参数");
            }
            case "turn_prize_face_up" -> {
                boolean all=effect.arguments().equals(java.util.Map.of("all","true"));
                if(!(effect.target()==EffectTarget.SELF||all&&effect.target()==EffectTarget.OPPONENT_ACTIVE)||!all&&!effect.arguments().equals(java.util.Map.of(
                    "amount","1","minimum","0","bind","crescent-purge"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("翻开奖赏只接受自身1张可选暗面奖赏及计数绑定");
                if(!all)AttackCountBindings.name(effect.arguments().get("bind"));
            }
            case DevolutionSpray.TYPE -> DevolutionSpray.validate(effect);
            case DiscardedDragonEnergy.TYPE -> DiscardedDragonEnergy.validate(effect);
            case DamagePump.TYPE -> DamagePump.validate(effect);
            case WitchDomain.TYPE -> WitchDomain.validate(effect);
            case RandomHandSupporterReveal.TYPE -> RandomHandSupporterReveal.validate(effect);
            case TopEnergyReveal.TYPE -> TopEnergyReveal.validate(effect);
            case "reveal_until_supporter" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("逐张公开至支援者须指定自身牌库且不包含额外参数");
            }
            case "reveal_top_take_pokemon" -> {
                if(!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())
                    ||!effect.arguments().equals(java.util.Map.of("amount","3"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("公开牌库顶并取出全部宝可梦须指定一方玩家及3张");
            }
            case "discard_bench_until" -> {
                if(!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())||!effect.arguments().equals(java.util.Map.of("amount","3"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("玩家弃置备战须明确一方及保留3只");
            }
            // Devolution is deliberately distinct from leaving play: the same
            // in-play Pokémon keeps its damage, attachments and identity while
            // only its top Evolution card returns to the owner's hand.
            case "devolve" -> {
                if(!Set.of("amount","destination").containsAll(effect.arguments().keySet())||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("DevolveEffect 只接受可选 amount、destination 且不接受子效果");
                int amount=Integer.parseInt(effect.arguments().getOrDefault("amount","1"));
                if(amount<1||amount>2)throw new IllegalArgumentException("退化层数须为1至2");
                if(effect.arguments().containsKey("destination")&&!"DECK_SHUFFLED".equals(effect.arguments().get("destination")))
                    throw new IllegalArgumentException("退化目的地只支持默认手牌或洗入牌库");
            }
            case "prevent_attack" -> {
                if (effect.arguments().containsKey("source_active_only")) {
                    if (effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON
                        || !effect.arguments().equals(java.util.Map.of("source_active_only","true"))
                        || !effect.effects().isEmpty())
                        throw new IllegalArgumentException("持续攻击限制须由战斗位来源作用于对手全场");
                } else if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON) AttackLockEffect.from(effect);
            }
            case "target_filter" -> AttackTargetFilter.validate(effect);
            case "prevent_evolution" -> EvolutionLockEffect.from(effect);
            case "prevent_retreat", "disable_abilities", "disable_tools" -> {
                boolean activeAura=effect.type().equals("prevent_retreat")&&effect.target()==EffectTarget.OPPONENT_ACTIVE
                    &&effect.arguments().equals(java.util.Map.of("source_active_only","true"));
                if(!activeAura&&!effect.arguments().isEmpty()||!effect.effects().isEmpty())
                    throw new IllegalArgumentException(effect.type() + " 不接受参数或子效果");
            }
            case "damage_per_energy" -> {EnergyCountQuery.from(effect.arguments());EnergyCountQuery.validateDamageTarget(effect.target());}
            case "damage_per_prize" -> PrizeCountEffect.mode(effect);
            // “… this attack does N more damage for each card you discarded in this way.”
            case "damage_per_bound" -> {
                if (!Set.of("bind", "amount").equals(effect.arguments().keySet()) || !effect.effects().isEmpty())
                    throw new IllegalArgumentException("damage_per_bound 只接受 bind、amount");
                AttackCountBindings.name(effect.arguments().get("bind"));
            }
            case "damage_per_tails" -> {
                if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().keySet().equals(Set.of("coin","amount"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("按反面数量计伤只接受对方战斗宝可梦、coin与amount");
                AttackCoinBindings.name(effect.arguments().get("coin"));
            }
            case "damage_per_special_condition" -> {
                if(!Set.of(EffectTarget.OPPONENT_ACTIVE,EffectTarget.SELF).contains(effect.target())
                    ||!effect.arguments().keySet().equals(Set.of("amount"))||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("按异常状态数量计伤只接受自身或对方战斗宝可梦与amount");
            }
            // “During your opponent's next turn, if this Pokémon is damaged by an attack, put N damage counters on the
            // Attacking Pokémon.” amount = N counters (1–30), or mode DAMAGE_DONE = “equal to the damage done to this Pokémon”.
            case "retaliate" -> {
                if (effect.target() != EffectTarget.SELF || !effect.effects().isEmpty())
                    throw new IllegalArgumentException("retaliate 只能指定使用招式的宝可梦 SELF，不接受子效果");
                boolean byDamage = "DAMAGE_DONE".equals(effect.arguments().get("mode"));
                if (byDamage ? !Set.of("mode").equals(effect.arguments().keySet()) : !Set.of("amount").equals(effect.arguments().keySet()))
                    throw new IllegalArgumentException("retaliate 只接受 amount（伤害指示物个数）或 mode DAMAGE_DONE 之一");
                if (!byDamage) positiveAmount(effect, "amount", 1, 30);
            }
            // “… is now Poisoned. Put N damage counters instead of 1 on that Pokémon between turns.” (Burned: instead of 2)
            case "poison", "burn", "confuse" -> {
                if (!Set.of("counters").containsAll(effect.arguments().keySet()))
                    throw new IllegalArgumentException(effect.type() + " 只接受 counters（宝可梦检查时的伤害指示物数量）");
                if (effect.arguments().containsKey("counters"))
                    integer(effect.arguments().get("counters"), effect.type() + " 的 counters", effect.type().equals("poison") ? 2 : effect.type().equals("burn") ? 3 : 4, 60);
            }
            // “During your opponent's next turn, they can't play any Item (Supporter) cards from their hand.”
            case "prevent_hand_energy" -> HandEnergyLock.onPlayer(effect);
            case "prevent_trainer_play" -> {
                if (effect.target() != EffectTarget.OPPONENT_ACTIVE || !effect.effects().isEmpty()
                    || !Set.of("trainer_category").equals(effect.arguments().keySet())
                    || !Set.of("ITEM", "SUPPORTER", "STADIUM").contains(effect.arguments().get("trainer_category")))
                    throw new IllegalArgumentException("prevent_trainer_play 只能指定对手（OPPONENT_ACTIVE），只接受 trainer_category ITEM、SUPPORTER 或 STADIUM");
            }
            // “You may do N more damage. If you do, …”: the player accepts or declines; acceptance is recorded under bind.
            case "opt_in" -> {
                if (effect.target() != EffectTarget.SELF || !Set.of("bind").equals(effect.arguments().keySet())
                    || !effect.effects().isEmpty() || !effect.conditions().isEmpty())
                    throw new IllegalArgumentException("opt_in 只接受 bind，不接受目标、条件或子效果");
                AttackCountBindings.name(effect.arguments().get("bind"));
            }
            case "free_retreat", "ignore_colorless_attack_cost", "ignore_all_attack_cost", "retreat_cost_modifier", "attack_cost_modifier", "attack_damage_modifier", "tool_capacity", "special_energy_override", "hit_points_modifier", "prize_card_modifier" -> PassiveModifierProgram.validateModifier(effect);
            case "suppress_abilities" -> AbilitySuppressionProgram.validateShape(effect);
            case "select_pokemon" -> PokemonSelectionScope.validate(effect);
            case "with_pokemon" -> AttackPokemonBindings.validateReference(effect);
            case "coin_flip" -> {
                CoinFlipPolicy.from(effect);
                if(effect.arguments().containsKey("bind"))AttackCoinBindings.name(effect.arguments().get("bind"));
                else if (effect.effects().isEmpty()) throw new IllegalArgumentException("未命名 CoinFlipEffect 至少需要一个分支 Effect");
            }
            case "conditional" -> {
                if (effect.conditions().isEmpty()) throw new IllegalArgumentException("ConditionalEffect 至少需要一个条件");
                if (effect.effects().isEmpty()) throw new IllegalArgumentException("ConditionalEffect 至少需要一个嵌套 Effect");
            }
            case "choice" -> {
                // optional:true adds a “do nothing” option for printed “you may …” text, so one real option suffices.
                boolean optional = optionalChoice(effect);
                if (effect.effects().size() < (optional ? 1 : 2) || effect.effects().size() > 60)
                    throw new IllegalArgumentException(optional ? "可选 ChoiceEffect 需要 1 至 60 个选项" : "ChoiceEffect 需要 2 至 60 个选项");
            }
            case "repeat" -> {
                positiveAmount(effect, "count", 1, 60);
                if (effect.effects().isEmpty()) throw new IllegalArgumentException("RepeatEffect 至少需要一个嵌套 Effect");
            }
            case "repeat_per_heads" -> {
                if (effect.target() != EffectTarget.SELF || !effect.arguments().isEmpty() || effect.effects().isEmpty())
                    throw new IllegalArgumentException("repeat_per_heads 只能使用默认目标且必须包含子效果");
            }
            case "discard", "search_deck", "search_discard_pile" -> {
                if (effect.target() != EffectTarget.HAND && effect.target() != EffectTarget.DECK && effect.target() != EffectTarget.DISCARD
                    && effect.target() != EffectTarget.LOST_ZONE && effect.target() != EffectTarget.SELF)
                    throw new IllegalArgumentException(effect.type() + " 只能作用于服务器可验证的区域或自身");
                if (effect.arguments().containsKey("minimum"))
                    integer(effect.arguments().get("minimum"), effect.type() + " 的 minimum", 0,
                        integer(effect.arguments().get("amount"), effect.type() + " 的 amount", 1, 60));
            }
            case "attach_energy" -> {
                if (effect.target() != EffectTarget.ENERGY && !isPokemonTarget(effect.target()))
                    throw new IllegalArgumentException(effect.type() + " 必须指定能量或公开场上宝可梦目标");
            }
            case "evolve_from_hand" -> HandEvolutionEffect.validate(effect);
            case "attach_tool" -> AttachToolEffect.from(effect);
            case "retreat" -> RetreatEffect.validate(effect);
            case "switch_source_with_active" -> {
                if(effect.target()!=EffectTarget.SELF||!effect.arguments().keySet().stream().allMatch(Set.of("bind")::contains)||!effect.effects().isEmpty())
                    throw new IllegalArgumentException("来源换位须以SELF指向当前效果来源，且只接受计数绑定");
                if(effect.arguments().containsKey("bind"))AttackCountBindings.name(effect.arguments().get("bind"));
            }
            case "survive_attack_damage" -> SurviveAttackDamage.validate(effect);
            case "clear_status" -> ClearSpecialConditionsEffect.from(effect);
            case "discard_energy", "return_energy", "attached_energy_to_lost_zone" -> AttachedEnergyEffect.from(effect);
            case "move_energy" -> AttachedEnergyEffect.from(effect);
            case "damage_counter" -> {if(DamageCounterEffect.expandedAttack(effect)||effect.arguments().containsKey("remaining_hp")||effect.arguments().containsKey("per_opponent_prize_taken")||effect.arguments().containsKey("target_count")||effect.arguments().containsKey("from_trigger_damage")||effect.arguments().containsKey("extra_turn_on_ko")||effect.arguments().containsKey("previous_turn_attack_ids")||effect.arguments().containsKey("per_source_energy_type"))DamageCounterEffect.from(effect);}
            case "move_damage_counter" -> MoveDamageCounterEffect.from(effect);
            case "discard_tool", "return_tool" -> AttachedToolEffect.from(effect);
            case "discard_tools_and_special_energy" -> DiscardToolsAndSpecialEnergy.validate(effect);
            default -> { }
        }
        for (ConditionDefinition condition : effect.conditions()) validate(condition);
        for (EffectDefinition child : effect.effects()) validate(child);
    }

    public static void validate(ConditionDefinition condition) {
        if(condition.type()==ConditionType.HAS_ENERGY||condition.type()==ConditionType.ENERGY_COUNT)EnergyCountQuery.from(condition.arguments());
        switch (condition.type()) {
            case RETREATED_LAST_OPPONENT_TURN -> {
                if(!condition.arguments().equals(java.util.Map.of("target","OPPONENT_BENCH")))
                    throw new IllegalArgumentException("上回合撤退条件只接受本次攻击所选的对方备战目标");
            }
            case OPPONENT_USED_LIMIT_GROUP, OWN_USED_LIMIT_GROUP -> {
                if (!Set.of("target", "group").containsAll(condition.arguments().keySet())
                    || !"SELF".equals(condition.arguments().getOrDefault("target", "SELF"))
                    || !Set.of(SharedUsageLimit.GX, SharedUsageLimit.VSTAR).contains(condition.arguments().getOrDefault("group", "")))
                    throw new IllegalArgumentException("共享资格条件须指定 GX 或 VSTAR 组及 SELF");
            }
            case HAS_EXTRA_ATTACK_ENERGY -> ExtraAttackEnergyRequirement.from(condition);
            case RETREAT_COST, POKEMON_COUNT, ENERGY_COUNT, DAMAGE_COUNTER_COUNT, BENCH_COUNT, HAND_SIZE, DECK_SIZE, PRIZE_COUNT, DISCARD_COUNT, LOST_ZONE_COUNT ->
                NumericConditionPolicy.validate(condition);
            case HP_BELOW, HP_ABOVE -> integer(condition.arguments().get("value"), "条件 value", -10_000, 10_000);
            case HAS_STATUS, TARGET_HAS_STATUS -> required(condition.arguments().get("status"), "条件 status");
            case COIN_RESULT -> {
                if(condition.arguments().containsKey("tails")) {
                    if(!condition.arguments().keySet().equals(Set.of("coin","tails")))
                        throw new IllegalArgumentException("精确反面数条件须且仅须指定 coin、tails");
                    AttackCoinBindings.name(condition.arguments().get("coin"));
                    integer(condition.arguments().get("tails"),"反面数",0,CoinFlipPolicy.FINITE_LIMIT);
                    break;
                }
                String value = required(condition.arguments().get("value"), "硬币条件 value");
                if (!Set.of("HEADS","TAILS","ALL_HEADS","ALL_TAILS").contains(value.toUpperCase(java.util.Locale.ROOT)))
                    throw new IllegalArgumentException("未知硬币正反面条件");
                if(condition.arguments().containsKey("coin"))AttackCoinBindings.name(condition.arguments().get("coin"));
            }
            case BOUND_COUNT -> {
                if (!condition.arguments().keySet().containsAll(Set.of("bind", "value"))
                    || !Set.of("bind", "value", "comparison").containsAll(condition.arguments().keySet()))
                    throw new IllegalArgumentException("攻击计数条件只接受 bind、value 和可选 comparison");
                AttackCountBindings.name(condition.arguments().get("bind"));
                integer(condition.arguments().get("value"), "攻击计数条件 value", 1, 60);
                NumericConditionPolicy.comparison(condition.arguments().getOrDefault("comparison", ">="));
            }
            case AND, OR, NOT -> {
                if (!condition.arguments().isEmpty())
                    throw new IllegalArgumentException("组合条件只能包含子条件，不能悄悄忽略参数：" + condition.type());
            }
            case HAS_ATTACK -> { if(!Set.of("target","attack_id").containsAll(condition.arguments().keySet()))throw new IllegalArgumentException("招式条件仅接受目标和attack_id");AttackCountBindings.name(condition.arguments().get("attack_id")); }
            case ATTACK_DID_DAMAGE -> {
                if(!Set.of("target").containsAll(condition.arguments().keySet())||!"SELF".equals(condition.arguments().getOrDefault("target","SELF")))
                    throw new IllegalArgumentException("本次攻击伤害条件只接受SELF且无额外参数");
            }
            case POKEMON_ATTACKED_LAST_OWN_TURN, CARD_PLAYED_THIS_TURN -> TurnHistoryCondition.validate(condition);
            case NAME_EQUALS, NAME_CONTAINS -> { if(!Set.of("target","value").containsAll(condition.arguments().keySet()) || condition.arguments().getOrDefault("value", "").isBlank() || condition.arguments().get("value").length()>120)throw new IllegalArgumentException("卡名条件须声明1至120字符的value"); }
            case ALL_POKEMON_NAME_CONTAINS -> {
                if(!Set.of("target","value").containsAll(condition.arguments().keySet())
                    || !"SELF".equals(condition.arguments().getOrDefault("target","SELF"))
                    || condition.arguments().getOrDefault("value","").isBlank()
                    || condition.arguments().get("value").length()>120)
                    throw new IllegalArgumentException("全场卡名条件只接受SELF和1至120字符的value");
            }
            case KNOCKED_OUT_LAST_TURN, KNOCKED_OUT_BY_ATTACK_LAST_TURN -> {
                if(!Set.of("target","name_contains","pokemon_type").containsAll(condition.arguments().keySet())
                    ||condition.arguments().containsKey("name_contains")&&condition.arguments().containsKey("pokemon_type")
                    || condition.arguments().containsKey("name_contains")&&condition.arguments().get("name_contains").isBlank())
                    throw new IllegalArgumentException("上回合击倒条件只接受目标和非空name_contains");
                if(condition.arguments().containsKey("pokemon_type"))dev.hua.ptcglive.engine.field.EnergyType.valueOf(condition.arguments().get("pokemon_type"));
            }
            case HAS_ENERGY, IS_EVOLVED, EVOLVED_THIS_TURN, IS_BASIC, IS_EX, IS_V, IS_GX, IS_VSTAR, IS_LEGACY_EX, IS_VMAX, IS_TERA, BECAME_ACTIVE_THIS_TURN, BECAME_ACTIVE_LAST_OPPONENT_TURN, HEALED_THIS_TURN, HAS_ABILITIES, HAS_ABILITY, HAS_OTHER_SPECIAL_ENERGY, FIELDS_SHARE_POKEMON_TYPE, ALL_BENCH_DAMAGED, HAS_TOOL_DEFINITION, TOOL_ATTACHED_THIS_TURN, HAS_TOOL, HAS_RULE_BOX, IS_ACTIVE, STADIUM_IN_PLAY, TURN_STATE, CUSTOM -> { }
            case IS_STAGE -> {int stage=Integer.parseInt(condition.arguments().getOrDefault("stage","-1"));if(stage<0||stage>2)throw new IllegalArgumentException("IS_STAGE须指定0至2阶");}
            case IS_FAMILY -> dev.hua.ptcglive.rules.PokemonFamily.valueOf(required(condition.arguments().get("pokemon_family"), "宝可梦家族条件 pokemon_family"));
            case ALL_POKEMON_FAMILY -> {
                if(!Set.of("target","pokemon_family").containsAll(condition.arguments().keySet())
                    ||!"SELF".equals(condition.arguments().getOrDefault("target","SELF")))
                    throw new IllegalArgumentException("全场家族条件只接受SELF和pokemon_family");
                dev.hua.ptcglive.rules.PokemonFamily.valueOf(required(condition.arguments().get("pokemon_family"), "全场家族条件 pokemon_family"));
            }
            case IS_TYPE -> {
                try { dev.hua.ptcglive.engine.field.EnergyType.valueOf(required(condition.arguments().get("pokemon_type"), "宝可梦属性条件 pokemon_type")); }
                catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("IS_TYPE 的 pokemon_type 必须是宝可梦属性", invalid); }
            }
            case WEAK_TO_OPPONENT_ACTIVE -> {
                if(!condition.arguments().equals(java.util.Map.of("target","SELF")))throw new IllegalArgumentException("弱点对比只允许宿主SELF且无额外参数");
            }
            case HAS_RESISTANCE -> {
                if(!Set.of("target","energy_type").containsAll(condition.arguments().keySet()))
                    throw new IllegalArgumentException("HAS_RESISTANCE 只接受 target 与 energy_type");
                try { dev.hua.ptcglive.engine.field.EnergyType.valueOf(required(condition.arguments().get("energy_type"), "抗性条件 energy_type")); }
                catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("HAS_RESISTANCE 的 energy_type 必须是宝可梦属性", invalid); }
            }
        }
        for (ConditionDefinition child : condition.children()) validate(child);
    }

    /** “You may …”: the controller may also decline every option. */
    public static boolean optionalChoice(EffectDefinition effect) {
        if (!effect.type().equals("choice")) return false;
        if (!Set.of("optional","chooser","require_usable_option").containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("ChoiceEffect 包含未审核参数");
        if(effect.arguments().containsKey("chooser")&&!"OPPONENT".equals(effect.arguments().get("chooser")))
            throw new IllegalArgumentException("ChoiceEffect chooser 目前只接受 OPPONENT");
        if(effect.arguments().containsKey("require_usable_option")
            &&!"true".equals(effect.arguments().get("require_usable_option")))
            throw new IllegalArgumentException("ChoiceEffect require_usable_option 只接受 true");
        String raw = effect.arguments().getOrDefault("optional", "false");
        if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) throw new IllegalArgumentException("ChoiceEffect 的 optional 必须为 true 或 false");
        return Boolean.parseBoolean(raw);
    }
    public static boolean requireUsableChoice(EffectDefinition effect) {
        return effect.type().equals("choice") && "true".equals(effect.arguments().get("require_usable_option"));
    }
    private static boolean isPokemonTarget(EffectTarget target) {
        return target.eventBound() || target == EffectTarget.CHOSEN_POKEMON || target == EffectTarget.SELF || target == EffectTarget.OPPONENT_ACTIVE || target == EffectTarget.OWN_ACTIVE
            || target == EffectTarget.OWN_BENCH || target == EffectTarget.OPPONENT_BENCH || target == EffectTarget.ALL_OWN_POKEMON
            || target == EffectTarget.ALL_OPPONENT_POKEMON || target == EffectTarget.SELECT_OWN_POKEMON || target == EffectTarget.SELECT_OPPONENT_POKEMON
            || target == EffectTarget.SELECT_ANY_POKEMON;
    }
    private static void positiveAmount(EffectDefinition effect, String key, int min, int max) { integer(effect.arguments().get(key), effect.type() + " 的 " + key, min, max); }
    private static int integer(String value, String label, int min, int max) {
        try {
            int parsed = Integer.parseInt(required(value, label));
            if (parsed < min || parsed > max) throw new IllegalArgumentException(label + "超出范围");
            return parsed;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException(label + "不是整数", invalid); }
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "缺失");
        return value;
    }
}
