import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class StarterDeck100TargetCostTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    static AttackDefinition attack(int n,int slot){return StarterDeck100CombatTest.COMBAT.pokemon(id(n)).orElseThrow().attacks().get(slot);}
    public static void main(String[] args){flapple();grimmsnarl();zygarde();sirfetchd();malformed();System.out.println("StarterDeck100TargetCostTest passed: expiring attack cost, selected-target Energy/Prize damage and conditional Resistance bypass");}
    static void flapple(){UUID a=DynamicPassiveModifierTest.A,b=DynamicPassiveModifierTest.B;var hit=attack(41,0);
        for(String mode:List.of("PAY","EXPIRE","SWITCH","EVOLVE","PROTECTED")){
            var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),hit);
            m.attachEnergy(a,DynamicPassiveModifierTest.hand(m,a,"energy").instanceId(),m.activePokemonView(a).pokemonId());m.endTurn(a);
            UUID target=m.activePokemonView(b).pokemonId();m.attachEnergy(b,DynamicPassiveModifierTest.hand(m,b,"energy").instanceId(),target);
            if(mode.equals("PROTECTED"))StarterDeck100TransferTest.shield(m,b,target);m.endTurn(b);
            int retreat=m.activeRetreatCost(b);m.attack(a,hit.id());check(m.activeRetreatCost(b)==retreat,"attack cost increase changed retreat cost");
            if(mode.equals("PROTECTED")){check(m.canPayActiveAttackCost(b,hit),"attack-effect protection failed to stop cost increase");continue;}
            check(!m.canPayActiveAttackCost(b,hit),"Sour Spit failed to add two Colorless symbols");var before=m.clientView(b);TopDeckLookTest.rejects(()->m.attack(b,hit.id()));check(before.equals(m.clientView(b)),"unpaid increased cost mutated match");
            switch(mode){
                case "PAY" -> {m.attachEnergy(b,DynamicPassiveModifierTest.hand(m,b,"special").instanceId(),target);check(m.canPayActiveAttackCost(b,hit),"Double Colorless failed increased cost");m.attack(b,hit.id());}
                case "EXPIRE" -> {m.endTurn(b);check(m.canPayActiveAttackCost(b,hit),"cost increase did not expire at source next turn");}
                case "SWITCH" -> {m.switchActiveByEffect(b,b,m.benchPokemonView(b).getFirst().pokemonId());m.switchActiveByEffect(b,b,target);check(m.canPayActiveAttackCost(b,hit),"Bench failed to clear cost increase");}
                case "EVOLVE" -> {m.evolve(b,target,DynamicPassiveModifierTest.hand(m,b,"evolved").instanceId());check(m.canPayActiveAttackCost(b,hit),"evolution failed to clear cost increase");}
            }
        }
    }
    static void grimmsnarl(){for(boolean bench:List.of(false,true)){
        var m=StarterDeck100PokemonTest.game(288,172);UUID host=m.activePokemonView(A).pokemonId();
        for(int i=0;i<2;i++)StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_darkness_energy",host);
        StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_grass_energy",host);
        var before=m.clientView(A);TopDeckLookTest.rejects(()->m.attack(A,attack(288,0).id(),host));check(before.equals(m.clientView(A)),"ally target forgery changed field");
        var options=MatchSession.practice(m,A,A,B).view(A,A).options().stream().filter(o->o.labelZh().contains(attack(288,0).nameZh())).toList();check(options.size()==2,"selected attack did not offer both Active and Bench");
        UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();m.attack(A,attack(288,0).id(),target);
        check(m.lastAttack().finalDamage()==(bench?60:120)&&m.lastAttack().weaknessApplied()!=bench,"Longhair Shot typed Energy count/Active weakness failed");
    }
        UUID a=DynamicPassiveModifierTest.A,b=DynamicPassiveModifierTest.B;var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.DARKNESS,2),attack(288,0));
        m.attachEnergy(a,DynamicPassiveModifierTest.hand(m,a,"special").instanceId(),m.activePokemonView(a).pokemonId());m.attack(a,attack(288,0).id(),m.benchPokemonView(b).getFirst().pokemonId());check(m.lastAttack().finalDamage()==60,"Longhair Shot counted two-unit Special Energy as one card");
    }
    static void zygarde(){for(int prizes:List.of(0,2))for(boolean bench:List.of(false,true)){
        var m=StarterDeck100PokemonTest.game(322);if(prizes>0){var extra=StarterDeck100PokemonTest.hand(m,id(322));m.playBasicPokemonToBench(A,extra.instanceId());m.endTurn(A);
            for(int i=0;i<prizes;i++){m.knockOutPokemonByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());StarterDeck100CombatTest.settleKnockouts(m);}m.endTurn(B);}
        StarterDeck100PokemonTest.charge(m,1);UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();m.attack(A,attack(322,1).id(),target);
        check(m.lastAttack().finalDamage()==40*prizes,"Judgment Surge did not count opposing Prizes taken");
    }}
    static void sirfetchd(){for(String mode:List.of("NONE","TOOL","DISABLED","REMOVED")){
        var m=StarterDeck100PokemonTest.game(211,172,id(384));UUID host=m.activePokemonView(A).pokemonId();
        if(!mode.equals("NONE")){var tool=StarterDeck100PokemonTest.hand(m,id(384));m.playTrainer(A,tool.instanceId(),host);
            if(mode.equals("DISABLED"))m.disableToolEffectsByEffect(A,A,host);
            if(mode.equals("REMOVED"))m.discardToolByEffect(A,A,host,tool.instanceId());}
        StarterDeck100PokemonTest.attack(m,1);boolean held=mode.equals("TOOL")||mode.equals("DISABLED");
        check(m.lastAttack().finalDamage()==(held?160:40)&&m.lastAttack().resistanceApplied()!=held,"Leek Strike conditional Resistance/Tool possession failed: "+mode);
    }
        // Copying retains the text and checks the current attacker's Tool. Weakness remains even when Resistance is bypassed.
        UUID a=DynamicPassiveModifierTest.A;var copied=CopyAttackProgram.materialize(CopyAttackTest.copier(),attack(211,1),EnergyType.GRASS);
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,1),copied,List.of(),30);
        m.playTrainer(a,DynamicPassiveModifierTest.hand(m,a,"tool").instanceId(),m.activePokemonView(a).pokemonId());m.attachEnergy(a,DynamicPassiveModifierTest.hand(m,a,"energy").instanceId(),m.activePokemonView(a).pokemonId());m.attack(a,copied.id());check(m.lastAttack().finalDamage()==320&&m.lastAttack().weaknessApplied()&&!m.lastAttack().resistanceApplied(),"copied Leek Strike ignored Weakness or lost condition");
    }
    static void malformed(){for(String json:List.of("{\"type\":\"increase_attack_cost\",\"target\":\"SELF\",\"amount\":2}","{\"type\":\"increase_attack_cost\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":0}","{\"type\":\"ignore_resistance\",\"amount\":1}"))TopDeckLookTest.rejects(()->EffectSchemaValidator.validate(EffectRegistry.standard().parse(JsonParser.parseString(json).getAsJsonObject())));
        var effect=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"increase_attack_cost\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}").getAsJsonObject());
        TopDeckLookTest.rejects(()->AttackProgramRules.validate(attack(41,0).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.BEFORE_ATTACK,List.of(),List.of(effect)))))));
        var coin=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"coin_flip\",\"flips\":1,\"heads\":[{\"type\":\"damage_per_energy\",\"amount\":30}]}" ).getAsJsonObject());
        AttackProgramRules.validate(attack(288,0).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(coin))))));
    }
}
