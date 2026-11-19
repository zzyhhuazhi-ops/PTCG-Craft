import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.engine.effect.AttackProgramRules;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.EffectSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline audit: card art and names never become guessed card-effect text. */
public final class CardEffectCoverageReport {
    private record Unimplemented(String cardId, String cardNameZh, String set, String attackName, String originalText,
                                 String reason, String suggestedEffectType) { }

    public static void main(String[] args) throws IOException {
        var catalog = CardCatalog.load();
        var rules = BundledRules.loadCardDefinitions();
        var combat = BundledCombatData.load();
        Map<String, CardCatalog.Entry> catalogById = new LinkedHashMap<>();
        for (var entry : catalog) catalogById.put(entry.id(), entry);
        Map<String, CardDefinition> definitions = new LinkedHashMap<>();
        for (var definition : rules.all()) definitions.put(definition.id(), definition);

        var missing = new ArrayList<Unimplemented>();
        for (var entry : catalog) {
            var definition = definitions.get(entry.id());
            if (definition == null) missing.add(new Unimplemented(entry.id(), entry.label(), entry.set(), null, null,
                "未导入可审计的官方卡文；当前仅有卡名、编号和资源索引，不能安全推断对战效果。", "IMPORT_OFFICIAL_CARD_TEXT"));
            else if (definition.effectSupport() != EffectSupport.SUPPORTED) missing.add(new Unimplemented(entry.id(), definition.nameZh(), entry.set(), null, null,
                definition.unsupportedReasonZh(), "REVIEW_CARD_TEXT_AND_EFFECT_PROGRAM"));
        }

        int attacks = 0, basic = 0, component = 0, custom = 0, unsupportedAttacks = 0, abilities = 0;
        for (var entry : combat.pokemonProfiles().entrySet()) {
            CardDefinition definition = definitions.get(entry.getKey());
            CardCatalog.Entry catalogEntry = catalogById.get(entry.getKey());
            String cardName = definition == null ? entry.getKey() : definition.nameZh();
            String set = catalogEntry == null ? "（测试／未列入全卡索引）" : catalogEntry.set();
            for (AttackDefinition attack : entry.getValue().attacks()) {
                attacks++;
                var unsupported = AttackProgramRules.unsupportedReason(attack);
                if (attack.effectSupport() != EffectSupport.SUPPORTED || unsupported.isPresent()) {
                    unsupportedAttacks++;
                    missing.add(new Unimplemented(entry.getKey(), cardName, set, attack.nameZh(), null,
                        attack.effectSupport() == EffectSupport.UNSUPPORTED ? attack.unsupportedReasonZh() : unsupported.orElse("攻击缺少可执行组合"),
                        "REVIEW_ATTACK_EFFECT_PROGRAM"));
                } else if (isBasicDamageAttack(attack)) basic++;
                else { component++; if (containsCustom(attack)) custom++; }
            }
            abilities += entry.getValue().abilities().size();
        }
        missing.sort(Comparator.comparing(Unimplemented::cardId).thenComparing(item -> item.attackName() == null ? "" : item.attackName()));
        int supportedCatalogCards = catalog.size() - (int) missing.stream().map(Unimplemented::cardId).distinct().count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("scopeZh", "目录与已导入、可审计战斗数据的离线覆盖率；不从名称或图片猜测卡文。");
        summary.put("catalogCards", catalog.size()); summary.put("ruleDefinitions", rules.all().size());
        summary.put("pokemonProfiles", combat.pokemonProfiles().size()); summary.put("trainerProfiles", combat.trainerProfiles().size());
        summary.put("energyProfiles", combat.energyProfiles().size()); summary.put("attacks", attacks);
        summary.put("basicTemplateAttacks", basic); summary.put("componentAttacks", component); summary.put("customEffectAttacks", custom);
        summary.put("unsupportedAttacks", unsupportedAttacks); summary.put("abilities", abilities);
        summary.put("catalogCardsWithSupportedRules", supportedCatalogCards);
        summary.put("catalogCardsNeedingDataOrRules", catalog.size() - supportedCatalogCards);
        summary.put("automaticCoverageRate", catalog.isEmpty() ? 0.0d : (double) supportedCatalogCards / catalog.size());

        Path directory = Path.of(System.getProperty("ptcglive.reportDir", "build/reports")); Files.createDirectories(directory);
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
        Files.writeString(directory.resolve("card-effect-coverage.json"), gson.toJson(summary), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("unimplemented_effects.json"), gson.toJson(Map.of("schemaVersion", 1,
            "generatedAt", summary.get("generatedAt"), "noteZh", "originalText 为 null 表示工程尚未导入可审计官方卡文；不得据此自动结算。", "items", missing)), StandardCharsets.UTF_8);
        if (catalog.size() < 13_000 || missing.isEmpty()) throw new AssertionError("全卡目录或未导入卡文审计异常");
        System.out.println("CardEffectCoverageReport passed: catalog=" + catalog.size() + ", attacks=" + attacks
            + ", basic=" + basic + ", component=" + component + ", missing=" + missing.size());
    }

    private static boolean isBasicDamageAttack(AttackDefinition attack) {
        return attack.effectProgram().triggers().isEmpty() && attack.conditions().isEmpty() && attack.coinDamage() == null
            && attack.coinProtection() == null && attack.damageModifiers().isEmpty()
            && attack.damageTarget() == AttackDefinition.DamageTarget.OPPONENT_ACTIVE && attack.selfDamage() == 0
            && attack.discardSelectedAttachedEnergyAfterAttack() == 0 && !attack.discardAllAttachedEnergyAfterAttack()
            && attack.selfHealAfterAttack() == 0 && attack.damageReductionFromOpponentNextTurn() == 0
            && !attack.ignoresWeaknessAndResistance() && !attack.ignoresActiveDefenderDamageEffects()
            && !attack.cannotUseSameAttackDuringNextTurn();
    }
    private static boolean containsCustom(AttackDefinition attack) {
        return attack.effectProgram().triggers().stream().flatMap(binding -> binding.effects().stream()).anyMatch(CardEffectCoverageReport::containsCustom);
    }
    private static boolean containsCustom(EffectDefinition effect) {
        return effect.type().equals("custom") || effect.effects().stream().anyMatch(CardEffectCoverageReport::containsCustom);
    }
}
