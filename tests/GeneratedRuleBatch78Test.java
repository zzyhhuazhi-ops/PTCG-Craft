import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Ninetales/Patrat and the official Empoleon ex destination-protection case. */
public final class GeneratedRuleBatch78Test {
    static final UUID A=new UUID(0,7811),B=new UUID(0,7812);
    static final String VULPIX="me4_en_008",NINETALES="me4_en_009",PATRAT="me4_en_070",PIPLUP="me2_en_027",PRINPLUP="me2_en_028",EMPOLEON="me2_en_070";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static UUID source(ServerMatch m){return m.benchPokemonView(A).stream().filter(p->p.topCardId().equals("base")).findFirst().orElseThrow().pokemonId();}
    static ServerMatch game(String mode){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(VULPIX,NINETALES,PATRAT,PIPLUP,PRINPLUP,EMPOLEON)){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}
        for(String id:List.of("base","fire","wrong"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,
            TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),!id.equals("base"),60,Map.of(),EffectSupport.SUPPORTED,null));
        profiles.put("base",new PokemonProfile(1000,1,null,null,0,null,0,1,List.of(),List.of(),EnergyType.COLORLESS));
        var catalog=new CombatCatalog(profiles,Map.of("fire",EnergyType.FIRE,"wrong",EnergyType.WATER));
        String[] pool={VULPIX,VULPIX,NINETALES,PATRAT,"base","base","fire","fire","wrong",PIPLUP,PRINPLUP,EMPOLEON};
        String opposing=mode.equals("PATRAT_ACTIVE")?PATRAT:mode.equals("EMPOLEON")?PIPLUP:"base";
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool[i%pool.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(VULPIX))||List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2||m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(PATRAT)))||m.privateView(B).hand().stream().noneMatch(c->c.definitionId().equals(opposing)))continue;
            for(UUID p:List.of(A,B)){
                m.chooseOpeningActive(p,hand(m,p,p.equals(A)?VULPIX:opposing).instanceId());m.placeOpeningBench(p,hand(m,p,"base").instanceId());
                if(p.equals(A)&&mode.equals("OWN_PATRAT")||p.equals(B)&&mode.equals("PATRAT_BENCH"))m.placeOpeningBench(p,hand(m,p,PATRAT).instanceId());m.startAfterOpening(p);
            }
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->Arrays.stream(pool).anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            if(m.privateView(A).hand().stream().filter(c->c.definitionId().equals("fire")).count()<2)continue;
            m.evolve(A,active(m,A),hand(m,A,NINETALES).instanceId());
            if(mode.equals("EMPOLEON")){
                m.endTurn(A);m.evolve(B,active(m,B),hand(m,B,PRINPLUP).instanceId());m.endTurn(B);m.endTurn(A);m.evolve(B,active(m,B),hand(m,B,EMPOLEON).instanceId());m.endTurn(B);
            }
            return m;
        }throw new AssertionError("No actual Ninetales fixture: "+mode);
    }
    static void attach(ServerMatch m,UUID p,String id){m.attachEnergyByEffect(A,p,EffectSourceZone.HAND,hand(m,p,id).instanceId(),p,active(m,p));}
    static void choose(ServerMatch m,UUID id){var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),id);}
    public static void main(String[] args){printed();transfer();officialProtection();empty();retainedAttacks();copied();System.out.println("GeneratedRuleBatch78Test passed: actual Ninetales evolution/Fire costs and0/1/>60 full transfer, own/opposing Patrat in both positions, actual evolved Empoleon ex immunity, empty Bench, retained Will-O-Wisp/Bite and actual copy controller");}
    static void printed(){for(String id:List.of(NINETALES,PATRAT)){check(DB.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Actual card unsupported: "+id);DATA.pokemon(id).orElseThrow().attacks().forEach(AttackProgramRules::validate);}
        var n=DATA.pokemon(NINETALES).orElseThrow();check(n.hitPoints()==120&&n.retreatCost()==1&&n.pokemonType()==EnergyType.FIRE&&n.weaknessType()==EnergyType.WATER&&n.weaknessMultiplier()==2&&n.resistanceType()==null&&n.prizeCards()==1&&n.evolvesFromDefinitionId().equals(VULPIX)&&n.evolvesFromName().equals("Vulpix")&&n.attacks().size()==2,"Ninetales profile");
        check(n.attacks().getFirst().id().equals("nine-tailed-transfer")&&n.attacks().getFirst().baseDamage()==0&&n.attacks().getFirst().energyCost().equals(Map.of(EnergyType.FIRE,1))&&n.attacks().getLast().baseDamage()==70&&n.attacks().getLast().energyCost().equals(Map.of(EnergyType.FIRE,2)),"Ninetales printed costs/attacks");
        var p=DATA.pokemon(PATRAT).orElseThrow();check(DB.find(PATRAT).orElseThrow().basicPokemon()&&p.hitPoints()==70&&p.retreatCost()==1&&p.prizeCards()==1&&p.pokemonType()==EnergyType.COLORLESS&&p.weaknessType()==EnergyType.FIGHTING&&p.resistanceType()==null&&p.attacks().getFirst().baseDamage()==10&&p.attacks().getFirst().energyCost().equals(Map.of(EnergyType.COLORLESS,1)),"Patrat profile/cost");
        check(p.abilities().size()==1&&p.abilities().getFirst().id().equals("watchful-eye"),"Patrat Ability");PassiveModifierProgram.validate(p.abilities().getFirst().effectProgram(),PassiveModifierProgram.Source.ABILITY);
    }
    static void transfer(){for(int n:List.of(0,1,70)){
        var m=game("BASE");UUID from=source(m);int left=n;while(left>0){int step=Math.min(60,left);m.placeDamageCountersByEffect(A,A,from,step);left-=step;}
        reject(()->m.attack(A,"nine-tailed-transfer"));attach(m,A,"wrong");reject(()->m.attack(A,"nine-tailed-transfer"));attach(m,A,"fire");m.attack(A,"nine-tailed-transfer");choose(m,from);
        check(m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(from)).findFirst().orElseThrow().damage()==0&&m.activePokemonView(B).damage()==10*n,"Actual full transfer count");
    }}
    static void officialProtection(){for(String mode:List.of("OWN_PATRAT","PATRAT_ACTIVE","PATRAT_BENCH","EMPOLEON")){
        var m=game(mode);UUID from=source(m);m.placeDamageCountersByEffect(A,A,from,5);attach(m,A,"fire");m.attack(A,"nine-tailed-transfer");choose(m,from);
        int remaining=m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(from)).findFirst().orElseThrow().damage();
        check(remaining==(mode.equals("EMPOLEON")?0:50)&&m.activePokemonView(B).damage()==0,"Official prohibition-versus-immunity ruling: "+mode);
    }}
    static void empty(){var m=game("BASE");m.movePokemonByEffect(A,A,source(m),LeavePlayDestination.HAND);attach(m,A,"fire");m.attack(A,"nine-tailed-transfer");check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Actual empty Bench stalled attack");}
    static void retainedAttacks(){var m=game("BASE");attach(m,A,"fire");attach(m,A,"wrong");reject(()->m.attack(A,"will-o-wisp"));attach(m,A,"fire");m.attack(A,"will-o-wisp");check(m.lastAttack().finalDamage()==70,"Will-O-Wisp changed");
        var p=game("PATRAT_ACTIVE");attach(p,A,"fire");attach(p,B,"wrong");p.attack(A,"nine-tailed-transfer");choose(p,source(p));p.attack(B,"bite");check(p.activePokemonView(A).damage()==10,"Bite Colorless payment/damage changed");
    }
    static void copied(){var body=DATA.pokemon(NINETALES).orElseThrow().attacks().getFirst();var m=CopyAttackTest.fixture(CopyAttackTest.copier(),body,EnergyType.FIRE,0);UUID a=CopyAttackTest.A,b=CopyAttackTest.B;
        UUID from=m.benchPokemonView(a).getFirst().pokemonId();m.placeDamageCountersByEffect(a,a,from,2);CopyAttackTest.pay(m);var option=m.copyAttackOptions(a,"copy").stream().filter(o->o.attackId().equals(body.id())).findFirst().orElseThrow();CopyAttackTest.copy(m,option,List.of(),List.of());
        var q=m.effectPokemonPrompt(a);check(q.targets().stream().anyMatch(t->t.pokemonId().equals(from)),"Copied transfer offered donor side");m.chooseEffectPokemon(a,q.resolutionId(),q.step(),from);
        check(m.benchPokemonView(a).stream().filter(v->v.pokemonId().equals(from)).findFirst().orElseThrow().damage()==0&&m.activePokemonView(b).damage()==20,"Copied transfer read donor owner or energy cost");
    }
}
