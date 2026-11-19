import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual ME4 cards/costs/evolutions plus adversarial field and public-discard boundaries. */
public final class GeneratedRuleBatch76Test {
    static final UUID A=new UUID(0,7601),B=new UUID(0,7602);
    static final String WEEDLE="me4_en_001",KAKUNA="me4_en_002",BEE="me4_en_003",CHOMP="me4_en_004",HO="me4_en_010",FOSSIL="me5_en_073";
    static final List<String> IDS=List.of(BEE,"me4_en_098",CHOMP,HO);
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void energy(ServerMatch m,String id){m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,id).instanceId(),A,active(m,A));}
    static void cards(ServerMatch m,List<CardInstance> cs){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),cs.stream().map(CardInstance::instanceId).toList());}
    static ServerMatch game(String source,int retreat){return game(source,retreat,List.of());}
    static ServerMatch game(String source,int retreat,List<AbilityDefinition> events){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(WEEDLE,KAKUNA,BEE,"me4_en_098",CHOMP,HO,FOSSIL)){
            defs.add(DB.find(id).orElseThrow());DATA.pokemon(id).ifPresent(p->profiles.put(id,p));
        }
        for(String id:List.of("base","energy","wrong-energy"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,
            TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),!id.equals("base"),60,Map.of(),EffectSupport.SUPPORTED,null));
        profiles.put("base",new PokemonProfile(600,retreat,null,null,0,null,0,1,List.of(),events,EnergyType.COLORLESS,"Base",null));
        var catalog=new CombatCatalog(profiles,Map.of("energy",source.equals(HO)?EnergyType.FIRE:EnergyType.GRASS,"wrong-energy",EnergyType.WATER),Map.of(FOSSIL,DATA.trainer(FOSSIL).orElseThrow()));
        String initial=source.equals(BEE)||source.equals("me4_en_098")?WEEDLE:source;
        String[] pool={initial,initial,"base","base","base","energy","energy","energy","wrong-energy",KAKUNA,source,FOSSIL};
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){
                var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.equals(A)?76011:76012,i+1),owner,owner.equals(A)?pool[i%pool.length]:i%2==0?"base":FOSSIL));decks.add(cs);
            }
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(initial))||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("base")).count()<2)continue;
            m.chooseOpeningActive(A,hand(m,A,initial).instanceId());m.startAfterOpening(A);
            m.chooseOpeningActive(B,hand(m,B,"base").instanceId());m.placeOpeningBench(B,hand(m,B,"base").instanceId());m.startAfterOpening(B);
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,36);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(pool).anyMatch(id->m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            if(m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()<3||m.privateView(A).hand().stream().filter(c->c.definitionId().equals("base")).count()<8)continue;
            return m;
        }throw new AssertionError("No actual ME4 fixture");
    }
    public static void main(String[] args){printed();bees();namedBoundaries();chomp();copiedChomp();revival();capacity();entryEvents();brightWing();System.out.println("GeneratedRuleBatch76Test passed: four actual ME4 cards, real evolution and energy costs, exact current Beedrill names, live zero retreat cost including copied attacks/fossils, optional Basic revival identity/limits/events and retained Bright Wing");}
    static void printed(){
        for(String id:IDS){check(DB.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Unsupported actual card "+id);DATA.pokemon(id).orElseThrow().attacks().forEach(AttackProgramRules::validate);}
        for(String id:List.of(BEE,"me4_en_098")){
            var p=DATA.pokemon(id).orElseThrow();var a=p.attacks().getFirst();
            check(p.hitPoints()==310&&p.retreatCost()==1&&p.prizeCards()==2&&p.pokemonName().equals("Beedrill ex")&&p.evolvesFromName().equals("Kakuna")&&p.weaknessType()==EnergyType.FIRE&&p.weaknessMultiplier()==2&&p.resistanceType()==null,"Beedrill metadata");
            check(a.baseDamage()==0&&a.energyCost().equals(Map.of(EnergyType.GRASS,1)),"Beedrill printed cost/multiplier");
        }
        var c=DATA.pokemon(CHOMP).orElseThrow();check(c.hitPoints()==110&&c.retreatCost()==2&&c.pokemonType()==EnergyType.GRASS&&c.weaknessType()==EnergyType.FIRE&&c.prizeCards()==1&&c.attacks().getFirst().baseDamage()==80&&c.attacks().getFirst().energyCost().equals(Map.of(EnergyType.COLORLESS,3)),"Carnivine metadata/cost");
        var h=DATA.pokemon(HO).orElseThrow();check(h.hitPoints()==130&&h.retreatCost()==2&&h.pokemonType()==EnergyType.FIRE&&h.weaknessType()==EnergyType.WATER&&h.prizeCards()==1&&h.attacks().size()==2&&h.attacks().getFirst().energyCost().equals(Map.of(EnergyType.FIRE,1))&&h.attacks().getLast().energyCost().equals(Map.of(EnergyType.FIRE,3)),"Ho-Oh metadata/cost");
    }
    static void bees(){for(String id:List.of(BEE,"me4_en_098")){
        var m=game(id,1);UUID source=active(m,A);m.evolve(A,source,hand(m,A,KAKUNA).instanceId());reject(()->m.evolve(A,source,hand(m,A,id).instanceId()));
        m.endTurn(A);m.endTurn(B);m.evolve(A,source,hand(m,A,id).instanceId());reject(()->m.attack(A,"rumbling-bees"));energy(m,"wrong-energy");reject(()->m.attack(A,"rumbling-bees"));energy(m,"energy");
        m.attack(A,"rumbling-bees");check(m.lastAttack().finalDamage()==110&&m.activePokemonView(A).topCardId().equals(id),"Actual evolved Beedrill must count itself once");
    }}
    static void namedBoundaries(){for(String id:List.of(BEE,"me4_en_098"))for(boolean evolved:List.of(false,true)){
        var attack=GeneratedRuleBatch35Test.attack(id,0);var m=GeneratedRuleBatch37Test.fixture("Beedrill ex","Beedrill","Beedrill ex",EnergyType.GRASS,null,attack);
        UUID a=GeneratedRuleBatch37Test.A,b=GeneratedRuleBatch37Test.B;
        GeneratedRuleBatch37Test.add(m,a,"p1");UUID two=GeneratedRuleBatch37Test.add(m,a,"p2");GeneratedRuleBatch37Test.add(m,a,"wrong");GeneratedRuleBatch37Test.add(m,a,"v");
        if(evolved)m.evolveByEffect(a,two,hand(m,a,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        m.endTurn(a);GeneratedRuleBatch37Test.add(m,b,"p1");m.endTurn(b);m.attack(a,attack.id());
        check(m.lastAttack().finalDamage()==(evolved?220:330),"Name count included opponent, covered card, regional or V name");
    }}
    static void chomp(){for(int retreat:List.of(0,1,2)){
        var m=game(CHOMP,retreat);reject(()->m.attack(A,"chomp-whole"));energy(m,"energy");energy(m,"wrong-energy");reject(()->m.attack(A,"chomp-whole"));energy(m,"energy");
        m.attack(A,"chomp-whole");check(m.lastAttack().finalDamage()==(retreat==0?160:80),"Actual Carnivine printed cost/bonus");
    }
        var m=game(CHOMP,2);energy(m,"energy");energy(m,"energy");energy(m,"energy");m.endTurn(A);
        var fossil=hand(m,B,FOSSIL);m.playTrainer(B,fossil.instanceId(),null);m.switchActiveByEffect(B,B,fossil.instanceId());m.endTurn(B);
        check(m.activeRetreatCost(B)==0,"Fossil printed retreat ban must not create a cost");m.attack(A,"chomp-whole");check(m.lastAttack().finalDamage()==160,"Fossil inability to retreat incorrectly removed bonus");
    }
    static void copiedChomp(){for(boolean zero:List.of(false,true)){
        var body=DATA.pokemon(CHOMP).orElseThrow().attacks().getFirst();var m=CopyAttackTest.fixture(CopyAttackTest.copier(),body,EnergyType.GRASS,0);
        UUID a=CopyAttackTest.A,b=CopyAttackTest.B;if(!zero)m.modifyRetreatCostByEffect(a,b,active(m,b),1);CopyAttackTest.pay(m);
        var choice=m.copyAttackOptions(a,"copy").stream().filter(o->o.attackId().equals(body.id())).findFirst().orElseThrow();CopyAttackTest.copy(m,choice,List.of(),List.of());
        check(m.lastAttack().finalDamage()==(zero?160:80),"Copied condition read donor's opponent instead of actual attacker");
    }}
    static void revival(){for(int count:List.of(0,1,3)){
        var m=game(HO,1);var wanted=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("base")).limit(3).toList();
        var wrong=List.of(hand(m,A,KAKUNA),hand(m,A,FOSSIL),hand(m,A,"wrong-energy"));var discard=new ArrayList<>(wanted);discard.addAll(wrong);
        m.discardHandCardsByEffect(A,A,discard.stream().map(CardInstance::instanceId).toList());var foreign=hand(m,B,"base");m.discardHandCardsByEffect(A,B,List.of(foreign.instanceId()));
        reject(()->m.attack(A,"flames-of-revival"));energy(m,"wrong-energy");reject(()->m.attack(A,"flames-of-revival"));energy(m,"energy");
        int rng=m.randomTranscript().size();m.attack(A,"flames-of-revival");var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==3&&new HashSet<>(p.candidates()).equals(new HashSet<>(wanted)),"Revival must select own Basic Pokemon, never a fossil Item/evolution/Energy");
        var before=m.privateView(A);for(var invalid:List.of(wrong.getFirst(),wrong.get(1),foreign,hand(m,A,"base")))reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(wanted.getFirst().instanceId(),invalid.instanceId())));
        reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(wanted.getFirst().instanceId(),wanted.getFirst().instanceId())));
        check(before.equals(m.privateView(A))&&m.benchPokemonView(A).isEmpty(),"Invalid revival partially moved cards");cards(m,wanted.subList(0,count));
        check(m.benchPokemonView(A).size()==count&&m.currentPlayer().equals(B)&&m.randomTranscript().size()==rng,"Revival count/shuffle/turn");
        for(var c:wanted.subList(0,count)){UUID logical=PokemonIdentityTestSupport.logical(m,A,c.instanceId());check(m.benchPokemonView(A).stream().anyMatch(v->v.pokemonId().equals(logical)&&v.damage()==0&&v.evolutionCardIds().equals(List.of("base")))&&!m.privateView(A).discard().contains(c),"Revival physical identity/fresh state");}
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
    }}
    static void capacity(){for(int space:List.of(0,1,2,5)){
        var m=game(HO,1);for(int i=0;i<5-space;i++)m.playBasicPokemonToBench(A,hand(m,A,"base").instanceId());
        var wanted=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("base")).limit(3).toList();m.discardHandCardsByEffect(A,A,wanted.stream().map(CardInstance::instanceId).toList());energy(m,"energy");m.attack(A,"flames-of-revival");
        if(space==0)check(!m.hasPendingEffectChoice()&&m.privateView(A).discard().containsAll(wanted),"Full Bench should end without placement");
        else{check(m.effectCardPrompt(A).maximum()==Math.min(3,space),"Revival capacity");cards(m,wanted.subList(0,Math.min(space,3)));}
    }
        var empty=game(HO,1);energy(empty,"energy");empty.attack(A,"flames-of-revival");check(!empty.hasPendingEffectChoice()&&empty.currentPlayer().equals(B),"Empty discard stalled attack");
    }
    static void entryEvents(){for(String source:List.of("DISCARD","HAND")){
        var m=game(HO,1,List.of(GeneratedRuleBatch40Test.event("ON_ENTER_PLAY",source)));var card=hand(m,A,"base");m.discardHandCardsByEffect(A,A,List.of(card.instanceId()));energy(m,"energy");int before=m.privateView(A).hand().size();
        m.attack(A,"flames-of-revival");cards(m,List.of(card));DamageTriggeredAbilityTest.finishTriggers(m);check(m.privateView(A).hand().size()==before+(source.equals("DISCARD")?1:0),"Revival used incorrect entry source");
    }}
    static void brightWing(){var m=game(HO,1);energy(m,"energy");energy(m,"energy");energy(m,"wrong-energy");reject(()->m.attack(A,"bright-wing"));energy(m,"energy");
        m.attack(A,"bright-wing");var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Bright Wing lost mandatory Fire discard");var paid=p.candidates().getFirst();cards(m,List.of(paid));check(m.lastAttack().finalDamage()==130&&m.privateView(A).discard().contains(paid)&&m.activePokemonView(A).attachedEnergyIds().size()==3,"Bright Wing damage/discard changed");
    }
}
