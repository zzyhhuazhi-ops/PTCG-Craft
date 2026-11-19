import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual printed Pokemon, evolution chains and named Energy on controlled 60-card server fixtures. */
public final class GeneratedRuleBatch63Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String VIKA="me5_en_026",ZARUDE="me5_en_056",VOLTAIC="me5_en_084",SHADOW="me5_en_083",THIEVUL="me5_en_054";
    static final CombatCatalog BUNDLED=BundledCombatData.load();static final CardDatabase DATABASE=BundledRules.loadCardDefinitions();
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static AttackDefinition attack(String id,String name){return BUNDLED.pokemon(id).orElseThrow().attack(name).orElseThrow();}
    static List<String> chain(String id){var ids=new ArrayList<String>();for(String current=id;current!=null;){ids.addFirst(current);current=current.equals("plain")?null:BUNDLED.pokemon(current).orElseThrow().evolvesFromDefinitionId();}return ids;}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance attach(ServerMatch m,UUID p,UUID host,String id){var card=hand(m,p,id);m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,card.instanceId(),p,host);return card;}
    static void pay(ServerMatch m,String id,int n){for(int i=0;i<n;i++)attach(m,A,active(m,A),id);}
    static void override(ServerMatch m){m.playTrainer(A,hand(m,A,"override").instanceId(),null);}
    static ServerMatch game(String own){return game(own,"plain",EnergyType.DARKNESS);}
    static ServerMatch game(String own,String opponent,EnergyType benchType){
        var defs=new HashMap<String,CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(own,opponent))for(String c:chain(id))if(!c.equals("plain")){defs.put(c,DATABASE.find(c).orElseThrow());profiles.put(c,BUNDLED.pokemon(c).orElseThrow());}
        for(String id:List.of(VOLTAIC,SHADOW))defs.put(id,DATABASE.find(id).orElseThrow());
        for(String id:List.of("plain","basic_lightning","basic_darkness","wild","override")){boolean pokemon=id.equals("plain"),trainer=id.equals("override");defs.put(id,new CardDefinition(id,id,pokemon?CardCategory.POKEMON:trainer?CardCategory.TRAINER:CardCategory.ENERGY,trainer?TrainerCategory.STADIUM:TrainerCategory.NONE,"J",Set.of("international"),pokemon,id.startsWith("basic_"),60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),pokemon?Set.of(benchType):Set.of()));}
        profiles.put("plain",new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(new AttackDefinition("hit","hit",Map.of(),20,EnergyType.COLORLESS,EffectSupport.SUPPORTED,null)),List.of(),benchType));
        var catalog=new CombatCatalog(profiles,Map.of("basic_lightning",EnergyType.LIGHTNING,"basic_darkness",EnergyType.DARKNESS),Map.of("override",DynamicEnergyProvisionTest.stadium()),Map.of(VOLTAIC,BUNDLED.energyProfile(VOLTAIC).orElseThrow(),SHADOW,BUNDLED.energyProfile(SHADOW).orElseThrow(),"wild",new EnergyProfile(List.of(new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class))))));
        var chains=Map.of(A,chain(own),B,chain(opponent));var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var ids=new ArrayList<String>();for(String c:chains.get(p))for(int i=0;i<4;i++)ids.add(c);while(ids.size()<20)ids.add("plain");for(String id:List.of(VOLTAIC,SHADOW,"wild","override"))for(int i=0;i<4;i++)ids.add(id);for(String id:List.of("basic_lightning","basic_darkness"))for(int i=0;i<12;i++)ids.add(id);check(ids.size()==60,"Bad fixture size");var cards=new ArrayList<CardInstance>();for(int i=0;i<ids.size();i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids.get(i)));decks.add(cards);}
        for(int seed=0;seed<2000;seed++){
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs.values()),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(chains.get(p).getFirst()))||m.privateView(p).hand().stream().filter(c->c.definitionId().equals("plain")).count()<(chains.get(p).getFirst().equals("plain")?2:1)))continue;
            m.chooseOpeningActive(A,hand(m,A,chains.get(A).getFirst()).instanceId());m.placeOpeningBench(A,hand(m,A,"plain").instanceId());m.chooseOpeningActive(B,hand(m,B,chains.get(B).getFirst()).instanceId());m.placeOpeningBench(B,hand(m,B,"plain").instanceId());m.startAfterOpening(A);m.startAfterOpening(B);OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);
            if(List.of(A,B).stream().anyMatch(p->chains.get(p).stream().skip(1).anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))||List.of(VOLTAIC,SHADOW).stream().anyMatch(id->m.privateView(p).hand().stream().filter(c->c.definitionId().equals(id)).count()<2)||List.of("wild","override","basic_lightning","basic_darkness").stream().anyMatch(id->m.privateView(p).hand().stream().filter(c->c.definitionId().equals(id)).count()<(id.startsWith("basic_")?3:1))))continue;
            // Reviewed all-timing Evolution permission creates complete actual chains without rewriting any printed card.
            for(UUID p:List.of(A,B)){for(String id:chains.get(p).stream().skip(1).toList())m.evolveByEffect(p,active(m,p),hand(m,p,id).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(p);}return m;
        }throw new AssertionError("No named-energy real-card fixture");
    }
    public static void main(String[] args){printed();vikavolt();zarude();firstAttacks();copiedActual();reprints();System.out.println("GeneratedRuleBatch63Test passed: all four actual Vikavolt/Zarude prints, complete profiles and evolution chains, printed costs, exact attached Energy identities and scopes, current actual Energy bonuses/protection, suppressed-effect identity, zero and multiple attachments, unchanged first attacks, and actual Thievul copy with actor identity/type");}
    static void printed(){for(String id:List.of(VIKA,ZARUDE))check(DATABASE.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Actual card incomplete");var v=BUNDLED.pokemon(VIKA).orElseThrow();var z=BUNDLED.pokemon(ZARUDE).orElseThrow();check(v.hitPoints()==160&&v.retreatCost()==2&&v.evolvesFromDefinitionId().equals("me5_en_025")&&v.pokemonType()==EnergyType.LIGHTNING,"Vikavolt metadata changed");check(z.hitPoints()==130&&z.retreatCost()==2&&z.evolvesFromDefinitionId()==null&&z.pokemonType()==EnergyType.DARKNESS,"Zarude metadata changed");
        var rail=attack(VIKA,"giga-railgun");var e=rail.effectProgram().triggers().getFirst().effects().getFirst();check(rail.baseDamage()==0&&rail.energyCost().equals(Map.of(EnergyType.LIGHTNING,2))&&e.effects().getFirst().arguments().get("amount").equals("260")&&e.conditions().getFirst().arguments().get("definition_ids").equals(VOLTAIC),"Printed260 not represented by conditional damage");
        var whip=attack(ZARUDE,"shadowy-whip");check(whip.baseDamage()==100&&whip.energyCost().equals(Map.of(EnergyType.DARKNESS,3))&&whip.effectProgram().triggers().getFirst().effects().getFirst().arguments().get("amount").equals("70"),"Printed100+70 or DDD changed");
        var dive=attack(VIKA,"quick-dive");var overhead=attack(ZARUDE,"overhead-throw");check(dive.baseDamage()==50&&dive.energyCost().equals(Map.of(EnergyType.LIGHTNING,1))&&dive.damageTarget()==AttackDefinition.DamageTarget.OPPONENT_ANY,"Quick Dive changed");check(overhead.baseDamage()==30&&overhead.energyCost().equals(Map.of(EnergyType.DARKNESS,1))&&!overhead.effectProgram().isEmpty(),"Overhead Throw changed");
    }
    static void vikavolt(){for(String mode:List.of("NONE","OTHER_SPECIAL","OWN_BENCH","OPPONENT","ONE","TWO","SUPPRESSED","RESTORED")){
        var m=game(VIKA);int expected=0;
        if(mode.equals("TWO")){pay(m,VOLTAIC,2);expected=300;}
        else if(Set.of("ONE","SUPPRESSED","RESTORED").contains(mode)){pay(m,VOLTAIC,1);pay(m,"basic_lightning",mode.equals("ONE")?1:2);expected=280;if(!mode.equals("ONE")){override(m);expected=260;if(mode.equals("RESTORED")){m.discardStadiumByEffect(A);expected=280;}}}
        else {pay(m,"basic_lightning",2);if(mode.equals("OTHER_SPECIAL"))pay(m,"wild",1);if(mode.equals("OWN_BENCH"))attach(m,A,bench(m,A),VOLTAIC);if(mode.equals("OPPONENT"))attach(m,B,active(m,B),VOLTAIC);}
        m.attack(A,"giga-railgun");check(m.lastAttack().finalDamage()==expected&&m.currentPlayer().equals(B),"Vikavolt "+mode+" got "+m.lastAttack().finalDamage());
    }
        var m=game(VIKA);pay(m,VOLTAIC,1);GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->m.attack(A,"giga-railgun"));
    }
    static void zarude(){for(String mode:List.of("NONE","ACTIVE","BENCH","MANY","FOREIGN","OTHER_SPECIAL","EMPTY","SUPPRESSED","FIRE_HOST")){
        var m=game(ZARUDE,"plain",mode.equals("FIRE_HOST")?EnergyType.FIRE:EnergyType.DARKNESS);pay(m,"basic_darkness",3);int expected=100;
        if(Set.of("BENCH","MANY","SUPPRESSED","FIRE_HOST").contains(mode)){attach(m,A,bench(m,A),SHADOW);expected=170;if(mode.equals("MANY"))attach(m,A,bench(m,A),SHADOW);if(mode.equals("SUPPRESSED"))override(m);}
        if(mode.equals("ACTIVE"))pay(m,SHADOW,1);if(mode.equals("FOREIGN"))attach(m,B,bench(m,B),SHADOW);if(mode.equals("OTHER_SPECIAL"))attach(m,A,bench(m,A),"wild");if(mode.equals("EMPTY"))m.movePokemonByEffect(A,A,bench(m,A),LeavePlayDestination.HAND);
        m.attack(A,"shadowy-whip");check(m.lastAttack().finalDamage()==expected&&m.currentPlayer().equals(B),"Zarude "+mode+" got "+m.lastAttack().finalDamage());
    }}
    static void firstAttacks(){for(boolean bench:List.of(false,true)){var m=game(VIKA);pay(m,VOLTAIC,1);UUID target=bench?bench(m,B):active(m,B);m.attackWithEnergyDiscards(A,"quick-dive",List.of(target),List.of());check((bench?m.benchPokemonView(B).getFirst().damage():m.activePokemonView(B).damage())==(bench?50:70),"QuickDive or actual Voltaic Active-only bonus changed");}
        var m=game(ZARUDE);pay(m,"basic_darkness",1);attach(m,A,bench(m,A),SHADOW);m.attack(A,"overhead-throw");var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench(m,A));check(m.activePokemonView(B).damage()==30&&m.benchPokemonView(A).getFirst().damage()==30,"Own Bench damage was blocked by opponent-only Shadowy effect");
    }
    static void copiedActual(){for(boolean own:List.of(false,true)){var m=game(THIEVUL,VIKA,EnergyType.DARKNESS);pay(m,"basic_darkness",2);attach(m,own?A:B,active(m,own?A:B),VOLTAIC);ConditionalCopyAttackTest.empty(m,A);var c=m.copyAttackOptions(A,"skill-thief").stream().filter(x->x.attackId().equals("giga-railgun")).findFirst().orElseThrow();m.copyAttackWithEnergyDiscards(A,"skill-thief",c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());check(m.lastAttack().finalDamage()==(own?260:0),"Copied GigaRailgun used provider identity or granted Lightning-host bonus to Darkness Thievul");}}
    static void reprints(){for(String id:List.of("mealt_en_009","mebsp_en_088")){
        check(DATABASE.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&BUNDLED.pokemon(id).orElseThrow().equals(BUNDLED.pokemon(ZARUDE).orElseThrow()),"Zarude reprint programs/profile differ");
        for(boolean named:List.of(false,true)){var m=game(id);pay(m,"basic_darkness",3);if(named)attach(m,A,bench(m,A),SHADOW);m.attack(A,"shadowy-whip");check(m.lastAttack().finalDamage()==(named?170:100),"Reprint named-energy attack failed");}
        var m=game(id);pay(m,"basic_darkness",1);attach(m,A,bench(m,A),SHADOW);m.attack(A,"overhead-throw");var prompt=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),bench(m,A));check(m.activePokemonView(B).damage()==30&&m.benchPokemonView(A).getFirst().damage()==30,"Reprint first attack failed");
    }}

}
