package dev.hua.ptcglive.engine.effect;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical, data-driven execution classification for the Effect registry.
 * It is used both by the server preflight and the future full-catalog scanner,
 * avoiding the unsafe assumption that a successfully parsed text is playable.
 */
public final class EffectExecutionCapabilityCatalog {
    public record Assessment(EffectExecutionCapability capability, List<String> reasons) {
        public Assessment {
            Objects.requireNonNull(capability, "Effect 执行能力");
            reasons = List.copyOf(Objects.requireNonNull(reasons, "Effect 执行说明"));
        }
        public boolean directlyExecutable() { return capability == EffectExecutionCapability.DIRECT_SERVER; }
    }

    private static final Set<String> DIRECT = Set.of("remove_attack_effects","discard_bench_until","next_turn_extra_prize_if_defender_ko","shuffle_both_active_into_deck","force_opponent_coin_tails","return_source_to_bottom", "return_source_to_hand", "shuffle_source_into_deck","discard_hand_at_turn_end","draw_until_at_turn_end","shuffle_hand_keep_at_turn_end",
        "shuffle_both_hands_bottom_draw_prizes",
        "draw", "heal", "poison", "burn", "paralyze", "sleep", "confuse", "clear_status", "survive_attack_damage", "damage_counter", "move_damage_counter",
        "knock_out", "return_to_hand", "shuffle_into_deck", "discard_pokemon", "knockout_top_to_hand", "knockout_cards_to_hand", "devolve", "flustered_leap", "switch_source_with_active", "prevent_evolution", "prevent_attack", "prevent_retreat", "prevent_retreat_poisoned", "disable_abilities", "disable_tools", "discard_stadium",
        "recover_all_discard", "discard_recover_named_supporter", "discard_top_deck", "draw_bottom", "discard_random_hand", "shuffle_random_hand", "draw_until", "sleep_recovery_two_coins_until_cleared", "extra_prize_for_trigger_ko", "prevent_prize_take", "turn_choose_next_coin","turn_gx_permission","turn_ability_suppression", "turn_supporter_limit", "turn_attack_bonus", "turn_damage_reduction", "turn_extra_prize_on_attack_ko", "discard_hand", "shuffle_hand", "hand_to_deck_bottom", "turn_prize_face_up", "unrecoverable_from_discard", "prize_pokemon_to_bench_coin_extra_prize", "win_game", "reset_prizes", "resolve_hp_guess", "resolve_trainer_recovery_bargain");
    private static final Set<String> SELECTION = Set.of("devolve_evolutions_to_deck","discard_top_attach_dragon","move_own_damage_counters","reveal_random_hand_supporter","reveal_top_attach_energy","reveal_top_take_pokemon","reveal_until_supporter","redistribute_damage_counters","reveal_hand_cards",
        "discard", "search_deck", "reorder_top_deck", "inspect_top_deck", "inspect_prize", "swap_hand_top_deck", "swap_prizes_hand", "prize_random_hand_swap", "opponent_choose_discard", "opponent_hand_basic_to_bench", "opponent_top_basic_to_bench", "opponent_choose_deck_top", "search_basic_to_bench_then_move_energy", "self_counter_choice_damage", "redistribute_opponent_damage_counters", "evolve_each_bench_from_deck", "evolve_selected_from_deck", "search_discard_pile", "attach_energy", "attach_tool", "discard_energy", "return_energy", "move_energy", "discard_tool", "return_tool",
        "switch", "force_switch", "gust", "retreat", "bench_damage", "discard_named_bench_multi_target_damage", "choice", "select_pokemon", "reveal_hand", "attached_energy_to_lost_zone",
        "discard_hand_keep", "shuffle_hand_keep", "split_basic_energy_search", "discard_reorder_top", "quiz_select_pokemon", "quiz_select_printed_height","guess_pokemon_hp", "top_distinct_category_search", "tool_or_stadium_to_lost_zone", "discard_field_tools_and_stadium");
    private static final Set<String> ATTACK_CONTEXT = Set.of("extra_turn", "per_target_coin_damage", "discard_top_attach_energy", "shuffle_revealed_hand_cards",
        "damage", "self_damage", "recoil", "damage_multiplier", "damage_per_energy", "discard_tools_and_special_energy",
        "damage_per_damage_counter", "damage_per_bench", "damage_per_prize", "damage_per_bound", "damage_per_hand_card", "damage_per_pokemon", "damage_per_tails", "damage_per_special_condition", "repeat_target_damage", "attack_cost_override", "damage_per_discard_card", "prevent_damage", "prevent_small_attack_damage", "reduce_damage", "modify_damage_taken", "ignore_resistance", "increase_attack_cost", "increase_retreat_cost", "reduce_attack_damage", "boost_attack_damage", "increase_damage_taken", "next_turn_attack_base", "attack_coin_gate", "lock_opponent_attack", "lock_this_attack", "copy_attack",
        "damage_from_last_opponent_attack", "weakness_override", "grant_star_cipher_ability", "turn_attack_lock", "turn_ignore_defender_damage_effects", "field_attack_protection", "take_prize_cards", "opponent_hand_to_bottom", "opponent_hand_to_deck", "extra_prize_on_attack_ko", "opt_in", "retaliate", "prevent_hand_energy", "prevent_trainer_play", "trainer_attempt_coin_gate", "discard_opponent_stadium_and_lock", "hand_energy_counter_trap", "hand_energy_end_turn_trap", "use_top_supporter", "no_prizes_if_ko_next_opponent_turn", "survive_next_turn_attack_damage");

    private EffectExecutionCapabilityCatalog() { }

    public static Assessment assess(EffectDefinition effect) {
        Objects.requireNonNull(effect, "Effect");
        var capabilities = EnumSet.noneOf(EffectExecutionCapability.class);
        var reasons = new ArrayList<String>();
        collect(effect, capabilities, reasons);
        EffectExecutionCapability capability = capabilities.contains(EffectExecutionCapability.CUSTOM_HANDLER)
            ? EffectExecutionCapability.CUSTOM_HANDLER
            : capabilities.contains(EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE)
                ? EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE
            : capabilities.contains(EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT)
                ? EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT
                : capabilities.contains(EffectExecutionCapability.REQUIRES_SELECTION)
                    ? EffectExecutionCapability.REQUIRES_SELECTION : EffectExecutionCapability.DIRECT_SERVER;
        return new Assessment(capability, reasons);
    }

    private static void collect(EffectDefinition effect, EnumSet<EffectExecutionCapability> capabilities, List<String> reasons) {
        String type = effect.type();
        if(type.equals(RestoredPokemonRules.BENCH)||type.equals(RestoredPokemonRules.BOTTOM)) {
            RestoredPokemonRules.validate(effect);add(capabilities,reasons,type.equals(RestoredPokemonRules.BENCH)
                ?EffectExecutionCapability.REQUIRES_SELECTION:EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"复原宝可梦的实际手牌或弃牌区实体操作");return;
        }
        if(type.equals(DistinctBenchEnergy.TYPE)){DistinctBenchEnergy.validate(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"先选备战宝可梦，再按卡文检索并分配基本能量");return;}
        if(Set.of(TopPrizeSwap.INSPECT,TopPrizeSwap.RESOLVE).contains(effect.type())){TopPrizeSwap.validate(effect);add(capabilities,reasons,effect.type().equals(TopPrizeSwap.INSPECT)?EffectExecutionCapability.REQUIRES_SELECTION:EffectExecutionCapability.DIRECT_SERVER,"私下查看牌库顶，再与暗面奖赏位置交换");return;}
        if(effect.type().equals(PrizeSourceExchange.TYPE)){PrizeSourceExchange.validate(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"私下查看暗面奖赏并用实际使用的训练家交换，保留明面奖赏");return;}
        if(EnergyDeckSwap.handles(effect)){EnergyDeckSwap.validate(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"公开选择附着能量，再私下检索基本能量，确认后同时交换");return;}
        if(type.equals("rock_paper_scissors")){EffectSchemaValidator.validate(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"双方秘密出拳，平局须开启新一轮选择");return;}
        if(DrawCount.upTo(effect)) {DrawCount.from(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"抽牌玩家自行选择零至各自剩余奖赏数");return;}
        if(type.equals("use_opponent_hand_supporter")){EffectSchemaValidator.validate(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"公开手牌后可选择支援者，沿原攻击续接其效果");return;}
        if(type.equals("move_all_bench_damage_to_active")||type.equals("redistribute_opponent_damage_counters")){EffectSchemaValidator.validate(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"分阶段选择指示物来源、数量及不同的目的地");return;}
        if(type.equals("shuffle_named_bench_then_damage")||type.equals("shuffle_unchosen_opponent_bench")){PokemonEffectSelection.validate(effect);add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"按卡面规定的选择方和数量保留备战宝可梦");return;}
        if (type.equals("discard_pokemon_to_bench_self")) {
            if (!RevivingFlameAbility.reviveStep(effect)) throw new IllegalArgumentException("弃牌区自复活规则形状无效");
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"弃牌区实体卡必须通过原特性续接上场");return;
        }
        if(HandEvolutionEffect.handles(effect)) {
            HandEvolutionEffect.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"手牌进化须由原宝可梦主动特性绑定来源及私密手牌选择");return;
        }
        if(Sv85TrainerEffects.handles(effect)) {
            Sv85TrainerEffects.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"SV8.5训练家须由服务器跨步骤选牌事务结算");return;
        }
        if (type.equals(TrainerRecoveryBargain.TYPE)) {
            TrainerRecoveryBargain.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"弃牌区训练家实体选择与对手许可须由同一服务器续接事务结算");return;
        }
        if(AttackCoinRetry.handles(effect)) {
            AttackCoinRetry.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"攻击重投须由服务器持续来源授权并等待攻击者选择");return;
        }
        if(AttackTailsDraw.handles(effect)) {
            AttackTailsDraw.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"打空保险须由所附道具读取最终攻击硬币并安排回合末抽牌");return;
        }
        if(AttackDeckWindow.handles(effect)) {
            AttackDeckWindow.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"公开计伤及整理须共享本次攻击的服务器牌库窗口");return;
        }
        if (PlannedEnergyDiscard.handles(effect)) {
            PlannedEnergyDiscard.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"选能计伤与伤害后弃置须共享本次攻击的服务器记录");return;
        }
        if(BorrowedAttackProgram.handles(type)) {
            BorrowedAttackProgram.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"借招须按实时特性授权、印刷提供者和实际使用者费用重新核验");return;
        }
        if(PokemonDiscardReplacement.handles(effect)) {
            PokemonDiscardReplacement.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"弃牌替换须由训练家续接绑定已选己方基础宝可梦和弃牌选择");return;
        }
        if(PokemonDeckReplacement.handles(effect)) {
            PokemonDeckReplacement.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"牌库替换须有攻击原来源、私密选牌及完整继承状态的服务器事务");return;
        }
        if(BasicDeckReplacement.handles(effect)) {
            BasicDeckReplacement.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"变身启动须绑定主动特性来源并由服务器完成牌库选牌及原位替换");return;
        }
        if(TypedEnergyDiscard.handles(effect)) {
            TypedEnergyDiscard.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"混合类型弃能需要服务器攻击原来源和当前供能的联合分配");return;
        }
        if(AttachEnergyEffect.coinHeads(effect)) {
            AttachEnergyEffect.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"按正面附能必须读取本次攻击已生成的硬币结果");
            return;
        }
        if(effect.arguments().containsKey("discard_top_count")) {
            ZoneSelectionEffect.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"弃顶回收需要招式结算中服务器捕获的实际弃置窗口");
            return;
        }
        if(type.equals("damage_counter")&&(effect.arguments().containsKey("extra_turn_on_ko")||effect.arguments().containsKey("counter_multiplier"))) {
            DamageCounterEffect.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"倍增指示物及额外回合需要本次攻击后续接");
        }
        if(MoveDamageCounterEffect.handles(effect)&&(effect.arguments().containsKey("source_ref")||effect.arguments().containsKey("up_to")||effect.arguments().containsKey("all")))
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"伤害指示物移动需要服务器具名来源或数量选择");
        if(AttackPokemonBindings.reference(effect)||PokemonSelectionScope.handles(effect)&&effect.arguments().containsKey("bind"))
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"命名宝可梦需要同一服务器可续接结算的入场身份绑定");
        if(AttackCountBindings.bound(effect)!=null||AttackCountBindings.reference(effect)!=null||effect.conditions().stream().anyMatch(AttackCountBindings::references))
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"攻击计数必须绑定同一次服务器攻击");
        if(type.equals("coin_flip")&&effect.arguments().containsKey("bind")
            ||effect.conditions().stream().anyMatch(AttackCoinBindings::references))
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"命名硬币必须绑定同一次服务器攻击");
        if(type.equals(AttackDamageKoProtection.TYPE)) {
            AttackDamageKoProtection.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"击倒后防护需要当前攻击伤害击倒、原来源令牌和下一对手回合边界");return;
        }
        if(type.equals("delayed_effect")) {
            DelayedEffectProgram.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"延迟程序需要服务器目标入场身份、清除标记和到期队列");
            return;
        }
        if(type.equals("discard_source")) {
            EffectSchemaValidator.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"自弃必须绑定服务器捕获的工具／场地／能量入场身份");
            return;
        }
        if(type.equals("heal")&&effect.arguments().containsKey("discard_source_if_healed")) {
            HealingEffect.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"回复后弃置工具须绑定真实附着来源与实际回复量");
            return;
        }
        if(AttackTargetFilter.handles(effect)) {
            AttackTargetFilter.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"攻击目标筛选由服务器按宣告时的公开场面校验");
            return;
        }
        if(ToolGrantedAttack.handles(effect)) {
            ToolGrantedAttack.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"道具招式须由服务器核验当前所附道具及使用者");return;
        }
        if(AbilitySuppressionProgram.handles(type)) {
            AbilitySuppressionProgram.validateShape(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"特性压制需要当前场上工具／场地／能量来源持续重算");
            return;
        }
        if(PassiveAttackGuard.handles(type)) {
            PassiveAttackGuard.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"被动防护须查询未失效的特性和实际攻击者");
            return;
        }
        if(PassiveRuleProgram.handles(type)) {
            PassiveRuleProgram.validate(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"持续规则按当前有效特性查询");
            return;
        }
        if(PassiveModifierProgram.handles(type)) {
            PassiveModifierProgram.validateModifier(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_PERSISTENT_SOURCE,"持续修正需要当前场上来源与只读查询");
            return;
        }
        if (type.equals("prevent_effects") || type.equals("prevent_damage") && effect.arguments().containsKey("source")) {
            ProtectionEffect.from(effect);
            add(capabilities,reasons,effect.target() == EffectTarget.CHOSEN_POKEMON || effect.target() == EffectTarget.SELECT_OWN_POKEMON || effect.target() == EffectTarget.SELECT_OPPONENT_POKEMON
                ? EffectExecutionCapability.REQUIRES_SELECTION : EffectExecutionCapability.DIRECT_SERVER,"已声明来源、范围和到期的服务器防护");
        } else if (DamageCounterDistribution.handles(effect)) {
            DamageCounterDistribution.from(effect);
            add(capabilities,reasons,EffectExecutionCapability.REQUIRES_SELECTION,"damage_counter 分配需要服务器预算和多目标续接");
        } else if (type.equals("custom")) add(capabilities, reasons, EffectExecutionCapability.CUSTOM_HANDLER, "CustomEffect：" + effect.customHandler());
        else if ((type.equals("discard_energy") || type.equals("return_energy")
            || type.equals("attached_energy_to_lost_zone")) && AttachedEnergyEffect.from(effect).all()) {
            add(capabilities, reasons, EffectExecutionCapability.DIRECT_SERVER,
                type + " 的 all:true 由服务器一次性结算全部当前附着能量");
        } else if (DIRECT.contains(type)) {
            if (effect.target() == EffectTarget.CHOSEN_POKEMON || effect.target() == EffectTarget.SELECT_OWN_POKEMON || effect.target() == EffectTarget.SELECT_OPPONENT_POKEMON)
                add(capabilities, reasons, EffectExecutionCapability.REQUIRES_SELECTION, type + " 需要选择公开宝可梦目标");
            else add(capabilities, reasons, EffectExecutionCapability.DIRECT_SERVER, type + " 可由服务器直接结算");
        } else if (SELECTION.contains(type)) add(capabilities, reasons, EffectExecutionCapability.REQUIRES_SELECTION, type + " 需要服务器选择事务");
        else if (ATTACK_CONTEXT.contains(type)) add(capabilities, reasons, EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,
            type + " 需要攻击伤害／防伤／击倒时序上下文");
        else if (!type.equals("conditional") && !type.equals("coin_flip") && !type.equals("repeat") && !type.equals("repeat_per_heads") && !AttackPokemonBindings.reference(effect))
            throw new IllegalArgumentException("未登记的 Effect 执行能力：" + type);
        for (EffectDefinition child : effect.effects()) collect(child, capabilities, reasons);
    }

    private static void add(EnumSet<EffectExecutionCapability> capabilities, List<String> reasons,
                            EffectExecutionCapability capability, String reason) {
        capabilities.add(capability);
        reasons.add(reason);
    }
}
