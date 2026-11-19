package dev.hua.ptcglive.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardExclusions;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.effect.ConditionDefinition;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/** Loads an audited combat snapshot; the mod never fetches card facts during play. */
public final class BundledCombatData {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("ptcglive");
    private static final int INVALID_ENTRY_WARNING_LIMIT = 200;
    private BundledCombatData() { }
    public static CombatCatalog load() {
        JsonObject root = object("/data/ptcglive/combat/index.json");
        Map<String, PokemonProfile> pokemon = new LinkedHashMap<>();
        Map<String, EnergyType> energy = new LinkedHashMap<>();
        Map<String, EnergyProfile> specialEnergy = new LinkedHashMap<>();
        Map<String, TrainerProfile> trainers = new LinkedHashMap<>();
        List<String> invalidEntries = new ArrayList<>();
        EffectRegistry effectRegistry = EffectRegistry.standard();
        for (JsonElement file : root.getAsJsonArray("files")) {
            JsonObject data = object("/data/ptcglive/combat/" + file.getAsString());
            for (JsonElement card : data.getAsJsonArray("cards")) {
                JsonObject item = card.getAsJsonObject();
                String id = item.get("id").getAsString();
                if (CardExclusions.contains(id)) continue;
                String kind = item.get("kind").getAsString();
                try {
                    if (kind.equals("POKEMON")) {
                        if (pokemon.putIfAbsent(id, pokemon(item, effectRegistry)) != null) throw new IllegalStateException("重复宝可梦战斗数据: " + id);
                    } else if (kind.equals("ENERGY")) {
                        if (item.has("energyUnits")) {
                            if (specialEnergy.putIfAbsent(id, specialEnergy(item,effectRegistry)) != null)
                                throw new IllegalStateException("重复特殊能量战斗数据: " + id);
                        } else {
                            if(item.has("handDiscardCost")||item.has("attachmentRule")||item.has("automaticEffects")||item.has("provisionRules")||!effectProgram(item,effectRegistry,EffectTrigger.PASSIVE).isEmpty())throw new IllegalArgumentException("基本能量入口不能忽略附着限制、持续程序、自动触发或供能分支；请使用已审核特殊能量档案");
                            EnergyType type = EnergyType.valueOf(item.get("energyType").getAsString());
                            if (energy.putIfAbsent(id, type) != null) throw new IllegalStateException("重复能量战斗数据: " + id);
                        }
                    } else if (kind.equals("TRAINER")) {
                        var profile = trainer(item, effectRegistry);
                        if (trainers.putIfAbsent(id, profile) != null) throw new IllegalStateException("重复训练家效果: " + id);
                    } else throw new IllegalStateException("未知战斗数据类别: " + kind);
                } catch (RuntimeException invalid) {
                    invalidEntries.add(id + "（" + file.getAsString() + "）：" + invalid.getMessage());
                }
            }
        }
        if (!invalidEntries.isEmpty()) {
            int shown = Math.min(INVALID_ENTRY_WARNING_LIMIT, invalidEntries.size());
            LOGGER.warn("已跳过 {} 条无效战斗数据；这些卡牌仍可收藏，但在修复规则前不能用于有效对战。前 {} 条：\n{}",
                invalidEntries.size(), shown, String.join("\n", invalidEntries.subList(0, shown)));
            LOGGER.debug("全部无效战斗数据：\n{}", String.join("\n", invalidEntries));
        }
        return new CombatCatalog(pokemon, energy, trainers, specialEnergy);
    }
    /** Trainer shorthand binds to card play; an absent legacy opcode is not a second hidden effect. */
    public static TrainerProfile trainer(JsonObject item, EffectRegistry registry) {
        for(String flag:java.util.List.of("endsTurn","mayPlayFirstTurn","directionalStadium"))
            if(item.has(flag) && (!item.get(flag).isJsonPrimitive() || !item.getAsJsonPrimitive(flag).isBoolean()))
                throw new IllegalArgumentException("训练家布尔标记格式错误："+flag);
        JsonObject effect = optionalObject(item, "effect");
        var category=TrainerCategory.valueOf(item.get("trainerCategory").getAsString());
        var opcode = effect == null ? switch(category) {
            case TOOL -> TrainerEffect.Opcode.PASSIVE_TOOL;
            case STADIUM -> TrainerEffect.Opcode.PASSIVE_STADIUM;
            default -> TrainerEffect.Opcode.COMPOSED;
        } : TrainerEffect.Opcode.valueOf(effect.get("opcode").getAsString());
        int amount = effect == null ? 0 : effect.get("amount").getAsInt();
        PairedTrainerPlay pair=null;
        if(item.has("pairedPlay")) {
            if(!item.get("pairedPlay").isJsonObject())throw new IllegalArgumentException("pairedPlay须为明确对象");
            pair=PairedTrainerPlay.parse(item.getAsJsonObject("pairedPlay"),registry);
            if(!item.has("id")||!pair.definitionIds().contains(item.get("id").getAsString()))
                throw new IllegalArgumentException("同时使用卡牌编号组必须包含此卡");
        }
        return new TrainerProfile(category,
            new TrainerEffect(opcode, amount,
                effect != null && effect.has("sixPrizesAmount") ? effect.get("sixPrizesAmount").getAsInt() : 0,
                effect != null && effect.has("toolCapacity") ? effect.get("toolCapacity").getAsInt() : defaultToolCapacity(opcode.name()),
                effect != null && effect.has("discardHandCost") ? effect.get("discardHandCost").getAsInt() : 0,
                effect != null && effect.has("minimumSelections") ? effect.get("minimumSelections").getAsInt()
                    : (opcode == TrainerEffect.Opcode.SEARCH_BASIC_POKEMON_TO_HAND || opcode == TrainerEffect.Opcode.SEARCH_POKEMON_TO_HAND || opcode == TrainerEffect.Opcode.SEARCH_CARD_TO_HAND ? amount : 0)),
            effectProgram(item, registry, category==TrainerCategory.TOOL||category==TrainerCategory.STADIUM?EffectTrigger.PASSIVE:EffectTrigger.ON_CARD_PLAYED),automaticEffects(item,registry),
            item.has("endsTurn")&&item.get("endsTurn").getAsBoolean(),
            item.has("mayPlayFirstTurn")&&item.get("mayPlayFirstTurn").getAsBoolean(),
            item.has("inPlayPokemon")?pokemon(item.getAsJsonObject("inPlayPokemon"),registry):null,
            item.has("activation")?stadiumActivation(item.getAsJsonObject("activation"),registry):null,
            item.has("toolActivation")?toolActivation(item.getAsJsonObject("toolActivation"),registry):null,pair,
            item.has("directionalStadium")&&item.get("directionalStadium").getAsBoolean());
    }
    private static StadiumActivation stadiumActivation(JsonObject item,EffectRegistry registry) {
        if(!java.util.Set.of("usage","effects","triggers","endsTurn").containsAll(item.keySet())
                || !item.has("usage") || !item.get("usage").isJsonPrimitive()
                || !"ONCE_PER_PLAYER_TURN".equals(item.get("usage").getAsString()))
            throw new IllegalArgumentException("竞技场主动操作须明确每位玩家每个自己回合一次，不能忽略未知参数");
        if(item.has("endsTurn")&&(!item.get("endsTurn").isJsonPrimitive()||!item.getAsJsonPrimitive("endsTurn").isBoolean()))
            throw new IllegalArgumentException("竞技场 endsTurn 必须为布尔值");
        return new StadiumActivation(effectProgram(item,registry,EffectTrigger.ON_ACTIVATE),item.has("endsTurn")&&item.get("endsTurn").getAsBoolean());
    }
    private static ToolActivation toolActivation(JsonObject item,EffectRegistry registry) {
        if(!item.keySet().equals(java.util.Set.of("usageLimit","holderBasicOnly","holderFamilies","effects"))
            ||!item.get("holderBasicOnly").isJsonPrimitive()||!item.getAsJsonPrimitive("holderBasicOnly").isBoolean()
            ||!item.get("holderFamilies").isJsonPrimitive()||!item.getAsJsonPrimitive("holderFamilies").isString()
            ||!item.get("usageLimit").isJsonObject())
            throw new IllegalArgumentException("工具主动力量须完整声明共享资格、基础持有者、家族及效果");
        return new ToolActivation(effectProgram(item,registry,EffectTrigger.ON_ACTIVATE),
            dev.hua.ptcglive.engine.effect.SharedUsageLimit.parse(item.getAsJsonObject("usageLimit")),
            item.get("holderBasicOnly").getAsBoolean(),dev.hua.ptcglive.engine.effect.PokemonFamilyFilter.fromWire(
                item.get("holderFamilies").getAsString(),null));
    }
    /** Special Energy uses ordered first-matching provision branches, separate from ongoing effects. */
    public static EnergyProfile specialEnergy(JsonObject item,EffectRegistry registry) {
        if(item.has("effect")||item.has("energyType"))throw new IllegalArgumentException("特殊能量不能混用未执行的旧效果或基本能量字段");
        var rules=new ArrayList<dev.hua.ptcglive.engine.effect.EnergyProvisionRule>();
        if(item.has("provisionRules"))for(var element:item.getAsJsonArray("provisionRules")) {
            var rule=element.getAsJsonObject();
            if(!rule.keySet().equals(java.util.Set.of("conditions","energyUnits")))throw new IllegalArgumentException("供能分支须且仅须包含conditions和energyUnits");
            var conditions=new ArrayList<ConditionDefinition>();
            for(var value:rule.getAsJsonArray("conditions"))conditions.add(EffectRegistry.parseCondition(value.getAsJsonObject()));
            rules.add(new dev.hua.ptcglive.engine.effect.EnergyProvisionRule(conditions,energyProfile(rule.getAsJsonArray("energyUnits")).units()));
        }
        if(item.has("handDiscardCost")&&(!item.get("handDiscardCost").isJsonPrimitive()
            ||!item.getAsJsonPrimitive("handDiscardCost").isNumber()||!item.get("handDiscardCost").getAsString().equals("1")))
            throw new IllegalArgumentException("手牌附能弃牌费用须为整数1");
        return new EnergyProfile(energyProfile(item.getAsJsonArray("energyUnits")).units(),effectProgram(item,registry,EffectTrigger.PASSIVE),rules,automaticEffects(item,registry),
            item.has("attachmentRule")?dev.hua.ptcglive.engine.effect.EnergyAttachmentRule.from(item.getAsJsonObject("attachmentRule"))
                :dev.hua.ptcglive.engine.effect.EnergyAttachmentRule.NONE,item.has("handDiscardCost")?1:0);
    }
    private static List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects(JsonObject item,EffectRegistry registry) {
        if(!item.has("automaticEffects"))return List.of();
        var result=new ArrayList<dev.hua.ptcglive.engine.effect.CardTriggerDefinition>();
        for(var value:item.getAsJsonArray("automaticEffects")) {
            var data=value.getAsJsonObject();
            if(!java.util.Set.of("id","nameZh","eventPolicy","effects","triggers","usageLimit","sourceCardOnly").containsAll(data.keySet()))
                throw new IllegalArgumentException("持续卡自动效果包含未支持字段");
            if(data.has("sourceCardOnly")&&(!data.get("sourceCardOnly").isJsonPrimitive()||!data.getAsJsonPrimitive("sourceCardOnly").isBoolean()))
                throw new IllegalArgumentException("sourceCardOnly必须为布尔值");
            var policy=dev.hua.ptcglive.engine.effect.AbilityEventPolicy.parse(data.getAsJsonObject("eventPolicy"));
            result.add(new dev.hua.ptcglive.engine.effect.CardTriggerDefinition(data.get("id").getAsString(),data.get("nameZh").getAsString(),
                effectProgram(data,registry,policy.trigger()),policy,usageLimit(data),data.has("sourceCardOnly")&&data.get("sourceCardOnly").getAsBoolean()));
        }
        return List.copyOf(result);
    }
    /** JSON shape: energyUnits: [["FIRE","WATER"], ["COLORLESS"]]. Each inner array is one physical provision unit. */
    private static EnergyProfile energyProfile(JsonArray units) {
        List<EnergyProfile.Unit> result = new ArrayList<>();
        for (JsonElement element : units) {
            var types = new java.util.LinkedHashSet<EnergyType>();
            for (JsonElement type : element.getAsJsonArray()) types.add(EnergyType.valueOf(type.getAsString()));
            result.add(new EnergyProfile.Unit(types));
        }
        return new EnergyProfile(result);
    }
    private static int defaultToolCapacity(String opcode) {
        return opcode.equals("TOOL_RETREAT_REDUCTION_ALLOW_TWO") ? 2 : opcode.equals("TOOL_RETREAT_REDUCTION")||opcode.equals("PASSIVE_TOOL") ? 1 : 0;
    }
    public static PokemonProfile pokemon(JsonObject item, EffectRegistry effectRegistry) {
        JsonObject weakness = optionalObject(item, "weakness");
        JsonObject resistance = optionalObject(item, "resistance");
        List<AttackDefinition> attacks = new ArrayList<>();
        for (JsonElement element : item.getAsJsonArray("attacks")) attacks.add(attack(element.getAsJsonObject(), effectRegistry));
        List<AbilityDefinition> abilities = new ArrayList<>();
        if (item.has("abilities")) for (JsonElement element : item.getAsJsonArray("abilities")) abilities.add(ability(element.getAsJsonObject(), effectRegistry));
        return new PokemonProfile(item.get("hitPoints").getAsInt(), item.get("retreatCost").getAsInt(), optionalString(item, "evolvesFromDefinitionId"),
            weakness == null ? null : EnergyType.valueOf(weakness.get("type").getAsString()), weakness == null ? 0 : weakness.get("multiplier").getAsInt(),
            resistance == null ? null : EnergyType.valueOf(resistance.get("type").getAsString()), resistance == null ? 0 : resistance.get("amount").getAsInt(),
            item.get("prizeCards").getAsInt(), attacks, abilities,
            optionalString(item,"pokemonType")==null?null:EnergyType.valueOf(item.get("pokemonType").getAsString()),
            optionalString(item, "pokemonName"), optionalString(item, "evolvesFromName"),
            item.has("placementRule")?dev.hua.ptcglive.engine.field.PokemonPlacementRule.valueOf(item.get("placementRule").getAsString()):dev.hua.ptcglive.engine.field.PokemonPlacementRule.ANY_EFFECT,
            item.has("printedRules")?dev.hua.ptcglive.engine.field.PokemonPrintedRules.valueOf(item.get("printedRules").getAsString()):dev.hua.ptcglive.engine.field.PokemonPrintedRules.STANDARD);
    }
    public static AbilityDefinition ability(JsonObject item, EffectRegistry effectRegistry) {
        if (item.has("endsTurn") && (!item.get("endsTurn").isJsonPrimitive() || !item.getAsJsonPrimitive("endsTurn").isBoolean()))
            throw new IllegalArgumentException("特性 endsTurn 必须为布尔值");
        var kind = item.has("kind") ? AbilityDefinition.Kind.valueOf(item.get("kind").getAsString()) : AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN;
        var eventPolicy = item.has("eventPolicy") ? dev.hua.ptcglive.engine.effect.AbilityEventPolicy.parse(item.getAsJsonObject("eventPolicy")) : null;
        var defaultTrigger = switch (kind) {
            case ACTIVATED_UNLIMITED -> EffectTrigger.ON_ACTIVATE;
            case ACTIVATED_ONCE_PER_GAME -> EffectTrigger.ONCE_PER_GAME;
            case TRIGGERED -> eventPolicy == null ? EffectTrigger.ONCE_PER_TURN : eventPolicy.trigger();
            case PASSIVE -> EffectTrigger.PASSIVE;
            default -> EffectTrigger.ONCE_PER_TURN;
        };
        JsonObject effect = optionalObject(item, "effect");
        AbilityEffect.Opcode opcode = effect == null ? AbilityEffect.Opcode.COMPOSED : AbilityEffect.Opcode.valueOf(effect.get("opcode").getAsString());
        int amount = effect == null ? 0 : effect.get("amount").getAsInt();
        return new AbilityDefinition(item.get("id").getAsString(), item.get("nameZh").getAsString(),
            new AbilityEffect(opcode, amount, effect != null && effect.has("minimumSelections") ? effect.get("minimumSelections").getAsInt()
                : (opcode == AbilityEffect.Opcode.SEARCH_BASIC_POKEMON_TO_HAND || opcode == AbilityEffect.Opcode.SEARCH_POKEMON_TO_HAND
                    || opcode == AbilityEffect.Opcode.SEARCH_CARD_TO_HAND ? amount : 0)),
            EffectSupport.valueOf(item.get("effectSupport").getAsString()), optionalString(item, "unsupportedReasonZh"),
            kind, effectProgram(item, effectRegistry, defaultTrigger), eventPolicy,
            usageLimit(item), item.has("endsTurn") && item.get("endsTurn").getAsBoolean());
    }
    /** Shared parser for bundled snapshots and developer import validation. */
    public static AttackDefinition attack(JsonObject item, EffectRegistry effectRegistry) {
        if(item.has("mayUseFromBench")&&(!item.get("mayUseFromBench").isJsonPrimitive()||!item.getAsJsonPrimitive("mayUseFromBench").isBoolean()))
            throw new IllegalArgumentException("招式的备战位使用许可须为布尔值");
        if(item.has("targetHasSpecialEnergy")&&(!item.get("targetHasSpecialEnergy").isJsonPrimitive()||!item.getAsJsonPrimitive("targetHasSpecialEnergy").isBoolean()))
            throw new IllegalArgumentException("招式的附着特殊能量目标限定须为布尔值");
        for(String field:java.util.List.of("mayAttackFirstTurn","cannotAttackSecondPlayerFirstTurn","onlySecondPlayerFirstTurn"))
            if(item.has(field)&&(!item.get(field).isJsonPrimitive()||!item.getAsJsonPrimitive(field).isBoolean()))
                throw new IllegalArgumentException("招式的首回合时点字段须为布尔值："+field);
        List<dev.hua.ptcglive.engine.field.ConditionEffect> conditions = new ArrayList<>();
        if (item.has("conditions")) for (JsonElement element : item.getAsJsonArray("conditions")) {
            JsonObject effect = element.getAsJsonObject();
            JsonObject coinCondition = optionalObject(effect, "coinCondition");
            conditions.add(new dev.hua.ptcglive.engine.field.ConditionEffect(
                dev.hua.ptcglive.engine.field.ConditionEffect.Target.valueOf(effect.get("target").getAsString()),
                dev.hua.ptcglive.engine.field.SpecialCondition.valueOf(effect.get("condition").getAsString()),
                coinCondition == null ? null : new dev.hua.ptcglive.engine.field.ConditionEffect.CoinCondition(
                    coinCondition.get("flips").getAsInt(), coinCondition.get("requiredHeads").getAsInt())));
        }
        Map<EnergyType, Integer> cost = new LinkedHashMap<>();
        item.getAsJsonObject("energyCost").entrySet().forEach(entry -> cost.put(EnergyType.valueOf(entry.getKey()), entry.getValue().getAsInt()));
        List<dev.hua.ptcglive.engine.field.DamageModifier> modifiers = new ArrayList<>();
        if (item.has("damageModifiers")) for (JsonElement element : item.getAsJsonArray("damageModifiers")) {
            JsonObject value = element.getAsJsonObject();
            modifiers.add(new dev.hua.ptcglive.engine.field.DamageModifier(
                dev.hua.ptcglive.engine.field.DamageModifier.Basis.valueOf(value.get("basis").getAsString()),
                value.get("amountPerUnit").getAsInt(), value.has("maximumUnits") ? value.get("maximumUnits").getAsInt() : 0));
        }
        return new AttackDefinition(item.get("id").getAsString(), item.get("nameZh").getAsString(), cost,
            item.get("baseDamage").getAsInt(), item.has("damageType") && !item.get("damageType").isJsonNull()
                ? EnergyType.valueOf(item.get("damageType").getAsString()) : null,
            EffectSupport.valueOf(item.get("effectSupport").getAsString()), optionalString(item, "unsupportedReasonZh"), conditions,
            optionalObject(item, "coinDamage") == null ? null : new AttackDefinition.CoinDamage(
                item.getAsJsonObject("coinDamage").get("flips").getAsInt(), item.getAsJsonObject("coinDamage").get("perHeads").getAsInt()),
            optionalObject(item, "coinProtection") == null ? null : new AttackDefinition.CoinProtection(
                item.getAsJsonObject("coinProtection").get("flips").getAsInt(), item.getAsJsonObject("coinProtection").get("requiredHeads").getAsInt(),
                item.getAsJsonObject("coinProtection").has("scope")
                    ? AttackDefinition.ProtectionScope.valueOf(item.getAsJsonObject("coinProtection").get("scope").getAsString())
                    : AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS), modifiers,
            item.has("damageTarget") ? AttackDefinition.DamageTarget.valueOf(item.get("damageTarget").getAsString())
                : AttackDefinition.DamageTarget.OPPONENT_ACTIVE,
            item.has("selfDamage") ? item.get("selfDamage").getAsInt() : 0,
            item.has("targetCount") ? item.get("targetCount").getAsInt()
                : (item.has("damageTarget") && java.util.Set.of("ALL_OPPONENT_BENCH", "ALL_OPPONENT_POKEMON", "ALL_POKEMON").contains(item.get("damageTarget").getAsString()) ? 0 : 1),
            item.has("discardAllAttachedEnergyAfterAttack") && item.get("discardAllAttachedEnergyAfterAttack").getAsBoolean(),
            item.has("selfHealAfterAttack") ? item.get("selfHealAfterAttack").getAsInt() : 0,
            item.has("discardSelectedAttachedEnergyAfterAttack") ? item.get("discardSelectedAttachedEnergyAfterAttack").getAsInt() : 0,
            item.has("ignoresWeaknessAndResistance") && item.get("ignoresWeaknessAndResistance").getAsBoolean(),
            item.has("damageReductionFromOpponentNextTurn") ? item.get("damageReductionFromOpponentNextTurn").getAsInt() : 0,
            item.has("ignoresActiveDefenderDamageEffects") && item.get("ignoresActiveDefenderDamageEffects").getAsBoolean(),
            item.has("cannotUseSameAttackDuringNextTurn") && item.get("cannotUseSameAttackDuringNextTurn").getAsBoolean(),
            effectProgram(item, effectRegistry, EffectTrigger.ON_ATTACK),usageLimit(item),
            item.has("ignoresResistance") && item.get("ignoresResistance").getAsBoolean(),
            item.has("ignoresWeakness") && item.get("ignoresWeakness").getAsBoolean(),
            dev.hua.ptcglive.engine.effect.PokemonFamilyFilter.fromWire(optionalString(item,"targetPokemonFamilies"),optionalString(item,"targetFamilyMatch")),item.has("mayUseFromBench")&&item.get("mayUseFromBench").getAsBoolean(),
            item.has("targetHasSpecialEnergy")&&item.get("targetHasSpecialEnergy").getAsBoolean(),
            item.has("mayAttackFirstTurn")&&item.get("mayAttackFirstTurn").getAsBoolean(),
            item.has("cannotAttackSecondPlayerFirstTurn")&&item.get("cannotAttackSecondPlayerFirstTurn").getAsBoolean(),
            item.has("onlySecondPlayerFirstTurn")&&item.get("onlySecondPlayerFirstTurn").getAsBoolean(),
            attackUseRequirement(item),
            item.has("applyWeaknessResistanceToBench")&&item.get("applyWeaknessResistanceToBench").getAsBoolean());
    }
    private static dev.hua.ptcglive.engine.field.AttackUseRequirement attackUseRequirement(JsonObject attack) {
        if(!attack.has("useRequirement"))return null;
        if(!attack.get("useRequirement").isJsonObject())throw new IllegalArgumentException("招式使用条件须为对象");
        var value=attack.getAsJsonObject("useRequirement");
        var type=dev.hua.ptcglive.engine.field.AttackUseRequirement.Type.valueOf(value.get("type").getAsString());
        var fields=value.entrySet().stream().map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        if(type==dev.hua.ptcglive.engine.field.AttackUseRequirement.Type.NO_SUPPORTER_IN_DISCARD
            ||type==dev.hua.ptcglive.engine.field.AttackUseRequirement.Type.LOST_ZONE_TEN_CARDS) {
            if(!fields.equals(java.util.Set.of("type")))throw new IllegalArgumentException("区域数量条件仅接受 type");
            return new dev.hua.ptcglive.engine.field.AttackUseRequirement(type,null,null);
        }
        if(type==dev.hua.ptcglive.engine.field.AttackUseRequirement.Type.PREVIOUS_OWN_TURN_ATTACK_NOT_USED
            ||type==dev.hua.ptcglive.engine.field.AttackUseRequirement.Type.PREVIOUS_OWN_TURN_SOURCE_ATTACK) {
            if(!fields.equals(java.util.Set.of("type","attackId")))
                throw new IllegalArgumentException("上回合己方招式禁用条件须只指定招式 ID");
            return new dev.hua.ptcglive.engine.field.AttackUseRequirement(type,null,value.get("attackId").getAsString());
        }
        if(!fields.equals(java.util.Set.of("type","pokemonName","attackId")))
            throw new IllegalArgumentException("上回合招式条件须同时指定宝可梦名称与招式 ID");
        return new dev.hua.ptcglive.engine.field.AttackUseRequirement(type,
            value.get("pokemonName").getAsString(),value.get("attackId").getAsString());
    }
    private static dev.hua.ptcglive.engine.effect.SharedUsageLimit usageLimit(JsonObject item) {
        if(!item.has("usageLimit"))return null;
        if(!item.get("usageLimit").isJsonObject())throw new IllegalArgumentException("usageLimit必须为明确的共享资格对象");
        return dev.hua.ptcglive.engine.effect.SharedUsageLimit.parse(item.getAsJsonObject("usageLimit"));
    }
    /** Shorthand trigger is determined by the card role, never always ON_ATTACK. */
    private static EffectProgram effectProgram(JsonObject item, EffectRegistry registry, EffectTrigger defaultTrigger) {
        // Attack-level conditions are the legacy special-condition effects, parsed by attack().
        // For other roles, a misplaced predicate must not disappear from shorthand effects.
        if(defaultTrigger!=EffectTrigger.ON_ATTACK&&item.has("conditions"))
            throw new IllegalArgumentException("条件必须写在triggers绑定或Effect内；不能忽略卡牌顶层conditions");
        List<TriggeredEffects> bindings = new ArrayList<>();
        // The shared ordinary-attack template explicitly uses effects: [].
        // An empty program is different from an empty trigger binding.
        if (item.has("effects") && !item.getAsJsonArray("effects").isEmpty())
            bindings.add(new TriggeredEffects(defaultTrigger, List.of(), effects(item.getAsJsonArray("effects"), registry)));
        if (item.has("triggers")) for (JsonElement element : item.getAsJsonArray("triggers")) {
            JsonObject binding = element.getAsJsonObject();
            List<ConditionDefinition> conditions = new ArrayList<>();
            if (binding.has("conditions")) for (JsonElement condition : binding.getAsJsonArray("conditions"))
                conditions.add(EffectRegistry.parseCondition(condition.getAsJsonObject()));
            bindings.add(new TriggeredEffects(EffectTrigger.fromWire(binding.get("trigger").getAsString()), conditions,
                effects(binding.getAsJsonArray("effects"), registry)));
        }
        return bindings.isEmpty() ? EffectProgram.EMPTY : new EffectProgram(bindings);
    }
    private static List<EffectDefinition> effects(JsonArray values, EffectRegistry registry) {
        List<EffectDefinition> effects = new ArrayList<>();
        for (JsonElement element : values) effects.add(registry.parse(element.getAsJsonObject()));
        return List.copyOf(effects);
    }
    private static JsonObject object(String path) {
        InputStream stream = BundledCombatData.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("缺少战斗数据文件: " + path);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch (Exception exception) { throw new IllegalStateException("无法读取战斗数据文件: " + path, exception); }
    }
    private static JsonObject optionalObject(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.getAsJsonObject(name) : null;
    }
    private static String optionalString(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : null;
    }
}
