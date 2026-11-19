package dev.hua.ptcglive;

import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.DeckValidationReport;
import dev.hua.ptcglive.rules.DeckValidator;
import dev.hua.ptcglive.rules.Ruleset;
import dev.hua.ptcglive.packs.PackCatalog;
import dev.hua.ptcglive.packs.SetPackDefinition;
import dev.hua.ptcglive.packs.PackOpeningService;
import dev.hua.ptcglive.packs.PackOpeningAuditService;
import dev.hua.ptcglive.economy.EconomyConfig;
import dev.hua.ptcglive.economy.EconomyService;
import dev.hua.ptcglive.economy.PokeCoinLedger;
import dev.hua.ptcglive.table.CardTableManager;
import dev.hua.ptcglive.persistence.PtcgPersistentState;
import dev.hua.ptcglive.starter.StarterDeck100Catalog;
import dev.hua.ptcglive.starter.StarterDeck100Service;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Server process services. They are deliberately independent of GUI state and client packets. */
public final class PtcgServices {
    private static CardBoxService cardBoxes;
    private static CardDatabase cardDatabase;
    private static java.util.List<CardCatalog.Entry> cardCatalog;
    private static Map<String, String> cardLabels;
    private static CombatCatalog combatCatalog;
    private static CardTableManager tables;
    private static Map<String, Ruleset> rulesets;
    private static Map<String, SetPackDefinition> packs;
    private static dev.hua.ptcglive.packs.PackIdMigration packMigration;
    private static PackOpeningService packOpening;
    private static EconomyConfig economyConfig;
    private static EconomyService economy;
    private static PackOpeningAuditService packOpeningAudit;
    private static StarterDeck100Service starterDeck100;
    private static dev.hua.ptcglive.starter.NewbieDeckCatalog newbieDecks;
    private PtcgServices() { }
    public static synchronized void initialize() {
        if (tables != null) return;
        rulesets = BundledRules.loadCurrentRulesets();
        packs = PackCatalog.loadBundled();
        packMigration = dev.hua.ptcglive.packs.PackIdMigration.loadBundled();
        cardCatalog = CardCatalog.load();
        cardDatabase = BundledRules.loadCardDefinitions();
        var allPacks = new java.util.LinkedHashMap<>(packs);
        dev.hua.ptcglive.packs.CustomPackCatalog.loadBundled(cardCatalog, cardDatabase).forEach((id, pack) -> {
            if (allPacks.putIfAbsent(id, pack) != null) throw new IllegalStateException("自定义卡包 ID 冲突：" + id);
        });
        packs = java.util.Collections.unmodifiableMap(allPacks);
        cardLabels = cardCatalog.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(CardCatalog.Entry::id, CardCatalog.Entry::label));
        combatCatalog = BundledCombatData.load();
        cardBoxes = new CardBoxService(Clock.systemUTC());
        starterDeck100 = new StarterDeck100Service(StarterDeck100Catalog.loadBundled(), cardBoxes);
        newbieDecks = dev.hua.ptcglive.starter.NewbieDeckCatalog.loadBundled();
        packOpening = new PackOpeningService(cardBoxes);
        economyConfig = EconomyConfig.loadBundled();
        economy = new EconomyService(economyConfig);
        packOpeningAudit = new PackOpeningAuditService(packs);
        tables = new CardTableManager(rulesets, cardBoxes, cardDatabase, combatCatalog, ServerRandom::new);
    }
    public static CardBoxService cardBoxes() { require(); return cardBoxes; }
    /** Public card identity facts shared by pack preview and encyclopedia UI; immutable after startup. */
    public static java.util.List<CardCatalog.Entry> cardCatalog() { require(); return cardCatalog; }
    public static CardDatabase cardDatabase() { require(); return cardDatabase; }
    public static dev.hua.ptcglive.collection.BoxEditorSession openBoxEditor(UUID owner, UUID boxId) {
        require();
        return new dev.hua.ptcglive.collection.BoxEditorSession(cardBoxes, cardDatabase, combatCatalog, rulesets, cardLabels, owner, boxId);
    }
    public static CardTableManager tables() { require(); return tables; }
    public static Map<String, Ruleset> rulesets() { require(); return rulesets; }
    public static Map<String, SetPackDefinition> packs() { require(); return packs; }
    public static PackOpeningService packOpening() { require(); return packOpening; }
    /** Old pack ID → current booster pack mapping (old-pack-id-map.json). */
    public static dev.hua.ptcglive.packs.PackIdMigration packMigration() { require(); return packMigration; }
    public static EconomyService economy() { require(); return economy; }
    public static PackOpeningAuditService packOpeningAudit() { require(); return packOpeningAudit; }
    public static StarterDeck100Service starterDeck100() { require(); return starterDeck100; }
    public static dev.hua.ptcglive.starter.NewbieDeckCatalog newbieDecks() { require(); return newbieDecks; }
    /** One validation implementation is shared by card-box management and table start. */
    public static DeckValidationReport validateBox(UUID owner, UUID boxId, String rulesetId) {
        require();
        Ruleset ruleset = rulesets.get(rulesetId);
        if (ruleset == null) throw new IllegalArgumentException("未安装的规则包: " + rulesetId);
        return new DeckValidator(cardDatabase, combatCatalog).validate(ruleset, cardBoxes.contentsForValidation(owner, boxId));
    }
    /** Attaches the service graph to one Minecraft world's server-only persistent ledger. */
    public static synchronized void attachPersistentState(PtcgPersistentState state) {
        require();
        cardBoxes = CardBoxService.restore(Clock.systemUTC(), state.cardSnapshot());
        starterDeck100 = new StarterDeck100Service(StarterDeck100Catalog.loadBundled(), cardBoxes, state.starterDeck100Snapshot());
        packOpening = new PackOpeningService(cardBoxes);
        economy = new EconomyService(economyConfig,
            PokeCoinLedger.restore(Clock.systemUTC(), state.economySnapshot()));
        packOpeningAudit = new PackOpeningAuditService(packs, state.packOpeningAuditSnapshot());
        cardDatabase = BundledRules.loadCardDefinitions();
        combatCatalog = BundledCombatData.load();
        tables = CardTableManager.restore(rulesets, cardBoxes, cardDatabase, combatCatalog, ServerRandom::new,
            Clock.systemUTC(), state.tableSnapshot());
        state.bind(cardBoxes, economy, packOpeningAudit, starterDeck100, tables);
    }
    private static void require() { if (tables == null) throw new IllegalStateException("PTCG Craft 服务尚未初始化"); }
}
