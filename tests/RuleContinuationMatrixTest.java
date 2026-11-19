import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntFunction;

/** Alternate issued-action paths on real attack programs and explicit synthetic composition probes. */
public final class RuleContinuationMatrixTest {
    static final CombatCatalog DATA=BundledCombatData.load();
    static final Path OUT=Path.of(System.getProperty("ptcglive.auditDir","outputs/rule-continuation-audit"));
    record Scenario(String name,String attack,IntFunction<ServerMatch> create) { }
    static final List<Map<String,Object>> results=new ArrayList<>();
    static final Map<ActionType,Integer> coverage=new TreeMap<>();
    static final List<String> trace=new ArrayList<>();
    static int submissions;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AttackDefinition attack(String id,int slot){return DATA.pokemon(id).orElseThrow().attacks().get(slot);}
    static List<Scenario> scenarios(){
        var list=new ArrayList<Scenario>();
        for(String top:List.of(SlowkingOperationTest.DONOR,"energy","sv2_en_086"))
            list.add(new Scenario("Slowking/top/"+top,"seek-inspiration",seed->SlowkingOperationTest.fixture(top)));
        for(String id:List.of("sv10_en_087","sv9_en_098")) {
            var a=ModernCopyRepairTest.declared(id);
            list.add(new Scenario(id,a.id(),seed->{var m=ModernCopyRepairTest.game(a,true,seed);ModernCopyRepairTest.pay(m);return m;}));
        }
        // Different optional, whole-window, opponent choice, switching and delayed-discard programs.
        for(String id:List.of("sv4-5_en_031","sv3_en_179","sv7_en_096","sv4-5_en_020","sv6_en_068","sv8_en_061","sv3_en_069")) {
            var a=attack(id,0);list.add(new Scenario(id,a.id(),seed->AiCardOperationTest.attackGame(a,List.of())));
        }
        var hypno=attack("sv1_en_083",0);
        list.add(new Scenario("Hypno/coin-copy",hypno.id(),seed->ModernWindowDamageRepairTest.game(hypno,seed,false,List.of(GeneratedRuleBatch8Test.hit(40,false)))));
        for(String supporter:List.of("sv6_en_144","sv8-5_en_093")) {
            var mime=attack("sv5_en_063",0);
            list.add(new Scenario("MrMime/copy/"+supporter,mime.id(),seed->{
                var m=ModernEnergyRepairTest.game(DATA.trainer(supporter).orElseThrow(),List.of(),mime,EnergyType.PSYCHIC,seed);
                var p=m.currentPlayer();var other=m.clientView(null).players().stream().map(MatchClientView.Player::id).filter(id->!id.equals(p)).findFirst().orElseThrow();
                m.drawCardsByEffect(p,other,24);
                ModernRuleRepair2Test.pay(m,p,m.activePokemonView(p).pokemonId(),"energy",1);return m;
            }));
        }
        for(var spec:List.of(Map.entry("sv3-5_en_012",1),Map.entry("sv2_en_018",0))) {
            var a=attack(spec.getKey(),spec.getValue());
            list.add(new Scenario(spec.getKey()+"/whole-stack",a.id(),seed->{
                var m=ModernShuffleScopeRepairTest.game(a,seed);var seats=m.clientView(null).players().stream().map(MatchClientView.Player::id).toList();
                for(var seat:seats){ModernJointDepartureRepairTest.dress(m,seat,m.activePokemonView(seat).pokemonId());m.endTurn(seat);}return m;
            }));
        }
        String search="{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0}";
        String counters="{\"type\":\"damage_counter\",\"amount\":2,\"distribution\":\"EXACT\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"target_scope\":\"OPPONENT_FIELD\",\"min_targets\":1,\"max_targets\":2}";
        var nested=ResumableAttackTest.attack(search+","+counters+",{\"type\":\"draw\",\"amount\":1}");
        list.add(new Scenario("synthetic/search-counters-draw",nested.id(),seed->ResumableAttackTest.fixture(nested)));
        list.add(new Scenario("synthetic/copied-search-counters-draw","copy",seed->{var m=CopyAttackTest.fixture(CopyAttackTest.copier(),nested,EnergyType.PSYCHIC,seed);CopyAttackTest.pay(m);return m;}));
        list.add(new Scenario("synthetic/KO-awaits-search",nested.id(),seed->{
            var m=ResumableAttackTest.fixture(nested);var seats=m.clientView(null).players().stream().map(MatchClientView.Player::id).toList();
            m.placeDamageCountersByEffect(m.currentPlayer(),seats.getLast(),m.activePokemonView(seats.getLast()).pokemonId(),29);return m;
        }));
        for(var energies:List.of(List.of("fire","fire","water","lightning"),List.of("double","water","lightning")))
            list.add(new Scenario("me5-5_en_121/payment/"+String.join("-",energies),GeneratedRuleBatch49Test.blast().id(),seed->{
                var m=GeneratedRuleBatch49Test.fixture();GeneratedRuleBatch49Test.attach(m,energies.toArray(String[]::new));return m;
            }));
        return list;
    }
    static List<UUID> seats(ServerMatch m){return m.clientView(null).players().stream().map(MatchClientView.Player::id).toList();}
    static Map<UUID,Integer> physicalCards(ServerMatch m){
        var count=new HashMap<UUID,Integer>();var field=EventTargetBindingTest.field(m);
        java.util.function.Consumer<CardInstance> add=c->count.merge(c.instanceId(),1,Integer::sum);
        for(UUID p:seats(m)) {
            var v=m.privateView(p);for(var zone:List.of(v.hand(),v.deck(),v.prizes(),v.discard(),v.lostZone()))zone.forEach(add);
            var pokemon=new ArrayList<>(m.benchPokemonView(p));if(m.activePokemonView(p)!=null)pokemon.add(m.activePokemonView(p));
            for(var host:pokemon){add.accept(field.topCard(p,host.pokemonId()));field.previousEvolutionCards(p,host.pokemonId()).forEach(add);
                field.attachedEnergy(p,host.pokemonId()).forEach(add);host.attachedTools().forEach(add);}
        }
        check(count.values().stream().allMatch(n->n==1),"A physical card exists in multiple zones");return count;
    }
    static boolean settled(ServerMatch m){return m.result()!=null||m.phase()==MatchPhase.TURN&&!m.hasPendingEffectChoice()
        &&!m.hasPendingTurnDraw()&&m.triggerOrderPrompt()==null&&m.toolOverflowPrompt()==null;}
    static void entry(MatchSession.Snapshot view,MatchSession.Option option){
        var flow=new HashSet<Integer>();MatchActionMatcher.flowOptions(view).forEach(o->flow.add(o.index()));
        var visible=new HashSet<String>();MatchRegionModel.from(view).visibleCards().forEach(c->visible.add(c.key()));
        var hand=new HashSet<String>();view.game().hand().forEach(c->hand.add("hand:"+c.instanceId()));
        check(BattleOperationAccessibilityTest.reachable(view,option,flow,hand,visible),"No player route: "+option.type()+" "+option.labelZh());
    }
    static void submit(MatchSession session,UUID seat,MatchSession.Snapshot view,MatchSession.Option option){
        var m=session.match();entry(view,option);trace.add(seat+" T"+m.turnNumber()+" "+option.type()+" "+option.labelZh());
        session.submit(seat,seat,view.revision(),option.index());submissions++;coverage.merge(option.type(),1,Integer::sum);
        var a=m.clientView(seat);var random=m.randomTranscript();var privateViews=seats(m).stream().map(m::privateView).toList();
        boolean rejected=false;try{session.submit(seat,seat,view.revision(),option.index());}catch(IllegalStateException|IllegalArgumentException expected){rejected=true;}
        check(rejected,"Repeated option was accepted");
        check(a.equals(m.clientView(seat))&&random.equals(m.randomTranscript())&&privateViews.equals(seats(m).stream().map(m::privateView).toList()),"Rejected repeat mutated match");
    }
    static int rank(MatchSession.Option o,boolean maximum){
        return switch(o.type()) {
            case CONCEDE, CARD_DESELECT, RETREAT_CANCEL -> 100;
            case ACK_REVEAL -> 0;
            case COPY_SKIP, TRIGGER_SKIP, SWAP_SKIP -> maximum?80:0;
            case CARD_CONFIRM, DAMAGE_CONFIRM, ENERGY_CONFIRM, TARGET_FINISH -> maximum?30:0;
            case CARD_SELECT, TARGET_SELECT, ENERGY_ASSIGN -> 10;
            default -> o.cardKeys().contains("counter:remove")?100:15;
        };
    }
    static void settle(MatchSession session,String policy,int seed){
        var m=session.match();
        for(int step=0;step<200;step++) {
            if(settled(m))return;
            boolean moved=false;
            for(UUID p:seats(m)) {
                var input=AiPracticeScenarios.view(session,p);var view=input.snapshot();var rng=m.randomTranscript();
                MatchSession.Option option;
                if(policy.startsWith("HUMAN")) {
                    boolean maximum=policy.equals("HUMAN_MAX");var offered=view.options().stream().filter(o->o.enabled()&&rank(o,maximum)<100).toList();
                    if(offered.isEmpty())continue;
                    int best=offered.stream().mapToInt(o->rank(o,maximum)).min().orElseThrow();
                    var eligible=offered.stream().filter(o->rank(o,maximum)==best).toList();
                    option=eligible.get(Math.floorMod(seed+step+(maximum?eligible.size()-1:0),eligible.size()));
                } else {
                    var decision=AiPolicy.choose(input,seed*1000L+step,Set.of(),AiDifficulty.valueOf(policy));if(decision.isEmpty())continue;
                    option=view.options().stream().filter(o->o.index()==decision.get().option()).findFirst().orElseThrow();
                }
                check(rng.equals(m.randomTranscript()),"Choosing an action consumed game randomness");
                submit(session,p,view,option);moved=true;break;
            }
            check(moved,"No continuation at "+m.phase());
        }
        throw new AssertionError("Continuation exceeded 200 actions");
    }
    static void run(Scenario scenario,String policy,int seed){
        trace.clear();int before=submissions;
        try {
            var m=scenario.create().apply(seed);var ids=seats(m);UUID actor=m.currentPlayer();int turn=m.turnNumber();
            var original=physicalCards(m);check(original.size()==120,"Fixture is not two physical 60-card decks");
            var session=MatchSession.multiplayer(m,ids.getFirst(),ids.getLast());var view=session.view(actor,actor);
            var offered=view.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK)
                .filter(o->AiCardOperationTest.matchesDeclaredOperation(m,actor,scenario.attack(),o)).toList();
            check(!offered.isEmpty(),"Fixture has no issued declaration for "+scenario.attack());
            submit(session,actor,view,offered.get(Math.floorMod(seed,offered.size())));settle(session,policy,seed);
            check(m.result()!=null||m.turnNumber()==turn+1&&!m.currentPlayer().equals(actor),"Attack did not advance exactly one turn");
            check(original.equals(physicalCards(m)),"Cards were lost or created during continuation");
            for(int cycle=0;cycle<2&&m.result()==null;cycle++) {
                var next=m.currentPlayer();var nextView=session.view(next,next);
                var end=nextView.options().stream().filter(o->o.enabled()&&o.type()==ActionType.END_TURN).findFirst().orElseThrow();
                int prior=m.turnNumber();submit(session,next,nextView,end);settle(session,policy,seed+cycle+1);
                check(m.result()!=null||m.turnNumber()==prior+1,"Following turn did not advance exactly once");
                check(original.equals(physicalCards(m)),"Following turn lost or created physical cards");
            }
            results.add(Map.of("scenario",scenario.name(),"policy",policy,"seed",seed,"passed",true,"actions",submissions-before));
        }catch(Exception|AssertionError e){
            results.add(Map.of("scenario",scenario.name(),"policy",policy,"seed",seed,"passed",false,"error",e.toString(),"trace",List.copyOf(trace)));
        }
    }
    public static void main(String[] args)throws Exception {
        for(var scenario:scenarios())for(String policy:List.of("EASY","NORMAL","HARD","MASTER","HUMAN_MIN","HUMAN_MAX"))for(int seed:List.of(0,7,19))run(scenario,policy,seed);
        Files.createDirectories(OUT);long failed=results.stream().filter(r->!r.get("passed").equals(true)).count();
        var report=Map.of("cases",results.size(),"failed",failed,"submittedActions",submissions,"actionCoverage",coverage,"results",results,
            "scope","Real modern attack programs on qualified synthetic boards, composed copy probes, two subsequent turns, six selection policies, physical-card conservation and stale-action atomicity. Not all card combinations or a full client input test.");
        Files.writeString(OUT.resolve("continuation-matrix.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("RuleContinuationMatrixTest: cases="+results.size()+", actions="+submissions+", failed="+failed);
        check(failed==0,"Continuation matrix failed: "+OUT.resolve("continuation-matrix.json"));
        System.out.println("RuleContinuationMatrixTest passed");
    }
}
