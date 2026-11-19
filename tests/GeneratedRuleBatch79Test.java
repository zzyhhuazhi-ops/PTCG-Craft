import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Delphox, including its real two-stage evolution and Ignition Energy ruling. */
public final class GeneratedRuleBatch79Test {
    static final UUID A=new UUID(0,7911),B=new UUID(0,7912);
    static final String BASIC="me4_en_011",STAGE1="me4_en_012",CARD="me4_en_013",PROMO="mebsp_en_074",IGNITION="me2_en_124";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static ServerMatch game(){return game(null,null);}
    static ServerMatch game(EnergyType weakness,EnergyType resistance){return game(CARD,weakness,resistance);}
    static ServerMatch game(String card,EnergyType weakness,EnergyType resistance){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(BASIC,STAGE1,CARD,PROMO)){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}defs.add(DB.find(IGNITION).orElseThrow());
        for(String id:List.of("base","fire","water","special","multi"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,
            TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),Set.of("fire","water").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null));
        profiles.put("base",new PokemonProfile(1000,1,null,weakness,weakness==null?0:2,resistance,resistance==null?0:30,1,List.of(),List.of(),EnergyType.COLORLESS));
        var catalog=new CombatCatalog(profiles,Map.of("fire",EnergyType.FIRE,"water",EnergyType.WATER),Map.of(),Map.of(
            "special",EnergyProfile.fixed(EnergyType.FIRE,2),"multi",new EnergyProfile(List.of(new EnergyProfile.Unit(Set.of(EnergyType.FIRE,EnergyType.WATER,EnergyType.PSYCHIC)))),
            IGNITION,DATA.energyProfile(IGNITION).orElseThrow()));
        String[] pool={BASIC,BASIC,STAGE1,card,"base","base","fire","fire","water","special","multi",IGNITION};
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool[i%pool.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().filter(c->c.definitionId().equals(BASIC)).count()<2||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("base")).count()<2)continue;
            for(UUID p:List.of(A,B)){String id=p.equals(A)?BASIC:"base";m.chooseOpeningActive(p,hand(m,p,id).instanceId());m.placeOpeningBench(p,hand(m,p,id).instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->Arrays.stream(pool).anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            if(List.of(STAGE1,card,"fire").stream().anyMatch(id->m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id)).count()<(id.equals("fire")?3:2)))continue;
            for(UUID host:List.of(active(m,A),bench(m,A)))m.evolve(A,host,hand(m,A,STAGE1).instanceId());
            reject(()->m.evolve(A,active(m,A),hand(m,A,card).instanceId()));m.endTurn(A);m.endTurn(B);
            for(UUID host:List.of(active(m,A),bench(m,A)))m.evolve(A,host,hand(m,A,card).instanceId());return m;
        }throw new AssertionError("No actual Delphox fixture");
    }
    static CardInstance attach(ServerMatch m,UUID owner,UUID host,String id){var c=hand(m,owner,id);m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,c.instanceId(),owner,host);return c;}
    static void pay(ServerMatch m){attach(m,A,active(m,A),"fire");attach(m,A,active(m,A),"fire");}
    static void trim(ServerMatch m,int size,List<CardInstance> keep){GeneratedRuleBatch35Test.trim(m,A,size,keep);}
    static void submit(ServerMatch m,CardInstance c){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(c.instanceId()));}
    public static void main(String[] args){printed();abilityBoundaries();costAuthority();quota();storm();ignition();copied();promo();System.out.println("GeneratedRuleBatch79Test passed: actual Delphox and same-era promo, two-stage named evolution/printed stats and RR cost, typed Basic Fire cost before draw-until7, hand7/8 and empty/short decks, private atomic choices and per-source/turn quota, both-field supplied Energy units with flexible supply, actual Ignition3 and end-turn discard, weakness/resistance and copied attack");}
    static void printed(){
        check(DB.find(CARD).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Delphox still unsupported");var p=DATA.pokemon(CARD).orElseThrow();
        check(p.hitPoints()==160&&p.retreatCost()==2&&p.prizeCards()==1&&p.pokemonType()==EnergyType.FIRE&&p.weaknessType()==EnergyType.WATER&&p.weaknessMultiplier()==2&&p.resistanceType()==null&&p.evolvesFromDefinitionId().equals(STAGE1)&&p.evolvesFromName().equals("Braixen"),"Printed Delphox profile");
        check(p.attacks().size()==1&&p.attacks().getFirst().id().equals("energized-storm")&&p.attacks().getFirst().energyCost().equals(Map.of(EnergyType.FIRE,2)),"Printed Energized Storm cost");AttackProgramRules.validate(p.attacks().getFirst());
        check(p.abilities().size()==1&&p.abilities().getFirst().id().equals("flaring-magic")&&p.abilities().getFirst().kind()==AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN,"Flaring Magic identity/quota");ActivatedAbilityRules.validate(p.abilities().getFirst());
    }
    static void abilityBoundaries(){for(int size:List.of(1,7,8))for(int available:List.of(0,1,9))for(boolean onBench:List.of(false,true)){
        var m=game();UUID host=onBench?bench(m,A):active(m,A);var paid=hand(m,A,"fire");trim(m,size,List.of(paid));m.discardTopDeckCardsByEffect(A,A,Math.max(0,m.privateView(A).deck().size()-available));int deck=m.privateView(A).deck().size();boolean legal=size<=7&&deck>0;
        check(m.canUseAbility(A,host,"flaring-magic")==legal,"Flaring Magic legality size="+size+",deck="+deck);var before=m.privateView(A);
        if(!legal){reject(()->m.useAbility(A,host,"flaring-magic"));check(before.equals(m.privateView(A)),"Unavailable Ability paid cost");continue;}
        m.useAbility(A,host,"flaring-magic");check(m.privateView(A).hand().size()==size,"Drew before mandatory discard choice");submit(m,paid);
        check(m.privateView(A).hand().size()==Math.min(7,size-1+deck)&&m.privateView(A).discard().contains(paid)&&m.currentPlayer().equals(A),"Post-cost draw count/identity/turn");reject(()->m.useAbility(A,host,"flaring-magic"));
    }}
    static void costAuthority(){
        var m=game();UUID host=active(m,A);var fire=hand(m,A,"fire");var wrong=hand(m,A,"water");var special=hand(m,A,"special");trim(m,6,List.of(fire,wrong,special));
        m.useAbility(A,host,"flaring-magic");var p=m.effectCardPrompt(A);var before=m.clientView(A);
        check(p.minimum()==1&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("fire"))&&m.clientView(B).effectSelection()==null,"Basic Fire cost filter/privacy");
        reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(fire.instanceId())));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(wrong.instanceId())));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(special.instanceId())));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(fire.instanceId(),fire.instanceId())));
        check(before.equals(m.clientView(A)),"Forged cost changed state");submit(m,fire);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(fire.instanceId())));
        for(String id:List.of("water","special","multi")){var no=game();var card=hand(no,A,id);trim(no,1,List.of(card));var view=no.privateView(A);check(!no.canUseAbility(A,active(no,A),"flaring-magic"),"Non-Basic Fire paid cost");reject(()->no.useAbility(A,active(no,A),"flaring-magic"));check(view.equals(no.privateView(A)),"Unaffordable cost mutated state");}
    }
    static void quota(){var m=game();var fs=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("fire")).limit(3).toList();trim(m,3,fs);
        m.useAbility(A,active(m,A),"flaring-magic");submit(m,fs.getFirst());check(m.canUseAbility(A,bench(m,A),"flaring-magic"),"Second source shared quota");m.useAbility(A,bench(m,A),"flaring-magic");submit(m,fs.get(1));
        reject(()->m.useAbility(A,active(m,A),"flaring-magic"));reject(()->m.useAbility(A,bench(m,A),"flaring-magic"));m.endTurn(A);m.endTurn(B);
        var next=hand(m,A,"fire");trim(m,7,List.of(next));check(m.canUseAbility(A,active(m,A),"flaring-magic"),"Turn quota failed to reset");m.disableAbilityEffectsByEffect(A,A,active(m,A));check(!m.canUseAbility(A,active(m,A),"flaring-magic"),"Suppressed Ability usable");
    }
    static void storm(){for(int mode:List.of(0,1,2)){
        var m=game(mode==1?EnergyType.FIRE:null,mode==2?EnergyType.FIRE:null);reject(()->m.attack(A,"energized-storm"));attach(m,A,active(m,A),"water");attach(m,A,active(m,A),"fire");reject(()->m.attack(A,"energized-storm"));attach(m,A,active(m,A),"fire");
        attach(m,A,bench(m,A),"special");attach(m,B,active(m,B),"water");attach(m,B,bench(m,B),"multi");
        m.attack(A,"energized-storm");int expected=mode==1?420:mode==2?180:210;
        check(m.lastAttack().finalDamage()==expected&&m.activePokemonView(B).damage()==expected,"Both-field current units / weakness / resistance");check(m.activePokemonView(A).attachedEnergyIds().size()==3&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1,"Count query consumed Energy");
    }}
    static void ignition(){var m=game();pay(m);var own=attach(m,A,active(m,A),IGNITION);var opposing=attach(m,B,active(m,B),IGNITION);m.attack(A,"energized-storm");
        check(m.lastAttack().finalDamage()==180,"Actual Ignition counted as a card or supplied3 on Basic");check(m.privateView(A).discard().contains(own)&&m.activePokemonView(A).attachedEnergyIds().size()==2&&m.activePokemonView(B).attachedEnergyIds().contains(opposing.instanceId()),"Actual Ignition owner end-turn discard changed");
    }
    static void copied(){var body=DATA.pokemon(CARD).orElseThrow().attacks().getFirst();var m=CopyAttackTest.fixture(CopyAttackTest.copier(),body,EnergyType.PSYCHIC,0);var a=CopyAttackTest.A;var b=CopyAttackTest.B;CopyAttackTest.pay(m);
        for(UUID p:List.of(a,b))m.attachEnergyByEffect(a,p,EffectSourceZone.HAND,hand(m,p,"energy").instanceId(),p,bench(m,p));
        var option=m.copyAttackOptions(a,"copy").stream().filter(o->o.attackId().equals(body.id())).findFirst().orElseThrow();CopyAttackTest.copy(m,option,List.of(),List.of());check(m.lastAttack().finalDamage()==180,"Copied storm changed field sum or actual attacker weakness");
    }
    static void promo(){
        check(DB.find(PROMO).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Promo still unsupported");var p=DATA.pokemon(PROMO).orElseThrow();var original=DATA.pokemon(CARD).orElseThrow();
        check(p.hitPoints()==160&&p.retreatCost()==2&&p.prizeCards()==1&&p.pokemonType()==EnergyType.FIRE&&p.weaknessType()==EnergyType.WATER&&p.evolvesFromName().equals("Braixen")&&p.attacks().equals(original.attacks())&&p.abilities().equals(original.abilities()),"Promo printed rules differ");
        var m=game(PROMO,null,null);check(m.activePokemonView(A).evolutionCardIds().equals(List.of(BASIC,STAGE1,PROMO)),"Promo rejected cross-print named Braixen evolution");
        var fires=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("fire")).limit(3).toList();trim(m,7,fires);m.useAbility(A,bench(m,A),"flaring-magic");submit(m,fires.getFirst());check(m.privateView(A).hand().size()==7,"Promo draw-until");pay(m);m.attack(A,"energized-storm");check(m.lastAttack().finalDamage()==60,"Promo printed RR attack");
    }
}
