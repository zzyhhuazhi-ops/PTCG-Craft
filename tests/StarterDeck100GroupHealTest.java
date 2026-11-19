import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

public final class StarterDeck100GroupHealTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    static int damage(ServerMatch m,UUID host){return StarterDeck100PokemonTest.view(m,A,host).damage();}
    static void play(ServerMatch m,int n){m.playTrainer(A,StarterDeck100PokemonTest.hand(m,id(n)).instanceId(),null);}
    public static void main(String[] args){siebold();healthySelection();singleRapid();cheryl();noEffect();malformed();System.out.println("StarterDeck100GroupHealTest passed: one or two distinct Rapid Strike targets, UI/authority, full Evolution healing and Energy discard only for actual recipients");}
    static void siebold(){for(int count:List.of(1,2)){
        var m=StarterDeck100PokemonTest.game(245,id(402),id(1));UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        var other=StarterDeck100PokemonTest.hand(m,id(1));m.playBasicPokemonToBench(A,other.instanceId());m.placeDamageCountersByEffect(A,A,active,7);m.placeDamageCountersByEffect(A,A,bench,2);m.placeDamageCountersByEffect(A,A,other.instanceId(),2);
        play(m,402);var p=m.effectPokemonPrompt(A);check(!p.canFinish()&&p.targets().size()==2&&p.targets().stream().noneMatch(t->t.pokemonId().equals(PokemonIdentityTestSupport.logical(m,A,other.instanceId()))),"Siebold offered non-Rapid targets or allowed zero targets");
        TopDeckLookTest.rejects(()->m.finishEffectPokemonSelection(A,p.resolutionId(),p.step()));
        var before=m.clientView(A);TopDeckLookTest.rejects(()->m.finishEffectPokemonSelection(B,p.resolutionId(),p.step()));TopDeckLookTest.rejects(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),PokemonIdentityTestSupport.logical(m,A,other.instanceId())));check(before.equals(m.clientView(A)),"forged target/actor mutated heal selection");
        m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active);check(damage(m,active)==70,"healed before complete target set");TopDeckLookTest.rejects(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active));
        check(m.effectPokemonPrompt(A).canFinish()&&MatchSession.practice(m,A,A,B).view(A,A).options().stream().anyMatch(o->o.labelZh().startsWith("完成目标选择")),"missing finish after a valid target");
        if(count==2)m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench);else m.finishEffectPokemonSelection(A,p.resolutionId(),p.step());
        check(damage(m,active)==(count>0?10:70)&&damage(m,bench)==(count==2?0:20)&&damage(m,other.instanceId())==20,"Siebold wrong count, repeated heal or family scope");
        check(!m.mayPlaySupporter(A),"healing failed to consume played Supporter");TopDeckLookTest.rejects(()->m.finishEffectPokemonSelection(A,p.resolutionId(),p.step()));
    }}
    static void healthySelection(){
        var m=StarterDeck100PokemonTest.game(245,id(402));var damaged=m.activePokemonView(A).pokemonId();var healthy=m.benchPokemonView(A).getFirst().pokemonId();
        var extra=StarterDeck100PokemonTest.hand(m,id(245));m.playBasicPokemonToBench(A,extra.instanceId());m.placeDamageCountersByEffect(A,A,damaged,2);play(m,402);var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),healthy);
        check(!m.effectPokemonPrompt(A).canFinish()&&m.effectPokemonPrompt(A).targets().size()==1,"healthy-only selection could finish or offered another healthy final target");var before=m.clientView(A);
        TopDeckLookTest.rejects(()->m.finishEffectPokemonSelection(A,p.resolutionId(),p.step()));TopDeckLookTest.rejects(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),extra.instanceId()));check(before.equals(m.clientView(A)),"invalid healthy-only selection mutated prompt");
        m.chooseEffectPokemon(A,p.resolutionId(),p.step(),damaged);check(damage(m,damaged)==0&&!m.hasPendingPokemonSelection(),"healthy plus damaged is a legal pair");
    }
    static void singleRapid(){var m=StarterDeck100PokemonTest.game(245,id(402));m.knockOutPokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());StarterDeck100CombatTest.settleKnockouts(m);var active=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,active,2);play(m,402);var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active);check(!m.hasPendingPokemonSelection()&&damage(m,active)==0,"one available Rapid target left unfinishable choice");}
    static void shield(ServerMatch m,UUID target){var effect=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield("TRAINER","EFFECTS_ONLY","SELF","LEAVES_PLAY").replace("OPPONENT","ANY")).getAsJsonObject());m.executeEffectProgramByRule(A,target,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(effect)))),EffectTrigger.ON_CARD_PLAYED);}
    static void cheryl(){for(boolean protectedActive:List.of(false,true)){
        var m=StarterDeck100PokemonTest.game(303,id(411),id(1),"sm1_en_136");UUID active=m.activePokemonView(A).pokemonId(),healthy=m.benchPokemonView(A).getFirst().pokemonId();StarterDeck100PokemonTest.evolve(m,A,healthy,303);
        var extra=StarterDeck100PokemonTest.hand(m,id(302));m.playBasicPokemonToBench(A,extra.instanceId());StarterDeck100PokemonTest.evolve(m,A,extra.instanceId(),303);
        var basic=StarterDeck100PokemonTest.hand(m,id(1));m.playBasicPokemonToBench(A,basic.instanceId());
        m.placeDamageCountersByEffect(A,A,active,5);m.placeDamageCountersByEffect(A,A,extra.instanceId(),2);m.placeDamageCountersByEffect(A,A,basic.instanceId(),3);
        var special=StarterDeck100EnergyTest.attach(m,A,A,"sm1_en_136",active);var energy=StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_metal_energy",active);
        var cleanEnergy=StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_metal_energy",healthy);var basicEnergy=StarterDeck100EnergyTest.attach(m,A,A,"sd100_cn_grass_energy",basic.instanceId());
        if(protectedActive)shield(m,active);play(m,411);
        check(damage(m,active)==(protectedActive?50:0)&&damage(m,extra.instanceId())==0&&damage(m,basic.instanceId())==30,"Cheryl did not distinguish Evolution/Basic/protected target");
        check(m.activePokemonView(A).attachedEnergyIds().size()==(protectedActive?2:0),"Cheryl discarded Energy without actual healing or missed healed host");
        check(StarterDeck100PokemonTest.view(m,A,healthy).attachedEnergyIds().contains(cleanEnergy.instanceId())&&StarterDeck100PokemonTest.view(m,A,basic.instanceId()).attachedEnergyIds().contains(basicEnergy.instanceId()),"healthy Evolution or Basic lost Energy");
        check(m.privateView(A).discard().contains(special)!=protectedActive&&m.privateView(A).discard().contains(energy)!=protectedActive,"healed Energy cards did not reach owner discard");
    }}
    static void noEffect(){for(int n:List.of(402,411)){
        var m=StarterDeck100PokemonTest.game(1,id(n));m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),2);var card=StarterDeck100PokemonTest.hand(m,id(n));var before=m.clientView(A);TopDeckLookTest.rejects(()->m.playTrainer(A,card.instanceId(),null));check(before.equals(m.clientView(A)),"ineligible-only field allowed no-effect Supporter: "+n);
    }
        var m=StarterDeck100PokemonTest.game(303,id(411));var card=StarterDeck100PokemonTest.hand(m,id(411));TopDeckLookTest.rejects(()->m.playTrainer(A,card.instanceId(),null));
    }
    static void malformed(){var registry=EffectRegistry.standard();for(String json:List.of(
        "{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":60,\"target_count\":2}",
        "{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":60,\"target_count\":2,\"minimum_targets\":0}",
        "{\"type\":\"heal\",\"mode\":\"ALL\",\"amount\":1000}",
        "{\"type\":\"heal\",\"amount\":60,\"discard_energy_if_healed\":true}",
        "{\"type\":\"heal\",\"mode\":\"ALL\",\"pokemon_stage\":\"UNKNOWN\"}"))
            TopDeckLookTest.rejects(()->HealingEffect.from(registry.parse(JsonParser.parseString(json).getAsJsonObject())));
    }
}
