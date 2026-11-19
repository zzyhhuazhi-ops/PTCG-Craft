import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Chesnaught: normal two-stage evolution, printed costs and pre-KO source-Energy retaliation. */
public final class GeneratedRuleBatch77Test {
    static final UUID A=new UUID(0,7711),B=new UUID(0,7712);
    static final String BASIC="me4_en_005",STAGE1="me4_en_006",CARD="me4_en_007";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static ServerMatch game(boolean sourceOnA,int damage){
        UUID owner=sourceOnA?A:B;var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(BASIC,STAGE1,CARD)){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}
        for(String id:List.of("base","grass","wrong","special"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,
            TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),Set.of("grass","wrong").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null));
        profiles.put("base",new PokemonProfile(1000,1,null,null,0,null,0,1,List.of(DamageTriggeredAbilityTest.attack(damage,"")),List.of(),EnergyType.COLORLESS));
        var catalog=new CombatCatalog(profiles,Map.of("grass",EnergyType.GRASS,"wrong",EnergyType.WATER),Map.of(),
            Map.of("special",EnergyProfile.fixed(EnergyType.GRASS,2)));
        String[] pool={BASIC,BASIC,STAGE1,CARD,"base","base","grass","grass","grass","wrong","special","grass"};
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){
                var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,p.equals(owner)?pool[i%pool.length]:i%2==0?"base":"grass"));decks.add(cs);
            }
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(owner).hand().stream().noneMatch(c->c.definitionId().equals(BASIC))||m.privateView(owner).hand().stream().noneMatch(c->c.definitionId().equals("base"))
                ||m.privateView(sourceOnA?B:A).hand().stream().filter(c->c.definitionId().equals("base")).count()<2)continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,p.equals(owner)?BASIC:"base").instanceId());m.placeOpeningBench(p,hand(m,p,"base").instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,owner,32);m.drawCardsByEffect(A,sourceOnA?B:A,10);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(pool).anyMatch(id->m.privateView(owner).hand().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            if(m.privateView(owner).hand().stream().filter(c->c.definitionId().equals("grass")).count()<3)continue;
            if(!sourceOnA)m.endTurn(A);
            UUID host=active(m,owner);m.evolve(owner,host,hand(m,owner,STAGE1).instanceId());reject(()->m.evolve(owner,host,hand(m,owner,CARD).instanceId()));
            m.endTurn(owner);m.endTurn(sourceOnA?B:A);m.evolve(owner,host,hand(m,owner,CARD).instanceId());if(!sourceOnA)m.endTurn(B);
            return m;
        }throw new AssertionError("No actual Chesnaught fixture");
    }
    static void attach(ServerMatch m,UUID p,String id,int count){for(int i=0;i<count;i++)m.attachEnergyByEffect(A,p,EffectSourceZone.HAND,hand(m,p,id).instanceId(),p,active(m,p));}
    public static void main(String[] args){printed();reaction();simultaneousKo();impound();System.out.println("GeneratedRuleBatch77Test passed: actual Chesnaught metadata and normal two-stage evolution, mandatory active Needly Armor with current Grass units and special supply, even lethal/simultaneous KO, and retained GGC160 Impound retreat lock/expiry");}
    static void printed(){
        check(DB.find(CARD).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Chesnaught still unsupported");var p=DATA.pokemon(CARD).orElseThrow();
        check(p.hitPoints()==180&&p.retreatCost()==4&&p.prizeCards()==1&&p.pokemonType()==EnergyType.GRASS&&p.weaknessType()==EnergyType.FIRE&&p.weaknessMultiplier()==2&&p.resistanceType()==null
            &&p.evolvesFromDefinitionId().equals(STAGE1)&&p.evolvesFromName().equals("Quilladin")&&p.pokemonName().equals("Chesnaught"),"Printed Chesnaught profile");
        var a=p.attacks().getFirst();check(p.attacks().size()==1&&a.id().equals("impound")&&a.baseDamage()==160&&a.energyCost().equals(Map.of(EnergyType.GRASS,2,EnergyType.COLORLESS,1)),"Printed Impound cost/damage");AttackProgramRules.validate(a);
        var ability=p.abilities().getFirst();check(p.abilities().size()==1&&ability.id().equals("needly-armor"),"Printed Needly Armor identity");TriggeredAbilityRules.validate(ability);
        var policy=(DamageTriggerPolicy)ability.eventPolicy();check(policy.watch()==EventPokemonScope.SELF&&policy.activeOnly()&&!policy.lethalOnly()&&!policy.optional()&&policy.opponentOnly()&&policy.usage()==EnergyTriggerPolicy.Usage.NONE,"Printed reaction event");
    }
    static void reaction(){for(int units:List.of(0,1,3)){
        var m=game(false,10);attach(m,B,"grass",units);attach(m,B,"wrong",1);m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);
        check(m.activePokemonView(A).damage()==units*30&&m.activePokemonView(B).damage()==10&&m.activePokemonView(B).evolutionCardIds().equals(List.of(BASIC,STAGE1,CARD)),"Actual reaction/evolution units");
    }
        var special=game(false,10);attach(special,B,"special",1);special.attack(A,"hit");check(special.activePokemonView(A).damage()==60,"Actual reaction counted special card instead of provided units");
        var lethal=game(false,180);attach(lethal,B,"grass",2);lethal.attack(A,"hit");check(lethal.activePokemonView(A).damage()==60&&lethal.phase()==MatchPhase.PRIZE_SELECTION,"Actual lethal source lost reaction before cleanup");
    }
    static void simultaneousKo(){var m=game(false,180);attach(m,B,"grass",2);m.placeDamageCountersByEffect(A,A,active(m,A),60);m.placeDamageCountersByEffect(A,A,active(m,A),34);m.attack(A,"hit");
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(A).discard().stream().anyMatch(c->c.definitionId().equals("base"))&&m.privateView(B).discard().stream().anyMatch(c->c.definitionId().equals(CARD)),"Actual simultaneous KO missing a side");
        int cap=10;while(m.phase()==MatchPhase.PRIZE_SELECTION&&cap-->0)m.takePrizeAt(m.pendingChoicePlayer(),0);
        while(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&cap-->0){UUID p=m.pendingChoicePlayer();m.chooseReplacement(p,m.benchPokemonView(p).getFirst().pokemonId());}
        check(cap>0&&m.currentPlayer().equals(B)&&m.privateView(A).prizes().size()==5&&m.privateView(B).prizes().size()==5,"Simultaneous prize/turn resolution");
    }
    static void impound(){var m=game(true,10);reject(()->m.attack(A,"impound"));attach(m,A,"wrong",1);attach(m,A,"grass",1);reject(()->m.attack(A,"impound"));attach(m,A,"grass",1);attach(m,B,"grass",1);
        UUID old=active(m,B),bench=m.benchPokemonView(B).getFirst().pokemonId();var payment=m.activePokemonView(B).attachedEnergyIds();m.attack(A,"impound");
        check(m.lastAttack().finalDamage()==160&&m.currentPlayer().equals(B),"Actual Impound damage/turn");reject(()->m.retreat(B,bench,payment));
        m.endTurn(B);m.endTurn(A);m.retreat(B,bench,payment);check(!active(m,B).equals(old),"Impound lock did not expire after next opponent turn");
    }
}
