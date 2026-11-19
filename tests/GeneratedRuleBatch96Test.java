import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.*;
import java.util.*;

/** Real Cobalion ex prints. Every unprinted continuation, status setup and capacity fixture is synthetic. */
public final class GeneratedRuleBatch96Test {
    static final UUID A=new UUID(0,9611), B=new UUID(0,9612), WATCH=new UUID(0,9613);
    static final String CARD="me4_en_064", ALT="me4_en_103", ABRA="me1_en_054", CORPHISH="me1_en_033";
    static final String SLOWKING="sd100_cn_258", JYNX="me5_en_032", MAREEP="me4_en_027", FLAAFFY="me4_en_028", AMPHAROS="me4_en_029";
    static final String SWITCH="sv1_en_194", SURFER="sv8_en_187", BOSS="me1_en_114", MAGNET="me4_en_085", REVERSAL="sv2_en_192";
    static final String ROAD="metal-road", TACKLE="power-tackle";
    static final String SW="{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}";
    static final String SEARCH="{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1}";
    static final String DRAW="{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();
    static final CombatCatalog DATA=BundledCombatData.load();
    static final Gson GSON=new Gson();
    static void check(boolean ok,String label){GeneratedRuleBatch8Test.check(ok,label);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static UUID active(ServerMatch m,UUID owner){return m.activePokemonView(owner).pokemonId();}
    static UUID bench(ServerMatch m,UUID owner,String id){return m.benchPokemonView(owner).stream().filter(v->v.topCardId().equals(id)).findFirst().orElseThrow().pokemonId();}
    static void atomic(ServerMatch m,Runnable action){
        var a=m.privateView(A);var b=m.privateView(B);var view=m.clientView(A);var rng=m.randomTranscript();
        reject(action);check(a.equals(m.privateView(A))&&b.equals(m.privateView(B))&&view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Rejected action changed state/RNG");
    }
    static EffectDefinition effect(String json){return EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject());}
    static EffectProgram program(EffectTrigger trigger,String json){return new EffectProgram(List.of(BeforeDamageAttackTest.binding(trigger,json)));}
    static TrainerProfile trainer(TrainerCategory category,String json){return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\""+category+"\",\"effects\":["+json+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static CardDefinition synthetic(String id,CardCategory category,TrainerCategory trainer,boolean basic){
        return new CardDefinition(id,"Explicit synthetic "+id,category,trainer,"J",Set.of("international"),basic,Set.of("metal","water","psychic","dark").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null);
    }
    static ServerMatch game(){return game(DRAW,"NORMAL",0,true);}
    static ServerMatch game(String original,String mode){return game(original,mode,0,true);}
    static ServerMatch game(String original,String mode,int firstSeed,boolean advance){
        var defs=new ArrayList<CardDefinition>();var pokemon=new HashMap<String,PokemonProfile>();var trainers=new HashMap<String,TrainerProfile>();
        for(String id:List.of(CARD,ALT,ABRA,CORPHISH,SLOWKING,JYNX,MAREEP,FLAAFFY,AMPHAROS)){
            defs.add(DB.find(id).orElseThrow());pokemon.put(id,DATA.pokemon(id).orElseThrow());
        }
        for(String id:List.of(SWITCH,SURFER,BOSS)){defs.add(DB.find(id).orElseThrow());trainers.put(id,DATA.trainer(id).orElseThrow());}
        for(String id:List.of(MAGNET,REVERSAL))defs.add(DB.find(id).orElseThrow());
        defs.add(synthetic("base",CardCategory.POKEMON,TrainerCategory.NONE,true));
        pokemon.put("base",new PokemonProfile(1000,1,null,mode.equals("WEAK")?EnergyType.METAL:null,mode.equals("WEAK")?2:0,
            mode.equals("RESIST")?EnergyType.METAL:null,mode.equals("RESIST")?30:0,1,
            List.of(GeneratedRuleBatch8Test.hit(10,false),CopyAttackTest.copier()),List.of(),EnergyType.COLORLESS));
        for(String id:List.of("metal","water","psychic","dark","evolved-double"))defs.add(synthetic(id,CardCategory.ENERGY,TrainerCategory.NONE,false));
        for(var e:Map.of("item",TrainerCategory.ITEM,"field",TrainerCategory.STADIUM,"clear",TrainerCategory.STADIUM,"bench-hp",TrainerCategory.TOOL).entrySet())defs.add(synthetic(e.getKey(),CardCategory.TRAINER,e.getValue(),false));
        trainers.put("item",trainer(TrainerCategory.ITEM,original));
        trainers.put("field",new TrainerProfile(TrainerCategory.STADIUM,new TrainerEffect(TrainerEffect.Opcode.PASSIVE_STADIUM,0,0),
            program(EffectTrigger.PASSIVE,"{\"type\":\"suppress_abilities\",\"target\":\"SELF\",\"scope\":\"ALL_IN_PLAY\"}")));
        trainers.put("clear",new TrainerProfile(TrainerCategory.STADIUM,new TrainerEffect(TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION,1,0)));
        trainers.put("bench-hp",trainer(TrainerCategory.TOOL,"{\"type\":\"hit_points_modifier\",\"target\":\"SELF\",\"amount\":100,\"conditions\":[{\"type\":\"NOT\",\"conditions\":[{\"type\":\"IS_ACTIVE\"}]}]}"));
        // This is an evolved-host mechanism fixture, NOT Neo Upper's complete Stage2/ACE SPEC definition.
        String all="[\"GRASS\",\"FIRE\",\"WATER\",\"LIGHTNING\",\"PSYCHIC\",\"FIGHTING\",\"DARKNESS\",\"METAL\",\"DRAGON\",\"COLORLESS\",\"FAIRY\"]";
        var evolvedDouble=DynamicEnergyProvisionTest.energy("{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"IS_EVOLVED\"}],\"energyUnits\":["+all+","+all+"]}]}");
        var catalog=new CombatCatalog(pokemon,Map.of("metal",EnergyType.METAL,"water",EnergyType.WATER,"psychic",EnergyType.PSYCHIC,"dark",EnergyType.DARKNESS),trainers,
            Map.of(MAGNET,DATA.energyProfile(MAGNET).orElseThrow(),REVERSAL,DATA.energyProfile(REVERSAL).orElseThrow(),"evolved-double",evolvedDouble));
        var pool=new ArrayList<String>();
        if(mode.equals("CAPACITY")){for(int i=0;i<8;i++)pool.add("base");pool.add(CARD);while(pool.size()<60)pool.add("metal");}
        else {
            for(int i=0;i<10;i++)pool.add("base");
            pool.addAll(List.of(CARD,ALT,ABRA,CORPHISH,SLOWKING,JYNX,MAREEP,FLAAFFY,AMPHAROS,SWITCH,SWITCH,SWITCH,SWITCH,SWITCH,SURFER,SURFER,BOSS,MAGNET,MAGNET,REVERSAL,REVERSAL,"evolved-double"));
            for(int i=0;i<12;i++)pool.add("metal");for(int i=0;i<4;i++)pool.add("water");
            pool.addAll(List.of("psychic","psychic","dark","dark","item","item","field","clear","bench-hp"));
            while(pool.size()<60)pool.add("base");
        }
        check(pool.size()==60,"Physical fixture deck must remain60");
        for(int seed=firstSeed;seed<firstSeed+4000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,pool.get(i)));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,"base").instanceId());m.placeOpeningBench(p,hand(m,p,"base").instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);
            if(advance){m.endTurn(A);m.endTurn(B);}return m;
        }
        throw new AssertionError("No legal opening fixture: "+mode);
    }
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    @SuppressWarnings("unchecked") static List<CardInstance> zone(ServerMatch m,UUID owner,String name){
        try{var f=ServerMatch.class.getDeclaredField("players");f.setAccessible(true);Object state=((Map<?,?>)f.get(m)).get(owner);var z=state.getClass().getDeclaredField(name);z.setAccessible(true);return (List<CardInstance>)z.get(state);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    /** Setup only: relocate an existing owned physical card, never create a gameplay event or copy. */
    static CardInstance obtain(ServerMatch m,UUID p,String id){
        for(String z:List.of("hand","deck","prizes","discard","lostZone")){var list=zone(m,p,z);var c=list.stream().filter(v->v.definitionId().equals(id)).findFirst();
            if(c.isPresent()){
                if(z.equals("prizes")){
                    // Fixture access must not change the public prize count used by real Reversal Energy.
                    var replacement=zone(m,p,"deck").removeFirst();list.set(list.indexOf(c.get()),replacement);zone(m,p,"hand").add(c.get());
                }else if(!z.equals("hand")){list.remove(c.get());zone(m,p,"hand").add(c.get());}
                return c.get();}}
        throw new AssertionError("Missing existing physical fixture card "+id);
    }
    static UUID put(ServerMatch m,UUID p,String id){var c=obtain(m,p,id);m.playBasicPokemonToBench(p,c.instanceId());return PokemonIdentityTestSupport.logical(m,p,c.instanceId());}
    static CardInstance attach(ServerMatch m,UUID owner,UUID host,String id){var c=obtain(m,owner,id);m.attachEnergyByEffect(m.currentPlayer(),owner,EffectSourceZone.HAND,c.instanceId(),owner,host);return c;}
    static void cycle(ServerMatch m){UUID current=m.currentPlayer();m.endTurn(current);m.endTurn(current.equals(A)?B:A);}
    static void counters(ServerMatch m,UUID owner,UUID host,int amount){while(amount>0){int n=Math.min(60,amount);m.placeDamageCountersByEffect(m.currentPlayer(),owner,host,n);amount-=n;}}
    static UUID makeActive(ServerMatch m,UUID owner,String id){if(!m.currentPlayer().equals(owner))m.endTurn(m.currentPlayer());UUID host=put(m,owner,id);m.switchActiveByEffect(owner,owner,host);return active(m,owner);}
    static void play(ServerMatch m,UUID owner,String id,UUID target){m.playTrainer(owner,obtain(m,owner,id).instanceId(),target);}
    static void pickPokemon(ServerMatch m,UUID p,UUID host){var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),host);}
    static void submit(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void offer(ServerMatch m,UUID owner,UUID host){
        var q=m.triggerOrderPrompt();check(q!=null&&q.optionalDecision()&&q.choosingPlayer().equals(owner)&&q.trigger().name().equals("ON_BENCH_TO_ACTIVE")&&q.entries().size()==1,"Missing exact optional incoming Ability");
        check(q.entries().getFirst().pokemonId().equals(host),"Incoming event retargeted source");
    }
    static void decide(ServerMatch m,boolean use){var q=m.triggerOrderPrompt();m.decideTriggeredEffect(q.choosingPlayer(),q.id(),use);}
    static void chooseRoad(ServerMatch m,UUID host,List<CardInstance> selected){offer(m,A,host);decide(m,true);submit(m,A,selected);check(!m.hasPendingAttachmentTarget(),"Fixed captured destination incorrectly asks another host");}
    static void prizes(ServerMatch m){int n=20;while(m.phase()==MatchPhase.PRIZE_SELECTION&&n-->0)m.takePrizeAt(m.pendingChoicePlayer(),0);check(n>0,"Prize loop");}
    static void promote(ServerMatch m,UUID owner,UUID host){prizes(m);check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(owner),"Expected mandatory promotion");m.chooseReplacement(owner,host);}
    static boolean attached(ServerMatch m,UUID owner,UUID host,CardInstance card){return m.activePokemonView(owner)!=null&&active(m,owner).equals(host)?m.activePokemonView(owner).attachedEnergyIds().contains(card.instanceId()):m.benchPokemonView(owner).stream().filter(v->v.pokemonId().equals(host)).findFirst().orElseThrow().attachedEnergyIds().contains(card.instanceId());}
    static boolean metal(ServerMatch m,UUID owner,UUID host,int units){return m.matchesEffectCondition(owner,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("energy_type","METAL","count_mode","UNITS","value",""+units,"comparison","="),List.of()));}
    static void privacy(ServerMatch m){
        var s=MatchSession.multiplayer(m,A,B);s.addSpectator(WATCH);var rng=m.randomTranscript();
        for(UUID viewer:List.of(A,B,WATCH)){var snapshot=s.view(viewer,viewer.equals(WATCH)?null:viewer);String json=GSON.toJson(snapshot);
            check(!json.contains("effectProgram")&&!json.contains("capturedBench"),"Private execution proof leaked");
            for(UUID owner:List.of(A,B)){for(var c:m.privateView(owner).deck()){
                boolean entitledSearch=viewer.equals(owner)&&snapshot.game().effectSelection()!=null&&snapshot.game().effectSelection().visibleCards().contains(c);
                check(entitledSearch||!json.contains(c.instanceId().toString()),"Deck identity leaked outside issued private search");}
                if(!owner.equals(viewer))for(var c:m.privateView(owner).hand())check(!json.contains(c.instanceId().toString()),"Other hand identity leaked");}}
        check(m.randomTranscript().equals(rng),"Repeated views consumed RNG");
    }

    public static void main(String[] args){
        printed();retreatAndSwitch();surfer();attackSwitch();recoil();turnEnd();negativeTiming();usage();selection();specialEnergy();
        originalSuffix();sourceLifecycle();promotionHp();confusion();powerTackle();capacity();
        System.out.println("GeneratedRuleBatch96Test passed: real Cobalion prints, actual movement actors and Energy, captured-source/late-potential contract, authority, once usage, KO continuations,61-card session selection and120 synthetic snapshot");
    }
    static void printed(){
        for(String id:List.of(CARD,ALT)){
            var d=DB.find(id).orElseThrow();var p=DATA.pokemon(id).orElseThrow();
            check(d.effectSupport()==EffectSupport.SUPPORTED&&d.basicPokemon()&&d.regulationMark().equals("J")&&d.pokemonFamilies().containsAll(Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX)),"Actual Cobalion metadata "+id);
            check(p.hitPoints()==210&&p.pokemonType()==EnergyType.METAL&&p.weaknessType()==EnergyType.FIRE&&p.weaknessMultiplier()==2&&p.resistanceType()==EnergyType.GRASS&&p.resistanceAmount()==30&&p.retreatCost()==2&&p.prizeCards()==2,"Actual Cobalion profile");
            var a=p.abilities().getFirst();check(p.abilities().size()==1&&a.id().equals(ROAD)&&a.kind()==AbilityDefinition.Kind.TRIGGERED&&a.eventPolicy().trigger().name().equals("ON_BENCH_TO_ACTIVE")&&a.eventPolicy().optional()&&a.eventPolicy().usage()==EnergyTriggerPolicy.Usage.ONCE_PER_TURN,"Printed incoming Ability policy");
            var t=a.effectProgram().triggers().getFirst();check(t.conditions().isEmpty()&&t.effects().size()==1,"Unexpected MetalRoad program");
            var e=t.effects().getFirst();check(e.type().equals("move_energy")&&e.target()==EffectTarget.ALL_OWN_POKEMON&&e.arguments().equals(Map.of("amount","120","minimum","1","destination_scope","OWN_FIELD","destination_ref","SELF","energy_type","METAL","count_mode","CARDS")),"Exact120/min1 physical Metal transfer contract");
            var atk=p.attacks().getFirst();check(p.attacks().size()==1&&atk.id().equals(TACKLE)&&atk.baseDamage()==200&&atk.energyCost().equals(Map.of(EnergyType.METAL,2,EnergyType.COLORLESS,1)),"Actual Power Tackle");AttackProgramRules.validate(atk);
            var m=game();UUID host=put(m,A,id);check(m.triggerOrderPrompt()==null,"Hand to Bench invented incoming event");atomic(m,()->m.useAbility(A,host,ROAD));
        }
        check(DB.find("sv5_en_162").orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Completed Neo Upper remains disabled");
    }
    static void retreatAndSwitch(){
        for(String id:List.of(CARD,ALT))for(boolean firstTurn:List.of(false,true)){
            var m=game(DRAW,"NORMAL",0,!firstTurn);UUID old=active(m,A),c=put(m,A,id);var pay=attach(m,A,old,"water");var kept=attach(m,A,old,"metal");
            atomic(m,()->m.retreat(A,c,List.of()));atomic(m,()->m.retreat(B,c,List.of(pay.instanceId())));check(m.triggerOrderPrompt()==null,"Rejected retreat captured event");
            m.retreat(A,c,List.of(pay.instanceId()));offer(m,A,c);check(m.privateView(A).discard().contains(pay)&&attached(m,A,old,kept),"Movement/payment not completed before offer");chooseRoad(m,c,List.of(kept));
            check(attached(m,A,c,kept)&&m.currentPlayer().equals(A),"Actual incoming retreat transfer or continuation");atomic(m,()->m.retreat(A,old,List.of(kept.instanceId())));
        }
        for(String id:List.of(CARD,ALT)){var m=game();UUID old=active(m,A),c=put(m,A,id);var energy=attach(m,A,old,"metal");var card=obtain(m,A,SWITCH);m.playTrainer(A,card.instanceId(),c);
            offer(m,A,c);check(m.privateView(A).discard().contains(card),"Native Switch did not finish before Ability");chooseRoad(m,c,List.of(energy));check(m.currentPlayer().equals(A)&&!m.energyAttached(A),"Effect transfer consumed manual quota or ended turn");}
    }
    static void surfer(){
        for(boolean yes:List.of(false,true)){
            var m=game();UUID old=active(m,A),c=put(m,A,CARD);var energy=attach(m,A,old,"metal");var surfer=obtain(m,A,SURFER);
            for(var card:List.copyOf(zone(m,A,"hand")))if(!card.equals(surfer)){zone(m,A,"hand").remove(card);zone(m,A,"deck").addFirst(card);}
            var deck=m.privateView(A).deck().size();m.playTrainer(A,surfer.instanceId(),null);check(m.hasPendingPokemonSelection()&&m.triggerOrderPrompt()==null,"Surfer must choose before incoming event");
            var q=m.effectPokemonPrompt(A);atomic(m,()->m.chooseEffectPokemon(B,q.resolutionId(),q.step(),c));pickPokemon(m,A,c);
            offer(m,A,c);check(m.privateView(A).hand().size()==5&&m.privateView(A).deck().size()==deck-5&&m.privateView(A).discard().contains(surfer),"Official Surfer full draw before MetalRoad");
            privacy(m);decide(m,yes);if(yes)submit(m,A,List.of(energy));check(attached(m,A,yes?c:old,energy)&&m.privateView(A).hand().size()==5&&m.currentPlayer().equals(A),"Surfer continuation changed hand/owner");
            var second=obtain(m,A,SURFER);atomic(m,()->m.playTrainer(A,second.instanceId(),null));
        }
    }
    static void attackSwitch(){
        for(boolean yes:List.of(false,true)){
            var m=game();UUID abra=makeActive(m,A,ABRA),c=put(m,A,CARD);attach(m,A,abra,"psychic");var energy=attach(m,A,bench(m,A,"base"),"metal");int deck=m.privateView(B).deck().size();
            m.attack(A,"teleportation-attack");check(m.activePokemonView(B).damage()==10&&m.triggerOrderPrompt()==null,"Abra damage then its printed switch");pickPokemon(m,A,c);offer(m,A,c);
            check(m.currentPlayer().equals(A)&&m.privateView(B).deck().size()==deck,"Attack ended before incoming Ability");decide(m,yes);if(yes)submit(m,A,List.of(energy));
            check(m.currentPlayer().equals(B)&&m.privateView(B).deck().size()==deck-1&&m.activePokemonView(B).damage()==10,"Abra continuation did not end exactly once");
        }
    }
    static void recoil(){
        var m=game();UUID attacker=makeActive(m,A,CORPHISH),c=put(m,A,CARD);attach(m,A,attacker,"water");attach(m,A,attacker,"water");var energy=attach(m,A,bench(m,A,"base"),"metal");
        m.placeDamageCountersByEffect(A,A,attacker,7);int prizes=m.privateView(B).prizes().size(),deck=m.privateView(B).deck().size();m.attack(A,"take-down");
        check(m.triggerOrderPrompt()==null&&m.phase()==MatchPhase.PRIZE_SELECTION,"Recoil must settle mandatory KO before promotion event");promote(m,A,c);offer(m,A,c);
        check(m.privateView(B).prizes().size()==prizes-1&&m.currentPlayer().equals(A),"Recoil prize/turn ownership");chooseRoad(m,c,List.of(energy));
        check(m.currentPlayer().equals(B)&&m.privateView(B).deck().size()==deck-1&&m.activePokemonView(B).damage()==30,"Recoil promotion continuation");
    }
    static void turnEnd(){
        for(String actor:List.of(SLOWKING,JYNX)){
            var m=game();UUID c=put(m,A,CARD);var energy=attach(m,A,bench(m,A,"base"),"metal");m.endTurn(A);UUID attacker=makeActive(m,B,actor);
            attach(m,B,attacker,actor.equals(SLOWKING)?"dark":"psychic");if(actor.equals(SLOWKING))attach(m,B,attacker,"water");
            m.attack(B,actor.equals(SLOWKING)?"word-of-ruin":"intense-kiss");check(m.currentPlayer().equals(A),"Delayed actual attack did not begin target turn");
            int before=m.privateView(B).prizes().size(),deck=m.privateView(B).deck().size();m.endTurn(A);promote(m,A,c);offer(m,A,c);
            check(m.currentPlayer().equals(A)&&m.privateView(B).prizes().size()==before-(actor.equals(SLOWKING)?1:0),"Delayed KO/nonKO prize/owner boundary");
            chooseRoad(m,c,List.of(energy));check(m.currentPlayer().equals(B)&&m.privateView(B).deck().size()==deck-1,"Own end-turn promotion duplicated/skipped draw");
        }
    }
    static void negativeTiming(){
        var m=game();UUID c=put(m,A,CARD);attach(m,A,active(m,A),"metal");m.endTurn(A);play(m,B,BOSS,c);check(active(m,A).equals(c)&&m.triggerOrderPrompt()==null,"Opponent Boss produced own-turn event");
        for(boolean endByA:List.of(true,false)){
            var g=game();UUID receiver=put(g,A,CARD);attach(g,A,bench(g,A,"base"),"metal");counters(g,A,active(g,A),99);
            if(!endByA)g.endTurn(A);g.applySpecialConditionByEffect(g.currentPlayer(),A,SpecialCondition.POISONED);g.endTurn(g.currentPlayer());promote(g,A,receiver);
            check(g.triggerOrderPrompt()==null&&!g.hasPendingEffectChoice(),"Checkup promotion is not either player's turn");
        }
        var ko=game();UUID target=put(ko,A,CARD);attach(ko,A,bench(ko,A,"base"),"metal");counters(ko,A,active(ko,A),99);ko.endTurn(A);ko.attack(B,"hit");promote(ko,A,target);check(ko.triggerOrderPrompt()==null,"Opposing attack KO triggered MetalRoad");
    }
    static void usage(){
        var m=game();UUID first=put(m,A,CARD),second=put(m,A,ALT),old=active(m,A);CardInstance one=attach(m,A,old,"metal"),two=attach(m,A,old,"metal"),three=attach(m,A,old,"metal");
        play(m,A,SWITCH,first);offer(m,A,first);decide(m,false);play(m,A,SWITCH,old);play(m,A,SWITCH,first);chooseRoad(m,first,List.of(one));
        m.switchActiveByEffect(A,A,old);m.switchActiveByEffect(A,A,first);check(m.triggerOrderPrompt()==null,"Used same Pokemon reset after Bench roundtrip");
        m.switchActiveByEffect(A,A,second);chooseRoad(m,second,List.of(two));check(attached(m,A,first,one)&&attached(m,A,second,two),"Distinct real prints shared a usage limit");
        cycle(m);m.switchActiveByEffect(A,A,old);m.switchActiveByEffect(A,A,first);chooseRoad(m,first,List.of(three));check(m.currentPlayer().equals(A),"Next-own-turn use/normal switch continuation");
        var empty=game();UUID c=put(empty,A,CARD);play(empty,A,SWITCH,c);check(empty.triggerOrderPrompt()==null,"No-change pure Ability offered without donors");attach(empty,A,bench(empty,A,"base"),"metal");check(empty.triggerOrderPrompt()==null,"Later Energy attachment invented a manual late opportunity");atomic(empty,()->empty.useAbility(A,c,ROAD));
    }
    static void selection(){
        var m=game();UUID c=put(m,A,CARD),old=active(m,A),other=bench(m,A,"base");CardInstance a=attach(m,A,old,"metal"),b=attach(m,A,other,"metal"),kept=attach(m,A,old,"metal");
        CardInstance self=attach(m,A,c,"metal"),wrong=attach(m,A,old,"water"),foreign=attach(m,B,active(m,B),"metal"),handOnly=obtain(m,A,"metal");
        play(m,A,SWITCH,c);offer(m,A,c);var offered=m.triggerOrderPrompt();atomic(m,()->m.decideTriggeredEffect(B,offered.id(),true));atomic(m,()->m.decideTriggeredEffect(WATCH,offered.id(),true));decide(m,true);
        var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==3&&new HashSet<>(q.candidates()).equals(Set.of(a,b,kept)),"Only other own currently Metal physical attachments qualify");
        for(var bad:List.of(self,wrong,foreign,handOnly))atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(a.instanceId(),bad.instanceId())));
        atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(a.instanceId(),a.instanceId())));
        atomic(m,()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(a.instanceId())));atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step()+1,List.of(a.instanceId())));
        atomic(m,()->m.decideTriggeredEffect(A,offered.id(),true));privacy(m);submit(m,A,List.of(a,b));
        check(attached(m,A,c,a)&&attached(m,A,c,b)&&attached(m,A,c,self)&&attached(m,A,old,kept)&&attached(m,A,old,wrong)&&attached(m,B,active(m,B),foreign)&&!m.energyAttached(A),"Subset/untouched cards/hand quota corrupted");
        atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(kept.instanceId())));
        for(boolean accepted:List.of(false,true)){var quit=game();UUID host=put(quit,A,CARD);attach(quit,A,active(quit,A),"metal");play(quit,A,SWITCH,host);if(accepted)decide(quit,true);int deck=quit.privateView(B).deck().size();quit.concede(A);check(quit.phase()==MatchPhase.FINISHED&&quit.privateView(B).deck().size()==deck&&quit.triggerOrderPrompt()==null&&!quit.hasPendingEffectChoice(),"Concession leaked pending movement/turn continuation");}
    }
    static UUID evolveAmpharos(ServerMatch m,boolean stage2){UUID host=put(m,A,MAREEP);cycle(m);m.evolve(A,host,obtain(m,A,FLAAFFY).instanceId());if(stage2){cycle(m);m.evolve(A,host,obtain(m,A,AMPHAROS).instanceId());}return host;}
    static void specialEnergy(){
        var m=game();UUID c=put(m,A,CARD),donor=active(m,A);var magnet=attach(m,A,donor,MAGNET);play(m,A,SWITCH,c);chooseRoad(m,c,List.of(magnet));
        check(attached(m,A,c,magnet)&&metal(m,A,c,1)&&m.activeRetreatCost(A)==0,"Actual Magnet Metal did not move/read new Metal host");m.retreat(A,donor,List.of());
        for(boolean deficit:List.of(false,true)){
            var g=game();UUID evolved=evolveAmpharos(g,false),receiver=put(g,A,CARD);var reversal=attach(g,A,evolved,REVERSAL);
            if(deficit){g.knockOutPokemonByEffect(A,A,bench(g,A,"base"));prizes(g);}
            check(metal(g,A,evolved,deficit?3:0),"Actual Reversal live donor qualification");play(g,A,SWITCH,receiver);
            if(!deficit){check(g.triggerOrderPrompt()==null,"Colorless-only Reversal wrongly eligible");continue;}
            chooseRoad(g,receiver,List.of(reversal));check(attached(g,A,receiver,reversal)&&metal(g,A,receiver,0),"Reversal split/cached three Metal units after moving to Basic ex");
            atomic(g,()->g.attack(A,TACKLE));attach(g,A,receiver,"metal");atomic(g,()->g.attack(A,TACKLE));attach(g,A,receiver,"metal");g.attack(A,TACKLE);check(g.lastAttack().finalDamage()==200,"Moved actual Reversal must supply one Colorless with two BasicMetal");
        }
        var neo=game();UUID stage2=evolveAmpharos(neo,true),receiver=put(neo,A,CARD);check(neo.benchPokemonView(A).stream().anyMatch(v->v.pokemonId().equals(stage2)&&v.evolutionCardIds().equals(List.of(MAREEP,FLAAFFY,AMPHAROS))),"Real named Stage2 donor setup");
        var equivalent=attach(neo,A,stage2,"evolved-double");check(metal(neo,A,stage2,2),"Explicit evolved-host all-type fixture");play(neo,A,SWITCH,receiver);chooseRoad(neo,receiver,List.of(equivalent));check(metal(neo,A,receiver,0)&&attached(neo,A,receiver,equivalent),"Synthetic whole-card supply must be recomputed at Basic destination");
    }
    static void originalSuffix(){
        String add="{\"type\":\"attach_energy\",\"target\":\"SELECT_OWN_POKEMON\",\"source\":\"HAND\",\"target_scope\":\"OWN_FIELD\",\"amount\":1,\"minimum\":1,\"basic_energy_only\":true,\"energy_type\":\"METAL\"}";
        var m=game(SW+","+SEARCH+","+add,"NORMAL");UUID c=put(m,A,CARD),donor=active(m,A);obtain(m,A,"metal");play(m,A,"item",null);pickPokemon(m,A,c);
        check(m.hasPendingCardSelection()&&m.triggerOrderPrompt()==null,"Original search must pause before no-donor incoming event");privacy(m);var search=m.effectCardPrompt(A);submit(m,A,List.of(search.candidates().getFirst()));
        check(m.triggerOrderPrompt()==null&&m.hasPendingCardSelection(),"Original attachment must finish before MetalRoad");var attach=m.effectCardPrompt(A);var chosen=attach.candidates().stream().filter(v->v.definitionId().equals("metal")).findFirst().orElseThrow();submit(m,A,List.of(chosen));
        var host=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,host.resolutionId(),host.step(),donor);offer(m,A,c);chooseRoad(m,c,List.of(chosen));check(attached(m,A,c,chosen),"Capture-time empty pool was dropped before suffix could add Metal");
        var back=game(SW+","+SW,"NORMAL");UUID original=active(back,A),source=put(back,A,CARD);var energy=attach(back,A,original,"metal");play(back,A,"item",null);pickPokemon(back,A,source);check(back.triggerOrderPrompt()==null,"First switch drained before original second switch");pickPokemon(back,A,original);offer(back,A,source);chooseRoad(back,source,List.of(energy));check(active(back,A).equals(original)&&attached(back,A,source,energy),"Captured Bench source must receive from current Active donor");
        String remove="{\"type\":\"discard_energy\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":1,\"minimum\":1,\"energy_type\":\"METAL\",\"count_mode\":\"CARDS\"}";
        var gone=game(SW+","+remove,"NORMAL");UUID receiver2=put(gone,A,CARD);var removed=attach(gone,A,active(gone,A),"metal");play(gone,A,"item",null);pickPokemon(gone,A,receiver2);submit(gone,A,List.of(removed));check(gone.triggerOrderPrompt()==null&&!gone.hasPendingEffectChoice()&&gone.privateView(A).discard().contains(removed),"Removed final donor still opened stale incoming selection");
    }
    static void sourceLifecycle(){
        var pre=game(SW+",{\"type\":\"discard_stadium\"}","NORMAL");UUID c=put(pre,A,CARD);attach(pre,A,active(pre,A),"metal");play(pre,A,"field",null);play(pre,A,"item",null);pickPokemon(pre,A,c);check(pre.triggerOrderPrompt()==null,"Removing suppression cannot invent a missing capture");
        var post=game(SW+",{\"type\":\"disable_abilities\",\"target\":\"OWN_ACTIVE\"}","NORMAL");UUID p=put(post,A,CARD);var energy=attach(post,A,active(post,A),"metal");play(post,A,"item",null);pickPokemon(post,A,p);offer(post,A,p);chooseRoad(post,p,List.of(energy));check(attached(post,A,p,energy),"Existing captured-program inference lost event after later suppression");
        var left=game(SW+",{\"type\":\"discard_pokemon\",\"target\":\"OWN_ACTIVE\"}","NORMAL");UUID removed=put(left,A,CARD);var kept=attach(left,A,active(left,A),"metal");play(left,A,"item",null);pickPokemon(left,A,removed);prizes(left);
        if(left.phase()==MatchPhase.CHOOSE_REPLACEMENT)left.chooseReplacement(A,bench(left,A,"base"));check(left.triggerOrderPrompt()==null&&!left.hasPendingEffectChoice()&&left.privateView(A).discard().stream().anyMatch(v->v.definitionId().equals(CARD)),"Departed source retained old transfer opportunity");
        check(left.activePokemonView(A).attachedEnergyIds().contains(kept.instanceId())||left.benchPokemonView(A).stream().anyMatch(v->v.attachedEnergyIds().contains(kept.instanceId())),"Stale source transferred another host's card");
    }
    static void promotionHp(){
        // Synthetic Bench-only HP Tool tests actual Cobalion; it is not an additional printed Ability.
        var m=game();UUID c=put(m,A,CARD),donor=active(m,A);var kept=attach(m,A,donor,"metal");play(m,A,"bench-hp",c);m.placeDamageCountersByEffect(A,A,c,22);
        check(m.benchPokemonView(A).stream().anyMatch(v->v.pokemonId().equals(c)&&v.hitPoints()==310&&v.damage()==220),"Bench-only HP setup");play(m,A,SWITCH,c);
        check(m.triggerOrderPrompt()==null&&m.phase()==MatchPhase.PRIZE_SELECTION,"Already zeroHP source offered a rescue before mandatory KO");promote(m,A,donor);
        check(m.triggerOrderPrompt()==null&&attached(m,A,donor,kept)&&m.privateView(B).prizes().size()==4&&m.currentPlayer().equals(A),"Promotion HP KO/prizes/normal turn continuation");
    }
    static void confusion(){
        for(boolean yes:List.of(false,true)){
            boolean tested=false;
            for(int seed=0;seed<200&&!tested;seed++){
                var m=game(DRAW,"NORMAL",seed,true);UUID attacker=makeActive(m,A,CORPHISH),c=put(m,A,CARD);attach(m,A,attacker,"water");attach(m,A,attacker,"water");var energy=attach(m,A,bench(m,A,"base"),"metal");
                m.placeDamageCountersByEffect(A,A,attacker,5);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);
                int deck=m.privateView(B).deck().size(),rng=m.randomTranscript().size();m.attack(A,"take-down");if(m.lastAttack().finalDamage()!=0)continue;
                tested=true;check(m.lastAttack().selfDamage()==30&&m.activePokemonView(B).damage()==0&&m.randomTranscript().size()==rng+1,"Failed Confusion must precede printed attack/self10");promote(m,A,c);offer(m,A,c);
                check(m.currentPlayer().equals(A)&&m.privateView(B).deck().size()==deck,"False-continue path ended before incoming event");var q=m.triggerOrderPrompt();atomic(m,()->m.decideTriggeredEffect(B,q.id(),yes));atomic(m,()->m.endTurn(A));decide(m,yes);
                if(yes){var choose=m.effectCardPrompt(A);atomic(m,()->m.submitEffectCards(A,choose.resolutionId(),choose.step(),List.of()));submit(m,A,List.of(energy));}
                check(m.currentPlayer().equals(B)&&m.privateView(B).deck().size()==deck-1&&m.activePokemonView(B).damage()==10&&m.randomTranscript().size()==rng+1,"Confusion promotion must end/checkup/draw exactly once after accept/decline");
            }
            check(tested,"No deterministic tails Confusion fixture");
        }
    }
    static void powerTackle(){
        for(String id:List.of(CARD,ALT))for(String mode:List.of("NORMAL","WEAK","RESIST")){
            var m=game(DRAW,mode);UUID c=makeActive(m,A,id);atomic(m,()->m.attack(A,TACKLE));attach(m,A,c,"metal");atomic(m,()->m.attack(A,TACKLE));attach(m,A,c,"metal");atomic(m,()->m.attack(A,TACKLE));attach(m,A,c,"water");m.attack(A,TACKLE);
            check(m.lastAttack().finalDamage()==(mode.equals("WEAK")?400:mode.equals("RESIST")?170:200),"Actual PowerTackle damage/cost/W/R");m.endTurn(B);atomic(m,()->m.attack(A,TACKLE));m.endTurn(A);m.endTurn(B);m.attack(A,TACKLE);check(m.currentPlayer().equals(B),"PowerTackle cooldown expiry");
        }
        var clear=game();UUID c=makeActive(clear,A,CARD);attach(clear,A,c,"metal");attach(clear,A,c,"metal");attach(clear,A,c,"water");clear.attack(A,TACKLE);clear.endTurn(B);atomic(clear,()->clear.attack(A,TACKLE));clear.switchActiveByEffect(A,A,bench(clear,A,"base"));clear.switchActiveByEffect(A,A,c);check(clear.triggerOrderPrompt()==null,"No donor when clearing cooldown unexpectedly offers use");clear.attack(A,TACKLE);
        var copy=game();copy.endTurn(A);UUID donor=makeActive(copy,B,CARD);copy.endTurn(B);attach(copy,A,active(copy,A),"water");var choice=copy.copyAttackOptions(A,"copy").stream().filter(v->v.attackId().equals(TACKLE)).findFirst().orElseThrow();
        atomic(copy,()->copy.copyAttackWithEnergyDiscards(B,"copy",choice.pokemonId(),choice.entrySequence(),choice.attackId(),List.of(),List.of()));copy.copyAttackWithEnergyDiscards(A,"copy",choice.pokemonId(),choice.entrySequence(),choice.attackId(),List.of(),List.of());check(copy.lastAttack().finalDamage()==200,"Copied real PowerTackle");copy.endTurn(B);atomic(copy,()->copy.attack(A,"hit"));atomic(copy,()->copy.attack(A,"copy"));
        var prizes=game();UUID victim=put(prizes,A,CARD);prizes.knockOutPokemonByEffect(A,A,victim);prizes(prizes);check(prizes.privateView(B).prizes().size()==4,"Real Cobalion must award two prizes");
    }
    static void capacity(){
        for(boolean all:List.of(false,true)){
            var m=game(DRAW,"CAPACITY",0,true);UUID c=put(m,A,CARD),one=active(m,A),two=bench(m,A,"base");var energies=new ArrayList<CardInstance>();
            for(int i=0;i<35;i++)energies.add(attach(m,A,one,"metal"));
            var foreign=new ArrayList<CardInstance>();for(int i=0;i<26;i++)foreign.add(attach(m,B,active(m,B),"metal"));
            // Explicit setup-only generic transfer: no currently supported real stealing card is asserted.
            m.moveAttachedEnergyByEffect(A,B,active(m,B),A,two,foreign.stream().map(CardInstance::instanceId).toList());energies.addAll(foreign);
            m.switchActiveByEffect(A,A,c);offer(m,A,c);decide(m,true);var prompt=m.effectCardPrompt(A);check(prompt.minimum()==1&&prompt.maximum()==61&&new HashSet<>(prompt.candidates()).equals(new HashSet<>(energies)),"61 physical candidates were clipped or filtered by original owner");
            var session=MatchSession.multiplayer(m,A,B);var initial=session.view(A,A);var pages=new LinkedHashSet<String>();
            for(int page=0;page<13;page++){var view=EffectCardPage.from(initial,page);check(view.pages()==13,"61 candidates need13 pages");for(var card:view.cards())pages.add(card.key());}
            check(pages.size()==61,"Final page/candidate unreachable");
            var chosen=all?List.copyOf(energies):List.of(energies.getFirst(),energies.get(30),energies.getLast());
            for(var energy:chosen){var view=session.view(A,A);var option=view.options().stream().filter(o->o.cardKeys().contains("effect:"+energy.instanceId())).findFirst().orElseThrow();
                reject(()->session.submit(B,A,view.revision(),option.index()));session.submit(A,A,view.revision(),option.index());reject(()->session.submit(A,A,view.revision(),option.index()));}
            var selected=session.view(A,A);check(selected.game().effectSelection().selected().size()==chosen.size(),"Session toggles lost >60 selection");snapshotCapacity(selected,61);synthetic120(selected);
            var confirm=selected.options().stream().filter(o->o.cardKeys().isEmpty()&&o.labelZh().contains("确认选牌")).findFirst().orElseThrow();session.submit(A,A,selected.revision(),confirm.index());
            for(var energy:energies){boolean selectedCard=chosen.contains(energy);check(attached(m,A,selectedCard?c:energy.ownerId().equals(A)?one:two,energy),"61 all/subset identity changed");check(energy.ownerId().equals(foreign.contains(energy)?B:A),"Transfer rewrote original owner");}
            // Return selected attachments through the existing owner-aware hook to prove ownership remains executable, not merely a local record assertion.
            m.returnAttachedEnergyToHandByEffect(A,A,c,chosen.stream().map(CardInstance::instanceId).toList());for(var energy:chosen)check(m.privateView(energy.ownerId()).hand().contains(energy),"Foreign-owned card returned to wrong player's hand");
            check(m.currentPlayer().equals(A)&&!m.energyAttached(A),"Large transfer ended turn/charged manual attachment");
        }
    }
    static void snapshotCapacity(MatchSession.Snapshot snapshot,int candidates){
        String json=GSON.toJson(snapshot);check(json.length()<250000,"Snapshot exceeds existing transport string bound");var round=GSON.fromJson(json,MatchSession.Snapshot.class);
        check(round.game().effectSelection().candidates().equals(snapshot.game().effectSelection().candidates())&&round.game().effectSelection().selected().equals(snapshot.game().effectSelection().selected())&&round.options().equals(snapshot.options()),"Gson snapshot round-trip lost public selection/action data");
        check(MatchRegionModel.from(round).zone("effect-selection").cards().size()==candidates,"Region mapping clipped candidates");
    }
    static void synthetic120(MatchSession.Snapshot real61){
        // Capacity/DTO fixture only: not a claim that120 Energy plus Pokemon fit inside a conserved two-deck game.
        var json=GSON.toJsonTree(real61).getAsJsonObject();var selection=json.getAsJsonObject("game").getAsJsonObject("effectSelection");var candidates=new JsonArray();var selected=new JsonArray();var options=new JsonArray();
        for(int i=0;i<120;i++){var card=new CardInstance(new UUID(961200,i+1),i%2==0?A:B,"metal");candidates.add(GSON.toJsonTree(card));if(i%3==0)selected.add(card.instanceId().toString());options.add(GSON.toJsonTree(new MatchSession.Option(i,"Explicit synthetic capacity option",List.of("effect:"+card.instanceId()))));}
        selection.add("candidates",candidates);selection.add("selected",selected);selection.addProperty("minimum",1);selection.addProperty("maximum",120);selection.add("inspectedCards",new JsonArray());json.add("options",options);
        var snapshot=GSON.fromJson(json,MatchSession.Snapshot.class);snapshotCapacity(snapshot,120);var last=EffectCardPage.from(snapshot,23);check(last.pages()==24&&last.cards().size()==5&&last.cards().getLast().key().equals("effect:"+new UUID(961200,120)),"120 synthetic last-page access");
    }
}
