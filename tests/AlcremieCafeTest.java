import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual reviewed Alcremie profiles, including its named-Supporter exception. */
public final class AlcremieCafeTest {
    static final UUID A=NonPokemonBatch120Test.A,B=NonPokemonBatch120Test.B;
    static final List<String> IDS=List.of("swsh9_en_071","swsh9a_en_008");
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return NonPokemonBatch120Test.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static ServerMatch game(String alcremie,String cafe){
        var kinds=List.of("basic","basic","basic","basic","swsh9_en_070",alcremie,"trainer","copy","grass","water","fire","special");
        var defs=new ArrayList<CardDefinition>();
        for(String id:new LinkedHashSet<>(kinds)){boolean energy=Set.of("grass","water","fire","special").contains(id),pokemon=Set.of("basic","swsh9_en_070",alcremie).contains(id);defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,pokemon||energy?TrainerCategory.NONE:TrainerCategory.SUPPORTER,"H",Set.of("international"),id.equals("basic")||id.equals("swsh9_en_070"),energy&&!id.equals("special"),60,Map.of(),EffectSupport.SUPPORTED,null));}
        var basic=new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(300,false)),List.of(),EnergyType.GRASS);
        var data=NonPokemonBatch2Test.DATA;
        var combat=new CombatCatalog(Map.of("basic",basic,"swsh9_en_070",data.pokemon("swsh9_en_070").orElseThrow(),alcremie,data.pokemon(alcremie).orElseThrow()),Map.of("grass",EnergyType.GRASS,"water",EnergyType.WATER,"fire",EnergyType.FIRE),Map.of("trainer",data.trainer(cafe).orElseThrow(),"copy",data.trainer("sm9_en_154").orElseThrow()),Map.of("special",EnergyProfile.fixed(EnergyType.LIGHTNING,2)));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,kinds.get(i%kinds.size())));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            for(UUID p:List.of(A,B)){var bs=m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).toList();m.chooseOpeningActive(p,bs.getFirst().instanceId());m.placeOpeningBench(p,bs.get(1).instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,24);m.drawCardsByEffect(A,B,24);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->new LinkedHashSet<>(kinds).stream().anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            return m;
        }throw new AssertionError("No Alcremie fixture");
    }
    static UUID evolve(ServerMatch m,UUID p,String id){var base=hand(m,p,"swsh9_en_070");m.playBasicPokemonToBench(p,base.instanceId());UUID host=PokemonIdentityTestSupport.logical(m,p,base.instanceId());m.evolveByEffect(p,host,hand(m,p,id).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);return host;}
    static void select(ServerMatch m,UUID p,List<CardInstance> cards){NonPokemonBatch120Test.select(m,p,cards);}
    static void attach(ServerMatch m,UUID p,UUID host,String id){m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,hand(m,p,id).instanceId(),p,host);}
    public static void main(String[] args){scope();copy();rainbow();ai();schema();System.out.println("AlcremieCafeTest passed: both actual prints and players, active-only named use, suppressed ability, copied Cafe still ends, actual three-type attachment followed by attack, distinct basic types across own field, special/opponent exclusion and four AI difficulties");}
    static void scope(){for(String id:IDS)for(String cafe:NonPokemonBatch120Test.IDS)for(UUID p:List.of(A,B))for(String mode:List.of("active","bench","disabled","switched-away","devolved")){
        var m=game(id,cafe);if(p.equals(B))m.endTurn(A);UUID host=evolve(m,p,id);if(!mode.equals("bench"))m.switchActiveByEffect(p,p,host);
        if(mode.equals("disabled"))m.disableAbilityEffectsByEffect(p,p,host);
        if(mode.equals("switched-away"))m.switchActiveByEffect(p,p,m.benchPokemonView(p).getFirst().pokemonId());
        if(mode.equals("devolved"))m.devolvePokemonToHandByEffect(p,p,host);
        int turn=m.turnNumber();var source=hand(m,p,"trainer");m.playTrainer(p,source.instanceId(),null);select(m,p,List.of());NonPokemonBatch103Test.settle(m);
        boolean continues=mode.equals("active");check(m.currentPlayer().equals(continues?p:NonPokemonBatch120Test.other(p))&&m.turnNumber()==turn+(continues?0:1),"Wrong Alcremie scope "+mode);check(m.privateView(p).discard().contains(source),"Cafe not consumed");if(continues)check(!m.mayPlaySupporter(p),"Additional Order granted a second Supporter");
    }}
    static void copy(){for(String id:IDS)for(UUID p:List.of(A,B)){var m=game(id,NonPokemonBatch120Test.IDS.getFirst());if(p.equals(B))m.endTurn(A);UUID host=evolve(m,p,id);m.switchActiveByEffect(p,p,host);var copied=hand(m,NonPokemonBatch120Test.other(p),"trainer");m.playTrainer(p,hand(m,p,"copy").instanceId(),null);var reveal=m.effectCardReveal();if(reveal!=null)m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());select(m,p,List.of(copied));select(m,p,List.of());NonPokemonBatch103Test.settle(m);check(m.currentPlayer().equals(NonPokemonBatch120Test.other(p)),"Additional Order incorrectly changed copied Supporter");}}
    static void rainbow(){for(String id:IDS){
        var m=game(id,NonPokemonBatch120Test.IDS.getFirst());UUID host=evolve(m,A,id);m.switchActiveByEffect(A,A,host);attach(m,A,host,"special");attach(m,A,host,"grass");attach(m,A,m.benchPokemonView(A).getFirst().pokemonId(),"grass");attach(m,A,m.benchPokemonView(A).getFirst().pokemonId(),"water");attach(m,B,active(m,B),"fire");
        m.attack(A,"rainbow-flavor");check(m.activePokemonView(B).damage()==90,"Rainbow Flavor counted duplicate types, special energy or opponent energy");
        var n=game(id,NonPokemonBatch120Test.IDS.getFirst());UUID own=evolve(n,A,id);n.switchActiveByEffect(A,A,own);attach(n,A,own,"special");NonPokemonBatch120Test.fill(n,A,3);var targets=NonPokemonBatch120Test.hosts(n,A,3);var energy=NonPokemonBatch120Test.prepareEnergy(n,A);n.playTrainer(A,hand(n,A,"trainer").instanceId(),null);select(n,A,targets);select(n,A,energy);NonPokemonBatch120Test.assign(n,A,energy,targets);check(n.currentPlayer().equals(A),"Cafe energy attachment ended Alcremie turn");n.attack(A,"rainbow-flavor");check(n.activePokemonView(B).damage()==130,"Actual Cafe to Rainbow combo damage wrong");
    }}
    static void ai(){for(String id:IDS)for(var level:AiDifficulty.values()){var m=game(id,NonPokemonBatch120Test.IDS.getFirst());UUID host=evolve(m,A,id);m.switchActiveByEffect(A,A,host);attach(m,A,host,"special");NonPokemonBatch120Test.prepareEnergy(m,A);var s=MatchSession.multiplayer(m,A,B);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Alcremie AI stalled "+level);}}
    static void schema(){var e=new EffectDefinition("cafe_master_turn_continues",EffectTarget.SELF,Map.of(),List.of(),List.of());PassiveRuleProgram.validate(e);GeneratedRuleBatch8Test.reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),EffectTarget.OWN_BENCH,Map.of(),List.of(),List.of())));GeneratedRuleBatch8Test.reject(()->DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"cafe_master_turn_continues\",\"target\":\"SELF\"}"));}
}
