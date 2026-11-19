import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Guards inspect the real attacking card, stay live on the Bench, and never prevent attack effects/counters. */
public final class StarterDeck100GuardTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static final CombatCatalog COMBAT=StarterDeck100CombatTest.COMBAT;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static void prepareOpponent(ServerMatch m,int number,int slot){
        check(m.currentPlayer().equals(B),"opponent fixture turn");
        for(var stage:StarterDeck100PokemonTest.chain(id(number)).subList(1,StarterDeck100PokemonTest.chain(id(number)).size())){
            var card=m.privateView(B).hand().stream().filter(c->c.definitionId().equals(stage)).findFirst().orElse(null);
            if(card==null){card=m.privateView(B).deck().stream().filter(c->c.definitionId().equals(stage)).findFirst().orElseThrow();m.searchOwnDeckToHandByEffect(B,List.of(card.instanceId()));}
            m.evolveByEffect(B,m.activePokemonView(B).pokemonId(),card.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            while(m.triggerOrderPrompt()!=null){var p=m.triggerOrderPrompt();check(p.optionalDecision(),"fixture unexpected mandatory trigger");m.decideTriggeredEffect(p.choosingPlayer(),p.id(),false);}
        }
        for(var cost:COMBAT.pokemon(id(number)).orElseThrow().attacks().get(slot).energyCost().entrySet())for(int i=0;i<cost.getValue();i++)
            StarterDeck100EnergyTest.attach(m,B,B,"sd100_cn_"+(cost.getKey()==EnergyType.COLORLESS?"grass":cost.getKey().name().toLowerCase(Locale.ROOT))+"_energy",m.activePokemonView(B).pokemonId());
    }
    static void attackOpponent(ServerMatch m,int number,int slot){prepareOpponent(m,number,slot);m.attack(B,COMBAT.pokemon(id(number)).orElseThrow().attacks().get(slot).id());}
    static void benchChoice(ServerMatch m,UUID actor,UUID target){var p=m.effectPokemonPrompt(actor);check(p!=null,"Bench target prompt absent");m.chooseEffectPokemon(actor,p.resolutionId(),p.step(),target);}
    public static void main(String[] args){blockface();armor();crystalVeil();skyscraper();benchGuards();malformed();System.out.println("StarterDeck100GuardTest passed: Basic/V/VMAX/special-card filters, name exception, live suppression, Bench damage and defender-effect bypass");}
    static void blockface(){for(int attacker:List.of(1,305)){var m=StarterDeck100PokemonTest.game(123,attacker);m.endTurn(A);prepareOpponent(m,attacker,0);m.endTurn(B);StarterDeck100PokemonTest.attack(m,1);m.attack(B,COMBAT.pokemon(id(attacker)).orElseThrow().attacks().getFirst().id());check(m.lastAttack().finalDamage()==(attacker==1?0:180),"Blockface confused Basic and Evolution");}
        var m=StarterDeck100PokemonTest.game(123);StarterDeck100PokemonTest.attack(m,1);prepareOpponent(m,1,0);m.placeDamageCountersByEffect(B,A,m.activePokemonView(A).pokemonId(),1);check(m.activePokemonView(A).damage()==10,"Blockface prevented counters");m.endTurn(B);m.endTurn(A);m.attack(B,"leaf-drain");check(m.lastAttack().finalDamage()==50,"Blockface failed to expire at next owner turn");
    }
    static void armor(){for(boolean disabled:List.of(false,true)){var m=StarterDeck100PokemonTest.game(305,56);if(disabled)m.disableAbilityEffectsByEffect(A,A,m.activePokemonView(A).pokemonId());m.endTurn(A);attackOpponent(m,56,0);check(m.lastAttack().finalDamage()==(disabled?120:20),"Armor must reduce100 after Fire weakness and stop while suppressed");}
        var m=StarterDeck100PokemonTest.game(305,305);m.endTurn(A);attackOpponent(m,305,0);check(m.lastAttack().finalDamage()==90,"Armor reduced damage from a non-V Evolution");
    }
    static void crystalVeil(){for(int attacker:List.of(259,104,326)){var m=StarterDeck100PokemonTest.game(104,attacker);m.endTurn(A);attackOpponent(m,attacker,0);check(m.lastAttack().finalDamage()==(attacker==259?0:attacker==104?150:220),"Crystal Veil VMAX/name exception/ignore effects failed: "+attacker+" damage="+m.lastAttack().finalDamage());if(attacker==259)check(m.activePokemonView(A).damage()==120&&m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Crystal Veil incorrectly blocked attack Poison/Checkup");}
    }
    static void skyscraper(){for(int variant:List.of(0,1,2)){var m=StarterDeck100PokemonTest.game(326,1,"sm1_en_136");var host=m.activePokemonView(A).pokemonId();if(variant>0){var e=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("sm1_en_136")).findFirst().orElseThrow();m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,e.instanceId(),B,m.activePokemonView(B).pokemonId());}if(variant==2)m.disableAbilityEffectsByEffect(A,A,host);m.endTurn(A);attackOpponent(m,1,0);check(m.lastAttack().finalDamage()==(variant==1?0:50),"Skyscraper special-card presence or suppression failed");}
    }
    static void benchGuards(){for(int defender:List.of(305,104)){
        var m=StarterDeck100PokemonTest.game(defender,104);UUID bench=StarterDeck100EntryTest.evolveBench(m,defender);m.endTurn(A);attackOpponent(m,104,0);benchChoice(m,B,bench);
        check(StarterDeck100PokemonTest.view(m,A,bench).damage()==(defender==305?0:30),"live Bench guard/name exclusion failed");
    }}
    static void malformed(){var registry=EffectRegistry.standard();for(String json:List.of(
        "{\"type\":\"passive_attack_guard\",\"mode\":\"PREVENT\",\"amount\":10}",
        "{\"type\":\"passive_attack_guard\",\"mode\":\"REDUCE\",\"amount\":15}",
        "{\"type\":\"passive_attack_guard\",\"mode\":\"PREVENT\",\"attacker_filter\":\"CLIENT_TARGET\"}"))TopDeckLookTest.rejects(()->registry.parse(com.google.gson.JsonParser.parseString(json).getAsJsonObject()));
        var e=registry.parse(com.google.gson.JsonParser.parseString("{\"type\":\"prevent_damage\",\"source\":\"ABILITY\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_ONLY\",\"duration\":\"LEAVES_PLAY\",\"attacker_filter\":\"BASIC\"}").getAsJsonObject());TopDeckLookTest.rejects(()->ProtectionEffect.from(e));
    }
}
