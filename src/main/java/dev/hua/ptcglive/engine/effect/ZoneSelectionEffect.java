package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.CardEffectDestination;
import dev.hua.ptcglive.rules.CardDefinition;
import java.util.Objects;
import java.util.Set;

/** One declarative filter/transfer contract shared by selected-card zone Effects. */
public record ZoneSelectionEffect(EffectDefinition definition, EffectTargetResolution.Zone source,
                                  EffectCardSelectionSpec selection, CardEffectDestination recoveryDestination) {
    public static final Set<String> FIELDS = Set.of("evolves_from_name", "one_each_card_kind", "exclude_resolution_hand_discard", "distinct_name_groups", "trainer_name_equals", "required_definition_groups", "count_bind", "discard_top_count", "amount", "minimum", "card_category", "trainer_category",
        "allow_evolution", "any_stage", "basic_pokemon_only", "basic_energy_only", "energy_type", "rule_box", "pokemon_families", "family_match", "pokemon_types", "definition_ids", "destination", "reveal", "top_count", "bottom_count", "evolution_timing",
        "card_kinds", "excluded_definition_ids", "hp_max", "retreat_cost_min", "retreat_cost_max", "battle_style", "pokemon_names", "skip_stage", "chooser", "source_owner", "remainder", "pokemon_types_from_source_basic_energy", "match_own_pokemon_names", "match_discard_pokemon_names", "match_opponent_pokemon_types",
        // Server-recorded counts include hand costs and the one-card deck-to-Bench placement.
        "bind", "bind_pokemon", "count_mode", "as_cost", "requires_effect", "exclude_activation_cost", "extra_per_discard_definition_ids", "trainer_name_contains", "resistance_type", "pokemon_or_basic_energy_type", "pokemon_name_or_basic_energy_type", "pokemon_or_stadium_type", "pokemon_name_contains", "evolves_from_name_contains", "evolution_pokemon_only", "pokemon_stage", "distinct_energy_types", "distinct_pokemon_types", "match_opponent_pokemon_names", "amount_mode", "coin", "ordered");

    /** Printed Mega-era exceptions; the server still owns the search and Bench capacity. */
    public static boolean restoredFossilPlacement(EffectDefinition effect) {
        var names=effect.arguments().get("pokemon_names");
        return names!=null&&Set.of("Lileep","Tirtouga","Archen","Aerodactyl","Omanyte","Kabuto","Anorith","Shieldon","Tyrunt","Amaura").contains(names)
            &&effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK&&effect.conditions().isEmpty()&&effect.effects().isEmpty()
            &&effect.arguments().equals(java.util.Map.of("amount","1","minimum","0","card_category","POKEMON","pokemon_names",names,"bottom_count","7","destination","BENCH"));
    }
    public static boolean reviewedMegaPlacement(EffectDefinition effect) {
        if(!effect.type().equals("search_deck") || effect.target()!=EffectTarget.DECK
            || !effect.conditions().isEmpty() || !effect.effects().isEmpty()) return false;
        var args=effect.arguments();
        var names=args.get("pokemon_names");
        if(names!=null && Set.of("Shedinja","Silcoon|Cascoon").contains(names))
            return args.equals(java.util.Map.of("amount","1","minimum","0","card_category","POKEMON",
                "pokemon_names",names,"destination","BENCH","any_stage","true"));
        return args.equals(java.util.Map.of("top_count","7","amount","1","minimum","0",
            "card_category","POKEMON","pokemon_types","DARKNESS","destination","BENCH",
            "any_stage","true","remainder","SHUFFLE_BOTTOM"));
    }

    /** Binds the exact new Bench entry only for one Basic searched from the owner's deck. */
    public static String newlyBenchedPokemonBinding(EffectDefinition effect) {
        String binding = effect.arguments().get("bind_pokemon");
        if (binding == null) return null;
        if (!effect.type().equals("search_deck") || effect.target() != EffectTarget.DECK
            || !effect.arguments().keySet().equals(Set.of("amount", "minimum", "card_category",
                "basic_pokemon_only", "destination", "bind", "bind_pokemon"))
            || !"1".equals(effect.arguments().get("amount")) || !"0".equals(effect.arguments().get("minimum"))
            || !"POKEMON".equals(effect.arguments().get("card_category"))
            || !"true".equals(effect.arguments().get("basic_pokemon_only"))
            || !"BENCH".equals(effect.arguments().get("destination")))
            throw new IllegalArgumentException("新入场宝可梦绑定只支持从牌库检索最多1只基础宝可梦放入己方备战区");
        AttackCountBindings.name(effect.arguments().get("bind"));
        return AttackPokemonBindings.name(binding);
    }

    public static boolean opponentDiscardChooser(EffectDefinition effect) {
        return "OPPONENT".equals(effect.arguments().get("chooser"));
    }
    /** Printed fixed windows whose unchosen cards retain their positions until the following reorder. */
    public static boolean retainedWindowSearch(EffectDefinition effect) {
        if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK||!effect.effects().isEmpty())return false;
        var args=new java.util.LinkedHashMap<>(effect.arguments());
        if(args.equals(java.util.Map.of("amount","1","minimum","0","top_count","3","card_kinds","POKEMON|ENERGY","reveal","true","remainder","KEEP_ORDER","bind","nav-taken")))return true;
        if(!Set.of("0","1","2").contains(args.getOrDefault("minimum","")))return false;
        args.put("minimum","2");
        return args.equals(java.util.Map.of("amount","2","minimum","2","bottom_count","4","reveal","false","remainder","KEEP_ORDER"));
    }
    private static boolean opponentHandRecovery(EffectDefinition effect) {
        if(!effect.type().equals("search_discard_pile")||effect.target()!=EffectTarget.DISCARD)return false;
        var args=new java.util.LinkedHashMap<>(effect.arguments());
        boolean ownChoice=args.remove("chooser","OPPONENT");
        if(ownChoice&&!args.remove("card_category","POKEMON"))return false;
        // The server lowers the minimum to zero when the other discard pile has no eligible card.
        String destination=args.getOrDefault("destination","");
        return Set.of("0","1").contains(args.get("minimum"))&&Set.of("HAND","DECK_BOTTOM").contains(destination)
            &&(!ownChoice||destination.equals("HAND"))
            &&args.equals(java.util.Map.of("amount","1","minimum",args.get("minimum"),"destination",destination,"source_owner","OPPONENT"));
    }
    public static boolean researchLabPlacement(EffectDefinition effect) {
        var args=new java.util.LinkedHashMap<>(effect.arguments());
        if(!Set.of("1","2").contains(args.getOrDefault("amount","")))return false;
        args.put("amount","2");
        return effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK
            &&args.equals(java.util.Map.of("amount","2","minimum","0","card_category","POKEMON",
                "evolves_from_name","Unidentified Fossil","any_stage","true","destination","BENCH"))
            &&effect.conditions().isEmpty()&&effect.effects().isEmpty();
    }
    public static boolean handles(EffectDefinition effect) {
        return Set.of("reveal_hand_cards","evolve_from_hand", "replace_pokemon_from_deck", "replace_basic_from_deck", "replace_basic_from_discard", "swap_hand_top_deck", "inspect_top_deck", "reorder_top_deck", "search_deck", "discard", "search_discard_pile", "attach_energy", "attach_tool").contains(effect.type());
    }
    /** Lugia VSTAR's printed, public discard-to-Bench VSTAR Power. */
    public static boolean summoningStar(EffectDefinition effect) {
        return effect.type().equals("search_discard_pile") && effect.target()==EffectTarget.DISCARD
            && effect.conditions().isEmpty() && effect.effects().isEmpty()
            && effect.arguments().equals(java.util.Map.of("amount","2","minimum","0",
                "card_category","POKEMON","pokemon_types","COLORLESS","rule_box","NONE",
                "destination","BENCH","any_stage","true"));
    }
    /** Rotom V: move only the chosen physical Tools from its owner's discard to the Lost Zone. */
    public static boolean scrapTools(EffectDefinition effect) {
        return effect.type().equals("search_discard_pile") && effect.target()==EffectTarget.DISCARD
            && effect.conditions().isEmpty() && effect.effects().isEmpty()
            && effect.arguments().equals(java.util.Map.of("amount","60","minimum","0",
                "card_category","TRAINER","trainer_category","TOOL",
                "destination","LOST_ZONE","bind","scrap-tools"));
    }
    /** Lysandre Prism Star: mandatory, unfiltered opponent discard banishment. */
    public static boolean opponentDiscardToLostZone(EffectDefinition effect) {
        if(!effect.type().equals("search_discard_pile")||effect.target()!=EffectTarget.DISCARD
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())return false;
        var args=effect.arguments();
        if(!"OPPONENT".equals(args.get("source_owner"))||!"LOST_ZONE".equals(args.get("destination")))return false;
        if(args.containsKey("amount_mode"))return args.equals(java.util.Map.of("amount","60","minimum","60",
            "source_owner","OPPONENT","destination","LOST_ZONE","amount_mode","OWN_FIRE_POKEMON"));
        if(!args.keySet().equals(Set.of("amount","minimum","source_owner","destination")))return false;
        try { int amount=Integer.parseInt(args.get("amount")),minimum=Integer.parseInt(args.get("minimum"));
            return amount>=1&&amount<=60&&minimum>=0&&minimum<=amount;
        } catch(NumberFormatException invalid) { return false; }
    }
    /** The server may lower only the issued maximum when fewer than two Bench slots remain. */
    public static boolean narrowedSummoningStar(EffectDefinition declared, EffectDefinition issued) {
        if(!summoningStar(declared)||!issued.type().equals(declared.type())||issued.target()!=declared.target()
            ||!issued.conditions().isEmpty()||!issued.effects().isEmpty())return false;
        String amount=issued.arguments().get("amount");
        if(!Set.of("1","2").contains(amount))return false;
        var expected=new java.util.HashMap<>(declared.arguments());expected.put("amount",amount);
        return issued.arguments().equals(expected);
    }
    /** The interpreter, not this atomic transfer, evaluates the enclosing conditions. */
    public static ZoneSelectionEffect forProgram(EffectDefinition effect) {
        return forProgram(effect, false);
    }
    /**
     * Official rule: only attacks may choose zero for an “up to” effect
     * (TPCi Rules Team: Aura Jab 2025-09-25, Photon Cord 2026-07-16). Trainer
     * and Ability text that searches for any card must take at least one card
     * when the deck is not empty; attack text may declare minimum 0.
     */
    public static boolean lancePlacement(EffectDefinition effect) {
        return effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK&&effect.arguments().equals(java.util.Map.of(
            "amount","2","minimum","0","card_category","POKEMON","pokemon_types","DRAGON","any_stage","true","destination","BENCH"));
    }
    public static boolean climacticGatePlacement(EffectDefinition effect) {
        return effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK&&effect.arguments().equals(java.util.Map.of(
            "amount","2","minimum","0","card_category","POKEMON","pokemon_families","VMAX","destination","BENCH","any_stage","true"));
    }
    public static boolean typedDiscardPlacement(EffectDefinition effect) {
        if(!effect.type().equals("search_discard_pile")||effect.target()!=EffectTarget.DISCARD)return false;
        var a=effect.arguments();String type=a.get("pokemon_types"),minimum=a.get("minimum");
        return ("WATER".equals(type)||"FIGHTING".equals(type))&&("0".equals(minimum)||"1".equals(minimum))
            &&a.equals(java.util.Map.of("amount","1","minimum",minimum,"card_category","POKEMON","pokemon_types",type,
                "destination","BENCH","any_stage","true","bind","revived"));
    }
    public static ZoneSelectionEffect forProgram(EffectDefinition effect, boolean attackText) {
        ActivationCostRules.handCost(effect);
        if(effect.arguments().containsKey("exclude_resolution_hand_discard")&&!"1".equals(effect.arguments().get("minimum")))
            throw new IllegalArgumentException("支援者回收卡文必须要求选择一张；公开弃牌为空时才由服务器降低下限");
        if (!effect.effects().isEmpty()) throw new IllegalArgumentException("区域选择不能忽略嵌套效果");
        if (!effect.arguments().containsKey("minimum"))
            throw new IllegalArgumentException("组合选牌须在审核数据中明确 minimum，不能猜测卡文的最少数量");
        var operation = from(new EffectDefinition(effect.type(), effect.target(), effect.arguments(), java.util.List.of(), java.util.List.of()));
        if(effect.arguments().containsKey("count_mode")&&!attackText&&!Set.of("ENERGY_CARDS","TEAM_AQUA_POKEMON","TEAM_MAGMA_POKEMON").contains(effect.arguments().get("count_mode")))
            throw new IllegalArgumentException("弃置基本能量种类计数只核验招式");
        if(effect.arguments().containsKey("extra_per_discard_definition_ids")&&attackText)
            throw new IllegalArgumentException("按同名弃牌增加检索数量仅核验训练家程序");

        if(PokemonDeckReplacement.handles(effect)&&!attackText&&!PokemonDeckReplacement.activeToBench(effect)&&!PokemonDeckReplacement.selectedBasic(effect))throw new IllegalArgumentException("牌库替换仅允许招式、已审核训练家或离开战斗位特性结算");
        if(operation.revivesEvolutions()&&!attackText&&!summoningStar(effect)&&!mustardPlacement(effect)&&!typedDiscardPlacement(effect))
            throw new IllegalArgumentException("直接复活进化宝可梦目前只核验招式或星耀汇聚");
        if(operation.placesAnyStage()&&!attackText&&!researchLabPlacement(effect)&&!summoningStar(effect)&&!reviewedMegaPlacement(effect)&&!smogSignals(effect)&&!mustardPlacement(effect)&&!lancePlacement(effect)&&!climacticGatePlacement(effect)&&!typedDiscardPlacement(effect))
            throw new IllegalArgumentException("任意阶段直接上场目前只核验招式或星耀汇聚");
        if(operation.discardTopCount()>0&&(!attackText||operation.selection().minimum()!=1))
            throw new IllegalArgumentException("弃顶后限窗回收必须是招式效果，并在非空窗口中选1张");
        if(AttachEnergyEffect.coinHeads(effect)&&!attackText)throw new IllegalArgumentException("按硬币附能只能引用本次攻击结果");
        var spec = operation.selection();
        boolean unrestricted = spec.category() == null && spec.trainerCategory() == null && !spec.basicPokemonOnly()
            && spec.ruleBoxFilter() == RuleBoxFilter.ANY && spec.pokemonFamilyFilter().equals(PokemonFamilyFilter.ANY)
            && spec.pokemonTypeFilter().equals(PokemonTypeFilter.ANY)
            && spec.allowedDefinitionIds().isEmpty() && operation.cardKinds().isEmpty()
            && operation.excludedDefinitionIds().isEmpty() && operation.maximumPrintedHp() == 0
            && !effect.arguments().containsKey("battle_style") && operation.pokemonNames().isEmpty();
        boolean optionalDiscard=effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK
            &&effect.arguments().equals(java.util.Map.of("amount","5","minimum","0","destination","DISCARD"));
        if (!effect.type().equals("discard") && unrestricted && spec.minimum() == 0 && !attackText && operation.topCount() == 0 && operation.bottomCount() == 0&&!optionalDiscard&&!orderedDiscardRecovery(effect))
            throw new IllegalArgumentException("训练家／特性的任意卡检索必须至少选择一张（只有招式的“最多”可以选 0 张）；没有候选时由服务器处理空区域");
        return operation;
    }

    /** A private fixed window, then shuffle the rest of that same owner's deck before replacing one card. */
    public static boolean windowToTop(EffectDefinition effect) {
        if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK)return false;
        var args=new java.util.LinkedHashMap<>(effect.arguments());boolean opponent=args.remove("source_owner","OPPONENT");
        String size=args.get("top_count"),minimum=args.get("minimum");
        return ("0".equals(minimum)||"1".equals(minimum))&&(opponent&&"3".equals(size)||"5".equals(size))
            &&args.equals(java.util.Map.of("amount","1","minimum",minimum,"top_count",size,"destination","DECK_TOP_AFTER_SHUFFLE","reveal","false"));
    }
    public static boolean opponentItemWindow(EffectDefinition effect) {
        return effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK&&effect.arguments().equals(java.util.Map.of(
            "amount","5","minimum","0","top_count","5","source_owner","OPPONENT","card_category","TRAINER","trainer_category","ITEM","destination","DISCARD","remainder","DECK_SHUFFLED"));
    }
    public static boolean orderedSearchToTop(EffectDefinition effect) {
        return effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK
            &&effect.arguments().keySet().equals(Set.of("amount","minimum","destination","reveal","ordered"))
            &&"2".equals(effect.arguments().get("amount"))
            // The server clamps a printed minimum of two to the actual short deck.
            &&Set.of("0","1","2").contains(effect.arguments().get("minimum"))
            &&"DECK_TOP_AFTER_SHUFFLE".equals(effect.arguments().get("destination"))
            &&"false".equals(effect.arguments().get("reveal"))&&"true".equals(effect.arguments().get("ordered"));
    }
    public static ZoneSelectionEffect from(EffectDefinition effect) {
        if(effect.arguments().containsKey("evolution_timing")&&(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK
            ||!"EVOLVE_SELF".equals(effect.arguments().get("destination"))||!Set.of("STRICT","ANY").contains(effect.arguments().get("evolution_timing"))
            ||effect.arguments().containsKey("skip_stage")))throw new IllegalArgumentException("检索进化时点只能附于审核过的牌库进化步骤");
        for(String field:Set.of("distinct_pokemon_types","match_opponent_pokemon_names","match_own_pokemon_names","match_discard_pokemon_names","match_opponent_pokemon_types"))if(effect.arguments().containsKey(field)
            &&(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK||!(field.equals("distinct_pokemon_types")&&effect.arguments().equals(java.util.Map.of(
                "amount","3","minimum","0","card_category","POKEMON","basic_pokemon_only","true","destination","BENCH",field,"true")))&&!effect.arguments().equals(java.util.Map.of(
                "amount",field.equals("distinct_pokemon_types")?"3":"1","minimum","0","card_category","POKEMON","reveal","true",field,"true"))))
            throw new IllegalArgumentException("不同属性/对手同名检索须为已审核的可选宝可梦牌库检索并公开所选卡");
        if(effect.arguments().containsKey("exclude_activation_cost")) {
            var recovery=new java.util.LinkedHashMap<>(effect.arguments());String minimum=recovery.remove("minimum");
            // Printed public recovery is 1..4; an exhausted continuation may lower it to zero.
            if(!effect.type().equals("search_discard_pile")||effect.target()!=EffectTarget.DISCARD
                ||minimum==null||!Set.of("0","1").contains(minimum)
                ||!recovery.equals(java.util.Map.of("amount","4","card_category","ENERGY",
                    "basic_energy_only","true","destination","HAND","exclude_activation_cost","true")))
                throw new IllegalArgumentException("成本排除只核验高級能量回收的基础能量回手步骤");
        }
        if(effect.arguments().containsKey("ordered")&&!orderedSearchToTop(effect)&&!orderedDiscardRecovery(effect))
            throw new IllegalArgumentException("有序检索只支持私密检索任意2张，洗牌后按所选顺序置顶");
        Objects.requireNonNull(effect, "区域选择效果");
        if(DistinctNameSelection.handles(effect))DistinctNameSelection.validate(effect);
        if(DistinctCardKindSelection.handles(effect))DistinctCardKindSelection.validate(effect);
        if(effect.arguments().containsKey("exclude_resolution_hand_discard")) {
            var recovery=new java.util.LinkedHashMap<>(effect.arguments());String excluded=recovery.remove("excluded_definition_ids");String minimum=recovery.remove("minimum");
            if(!effect.type().equals("search_discard_pile")||effect.target()!=EffectTarget.DISCARD||excluded==null
                ||minimum==null||!Set.of("0","1").contains(minimum)||!recovery.equals(java.util.Map.of("amount","1","card_category","TRAINER","trainer_category","SUPPORTER","destination","HAND","exclude_resolution_hand_discard","true")))
                throw new IllegalArgumentException("本次弃牌排除只核验回收一张非指定同名的支援者");
            TurnHistoryCondition.values(excluded);
        }
        if(handPokemonExchange(effect)||maintenanceShuffle(effect))return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.HAND,EffectCardSelectionSpec.from(effect),null);
        if(effect.arguments().containsKey("count_bind")) {
            var args=new java.util.LinkedHashMap<>(effect.arguments());AttackCountBindings.name(args.remove("count_bind"));
            if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK||!effect.effects().isEmpty()||!effect.conditions().isEmpty()
                ||!Set.of("1","2").contains(args.remove("amount"))||!args.equals(java.util.Map.of("minimum","0","card_category","POKEMON","reveal","true")))
                throw new IllegalArgumentException("实际回库数量检索只支持公开检索至多2张宝可梦");
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.DECK,EffectCardSelectionSpec.from(effect),null);
        }
        if(effect.type().equals("reveal_hand_cards")) {
            SelectedHandReveal.validate(effect);
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.HAND,EffectCardSelectionSpec.from(effect),null);
        }
        if (!effect.conditions().isEmpty() || !effect.effects().isEmpty())
            throw new IllegalArgumentException("区域选择原子接口不能忽略嵌套效果或条件，请由程序解释器先处理");
        if(HandEvolutionEffect.handles(effect)) {
            HandEvolutionEffect.validate(effect);
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.HAND,EffectCardSelectionSpec.from(effect),null);
        }
        if(PokemonDiscardReplacement.handles(effect)) {
            PokemonDiscardReplacement.validate(effect);
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.DISCARD,EffectCardSelectionSpec.from(effect),null);
        }
        if(PokemonDeckReplacement.handles(effect)) {
            PokemonDeckReplacement.validate(effect);
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.DECK,EffectCardSelectionSpec.from(effect),null);
        }
        if(BasicDeckReplacement.handles(effect)) {
            BasicDeckReplacement.validate(effect);
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.DECK,EffectCardSelectionSpec.from(effect),null);
        }
        if(effect.type().equals("swap_hand_top_deck")) {
            HandTopDeckSwap.validate(effect);
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.HAND,EffectCardSelectionSpec.from(effect),null);
        }
        if(effect.type().equals("inspect_top_deck")) {
            TopCardInspection.validate(effect);
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.DECK,EffectCardSelectionSpec.from(effect),null);
        }
        if(effect.type().equals("reorder_top_deck")) {
            if(effect.target()==EffectTarget.DECK&&effect.arguments().equals(java.util.Map.of("amount","4","minimum","0","top_count","4","remainder","DECK_SHUFFLED")))
                return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.DECK,EffectCardSelectionSpec.from(effect),null);
            // The server clips the mandatory count to the actual window for a short deck.
            // Commit still requires every inspected card exactly once, including when minimum is zero.
            var printedArguments=new java.util.LinkedHashMap<>(effect.arguments());
            var selection=EffectCardSelectionSpec.from(effect);
            printedArguments.put("minimum",effect.arguments().get("amount"));
            String size=effect.arguments().get("amount");
            boolean opponentWindow=Set.of("3","5").contains(size)&&effect.target()==EffectTarget.OPPONENT_ACTIVE&&printedArguments.equals(java.util.Map.of(
                "amount",size,"minimum",size,"top_count",size,"source_owner","OPPONENT"));
            boolean ownWindow=Set.of("3","4","5").contains(size)&&effect.target()==EffectTarget.DECK&&printedArguments.equals(java.util.Map.of(
                "amount",size,"minimum",size,"top_count",size));
            ownWindow|="2".equals(size)&&effect.target()==EffectTarget.DECK&&printedArguments.equals(java.util.Map.of("amount","2","minimum","2","top_count","2"));
            boolean bottomToTop=effect.target()==EffectTarget.DECK&&printedArguments.equals(java.util.Map.of("amount","3","minimum","3","bottom_count","3"));
            boolean bottomOrder=effect.target()==EffectTarget.DECK&&printedArguments.equals(java.util.Map.of("amount","2","minimum","2","bottom_count","2","destination","DECK_BOTTOM"));
            if(!opponentWindow&&!ownWindow&&!bottomToTop&&!bottomOrder)
                throw new IllegalArgumentException("重排牌库顶只支持已审核的己方顶3或4张、对手顶3或5张");
            return new ZoneSelectionEffect(effect,EffectTargetResolution.Zone.DECK,selection,null);
        }
        if (effect.type().equals("attach_energy")) {
            var energy = AttachEnergyEffect.from(effect);
            return new ZoneSelectionEffect(effect, energy.source(), new EffectCardSelectionSpec(energy.policy().minCards(),
                energy.policy().maxCards(), dev.hua.ptcglive.rules.CardCategory.ENERGY, null, false, energy.policy().allowedDefinitionIds()), null);
        }
        if (effect.type().equals("attach_tool")) {
            var tool = AttachToolEffect.from(effect);
            return new ZoneSelectionEffect(effect, tool.source(), new EffectCardSelectionSpec(tool.minimum(), 1,
                dev.hua.ptcglive.rules.CardCategory.TRAINER, dev.hua.ptcglive.rules.TrainerCategory.TOOL, false,
                tool.allowedDefinitionIds()), null);
        }
        if(ItemPokemonDeckSearch.handles(effect)) ItemPokemonDeckSearch.validate(effect);
        if(effect.arguments().containsKey("trainer_name_equals")&&!ItemPokemonDeckSearch.handles(effect))
            throw new IllegalArgumentException("训练家精确名称检索只核验稀有化石直接上场");
        for (String field : effect.arguments().keySet()) if (!FIELDS.contains(field))
            throw new IllegalArgumentException("未支持的区域选择参数：" + field);
        newlyBenchedPokemonBinding(effect);
        if(effect.arguments().containsKey("required_definition_groups"))ActivationCostRules.handCost(effect);
        if(effect.arguments().containsKey("count_mode")) {
            var args=new java.util.LinkedHashMap<>(effect.arguments());AttackCountBindings.name(args.remove("bind"));
            if(!effect.type().equals("discard")||effect.target()!=EffectTarget.HAND
                ||!(args.equals(java.util.Map.of("amount","60","minimum","0","card_category","ENERGY","basic_energy_only","true","count_mode","DISTINCT_TYPES"))
                    ||Set.of("ENERGY_CARDS","TEAM_AQUA_POKEMON","TEAM_MAGMA_POKEMON").contains(args.getOrDefault("count_mode",""))
                        &&args.equals(java.util.Map.of("amount","1","minimum","1","as_cost","true","count_mode",args.get("count_mode")))))
                throw new IllegalArgumentException("弃牌种类计数须为手牌任意张基本能量");
        }
        if(effect.arguments().containsKey("extra_per_discard_definition_ids")) {
            var args=new java.util.LinkedHashMap<>(effect.arguments());
            TurnHistoryCondition.values(args.remove("extra_per_discard_definition_ids"));
            if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK
                ||!args.equals(java.util.Map.of("amount","1","minimum","0","card_category","POKEMON","rule_box","NONE","reveal","true")))
                throw new IllegalArgumentException("同名弃牌追加检索须为展示非规则盒宝可梦、基础上限一张的牌库检索");
        }
        if(effect.arguments().containsKey("battle_style"))dev.hua.ptcglive.rules.BattleStyle.valueOf(effect.arguments().get("battle_style"));
        if(effect.arguments().containsKey("pokemon_name_contains")&&!effect.arguments().containsKey("pokemon_name_or_basic_energy_type")
            &&(effect.arguments().get("pokemon_name_contains").isBlank()
                ||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON))
            throw new IllegalArgumentException("宝可梦名称片段筛选须限定宝可梦类别且非空");
        if(effect.arguments().containsKey("pokemon_types_from_source_basic_energy")
            &&(!"true".equals(effect.arguments().get("pokemon_types_from_source_basic_energy"))
                ||!effect.type().equals("search_deck")||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON))
            throw new IllegalArgumentException("按来源基础能量属性检索须为牌库宝可梦检索");
        if(effect.arguments().containsKey("trainer_name_contains")&&!ItemPokemonDeckSearch.handles(effect)) {
            String value=effect.arguments().get("trainer_name_contains");
            var spec=EffectCardSelectionSpec.from(effect);
            if(value==null||value.isBlank()||!value.equals(value.trim())||value.length()>64
                ||spec.category()!=dev.hua.ptcglive.rules.CardCategory.TRAINER)
                throw new IllegalArgumentException("训练家名称片段筛选须限定TRAINER类别且非空");
        }
        if(effect.arguments().containsKey("evolution_pokemon_only")
            &&(!"true".equals(effect.arguments().get("evolution_pokemon_only"))
                ||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON))
            throw new IllegalArgumentException("进化宝可梦筛选须明确限定POKEMON类别且值为true");
        if(effect.arguments().containsKey("pokemon_stage")) {
            int stage;
            try {stage=Integer.parseInt(effect.arguments().get("pokemon_stage"));}
            catch(NumberFormatException invalid){throw new IllegalArgumentException("牌库宝可梦阶段须为0至2",invalid);}
            if(stage<0||stage>2||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON)
                throw new IllegalArgumentException("牌库宝可梦阶段筛选须限定POKEMON，且阶段为0至2");
        }
        if(effect.arguments().containsKey("distinct_energy_types")&&(!"true".equals(effect.arguments().get("distinct_energy_types"))
            ||!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK
            ||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.ENERGY
            ||!EffectCardSelectionSpec.from(effect).basicPokemonOnly()&&!"true".equals(effect.arguments().get("basic_energy_only"))))
            throw new IllegalArgumentException("不同属性基本能量检索须限定牌库基本能量");
        if(effect.arguments().containsKey("resistance_type")) {
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("resistance_type"));
            if(!effect.type().equals("search_deck")||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON)
                throw new IllegalArgumentException("抗性筛选须用于牌库宝可梦检索");
        }
        if(effect.arguments().containsKey("pokemon_or_basic_energy_type")) {
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("pokemon_or_basic_energy_type"));
            if(!Set.of("search_deck","search_discard_pile").contains(effect.type())
                ||!values(effect.arguments().get("card_kinds")).equals(Set.of("POKEMON","BASIC_ENERGY")))
                throw new IllegalArgumentException("混合属性检索须限定牌库或弃牌区宝可梦与基础能量的并集");
        }
        if(effect.arguments().containsKey("pokemon_name_or_basic_energy_type")) {
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("pokemon_name_or_basic_energy_type"));
            if(!effect.type().equals("search_deck")
                ||!values(effect.arguments().get("card_kinds")).equals(Set.of("POKEMON","BASIC_ENERGY"))
                ||effect.arguments().getOrDefault("pokemon_name_contains","").isBlank())
                throw new IllegalArgumentException("冠名宝可梦与基础能量混合检索须明确牌库、名称、属性及两类卡牌");
        }
        if(effect.arguments().containsKey("evolves_from_name")&&!researchLabPlacement(effect))
            throw new IllegalArgumentException("研究所须精确检索不明化石的直接进化卡，最多2只放备战区");
        if(effect.arguments().containsKey("evolves_from_name_contains")
            &&!(effect.type().equals("search_deck") && effect.target()==EffectTarget.DECK
                &&Set.of("1","2").contains(effect.arguments().get("amount"))
                &&effect.arguments().entrySet().stream().filter(entry->!entry.getKey().equals("amount"))
                    .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,java.util.Map.Entry::getValue))
                    .equals(java.util.Map.of("minimum","0","card_category","POKEMON",
                        "evolves_from_name_contains","Fossil","any_stage","true","destination","BENCH"))))
            throw new IllegalArgumentException("化石进化检索只允许招式从牌库将最多2只指定进化来源宝可梦直接放备战区");
        if(effect.arguments().containsKey("pokemon_or_stadium_type")) {
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("pokemon_or_stadium_type"));
            if(!effect.type().equals("search_deck")
                ||!values(effect.arguments().get("card_kinds")).equals(Set.of("POKEMON","STADIUM")))
                throw new IllegalArgumentException("宝可梦与场地混合检索须限定牌库、宝可梦与场地的并集");
        }
        if(effect.arguments().containsKey("pokemon_names")&&(values(effect.arguments().get("pokemon_names")).isEmpty()
            ||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON))
            throw new IllegalArgumentException("指定宝可梦名称须为非空集合并限定POKEMON类别");
        if (effect.arguments().containsKey("card_kinds")) {
            var kinds = values(effect.arguments().get("card_kinds"));
            if (!Set.of("POKEMON", "NON_RULEBOX_POKEMON", "ENERGY", "BASIC_ENERGY", "SPECIAL_ENERGY", "ITEM", "SUPPORTER", "TOOL", "STADIUM").containsAll(kinds)
                || effect.arguments().containsKey("card_category") || effect.arguments().containsKey("trainer_category"))
                throw new IllegalArgumentException("card_kinds 使用宝可梦、能量或训练家子类的并集，不能与类别参数混用");
        }
        if(effect.arguments().containsKey("amount_mode")) {
            if ("OWN_BENCH_SIZE".equals(effect.arguments().get("amount_mode"))) {
                if (!effect.type().equals("search_deck") || effect.target()!=EffectTarget.DECK
                    || !effect.arguments().equals(java.util.Map.of("amount","60","minimum","0","amount_mode","OWN_BENCH_SIZE")))
                    throw new IllegalArgumentException("按己方备战数量检索须为最多数量的任意牌库检索");
            } else if ("OWN_FIELD_TYPES".equals(effect.arguments().get("amount_mode"))) {
                if (!effect.type().equals("search_deck") || effect.target()!=EffectTarget.DECK
                    || !effect.arguments().equals(java.util.Map.of("amount","60","minimum","0","amount_mode","OWN_FIELD_TYPES")))
                    throw new IllegalArgumentException("按己方场上属性种类检索须为最多数量的任意牌库检索");
            } else if("OWN_FIRE_POKEMON".equals(effect.arguments().get("amount_mode"))) {
                if(!opponentDiscardToLostZone(effect))throw new IllegalArgumentException("Fire count requires mandatory opponent discard banishment");
            } else {
            if(!Set.of("search_deck","search_discard_pile").contains(effect.type())||!"COIN_HEADS".equals(effect.arguments().get("amount_mode"))
                ||!effect.arguments().containsKey("coin")||!"60".equals(effect.arguments().get("amount"))
                ||!"0".equals(effect.arguments().get("minimum")))
                throw new IllegalArgumentException("动态检索数量仅支持按本次攻击正面数从牌库或弃牌区选取最多60张牌");
            AttackCoinBindings.name(effect.arguments().get("coin"));
            }
        } else if(effect.arguments().containsKey("coin"))throw new IllegalArgumentException("检索硬币引用必须配合COIN_HEADS");
        if (effect.arguments().containsKey("excluded_definition_ids")) values(effect.arguments().get("excluded_definition_ids"));
        if(effect.arguments().containsKey("retreat_cost_min")) {
            int minimum=Integer.parseInt(effect.arguments().get("retreat_cost_min"));
            if(minimum<0||minimum>4||!effect.type().equals("search_deck")
                ||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON)
                throw new IllegalArgumentException("印刷撤退费用筛选仅用于牌库宝可梦检索，下限为0至4");
        }
        if(effect.arguments().containsKey("retreat_cost_max")) {
            int maximum=Integer.parseInt(effect.arguments().get("retreat_cost_max"));
            if(maximum<0||maximum>4||!effect.type().equals("search_deck")
                ||EffectCardSelectionSpec.from(effect).category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON
                ||Integer.parseInt(effect.arguments().getOrDefault("retreat_cost_min","0"))>maximum)
                throw new IllegalArgumentException("印刷撤退费用上限须为0至4，并且不低于下限");
        }
        if (effect.arguments().containsKey("hp_max")) {
            int hp;
            try { hp = Integer.parseInt(effect.arguments().get("hp_max")); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("hp_max 必须是整数", invalid); }
            if (hp < 10 || hp > 1000 || EffectCardSelectionSpec.from(effect).category() != dev.hua.ptcglive.rules.CardCategory.POKEMON)
                throw new IllegalArgumentException("HP 筛选必须限定 POKEMON，且上限为 10 至 1000");
        }
        if(effect.arguments().containsKey("discard_top_count")) {
            if(!effect.type().equals("search_discard_pile")||effect.target()!=EffectTarget.DISCARD
                ||!effect.arguments().keySet().equals(Set.of("discard_top_count","amount","minimum","destination","reveal"))
                ||!"3".equals(effect.arguments().get("discard_top_count"))||!"1".equals(effect.arguments().get("amount"))
                ||!Set.of("0","1").contains(effect.arguments().get("minimum"))
                ||!"HAND".equals(effect.arguments().get("destination"))||!"true".equals(effect.arguments().get("reveal")))
                throw new IllegalArgumentException("弃顶回收只核验先弃己方顶3张，再从实际弃置窗口展示取回1张");
        }
        if (effect.arguments().containsKey("top_count")) {
            int count;
            try { count = Integer.parseInt(effect.arguments().get("top_count")); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("top_count 必须是整数", invalid); }
            if (!effect.type().equals("search_deck") || effect.target() != EffectTarget.DECK
                || count < 1 || count > 60 || effect.arguments().containsKey("destination")
                    && !Set.of("BENCH","DECK_BOTTOM_SHUFFLED","DISCARD").contains(effect.arguments().get("destination"))&&!windowToTop(effect))
                throw new IllegalArgumentException("top_count 仅用于查看牌库顶 1 至 60 张后选择加入手牌或放入备战区并洗牌");
            var spec = EffectCardSelectionSpec.from(effect);
            if ((spec.minimum() != 0 || spec.maximum() > count)&&!fixedWindowHandChoice(effect)&&!wholeWindowToBottom(effect)&&!windowToTop(effect))
                if(!"DECK_BOTTOM".equals(effect.arguments().get("remainder"))&&!"LOST_ZONE".equals(effect.arguments().get("remainder"))
                    ||!((spec.minimum()==1&&spec.maximum()==1&&count==2)||topFiveThreeToLostZone(effect)))
                    throw new IllegalArgumentException("牌库顶查看选择必须允许选 0 张，且上限不能超过查看张数；顶2选1放底除外");
        }
        if (effect.arguments().containsKey("bottom_count")) {
            int count;
            try { count = Integer.parseInt(effect.arguments().get("bottom_count")); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("bottom_count 必须是整数", invalid); }
            var spec = EffectCardSelectionSpec.from(effect);
            boolean diggingDuo=Set.of("3","8").contains(effect.arguments().get("bottom_count"))
                &&effect.arguments().equals(java.util.Map.of("amount","1","minimum",effect.arguments().get("minimum"),"bottom_count",effect.arguments().get("bottom_count")))
                &&Set.of("0","1").contains(effect.arguments().get("minimum"));
            if (!effect.type().equals("search_deck") || effect.target() != EffectTarget.DECK || count < 1 || count > 60
                || !diggingDuo&&!retainedWindowSearch(effect)&&(
                   !(spec.minimum()==0||spec.minimum()==1&&spec.maximum()==1) || spec.maximum()>count || spec.category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON
                || !Boolean.parseBoolean(effect.arguments().getOrDefault("reveal","false"))&&!"BENCH".equals(effect.arguments().get("destination"))))
                throw new IllegalArgumentException("牌库底查看只核验查看底部1至60张、可展示最多相同张数宝可梦加入手牌");
        }
        if(effect.arguments().containsKey("top_count")&&effect.arguments().containsKey("bottom_count"))
            throw new IllegalArgumentException("同一牌库窗口不能同时从顶部和底部读取");
        if(effect.arguments().containsKey("remainder")&&!effect.type().equals("attach_energy")) {
            String remainder=effect.arguments().get("remainder");
            boolean topTwo="DECK_BOTTOM".equals(remainder)&&effect.type().equals("search_deck")
                &&effect.target()==EffectTarget.DECK&&"2".equals(effect.arguments().get("top_count"))
                &&"1".equals(effect.arguments().get("amount"))&&"1".equals(effect.arguments().get("minimum"));
            boolean topTwoLost="LOST_ZONE".equals(remainder)&&effect.type().equals("search_deck")
                &&effect.target()==EffectTarget.DECK&&"2".equals(effect.arguments().get("top_count"))
                &&"1".equals(effect.arguments().get("amount"))&&"1".equals(effect.arguments().get("minimum"));
            boolean topFiveLost=topFiveThreeToLostZone(effect);
            boolean shuffledDeck="DECK_SHUFFLED".equals(remainder)&&effect.type().equals("search_deck")
                &&effect.target()==EffectTarget.DECK&&effect.arguments().containsKey("top_count")
                &&"0".equals(effect.arguments().get("minimum"))&&!effect.arguments().containsKey("destination");
            boolean shuffledBottom="SHUFFLE_BOTTOM".equals(remainder)&&effect.type().equals("search_deck")
                &&effect.target()==EffectTarget.DECK&&effect.arguments().containsKey("top_count")
                &&"1".equals(effect.arguments().get("amount"))&&"0".equals(effect.arguments().get("minimum"))
                &&"BENCH".equals(effect.arguments().get("destination"));
            if(!topTwo&&!topTwoLost&&!topFiveLost&&!shuffledBottom&&!shuffledDeck&&!fixedWindowHandChoice(effect)&&!opponentItemWindow(effect)&&!retainedWindowSearch(effect))
                throw new IllegalArgumentException("牌库窗口剩余牌去向只核验顶2选1放底／放逐、顶5选3放逐，或顶牌窗口洗匀后放牌库底");
        }
        if (effect.arguments().containsKey("bind")) {
            boolean topDiscard=effect.type().equals("search_deck")&&"DISCARD".equals(effect.arguments().get("destination"))
                &&effect.arguments().containsKey("top_count");
            boolean newlyBenched=newlyBenchedPokemonBinding(effect)!=null;
            if (!effect.type().equals("discard")&&!topDiscard&&!newlyBenched&&!scrapTools(effect)&&!boundRecovery(effect)&&!mustardPlacement(effect)&&!typedDiscardPlacement(effect)&&!retainedWindowSearch(effect))
                throw new IllegalArgumentException("只有弃置手牌、牌库顶选择弃置或新入场宝可梦可以记录攻击计数 bind");
            AttackCountBindings.name(effect.arguments().get("bind"));
        }
        EffectTargetResolution.Zone source = switch (effect.type()) {
            case "search_deck" -> EffectTargetResolution.Zone.DECK;
            case "discard" -> EffectTargetResolution.Zone.HAND;
            case "search_discard_pile" -> EffectTargetResolution.Zone.DISCARD;
            default -> throw new IllegalArgumentException("未支持的区域选择效果：" + effect.type());
        };
        if (!effect.target().name().equals(source.name()))
            throw new IllegalArgumentException("效果类型与来源区域不一致");
        if (effect.arguments().containsKey("reveal")) {
            String value = effect.arguments().get("reveal");
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("reveal 必须为 true 或 false，不能猜测卡文是否展示");
            if (Boolean.parseBoolean(value) && source == EffectTargetResolution.Zone.HAND)
                throw new IllegalArgumentException("此展示步骤只用于检索；手牌展示／弃置需独立卡文语义");
        }
        if (effect.arguments().containsKey("basic_energy_only")) {
            // “basic Energy cards”: only Basic Energy cards qualify; Special Energy never does.
            String value = effect.arguments().get("basic_energy_only");
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("basic_energy_only 必须为 true 或 false");
            if (Boolean.parseBoolean(value) && EffectCardSelectionSpec.from(effect).category() != dev.hua.ptcglive.rules.CardCategory.ENERGY)
                throw new IllegalArgumentException("基本能量筛选必须明确限定 ENERGY 类别");
        }
        if (effect.arguments().containsKey("energy_type")) {
            // “{R} Energy cards” in a deck or discard pile are Basic Fire Energy cards: a Special Energy has no type there.
            if (!Boolean.parseBoolean(effect.arguments().getOrDefault("basic_energy_only", "false")))
                throw new IllegalArgumentException("按属性选择能量卡必须同时限定 basic_energy_only");
            try { if(!"FIRE|FIGHTING".equals(effect.arguments().get("energy_type")))dev.hua.ptcglive.engine.field.EnergyType.valueOf(effect.arguments().get("energy_type")); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("energy_type 必须是能量属性", invalid); }
        }
        CardEffectDestination destination = null;
        if(effect.arguments().containsKey("any_stage")&&(!"true".equals(effect.arguments().get("any_stage"))
            ||!Set.of(EffectTargetResolution.Zone.DECK,EffectTargetResolution.Zone.DISCARD).contains(source)||!"BENCH".equals(effect.arguments().get("destination"))
            ||!"POKEMON".equals(effect.arguments().get("card_category"))
            ||effect.arguments().containsKey("basic_pokemon_only")||effect.arguments().containsKey("allow_evolution")))
            throw new IllegalArgumentException("任意阶段上场须明确己方牌库或弃牌区宝可梦到备战区，不能混用基础限定或allow_evolution");
        if(effect.arguments().containsKey("allow_evolution")&&(!"true".equals(effect.arguments().get("allow_evolution"))
            ||source!=EffectTargetResolution.Zone.DISCARD||!"BENCH".equals(effect.arguments().get("destination"))
            ||effect.arguments().containsKey("source_owner")||!"DRAGON".equals(effect.arguments().get("pokemon_types"))
            ||!"POKEMON".equals(effect.arguments().get("card_category"))||effect.arguments().containsKey("basic_pokemon_only")))
            throw new IllegalArgumentException("跨阶段复活须明确己方弃牌区龙宝可梦到备战区，不能混用基础限定");
        if(effect.arguments().containsKey("chooser")&&!opponentHandRecovery(effect)&&(!opponentDiscardChooser(effect)||!effect.type().equals("search_discard_pile")
            ||!Set.of("0","1").contains(effect.arguments().get("minimum"))
            ||!effect.arguments().equals(java.util.Map.of("amount","1","minimum",effect.arguments().get("minimum"),"card_category","POKEMON","basic_pokemon_only","true","destination","BENCH","source_owner","OPPONENT","chooser","OPPONENT"))))
            throw new IllegalArgumentException("对手自主弃牌上场须恰好1只基础宝可梦");
        if(effect.arguments().containsKey("source_owner")) {
            boolean opponentBench="OPPONENT".equals(effect.arguments().get("source_owner"))&&source==EffectTargetResolution.Zone.DISCARD
                &&"BENCH".equals(effect.arguments().get("destination"));
            boolean opponentSupporterToHand="OPPONENT".equals(effect.arguments().get("source_owner"))&&source==EffectTargetResolution.Zone.DISCARD
                &&"HAND".equals(effect.arguments().get("destination"))&&"TRAINER".equals(effect.arguments().get("card_category"))
                &&"SUPPORTER".equals(effect.arguments().get("trainer_category"));
            boolean opponentSupporterEffect="OPPONENT".equals(effect.arguments().get("source_owner"))&&source==EffectTargetResolution.Zone.DISCARD
                &&"USE_SUPPORTER_EFFECT".equals(effect.arguments().get("destination"))&&"TRAINER".equals(effect.arguments().get("card_category"))
                &&"SUPPORTER".equals(effect.arguments().get("trainer_category"));
            if(!opponentBench&&!opponentSupporterToHand&&!opponentSupporterEffect&&!opponentHandRecovery(effect)&&!windowToTop(effect)&&!opponentItemWindow(effect)&&!opponentDiscardToLostZone(effect))
                throw new IllegalArgumentException("对手区域选牌只接受弃牌区基础宝可梦上场，或对手弃牌区支援者回到对手手牌");
        }
        if(effect.arguments().containsKey("skip_stage") && (!"1".equals(effect.arguments().get("skip_stage"))
            || source!=EffectTargetResolution.Zone.DECK || !"EVOLVE_SELF".equals(effect.arguments().get("destination"))
            || values(effect.arguments().get("pokemon_names")).isEmpty()))
            throw new IllegalArgumentException("跳阶进化必须明确指定牌库进化目的地、宝可梦名称和恰好跳过1阶");
        if (source == EffectTargetResolution.Zone.DISCARD) {
            String value = effect.arguments().get("destination");
            if (value == null) throw new IllegalArgumentException("弃牌检索必须明确指定目的地");
            if(value.equals("BENCH")) {
                var spec=EffectCardSelectionSpec.from(effect);
                if(spec.category()!=dev.hua.ptcglive.rules.CardCategory.POKEMON||!spec.basicPokemonOnly()
                    &&!"true".equals(effect.arguments().get("allow_evolution"))&&!"true".equals(effect.arguments().get("any_stage"))
                    ||Boolean.parseBoolean(effect.arguments().getOrDefault("reveal","false")))
                    throw new IllegalArgumentException("弃牌上场必须限定基础宝可梦，直接公开上场无需展示确认");
            } else if(value.equals("LOST_ZONE")) {
                if(!scrapTools(effect)&&!opponentDiscardToLostZone(effect))throw new IllegalArgumentException("弃牌放逐仅核验洛托姆V的工具选择和本次攻击计数");
            } else if(!value.equals("USE_SUPPORTER_EFFECT")) try { destination = CardEffectDestination.valueOf(value); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("未支持的弃牌检索目的地：" + value, invalid); }
        } else if(source==EffectTargetResolution.Zone.DECK&&"DISCARD".equals(effect.arguments().get("destination"))) {
            var spec=EffectCardSelectionSpec.from(effect);
            int top=Integer.parseInt(effect.arguments().getOrDefault("top_count","0"));
            boolean topWindow=top>=1&&spec.minimum()==0&&spec.maximum()==top&&effect.arguments().containsKey("bind");
            boolean wholeDeck=top==0&&(spec.maximum()==2&&spec.minimum()<=1||spec.maximum()==3&&spec.minimum()==1||spec.maximum()==5&&spec.minimum()==0)
                &&effect.arguments().keySet().equals(Set.of("amount","minimum","destination"));
            boolean basicFighting=effect.arguments().equals(java.util.Map.of("amount","3","minimum","0","destination","DISCARD",
                "card_category","ENERGY","basic_energy_only","true","energy_type","FIGHTING"));
            if(!topWindow&&!wholeDeck&&!basicFighting&&!opponentItemWindow(effect))
                throw new IllegalArgumentException("弃置牌库检索只支持已审核的完整牌库最多2或5张，或牌库顶窗口0至全部并记录数量");
        } else if (source == EffectTargetResolution.Zone.DECK && "DECK_BOTTOM_SHUFFLED".equals(effect.arguments().get("destination"))) {
            if(!wholeWindowToBottom(effect))
                throw new IllegalArgumentException("牌库窗口洗匀置底只核验查看己方牌库顶3张后全部洗匀置底");
        } else if (source == EffectTargetResolution.Zone.DECK && "DECK_TOP_AFTER_SHUFFLE".equals(effect.arguments().get("destination"))) {
            if (Boolean.parseBoolean(effect.arguments().getOrDefault("reveal", "false"))) {
                if (EffectCardSelectionSpec.from(effect).maximum() != 1)
                    throw new IllegalArgumentException("检索后置顶须最多1张并明确展示，先洗其余牌库再放回顶端");
            } else if (!windowToTop(effect)&&!orderedSearchToTop(effect)&&(!effect.arguments().keySet().equals(Set.of("amount", "minimum", "destination", "reveal"))
                || !"1".equals(effect.arguments().get("amount")) || !"1".equals(effect.arguments().get("minimum"))
                || !"false".equals(effect.arguments().get("reveal")))) {
                throw new IllegalArgumentException("私密检索置顶只核验必选任意1张，不展示；先洗其余牌库再放回顶端");
            }
        } else if (source == EffectTargetResolution.Zone.DECK && "EVOLVE_SELF".equals(effect.arguments().get("destination"))) {
            // “Search your deck for a card that evolves from this Pokémon and put it onto this Pokémon to evolve it.”
            // Which card evolves from “this Pokémon” is the server's evolution check, never a data filter.
            if (!Set.of("amount", "minimum", "card_category", "destination", "pokemon_names", "skip_stage", "evolution_timing", "pokemon_families").containsAll(effect.arguments().keySet()))
                throw new IllegalArgumentException("检索进化只接受张数、宝可梦类别及名称、目的地和跳阶参数，进化来源由服务器按卡名判断");
            if(effect.arguments().containsKey("pokemon_families")&&(!"GX".equals(effect.arguments().get("pokemon_families"))
                ||!"STRICT".equals(effect.arguments().get("evolution_timing"))||effect.arguments().size()!=6))
                throw new IllegalArgumentException("GX检索进化须遵循通常时点且不得跳阶");
            var spec = EffectCardSelectionSpec.from(effect);
            if (spec.maximum() != 1 || spec.category() != dev.hua.ptcglive.rules.CardCategory.POKEMON)
                throw new IllegalArgumentException("检索进化必须是 1 张宝可梦卡（amount 1、card_category POKEMON）");
            if (spec.minimum() != 0)
                throw new IllegalArgumentException("检索特定条件的卡可以找不到，检索进化的 minimum 必须为 0");
        } else if (source == EffectTargetResolution.Zone.DECK && ItemPokemonDeckSearch.handles(effect)) {
            ItemPokemonDeckSearch.validate(effect);
        } else if (source == EffectTargetResolution.Zone.DECK && "HAND".equals(effect.arguments().get("destination"))) {
            // Explicit HAND is identical to the normal deck search destination.
            destination = CardEffectDestination.HAND;
        } else if (source == EffectTargetResolution.Zone.DECK && effect.arguments().containsKey("destination")) {
            // “Search your deck for (up to N) Basic Pokémon and put them onto your Bench.”
            if (!"BENCH".equals(effect.arguments().get("destination")))
                throw new IllegalArgumentException("未支持的牌库检索目的地");
            var spec = EffectCardSelectionSpec.from(effect);
            if (spec.category() != dev.hua.ptcglive.rules.CardCategory.POKEMON
                || !spec.basicPokemonOnly() && values(effect.arguments().get("pokemon_names")).isEmpty()
                    && !"true".equals(effect.arguments().get("any_stage")))
                throw new IllegalArgumentException("从牌库放到备战区须明确基础、指定名称或任意阶段宝可梦");
            if (Boolean.parseBoolean(effect.arguments().getOrDefault("reveal", "false")))
                throw new IllegalArgumentException("放到备战区的宝可梦本来就会公开，不需要 reveal 步骤");
        } else if(source==EffectTargetResolution.Zone.HAND&&effect.arguments().containsKey("destination")
            &&Set.of("DECK_BOTTOM","DECK_TOP","LOST_ZONE").contains(effect.arguments().get("destination"))) {
            if(!effect.type().equals("discard")||EffectCardSelectionSpec.from(effect).minimum()<1&&!caitlinHandBottom(effect))
                throw new IllegalArgumentException("选择手牌置于牌库须明确至少选择1张");
        } else if (effect.arguments().containsKey("destination")) {
            throw new IllegalArgumentException("该效果的目的地固定，不能被参数替换");
        }
        return new ZoneSelectionEffect(effect, source, EffectCardSelectionSpec.from(effect), destination);
    }
    public boolean revealsCards() { return Boolean.parseBoolean(definition.arguments().getOrDefault("reveal", "false")); }
    public static boolean maintenanceShuffle(EffectDefinition effect) {
        return effect.type().equals("discard")&&effect.target()==EffectTarget.HAND&&effect.conditions().isEmpty()&&effect.effects().isEmpty()
            &&Set.of("1","2").contains(effect.arguments().get("amount"))
            &&effect.arguments().equals(java.util.Map.of("amount",effect.arguments().get("amount"),"minimum",effect.arguments().get("amount"),"as_cost","true","destination","DECK_SHUFFLED"));
    }
    public static boolean handPokemonExchange(EffectDefinition effect) {
        if(!effect.type().equals("discard")||effect.target()!=EffectTarget.HAND||!effect.effects().isEmpty()||!effect.conditions().isEmpty())return false;
        var args=new java.util.LinkedHashMap<>(effect.arguments());String bind=args.remove("bind");if(bind==null)return false;
        AttackCountBindings.name(bind);
        return args.equals(java.util.Map.of("amount","2","minimum","0","card_category","POKEMON","destination","DECK_UNSHUFFLED","reveal","true"))
            ||args.equals(java.util.Map.of("amount","1","minimum","1","card_category","POKEMON","destination","DECK_UNSHUFFLED","reveal","true"));
    }
    public static boolean boundRecovery(EffectDefinition effect) {
        if(!effect.type().equals("search_discard_pile")||effect.target()!=EffectTarget.DISCARD)return false;
        var args=new java.util.LinkedHashMap<>(effect.arguments());
        String binding=args.remove("bind");if(binding==null)return false;AttackCountBindings.name(binding);
        return args.equals(java.util.Map.of("amount","5","minimum","0","card_category","POKEMON","destination","DECK_SHUFFLED"));
    }
    public boolean topFiveThreeToLostZone() { return topFiveThreeToLostZone(definition); }
    public boolean fixedWindowHandChoice() { return fixedWindowHandChoice(definition); }
    /** Mandatory counts may be clipped for an exhausted window; commit verifies the actual exact count. */
    private static boolean fixedWindowHandChoice(EffectDefinition effect) {
        if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK)return false;
        var args=new java.util.LinkedHashMap<>(effect.arguments());
        if(!Set.of("0","1","2").contains(args.getOrDefault("minimum","")))return false;
        args.put("minimum","2");
        return args.equals(java.util.Map.of("top_count","4","amount","2","minimum","2","remainder","SHUFFLE_BOTTOM"))
            ||args.equals(java.util.Map.of("top_count","4","amount","2","minimum","2","reveal","false"))
            ||args.equals(java.util.Map.of("top_count","2","amount","1","minimum","2","reveal","false","remainder","DISCARD"))
            ||args.equals(java.util.Map.of("top_count","8","amount","1","minimum","2","reveal","false"))
            ||args.equals(java.util.Map.of("top_count","6","amount","2","minimum","2","reveal","false","remainder","DISCARD"));
    }
    private static boolean wholeWindowToBottom(EffectDefinition effect) {
        if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK)return false;
        var args=new java.util.LinkedHashMap<>(effect.arguments());
        if(!Set.of("0","1","2","3").contains(args.getOrDefault("minimum","")))return false;
        args.put("minimum","3");
        return args.equals(java.util.Map.of("amount","3","minimum","3","top_count","3","destination","DECK_BOTTOM_SHUFFLED"));
    }
    private static boolean topFiveThreeToLostZone(EffectDefinition effect) {
        return effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK
            &&effect.arguments().equals(java.util.Map.of("top_count","5","amount","3","minimum","3","remainder","LOST_ZONE"));
    }
    public boolean toLostZone() { return scrapTools(definition)||opponentDiscardToLostZone(definition); }
    public boolean usesSupporterEffect() { return source==EffectTargetResolution.Zone.DISCARD&&"USE_SUPPORTER_EFFECT".equals(definition.arguments().get("destination")); }
    /** Zero means an ordinary whole-deck search; positive counts inspect only that many cards from the top. */
    public int topCount() { return Integer.parseInt(definition.arguments().getOrDefault("top_count", "0")); }
    /** Zero means no bottom window; the server stores deck bottom at index zero. */
    public int bottomCount() { return Integer.parseInt(definition.arguments().getOrDefault("bottom_count", "0")); }
    public boolean toShuffledDeckBottom() { return source == EffectTargetResolution.Zone.DECK && "DECK_BOTTOM_SHUFFLED".equals(definition.arguments().get("destination")); }
    public int maximumPrintedHp() { return Integer.parseInt(definition.arguments().getOrDefault("hp_max", "0")); }
    public Set<String> cardKinds() { return values(definition.arguments().get("card_kinds")); }
    public Set<String> pokemonNames() { return values(definition.arguments().get("pokemon_names")); }
    public Set<String> excludedDefinitionIds() { return values(definition.arguments().get("excluded_definition_ids")); }
    private static Set<String> values(String raw) {
        if (raw == null) return Set.of();
        var result = new java.util.LinkedHashSet<String>();
        for (String value : raw.split("\\|", -1)) {
            if (value.isBlank() || !value.equals(value.trim()) || !result.add(value))
                throw new IllegalArgumentException("卡牌筛选集合包含空值、空白或重复值");
        }
        return Set.copyOf(result);
    }
    public boolean basicEnergyOnly() { return Boolean.parseBoolean(definition.arguments().getOrDefault("basic_energy_only", "false")); }
    /** The allowed Basic Energy types for a search/recovery, or an empty set; the match resolver checks it against audited energy data. */
    public Set<dev.hua.ptcglive.engine.field.EnergyType> basicEnergyTypes() {
        String raw = definition.type().equals("attach_energy") || definition.type().equals("attach_tool") ? null : definition.arguments().get("energy_type");
        if(raw==null)return Set.of();
        if(raw.equals("FIRE|FIGHTING"))return Set.of(dev.hua.ptcglive.engine.field.EnergyType.FIRE,dev.hua.ptcglive.engine.field.EnergyType.FIGHTING);
        return Set.of(dev.hua.ptcglive.engine.field.EnergyType.valueOf(raw));
    }
    /** Deck search whose chosen Basic Pokémon go onto the Bench (limited by free Bench slots) instead of the hand. */
    public boolean toBench() { return (source == EffectTargetResolution.Zone.DECK||source == EffectTargetResolution.Zone.DISCARD)
        && ("BENCH".equals(definition.arguments().get("destination")) || itemPokemonToBench()); }
    public boolean itemPokemonToBench() { return ItemPokemonDeckSearch.handles(definition); }
    public boolean revivesEvolutions() { return source==EffectTargetResolution.Zone.DISCARD
        &&("true".equals(definition.arguments().get("allow_evolution"))||"true".equals(definition.arguments().get("any_stage"))); }
    /** Printed deck placement may include Evolutions without evolving a Pokémon already in play. */
    public boolean placesAnyStage() { return "true".equals(definition.arguments().get("any_stage")); }
    public static boolean caitlinHandBottom(EffectDefinition effect) {
        return effect.type().equals("discard")&&effect.target()==EffectTarget.HAND
            &&effect.arguments().equals(java.util.Map.of("amount","60","minimum","0","destination","DECK_BOTTOM","bind","caitlin"));
    }
    public static boolean orderedDiscardRecovery(EffectDefinition effect) {
        return effect.type().equals("search_discard_pile")&&effect.target()==EffectTarget.DISCARD
            &&"DECK_TOP".equals(effect.arguments().get("destination"))&&"true".equals(effect.arguments().get("ordered"))
            &&Set.of("amount","minimum","destination","ordered","amount_mode","coin").containsAll(effect.arguments().keySet());
    }
    /** Mustard places the selected Battle Style at any stage without evolving a host. */
    public static boolean mustardPlacement(EffectDefinition effect) {
        var args=new java.util.LinkedHashMap<>(effect.arguments());
        boolean rapid=effect.type().equals("search_discard_pile")&&effect.target()==EffectTarget.DISCARD
            &&"mustard".equals(args.remove("bind"));
        boolean single=effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK;
        return (rapid||single)&&args.equals(java.util.Map.of("amount","1","minimum",rapid?"1":"0",
            "card_category","POKEMON","battle_style",rapid?"RAPID_STRIKE":"SINGLE_STRIKE","destination","BENCH","any_stage","true"));
    }
    /** Smog Signals searches by printed name; it does not impose an unwritten stage restriction. */
    public static boolean smogSignals(EffectDefinition effect) {
        return effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK
            &&effect.arguments().equals(java.util.Map.of("amount","2","minimum","0","card_category","POKEMON",
                "pokemon_name_contains","Koffing","destination","BENCH","any_stage","true"));
    }
    public boolean opponentSource() { return "OPPONENT".equals(definition.arguments().get("source_owner")); }
    public boolean toShuffledDeckTop() { return source == EffectTargetResolution.Zone.DECK && "DECK_TOP_AFTER_SHUFFLE".equals(definition.arguments().get("destination")); }
    /** Deck search whose chosen card is put onto the effect's own source Pokémon to evolve it (“this Pokémon”). */
    public int discardTopCount() {return Integer.parseInt(definition.arguments().getOrDefault("discard_top_count","0"));}
    public boolean evolvesSelf() { return source == EffectTargetResolution.Zone.DECK && "EVOLVE_SELF".equals(definition.arguments().get("destination")); }
    public boolean skipsEvolutionStage() { return "1".equals(definition.arguments().get("skip_stage")); }
    public dev.hua.ptcglive.engine.field.EvolutionPermission evolutionPermission() {
        if(definition.arguments().containsKey("evolution_timing"))return TrainerDeckEvolution.permission(definition);
        return new dev.hua.ptcglive.engine.field.EvolutionPermission(true,true,true,skipsEvolutionStage());
    }

    public boolean allows(CardDefinition card) {
        return allows(card, dev.hua.ptcglive.rules.TrainerClassificationRules.MODERN);
    }
    public boolean allows(CardDefinition card, dev.hua.ptcglive.rules.TrainerClassificationRules classification) {
        if (definition.type().equals("attach_energy")) return AttachEnergyEffect.from(definition).allows(card);
        if (definition.type().equals("attach_tool")) return AttachToolEffect.from(definition).allows(card, classification);
        String kind = card.category() == dev.hua.ptcglive.rules.CardCategory.TRAINER ? card.trainerCategory().name() : card.category().name();
        return (cardKinds().isEmpty() || cardKinds().contains(kind)
                || cardKinds().contains("NON_RULEBOX_POKEMON")&&card.category()==dev.hua.ptcglive.rules.CardCategory.POKEMON
                    &&!card.pokemonFamilies().contains(dev.hua.ptcglive.rules.PokemonFamily.RULE_BOX)
                || cardKinds().contains("BASIC_ENERGY") && card.category()==dev.hua.ptcglive.rules.CardCategory.ENERGY && card.basicEnergy()
                || cardKinds().contains("SPECIAL_ENERGY") && card.category()==dev.hua.ptcglive.rules.CardCategory.ENERGY && !card.basicEnergy()
                || cardKinds().contains("ITEM") && classification.matches(card, dev.hua.ptcglive.rules.TrainerCategory.ITEM))
            && !excludedDefinitionIds().contains(card.id())
            && (!definition.arguments().containsKey("battle_style") || card.battleStyles().contains(dev.hua.ptcglive.rules.BattleStyle.valueOf(definition.arguments().get("battle_style"))))
            && (selection.category() == null || card.category() == selection.category())
            && classification.matches(card, selection.trainerCategory())
            && (!selection.basicPokemonOnly() || card.basicPokemon()
                || card.basicEnergy() && cardKinds().contains("BASIC_ENERGY"))
            && (!basicEnergyOnly() || card.basicEnergy())
            && switch (selection.ruleBoxFilter()) {
                case ANY -> true;
                case HAS -> card.pokemonFamilies().contains(dev.hua.ptcglive.rules.PokemonFamily.RULE_BOX);
                case NONE -> !card.pokemonFamilies().contains(dev.hua.ptcglive.rules.PokemonFamily.RULE_BOX);
            }
            && selection.pokemonFamilyFilter().matches(card)
            && selection.pokemonTypeFilter().matches(card)
            && (selection.allowedDefinitionIds().isEmpty() || selection.allowedDefinitionIds().contains(card.id()));
    }
}
