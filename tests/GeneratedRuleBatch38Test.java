import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real ME5-5 programs, evaluated against current public field and exact physical cards. */
public final class GeneratedRuleBatch38Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static UUID add(ServerMatch m,UUID p,String id){var c=hand(m,p,id);m.playBasicPokemonToBench(p,c.instanceId());return PokemonIdentityTestSupport.logical(m,p,c.instanceId());}
    static void cards(ServerMatch m,List<UUID> ids){GeneratedRuleBatch23Test.cards(m,ids);}
    static AttackDefinition attack(String id){return GeneratedRuleBatch35Test.attack(id,0);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){var c=hand(m,owner,id);m.attachEnergyByEffect(m.currentPlayer(),owner,EffectSourceZone.HAND,c.instanceId(),owner,host);}
    static CardInstance evolve(ServerMatch m,UUID p,UUID host,String id){var c=hand(m,p,id);m.evolveByEffect(p,host,c.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);return c;}
    public static void main(String[] args){types();maximumHp();recovery();devolution();schema();System.out.println("GeneratedRuleBatch38Test passed: distinct Basic Energy types across hosts, current maximum HP vs printed/remaining HP, one mixed discard recovery and whole-field single-stage devolution with protections and deferred KOs");}
    static void types(){for(int n:List.of(0,1,2,3)){
        var a=attack("me5-5_en_071");var m=fixture(a);UUID bench=add(m,A,"low");
        attach(m,A,active(m,A),"special");attach(m,B,active(m,B),"lightning");
        if(n>0){attach(m,A,active(m,A),"fire");attach(m,A,bench,"fire");}if(n>1)attach(m,A,bench,"water");if(n>2)attach(m,A,active(m,A),"lightning");
        m.attack(A,a.id());check(m.lastAttack().finalDamage()==n*50,"Distinct Basic types counted physical duplicates, Special Energy, opponent Energy, hand/deck or omitted Active");
    }}
    static void maximumHp(){for(String mode:List.of("plain","boosted","suppressed","evolved","moved")){
        var a=attack("me5-5_en_120");var m=fixture(a);UUID low=add(m,A,"low"),high=add(m,A,"high");m.placeDamageCountersByEffect(A,A,low,1);m.placeDamageCountersByEffect(A,A,high,7);
        m.endTurn(A);add(m,B,"low");m.endTurn(B);
        if(Set.of("boosted","suppressed").contains(mode)){m.playTrainer(A,hand(m,A,"tool").instanceId(),low);if(mode.equals("suppressed"))m.disableToolEffectsByEffect(A,A,low);}
        if(mode.equals("evolved"))evolve(m,A,low,"evolved");if(mode.equals("moved"))m.switchActiveByEffect(A,A,low);
        m.attack(A,a.id());check(m.lastAttack().finalDamage()==(Set.of("plain","suppressed").contains(mode)?30:0),"Maximum HP used printed HP, remaining HP, stale Tool modifier, covered Basic, Active or opponent");
    }}
    static void recovery(){for(int take:List.of(0,2,3)){
        var a=attack("me5-5_en_103");var m=fixture(a);var chosen=List.of(hand(m,A,"evolved"),hand(m,A,"fire"),hand(m,A,"low"));var special=hand(m,A,"special");var tool=hand(m,A,"tool");var discard=new ArrayList<>(chosen);discard.add(special);discard.add(tool);m.discardHandCardsByEffect(A,A,discard.stream().map(CardInstance::instanceId).toList());int deck=m.privateView(A).deck().size();
        m.attack(A,a.id());var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==3&&new HashSet<>(p.candidates()).equals(new HashSet<>(chosen)),"Mixed recovery must allow Pokemon of any stage and Basic Energy, excluding Special/Trainer");reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(special.instanceId())));
        var selected=chosen.subList(0,take);cards(m,selected.stream().map(CardInstance::instanceId).toList());check(m.privateView(A).deck().size()==deck+take&&m.privateView(A).deck().containsAll(selected)&&m.privateView(A).discard().contains(special)&&m.privateView(A).discard().contains(tool)&&!m.hasPendingEffectChoice(),"Mixed recovery lost physical card, destination or required second choice");
    }
        var a=attack("me5-5_en_103");var m=fixture(a);m.attack(A,a.id());check(!m.hasPendingEffectChoice(),"Empty discard recovery stalled");
    }
    static void devolution(){for(String mode:List.of("normal","guarded","knockout")){
        var a=attack("me5-5_en_069");var m=fixture(a,mode.equals("guarded"));UUID own=add(m,A,"low");evolve(m,A,own,"evolved");m.endTurn(A);UUID b1=add(m,B,"low"),b2=add(m,B,"low"),basic=add(m,B,"high");
        var e1=evolve(m,B,b1,"evolved");var e2=evolve(m,B,b2,"evolved");var top=evolve(m,B,b2,"stage2");m.switchActiveByEffect(B,B,b1);attach(m,B,b1,"water");m.placeDamageCountersByEffect(B,B,b1,mode.equals("knockout")?4:1);m.placeDamageCountersByEffect(B,B,b2,mode.equals("knockout")?22:2);
        m.endTurn(B);m.attack(A,a.id());
        check(m.privateView(B).hand().contains(top)&&!m.privateView(B).hand().contains(e2),"Stage2 must return only highest card, leaving Stage1 until KO");check(m.benchPokemonView(A).getFirst().topCardId().equals("evolved"),"Devolution touched own field");
        if(mode.equals("knockout")){check(m.privateView(B).hand().contains(e1)&&m.privateView(B).discard().contains(e2)&&m.privateView(A).prizes().size()==6,"Both targets must devolve before deferred KO/Prize choice");check(m.privateView(B).discard().stream().anyMatch(c->c.definitionId().equals("water")),"KO after devolution lost attached Energy");}
        else {check(m.activePokemonView(B).topCardId().equals(mode.equals("guarded")?"evolved":"low")&&m.activePokemonView(B).damage()==10&&m.activePokemonView(B).attachedEnergyIds().size()==1,"Active devolution protection/HP damage/attachment mismatch");check(m.benchPokemonView(B).stream().anyMatch(v->v.pokemonId().equals(b2)&&v.topCardId().equals("evolved")&&v.damage()==20)&&m.benchPokemonView(B).stream().anyMatch(v->v.pokemonId().equals(basic)&&v.topCardId().equals("high")),"Group did not devolve Bench or touched unevolved Basic");}
    }
        var a=attack("me5-5_en_069");var m=fixture(a);m.attack(A,a.id());check(m.activePokemonView(B).topCardId().equals("source")&&!m.hasPendingEffectChoice(),"Unevolved field must resolve with no effect");
    }
    static void schema(){
        reject(()->AttachedEnergyEffect.from(new EffectDefinition("discard_energy",EffectTarget.SELF,Map.of("amount","1","minimum","1","count_mode","DISTINCT_TYPES","card_kind","BASIC"),List.of(),List.of())));
        for(var args:List.of(Map.of("count_mode","DISTINCT_TYPES"),Map.of("count_mode","DISTINCT_TYPES","card_kind","SPECIAL"),Map.of("count_mode","DISTINCT_TYPES","card_kind","BASIC","energy_type","FIRE")))reject(()->EnergyCountQuery.from(args));
        for(String hp:List.of("0","-10","31","1010"))reject(()->PokemonCountEffect.from(new EffectDefinition("damage_per_pokemon",EffectTarget.OWN_BENCH,Map.of("amount","30","maximum_hp_equals",hp),List.of(),List.of())));
        for(EffectTarget target:List.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.OPPONENT_BENCH))reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.AFTER_ATTACK,"{\"type\":\"devolve\",\"target\":\""+target+"\"}"))),true,EffectTrigger.AFTER_ATTACK));
        reject(()->TrainerProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,"{\"type\":\"devolve\",\"target\":\"ALL_OPPONENT_POKEMON\"}")))));
    }
    static ServerMatch fixture(AttackDefinition attack){return fixture(attack,false);}
    static ServerMatch fixture(AttackDefinition attack,boolean guarded){
        var guard=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"guard","nameZh":"Guard","kind":"PASSIVE","effectSupport":"SUPPORTED","effects":[{"type":"passive_effect_guard","source":"ATTACK","from":"OPPONENT","scope":"EFFECTS_ONLY"}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        String[] ids={"source","low","low","high","evolved","evolved","stage2","fire","fire","water","lightning","special","tool","source","low"};
        var hp=Map.of("source",500,"low",30,"high",100,"evolved",200,"stage2",300);var energy=Map.of("fire",EnergyType.FIRE,"water",EnergyType.WATER,"lightning",EnergyType.LIGHTNING);
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=hp.containsKey(id),basic=pokemon&&!id.equals("evolved")&&!id.equals("stage2");defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("tool")?CardCategory.TRAINER:CardCategory.ENERGY,id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"J",Set.of("international"),basic,energy.containsKey(id),60,Map.of(),EffectSupport.SUPPORTED,null));
            if(pokemon)profiles.put(id,new PokemonProfile(hp.get(id),1,id.equals("evolved")?"low":id.equals("stage2")?"evolved":null,null,0,null,0,1,List.of(attack),guarded&&id.equals("evolved")?List.of(guard):List.of(),EnergyType.COLORLESS));
        }
        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"amount\":40}");var catalog=new CombatCatalog(profiles,energy,Map.of("tool",tool),Map.of("special",EnergyProfile.fixed(EnergyType.PSYCHIC,2)));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("source"))))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,"source").instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,36);m.drawCardsByEffect(A,B,36);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id)))&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("evolved")).count()>=2&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("low")).count()>=2))return m;
        }throw new AssertionError("No batch38 fixture");
    }
}
