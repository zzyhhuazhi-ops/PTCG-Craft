import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
import java.nio.file.*;

/** Actual printed operation programs on prepared synthetic boards; not a full-card/full-match coverage claim. */
public final class AiCardOperationTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<Map<String,Object>> rows=new ArrayList<>();
    static java.util.function.Consumer<MatchSession.Snapshot> snapshotObserver = snapshot -> { };
    static String observedCard="", observedOperation="";
    static final Set<String> CATALOG=dev.hua.ptcglive.CardCatalog.load().stream().map(dev.hua.ptcglive.CardCatalog.Entry::id).collect(java.util.stream.Collectors.toSet());
    static final Set<String> FILTER=Set.of(System.getProperty("ptcglive.cardFilter","").split(","));
    static final Set<String> SCOPE=scope();
    static Set<String> scope(){String path=System.getProperty("ptcglive.scopeFile","");if(path.isEmpty())return CATALOG;try{var result=new HashSet<String>();for(var id:com.google.gson.JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonArray())result.add(id.getAsString());return Set.copyOf(result);}catch(Exception e){throw new IllegalArgumentException("Invalid operation-test scope",e);}}
    static boolean included(String id){return CATALOG.contains(id)&&SCOPE.contains(id)&&(FILTER.contains("")||FILTER.contains(id));}
    static final Set<String> FESTIVALS=Set.of("bwbsp_en_095","smbsp_en_078","smbsp_en_148","smbsp_en_231","swshbsp_en_296","xybsp_en_027","xybsp_en_091","xybsp_en_176");
    static final Set<String> SPECIAL=Set.of("split_basic_energy_search","top_distinct_category_search","copy_attack","copy_attack_from_own_deck_top","borrow_attacks","use_supporter_effect","use_top_supporter","use_opponent_hand_supporter");
    static boolean relevant(EffectDefinition e){return SPECIAL.contains(e.type())||e.type().contains("copy_attack")||e.arguments().containsKey("distinct_energy_types")||e.arguments().containsKey("one_per_target")||e.arguments().containsKey("energy_types")||e.effects().stream().anyMatch(AiCardOperationTest::relevant);}
    static boolean relevant(EffectProgram p){return p.triggers().stream().flatMap(t->t.effects().stream()).anyMatch(AiCardOperationTest::relevant);}
    public static void main(String[] args)throws Exception {
        var data=BundledCombatData.load();
        boolean all=Boolean.getBoolean("ptcglive.allOperations");
        // Release scans explicitly exercise every policy without a huge command-line card list.
        for(var level:all&&FILTER.contains("")&&!Boolean.getBoolean("ptcglive.allDifficulties")?new AiDifficulty[]{AiDifficulty.NORMAL}:AiDifficulty.values()) {
            System.out.println("Operation difficulty: "+level);
            typed(level);distinct(level);
            for(String id:List.of("sv10_en_087","sv9_en_098","sv9_en_175","sv9_en_185","sv9_en_189")) {
                var m=ModernCopyRepairTest.game(ModernCopyRepairTest.declared(id),true,7);ModernCopyRepairTest.pay(m);
                operate(id,"qualified-copy-source",m,level,ActionType.ATTACK);
            }
            for(String id:List.of("sv4_en_170","sv6-5_en_059")) {
                var m=ModernSupporterEnergyRepairTest.game(id);var owner=m.currentPlayer();
                var energy=m.privateView(owner).hand().stream().filter(c->c.definitionId().equals("energy")).limit(2).map(CardInstance::instanceId).toList();
                m.discardHandCardsByEffect(owner,owner,energy);
                operate(id,"distinct-hosts",m,level,ActionType.TRAINER);
            }
            for(var entry:new TreeMap<>(data.trainerProfiles()).entrySet())if(included(entry.getKey())&&(all||relevant(entry.getValue().effectProgram()))) {
                try {
                    var p=entry.getValue();
                    if(p.effect().opcode()==TrainerEffect.Opcode.SETUP_FIRST_TICKET) {
                        NonPokemonBatch131Test.inactiveAi(level);
                        record(entry.getKey(),"current-setup-no-activation",level,"RULE_INACTIVE_MATCH_COMPLETED","Coin/order precedes opening hand; First Ticket has no legal activation. AI completed a match with it in the deck.");continue;
                    }
                    if(p.pairedPlay()!=null)p=new TrainerProfile(p.category(),p.effect(),p.effectProgram(),p.automaticEffects(),p.endsTurn(),p.mayPlayFirstTurn(),p.inPlayPokemon(),p.activation(),p.toolActivation(),new PairedTrainerPlay(p.pairedPlay().count(),Set.of("trainer"),p.pairedPlay().program()));
                    var energy=entry.getKey().equals("swsh3-5_en_066")?EnergyType.PSYCHIC:EnergyType.GRASS;
                    var m=GeneratedRuleBatch8Test.fixture(List.of(),energy,entry.getKey().equals("sm6_en_105")?EnergyType.FAIRY:EnergyType.GRASS,p,GeneratedRuleBatch8Test.hit(10,false),1,Set.of(),entry.getKey().equals("swsh8_en_225"));
                    if(NonPokemonBatch33Test.IDS.contains(entry.getKey()))m=NonPokemonBatch33Test.game(entry.getKey(),false,false);
                    if(NonPokemonBatch37Test.IDS.contains(entry.getKey()))m=NonPokemonBatch37Test.aiGame(entry.getKey());
                    if(NonPokemonBatch38Test.IDS.contains(entry.getKey()))m=NonPokemonBatch38Test.aiGame(entry.getKey());
                    if(NonPokemonBatch39Test.IDS.contains(entry.getKey()))m=NonPokemonBatch39Test.aiGame(entry.getKey());
                    if(NonPokemonBatch40Test.IDS.contains(entry.getKey())||NonPokemonBatch40Test.VACUUMS.contains(entry.getKey()))m=NonPokemonBatch40Test.aiGame(entry.getKey());
                    if(NonPokemonBatch41Test.IDS.contains(entry.getKey()))m=NonPokemonBatch41Test.aiGame(entry.getKey());
                    if(NonPokemonBatch42Test.IDS.contains(entry.getKey()))m=NonPokemonBatch42Test.aiGame(entry.getKey());
                    if(NonPokemonCnPrintingTest.SOURCES.containsKey(entry.getKey()))m=NonPokemonCnPrintingTest.game(entry.getKey());
                    if(NonPokemonBatch43Test.AREAS.contains(entry.getKey()))m=NonPokemonBatch43Test.areaGame(entry.getKey());
                    if(NonPokemonBatch44Test.IDS.contains(entry.getKey()))m=NonPokemonBatch44Test.aiGame(entry.getKey());
                    if(NonPokemonBatch45Test.IDS.contains(entry.getKey()))m=NonPokemonBatch45Test.aiGame(entry.getKey());
                    if(NonPokemonBatch46Test.IDS.contains(entry.getKey()))m=NonPokemonBatch46Test.aiGame(entry.getKey());
                    if(NonPokemonBatch47Test.IDS.contains(entry.getKey()))m=NonPokemonBatch47Test.aiGame(entry.getKey());
                    if(NonPokemonBatch48Test.IDS.contains(entry.getKey()))m=NonPokemonBatch48Test.aiGame(entry.getKey());
                    if(NonPokemonBatch49Test.IDS.contains(entry.getKey()))m=NonPokemonBatch49Test.aiGame(entry.getKey());
                    if(NonPokemonBatch50Test.IDS.contains(entry.getKey()))m=NonPokemonBatch50Test.aiGame(entry.getKey());
                    if(NonPokemonBatch51Test.IDS.contains(entry.getKey()))m=NonPokemonBatch51Test.aiGame(entry.getKey());
                    if(NonPokemonBatch132Test.IDS.contains(entry.getKey()))m=NonPokemonBatch132Test.aiGame(entry.getKey());
                    if(NonPokemonBatch133Test.IDS.contains(entry.getKey()))m=NonPokemonBatch133Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch130Test.IDS.contains(entry.getKey()))m=NonPokemonBatch130Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch128Test.IDS.contains(entry.getKey()))m=NonPokemonBatch128Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch127Test.IDS.contains(entry.getKey()))m=NonPokemonBatch127Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch126Test.IDS.contains(entry.getKey()))m=NonPokemonBatch126Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch125Test.IDS.contains(entry.getKey()))m=NonPokemonBatch125Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch124Test.IDS.contains(entry.getKey()))m=NonPokemonBatch124Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch122Test.IDS.contains(entry.getKey()))m=NonPokemonBatch122Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch121Test.IDS.contains(entry.getKey()))m=NonPokemonBatch121Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch120Test.IDS.contains(entry.getKey()))m=NonPokemonBatch120Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch119Test.IDS.contains(entry.getKey()))m=NonPokemonBatch119Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch118Test.IDS.contains(entry.getKey()))m=NonPokemonBatch118Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch117Test.IDS.contains(entry.getKey()))m=NonPokemonBatch117Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch116Test.ALL_IDS.contains(entry.getKey()))m=NonPokemonBatch116Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch115Test.IDS.contains(entry.getKey()))m=NonPokemonBatch115Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch114Test.IDS.contains(entry.getKey()))m=NonPokemonBatch114Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch113Test.IDS.contains(entry.getKey()))m=NonPokemonBatch113Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch112Test.IDS.contains(entry.getKey()))m=NonPokemonBatch112Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch111Test.IDS.contains(entry.getKey()))m=NonPokemonBatch111Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch110Test.IDS.contains(entry.getKey()))m=NonPokemonBatch110Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch109Test.TOOLS.contains(entry.getKey()))m=NonPokemonBatch109Test.aiGame(entry.getKey());
                    if(NonPokemonBatch108Test.IDS.contains(entry.getKey()))m=NonPokemonBatch108Test.aiGame(entry.getKey());
                    if(NonPokemonBatch107Test.IDS.contains(entry.getKey()))m=NonPokemonBatch107Test.aiGame(entry.getKey());
                    if(NonPokemonBatch106Test.IDS.contains(entry.getKey()))m=NonPokemonBatch106Test.aiGame(entry.getKey());
                    if(NonPokemonBatch105Test.IDS.contains(entry.getKey()))m=NonPokemonBatch105Test.aiGame(entry.getKey());
                    if(NonPokemonBatch104Test.IDS.contains(entry.getKey()))m=NonPokemonBatch104Test.aiGame(entry.getKey());
                    if(NonPokemonBatch103Test.TOOLS.contains(entry.getKey()))m=NonPokemonBatch103Test.aiGame(entry.getKey());
                    if(NonPokemonBatch102Test.IDS.contains(entry.getKey()))m=NonPokemonBatch102Test.aiGame(entry.getKey());
                    if(NonPokemonBatch101Test.IDS.contains(entry.getKey()))m=NonPokemonBatch101Test.aiGame(entry.getKey());
                    if(NonPokemonBatch100Test.IDS.contains(entry.getKey()))m=NonPokemonBatch100Test.aiGame(entry.getKey());
                    if(NonPokemonBatch99Test.IDS.contains(entry.getKey()))m=NonPokemonBatch99Test.aiGame(entry.getKey());
                    if(NonPokemonBatch98Test.IDS.contains(entry.getKey()))m=NonPokemonBatch98Test.aiGame(entry.getKey());
                    if(NonPokemonBatch97Test.IDS.contains(entry.getKey()))m=NonPokemonBatch97Test.aiGame(entry.getKey());
                    if(NonPokemonBatch96Test.IDS.contains(entry.getKey()))m=NonPokemonBatch96Test.aiGame(entry.getKey());
                    if(NonPokemonBatch95Test.IDS.contains(entry.getKey()))m=NonPokemonBatch95Test.aiGame(entry.getKey());
                    if(NonPokemonBatch94Test.IDS.contains(entry.getKey()))m=NonPokemonBatch94Test.aiGame(entry.getKey());
                    if(NonPokemonBatch93Test.IDS.contains(entry.getKey()))m=NonPokemonBatch93Test.aiGame(entry.getKey());
                    if(NonPokemonBatch92Test.IDS.contains(entry.getKey()))m=NonPokemonBatch92Test.aiGame(entry.getKey());
                    if(NonPokemonBatch91Test.IDS.contains(entry.getKey()))m=NonPokemonBatch91Test.aiGame(entry.getKey());
                    if(NonPokemonBatch90Test.IDS.contains(entry.getKey()))m=NonPokemonBatch90Test.aiGame(entry.getKey());
                    if(NonPokemonBatch89Test.IDS.contains(entry.getKey()))m=NonPokemonBatch89Test.aiGame(entry.getKey());
                    if(NonPokemonBatch88Test.IDS.contains(entry.getKey()))m=NonPokemonBatch88Test.aiGame(entry.getKey());
                    if(NonPokemonBatch87Test.IDS.contains(entry.getKey()))m=NonPokemonBatch87Test.aiGame(entry.getKey());
                    if(NonPokemonBatch86Test.IDS.contains(entry.getKey()))m=NonPokemonBatch86Test.aiGame(entry.getKey());
                    if(NonPokemonBatch85Test.IDS.contains(entry.getKey()))m=NonPokemonBatch85Test.aiGame(entry.getKey());
                    if(NonPokemonBatch84Test.IDS.contains(entry.getKey()))m=NonPokemonBatch84Test.aiGame(entry.getKey());
                    if(NonPokemonBatch83Test.IDS.contains(entry.getKey()))m=NonPokemonBatch83Test.aiGame(entry.getKey());
                    if(NonPokemonBatch82Test.IDS.contains(entry.getKey()))m=NonPokemonBatch82Test.aiGame(entry.getKey());
                    if(NonPokemonBatch81Test.IDS.contains(entry.getKey()))m=NonPokemonBatch81Test.aiGame(entry.getKey());
                    if(NonPokemonBatch80Test.IDS.contains(entry.getKey()))m=NonPokemonBatch80Test.aiGame(entry.getKey());
                    if(NonPokemonBatch79Test.IDS.contains(entry.getKey()))m=NonPokemonBatch79Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch78Test.IDS.contains(entry.getKey()))m=NonPokemonBatch78Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch77Test.IDS.contains(entry.getKey()))m=NonPokemonBatch77Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch76Test.IDS.contains(entry.getKey()))m=NonPokemonBatch76Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch75Test.IDS.contains(entry.getKey()))m=NonPokemonBatch75Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch74Test.IDS.contains(entry.getKey()))m=NonPokemonBatch74Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch73Test.IDS.contains(entry.getKey()))m=NonPokemonBatch73Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch72Test.IDS.contains(entry.getKey()))m=NonPokemonBatch72Test.aiGame(entry.getKey());
                    if(NonPokemonBatch71Test.IDS.contains(entry.getKey()))m=NonPokemonBatch71Test.aiGame(entry.getKey());
                    if(NonPokemonBatch70Test.IDS.contains(entry.getKey()))m=NonPokemonBatch70Test.aiGame(entry.getKey());
                    if(NonPokemonBatch69Test.IDS.contains(entry.getKey()))m=NonPokemonBatch69Test.aiGame(entry.getKey());
                    if(NonPokemonBatch68Test.IDS.contains(entry.getKey()))m=NonPokemonBatch68Test.aiGame(entry.getKey());
                    if(NonPokemonBatch67Test.IDS.contains(entry.getKey()))m=NonPokemonBatch67Test.aiGame(entry.getKey());
                    if(NonPokemonBatch66Test.IDS.contains(entry.getKey()))m=NonPokemonBatch66Test.aiGame(entry.getKey());
                    if(NonPokemonBatch65Test.IDS.contains(entry.getKey()))m=NonPokemonBatch65Test.aiGame(entry.getKey());
                    if(NonPokemonBatch64Test.IDS.contains(entry.getKey()))m=NonPokemonBatch64Test.aiGame(entry.getKey());
                    if(NonPokemonBatch63Test.IDS.contains(entry.getKey()))m=NonPokemonBatch63Test.aiGame(entry.getKey());
                    if(NonPokemonBatch62Test.IDS.contains(entry.getKey()))m=NonPokemonBatch62Test.aiGame(entry.getKey());
                    if(NonPokemonBatch61Test.IDS.contains(entry.getKey()))m=NonPokemonBatch61Test.aiGame(entry.getKey());
                    if(NonPokemonBatch60Test.IDS.contains(entry.getKey()))m=NonPokemonBatch60Test.aiGame(entry.getKey());
                    if(NonPokemonBatch59Test.IDS.contains(entry.getKey()))m=NonPokemonBatch59Test.aiGame(entry.getKey());
                    if(NonPokemonBatch57Test.IDS.contains(entry.getKey()))m=NonPokemonBatch57Test.aiGame(entry.getKey());
                    if(NonPokemonBatch56Test.IDS.contains(entry.getKey()))m=NonPokemonBatch56Test.aiGame(entry.getKey());
                    if(NonPokemonBatch55Test.IDS.contains(entry.getKey()))m=NonPokemonBatch55Test.aiGame(entry.getKey());
                    if(NonPokemonBatch54Test.IDS.contains(entry.getKey()))m=NonPokemonBatch54Test.aiGame(entry.getKey());
                    if(NonPokemonBatch53Test.IDS.contains(entry.getKey()))m=NonPokemonBatch53Test.aiGame(entry.getKey());
                    if(NonPokemonBatch52Test.IDS.contains(entry.getKey()))m=NonPokemonBatch52Test.aiGame(entry.getKey());
                    if(entry.getKey().equals("sm7-5_en_061"))m=NonPokemonBatch34Test.lanceGame();
                    if(entry.getKey().equals("sm6_en_114")) {
                        var cfg=NonPokemonBatch34Test.config(entry.getKey());cfg.ownFamilies=Set.of(PokemonFamily.ULTRA_BEAST);m=GeneratedRuleBatch28Test.fixture(cfg);
                    }
                    if(entry.getKey().equals("xy12_en_084")) {
                        UUID keep=DynamicPassiveModifierTest.hand(m,A,"trainer").instanceId();
                        m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.instanceId().equals(keep)).map(CardInstance::instanceId).toList());
                    }
                    if(p.ongoingProgram().triggers().stream().flatMap(t->t.effects().stream()).anyMatch(NonPokemonBatch29Test::link))
                        m=NonPokemonBatch29Test.game(p,p.ongoingProgram().triggers().getFirst().effects().getFirst().conditions().getFirst().arguments().get("value"),true);
                    if(p.effectProgram().triggers().stream().flatMap(t->t.effects().stream()).anyMatch(TrainerDeckEvolution::handles))
                        m=NonPokemonBatch32Test.game(entry.getKey(),false,false,"Mismagius-GX");
                    if(Set.of("swsh3_en_161","xy5_en_136").contains(entry.getKey())) {
                        m=NonPokemonBatch31Test.namedGame(entry.getKey());
                        if(entry.getKey().equals("swsh3_en_161"))m.discardHandCardsByEffect(A,A,List.of(DynamicPassiveModifierTest.hand(m,A,"attacker").instanceId()));
                    }
                    if(Set.of("xy3_en_101","xy2_en_097").contains(entry.getKey())) {
                        UUID owner=entry.getKey().equals("xy3_en_101")?A:B;
                        for(UUID host:List.of(m.activePokemonView(owner).pokemonId(),m.benchPokemonView(owner).getFirst().pokemonId()))
                            m.attachToolByEffect(A,owner,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,owner,"tool").instanceId(),owner,host);
                    }
                    if(entry.getKey().equals("xybsp_en_177"))for(UUID owner:List.of(A,B))m.discardHandCardsByEffect(A,owner,List.of(DynamicPassiveModifierTest.hand(m,owner,"basic").instanceId()));
                    if(Set.of("swsh7_en_163","swsh7_en_233").contains(entry.getKey())) {
                        var bench=m.benchPokemonView(B).getFirst();m.placeDamageCountersByEffect(A,B,bench.pokemonId(),(bench.hitPoints()-50)/10);
                    }
                    if(entry.getKey().equals("swsh8_en_231"))m.discardHandCardsByEffect(A,A,List.of(DynamicPassiveModifierTest.hand(m,A,"basic").instanceId()));
                    if(Set.of("sm10_en_187","xy8_en_135").contains(entry.getKey())) {
                        m.discardHandCardsByEffect(A,B,List.of(DynamicPassiveModifierTest.hand(m,B,"basic").instanceId()));
                        if(entry.getKey().equals("xy8_en_135"))m.discardHandCardsByEffect(A,A,List.of(DynamicPassiveModifierTest.hand(m,A,"basic").instanceId()));
                    }
                    if(Set.of("xy5_en_141","xy7_en_069","sm6_en_105").contains(entry.getKey())) {
                        m.endTurn(A);m.knockOutPokemonByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());UnrestrictedAttackPrizeTest.take(m,B);m.endTurn(B);
                    }
                    if(Set.of("swsh1_en_166","swsh3-5_en_054","swsh3-5_en_066").contains(entry.getKey())) {
                        var host=m.activePokemonView(A).pokemonId();m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"special").instanceId(),host);m.placeDamageCountersByEffect(A,A,host,20);
                    }
                    if(Set.of("swsh5_en_127","swsh6_en_226").contains(entry.getKey()))m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,B,"special").instanceId(),B,m.benchPokemonView(B).getFirst().pokemonId());
                    if(m.phase()==MatchPhase.TURN&&!m.hasPendingEffectChoice()&&!m.hasPendingTurnDraw()&&!initialAction(m,ActionType.TRAINER))preparePublicResources(m);
                    operate(entry.getKey(),"trainer",m,level,m.phase()==MatchPhase.PRIZE_SELECTION?ActionType.CHOICE:m.hasPendingDrawnTrainer()?ActionType.LUCKY_USE:ActionType.TRAINER);
                    if(NonPokemonBatch64Test.IDS.contains(entry.getKey()))operate(entry.getKey(),"retreat-energy-return",NonPokemonBatch64Test.retreatGame(entry.getKey()),level,ActionType.RETREAT);
                    if(NonPokemonBatch62Test.IDS.contains(entry.getKey()))operate(entry.getKey(),"printed-departure",NonPokemonBatch62Test.departureGame(entry.getKey()),level,entry.getKey().equals(NonPokemonBatch62Test.IDS.getFirst())?ActionType.FOSSIL_DISCARD:ActionType.DOLL_RETURN);
                    if(NonPokemonBatch103Test.TOOLS.contains(entry.getKey())) {
                        var attackMatch=NonPokemonBatch103Test.aiGame(entry.getKey());NonPokemonBatch98Test.equip(attackMatch,A);
                        operate(entry.getKey(),"tool-attack",attackMatch,level,ActionType.ATTACK);
                    }
                    if(NonPokemonBatch98Test.IDS.contains(entry.getKey())) {
                        var attackMatch=NonPokemonBatch98Test.aiGame(entry.getKey());NonPokemonBatch98Test.equip(attackMatch,A);
                        operate(entry.getKey(),"tool-attack",attackMatch,level,ActionType.ATTACK);
                    }
                    if(NonPokemonBatch33Test.IDS.contains(entry.getKey())) {
                        var attackMatch=NonPokemonBatch33Test.game(entry.getKey(),false,false);NonPokemonBatch33Test.equip(attackMatch);NonPokemonBatch33Test.attach(attackMatch,A,"energy",5);
                        operate(entry.getKey(),"tool-attack",attackMatch,level,ActionType.ATTACK);
                    }
                }catch(Exception e){record(entry.getKey(),"trainer",level,"FIXTURE_UNREACHABLE",e.toString());}
            }
            for(var entry:new TreeMap<>(data.pokemonProfiles()).entrySet())for(var attack:entry.getValue().attacks())
                if(included(entry.getKey())&&attack.effectSupport()==EffectSupport.SUPPORTED&&AttackProgramRules.unsupportedReason(attack).isEmpty()&&(all||relevant(attack.effectProgram()))) {
                    try {
                        var m=attackGame(attack,List.of());
                        if(Set.of("gemstone-mimicry","night-joker").contains(attack.id())){m=ModernCopyRepairTest.game(attack,true,7);ModernCopyRepairTest.pay(m);}
                        if(attack.id().equals("follow-up-kerzap")){
                            var previous=entry.getValue().attacks().stream().filter(a->a.id().equals("stun-needle")).findFirst().orElseThrow();
                            m=ModernWindowDamageRepairTest.game(attack,0,false,List.of(previous));m.attack(A,previous.id());m.endTurn(B);
                        }
                        if(!initialAction(m,ActionType.ATTACK))for(var mon:m.benchPokemonView(B))m.placeDamageCountersByEffect(A,B,mon.pokemonId(),2);
                        if(!initialAction(m,ActionType.ATTACK))m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),2);
                        operate(entry.getKey(),attack.id(),m,level,ActionType.ATTACK);
                    }catch(Exception e){record(entry.getKey(),attack.id(),level,"FIXTURE_UNREACHABLE",e.toString());}
                }
            for(var entry:new TreeMap<>(data.pokemonProfiles()).entrySet())for(var ability:entry.getValue().abilities())
                if(included(entry.getKey())&&ability.effectSupport()==EffectSupport.SUPPORTED&&ability.kind()!=AbilityDefinition.Kind.PASSIVE&&ability.kind()!=AbilityDefinition.Kind.TRIGGERED&&(all||relevant(ability.effectProgram()))) {
                    try{
                        if(RestoredPokemonRules.prehistoricCall(ability)) {
                            var m=NonPokemonBatch121Test.game(entry.getKey(),GeneratedRuleBatch8Test.hit(300,false));UUID actor=m.currentPlayer();
                            var card=NonPokemonBatch121Test.hand(m,actor,entry.getKey());m.discardHandCardsByEffect(actor,actor,List.of(card.instanceId()));
                            operate(entry.getKey(),ability.id(),m,level,ActionType.DISCARD_ABILITY);
                        } else {var m=abilityGame(ability);if(!initialAction(m,ActionType.ABILITY))preparePublicResources(m);operate(entry.getKey(),ability.id(),m,level,ActionType.ABILITY);}
                    }
                    catch(Exception e){record(entry.getKey(),ability.id(),level,"FIXTURE_UNREACHABLE",e.toString());}
                }
            if(all)for(var entry:new TreeMap<>(data.energyProfiles()).entrySet())if(included(entry.getKey())) {
                if(entry.getKey().equals(NonPokemonBatch129Test.ID))try{operate(entry.getKey(),"prize-attachment",NonPokemonBatch129Test.aiGame(),level,ActionType.CHOICE);}catch(Exception e){record(entry.getKey(),"prize-attachment",level,"FIXTURE_UNREACHABLE",e.toString());}
                try{operate(entry.getKey(),"energy-attachment",NonPokemonBatch109Test.ENERGIES.contains(entry.getKey())?NonPokemonBatch109Test.aiGame(entry.getKey()):NonPokemonBatch103Test.ENERGIES.contains(entry.getKey())?NonPokemonBatch103Test.aiGame(entry.getKey()):NonPokemonBatch97Test.IDS.contains(entry.getKey())?NonPokemonBatch97Test.aiGame(entry.getKey()):NonPokemonBatch79Test.IDS.contains(entry.getKey())?NonPokemonBatch79Test.aiGame(entry.getKey()):NonPokemonBatch78Test.IDS.contains(entry.getKey())?NonPokemonBatch78Test.aiGame(entry.getKey()):NonPokemonBatch58Test.IDS.contains(entry.getKey())?NonPokemonBatch58Test.aiGame(entry.getKey()):DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),entry.getValue(),GeneratedRuleBatch8Test.hit(10,false)),level,ActionType.ATTACH);}
                catch(Exception e){record(entry.getKey(),"energy-attachment",level,"FIXTURE_UNREACHABLE",e.toString());}
            }
            if(all)for(var entry:new TreeMap<>(data.trainerProfiles()).entrySet())if(included(entry.getKey())&&entry.getValue().activation()!=null) {
                try {
                    var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),entry.getValue(),EnergyProfile.fixed(EnergyType.GRASS,2),GeneratedRuleBatch8Test.hit(10,false),List.of(),0,null,entry.getKey().equals("xy5_en_137")?Map.of("basic",EnergyType.WATER):Map.of());
                    if(entry.getKey().equals("swsh5_en_137"))m=NonPokemonBatch35Test.towerGame();
                    if(NonPokemonBatch39Test.IDS.contains(entry.getKey()))m=NonPokemonBatch39Test.aiGame(entry.getKey());
                    if(NonPokemonBatch42Test.IDS.contains(entry.getKey()))m=NonPokemonBatch42Test.aiGame(entry.getKey());
                    if(NonPokemonBatch44Test.IDS.contains(entry.getKey()))m=NonPokemonBatch44Test.aiGame(entry.getKey());
                    if(NonPokemonBatch45Test.IDS.contains(entry.getKey()))m=NonPokemonBatch45Test.aiGame(entry.getKey());
                    if(NonPokemonBatch46Test.IDS.contains(entry.getKey()))m=NonPokemonBatch46Test.aiGame(entry.getKey());
                    if(NonPokemonBatch47Test.IDS.contains(entry.getKey()))m=NonPokemonBatch47Test.aiGame(entry.getKey());
                    if(NonPokemonBatch48Test.IDS.contains(entry.getKey()))m=NonPokemonBatch48Test.aiGame(entry.getKey());
                    if(NonPokemonBatch49Test.IDS.contains(entry.getKey()))m=NonPokemonBatch49Test.aiGame(entry.getKey());
                    if(NonPokemonBatch50Test.IDS.contains(entry.getKey()))m=NonPokemonBatch50Test.aiGame(entry.getKey());
                    if(NonPokemonBatch51Test.IDS.contains(entry.getKey()))m=NonPokemonBatch51Test.aiGame(entry.getKey());
                    if(NonPokemonBatch132Test.IDS.contains(entry.getKey()))m=NonPokemonBatch132Test.aiGame(entry.getKey());
                    if(NonPokemonBatch133Test.IDS.contains(entry.getKey()))m=NonPokemonBatch133Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch130Test.IDS.contains(entry.getKey()))m=NonPokemonBatch130Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch128Test.IDS.contains(entry.getKey()))m=NonPokemonBatch128Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch127Test.IDS.contains(entry.getKey()))m=NonPokemonBatch127Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch126Test.IDS.contains(entry.getKey()))m=NonPokemonBatch126Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch125Test.IDS.contains(entry.getKey()))m=NonPokemonBatch125Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch124Test.IDS.contains(entry.getKey()))m=NonPokemonBatch124Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch122Test.IDS.contains(entry.getKey()))m=NonPokemonBatch122Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch121Test.IDS.contains(entry.getKey()))m=NonPokemonBatch121Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch120Test.IDS.contains(entry.getKey()))m=NonPokemonBatch120Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch119Test.IDS.contains(entry.getKey()))m=NonPokemonBatch119Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch118Test.IDS.contains(entry.getKey()))m=NonPokemonBatch118Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch117Test.IDS.contains(entry.getKey()))m=NonPokemonBatch117Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch116Test.ALL_IDS.contains(entry.getKey()))m=NonPokemonBatch116Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch115Test.IDS.contains(entry.getKey()))m=NonPokemonBatch115Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch114Test.IDS.contains(entry.getKey()))m=NonPokemonBatch114Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch113Test.IDS.contains(entry.getKey()))m=NonPokemonBatch113Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch112Test.IDS.contains(entry.getKey()))m=NonPokemonBatch112Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch111Test.IDS.contains(entry.getKey()))m=NonPokemonBatch111Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch110Test.IDS.contains(entry.getKey()))m=NonPokemonBatch110Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch109Test.TOOLS.contains(entry.getKey()))m=NonPokemonBatch109Test.aiGame(entry.getKey());
                    if(NonPokemonBatch108Test.IDS.contains(entry.getKey()))m=NonPokemonBatch108Test.aiGame(entry.getKey());
                    if(NonPokemonBatch107Test.IDS.contains(entry.getKey()))m=NonPokemonBatch107Test.aiGame(entry.getKey());
                    if(NonPokemonBatch106Test.IDS.contains(entry.getKey()))m=NonPokemonBatch106Test.aiGame(entry.getKey());
                    if(NonPokemonBatch105Test.IDS.contains(entry.getKey()))m=NonPokemonBatch105Test.aiGame(entry.getKey());
                    if(NonPokemonBatch104Test.IDS.contains(entry.getKey()))m=NonPokemonBatch104Test.aiGame(entry.getKey());
                    if(NonPokemonBatch103Test.TOOLS.contains(entry.getKey()))m=NonPokemonBatch103Test.aiGame(entry.getKey());
                    if(NonPokemonBatch102Test.IDS.contains(entry.getKey()))m=NonPokemonBatch102Test.aiGame(entry.getKey());
                    if(NonPokemonBatch101Test.IDS.contains(entry.getKey()))m=NonPokemonBatch101Test.aiGame(entry.getKey());
                    if(NonPokemonBatch100Test.IDS.contains(entry.getKey()))m=NonPokemonBatch100Test.aiGame(entry.getKey());
                    if(NonPokemonBatch99Test.IDS.contains(entry.getKey()))m=NonPokemonBatch99Test.aiGame(entry.getKey());
                    if(NonPokemonBatch98Test.IDS.contains(entry.getKey()))m=NonPokemonBatch98Test.aiGame(entry.getKey());
                    if(NonPokemonBatch97Test.IDS.contains(entry.getKey()))m=NonPokemonBatch97Test.aiGame(entry.getKey());
                    if(NonPokemonBatch96Test.IDS.contains(entry.getKey()))m=NonPokemonBatch96Test.aiGame(entry.getKey());
                    if(NonPokemonBatch95Test.IDS.contains(entry.getKey()))m=NonPokemonBatch95Test.aiGame(entry.getKey());
                    if(NonPokemonBatch94Test.IDS.contains(entry.getKey()))m=NonPokemonBatch94Test.aiGame(entry.getKey());
                    if(NonPokemonBatch93Test.IDS.contains(entry.getKey()))m=NonPokemonBatch93Test.aiGame(entry.getKey());
                    if(NonPokemonBatch92Test.IDS.contains(entry.getKey()))m=NonPokemonBatch92Test.aiGame(entry.getKey());
                    if(NonPokemonBatch91Test.IDS.contains(entry.getKey()))m=NonPokemonBatch91Test.aiGame(entry.getKey());
                    if(NonPokemonBatch90Test.IDS.contains(entry.getKey()))m=NonPokemonBatch90Test.aiGame(entry.getKey());
                    if(NonPokemonBatch89Test.IDS.contains(entry.getKey()))m=NonPokemonBatch89Test.aiGame(entry.getKey());
                    if(NonPokemonBatch88Test.IDS.contains(entry.getKey()))m=NonPokemonBatch88Test.aiGame(entry.getKey());
                    if(NonPokemonBatch87Test.IDS.contains(entry.getKey()))m=NonPokemonBatch87Test.aiGame(entry.getKey());
                    if(NonPokemonBatch86Test.IDS.contains(entry.getKey()))m=NonPokemonBatch86Test.aiGame(entry.getKey());
                    if(NonPokemonBatch85Test.IDS.contains(entry.getKey()))m=NonPokemonBatch85Test.aiGame(entry.getKey());
                    if(NonPokemonBatch84Test.IDS.contains(entry.getKey()))m=NonPokemonBatch84Test.aiGame(entry.getKey());
                    if(NonPokemonBatch83Test.IDS.contains(entry.getKey()))m=NonPokemonBatch83Test.aiGame(entry.getKey());
                    if(NonPokemonBatch82Test.IDS.contains(entry.getKey()))m=NonPokemonBatch82Test.aiGame(entry.getKey());
                    if(NonPokemonBatch81Test.IDS.contains(entry.getKey()))m=NonPokemonBatch81Test.aiGame(entry.getKey());
                    if(NonPokemonBatch80Test.IDS.contains(entry.getKey()))m=NonPokemonBatch80Test.aiGame(entry.getKey());
                    if(NonPokemonBatch79Test.IDS.contains(entry.getKey()))m=NonPokemonBatch79Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch78Test.IDS.contains(entry.getKey()))m=NonPokemonBatch78Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch77Test.IDS.contains(entry.getKey()))m=NonPokemonBatch77Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch76Test.IDS.contains(entry.getKey()))m=NonPokemonBatch76Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch75Test.IDS.contains(entry.getKey()))m=NonPokemonBatch75Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch74Test.IDS.contains(entry.getKey()))m=NonPokemonBatch74Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch73Test.IDS.contains(entry.getKey()))m=NonPokemonBatch73Test.aiGame(entry.getKey());
                    else if(NonPokemonBatch72Test.IDS.contains(entry.getKey()))m=NonPokemonBatch72Test.aiGame(entry.getKey());
                    if(NonPokemonBatch71Test.IDS.contains(entry.getKey()))m=NonPokemonBatch71Test.aiGame(entry.getKey());
                    if(NonPokemonBatch70Test.IDS.contains(entry.getKey()))m=NonPokemonBatch70Test.aiGame(entry.getKey());
                    if(NonPokemonBatch69Test.IDS.contains(entry.getKey()))m=NonPokemonBatch69Test.aiGame(entry.getKey());
                    if(NonPokemonBatch68Test.IDS.contains(entry.getKey()))m=NonPokemonBatch68Test.aiGame(entry.getKey());
                    if(NonPokemonBatch67Test.IDS.contains(entry.getKey()))m=NonPokemonBatch67Test.aiGame(entry.getKey());
                    if(NonPokemonBatch66Test.IDS.contains(entry.getKey()))m=NonPokemonBatch66Test.aiGame(entry.getKey());
                    if(NonPokemonBatch65Test.IDS.contains(entry.getKey()))m=NonPokemonBatch65Test.aiGame(entry.getKey());
                    if(NonPokemonBatch64Test.IDS.contains(entry.getKey()))m=NonPokemonBatch64Test.aiGame(entry.getKey());
                    if(NonPokemonBatch63Test.IDS.contains(entry.getKey()))m=NonPokemonBatch63Test.aiGame(entry.getKey());
                    if(NonPokemonBatch62Test.IDS.contains(entry.getKey()))m=NonPokemonBatch62Test.aiGame(entry.getKey());
                    if(NonPokemonBatch61Test.IDS.contains(entry.getKey()))m=NonPokemonBatch61Test.aiGame(entry.getKey());
                    if(NonPokemonBatch60Test.IDS.contains(entry.getKey()))m=NonPokemonBatch60Test.aiGame(entry.getKey());
                    if(NonPokemonBatch59Test.IDS.contains(entry.getKey()))m=NonPokemonBatch59Test.aiGame(entry.getKey());
                    if(NonPokemonBatch57Test.IDS.contains(entry.getKey()))m=NonPokemonBatch57Test.aiGame(entry.getKey());
                    if(NonPokemonBatch56Test.IDS.contains(entry.getKey()))m=NonPokemonBatch56Test.aiGame(entry.getKey());
                    if(NonPokemonBatch55Test.IDS.contains(entry.getKey()))m=NonPokemonBatch55Test.aiGame(entry.getKey());
                    if(NonPokemonBatch54Test.IDS.contains(entry.getKey()))m=NonPokemonBatch54Test.aiGame(entry.getKey());
                    if(NonPokemonBatch53Test.IDS.contains(entry.getKey()))m=NonPokemonBatch53Test.aiGame(entry.getKey());
                    if(NonPokemonBatch52Test.IDS.contains(entry.getKey()))m=NonPokemonBatch52Test.aiGame(entry.getKey());
                    m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,entry.getKey().equals("swsh5_en_137")||NonPokemonBatch121Test.IDS.contains(entry.getKey())||NonPokemonBatch100Test.IDS.contains(entry.getKey())||NonPokemonBatch39Test.IDS.contains(entry.getKey())||NonPokemonBatch42Test.IDS.contains(entry.getKey())||NonPokemonBatch44Test.IDS.contains(entry.getKey())||NonPokemonBatch45Test.IDS.contains(entry.getKey())||NonPokemonBatch46Test.IDS.contains(entry.getKey())?"trainer":"stadium").instanceId(),null);
                    if(entry.getKey().equals("swsh7_en_157"))m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),m.activePokemonView(A).pokemonId());
                    if(FESTIVALS.contains(entry.getKey()))while(m.benchPokemonView(A).size()<5)m.playBasicPokemonToBench(A,DynamicPassiveModifierTest.hand(m,A,"basic").instanceId());
                    if(FESTIVALS.contains(entry.getKey())||Set.of("xy5_en_137","xy9_en_096").contains(entry.getKey()))m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),5);
                    if(entry.getKey().equals("xy9_en_096"))m.applySpecialConditionByEffect(A,A,SpecialCondition.ASLEEP);
                    operate(entry.getKey(),"stadium-activation",m,level,ActionType.STADIUM);
                }catch(Exception e){record(entry.getKey(),"stadium-activation",level,"FIXTURE_UNREACHABLE",e.toString());}
            }
        }
        Files.writeString(Path.of(all?"outputs/ai-all-operations.json":"outputs/ai-card-operations.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
        var counts=new TreeMap<String,Integer>();rows.forEach(r->counts.merge((String)r.get("status"),1,Integer::sum));
        System.out.println("AiCardOperationTest: "+counts);
        if(counts.getOrDefault("FAILED",0)>0)throw new AssertionError("AI operation failures: see outputs/ai-card-operations.json");
        if((!FILTER.contains("")||!System.getProperty("ptcglive.scopeFile","").isEmpty())&&counts.getOrDefault("FIXTURE_UNREACHABLE",0)>0)throw new AssertionError("Requested card batch has untested operations: see outputs/ai-all-operations.json");
    }
    static EnergyProfile rainbow(){var units=new ArrayList<EnergyProfile.Unit>();for(int i=0;i<4;i++)units.add(new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class)));return new EnergyProfile(units);}
    static ServerMatch abilityGame(AbilityDefinition ability) {
        var bindings=ability.effectProgram().triggers();
        if(bindings.size()==1&&bindings.getFirst().conditions().isEmpty()&&bindings.getFirst().effects().size()==1) {
            var effect=bindings.getFirst().effects().getFirst();var a=effect.arguments();
            if(effect.type().equals("attach_energy")&&"true".equals(a.get("basic_energy_only"))
                &&a.containsKey("energy_type")&&Set.of("HAND","DISCARD").contains(a.getOrDefault("source",""))) {
                var m=attackGame(GeneratedRuleBatch8Test.hit(10,false),List.of(ability),EnergyType.valueOf(a.get("energy_type")));
                if("DISCARD".equals(a.get("source"))) {
                    var payment=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy"))
                        .limit(Integer.parseInt(a.getOrDefault("amount","1"))).map(CardInstance::instanceId).toList();
                    m.discardHandCardsByEffect(A,A,payment);
                }
                return m;
            }
        }
        return attackGame(GeneratedRuleBatch8Test.hit(10,false),List.of(ability));
    }
    static ServerMatch attackGame(AttackDefinition attack,List<AbilityDefinition> abilities) {
        return attackGame(attack,abilities,EnergyType.GRASS);
    }
    static ServerMatch attackGame(AttackDefinition attack,List<AbilityDefinition> abilities,EnergyType basicEnergy) {
        var defs=new ArrayList<CardDefinition>();
        for(String id:List.of("basic","opponent","energy","special","trainer","supporter")) {
            boolean pokemon=Set.of("basic","opponent").contains(id),energy=Set.of("energy","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("supporter")?TrainerCategory.SUPPORTER:id.equals("trainer")?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),pokemon,id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        var opponent=GeneratedRuleBatch8Test.hit(30,false);
        var pokemon=new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack),abilities,attack.damageType()==null?EnergyType.COLORLESS:attack.damageType());
        var defender=new PokemonProfile(500,0,null,null,0,null,0,1,List.of(opponent),List.of(),EnergyType.FIRE);
        var draw=ComposedTrainerIntegrationTest.item("{\"type\":\"draw\",\"amount\":1}");
        var supporter=BundledCombatData.trainer(com.google.gson.JsonParser.parseString("{\"trainerCategory\":\"SUPPORTER\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject(),EffectRegistry.standard());
        var catalog=new CombatCatalog(Map.of("basic",pokemon,"opponent",defender),Map.of("energy",basicEnergy),Map.of("trainer",draw,"supporter",supporter),Map.of("special",rainbow()));
        for(int seed=0;seed<100;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID seat:List.of(A,B)){var deck=new ArrayList<CardInstance>();String host=seat.equals(A)?"basic":"opponent";
                String[] ids={host,host,host,"energy","energy","special","special","trainer","supporter",host};
                for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(seat.getLeastSignificantBits(),i+1),seat,ids[i%ids.length]));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));
            m.callCoin(A,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?A:B;
            m.chooseWhoGoesFirst(winner,winner.equals(attack.onlySecondPlayerFirstTurn()?B:A));
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(m.currentPlayer(),A,15);m.drawCardsByEffect(m.currentPlayer(),B,10);
            if(!attack.onlySecondPlayerFirstTurn())m.endTurn(A);m.endTurn(B);
            for(UUID seat:List.of(A,B))for(var special:m.privateView(seat).hand().stream().filter(c->c.definitionId().equals("special")).limit(2).toList())
                m.attachEnergyByEffect(A,seat,EffectSourceZone.HAND,special.instanceId(),seat,m.activePokemonView(seat).pokemonId());
            if(attack.useRequirement()!=null&&attack.useRequirement().type()==AttackUseRequirement.Type.LOST_ZONE_TEN_CARDS)
                for(var card:m.privateView(A).hand().stream().limit(10).toList())m.sendToLostZoneByEffect(A,EffectSourceZone.HAND,card.instanceId());
            return m;
        }
        throw new IllegalStateException("No opening fixture");
    }
    static boolean initialAction(ServerMatch m,ActionType type){var seats=m.clientView(null).players().stream().map(MatchClientView.Player::id).toList();var p=m.currentPlayer();return MatchSession.multiplayer(m,seats.get(0),seats.get(1)).view(p,p).options().stream().anyMatch(o->o.enabled()&&o.type()==type&&(type!=ActionType.TRAINER||m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals("trainer")&&o.cardKeys().contains("hand:"+c.instanceId()))));}
    /** Supply common printed preconditions through ordinary effect APIs; never bypass a play gate. */
    static void preparePublicResources(ServerMatch m){
        var actor=m.currentPlayer();
        for(var player:m.clientView(null).players()) {
            UUID seat=player.id();var mons=new ArrayList<>(m.benchPokemonView(seat));mons.addFirst(m.activePokemonView(seat));
            for(var mon:mons) {
                if(mon.hitPoints()-mon.damage()>100)m.placeDamageCountersByEffect(actor,seat,mon.pokemonId(),5);
                for(String key:List.of("energy","special")) {
                    var card=m.privateView(seat).hand().stream().filter(c->c.definitionId().equals(key)).findFirst();
                    if(card.isPresent())m.attachEnergyByEffect(actor,seat,EffectSourceZone.HAND,card.get().instanceId(),seat,mon.pokemonId());
                }
            }
            var discard=m.privateView(seat).hand().stream().filter(c->!Set.of("trainer","supporter").contains(c.definitionId())).limit(10).map(CardInstance::instanceId).toList();
            m.discardHandCardsByEffect(actor,seat,discard);
        }
        if(!m.activePokemonView(actor).conditions().contains(SpecialCondition.POISONED))m.applySpecialConditionByEffect(actor,actor,SpecialCondition.POISONED);
        var hand=m.privateView(actor).hand();var keep=hand.stream().filter(c->c.definitionId().equals("trainer")).limit(4).map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet());
        int remove=Math.max(0,hand.size()-Math.max(4,keep.size()));
        m.discardHandCardsByEffect(actor,actor,hand.stream().filter(c->!keep.contains(c.instanceId())).limit(remove).map(CardInstance::instanceId).toList());
    }
    static void record(String id,String operation,AiDifficulty level,String status,String detail){
        observedCard=id;observedOperation=operation;
        if(status.equals("FIXTURE_UNREACHABLE")){
            var prepared=AiOperationFixtures.find(id,operation,level);
            if(prepared!=null){
                try{
                    var session=prepared.session();UUID actor=prepared.actor();
                    snapshotObserver.accept(session.view(actor,actor));
                    session.submit(actor,actor,session.view(actor,actor).revision(),prepared.option().index());settle(session,level);
                    boolean full=!Boolean.getBoolean("ptcglive.allOperations")||Boolean.getBoolean("ptcglive.completeMatches");
                    if(full)finish(session,level);
                    status=full?"COMPLETED_MATCH":"RESOLVED";detail="Exact printed profile on reused fixture: "+prepared.fixture();
                }catch(RuntimeException|AssertionError e){status="FAILED";detail=e+" | "+prepared.fixture();}
            }
            else if(AiOperationFixtures.DIAGNOSTICS.containsKey(id+"/"+operation))detail+=" | Reused resource fixture: "+AiOperationFixtures.DIAGNOSTICS.get(id+"/"+operation);
        }
        rows.add(Map.of("card",id,"operation",operation,"difficulty",level,"status",status,"detail",detail));if(rows.size()%500==0)System.out.println("Operation attempts: "+rows.size());
    }
    static boolean matchesDeclaredOperation(ServerMatch m,UUID actor,String operation,MatchSession.Option option) {
        if(option.type()!=ActionType.ATTACK&&option.type()!=ActionType.ABILITY||Set.of("qualified-copy-source","tool-attack").contains(operation)||option.commandId().equals(operation))return true;
        if(option.type()!=ActionType.ATTACK)return false;
        var declared=m.activeAttacks(actor).stream().filter(a->a.id().equals(operation)).findFirst();
        if(declared.isEmpty()||!m.needsCopyAttackSelection(actor,declared.get())||!option.labelZh().startsWith("复制攻击："+declared.get().nameZh()+" → "))return false;
        return m.copyAttackOptions(actor,operation).stream().anyMatch(c->option.commandId().equals(c.effectiveAttack().id())
            &&option.cardKeys().contains("copy-source:"+c.topCardId())&&option.cardKeys().contains("copy-attack:"+c.attackId()));
    }
    static void operate(String id,String operation,ServerMatch m,AiDifficulty level,ActionType type) {
        observedCard=id;observedOperation=operation;
        var seats=m.clientView(null).players().stream().map(MatchClientView.Player::id).toList();var actor=m.currentPlayer();
        var session=MatchSession.multiplayer(m,seats.get(0),seats.get(1));var snapshot=session.view(actor,actor);
        snapshotObserver.accept(snapshot);
        var option=snapshot.options().stream().filter(o->o.enabled()&&o.type()==type)
            .filter(o->matchesDeclaredOperation(m,actor,operation,o))
            .filter(o->!operation.equals("tool-attack")||o.cardKeys().stream().anyMatch(k->k.startsWith("copy-attack:")))
            .filter(o->type!=ActionType.TRAINER||m.privateView(actor).hand().stream().anyMatch(c->c.definitionId().equals("trainer")&&o.cardKeys().contains("hand:"+c.instanceId())))
            .filter(o->type!=ActionType.ATTACH||m.privateView(actor).hand().stream().anyMatch(c->c.definitionId().equals("special")&&o.cardKeys().contains("hand:"+c.instanceId()))).findFirst();
        if(option.isEmpty()){record(id,operation,level,"FIXTURE_UNREACHABLE","No legal initial action on this prepared board");return;}
        try {
            session.submit(actor,actor,snapshot.revision(),option.get().index());settle(session,level);
            boolean completeMatch=!Boolean.getBoolean("ptcglive.allOperations")||Boolean.getBoolean("ptcglive.completeMatches");
            if(completeMatch)finish(session,level);
            record(id,operation,level,completeMatch?"COMPLETED_MATCH":"RESOLVED","Printed program; synthetic host/board; AI completed required follow-up choices");
        }catch(RuntimeException|AssertionError e){record(id,operation,level,"FAILED",e+" | "+Arrays.toString(Arrays.copyOf(e.getStackTrace(),Math.min(8,e.getStackTrace().length))));}
    }
    static void settle(MatchSession session,AiDifficulty level) {
        var m=session.match();
        for(int step=0;step<160;step++) {
            if(m.phase()==MatchPhase.FINISHED)return;
            if(!m.hasPendingEffectChoice()&&!m.hasPendingTurnDraw()&&m.triggerOrderPrompt()==null&&m.toolOverflowPrompt()==null&&m.phase()==MatchPhase.TURN) {
                for(var player:m.clientView(null).players())snapshotObserver.accept(session.view(player.id(),player.id()));
                return;
            }
            boolean moved=false;
            for(UUID seat:m.clientView(null).players().stream().map(MatchClientView.Player::id).toList()) {
                var view=AiPracticeScenarios.view(session,seat);var decision=AiPolicy.choose(view,77+step,Set.of(),level);
                snapshotObserver.accept(view.snapshot());
                if(decision.isEmpty())continue;
                var d=decision.get();session.submit(seat,seat,d.revision(),d.option());moved=true;break;
            }
            if(!moved)throw new AssertionError("No choice at "+m.phase());
        }
        throw new AssertionError("Choice loop exceeded 160 actions");
    }
    static void finish(MatchSession session,AiDifficulty level) {
        var m=session.match();var used=new HashMap<UUID,Set<String>>();int turn=-1;
        for(int step=0;step<1200;step++) {
            if(m.phase()==MatchPhase.FINISHED)return;
            if(turn!=m.turnNumber()){turn=m.turnNumber();used.clear();}
            boolean moved=false;
            for(UUID seat:m.clientView(null).players().stream().map(MatchClientView.Player::id).toList()) {
                var input=AiPracticeScenarios.view(session,seat);var remembered=used.computeIfAbsent(seat,k->new HashSet<>());
                snapshotObserver.accept(input.snapshot());
                var decision=AiPolicy.choose(input,1234+step,remembered,level);if(decision.isEmpty())continue;
                var d=decision.get();var action=input.snapshot().options().get(d.option());
                session.submit(seat,seat,d.revision(),d.option());var key=AiPolicy.optionalKey(action);if(!key.isEmpty())remembered.add(key);moved=true;break;
            }
            if(!moved)throw new AssertionError("Full game stalled at "+m.phase());
        }
        throw new AssertionError("Full game exceeded 1200 actions");
    }
    static void typed(AiDifficulty level) {
        var m=GeneratedRuleBatch49Test.fixture();GeneratedRuleBatch49Test.attach(m,"fire","fire","water","lightning");
        m.attack(A,GeneratedRuleBatch49Test.blast().id());
        try{settle(MatchSession.multiplayer(m,A,B),level);record("me5-5_en_121","typed-payment-duplicate-fire",level,"RESOLVED","");}
        catch(RuntimeException|AssertionError e){record("me5-5_en_121","typed-payment-duplicate-fire",level,"FAILED",e.toString());}
    }
    static void distinct(AiDifficulty level) {
        var trainer=ComposedTrainerIntegrationTest.item("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"distinct_energy_types\":true}");
        var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,trainer,GeneratedRuleBatch8Test.hit(10,false));
        operate("synthetic","distinct-energy-search",m,level,ActionType.TRAINER);
    }
}
