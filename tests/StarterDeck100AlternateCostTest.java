import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Printed alternate costs are replacements, independent of all ordinary cost modifiers. */
public final class StarterDeck100AlternateCostTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static void check(boolean ok,String reason){StarterDeck100PokemonTest.check(ok,reason);}
    static AttackDefinition attack(int n,int slot){return StarterDeck100CombatTest.COMBAT.pokemon(StarterDeck100PokemonTest.id(n)).orElseThrow().attacks().get(slot);}
    public static void main(String[] args){ninja();defiant();modifiers();copied();malformed();System.out.println("StarterDeck100AlternateCostTest passed: switch/damage conditions, expiry, modifier independence and declared copy costs");}
    static void ninja(){var m=StarterDeck100PokemonTest.game(27);var host=m.activePokemonView(A).pokemonId();
        StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_grass_energy",host);
        check(!m.canPayActiveAttackCost(A,attack(27,0)),"Ninja Tornado gained alternate cost without switching");
        var before=m.clientView(A);TopDeckLookTest.rejects(()->m.attack(A,attack(27,0).id()));check(before.equals(m.clientView(A)),"unpaid attack mutated game");
        m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());m.switchActiveByEffect(A,A,host);
        check(m.canPayActiveAttackCost(A,attack(27,0)),"Ninja Tornado did not activate after switching");
        m.attack(A,attack(27,0).id());check(m.lastAttack().finalDamage()==120,"Ninja Tornado failed at one Grass");m.endTurn(B);
        check(!m.canPayActiveAttackCost(A,attack(27,0)),"Ninja Tornado switch condition survived next turn");
    }
    static void defiant(){var m=StarterDeck100PokemonTest.game(154);var host=m.activePokemonView(A).pokemonId();
        StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_lightning_energy",host);
        check(!m.canPayActiveAttackCost(A,attack(154,1)),"Defiant Spark received discount without damage");
        m.placeDamageCountersByEffect(A,A,host,1);check(m.canPayActiveAttackCost(A,attack(154,1)),"one damage counter did not activate Defiant Spark");
        m.healDamageByEffect(A,A,host,10);check(!m.canPayActiveAttackCost(A,attack(154,1)),"healing did not restore printed cost");
        m.placeDamageCountersByEffect(A,A,host,1);m.attack(A,attack(154,1).id());check(m.lastAttack().finalDamage()==130,"Defiant Spark at one Lightning failed");
        var corner=StarterDeck100PokemonTest.game(154);StarterDeck100PokemonTest.attack(corner,0);check(corner.activePokemonView(B).continuousEffectZh().contains("不能撤退"),"Corner lost its retreat lock");
    }
    static void modifiers(){for(int modifier:List.of(-4,4)){
        var printed=attack(27,0);var conditional=printed.withEffectProgram(new EffectProgram(List.of(
            new TriggeredEffects(EffectTrigger.PASSIVE,List.of(new ConditionDefinition(ConditionType.DAMAGE_COUNTER_COUNT,Map.of("target","SELF","value","1"),List.of())),List.of(
                EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"attack_cost_override\",\"amount\":1,\"energy_type\":\"COLORLESS\"}").getAsJsonObject()))))));
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"attack_cost_modifier\",\"amount\":"+modifier+"}"),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,1),conditional);
        var a=DynamicPassiveModifierTest.A;var host=m.activePokemonView(a).pokemonId();
        m.playTrainer(a,DynamicPassiveModifierTest.hand(m,a,"tool").instanceId(),host);m.placeDamageCountersByEffect(a,a,host,1);
        check(!m.canPayActiveAttackCost(a,conditional),"cost reduction erased alternate Colorless cost");
        m.attachEnergy(a,DynamicPassiveModifierTest.hand(m,a,"energy").instanceId(),host);
        check(m.canPayActiveAttackCost(a,conditional),"cost increase applied to alternate requirement");m.attack(a,conditional.id());
    }}
    static void copied(){var declared=CopyAttackTest.copier();var selected=attack(154,1);var materialized=CopyAttackProgram.materialize(declared,selected,EnergyType.PSYCHIC);
        check(materialized.energyCost().equals(declared.energyCost())&&materialized.effectProgram().forTrigger(EffectTrigger.PASSIVE).isEmpty(),"copy retained selected alternate cost");
        var m=CopyAttackTest.fixture(selected);CopyAttackTest.pay(m);var choice=m.copyAttackOptions(CopyAttackTest.A,"copy").stream().filter(c->c.attackId().equals(selected.id())).findFirst().orElseThrow();CopyAttackTest.copy(m,choice,List.of(),List.of());check(m.lastAttack().finalDamage()==260,"copy failed declared one-Colorless cost");
    }
    static void malformed(){var registry=EffectRegistry.standard();for(String json:List.of(
        "{\"type\":\"attack_cost_override\",\"amount\":1,\"energy_type\":\"GRASS\",\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"value\":1}]}",
        "{\"type\":\"attack_cost_override\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"energy_type\":\"GRASS\"}"))TopDeckLookTest.rejects(()->EffectSchemaValidator.validate(registry.parse(JsonParser.parseString(json).getAsJsonObject())));
        EffectSchemaValidator.validate(registry.parse(JsonParser.parseString("{\"type\":\"attack_cost_override\",\"amount\":0,\"energy_type\":\"GRASS\"}").getAsJsonObject()));
        var effect=registry.parse(JsonParser.parseString("{\"type\":\"attack_cost_override\",\"amount\":1,\"energy_type\":\"GRASS\"}").getAsJsonObject());
        for(var phase:List.of(EffectTrigger.PASSIVE,EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.AFTER_ATTACK))TopDeckLookTest.rejects(()->AttackProgramRules.validate(attack(27,0).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(phase,List.of(),List.of(effect)))))));
    }
}
