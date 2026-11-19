import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.collection.*;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

public final class LiveDeckImportTest {
    public static final String DRAGAPULT_SAMPLE = """
        Pokémon: 23
        4 Dreepy PRE 71
        4 Drakloak PRE 72
        3 Dragapult ex PRE 73
        2 Duskull PRE 35
        2 Dusclops SFA 19
        1 Dusknoir SFA 20
        2 Budew PRE 4
        1 Fezandipiti ex SFA 38
        1 Latias ex SSP 76
        1 Hawlucha SVI 118
        1 Munkidori TWM 95
        1 Bloodmoon Ursaluna ex TWM 141
        Trainer: 30
        4 Lillie's Determination MEG 119
        4 Iono PAL 185
        3 Boss's Orders RCL 154
        1 Colress's Tenacity SFA 57
        4 Poké Pad 30C 126
        4 Buddy-Buddy Poffin PRE 101
        3 Counter Catcher CIN 91
        3 Ultra Ball SLG 68
        2 Night Stretcher SFA 61
        2 Jamming Tower TWM 153
        Energy: 7
        3 Luminous Energy PAL 191
        2 Psychic Energy SVE 5
        1 Fire Energy SVE 2
        1 Neo Upper Energy TEF 162
        """;
    public static final String GRIMMSNARL_SAMPLE = """
        Pokémon: 19
        4 Munkidori TWM 95
        3 Marnie's Impidimp DRI 134
        2 Marnie's Morgrem DRI 135
        2 Marnie's Grimmsnarl ex DRI 136
        2 Snorunt TWM 51
        2 Froslass TWM 53
        2 Budew PRE 4
        1 Iron Bundle PAR 56
        1 Shaymin DRI 10
        Trainer: 32
        4 Lillie's Determination MEG 119
        4 Arven SVI 166
        4 Iono PAL 185
        2 Boss's Orders RCL 154
        3 Poké Pad 30C 126
        2 Counter Catcher CIN 91
        2 Night Stretcher SFA 61
        1 Super Rod PAL 188
        1 Secret Box TWM 163
        1 Rare Candy SUM 129
        1 Buddy-Buddy Poffin PRE 101
        1 Rescue Board TEF 159
        1 Bravery Charm PAL 173
        1 Technical Machine: Evolution PAR 178
        1 Technical Machine: Devolution PAR 177
        3 Spikemuth Gym DRI 169
        Energy: 9
        9 Darkness Energy SVE 7
        """;
    public static final String HIGH_RARITY_SAMPLE = """
        Pokémon: 10
        4 Munkidori SFA 72
        1 Fezandipiti ex ASC 288
        1 Scream Tail SVALT 40
        1 Lillie's Clefairy ex ASC 280
        1 Gardevoir ex PAF 233
        1 Mew ex PAF 232
        3 Ralts SVI 211
        2 Kirlia SVI 212
        1 Unown 30C 72
        1 Gardevoir ex SVI 245

        Trainer: 22
        1 Buddy-Buddy Poffin TWM 223
        1 Iono PAL 254
        3 Lillie's Determination MEG 184
        1 Secret Box TWM 163
        1 Super Rod PAL 276
        1 Technical Machine: Devolution PAR 177
        2 Artazon OBF 229
        1 Iono PR-SV 124
        1 Nest Ball SVI 255
        3 Earthen Vessel SFA 96
        2 Rare Candy SVI 256
        2 Arven PAF 235
        1 Boss's Orders PAL 265
        2 Night Stretcher SSP 251
        1 Nest Ball SUM 158
        1 Bravery Charm SCR 175
        1 Iono PAL 269
        2 Technical Machine: Evolution PAR 178
        2 Ultra Ball BRS 186
        1 Counter Catcher PAR 264
        2 Professor's Research SVI 240
        1 Ultra Ball SUM 161

        Energy: 6
        1 Basic {D} Energy BUS 168
        2 Basic {D} Energy EVS 236
        3 Basic {P} Energy CRE 232
        1 Basic {D} Energy SFA 98
        2 Basic {P} Energy MEW 207
        2 Basic {P} Energy SUM 162

        Total Cards: 60
        """;
    public static final String SAMPLE = """
        Pokémon: 17
        4 Munkidori TWM 95
        3 Ralts SVI 84
        2 Kirlia SVI 85
        2 Gardevoir ex SVI 86
        1 Scream Tail PRE 42
        1 Fezandipiti ex SFA 38
        1 Psyduck ASC 39
        1 Lillie's Clefairy ex ASC 76
        1 Mew ex MEW 151
        1 Unown 30C 72

        Trainer: 32
        4 Iono PAL 185
        2 Professor's Research SSH 178
        2 Lillie's Determination MEG 119
        2 Arven SVI 166
        3 Earthen Vessel PRE 106
        3 Ultra Ball SLG 68
        2 Nest Ball SUM 123
        2 Counter Catcher CIN 91
        2 Rare Candy SUM 129
        2 Night Stretcher SFA 61
        1 Super Rod PAL 188
        1 Secret Box TWM 163
        2 Technical Machine: Evolution PAR 178
        1 Bravery Charm PAL 173
        1 Technical Machine: Turbo Energize PAR 179
        2 Artazon PAL 171

        Energy: 11
        7 Psychic Energy SVE 5
        4 Darkness Energy SVE 7
        """;
    public static void main(String[] args) {
        var labels = CardCatalog.load().stream().collect(Collectors.toMap(CardCatalog.Entry::id, CardCatalog.Entry::label));
        creativeImport(labels);
        for (String sample : List.of(SAMPLE, HIGH_RARITY_SAMPLE, DRAGAPULT_SAMPLE, GRIMMSNARL_SAMPLE)) {
            var normal = LiveDeckParser.parse(sample, labels.keySet());
            check(LiveDeckParser.parse(LiveDeckParser.export(normal), labels.keySet()).equals(normal), "Live export/import keeps exact printings");
            check(LiveDeckParser.parse(sample.replace("\n", ""), labels.keySet()).equals(normal), "lost line breaks must preserve all quantities and printings");
            check(LiveDeckParser.parse(sample.replace("\n", " "), labels.keySet()).equals(normal), "single-line space-separated deck");
            check(LiveDeckParser.parse("|   |\n| - |\n" + sample.replace("\n", "\n\n"), labels.keySet()).equals(normal), "empty Markdown table and blank lines");
        }
        var plan = LiveDeckParser.parse(SAMPLE, labels.keySet());
        Map<String, Integer> craftSample = new LinkedHashMap<>();
        String craftCounts = "2 me1_en_119,1 me1_en_169,1 me1_en_173,1 me2-5_en_183,1 me2-5_en_264,1 me5-5_en_072,7 sm1_en_162,4 sm3_en_168,2 sv1_en_196,3 sv1_en_211,1 sv1_en_212,1 sv1_en_235,1 sv1_en_240,1 sv1_en_241,1 sv1_en_245,1 sv1_en_249,2 sv1_en_255,1 sv1_en_256,2 sv2_en_171,1 sv2_en_173,1 sv2_en_185,1 sv2_en_254,1 sv2_en_269,1 sv2_en_276,1 sv4-5_en_089,1 sv4-5_en_154,1 sv4-5_en_232,1 sv4-5_en_233,1 sv4_en_160,1 sv4_en_163,1 sv4_en_177,2 sv4_en_178,1 sv5_en_144,1 sv6-5_en_061,4 sv6-5_en_072,1 sv6-5_en_092,2 sv6-5_en_096,1 sv6_en_163,1 sv8-5_en_042,1 sv9_en_056";
        for (String line : craftCounts.split(",")) { String[] parts = line.split(" "); craftSample.put(parts[1], Integer.parseInt(parts[0])); }
        String craftExport = LiveDeckParser.export(craftSample);
        check(craftExport.contains("2 Lillie's Determination MEG 119") && craftExport.contains("1 Lillie's Clefairy ex JTG 56"), "English names and Live set codes");
        check(craftExport.endsWith("Total Cards: 60") && !craftExport.contains("["), "Live export has no Craft metadata");
        check(LiveDeckParser.parse(craftExport, labels.keySet()).equals(craftSample), "user's 60-card Craft export round trip");
        rejects(() -> LiveDeckParser.export(Map.of("unknown_card", 60)));
        String manyEnergy = "Pokémon: 0\nTrainer: 0\nEnergy: 2\n40 Psychic Energy SVE 5\n20 Darkness Energy SVE 7";
        check(LiveDeckParser.parse(manyEnergy.replace("\n", ""), labels.keySet()).equals(LiveDeckParser.parse(manyEnergy, labels.keySet())), "two-digit counts survive joined numbers");
        rejects(() -> LiveDeckParser.parse(DRAGAPULT_SAMPLE.replace("4 Dreepy", "5 Dreepy").replace("\n", ""), labels.keySet()));
        rejects(() -> LiveDeckParser.parse(DRAGAPULT_SAMPLE.replace("Dreepy", "Ralts").replace("\n", ""), labels.keySet()));
        var highRarity = LiveDeckParser.parse(HIGH_RARITY_SAMPLE, labels.keySet());
        check(highRarity.size() == 38 && highRarity.values().stream().mapToInt(Integer::intValue).sum() == 60, "complete high-rarity Live export");
        check(highRarity.get("svalt_en_040") == 1 && !highRarity.containsKey("svbsp_en_040"), "SVALT Scream Tail must not become promo Pawmi");
        check(highRarity.get("svbsp_en_124") == 1 && highRarity.get("sv6-5_en_072") == 4
            && highRarity.get("swsh7_en_236") == 2, "promo, high rarity and symbol Energy keep exact printing");
        for (String set : List.of("bwalt", "xyalt", "smalt", "swshalt", "svalt", "mealt", "gum")) {
            String id = labels.keySet().stream().filter(key -> key.startsWith(set + "_en_")).sorted().findFirst().orElseThrow();
            String number = id.substring((set + "_en_").length());
            check(LiveDeckParser.parse("60 Test " + set.toUpperCase(Locale.ROOT) + " " + number, labels.keySet()).containsKey(id), "catalogue set " + set);
        }
        check(plan.size() == 28 && plan.values().stream().mapToInt(Integer::intValue).sum() == 60, "sample resolves all 60 cards");
        check(plan.get("me2-5_en_039") == 1 && plan.get("me5-5_en_072") == 1 && plan.get("sve_en_005") == 7, "new sets and energies");
        check(LiveDeckParser.parse("\uFEFF" + SAMPLE.replace("\n", "\r\n").replace("TWM 95", "twm 095"), labels.keySet()).equals(plan), "clipboard variants");
        check(LiveDeckParser.parse("60 Test BRS 3", labels.keySet()).containsKey("swsh9_en_003"), "main set is not confused with gallery");
        check(LiveDeckParser.parse("60 Test BRS TG03", labels.keySet()).containsKey("swsh9a_en_003"), "trainer gallery");
        check(LiveDeckParser.parse("60 Test CRZ GG01", labels.keySet()).containsKey("swsh12-5a_en_001"), "galarian gallery");
        check(LiveDeckParser.parse("60 Test LTR RC1", labels.keySet()).containsKey("bw11r_en_001"), "radiant collection");
        check(LiveDeckParser.parse("60 Test BRS-TG 3 PH", labels.keySet()).containsKey("swsh9a_en_003"), "Live gallery spelling and foil suffix");
        check(LiveDeckParser.parse("60 Test PR-SV 1", labels.keySet()).containsKey("svbsp_en_001"), "Live promo code");
        rejects(() -> LiveDeckParser.parse(SAMPLE.replace("TWM 95", "UNKNOWN 95"), labels.keySet()));
        rejects(() -> LiveDeckParser.parse(SAMPLE.replace("4 Munkidori", "5 Munkidori"), labels.keySet()));
        rejects(() -> LiveDeckParser.parse(SAMPLE + "\nmalformed", labels.keySet()));
        rejects(() -> LiveDeckParser.parse("x".repeat(16385), labels.keySet()));

        var boxes = new CardBoxService(Clock.systemUTC()); var owner = UUID.randomUUID();
        var box = boxes.create(owner, "Live", "fixture");
        String id = "sv6_en_095", extra = "sve_en_001";
        boxes.put(owner, box.id(), boxes.mint(owner, id).instanceId());
        boxes.put(owner, box.id(), boxes.mint(owner, extra).instanceId());
        boxes.mint(owner, id); boxes.mint(UUID.randomUUID(), id);
        var otherBox = boxes.create(owner, "other", "fixture");
        boxes.put(owner, otherBox.id(), boxes.mint(owner, id).instanceId());
        boxes.observe(owner, List.of("me5-5_en_072"));
        var editor = editor(boxes, owner, box.id(), labels);
        var view = editor.view(owner);
        int stale = view.revision();
        view = editor.submit(owner, view.revision(), "IMPORT_LIVE", SAMPLE, 0, "");
        check(view.totalCards() == 2 && missing(view) == 58, "no mint, foreign/other boxes/discovery excluded");
        check(boxes.unboxedForOwner(owner).stream().anyMatch(c -> c.definitionId().equals(extra)), "extras returned");
        rejects(() -> editor.submit(owner, stale, "IMPORT_LIVE", SAMPLE, 0, ""));
        rejects(() -> boxes.snapshotForMatch(owner, box.id(), UUID.randomUUID()));
        check(!boxes.isLocked(owner, box.id()), "failed start does not lock");
        view = editor.submit(owner, view.revision(), "IMPORT_LIVE", SAMPLE, 0, "");
        check(view.totalCards() == 2 && missing(view) == 58, "reimport conserves copies");
        boxes.mint(owner, id);
        view = editor.submit(owner, view.revision(), "FILL_MISSING", id, 0, "");
        check(view.totalCards() == 3 && missing(view) == 57, "manual fill reduces ghost count");
        var saved = boxes.snapshot();
        var restored = CardBoxService.restore(Clock.systemUTC(), saved);
        check(restored.missingCards(owner, box.id()).equals(boxes.missingCards(owner, box.id())), "restart preserves plan");
        check(missing(editor(restored, owner, box.id(), labels).view(owner)) == 57, "reopen preserves ghosts");
        rejects(() -> boxes.transaction(() -> { boxes.plan(owner, box.id(), Map.of()); throw new IllegalStateException("rollback"); }));
        check(boxes.getOwned(owner, box.id()).plannedCards().equals(plan), "transaction restores plan");
        for (var entry : boxes.missingCards(owner, box.id()).entrySet())
            for (int i = 0; i < entry.getValue(); i++) boxes.put(owner, box.id(), boxes.mint(owner, entry.getKey()).instanceId());
        check(boxes.missingCards(owner, box.id()).isEmpty(), "ordinary manual add satisfies plan");
        UUID match = UUID.randomUUID(); boxes.snapshotForMatch(owner, box.id(), match);
        rejects(() -> editor.submit(owner, stale, "IMPORT_LIVE", SAMPLE, 0, ""));
        boxes.unlock(match);
        view = editor.submit(owner, editor.view(owner).revision(), "EXPORT", "", 0, "");
        check(LiveDeckParser.parse(view.exportText(), labels.keySet()).equals(plan), "built-in copy deck uses Live format");
        System.out.println("LiveDeckImportTest passed: full sample, parsing, ownership, placeholders, replay, persistence, rollback");
    }
    private static void creativeImport(Map<String,String> labels) {
        var boxes=new CardBoxService(Clock.systemUTC());UUID owner=UUID.randomUUID();
        var box=boxes.create(owner,"创造导入","standard-international-2026");
        var session=editor(boxes,owner,box.id(),labels);
        var creative=new java.util.concurrent.atomic.AtomicBoolean(false);
        session.connectCreativeMode(creative::get);
        var view=session.view(owner);
        view=session.submit(owner,view.revision(),"IMPORT_LIVE",SAMPLE,0,"");
        check(view.totalCards()==0&&missing(view)==60,"survival must not create missing cards");
        creative.set(true);int oldRevision=view.revision();
        view=session.submit(owner,oldRevision,"IMPORT_LIVE",SAMPLE,0,"");
        check(view.totalCards()==60&&missing(view)==0&&boxes.snapshot().cards().size()==60,"creative completes real cards");
        check(boxes.contentsForEditing(owner,box.id()).stream().map(CardInstance::instanceId).distinct().count()==60,"creative instance IDs distinct");
        rejects(()->session.submit(owner,oldRevision,"IMPORT_LIVE",SAMPLE,0,""));
        view=session.submit(owner,view.revision(),"IMPORT_LIVE",SAMPLE,0,"");
        check(boxes.snapshot().cards().size()==60,"reimport must not mint surplus copies");
        var restored=CardBoxService.restore(Clock.systemUTC(),boxes.snapshot());
        check(restored.contentsForEditing(owner,box.id()).size()==60,"creative cards persist");
        var other=boxes.create(owner,"模式切换","standard-international-2026");
        var next=editor(boxes,owner,other.id(),labels);next.connectCreativeMode(creative::get);
        var fresh=next.view(owner);creative.set(false);
        fresh=next.submit(owner,fresh.revision(),"IMPORT_LIVE",SAMPLE,0,"");
        check(fresh.totalCards()==0&&boxes.snapshot().cards().size()==60,"mode revocation is checked at import, other box not consumed");
        creative.set(true);
        next.connectTransaction(move->boxes.transaction(()->{move.run();throw new IllegalStateException("injected commit failure");}));
        int revision=fresh.revision();
        rejects(()->next.submit(owner,revision,"IMPORT_LIVE",SAMPLE,0,""));
        check(boxes.snapshot().cards().size()==60&&boxes.contentsForEditing(owner,other.id()).isEmpty(),"failed creative transaction rolled back minted cards");
        rejects(()->session.submit(UUID.randomUUID(),oldRevision,"IMPORT_LIVE",SAMPLE,0,""));
        System.out.println("Creative import passed: mode changes, full real deck, unique identities, no surplus/replay, persistence, rollback and ownership");
    }
    private static BoxEditorSession editor(CardBoxService boxes, UUID owner, UUID box, Map<String, String> labels) {
        return new BoxEditorSession(boxes, new CardDatabase(List.of()), new CombatCatalog(Map.of(), Map.of()), Map.of(), labels, owner, box);
    }
    private static int missing(BoxEditorSession.Snapshot view) { return view.contents().stream().filter(BoxEditorSession.CardRow::missing).mapToInt(BoxEditorSession.CardRow::count).sum(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("expected rejection"); }
}
