import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Delibird's optional two-player hand attachment and retained Flap. */
public final class GeneratedRuleBatch81Test {
    static final UUID A=new UUID(0,8111),B=new UUID(0,8112),WATCH=new UUID(0,8113);
    static final String CARD="me4_en_018";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static ServerMatch game(){
        var defs=new ArrayList<CardDefinition>();defs.add(DB.find(CARD).orElseThrow());
        for(String id:List.of("base","water","fire","special"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),Set.of("water","fire").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null));
        var catalog=new CombatCatalog(Map.of(CARD,DATA.pokemon(CARD).orElseThrow(),"base",new PokemonProfile(1000,1,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(),EnergyType.COLORLESS)),Map.of("water",EnergyType.WATER,"fire",EnergyType.FIRE),Map.of(),Map.of("special",EnergyProfile.fixed(EnergyType.WATER,2)));
        String[] pool={CARD,"base","base","water","water","fire","fire","special"};
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool[i%pool.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(CARD))||List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,p.equals(A)?CARD:"base").instanceId());m.placeOpeningBench(p,hand(m,p,"base").instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,26);m.drawCardsByEffect(A,B,26);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->Arrays.stream(pool).anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;return m;
        }throw new AssertionError("No actual Delibird fixture");
    }
    static void pay(ServerMatch m,String id){m.attachEnergy(A,hand(m,A,id).instanceId(),active(m,A));}
    static List<CardInstance> choose(ServerMatch m,UUID p,int count){return OpponentEnergyAttachmentTest.choose(m,p,count);}
    static void distribute(ServerMatch m,UUID p,List<CardInstance> cards){OpponentEnergyAttachmentTest.distribute(m,p,cards);}
    public static void main(String[] args){printed();present();emptyAndOptional();flap();copied();System.out.println("GeneratedRuleBatch81Test passed: actual Delibird90HP/Water/Metal weakness/retreat1, C-cost Pleasing Present opponent completes up-to3 Basic hand attachments before independent owner choice, all colors/multiple hosts/privacy/manual-cap independence, empty sources/declines, CC40 Flap and actual copied attack controller");}
    static void printed(){var c=DB.find(CARD).orElseThrow();var p=DATA.pokemon(CARD).orElseThrow();check(c.effectSupport()==EffectSupport.SUPPORTED&&c.basicPokemon()&&p.hitPoints()==90&&p.pokemonType()==EnergyType.WATER&&p.weaknessType()==EnergyType.METAL&&p.weaknessMultiplier()==2&&p.retreatCost()==1&&p.resistanceType()==null&&p.prizeCards()==1&&p.abilities().isEmpty(),"Printed Delibird facts");check(p.attacks().size()==2&&p.attacks().getFirst().id().equals("pleasing-present")&&p.attacks().getFirst().energyCost().equals(Map.of(EnergyType.COLORLESS,1))&&p.attacks().getFirst().baseDamage()==0&&p.attacks().getLast().id().equals("flap")&&p.attacks().getLast().energyCost().equals(Map.of(EnergyType.COLORLESS,2))&&p.attacks().getLast().baseDamage()==40,"Printed attacks/costs");p.attacks().forEach(AttackProgramRules::validate);}
    static void present(){var m=game();var before=m.clientView(A);reject(()->m.attack(A,"pleasing-present"));check(before.equals(m.clientView(A)),"Unpaid attack changed state");pay(m,"fire");check(m.energyAttached(A),"Fixture did not consume manual attachment");m.attack(A,"pleasing-present");var p=m.effectCardPrompt(B);check(p.candidates().stream().allMatch(c->c.ownerId().equals(B)&&m.cardDefinition(c.definitionId()).basicEnergy())&&p.candidates().stream().map(CardInstance::definitionId).collect(java.util.stream.Collectors.toSet()).equals(Set.of("water","fire")),"Opponent Basic/all-color candidates");var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);check(session.view(A,A).game().effectSelection()==null&&session.view(WATCH,null).game().effectSelection()==null,"Actual private hand leaked");var cs=choose(m,B,3);distribute(m,B,cs);check(m.pendingChoicePlayer().equals(A)&&m.activePokemonView(B).attachedEnergyIds().size()==2&&m.benchPokemonView(B).getFirst().attachedEnergyIds().size()==1,"Opponent attachment not committed first");cs=choose(m,A,3);distribute(m,A,cs);check(m.activePokemonView(A).attachedEnergyIds().size()==3&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice()&&m.lastAttack().finalDamage()==0,"Own attachment/manual cap/end turn");}
    static void emptyAndOptional(){for(boolean empty:List.of(false,true)){var m=game();pay(m,"water");if(empty)m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().map(CardInstance::instanceId).toList());m.attack(A,"pleasing-present");if(!empty)choose(m,B,0);check(m.pendingChoicePlayer().equals(A),"Opponent empty/decline blocked owner");if(empty)distribute(m,A,choose(m,A,1));else choose(m,A,0);check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Optional/empty completion");}
        for(boolean both:List.of(false,true)){var m=game();pay(m,"water");for(UUID p:both?List.of(A,B):List.of(A))m.discardHandCardsByEffect(A,p,m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicEnergy()).map(CardInstance::instanceId).toList());m.attack(A,"pleasing-present");if(!both)distribute(m,B,choose(m,B,1));check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Empty own/both sources stranded attack");}
    }
    static void flap(){var m=game();pay(m,"fire");reject(()->m.attack(A,"flap"));m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,"water").instanceId(),A,active(m,A));m.attack(A,"flap");check(m.activePokemonView(B).damage()==40&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Flap40 or CC payment changed");}
    static void copied(){var selected=DATA.pokemon(CARD).orElseThrow().attacks().getFirst();var m=CopyAttackTest.fixture(CopyAttackTest.copier(),selected,EnergyType.PSYCHIC,0);CopyAttackTest.pay(m);var c=m.copyAttackOptions(CopyAttackTest.A,"copy").stream().filter(x->x.attackId().equals(selected.id())).findFirst().orElseThrow();CopyAttackTest.copy(m,c,List.of(),List.of());choose(m,CopyAttackTest.B,0);choose(m,CopyAttackTest.A,0);check(m.currentPlayer().equals(CopyAttackTest.B)&&!m.hasPendingEffectChoice(),"Actual copied Present ownership");}
}
