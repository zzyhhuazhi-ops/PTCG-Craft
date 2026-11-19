import com.google.gson.GsonBuilder;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.view.*;
import java.nio.file.*;
import java.util.*;

/** Audits actual issued snapshots, not card support flags. Records every failure before failing the run. */
public final class BattleOperationAccessibilityTest {
    static final Path OUT=Path.of(System.getProperty("ptcglive.auditDir","outputs/battle-operation-audit"));
    static long snapshots, options;
    static final Map<ActionType,Long> seen=new TreeMap<>(), missing=new TreeMap<>();
    static final Map<ActionType,Set<String>> affected=new TreeMap<>();
    static final List<Map<String,Object>> examples=new ArrayList<>();
    static final Set<ActionType> saved=new HashSet<>();

    static boolean reachable(MatchSession.Snapshot view,MatchSession.Option option,Set<Integer> flow,Set<String> hand,Set<String> visible) {
        if(flow.contains(option.index()))return true;
        if(Set.of(ActionType.END_TURN,ActionType.CONCEDE,ActionType.PRIZE_PICK).contains(option.type()))return true;
        var keys=option.cardKeys();
        // Replacement is also reachable by dragging the selected Bench card into the vacant Active slot.
        if(option.type()==ActionType.REPLACEMENT&&keys.stream().anyMatch(k->k.startsWith("field:")&&visible.contains(k)))return true;
        // The table supports selecting/paying directly on the effect-card overlay and attachment row.
        if(keys.stream().anyMatch(k->visible.contains(k)&&(k.startsWith("effect:")||k.startsWith("effect-source:")||k.startsWith("tool-overflow:"))))return true;
        if(keys.contains(MatchSession.POKEMON_SELECTION_KEY)||keys.stream().anyMatch(k->k.startsWith("counter:")))
            return keys.stream().anyMatch(k->k.startsWith("field:")&&visible.contains(k));
        if(view.game().effectAttachment()!=null&&keys.stream().anyMatch(k->k.startsWith("field:")&&visible.contains(k)))return true;
        // Hand-card clicks open the issued actions; field cards only expose their card-face actions.
        if(keys.stream().anyMatch(hand::contains))return true;
        return keys.stream().filter(k->k.startsWith("field:")&&visible.contains(k)).anyMatch(k->
            MatchActionMatcher.cardFaceActions(view.options(),k,!view.game().resolvingCardEffect()).contains(option));
    }
    static void inspect(MatchSession.Snapshot view) {
        snapshots++;
        var flow=new HashSet<Integer>();MatchActionMatcher.flowOptions(view).forEach(o->flow.add(o.index()));
        var model=MatchRegionModel.from(view);var visible=new HashSet<String>();model.visibleCards().forEach(c->visible.add(c.key()));
        var hand=new HashSet<String>();view.game().hand().forEach(c->hand.add("hand:"+c.instanceId()));
        for(var option:view.options()) {
            if(!option.enabled()||MatchActionMatcher.isAttackOption(option)&&!MatchActionMatcher.isAvailableAttackOption(option))continue;
            options++;seen.merge(option.type(),1L,Long::sum);
            if(reachable(view,option,flow,hand,visible))continue;
            missing.merge(option.type(),1L,Long::sum);
            affected.computeIfAbsent(option.type(),k->new TreeSet<>()).add(AiCardOperationTest.observedCard);
            if(saved.add(option.type())) {
                examples.add(Map.of("card",AiCardOperationTest.observedCard,"operation",AiCardOperationTest.observedOperation,
                    "type",option.type(),"label",option.labelZh(),"keys",option.cardKeys(),"index",option.index(),"phase",view.game().phase()));
                try{Files.writeString(OUT.resolve(option.type()+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(view));}
                catch(Exception e){throw new IllegalStateException(e);}
            }
        }
    }
    public static void main(String[] args)throws Exception {
        Files.createDirectories(OUT);
        System.setProperty("ptcglive.allOperations","true");
        System.setProperty("ptcglive.allDifficulties","true");
        if(System.getProperty("ptcglive.scopeFile","").isEmpty())System.setProperty("ptcglive.scopeFile","tests/fixtures/battle-operation-scope.json");
        AiCardOperationTest.snapshotObserver=BattleOperationAccessibilityTest::inspect;
        Throwable failure=null;
        try{AiCardOperationTest.main(args);}catch(Exception|AssertionError e){failure=e;}
        finally {
            var report=new LinkedHashMap<String,Object>();
            report.put("scope","SV+ Pokemon and all non-Pokemon; synthetic qualified boards; actual server-issued initial/continuation options. This is not all card combinations or full-game coverage.");
            report.put("snapshots",snapshots);report.put("enabledOptions",options);report.put("seen",seen);
            report.put("unreachable",missing);report.put("affectedFixtureCards",affected);report.put("examples",examples);
            report.put("operationAuditFailure",failure==null?null:failure.toString());
            Files.writeString(OUT.resolve("accessibility.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.copy(Path.of("outputs/ai-all-operations.json"),OUT.resolve("operations.json"),StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("BattleOperationAccessibilityTest: snapshots="+snapshots+", options="+options+", unreachable="+missing);
        if(failure!=null)throw new AssertionError("Operation audit failed",failure);
        if(!missing.isEmpty())throw new AssertionError("Issued actions lack a player route: "+missing.keySet());
        System.out.println("BattleOperationAccessibilityTest passed");
    }
}
