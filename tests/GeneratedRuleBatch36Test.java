import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Newest ME series: actual card programs, live source position and chosen damage recipients. */
public final class GeneratedRuleBatch36Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void cards(ServerMatch m,List<UUID> ids){GeneratedRuleBatch23Test.cards(m,ids);}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch35Test.attack(id,slot);}
    static void attach(ServerMatch m,String id,UUID host){var c=hand(m,A,id);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,c.instanceId(),A,host);}
    public static void main(String[] args){hp();sourcePosition();benchGuard();counterDamage();energySearch();discardDamage();benchSearch();counters();shuffleDraw();pad();faintingSpell();schema();System.out.println("GeneratedRuleBatch36Test passed: ME5-5 live source positions, supplied Energy HP, Bench damage/effect guard, selected-recipient damage, typed physical discard, private Energy/no-Rule-Box search, Bench capacity, counters, opponent shuffle/draw and actual KO coin reactions");}
    static void hp(){
        var ability=GeneratedRuleBatch8Test.ability("me5-5_en_002");var m=GeneratedRuleBatch19Test.fixture(List.of(ability),EnergyType.GRASS,EnergyType.GRASS,GeneratedRuleBatch19Test.plain(),GeneratedRuleBatch8Test.hit(100,false),Map.of());
        m.drawCardsByEffect(A,A,60);UUID host=active(m,A);attach(m,"wrong",host);attach(m,"special",host);attach(m,"energy",host);attach(m,"energy",host);attach(m,"energy",host);check(m.activePokemonView(A).hitPoints()==500,"Five supplied Grass units must not reach threshold");attach(m,"energy",host);check(m.activePokemonView(A).hitPoints()==750&&m.benchPokemonView(A).getFirst().hitPoints()==500,"HP must count supplied Grass units on this host only");m.disableAbilityEffectsByEffect(A,A,host);check(m.activePokemonView(A).hitPoints()==500,"Suppression retained extra HP");
    }
    static ServerMatch dynamic(String id,boolean spread){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.GRASS,2),DynamicPassiveModifierTest.attack(100,false,spread),List.of(GeneratedRuleBatch8Test.ability(id)));}
    static void sourcePosition(){
        UUID x=DynamicPassiveModifierTest.A,y=DynamicPassiveModifierTest.B;
        for(boolean suppressed:List.of(false,true))for(boolean spread:List.of(false,true)){
            var m=dynamic("me5-5_en_028",spread);if(suppressed)m.disableAbilityEffectsByEffect(x,y,active(m,y));m.attack(x,"hit");check((spread?m.benchPokemonView(y).getFirst():m.activePokemonView(y)).damage()==(suppressed?(spread?100:200):(spread?80:160)),"Lonely Gaze source must be Active and reduce before weakness, including Bench damage");
        }
        var m=dynamic("me5-5_en_096",false);UUID source=bench(m,x),former=active(m,x);m.disableAbilityEffectsByEffect(x,y,bench(m,y));check(m.activeRetreatCost(x)==1&&m.activeRetreatCost(y)==3,"Nighttime Byway own-only Bench source");m.disableAbilityEffectsByEffect(x,x,former);m.switchActiveByEffect(x,x,source);m.disableAbilityEffectsByEffect(x,x,former);check(m.activeRetreatCost(x)==3,"Active source retained Bench aura");m.switchActiveByEffect(x,x,former);check(m.activeRetreatCost(x)==1,"Returning source to Bench failed to restore aura");m.playBasicPokemonToBench(x,DynamicPassiveModifierTest.hand(m,x,"basic").instanceId());check(m.activeRetreatCost(x)==0,"Two independent Bench sources failed to stack");m.disableAbilityEffectsByEffect(x,x,source);check(m.activeRetreatCost(x)==1,"Suppressed source retained stack");
    }
    static void benchGuard(){for(boolean suppressed:List.of(false,true)){
        var m=GeneratedRuleBatch16Test.fixture("me5-5_en_033",Set.of(),"Attacker",false,false,true);UUID target=bench(m,B);if(suppressed)m.disableAbilityEffectsByEffect(A,B,target);m.attack(A,"strike");check(m.benchPokemonView(B).getFirst().damage()==(suppressed?100:0)&&m.activePokemonView(B).damage()==30,"Keep Hidden must protect only its Bench host, leaving Active counter/status effects");
        var a=attack("me5-5_en_090",0);var n=GeneratedRuleBatch19Test.game(a,GeneratedRuleBatch19Test.plain(),List.of(GeneratedRuleBatch8Test.ability("me5-5_en_033")),Map.of());if(suppressed)n.disableAbilityEffectsByEffect(A,B,bench(n,B));n.attack(A,a.id());var p=n.effectPokemonPrompt(A);n.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench(n,B));check(n.benchPokemonView(B).getFirst().damage()==(suppressed?130:0),"Keep Hidden must also prevent selected attack effects");
    }}
    static void counterDamage(){for(boolean toBench:List.of(false,true))for(int damage:List.of(0,2,5)){
        var a=attack("me5-5_en_021",0);var m=GeneratedRuleBatch19Test.game(a);UUID target=toBench?bench(m,B):active(m,B),other=toBench?active(m,B):bench(m,B);if(damage>0)m.placeDamageCountersByEffect(A,B,target,damage);m.placeDamageCountersByEffect(A,B,other,7);m.attack(A,a.id(),target);check((toBench?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==damage*40,"Stealthy Slash read Active instead of chosen recipient or used post-damage counters");check((toBench?m.activePokemonView(B):m.benchPokemonView(B).getFirst()).damage()==70,"Unselected target received damage");
    }}
    static void energySearch(){for(boolean take:List.of(false,true)){
        var a=attack("me5-5_en_037",0);var m=GeneratedRuleBatch19Test.game(a);m.attack(A,a.id());var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==1&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY)&&p.candidates().stream().anyMatch(c->c.definitionId().equals("special"))&&m.clientView(B).effectSelection()==null,"Energy search must include Special, stay private and allow fail-to-find");var found=p.candidates().stream().filter(c->c.definitionId().equals("special")).findFirst().orElseThrow();cards(m,take?List.of(found.instanceId()):List.of());if(take)check(m.privateView(A).hand().contains(found),"Search did not transfer physical Special Energy");
    }}
    static void discardDamage(){for(boolean hasEnergy:List.of(false,true))for(boolean toBench:List.of(false,true)){
        var a=attack("me5-5_en_051",0);var m=GeneratedRuleBatch19Test.fixture(List.of(),EnergyType.LIGHTNING,EnergyType.GRASS,GeneratedRuleBatch19Test.plain(),a,Map.of());UUID host=active(m,A);attach(m,"wrong",host);if(hasEnergy){attach(m,"energy",host);attach(m,"special",host);}UUID target=toBench?bench(m,B):active(m,B);m.attack(A,a.id(),target);check((toBench?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==90,"Typed discard must not gate damage when no matching Energy");check(m.activePokemonView(A).attachedEnergyIds().size()==1,"Typed ALL discard failed to remove Special supplied type or removed wrong Energy");
    }}
    static void benchSearch(){for(boolean decline:List.of(false,true)){
        var a=attack("me5-5_en_053",0);var m=GeneratedRuleBatch19Test.game(a);m.attack(A,a.id());var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()<=4&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicPokemon()),"Any-number search ignored free Bench slots or included Evolutions");var found=p.candidates().stream().limit(decline?0:p.maximum()).map(CardInstance::instanceId).toList();cards(m,found);check(m.benchPokemonView(A).size()==1+found.size()&&!m.hasPendingEffectChoice(),"Bench search did not finish at selected count");
    }}
    static void counters(){for(boolean toBench:List.of(false,true)){
        var a=attack("me5-5_en_090",0);var m=GeneratedRuleBatch19Test.game(a);m.attack(A,a.id());var p=m.effectPokemonPrompt(A);UUID target=toBench?bench(m,B):active(m,B);reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),target));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);check((toBench?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==130,"Chaotic Pain selected counter count");
        var b=attack("me5-5_en_123",1);var n=GeneratedRuleBatch19Test.game(b);n.placeDamageCountersByEffect(A,B,active(n,B),toBench?47:3);n.attack(A,b.id());check(n.activePokemonView(B).damage()==(toBench?470:450),"Remaining-HP counters must stop at 50 HP and never heal an already lower target");
    }}
    static void shuffleDraw(){var a=attack("me5-5_en_094",0);var m=GeneratedRuleBatch19Test.game(a);var own=List.copyOf(m.privateView(A).hand());m.attack(A,a.id());check(m.privateView(B).hand().size()==5&&m.privateView(A).hand().equals(own),"Opponent draws four before its next-turn draw; attacking hand untouched");}
    static void pad(){var m=GeneratedRuleBatch23Test.game("me5-5_en_126");GeneratedRuleBatch23Test.play(m);var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.candidates().stream().allMatch(c->Set.of("basic","evolved").contains(c.definitionId()))&&p.candidates().stream().anyMatch(c->c.definitionId().equals("evolved")),"Poke Pad must accept Evolutions and exclude Rule Box Pokemon");cards(m,List.of());check(!m.hasPendingEffectChoice(),"Poke Pad fail-to-find stalled");}
    static void faintingSpell(){var seen=new HashSet<Integer>();for(boolean toBench:List.of(false,true))for(int offset=0;offset<8;offset++){
        var c=new GeneratedRuleBatch34Test.Config();c.abilities=List.of(GeneratedRuleBatch8Test.ability("me5-5_en_090"));c.attack=GeneratedRuleBatch34Test.hit(500,toBench?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":"");var m=GeneratedRuleBatch34Test.game(c);UUID attacker=active(m,A);UUID physical=PokemonIdentityTestSupport.physical(m,A,attacker);if(offset>0)m.flipCoinsByEffect(A,offset);int before=m.randomTranscript().size();GeneratedRuleBatch34Test.swing(m);var events=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.bound()==2).toList();check(events.size()==1,"Fainting Spell must flip exactly once after actual KO");int coin=events.getFirst().result();seen.add(coin);check(m.privateView(A).discard().stream().anyMatch(card->card.instanceId().equals(physical))==(coin==0),"Fainting Spell failed heads/tails or targeted current Active instead of actual attacker");
    }check(seen.equals(Set.of(0,1)),"Coin fixture must cover both outcomes");
        for(String mode:List.of("nonlethal","disabled","counters","direct","checkup")){
            var c=new GeneratedRuleBatch34Test.Config();c.abilities=List.of(GeneratedRuleBatch8Test.ability("me5-5_en_090"));c.attack=GeneratedRuleBatch34Test.hit(mode.equals("nonlethal")?20:mode.equals("disabled")?500:0,mode.equals("counters")?GeneratedRuleBatch34Test.after("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}"):mode.equals("direct")?GeneratedRuleBatch34Test.after("{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}"):"");var m=GeneratedRuleBatch34Test.game(c);if(mode.equals("disabled"))m.disableAbilityEffectsByEffect(A,B,active(m,B));if(mode.equals("checkup")){m.placeDamageCountersByEffect(A,B,active(m,B),29);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);}int before=m.randomTranscript().size();GeneratedRuleBatch34Test.swing(m);check(m.randomTranscript().subList(before,m.randomTranscript().size()).stream().noneMatch(e->e.bound()==2)&&m.privateView(A).discard().isEmpty(),"Fainting Spell incorrectly triggered from "+mode);
        }
    }
    static void schema(){for(String bad:List.of("\"source_benched_only\":false","\"source_benched_only\":true,\"source_active_only\":true","\"source_benched_only\":\"BENCH\"")){
        reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"retreat_cost_modifier\",\"target\":\"OWN_ACTIVE\",\"amount\":-2,"+bad+"}").getAsJsonObject()));
    }
        var data=GeneratedRuleBatch8Test.entry("me5-5_en_021").getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();data.addProperty("targetCount",2);reject(()->BundledCombatData.attack(data,EffectRegistry.standard()));data.addProperty("targetCount",1);data.getAsJsonArray("damageModifiers").get(0).getAsJsonObject().addProperty("basis","DEFENDER_HAND_SIZE");reject(()->BundledCombatData.attack(data,EffectRegistry.standard()));
        reject(()->DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"retreat_cost_modifier\",\"target\":\"OWN_ACTIVE\",\"amount\":-2,\"source_benched_only\":true}"));
    }
}
