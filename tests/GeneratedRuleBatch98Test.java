import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Goodra and matching promotion; actual evolution/attack plus synthetic retreating hosts. */
public final class GeneratedRuleBatch98Test {
    static final UUID A=RetreatAttemptCoinGateTest.A,B=RetreatAttemptCoinGateTest.B;
    static final String BASIC="me4_en_066",STAGE1="me4_en_067",CARD="me4_en_068",PROMO="mebsp_en_077",FERRO="me4_en_063",V="swsh5_en_109",VMAX="swsh5_en_110";
    static final List<String> ACTUAL=List.of(BASIC,BASIC,STAGE1,STAGE1,CARD,PROMO,FERRO,V,VMAX);
    static final CardDatabase DB=RetreatAttemptCoinGateTest.DB;
    static final CombatCatalog DATA=RetreatAttemptCoinGateTest.DATA;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return RetreatAttemptCoinGateTest.active(m,p);}
    static UUID bench(ServerMatch m,UUID p){return RetreatAttemptCoinGateTest.bench(m,p);}
    static CardInstance obtain(ServerMatch m,UUID p,String id){return RetreatAttemptCoinGateTest.obtain(m,p,id);}
    static CardInstance attach(ServerMatch m,UUID p,UUID h,String id){return RetreatAttemptCoinGateTest.attach(m,p,h,id);}
    static void cycle(ServerMatch m){RetreatAttemptCoinGateTest.cycle(m);}
    static ServerMatch game(int seed){return RetreatAttemptCoinGateTest.game(seed,1,ACTUAL);}
    static UUID goodra(ServerMatch m,UUID p,String id){
        if(!m.currentPlayer().equals(p))m.endTurn(m.currentPlayer());
        UUID host=RetreatAttemptCoinGateTest.put(m,p,BASIC);var first=obtain(m,p,STAGE1);
        reject(()->m.evolve(p,host,first.instanceId()));cycle(m);m.evolve(p,host,first.instanceId());
        var second=obtain(m,p,id);reject(()->m.evolve(p,host,second.instanceId()));cycle(m);m.evolve(p,host,second.instanceId());
        check(m.benchPokemonView(p).stream().anyMatch(v->v.pokemonId().equals(host)&&v.evolutionCardIds().equals(List.of(BASIC,STAGE1,id))),"Actual two-stage evolution");
        return host;
    }
    public static void main(String[] args){
        printed();realRetreat();multipleAndLifecycle();playerBoundary();dragonPulse();prizes();
        System.out.println("GeneratedRuleBatch98Test passed: both actual Goodra prints, normal two-stage evolution, nonstacking live retreat gate, actual Corviknight player-effect inference, Dragon Pulse costs/damage/own deck discard and Ferrothorn exclusion, one prize");
    }
    static void printed(){
        for(String id:List.of(CARD,PROMO)){
            var p=DATA.pokemon(id).orElseThrow();
            check(DB.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&p.hitPoints()==160&&p.retreatCost()==3&&p.prizeCards()==1&&p.pokemonType()==EnergyType.DRAGON&&p.evolvesFromName().equals("Sliggoo")&&p.weaknessType()==null&&p.resistanceType()==null,"Goodra metadata "+id);
            check(p.abilities().size()==1&&p.abilities().getFirst().id().equals("slimy-sliding")&&p.abilities().getFirst().kind()==AbilityDefinition.Kind.PASSIVE,"Actual passive Ability");
            var a=p.attacks().getFirst();check(p.attacks().size()==1&&a.id().equals("dragon-pulse")&&a.baseDamage()==160&&a.energyCost().equals(Map.of(EnergyType.WATER,1,EnergyType.PSYCHIC,1)),"Printed WP160");AttackProgramRules.validate(a);
        }
    }
    static void realRetreat(){
        for(String id:List.of(CARD,PROMO)){
            var seen=new HashSet<Boolean>();
            for(int seed=0;seed<100&&seen.size()<2;seed++){
                var m=game(seed);UUID source=goodra(m,B,id);
                if(seed%2==0)m.switchActiveByEffect(B,B,source);m.endTurn(B);
                UUID old=active(m,A),next=bench(m,A);var paid=attach(m,A,old,"water");int hand=m.privateView(A).hand().size();
                m.retreat(A,next,List.of(paid.instanceId()));boolean success=RetreatAttemptCoinGateTest.heads(m);seen.add(success);
                check(RetreatAttemptCoinGateTest.coins(m)==1&&active(m,A).equals(success?next:old),"Actual Slimy Sliding outcome");
                check(m.privateView(A).discard().contains(paid)==success&&m.privateView(A).hand().size()==hand+(success?1:0),"Actual tails paid Energy or emitted retreat event");
                check(m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Retreat must continue own turn");
            }check(seen.size()==2,"Both server outcomes required for "+id);
        }
    }
    static void multipleAndLifecycle(){
        var m=game(0);UUID one=goodra(m,B,CARD),two=goodra(m,B,PROMO);m.endTurn(B);
        var p=attach(m,A,active(m,A),"water");m.retreat(A,bench(m,A),List.of(p.instanceId()));
        check(RetreatAttemptCoinGateTest.coins(m)==1,"Two actual Goodra must not stack");
        cycle(m);m.devolvePokemonToHandByEffect(A,B,one);m.devolvePokemonToHandByEffect(A,B,two);
        p=attach(m,A,active(m,A),"water");m.retreat(A,bench(m,A),List.of(p.instanceId()));
        check(RetreatAttemptCoinGateTest.coins(m)==1,"Devolved sources left a lingering gate");
        m.endTurn(A);m.evolve(B,one,obtain(m,B,CARD).instanceId());m.endTurn(B);
        p=attach(m,A,active(m,A),"water");m.retreat(A,bench(m,A),List.of(p.instanceId()));
        check(RetreatAttemptCoinGateTest.coins(m)==2,"Re-evolved actual source did not restore live rule");
    }
    static void playerBoundary(){
        var m=game(0);goodra(m,B,CARD);m.endTurn(B);
        UUID host=RetreatAttemptCoinGateTest.put(m,A,V);cycle(m);m.evolve(A,host,obtain(m,A,VMAX).instanceId());m.switchActiveByEffect(A,A,host);
        check(m.activeRetreatCost(A)==0,"Actual Corviknight VMAX zero retreat");
        m.retreat(A,bench(m,A),List.of());
        check(RetreatAttemptCoinGateTest.coins(m)==1,"Pokemon Ability protection must not cancel an instruction to its player");
    }
    static void dragonPulse(){
        for(String id:List.of(CARD,PROMO))for(boolean empty:List.of(false,true)){
            var m=game(0);UUID h=goodra(m,A,id);m.switchActiveByEffect(A,A,h);
            reject(()->m.attack(A,"dragon-pulse"));attach(m,A,h,"water");reject(()->m.attack(A,"dragon-pulse"));attach(m,A,h,"psychic");
            var deck=GeneratedRuleBatch96Test.zone(m,A,"deck");
            CardInstance top;
            if(empty){GeneratedRuleBatch96Test.zone(m,A,"hand").addAll(deck);deck.clear();top=null;}
            else {top=obtain(m,A,FERRO);GeneratedRuleBatch96Test.zone(m,A,"hand").remove(top);deck.add(top);}
            int mine=deck.size(),opponent=m.privateView(B).deck().size();m.attack(A,"dragon-pulse");
            check(m.lastAttack().finalDamage()==160&&m.activePokemonView(B).damage()==160&&m.currentPlayer().equals(B),"Actual Dragon Pulse");
            check(deck.size()==Math.max(0,mine-1)&&m.privateView(B).deck().size()==opponent-1&&m.triggerOrderPrompt()==null,"Own discard triggered Ferrothorn or altered opposing deck");
            if(top!=null)check(m.privateView(A).discard().contains(top),"Exact own top physical card not discarded");
        }
    }
    static void prizes(){
        var m=game(0);UUID h=goodra(m,A,CARD);m.switchActiveByEffect(A,A,h);m.placeDamageCountersByEffect(A,A,h,15);m.endTurn(A);m.attack(B,"hit");
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"Goodra KO missing");m.takePrizeAt(B,0);
        check(m.privateView(B).prizes().size()==5&&m.phase()!=MatchPhase.PRIZE_SELECTION,"Goodra should award one prize");
    }
}
