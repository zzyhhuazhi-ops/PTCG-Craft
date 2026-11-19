import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Garbodor, Trubbish, Chespin and Brave Bangle; unsupported Jamming Tower uses an explicit suppression hook only. */
public final class GeneratedRuleBatch93Test {
    static final UUID A=new UUID(0,9311),B=new UUID(0,9312);
    static final String BASIC="me4_en_056",CARD="me4_en_057",ABILITY="gloomy-garbage",ATTACK="sludge-bomb";
    static final String CHESPIN="me4_en_005",BANGLE="me5_en_104",KANGA="me1_en_104";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void atomic(ServerMatch m,Runnable r){var a=m.privateView(A);var b=m.privateView(B);var view=m.clientView(A);var rng=m.randomTranscript();reject(r);check(a.equals(m.privateView(A))&&b.equals(m.privateView(B))&&view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Rejected action changed state/RNG");}
    static CardDefinition synthetic(String id,CardCategory category,TrainerCategory trainer,boolean basic){return new CardDefinition(id,id,category,trainer,"J",Set.of("international"),basic,category==CardCategory.ENERGY,60,Map.of(),EffectSupport.SUPPORTED,null);}
    static AttackDefinition attack(String id,int damage,String extra){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\""+id+"\",\"energyCost\":{},\"baseDamage\":"+damage+",\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\""+extra+"}").getAsJsonObject(),EffectRegistry.standard());}
    static AbilityDefinition capacity(){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"synthetic-capacity\",\"nameZh\":\"Synthetic capacity\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"tool_capacity\",\"amount\":2}]}").getAsJsonObject(),EffectRegistry.standard());}
    static AbilityDefinition receivedBonus(){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"synthetic-received-bonus\",\"nameZh\":\"Synthetic received bonus\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"attack_damage_modifier\",\"target\":\"SELF\",\"direction\":\"RECEIVED\",\"amount\":30}]}").getAsJsonObject(),EffectRegistry.standard());}
    static String benches(){return "{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":40},{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":40}";}
    static String after(String effects){return ",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+effects+"]}]";}
    static ServerMatch game(int count,boolean sourceActive,String mode){
        boolean own=mode.equals("OWN"),faq=mode.equals("FAQ");UUID owner=own?A:B;
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(BASIC,CARD,CHESPIN,KANGA)){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}defs.add(DB.find(BANGLE).orElseThrow());
        defs.add(synthetic("base",CardCategory.POKEMON,TrainerCategory.NONE,true));for(String id:List.of("dark","wrong","grass"))defs.add(synthetic(id,CardCategory.ENERGY,TrainerCategory.NONE,false));for(String id:List.of("tool","reaction"))defs.add(synthetic(id,CardCategory.TRAINER,TrainerCategory.TOOL,false));
        var attacks=List.of(attack("hit",100,""),attack("forty",40,""),attack("bench-use",100,",\"mayUseFromBench\":true"),CopyAttackTest.copier(),
            attack("mixed",40,",\"damageTarget\":\"ALL_POKEMON\",\"selfDamage\":30,\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}]}]"),
            attack("secondary",0,after(benches())),attack("staged",40,after(benches())),
            attack("before-devolve",40,",\"triggers\":[{\"trigger\":\"BEFORE_ATTACK\",\"effects\":[{\"type\":\"devolve\",\"target\":\"OPPONENT_ACTIVE\"}]},{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+benches()+"]}]"),
            attack("recoil",0,",\"selfDamage\":30"),attack("composed-recoil",0,after("{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":30}")),
            attack("self-counters",0,after("{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":2}")),
            attack("counters",0,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}]}]"));
        profiles.put("base",new PokemonProfile(1000,2,null,mode.equals("WEAK")?EnergyType.GRASS:null,mode.equals("WEAK")?2:0,
            mode.startsWith("RESIST")?EnergyType.GRASS:null,mode.startsWith("RESIST")?30:0,1,attacks,mode.equals("RESIST_FLOOR")?List.of(capacity(),receivedBonus()):List.of(capacity()),EnergyType.COLORLESS));
        var catalog=new CombatCatalog(profiles,Map.of("dark",EnergyType.DARKNESS,"wrong",EnergyType.WATER,"grass",EnergyType.GRASS),
            Map.of("tool",DynamicPassiveModifierTest.plainTool(),BANGLE,DATA.trainer(BANGLE).orElseThrow(),"reaction",PersistentSourceTriggerTest.trainer("TOOL",PersistentSourceTriggerTest.trigger("synthetic-devolve-on-damage",PersistentSourceTriggerTest.DAMAGE,"{\"type\":\"devolve\",\"target\":\"SELF\"}"))));
        String[] pool={BASIC,BASIC,BASIC,BASIC,CARD,CARD,CARD,CARD,"base","base","base","base","base","base",
            "dark","dark","dark","dark","wrong","wrong","grass","grass","tool","tool","tool","tool",BANGLE,BANGLE,CHESPIN,CHESPIN,KANGA,KANGA,"reaction","reaction"};
        String af=own?BASIC:faq?CHESPIN:"base",bf=own?"base":sourceActive?BASIC:faq?KANGA:"base";
        for(int seed=0;seed<6000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool[i%pool.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().filter(c->c.definitionId().equals(af)).count()<(af.equals("base")?2:1)
                ||m.privateView(B).hand().stream().filter(c->c.definitionId().equals(bf)).count()<(bf.equals("base")?2:1)
                ||List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("base")))
                ||m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(BASIC)).count()<2)continue;
            m.chooseOpeningActive(A,hand(m,A,af).instanceId());m.chooseOpeningActive(B,hand(m,B,bf).instanceId());
            m.placeOpeningBench(A,hand(m,A,"base").instanceId());m.placeOpeningBench(B,hand(m,B,"base").instanceId());
            int need=(own||sourceActive)?1:2;for(int i=0;i<need;i++)m.placeOpeningBench(owner,hand(m,owner,BASIC).instanceId());
            m.startAfterOpening(A);m.startAfterOpening(B);OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(CARD)).count()<2||m.privateView(A).hand().stream().filter(c->c.definitionId().equals("dark")).count()<2
                ||m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).count()<2||m.privateView(B).hand().stream().noneMatch(c->c.definitionId().equals("tool"))
                ||(mode.equals("REACTION")&&m.privateView(B).hand().stream().noneMatch(c->c.definitionId().equals("reaction")))
                ||List.of("wrong","grass",BANGLE).stream().anyMatch(id->m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            if(owner.equals(B))m.endTurn(A);var hosts=new ArrayList<UUID>();if(m.activePokemonView(owner).topCardId().equals(BASIC))hosts.add(active(m,owner));
            hosts.addAll(m.benchPokemonView(owner).stream().filter(v->v.topCardId().equals(BASIC)).map(v->v.pokemonId()).toList());
            for(int i=0;i<count;i++)m.evolve(owner,hosts.get(i),hand(m,owner,CARD).instanceId());if(owner.equals(B))m.endTurn(B);return m;
        }throw new AssertionError("No actual Garbodor fixture "+count+"/"+sourceActive+"/"+mode);
    }
    static ServerMatch game(int n){return game(n,false,"NORMAL");}
    static List<UUID> sources(ServerMatch m,UUID owner){var out=new ArrayList<UUID>();if(m.activePokemonView(owner).topCardId().equals(CARD))out.add(active(m,owner));out.addAll(m.benchPokemonView(owner).stream().filter(v->v.topCardId().equals(CARD)).map(v->v.pokemonId()).toList());return out;}
    static CardInstance tool(ServerMatch m,UUID owner,UUID host,String id){var c=hand(m,owner,id);m.attachToolByEffect(A,owner,EffectSourceZone.HAND,c.instanceId(),owner,host);return c;}
    static void energy(ServerMatch m,String id){m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,id).instanceId(),A,active(m,A));}
    static void strike(ServerMatch m,int damage){int before=m.activePokemonView(B).damage();var rng=m.randomTranscript();m.attack(A,"hit");check(m.lastAttack().finalDamage()==damage&&m.activePokemonView(B).damage()==before+damage&&m.randomTranscript().equals(rng),"Actual passive damage expected "+damage);}
    static void back(ServerMatch m){m.endTurn(B);}
    static void shield(ServerMatch m,String origin,String scope,String target){var e=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield(origin,scope,target,"LEAVES_PLAY")).getAsJsonObject());m.executeEffectProgramByRule(A,active(m,A),new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e)))),EffectTrigger.ON_CARD_PLAYED);}
    public static void main(String[] args){printedAndCost();sourcesAndToolCount();toolOwnerAndLive();sourceLifecycle();realBraveBangleFaq();towerSuppressionAnalogue();damageScopeAndOrder();secondaryAndSnapshot();selfDamageAndCounters();resistanceStopsCalculation();actualCopiedSource();protection();System.out.println("GeneratedRuleBatch93Test passed: actual Garbodor named evolution/DDC100, live affected Active physical Tool and source stacking, real Chespin Beat+Brave Bangle FAQ, explicit unsupported-Tower suppression analogue, source lifetime/suppression, pre-WR/zero-floor and mixed attack damage/counters, native/composed self damage, secondary damage snapshot, Bench attacking source and actual copied Sludge Bomb, source-kind protection");}
    static void printedAndCost(){
        var p=DATA.pokemon(CARD).orElseThrow();check(DB.find(CARD).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&p.hitPoints()==140&&p.retreatCost()==3&&p.prizeCards()==1
            &&p.pokemonType()==EnergyType.DARKNESS&&p.weaknessType()==EnergyType.FIGHTING&&p.weaknessMultiplier()==2&&p.resistanceType()==null&&p.evolvesFromName().equals("Trubbish"),"Actual Garbodor profile");
        var a=p.abilities().getFirst();check(p.abilities().size()==1&&a.kind()==AbilityDefinition.Kind.PASSIVE&&a.id().equals(ABILITY),"Actual passive kind/name");
        var e=a.effectProgram().triggers().getFirst().effects().getFirst();check(e.type().equals("attack_damage_modifier")&&e.target()==EffectTarget.OPPONENT_ACTIVE
            &&e.arguments().get("amount").equals("-20")&&e.arguments().get("direction").equals("DEALT")&&e.conditions().size()==1&&e.conditions().getFirst().type()==ConditionType.HAS_TOOL,"Affected-attacker physical Tool modifier schema");
        var attack=p.attacks().getFirst();check(p.attacks().size()==1&&attack.id().equals(ATTACK)&&attack.baseDamage()==100&&attack.energyCost().equals(Map.of(EnergyType.DARKNESS,2,EnergyType.COLORLESS,1)),"Actual Sludge Bomb cost/damage");
        var m=game(1,true,"OWN");check(m.activePokemonView(A).evolutionCardIds().equals(List.of(BASIC,CARD)),"Normal actual Trubbish evolution");
        atomic(m,()->m.useAbility(A,active(m,A),ABILITY));atomic(m,()->m.attack(A,ATTACK));energy(m,"wrong");energy(m,"dark");atomic(m,()->m.attack(A,ATTACK));energy(m,"dark");
        tool(m,A,active(m,A),"tool");m.attack(A,ATTACK);check(m.lastAttack().finalDamage()==100,"Own Garbodor source reduced its own Tool-bearing attack");
    }
    static void sourcesAndToolCount(){
        for(int n:List.of(0,1,2))for(int tools:List.of(0,1,2)){
            var m=game(n);for(int i=0;i<tools;i++)tool(m,A,active(m,A),"tool");strike(m,100-(tools==0?0:20*n));
        }
        for(int n:List.of(1,2)){var m=game(n,true,"NORMAL");tool(m,A,active(m,A),"tool");strike(m,100-20*n);}
    }
    static void toolOwnerAndLive(){
        var sourceTool=game(1);tool(sourceTool,B,sources(sourceTool,B).getFirst(),"tool");strike(sourceTool,100);
        var m=game(1);UUID original=active(m,A);var t=tool(m,A,original,"tool");strike(m,80);back(m);
        m.returnAttachedToolToHandByEffect(A,A,original,t.instanceId());strike(m,100);back(m);tool(m,A,original,"tool");strike(m,80);back(m);
        UUID fresh=m.benchPokemonView(A).getFirst().pokemonId();m.switchActiveByEffect(A,A,fresh);strike(m,100);back(m);m.switchActiveByEffect(A,A,original);strike(m,80);
    }
    static void sourceLifecycle(){
        var m=game(2);tool(m,A,active(m,A),"tool");var hosts=sources(m,B);m.disableAbilityEffectsByEffect(A,B,hosts.getFirst());strike(m,80);back(m);strike(m,60);back(m);
        m.devolvePokemonToHandByEffect(A,B,hosts.getFirst());strike(m,80);back(m);m.movePokemonByEffect(A,B,hosts.getLast(),LeavePlayDestination.HAND);strike(m,100);
        var switched=game(1);tool(switched,A,active(switched,A),"tool");UUID holder=sources(switched,B).getFirst();switched.switchActiveByEffect(A,B,holder);strike(switched,80);
    }
    static void realBraveBangleFaq(){
        check(DATA.pokemon(CHESPIN).orElseThrow().attacks().getFirst().id().equals("beat"),"FAQ Chespin actual English attack is Beat, not Tackle");
        for(int n:List.of(0,1,2)){var m=game(n,false,"FAQ");tool(m,A,active(m,A),BANGLE);energy(m,"grass");m.attack(A,"beat");check(m.lastAttack().finalDamage()==Math.max(0,10+30-20*n),"Actual Chespin10+BraveBangle30-Garbodor sources before zero floor");}
    }
    static void towerSuppressionAnalogue(){
        // Real Jamming Tower prints remain unsupported; this hook isolates its already-known Tool-effect suppression semantics.
        var m=game(1,false,"FAQ");var tool=tool(m,A,active(m,A),BANGLE);energy(m,"grass");m.disableToolEffectsByEffect(A,A,active(m,A));m.attack(A,"beat");
        check(m.activePokemonView(A).attachedTools().contains(tool)&&m.lastAttack().finalDamage()==0,"Physical disabled Tool must retain Garbodor-20 while Brave Bangle+30 is disabled; without possession result would be10");
        var plain=game(1);var attached=tool(plain,A,active(plain,A),"tool");plain.disableToolEffectsByEffect(A,A,active(plain,A));strike(plain,80);
        check(plain.activePokemonView(A).attachedTools().contains(attached),"Suppression removed physical Tool");
    }
    static void damageScopeAndOrder(){
        for(String mode:List.of("WEAK","RESIST")){var m=game(1,false,mode);tool(m,A,active(m,A),"tool");strike(m,mode.equals("WEAK")?160:50);}
        var mixed=game(1);tool(mixed,A,active(mixed,A),"tool");mixed.attack(A,"mixed");
        check(mixed.activePokemonView(B).damage()==40&&mixed.activePokemonView(A).damage()==30
            &&mixed.benchPokemonView(A).stream().allMatch(v->v.damage()==20)&&mixed.benchPokemonView(B).stream().allMatch(v->v.damage()==20),"Garbodor reduces both20 field damage and30 self-damage to10; separate2 counters remain20 (official advanced rule B-09)");
        var counters=game(2);tool(counters,A,active(counters,A),"tool");counters.attack(A,"counters");check(counters.lastAttack().finalDamage()==0&&counters.activePokemonView(B).damage()==20,"Damage modifiers changed counters or generated damage from printed0");
        var bench=game(1);UUID host=bench.benchPokemonView(A).getFirst().pokemonId();tool(bench,A,host,"tool");tool(bench,A,active(bench,A),"tool");
        var option=bench.benchAttackOptions(A).stream().filter(v->v.pokemonId().equals(host)&&v.attack().id().equals("bench-use")).findFirst().orElseThrow();
        bench.attackFromBench(A,host,option.entry(),option.topCardId(),option.attack().id());check(bench.lastAttack().finalDamage()==100,"Opponent Active modifier leaked to actual Bench attacker");
    }
    static void secondaryAndSnapshot(){
        // Explicit synthetic borrowed attacks/Tool isolate timing; actual Garbodor and its printed passive are unchanged.
        var secondary=game(1);tool(secondary,A,active(secondary,A),"tool");secondary.attack(A,"secondary");
        check(secondary.activePokemonView(A).damage()==0&&secondary.activePokemonView(B).damage()==0
            &&secondary.benchPokemonView(A).stream().allMatch(v->v.damage()==20)&&secondary.benchPokemonView(B).stream().allMatch(v->v.damage()==20),"Independent bench_damage to both owners lost actual Garbodor-20 or applied base0 to secondary40");
        var before=game(1,true,"NORMAL");tool(before,A,active(before,A),"tool");before.attack(A,"before-devolve");
        check(before.activePokemonView(B).topCardId().equals(BASIC)&&before.activePokemonView(B).damage()==40
            &&before.benchPokemonView(A).stream().allMatch(v->v.damage()==40)&&before.benchPokemonView(B).stream().allMatch(v->v.damage()==40),"BEFORE_ATTACK devolution must remove actual passive before the damage checkpoint");
        var during=game(1,true,"REACTION");tool(during,A,active(during,A),"tool");tool(during,B,active(during,B),"reaction");during.attack(A,"staged");
        check(during.activePokemonView(B).topCardId().equals(BASIC)&&during.activePokemonView(B).damage()==20
            &&during.benchPokemonView(A).stream().allMatch(v->v.damage()==20)&&during.benchPokemonView(B).stream().allMatch(v->v.damage()==20),"ON_DAMAGE devolution removed passive source but must not recalculate already captured secondary attack damage");
    }
    static void selfDamageAndCounters(){
        // Advanced rule B-09: self attack damage follows the normal damage stages; damage counters do not.
        for(String attack:List.of("recoil","composed-recoil"))for(int count:List.of(0,1,2)){
            var m=game(count);tool(m,A,active(m,A),"tool");m.attack(A,attack);
            check(m.activePokemonView(A).damage()==Math.max(0,30-count*20)&&m.activePokemonView(B).damage()==0,"Actual Garbodor must modify native and composed self attack damage: "+attack+"/"+count);
        }
        for(String attack:List.of("recoil","composed-recoil"))for(String mode:List.of("WEAK","RESIST")){
            var m=game(1,false,mode);tool(m,A,active(m,A),"tool");m.attack(A,attack);
            check(m.activePokemonView(A).damage()==(mode.equals("WEAK")?20:0),"Self attack damage must apply outgoing Garbodor before own W/R: "+attack+"/"+mode);
        }
        var counters=game(2);tool(counters,A,active(counters,A),"tool");counters.attack(A,"self-counters");
        check(counters.activePokemonView(A).damage()==20&&counters.activePokemonView(B).damage()==0,"Attack self damage counters must remain separate from reduced attack self damage");
    }
    static void resistanceStopsCalculation(){
        // Actual Garbodor plus explicit synthetic resistance/received+30; advanced rule damage step4 ends at <=0.
        var m=game(1,false,"RESIST_FLOOR");tool(m,A,active(m,A),"tool");m.attack(A,"forty");
        check(m.lastAttack().finalDamage()==0&&m.activePokemonView(B).damage()==0,"40-20 Garbodor-30 resistance must stop at0 before received+30 can revive it");
    }
    static void actualCopiedSource(){
        for(boolean actualTool:List.of(false,true)){var m=game(1,true,"NORMAL");energy(m,"wrong");
            if(actualTool)tool(m,A,active(m,A),"tool");else tool(m,B,active(m,B),"tool");
            var o=m.copyAttackOptions(A,"copy").stream().filter(v->v.attackId().equals(ATTACK)).findFirst().orElseThrow();m.copyAttackWithEnergyDiscards(A,"copy",o.pokemonId(),o.entrySequence(),o.attackId(),List.of(),List.of());
            check(m.lastAttack().finalDamage()==(actualTool?80:100),"Copied actual Sludge Bomb tested donor Tool/owner instead of real attacker");}
    }
    static void protection(){
        var immune=game(1);tool(immune,A,active(immune,A),"tool");shield(immune,"ABILITY","EFFECTS_ONLY","SELF");strike(immune,100);
        for(String scope:List.of("DAMAGE_ONLY","EFFECTS_ONLY")){var m=game(1);tool(m,A,active(m,A),"tool");shield(m,"ATTACK",scope,"OPPONENT_ACTIVE");strike(m,scope.equals("DAMAGE_ONLY")?0:80);}
    }
}
