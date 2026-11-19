import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual generated abilities: event boundaries, owner choices and live passive conditions. */
public final class GeneratedRuleBatch10Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable action){BeforeDamageAttackTest.reject(action);}
    static AbilityDefinition ability(String id){return GeneratedRuleBatch8Test.ability(id);}
    static ServerMatch fixture(AbilityDefinition ability){return fixture(List.of(ability),EnergyType.GRASS);}
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy){return GeneratedRuleBatch8Test.fixture(abilities,energy,EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("sv1_en_176"),GeneratedRuleBatch8Test.hit(40,false));}
    static CardInstance hand(ServerMatch m,String id){return DynamicPassiveModifierTest.hand(m,A,id);}
    static UUID bench(ServerMatch m,UUID owner){return m.benchPokemonView(owner).getFirst().pokemonId();}
    static void evolve(ServerMatch m,UUID host){m.evolveByEffect(A,host,hand(m,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static void optional(ServerMatch m,boolean use){
        var p=m.triggerOrderPrompt();check(p!=null&&p.optionalDecision()&&p.choosingPlayer().equals(A),"Missing owner optional trigger");
        reject(()->m.decideTriggeredEffect(B,p.id(),use));reject(()->m.endTurn(A));m.decideTriggeredEffect(A,p.id(),use);reject(()->m.decideTriggeredEffect(A,p.id(),use));
    }
    static void target(ServerMatch m,UUID id){var p=m.effectPokemonPrompt(A);reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),id));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),id);}
    static void select(ServerMatch m,List<UUID> ids){
        var p=m.effectCardPrompt(A);reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));m.submitEffectCards(A,p.resolutionId(),p.step(),ids);
        if(m.effectCardReveal()!=null)m.acknowledgeEffectReveal(B,m.effectCardReveal().id());
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),ids));
    }
    static void seedDeck(ServerMatch m,String id,int count){var ids=m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id)).limit(count).map(CardInstance::instanceId).toList();check(ids.size()==count,"Fixture lacks deck seed");m.discardHandCardsByEffect(A,A,ids);m.shuffleDiscardIntoDeckByEffect(A,A);}
    public static void main(String[] args){evolutionCounters();drawAndSource();triggeredSearch();triggeredEnergy();triggeredActions();millBoundaries();activeAbilities();passiveDamage();effectGuard();System.out.println("GeneratedRuleBatch10Test passed: hand-only optional events, search/reveal, Energy source/distribution, triggered actions, per-source limits and passive boundaries");}
    static void millBoundaries(){
        var a=ability("sm3_en_067");TriggeredAbilityRules.validate(a);
        for(int count:List.of(0,2,3)){
            var m=fixture(a);m.discardTopDeckCardsUpToByEffect(A,B,m.privateView(B).deck().size()-count);int discarded=m.privateView(B).discard().size();
            evolve(m,bench(m,A));if(count>0)optional(m,true);else check(m.triggerOrderPrompt()==null,"empty-deck mill offered an ineffectual trigger");check(m.privateView(B).deck().isEmpty()&&m.privateView(B).discard().size()==discarded+count,"automatic mill exceeded or skipped remaining deck");
        }
        for(String bad:List.of("{\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":3}","{\"target\":\"DECK\",\"amount\":0}","{\"target\":\"DECK\",\"amount\":61}","{\"target\":\"DECK\",\"amount\":3,\"bind\":\"milled\"}")){
            var data=GeneratedRuleBatch8Test.entry("sm3_en_067").getAsJsonArray("abilities").get(0).getAsJsonObject().deepCopy();var effect=JsonParser.parseString(bad).getAsJsonObject();effect.addProperty("type","discard_top_deck");
            data.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects").set(0,effect);reject(()->TriggeredAbilityRules.validate(BundledCombatData.ability(data,EffectRegistry.standard())));
        }
    }
    static void evolutionCounters(){
        var a=ability("sm6_en_023");
        for(boolean use:List.of(false,true)){
            var m=fixture(a);check(m.triggerOrderPrompt()==null,"opening triggered evolution ability");UUID host=bench(m,A),victim=bench(m,B);
            reject(()->m.useAbility(A,host,a.id()));evolve(m,host);check(m.benchPokemonView(B).getFirst().damage()==0,"event acted before optional choice");optional(m,use);
            if(use){check(m.hasPendingPokemonSelection(),"Gale Shuriken skipped target choice");target(m,victim);}
            check(m.benchPokemonView(B).getFirst().damage()==(use?20:0)&&m.activePokemonView(B).damage()==0,"Gale Shuriken wrong target or counters");
        }
    }
    static void drawAndSource(){
        var a=ability("sm2_en_113");var m=fixture(a);int before=m.privateView(A).hand().size();
        evolve(m,bench(m,A));optional(m,true);check(m.privateView(A).hand().size()==before+2,"Rake It In did not consume evolution then draw three");
        evolve(m,m.activePokemonView(A).pokemonId());optional(m,true);check(m.privateView(A).hand().size()==before+4,"separate evolution instance incorrectly shared a quota");
        var grow=ResumableAbilityTest.ability("grow",DeckEvolutionEffectTest.EVOLVE);var deck=fixture(List.of(a,grow),EnergyType.GRASS);seedDeck(deck,"evolved",1);before=deck.privateView(A).hand().size();
        deck.useAbility(A,deck.activePokemonView(A).pokemonId(),grow.id());var p=deck.effectCardPrompt(A);select(deck,List.of(p.candidates().getFirst().instanceId()));
        check(deck.activePokemonView(A).topCardId().equals("evolved")&&deck.triggerOrderPrompt()==null&&deck.privateView(A).hand().size()==before,"deck evolution incorrectly triggered a HAND-only ability");
    }
    static void triggeredSearch(){
        for(String card:List.of("sm2_en_060","swsh1_en_058"))for(boolean find:List.of(false,true)){
            var a=ability(card);var m=fixture(a);seedDeck(m,"trainer",1);boolean enter=card.equals("sm2_en_060");
            if(enter)m.playBasicPokemonToBench(A,hand(m,"basic").instanceId());else evolve(m,bench(m,A));
            optional(m,true);var p=m.effectCardPrompt(A);
            check(p.minimum()==0&&p.maximum()==(enter?1:2)&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.TRAINER),"triggered Trainer search bounds/filter");
            if(enter)check(p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).trainerCategory()==TrainerCategory.SUPPORTER),"Wonder Tag accepted non-Supporter");
            var found=p.candidates().getFirst();int before=m.privateView(A).hand().size();select(m,find?List.of(found.instanceId()):List.of());
            check(m.privateView(A).hand().size()==before+(find?1:0)&&(!find||m.privateView(A).hand().contains(found)),"triggered search/reveal did not finish");
            check(!m.hasPendingEffectChoice()&&m.triggerOrderPrompt()==null,"search left an unfinished event");
        }
    }
    static void triggeredEnergy(){
        var a=ability("swsh7_en_063");
        for(boolean use:List.of(false,true)){
            var m=fixture(List.of(a),EnergyType.PSYCHIC);var source=hand(m,"basic");m.playBasicPokemonToBench(A,source.instanceId());optional(m,use);
            if(use){var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==2&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Cruel Charge wrong Energy cards");select(m,p.candidates().subList(0,2).stream().map(CardInstance::instanceId).toList());}
            var host=m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(PokemonIdentityTestSupport.logical(m,A,source.instanceId()))).findFirst().orElseThrow();check(host.attachedEnergyIds().size()==(use?2:0)&&!m.energyAttached(A),"Cruel Charge wrong SELF host or manual quota");
            var fromDeck=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("basic")).findFirst().orElseThrow();m.putBasicPokemonFromOwnDeckToBenchByEffect(A,fromDeck.instanceId());check(m.triggerOrderPrompt()==null,"deck entry incorrectly triggered hand-entry ability");
        }
        for(boolean find:List.of(false,true)){
            var m=fixture(List.of(ability("sv3_en_125")),EnergyType.FIRE);seedDeck(m,"energy",3);evolve(m,bench(m,A));optional(m,true);var p=m.effectCardPrompt(A);
            check(p.minimum()==0&&p.maximum()==3&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Infernal Reign deck filter");var picked=p.candidates().subList(0,3).stream().map(CardInstance::instanceId).toList();select(m,find?picked:List.of());
            if(find){var dest=m.effectAttachmentPrompt(A);check(m.hasPendingEnergyDistribution(),"Infernal Reign omitted distribution");UUID active=m.activePokemonView(A).pokemonId(),bench=bench(m,A);for(int i=0;i<3;i++)m.assignEffectEnergyDistribution(A,dest.resolutionId(),dest.step(),picked.get(i),i==0?active:bench);m.confirmEffectEnergyDistribution(A,dest.resolutionId(),dest.step());check(m.activePokemonView(A).attachedEnergyIds().size()==1&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==2,"Infernal Reign incorrect distribution");}
            check(!m.energyAttached(A)&&!m.hasPendingEffectChoice(),"Infernal Reign consumed manual attachment or did not finish");
        }
        var m=fixture(List.of(ability("sm5_en_038")),EnergyType.WATER);var energy=hand(m,"energy");m.discardHandCardsByEffect(A,A,List.of(energy.instanceId()));evolve(m,bench(m,A));optional(m,true);select(m,List.of(energy.instanceId()));var dest=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,dest.resolutionId(),dest.step(),m.activePokemonView(A).pokemonId());check(m.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId()),"Blessings of the Frost did not recover selected Energy");
    }
    static void triggeredActions(){
        var m=fixture(ability("sm2_en_102"));m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),10);evolve(m,bench(m,A));optional(m,true);check(m.activePokemonView(A).damage()==20,"Fresh Egg healed wrong host or amount");
        m=fixture(ability("sm3_en_067"));int before=m.privateView(B).deck().size(),own=m.privateView(A).deck().size();evolve(m,bench(m,A));optional(m,true);check(m.privateView(B).deck().size()==before-3&&m.privateView(A).deck().size()==own,"Toppling Wind milled wrong deck");
        m=fixture(ability("swsh7_en_095"));UUID switched=bench(m,B);evolve(m,bench(m,A));optional(m,true);target(m,switched);check(m.activePokemonView(B).pokemonId().equals(switched),"Dark Signal failed controller-chosen gust");
        m=fixture(ability("swsh8_en_003"));evolve(m,bench(m,A));optional(m,true);check(m.activePokemonView(B).conditions().containsAll(Set.of(SpecialCondition.BURNED,SpecialCondition.CONFUSED,SpecialCondition.POISONED))&&m.activePokemonView(B).damage()==0,"Tricolored Scales status/timing");
        m=fixture(ability("sm4_en_049"));m.playBasicPokemonToBench(A,hand(m,"basic").instanceId());optional(m,true);for(UUID owner:List.of(A,B))check(m.activePokemonView(owner).conditions().containsAll(Set.of(SpecialCondition.CONFUSED,SpecialCondition.POISONED)),"Empty Light omitted an Active");
    }
    static void activeAbilities(){
        var draw=ability("swsh7_en_031");var m=fixture(draw);UUID active=m.activePokemonView(A).pokemonId(),bench=bench(m,A);var game=m;
        reject(()->game.useAbility(A,bench,draw.id()));int before=m.privateView(A).hand().size();m.useAbility(A,active,draw.id());reject(()->game.useAbility(A,active,draw.id()));m.switchActiveByEffect(A,A,bench);m.useAbility(A,bench,draw.id());check(m.privateView(A).hand().size()==before+2,"Fleet-Footed source or shared quota");m.switchActiveByEffect(A,A,active);reject(()->game.useAbility(A,active,draw.id()));
        var heal=ability("rsv10-5_en_002");m=fixture(heal);UUID source=bench(m,A);m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),10);m.placeDamageCountersByEffect(A,A,source,6);m.useAbility(A,source,heal.id());check(m.activePokemonView(A).damage()==80&&m.benchPokemonView(A).getFirst().damage()==60,"Healing Leaves healed source rather than Active");var healing=m;reject(()->healing.useAbility(A,source,heal.id()));
        var counters=ability("swsh6_en_043");m=fixture(counters);UUID shooter=m.activePokemonView(A).pokemonId(),victim=bench(m,B);var shooting=m;
        reject(()->shooting.useAbility(A,shooter,counters.id(),shooter));m.useAbility(A,shooter,counters.id(),victim);check(m.benchPokemonView(B).getFirst().damage()==20,"Quick Shooting wrong target/counters");reject(()->shooting.useAbility(A,shooter,counters.id(),victim));
    }
    static void passiveDamage(){
        for(String id:List.of("sm7_en_090","sv10_en_092"))for(int counters:id.equals("sm7_en_090")?List.of(39,40,41):List.of(1,2,3))for(boolean disabled:List.of(false,true)){
            var a=ability(id);var m=fixture(a);UUID source=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,source,counters);if(disabled)m.disableAbilityEffectsByEffect(A,A,source);m.attack(A,"hit");boolean boost=!disabled&&counters>=(id.equals("sm7_en_090")?40:2);check(m.lastAttack().finalDamage()==40+(boost?(id.equals("sm7_en_090")?80:120):0),"passive HP/counter threshold or suppression");
        }
        var m=GeneratedRuleBatch8Test.fixture(List.of(ability("sm7_en_090")),EnergyType.GRASS,EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("sv1_en_176"),GeneratedRuleBatch8Test.hit(40,true));m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),40);m.attack(A,"hit");check(m.benchPokemonView(B).getFirst().damage()==40,"Danger Perception boosted Bench damage");
    }
    static void effectGuard(){
        var hit=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"hit","nameZh":"效果防护测试","energyCost":{},"baseDamage":40,"damageType":"COLORLESS","effectSupport":"SUPPORTED",
             "triggers":[{"trigger":"AFTER_ATTACK","effects":[{"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":2},{"type":"poison","target":"OPPONENT_ACTIVE"},{"type":"damage_counter","target":"SELF","amount":1}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        for(boolean disabled:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(ability("me2_en_070")),EnergyType.GRASS,EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("sv1_en_176"),hit);UUID host=m.activePokemonView(A).pokemonId();if(disabled)m.disableAbilityEffectsByEffect(A,A,host);m.endTurn(A);m.attack(B,"hit");
            check(m.lastAttack().finalDamage()==40&&m.activePokemonView(A).damage()==(disabled?70:40)&&m.activePokemonView(B).damage()==10,"attack-effect guard blocked damage/own effect or missed opponent effect");m.placeDamageCountersByEffect(A,A,host,1);check(m.activePokemonView(A).damage()==(disabled?80:50),"guard blocked rule-origin counters");
        }
    }
}
