import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Live passive quantities must disappear with their source and never turn into lingering modifiers. */
public final class StarterDeck100PassiveCountTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    static AbilityDefinition ability(int n){return StarterDeck100CombatTest.COMBAT.pokemon(id(n)).orElseThrow().abilities().getFirst();}
    static AttackDefinition attack(int n){return StarterDeck100CombatTest.COMBAT.pokemon(id(n)).orElseThrow().attacks().getFirst();}
    public static void main(String[] args){zapdosCost();zapdosDiscard();cinderace();slaking();malformed();System.out.println("StarterDeck100PassiveCountTest passed: live V cost scaling, Special Energy before damage, Prize bonus Active-only and Stadium attack restriction");}
    static void zapdosCost(){for(int n:List.of(217,421)){
        var m=StarterDeck100PokemonTest.game(n);UUID host=m.activePokemonView(A).pokemonId();
        StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_fighting_energy",host);
        check(!m.canPayActiveAttackCost(A,attack(n)),"two opposing V removed three Colorless symbols");
        m.endTurn(A);var extra=StarterDeck100ZoneCountTest.hand(m,B,id(1));m.playBasicPokemonToBench(B,extra.instanceId());m.endTurn(B);
        check(m.canPayActiveAttackCost(A,attack(n)),"third opposing V did not remove last Colorless symbol");
        m.disableAbilityEffectsByEffect(A,A,host);check(!m.canPayActiveAttackCost(A,attack(n)),"suppressed Fighting Instinct retained reduction");
        for(int i=0;i<3;i++)StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_grass_energy",host);
        check(m.canPayActiveAttackCost(A,attack(n)),"printed cost failed after suppression");
        var none=StarterDeck100PokemonTest.game(n,194);UUID other=none.activePokemonView(A).pokemonId();
        for(int i=0;i<4;i++)StarterDeck100EnergyTest.attach(none,A,A,"sd100_cn_grass_energy",other);
        check(!none.canPayActiveAttackCost(A,attack(n)),"Colorless reduction removed typed Fighting requirement");
        StarterDeck100EnergyTest.attach(none,A,A,"sd100_cn_fighting_energy",other);check(none.canPayActiveAttackCost(A,attack(n)),"no opposing V did not permit full printed cost");
    }
        var vmax=StarterDeck100PokemonTest.game(217,192);vmax.endTurn(A);StarterDeck100GuardTest.prepareOpponent(vmax,192,0);vmax.endTurn(B);
        StarterDeck100EnergyTest.attach(vmax,A,A,"sd100_cn_fighting_energy",vmax.activePokemonView(A).pokemonId());
        check(!vmax.canPayActiveAttackCost(A,attack(217)),"VMAX and underlying V counted twice");
        StarterDeck100EnergyTest.attach(vmax,A,A,"sd100_cn_grass_energy",vmax.activePokemonView(A).pokemonId());
        check(vmax.canPayActiveAttackCost(A,attack(217)),"VMAX failed V family match");
    }
    static void zapdosDiscard(){for(boolean special:List.of(false,true)){
        var m=StarterDeck100ZoneCountTest.game(217,"sm1_en_136");UUID target=m.activePokemonView(B).pokemonId();
        var basic=StarterDeck100EnergyTest.attach(m,A,B,"sd100_cn_grass_energy",target);
        CardInstance card=special?StarterDeck100EnergyTest.attach(m,A,B,"sm1_en_136",target):null;
        StarterDeck100PokemonTest.attack(m,0);
        if(special){var p=m.effectCardPrompt(A);check(p.candidates().equals(List.of(card))&&m.activePokemonView(B).damage()==0,"Thunderous Kick did not pause BEFORE damage with only Special Energy");
            TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(basic.instanceId())));
            StarterDeck100TransferTest.select(m,A,List.of(card.instanceId()));check(m.privateView(B).discard().contains(card),"Special Energy did not reach owner discard");}
        check(m.lastAttack().finalDamage()==170&&m.activePokemonView(B).attachedEnergyIds().contains(basic.instanceId()),"Thunderous Kick lost damage/basic Energy");
    }}
    static ServerMatch passiveFixture(int n,AttackDefinition hit){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,1),hit,List.of(ability(n)));}
    static void cinderace(){UUID a=DynamicPassiveModifierTest.A,b=DynamicPassiveModifierTest.B;
        for(String mode:List.of("ZERO_PRIZES","ACTIVE","BENCH","SUPPRESSED","ZERO_DAMAGE")){
            boolean bench=mode.equals("BENCH");var m=passiveFixture(72,DynamicPassiveModifierTest.attack(mode.equals("ZERO_DAMAGE")?0:40,false,bench));
            if(!mode.equals("ZERO_PRIZES")){m.endTurn(a);m.knockOutPokemonByEffect(b,a,m.benchPokemonView(a).getFirst().pokemonId());StarterDeck100CombatTest.settleKnockouts(m);m.endTurn(b);check(m.publicView().get(b).prizeCount()==5,"opponent did not take fixture Prize");}
            if(mode.equals("SUPPRESSED"))m.disableAbilityEffectsByEffect(a,a,m.activePokemonView(a).pokemonId());
            m.attack(a,"hit");int damage=bench?m.benchPokemonView(b).getFirst().damage():m.lastAttack().finalDamage();
            int expected=switch(mode){case "ACTIVE"->140;case "BENCH"->40;case "ZERO_DAMAGE"->0;default->80;};
            check(damage==expected,"Crisis Power scope/layer failed: "+mode+" damage="+damage);
        }
        var real=StarterDeck100PokemonTest.game(72);StarterDeck100PokemonTest.attack(real,0);StarterDeck100CombatTest.settleKnockouts(real);real.endTurn(B);check(!real.mayAttack(A),"Fireball Shot lost next own turn lock");
        var source=real.activePokemonView(A).pokemonId();real.switchActiveByEffect(A,A,real.benchPokemonView(A).getFirst().pokemonId());real.switchActiveByEffect(A,A,source);check(real.mayAttack(A),"Bench did not clear Fireball Shot attack lock");
    }
    static void slaking(){UUID a=DynamicPassiveModifierTest.A,b=DynamicPassiveModifierTest.B;
        for(boolean opponent:List.of(false,true)){
            var m=passiveFixture(346,DynamicPassiveModifierTest.attack(40,false,false));check(m.mayAttack(a),"Slaking blocked without Stadium");
            if(opponent)m.endTurn(a);UUID actor=opponent?b:a;m.playTrainer(actor,DynamicPassiveModifierTest.hand(m,actor,"stadium").instanceId(),null);if(opponent)m.endTurn(b);
            check(!m.mayAttack(a)&&m.activeAttacks(a).isEmpty(),"Stadium did not block Active attacks");var before=m.clientView(a);TopDeckLookTest.rejects(()->m.attack(a,"hit"));check(before.equals(m.clientView(a)),"blocked attack mutated state");
            m.discardStadiumByEffect(a);check(m.mayAttack(a)&&!m.activeAttacks(a).isEmpty(),"removed Stadium left lingering lock");
            m.endTurn(a);m.endTurn(b);
            m.playTrainer(a,DynamicPassiveModifierTest.hand(m,a,"stadium").instanceId(),null);m.disableAbilityEffectsByEffect(a,a,m.activePokemonView(a).pokemonId());check(m.mayAttack(a),"suppressed Slaking still prohibited attack");m.attack(a,"hit");
        }
        var real=StarterDeck100PokemonTest.game(346);StarterDeck100PokemonTest.attack(real,0);check(real.lastAttack().finalDamage()==150,"Rout lost Bench quantity damage");
    }
    static void malformed(){for(String json:List.of(
        "{\"type\":\"attack_damage_modifier\",\"amount\":30,\"direction\":\"RECEIVED\",\"opponent_active_only\":true}",
        "{\"type\":\"attack_cost_modifier\",\"amount\":-1,\"per_opponent_family\":\"MADE_UP\"}",
        "{\"type\":\"attack_damage_modifier\",\"amount\":30,\"direction\":\"DEALT\",\"per_opponent_prize_taken\":false}"))
            TopDeckLookTest.rejects(()->EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject()));
        TopDeckLookTest.rejects(()->DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"attack_cost_modifier\",\"amount\":-1,\"per_opponent_family\":\"V\"}"));
        TopDeckLookTest.rejects(()->DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"prevent_attack\"}"));
    }
}
