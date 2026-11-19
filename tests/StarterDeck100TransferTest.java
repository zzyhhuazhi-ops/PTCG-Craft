import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100TransferTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static void select(ServerMatch m,UUID actor,List<UUID> ids){var p=m.effectCardPrompt(actor);m.submitEffectCards(actor,p.resolutionId(),p.step(),ids);}
    static void move(ServerMatch m,UUID actor,UUID target){var p=m.effectAttachmentPrompt(actor);m.chooseEffectAttachmentTarget(actor,p.resolutionId(),p.step(),target);}
    public static void main(String[] args){lapras();ribombee();bronzong();incompatibleHost();grapploct();stonjourner();invalid();System.out.println("StarterDeck100TransferTest passed: all-Energy return, optional transfers, blocked destinations, incompatible hosts, unlimited typed transfer and live Active/Stadium conditions");}
    static void lapras(){var m=StarterDeck100PokemonTest.game(86,"sm1_en_136");StarterDeck100PokemonTest.charge(m,1);StarterDeck100EnergyTest.attach(m,A,A,"sm1_en_136",m.activePokemonView(A).pokemonId());var cards=List.copyOf(m.activePokemonView(A).attachedEnergyIds());UUID bench=m.benchPokemonView(B).getFirst().pokemonId();
        var before=m.clientView(A);TopDeckLookTest.rejects(()->m.attack(A,"splash-arch",m.activePokemonView(B).pokemonId()));check(before.equals(m.clientView(A)),"invalid Bench target returned Energy before rejection");
        m.attack(A,"splash-arch",bench);check(m.activePokemonView(A).attachedEnergyIds().isEmpty()&&m.privateView(A).hand().stream().map(CardInstance::instanceId).toList().containsAll(cards),"Splash Arch did not return ALL Energy to hand");check(m.activePokemonView(B).damage()==0&&m.benchPokemonView(B).getFirst().damage()==100,"Splash Arch hit Active or applied Bench weakness");
    }
    static void shield(ServerMatch m,UUID owner,UUID target){var effect=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield("ATTACK","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START")).getAsJsonObject());m.executeEffectProgramByRule(owner,target,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(effect)))),EffectTrigger.ON_CARD_PLAYED);}
    static void ribombee(){for(String mode:List.of("MOVE","DECLINE","SOURCE_SHIELD","DESTINATION_SHIELD")){
        var m=StarterDeck100PokemonTest.game(194);UUID source=m.activePokemonView(B).pokemonId(),target=m.benchPokemonView(B).getFirst().pokemonId();var energy=StarterDeck100EnergyTest.attach(m,A,B,"sd100_cn_grass_energy",source);
        if(mode.endsWith("SHIELD")){m.endTurn(A);shield(m,B,mode.equals("SOURCE_SHIELD")?source:target);m.endTurn(B);}
        StarterDeck100PokemonTest.attack(m,0);select(m,A,mode.equals("DECLINE")?List.of():List.of(energy.instanceId()));
        if(!mode.equals("DECLINE"))move(m,A,target);
        boolean stays=mode.equals("DECLINE")||mode.equals("SOURCE_SHIELD");
        check(m.activePokemonView(B).attachedEnergyIds().contains(energy.instanceId())==stays,"Tricky Steps source removal wrong: "+mode);
        check(m.benchPokemonView(B).getFirst().attachedEnergyIds().contains(energy.instanceId())==mode.equals("MOVE"),"Tricky Steps destination wrong: "+mode);
        check(m.privateView(B).discard().contains(energy)==mode.equals("DESTINATION_SHIELD"),"immune destination must discard detached Energy: "+mode);
    }}
    static void bronzong(){var m=StarterDeck100PokemonTest.game(303);UUID source=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        var grass=StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_grass_energy",source);var metal=StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_metal_energy",bench);
        for(UUID target:List.of(source,bench)){StarterDeck100PokemonTest.ability(m,source);var p=m.effectCardPrompt(A);check(p.candidates().equals(List.of(metal)),"Metal Transfer offered non-Metal or omitted Bench source");TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(grass.instanceId())));select(m,A,List.of(metal.instanceId()));var prompt=m.effectAttachmentPrompt(A);check(!prompt.targets().contains(target.equals(source)?bench:source),"Metal Transfer offered same host");move(m,A,target);}
        check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(metal.instanceId()),"unlimited Metal Transfer failed second use");m.disableAbilityEffectsByEffect(A,A,source);check(!m.canUseAbility(A,source,"metal-transfer"),"suppressed Bronzong still activated");
    }
    static void incompatibleHost(){var m=StarterDeck100ZoneCountTest.game(194,id(245),id(414));m.endTurn(A);var rapid=StarterDeck100ZoneCountTest.hand(m,B,id(245));m.playBasicPokemonToBench(B,rapid.instanceId());var energy=StarterDeck100EnergyTest.attach(m,B,B,id(414),rapid.instanceId());var target=m.activePokemonView(B).pokemonId();m.switchActiveByEffect(B,B,rapid.instanceId());m.endTurn(B);StarterDeck100PokemonTest.attack(m,0);select(m,A,List.of(energy.instanceId()));check(m.effectAttachmentPrompt(A).targets().contains(target),"non-Rapid transfer destination incorrectly excluded");move(m,A,target);check(m.privateView(B).discard().contains(energy)&&StarterDeck100PokemonTest.view(m,B,target).attachedEnergyIds().isEmpty(),"transferred Rapid Strike Energy was not discarded on incompatible host");
    }
    static void grapploct(){var aura=StarterDeck100PokemonTest.game(244);UUID source=aura.activePokemonView(A).pokemonId(),bench=aura.benchPokemonView(A).getFirst().pokemonId();int base=StarterDeck100CombatTest.COMBAT.pokemon(id(1)).orElseThrow().retreatCost();check(aura.activeRetreatCost(B)==base+2,"Active Grapploct did not increase opponent retreat");aura.switchActiveByEffect(A,A,bench);check(aura.activeRetreatCost(B)==base,"Benched source retained aura");aura.switchActiveByEffect(A,A,source);check(aura.activeRetreatCost(B)==base+2,"returning Active source did not restore aura");aura.disableAbilityEffectsByEffect(A,A,source);check(aura.activeRetreatCost(B)==base,"suppressed source retained aura");
        for(boolean equal:List.of(false,true)){var m=StarterDeck100PokemonTest.game(244);for(UUID player:List.of(A,B)){m.drawCardsByEffect(A,player,10);var hand=m.privateView(player).hand();int keep=player.equals(A)&&!equal?5:4;m.discardHandCardsByEffect(A,player,hand.subList(keep,hand.size()).stream().map(CardInstance::instanceId).toList());}StarterDeck100PokemonTest.attack(m,0);check(m.lastAttack().finalDamage()==(equal?160:80),"Synchro Buster miscompared hand sizes");}
    }
    static void stonjourner(){var attack=StarterDeck100CombatTest.COMBAT.pokemon(id(246)).orElseThrow().attacks().getFirst();var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.FIGHTING,2),attack);UUID a=DynamicPassiveModifierTest.A,b=DynamicPassiveModifierTest.B;m.attachEnergy(a,DynamicPassiveModifierTest.hand(m,a,"special").instanceId(),m.activePokemonView(a).pokemonId());m.attack(a,attack.id());check(m.lastAttack().finalDamage()==30,"Lands Pulse bonus without Stadium");m.playTrainer(b,DynamicPassiveModifierTest.hand(m,b,"stadium").instanceId(),null);m.endTurn(b);m.attack(a,attack.id());check(m.lastAttack().finalDamage()==60,"opponent Stadium did not grant Lands Pulse bonus");m.endTurn(b);m.discardStadiumByEffect(a);m.attack(a,attack.id());check(m.lastAttack().finalDamage()==30,"removed Stadium retained bonus");
        var real=StarterDeck100PokemonTest.game(246);StarterDeck100PokemonTest.attack(real,1);real.endTurn(B);check(real.activeAttacks(A).stream().noneMatch(x->x.id().equals("giga-hammer")),"Giga Hammer lost next-turn restriction");
    }
    static void invalid(){DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"retreat_cost_modifier\",\"amount\":2,\"source_active_only\":true}");}
}
