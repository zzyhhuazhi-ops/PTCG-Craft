import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Printed Battle Style attachment restrictions, Lucky Energy reactions and Bruno's KO timing. */
public final class StarterDeck100EnergyTest {
    static final UUID A=StarterDeck100CombatTest.A,B=StarterDeck100CombatTest.B;
    static void check(boolean ok,String text){StarterDeck100CombatTest.check(ok,text);}
    static String id(int n){return StarterDeck100CommonTest.id(n);}
    public static void main(String[] args){styleEnergy(412,7);styleEnergy(414,301);lucky(false);lucky(true);bruno("NONE");bruno("COUNTERS");bruno("CHECKUP");bruno("ATTACK");heracrossCoins();System.out.println("StarterDeck100EnergyTest passed: Battle Styles, unit payment, invalid-host discard, Lucky Energy and KO/Checkup boundaries");}
    static ServerMatch game(int pokemon,int energy){
        return game(pokemon,energy,0);
    }
    static ServerMatch game(int pokemon,int energy,int firstSeed){
        for(int seed=firstSeed;seed<firstSeed+200;seed++){
            var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(StarterDeck100CombatTest.card(A,i<20?id(pokemon):i<40?id(energy):"sd100_cn_grass_energy"));
            var m=new ServerMatch(A,B,deck,StarterDeck100CombatTest.syntheticDeck(B,id(1),null),StarterDeck100CombatTest.RULE,StarterDeck100CombatTest.CARDS,StarterDeck100CombatTest.COMBAT,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);if(m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id(energy))))return m;
        }throw new AssertionError("No special Energy fixture");
    }
    static CardInstance attach(ServerMatch m,UUID actor,UUID owner,String energy,UUID host){var e=m.privateView(owner).deck().stream().filter(c->c.definitionId().equals(energy)).findFirst().orElseThrow();m.attachEnergyByEffect(actor,owner,EffectSourceZone.DECK,e.instanceId(),owner,host);return e;}
    static void styleEnergy(int energy,int host){
        var m=game(host,energy);UUID target=m.activePokemonView(A).pokemonId();var printed=StarterDeck100CombatTest.COMBAT.energyProfile(id(energy)).orElseThrow();
        check(printed.unitCount()==(energy==412?1:2),"wrong number of selectable units");
        check(!EnergyProfile.canPay(List.of(printed),Map.of(EnergyType.FIGHTING,2,energy==412?EnergyType.DARKNESS:EnergyType.WATER,1)),"one Energy unit spent twice");
        if(energy==414)check(EnergyProfile.canPay(List.of(printed),Map.of(EnergyType.FIGHTING,1,EnergyType.WATER,1)),"Rapid Strike cannot split its two units");
        var hand=StarterDeck100CombatTest.hand(m,id(energy));m.attachEnergy(A,hand.instanceId(),target);check(TypedEnergyCountTest.count(m,A,target,energy==412?1:2,null,"UNITS",null),"server supplied units differ from profile");
        m.moveAttachedEnergyByEffect(A,A,target,B,m.activePokemonView(B).pokemonId(),List.of(hand.instanceId()));
        check(m.privateView(A).discard().contains(hand)&&!m.privateView(B).discard().contains(hand),"invalid host did not discard into original owner's discard");
        var wrong=StarterDeck100CommonTest.game(368,0,id(energy));var e=wrong.privateView(A).deck().stream().filter(c->c.definitionId().equals(id(energy))).findFirst().orElseThrow();var before=wrong.privateView(A);TopDeckLookTest.rejects(()->wrong.attachEnergyByEffect(A,A,EffectSourceZone.DECK,e.instanceId(),A,wrong.activePokemonView(A).pokemonId()));check(before.equals(wrong.privateView(A)),"illegal style attachment partially committed");
        if(energy==412){m.endTurn(A);m.endTurn(B);attach(m,A,A,id(412),target);attach(m,A,A,id(412),target);m.attack(A,"horn-attack");check(m.lastAttack().finalDamage()==60,"Single Strike Energy bonuses did not stack before weakness");}
    }
    static void drain(ServerMatch m){int budget=30;while(m.triggerOrderPrompt()!=null&&budget-->0){var p=m.triggerOrderPrompt();m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());}check(budget>0,"automatic trigger loop");}
    static void lucky(boolean lethal){
        var m=game(1,413);var host=m.activePokemonView(A).pokemonId();attach(m,A,A,id(413),host);attach(m,A,A,id(413),host);
        int before=m.privateView(A).hand().size();m.placeDamageCountersByEffect(A,A,host,1);check(before==m.privateView(A).hand().size(),"Lucky Energy triggered on counters");
        if(lethal)m.placeDamageCountersByEffect(A,A,host,m.activePokemonView(A).hitPoints()/10-2);
        attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());m.endTurn(A);
        before=m.privateView(A).hand().size();m.attack(B,StarterDeck100CombatTest.COMBAT.pokemon(id(1)).orElseThrow().attacks().getFirst().id());drain(m);
        if(lethal){check(m.privateView(A).hand().size()==before+2,"lethal damage lost Lucky triggers before KO");StarterDeck100CombatTest.settleKnockouts(m);}
        check(m.currentPlayer().equals(A)&&m.privateView(A).hand().size()==before+3,"each Lucky copy should draw once plus normal turn draw");
    }
    static void bruno(String cause){
        var m=StarterDeck100CommonTest.game(396,1);UUID host=m.activePokemonView(A).pokemonId();if(!cause.equals("NONE"))m.placeDamageCountersByEffect(A,A,host,m.activePokemonView(A).hitPoints()/10-1);
        if(cause.equals("ATTACK")){attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());}
        m.endTurn(A);
        switch(cause){
            case "COUNTERS"->{m.placeDamageCountersByEffect(B,A,host,1);StarterDeck100CombatTest.settleKnockouts(m);m.endTurn(B);}
            case "ATTACK"->{m.attack(B,StarterDeck100CombatTest.COMBAT.pokemon(id(1)).orElseThrow().attacks().getFirst().id());StarterDeck100CombatTest.settleKnockouts(m);}
            case "CHECKUP"->{m.applySpecialConditionByEffect(B,A,SpecialCondition.POISONED);m.endTurn(B);StarterDeck100CombatTest.settleKnockouts(m);}
            default->m.endTurn(B);
        }
        var flag=new ConditionDefinition(ConditionType.KNOCKED_OUT_LAST_TURN,Map.of("target","OWN_ACTIVE"),List.of());
        boolean bonus=cause.equals("ATTACK")||cause.equals("COUNTERS");check(m.matchesEffectCondition(A,m.activePokemonView(A).pokemonId(),flag)==bonus,"KO flag wrong for "+cause);
        StarterDeck100CommonTest.play(m,396);check(m.privateView(A).hand().size()==(bonus?7:4),"Bruno wrong draw count for "+cause);
        m.endTurn(A);m.endTurn(B);check(!m.matchesEffectCondition(A,m.activePokemonView(A).pokemonId(),flag),"Bruno bonus did not expire");
    }
    static void heracrossCoins(){var seen=new HashSet<Integer>();for(int seed=0;seed<80;seed++){var m=game(7,412,seed);m.endTurn(A);m.endTurn(B);var target=m.activePokemonView(A).pokemonId();attach(m,A,A,"sd100_cn_grass_energy",target);attach(m,A,A,"sd100_cn_grass_energy",target);m.attack(A,"single-horn-throw");int damage=m.lastAttack().finalDamage();check(damage==40||damage==200,"two-coin attack used per-head bonus");seen.add(damage);if(seen.size()==2)break;}check(seen.equals(Set.of(40,200)),"did not exercise both coin branches");}
}
