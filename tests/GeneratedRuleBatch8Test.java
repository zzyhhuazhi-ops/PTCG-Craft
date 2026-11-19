import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated card programs in owned 60-card matches; synthetic hosts isolate each effect. */
public final class GeneratedRuleBatch8Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final Ruleset RULES=BundledRules.loadRulesets().get("standard-international-2026");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable action){BeforeDamageAttackTest.reject(action);}
    static JsonObject entry(String id){
        String set=id.substring(0,id.indexOf("_en_"));
        try(var in=GeneratedRuleBatch8Test.class.getResourceAsStream("/data/ptcglive/combat/generated-"+set+".json")){
            var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var e:data.getAsJsonArray("cards"))if(e.getAsJsonObject().get("id").getAsString().equals(id))return e.getAsJsonObject();
            throw new AssertionError("Missing generated card "+id);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    static AbilityDefinition ability(String id){
        var data=entry(id).getAsJsonArray("abilities").get(0).getAsJsonObject();
        check(data.get("effectSupport").getAsString().equals("SUPPORTED"),id+" ability not supported");
        return BundledCombatData.ability(data,EffectRegistry.standard());
    }
    static TrainerProfile trainer(String id){return BundledCombatData.trainer(entry(id),EffectRegistry.standard());}
    static AttackDefinition hit(int damage,boolean bench){return BundledCombatData.attack(JsonParser.parseString(
        "{\"id\":\"hit\",\"nameZh\":\"测试\",\"energyCost\":{},\"baseDamage\":"+damage+",\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\""+
        (bench?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":"")+"}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy,EnergyType type,TrainerProfile trainer,AttackDefinition attack){
        return fixture(abilities,energy,type,trainer,attack,1);
    }
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy,EnergyType type,TrainerProfile trainer,AttackDefinition attack,int retreat){
        return fixture(abilities,energy,type,trainer,attack,retreat,Set.of());
    }
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy,EnergyType type,TrainerProfile trainer,AttackDefinition attack,int retreat,Set<BattleStyle> styles){
        return fixture(abilities,energy,type,trainer,attack,retreat,styles,false);
    }
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy,EnergyType type,TrainerProfile trainer,AttackDefinition attack,int retreat,Set<BattleStyle> styles,boolean firstTurn){
        String[] ids={"basic","basic","basic","basic","energy","energy","wrong","special","tool","tool","tool","tool","trainer","evolved","rulebox"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","rulebox").contains(id),isEnergy=Set.of("energy","wrong","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:isEnergy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("trainer")?trainer.category():id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),
                id.equals("basic")||id.equals("rulebox"),id.equals("energy")||id.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null,
                id.equals("rulebox")?Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX):Set.of(),pokemon?Set.of(id.equals("evolved")?EnergyType.PSYCHIC:type):Set.of(),pokemon?styles:Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","rulebox"))pokemon.put(id,new PokemonProfile(500,id.equals("evolved")&&retreat>1?retreat-1:retreat,id.equals("evolved")?"basic":null,null,0,null,0,
            id.equals("rulebox")?2:1,List.of(attack),id.equals("rulebox")?List.of():abilities,id.equals("evolved")?EnergyType.PSYCHIC:type));
        var combat=new CombatCatalog(pokemon,Map.of("energy",energy,"wrong",energy==EnergyType.FIRE?EnergyType.WATER:EnergyType.FIRE),
            Map.of("trainer",trainer,"tool",DynamicPassiveModifierTest.plainTool()),Map.of("special",EnergyProfile.fixed(energy,2)));
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).toList();return basics.size()<2||basics.subList(0,2).stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);if(!firstTurn){m.endTurn(A);m.endTurn(B);}
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))&&
                m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).count()>=5)return m;
        }throw new AssertionError("No batch 8 fixture");
    }
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy,EnergyType type){return fixture(abilities,energy,type,trainer("swsh5_en_124"),hit(40,false));}
    static CardInstance hand(ServerMatch m,String id){return DynamicPassiveModifierTest.hand(m,A,id);}
    public static void main(String[] args){aura();unlimited();capacity();guard();judge();recycler();System.out.println("GeneratedRuleBatch8Test passed: actual typed auras, unrestricted/filtered hand Energy, Tool capacity, Ability guard, Judge and Energy Recycler");}
    static void aura(){
        var aura=ability("sm2_en_059");
        for(String mode:List.of("two","one","suppressed","wrong-type","bench","zero")){
            var m=fixture(List.of(aura),EnergyType.METAL,mode.equals("wrong-type")?EnergyType.PSYCHIC:EnergyType.METAL,trainer("swsh5_en_124"),hit(mode.equals("zero")?0:40,mode.equals("bench")));
            UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
            if(mode.equals("one")){m.disableAbilityEffectsByEffect(A,A,active);m.evolveByEffect(A,bench,hand(m,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
            if(mode.equals("suppressed")){m.disableAbilityEffectsByEffect(A,A,active);m.disableAbilityEffectsByEffect(A,A,bench);}
            m.attack(A,"hit");int damage=mode.equals("bench")?m.benchPokemonView(B).getFirst().damage():m.activePokemonView(B).damage();
            check(damage==(mode.equals("two")?60:mode.equals("one")?50:mode.equals("zero")?0:40),"Steelworker recipient/source/stacking boundary: "+mode+" = "+damage);
        }
    }
    static void unlimited(){
        var cases=Map.of("rsv10-5_en_013",EnergyType.FIRE,"sm5_en_083",EnergyType.METAL,"swsh1_en_064",EnergyType.WATER,"swsh5_en_008",EnergyType.GRASS);
        for(var row:cases.entrySet()){
            var ability=ability(row.getKey());var m=fixture(List.of(ability),row.getValue(),row.getValue());
            UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
            var rulebox=hand(m,"rulebox");m.playBasicPokemonToBench(A,rulebox.instanceId());UUID blocked=rulebox.instanceId();
            boolean ice=row.getKey().equals("swsh1_en_064"),bloom=row.getKey().equals("swsh5_en_008");
            if(ice){var other=hand(m,"basic");m.playBasicPokemonToBench(A,other.instanceId());m.evolveByEffect(A,other.instanceId(),hand(m,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);blocked=other.instanceId();}
            UUID illegalHost=blocked;
            for(int use=0;use<2;use++){
                m.useAbility(A,use==0?active:bench,ability.id());
                if(m.hasPendingPokemonSelection()){
                    var p=m.effectPokemonPrompt(A);check(p.targets().stream().noneMatch(t->t.pokemonId().equals(illegalHost)),"filtered host appeared: "+row.getKey());
                    reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),illegalHost));
                    if(ice)reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active));
                    reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),bench));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench);
                }else check(!ice&&!bloom,"filtered ability omitted host choice");
                var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"wrong basic/special Energy accepted");
                UUID chosen=p.candidates().getFirst().instanceId();reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(hand(m,"special").instanceId())));
                reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(chosen)));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen));
                if(m.hasPendingAttachmentTarget()){var t=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,t.resolutionId(),t.step(),bench);}
                reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen)));check(!m.hasPendingEffectChoice(),"Energy ability did not finish");
            }
            check(m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==2&&!m.energyAttached(A),"unlimited ability consumed manual attachment");
            m.attachEnergy(A,hand(m,"energy").instanceId(),active);check(m.energyAttached(A),"manual attachment unavailable after ability");
            var remaining=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).map(CardInstance::instanceId).toList();
            if(!remaining.isEmpty())m.discardHandCardsByEffect(A,A,remaining);
            reject(()->m.useAbility(A,active,ability.id()));
        }
    }
    static void capacity(){
        for(String id:List.of("sm8_en_130","sv3_en_156")){
            var m=fixture(List.of(ability(id)),EnergyType.METAL,EnergyType.METAL);UUID host=m.activePokemonView(A).pokemonId();int cap=id.equals("sv3_en_156")?4:2;
            check(m.currentToolCapacity(A,host)==cap,"wrong printed capacity");for(int i=0;i<cap;i++)m.playTrainer(A,hand(m,"tool").instanceId(),host);
            reject(()->m.playTrainer(A,hand(m,"tool").instanceId(),host));m.disableAbilityEffectsByEffect(A,A,host);
            check(m.toolOverflowPrompt()!=null&&m.toolOverflowPrompt().owner().equals(A),"capacity loss omitted owner choice");
            reject(()->m.attack(A,"hit"));reject(()->m.endTurn(A));var p=m.toolOverflowPrompt();UUID chosen=p.tools().getLast().instanceId();
            reject(()->m.discardOverflowTool(B,p.id(),chosen));m.discardOverflowTool(A,p.id(),chosen);reject(()->m.discardOverflowTool(A,p.id(),chosen));ToolCapacityResolutionTest.clear(m);
            check(m.activePokemonView(A).attachedTools().size()==1&&m.privateView(A).discard().stream().anyMatch(c->c.instanceId().equals(chosen)),"overflow cleanup did not retain one");
        }
    }
    static void guard(){
        var shooting=StarterDeck100PokemonTest.COMBAT.pokemon("sd100_cn_118").orElseThrow().abilities().getFirst();
        var own=ResumableAbilityTest.ability("own-counter","{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":1}");
        for(boolean disabled:List.of(false,true)){
            var guard=ability("swsh5_en_110");var m=fixture(List.of(guard,shooting,own),EnergyType.METAL,EnergyType.METAL);UUID host=m.activePokemonView(A).pokemonId();
            m.useAbility(A,host,own.id());check(m.activePokemonView(A).damage()==10,"guard blocked own ability");if(disabled)m.disableAbilityEffectsByEffect(A,A,host);
            m.endTurn(A);m.useAbility(B,m.activePokemonView(B).pokemonId(),shooting.id(),host);check(m.activePokemonView(A).damage()==(disabled?30:10),"opponent ability guard boundary");
            m.placeDamageCountersByEffect(B,A,host,1);m.attack(B,"hit");check(m.activePokemonView(A).damage()==(disabled?80:60),"guard blocked rule counters or attack damage");
        }
    }
    static void judge(){
        var m=fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,trainer("sv1_en_176"),hit(40,false));var card=hand(m,"trainer");
        int a=m.privateView(A).hand().size()+m.privateView(A).deck().size(),b=m.privateView(B).hand().size()+m.privateView(B).deck().size();
        m.playTrainer(A,card.instanceId(),null);
        check(m.privateView(A).hand().size()==4&&m.privateView(B).hand().size()==4,"Judge did not draw four for both owners");
        check(m.privateView(A).discard().contains(card)&&m.privateView(A).deck().size()+4==a-1&&m.privateView(B).deck().size()+4==b,"Judge reshuffled itself or lost cards");
        var second=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("trainer")).findFirst();second.ifPresent(c->reject(()->m.playTrainer(A,c.instanceId(),null)));
    }
    static void recycler(){
        for(int count:List.of(1,3,5)){
            var m=fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS);var card=hand(m,"trainer");reject(()->m.playTrainer(A,card.instanceId(),null));
            var basics=m.privateView(A).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicEnergy()).limit(6).toList();check(basics.size()>=5,"recovery fixture lacks Energy");
            var special=hand(m,"special");var discard=new ArrayList<UUID>(basics.stream().map(CardInstance::instanceId).toList());discard.add(special.instanceId());m.discardHandCardsByEffect(A,A,discard);
            int deck=m.privateView(A).deck().size();m.playTrainer(A,card.instanceId(),null);var p=m.effectCardPrompt(A);
            check(p.minimum()==1&&p.maximum()==5&&p.candidates().size()==basics.size()&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicEnergy()),"Recycler filter or up-to bound incorrect");
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(special.instanceId())));
            var chosen=basics.subList(0,count).stream().map(CardInstance::instanceId).toList();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),chosen));m.submitEffectCards(A,p.resolutionId(),p.step(),chosen);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),chosen));
            check(!m.hasPendingEffectChoice()&&m.privateView(A).deck().size()==deck+count&&m.privateView(A).discard().contains(special)&&m.privateView(A).discard().contains(card),"Recycler moved wrong zone or card");
            check(m.privateView(A).deck().stream().filter(c->chosen.contains(c.instanceId())).count()==count,"Recycler failed to conserve selected cards");
        }
    }
}
