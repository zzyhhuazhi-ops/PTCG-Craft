import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual generated programs; neutral, free synthetic hosts isolate printed effect combinations. */
public final class GeneratedRuleBatch54Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B,WATCH=new UUID(0,5400);
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AttackDefinition attack(String card,int slot){return GeneratedRuleBatch6Test.attack(card,slot);}
    static AbilityDefinition ability(String card){return GeneratedRuleBatch8Test.ability(card);}
    static AttackDefinition hit(){return GeneratedRuleBatch8Test.hit(40,false);}
    static void discardHand(ServerMatch m,UUID p){m.discardHandCardsByEffect(A,p,m.privateView(p).hand().stream().map(CardInstance::instanceId).toList());}
    static void cards(ServerMatch m,List<UUID> selected){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),selected);}
    static void target(ServerMatch m,UUID selected){var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),selected);}
    static ServerMatch game(AttackDefinition attack,AbilityDefinition... abilities){return game(attack,0,List.of(abilities));}
    static ServerMatch game(AttackDefinition attack,int firstSeed,List<AbilityDefinition> abilities){
        String[] ids={"basic","basic","basic","basic","energy","energy","wrong","special","tool","supporter","item","evolved","evolved","evolved","lookalike"};
        var definitions=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","lookalike").contains(id),energy=Set.of("energy","wrong","special").contains(id);
            definitions.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("tool")?TrainerCategory.TOOL:id.equals("supporter")?TrainerCategory.SUPPORTER:id.equals("item")?TrainerCategory.ITEM:TrainerCategory.NONE,
                "J",Set.of("international"),id.equals("basic"),id.equals("energy")||id.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null));
            if(pokemon){String name=id.equals("evolved")?"Lampent":id.equals("lookalike")?"Team Rocket's Lampent":"Neutral host";
                profiles.put(id,new PokemonProfile(500,1,id.equals("basic")?null:"basic",null,0,null,0,1,List.of(attack),abilities,EnergyType.COLORLESS,name,id.equals("basic")?null:"Neutral host"));}
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS,"wrong",EnergyType.WATER),
            Map.of("tool",DynamicPassiveModifierTest.plainTool(),"supporter",GeneratedRuleBatch8Test.trainer("xy2_en_093"),"item",GeneratedRuleBatch8Test.trainer("swsh5_en_124")),
            Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        for(int seed=firstSeed;seed<firstSeed+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(definitions),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))
                &&m.privateView(A).deck().stream().filter(c->c.definitionId().equals("evolved")).count()>=3)return m;
        }throw new AssertionError("No batch54 fixture");
    }
    public static void main(String[] args){printedFacts();counters();discardAny();lampent();evolutionHealing();coinEnergy();emptyHandSearch();guards();confusedGate();
        System.out.println("GeneratedRuleBatch54Test passed: actual ME5 programs/printed facts, Active and Bench counters, optional private hand discard, named Evolution Bench search/capacity/conservation, hand-only optional healing and repeat legal evolution events, once-turn Active coin Energy discard/ownership/replay, empty-hand filtered/revealed Supporter search, live attack and Ability guards preserving ordinary damage/own effects, Confused defender damage gate");}
    static void printedFacts(){
        var definitions=BundledRules.loadCardDefinitions();
        for(String id:List.of("me5_en_005","me5_en_020","me5_en_029","me5_en_033","me5_en_034","me5_en_037","me5_en_041","me5_en_045","me5_en_052","me5_en_070","me5_en_088","me5_en_095","me5_en_100","me3_en_049","sm12_en_154"))
            check(definitions.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Actual definition lacks full audited support: "+id);
        var lamp=GeneratedRuleBatch8Test.entry("me5_en_037");check(lamp.get("hitPoints").getAsInt()==90&&lamp.get("evolvesFromDefinitionId").getAsString().equals("me5_en_036"),"Lampent printed evolution/HP changed");
        var ghost=GeneratedRuleBatch8Test.entry("me5_en_041").getAsJsonArray("attacks").get(0).getAsJsonObject();check(ghost.get("baseDamage").getAsInt()==100&&!ghost.getAsJsonObject("energyCost").entrySet().isEmpty(),"Ghostly Blow lost printed damage/cost");
        var brain=GeneratedRuleBatch8Test.entry("me5_en_052").getAsJsonArray("attacks").get(1).getAsJsonObject();check(brain.get("baseDamage").getAsInt()==0&&brain.getAsJsonArray("effects").get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().get("amount").getAsInt()==130,"Brain Crush gate lost printed130");
    }
    static void counters(){
        var a=attack("me5_en_005",0);var m=game(a);m.attack(A,a.id());check(m.activePokemonView(B).damage()==10&&m.benchPokemonView(B).getFirst().damage()==0&&m.lastAttack().finalDamage()==0,"Furtive Drop became damage or wrong target");
        var h=attack("me3_en_049",0);var older=game(h);older.attack(A,h.id());check(older.activePokemonView(B).damage()==30,"Haunter printed counter quantity differs");
        var g=attack("me5_en_041",0);var n=game(g);n.attack(A,g.id());check(n.activePokemonView(B).damage()==100&&n.benchPokemonView(B).getFirst().damage()==0&&n.currentPlayer().equals(A),"Ghostly Blow Active damage timing");
        var p=n.effectPokemonPrompt(A);check(p.targets().stream().allMatch(t->t.owner().equals(B)&&!t.pokemonId().equals(active(n,B))),"Bench effect offered Active/own field");
        atomic(n,()->n.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(n,B)));atomic(n,()->n.chooseEffectPokemon(B,p.resolutionId(),p.step(),bench(n,B)));target(n,bench(n,B));
        check(n.benchPokemonView(B).getFirst().damage()==50&&n.currentPlayer().equals(B),"Bench counters did not finish attack");atomic(n,()->n.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench(n,B)));
        var empty=game(g);empty.movePokemonByEffect(A,B,bench(empty,B),LeavePlayDestination.HAND);empty.attack(A,g.id());check(!empty.hasPendingEffectChoice()&&empty.lastAttack().finalDamage()==100&&empty.currentPlayer().equals(B),"Empty Bench stalled attack");
    }
    static void discardAny(){for(int count:List.of(0,1,-1)){
        var a=attack("me5_en_029",0);var m=game(a);var before=m.privateView(A);int rng=m.randomTranscript().size();m.attack(A,a.id());var p=m.effectCardPrompt(A);
        check(p.minimum()==0&&p.maximum()==60&&new HashSet<>(p.candidates()).equals(new HashSet<>(before.hand())),"Any-hand discard narrowed categories/minimum");
        var selected=p.candidates().stream().limit(count<0?p.candidates().size():count).toList();if(!selected.isEmpty()){UUID c=selected.getFirst().instanceId();atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(c,c)));}
        atomic(m,()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of()));var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
        for(UUID viewer:List.of(B,WATCH)){String view=new Gson().toJson(session.view(viewer,viewer.equals(B)?B:null));for(var c:p.candidates())check(!view.contains(c.instanceId().toString()),"Private hand choice leaked");}
        cards(m,selected.stream().map(CardInstance::instanceId).toList());check(m.privateView(A).hand().size()==before.hand().size()-selected.size()&&m.privateView(A).discard().containsAll(selected)&&m.privateView(A).deck().equals(before.deck())&&m.randomTranscript().size()==rng&&m.currentPlayer().equals(B),"Optional discard mutated wrong zone/RNG");
        atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
    }
        var a=attack("me5_en_029",0);var empty=game(a);discardHand(empty,A);empty.attack(A,a.id());check(!empty.hasPendingEffectChoice()&&empty.currentPlayer().equals(B),"Empty hand stalled discard");
    }
    static void lampent(){for(int count:List.of(0,1,3)){
        var a=attack("me5_en_037",0);var m=game(a);var before=m.privateView(A);int benchCount=m.benchPokemonView(A).size(),rng=m.randomTranscript().size();m.attack(A,a.id());var p=m.effectCardPrompt(A);
        check(p.minimum()==0&&p.maximum()==3&&p.candidates().stream().allMatch(c->c.definitionId().equals("evolved")),"Named Evolution search wrong name/stage");
        var wrong=before.deck().stream().filter(c->!c.definitionId().equals("evolved")).findFirst().orElseThrow();atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(wrong.instanceId())));
        var chosen=p.candidates().subList(0,count);cards(m,chosen.stream().map(CardInstance::instanceId).toList());check(m.benchPokemonView(A).size()==benchCount+count&&m.privateView(A).deck().size()==before.deck().size()-count&&m.privateView(A).hand().equals(before.hand())&&m.currentPlayer().equals(B),"Lampent moved through Hand/wrong count");
        for(var c:chosen){var v=m.benchPokemonView(A).stream().filter(x->PokemonIdentityTestSupport.logical(m,A,c.instanceId()).equals(x.pokemonId())).findFirst().orElseThrow();check(v.topCardId().equals("evolved")&&v.evolutionCardIds().equals(List.of("evolved"))&&v.damage()==0&&v.attachedEnergyIds().isEmpty(),"Named Evolution did not enter as its own stack");}
        check(m.randomTranscript().size()-rng==Math.max(0,m.privateView(A).deck().size()-1),"Named inspected search not shuffled exactly once");atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
    }
        var a=attack("me5_en_037",0);var m=game(a);while(m.benchPokemonView(A).size()<4)m.playBasicPokemonToBench(A,hand(m,A,"basic").instanceId());m.attack(A,a.id());var p=m.effectCardPrompt(A);check(p.maximum()==1,"Bench capacity not applied");atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(2).map(CardInstance::instanceId).toList()));cards(m,List.of(p.candidates().getFirst().instanceId()));check(m.benchPokemonView(A).size()==5,"Last slot missed");m.endTurn(B);m.attack(A,a.id());check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Full Bench stalled search");
    }
    static void event(ServerMatch m,boolean use){var p=m.triggerOrderPrompt();check(p!=null&&p.trigger()==EffectTrigger.ON_EVOLVE,"Missing evolution event");if(!p.optionalDecision()){m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());p=m.triggerOrderPrompt();}check(p.optionalDecision()&&p.choosingPlayer().equals(A),"Hand evolution mandatory/foreign-owned");var decision=p;atomic(m,()->m.decideTriggeredEffect(B,decision.id(),use));m.decideTriggeredEffect(A,p.id(),use);atomic(m,()->m.decideTriggeredEffect(A,decision.id(),use));}
    static void evolutionHealing(){for(String id:List.of("me5_en_020","me5_en_088"))for(boolean use:List.of(false,true)){
        var ab=ability(id);var m=game(hit(),ab);check(m.triggerOrderPrompt()==null,"Opening triggered healing");UUID host=active(m,A),other=bench(m,A);m.placeDamageCountersByEffect(A,A,host,4);m.placeDamageCountersByEffect(A,A,other,7);m.evolve(A,host,hand(m,A,"evolved").instanceId());event(m,use);
        if(use){var p=m.effectPokemonPrompt(A);atomic(m,()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,B)));target(m,other);}check(m.activePokemonView(A).damage()==40&&m.benchPokemonView(A).getFirst().damage()==(use?0:70)&&!m.hasPendingEffectChoice(),"Optional healing wrong target/amount");
        if(use){m.devolvePokemonToHandByEffect(A,A,host);m.placeDamageCountersByEffect(A,A,other,2);m.evolveByEffect(A,host,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);event(m,true);target(m,other);check(m.benchPokemonView(A).getFirst().damage()==0,"Separate legal hand evolution limited across turn");}
        var grow=ResumableAbilityTest.ability("grow",DeckEvolutionEffectTest.EVOLVE);var n=game(hit(),ab,grow);n.placeDamageCountersByEffect(A,A,bench(n,A),2);n.useAbility(A,active(n,A),grow.id());cards(n,List.of(n.effectCardPrompt(A).candidates().getFirst().instanceId()));check(n.triggerOrderPrompt()==null&&n.benchPokemonView(A).getFirst().damage()==20,"Deck evolution triggered hand-only heal");
    }}
    static void coinEnergy(){for(String id:List.of("me5_en_045","me5_en_100")){
        var ab=ability(id);var outcomes=new HashSet<Boolean>();for(int seed=0;seed<30&&outcomes.size()<2;seed++){
            var m=game(hit(),seed,List.of(ab));UUID source=active(m,A);check(!m.canUseAbility(A,bench(m,A),ab.id()),"Active ability offered on Bench");atomic(m,()->m.useAbility(A,bench(m,A),ab.id()));
            var basic=hand(m,B,"energy");var special=hand(m,B,"special");for(var c:List.of(basic,special))m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,c.instanceId(),B,active(m,B));
            int rng=m.randomTranscript().size();m.useAbility(A,source,ab.id());var rolls=m.randomTranscript().subList(rng,m.randomTranscript().size());check(rolls.size()==1,"Ability coin repeated/extra RNG");boolean heads=rolls.getFirst().result()==0;outcomes.add(heads);
            if(heads){var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&Set.copyOf(p.candidates()).equals(Set.of(basic,special)),"Energy discard counted units/omitted Special");atomic(m,()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(special.instanceId())));atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(hand(m,A,"energy").instanceId())));cards(m,List.of(special.instanceId()));check(m.privateView(B).discard().contains(special)&&m.activePokemonView(B).attachedEnergyIds().equals(List.of(basic.instanceId())),"Energy physical owner/card discard wrong");}
            else check(!m.hasPendingEffectChoice()&&m.activePokemonView(B).attachedEnergyIds().size()==2,"Tails discarded/stalled");
            check(m.randomTranscript().size()==rng+1&&!m.canUseAbility(A,source,ab.id()),"Choice rerolled/usage not consumed");atomic(m,()->m.useAbility(A,source,ab.id()));m.endTurn(A);m.endTurn(B);check(m.canUseAbility(A,source,ab.id()),"Next-turn usage not refreshed");
        }check(outcomes.size()==2,"Missing coin outcome");
    }}
    static void emptyHandSearch(){for(String id:List.of("me5_en_070","me5_en_095")){
        var ab=ability(id);var m=game(hit(),ab);UUID source=bench(m,A);check(!m.canUseAbility(A,source,ab.id()),"Nonempty hand permitted search");atomic(m,()->m.useAbility(A,source,ab.id()));discardHand(m,A);m.useAbility(A,source,ab.id());var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("supporter")),"Supporter filter/failure policy");var wanted=p.candidates().getFirst();cards(m,List.of(wanted.instanceId()));check(m.effectCardReveal()!=null&&m.privateView(A).hand().isEmpty(),"Supporter did not pause for reveal before transfer");m.acknowledgeEffectReveal(B,m.effectCardReveal().id());check(m.privateView(A).hand().equals(List.of(wanted)),"Revealed Supporter not transferred");discardHand(m,A);check(!m.canUseAbility(A,source,ab.id()),"Emptying hand refreshed usage");
        var n=game(hit(),ab);discardHand(n,A);n.useAbility(A,active(n,A),ab.id());var before=n.privateView(A);int rng=n.randomTranscript().size();cards(n,List.of());check(n.privateView(A).hand().isEmpty()&&new HashSet<>(n.privateView(A).deck()).equals(new HashSet<>(before.deck()))&&n.randomTranscript().size()-rng==before.deck().size()-1&&!n.hasPendingEffectChoice(),"Failed filtered search not shuffled/finished");
    }}
    static void guards(){for(String id:List.of("me5_en_005","me5_en_006","me5_en_033","me5_en_034")){
        var guard=ability(id);var a=BeforeDamageAttackTest.attack("","","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2},{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"}");var m=game(a,guard);m.attack(A,a.id());check(m.activePokemonView(B).damage()==40&&m.activePokemonView(B).conditions().isEmpty(),"Guard prevented damage/allowed attack effects");
        var counter=ResumableAbilityTest.ability("counter","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2},{\"type\":\"draw\",\"amount\":1}");var n=game(hit(),guard,counter);int h=n.privateView(A).hand().size();n.useAbility(A,active(n,A),counter.id());check(n.activePokemonView(B).damage()==0&&n.privateView(A).hand().size()==h+1,"Ability guard swallowed actor draw/allowed counters");
        n.placeDamageCountersByEffect(A,B,active(n,B),1);check(n.activePokemonView(B).damage()==10,"Rule counters mislabelled Ability");n.disableAbilityEffectsByEffect(A,B,active(n,B));n.useAbility(A,bench(n,A),counter.id());check(n.activePokemonView(B).damage()==30,"Suppressed guard remained active");
        var own=ResumableAbilityTest.ability("own","{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":2}");var o=game(hit(),guard,own);o.useAbility(A,active(o,A),own.id());check(o.activePokemonView(A).damage()==20,"Guard blocked own Ability");
    }}
    static void confusedGate(){for(String id:List.of("me5_en_052","sm12_en_154")){var a=attack(id,1);int printed=GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks").get(1).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().get("amount").getAsInt();for(String status:List.of("none","confused","poisoned","own-confused")){
        var m=game(a);if(status.equals("confused"))m.applySpecialConditionByEffect(A,B,SpecialCondition.CONFUSED);else if(status.equals("poisoned"))m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);else if(status.equals("own-confused"))m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);
        m.attack(A,a.id());check(m.lastAttack().finalDamage()==(status.equals("confused")?printed:0),"Confusion gate wrong status/owner/ungated base: "+id);
    }}}
}
