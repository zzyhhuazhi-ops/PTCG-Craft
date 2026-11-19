import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic component contracts; explicit zone mutations are fixture setup, never a claimed card effect. */
public final class DeckDiscardTriggeredAbilityTest {
    static final UUID A=new UUID(0,9501),B=new UUID(0,9502),WATCH=new UUID(0,9503);
    static final String MILL="{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}";
    static final String CHOICE="{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}";
    static final String POLICY="{\"trigger\":\"ON_DISCARDED_FROM_DECK\",\"turn\":\"OPPONENT\",\"cause\":\"OPPONENT_POKEMON_ATTACK_ABILITY_OR_ITEM_SUPPORTER\",\"optional\":false,\"usage\":\"NONE\"}";
    static final String REACTION="{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":8}";
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void reject(Runnable r){try{r.run();}catch(IllegalStateException|IllegalArgumentException expected){return;}throw new AssertionError("Invalid operation accepted");}
    static AbilityDefinition reaction(String policy,String effects){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"reactor\",\"nameZh\":\"弃牌反应\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":"+policy+",\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static AbilityDefinition active(String effects){return ResumableAbilityTest.ability("produce",effects);}
    static AttackDefinition attack(int damage,String effects){if(effects.isEmpty())return DynamicPassiveModifierTest.attack(damage,false,false);return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"来源招式\",\"energyCost\":{},\"baseDamage\":"+damage+",\"damageType\":\"METAL\",\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+effects+"]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static TrainerProfile trainer(String category,String effects){return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\""+category+"\",\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static UUID field(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static int deck(ServerMatch m,UUID p){return m.privateView(p).deck().size();}
    static long reactions(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().startsWith("结算触发特性：reactor；")).count();}
    @SuppressWarnings("unchecked") static List<CardInstance> zone(ServerMatch m,UUID owner,String name){
        try{var f=ServerMatch.class.getDeclaredField("players");f.setAccessible(true);Object state=((Map<UUID,?>)f.get(m)).get(owner);var z=state.getClass().getDeclaredField(name);z.setAccessible(true);return (List<CardInstance>)z.get(state);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    /** Top is the last element. Choose existing physical cards, without creating or duplicating one. */
    static List<CardInstance> top(ServerMatch m,UUID owner,String... ids){
        var list=zone(m,owner,"deck");var selected=new ArrayList<CardInstance>();
        for(String id:ids){var card=list.stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();list.remove(card);selected.add(card);}
        for(int i=selected.size()-1;i>=0;i--)list.add(selected.get(i));return List.copyOf(selected);
    }
    static ServerMatch fixture(AttackDefinition attack,List<AbilityDefinition> abilities,TrainerProfile item){return fixture(attack,abilities,item,DynamicPassiveModifierTest.plainTool());}
    static ServerMatch fixture(AttackDefinition attack,List<AbilityDefinition> abilities,TrainerProfile item,TrainerProfile tool){
        TrainerCategory cat=item==null?TrainerCategory.ITEM:item.category();
        var defs=new ArrayList<CardDefinition>();
        for(String id:List.of("host","reactor","item","water","grass","tool")){
            boolean p=id.equals("host")||id.equals("reactor"),e=id.equals("water")||id.equals("grass");
            defs.add(new CardDefinition(id,id,p?CardCategory.POKEMON:e?CardCategory.ENERGY:CardCategory.TRAINER,p||e?TrainerCategory.NONE:id.equals("tool")?TrainerCategory.TOOL:cat,"H",Set.of("international"),id.equals("host"),e,60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        var combat=new CombatCatalog(Map.of("host",new PokemonProfile(300,0,null,null,0,null,0,1,List.of(attack),abilities),
            "reactor",new PokemonProfile(130,0,"host",null,0,null,0,1,List.of(),List.of(reaction(POLICY,REACTION)))),
            Map.of("water",EnergyType.WATER,"grass",EnergyType.GRASS),item==null?Map.of("tool",tool):Map.of("item",item,"tool",tool));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i<18?"host":i<26?"reactor":i<34?"item":i<47?"water":i<56?"grass":"tool"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("host")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);if(m.currentPlayer().equals(B))m.endTurn(B);
            m.endTurn(A);m.endTurn(B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("item")))continue;
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).deck().stream().filter(c->c.definitionId().equals("reactor")).count()<3))continue;
            return m;
        }throw new AssertionError("Fixture unavailable");
    }
    static ServerMatch fixture(String effects){return fixture(attack(0,""),List.of(active(effects)),null);}
    static void choose(ServerMatch m){var p=m.effectChoicePrompt(A);check(p!=null,"Missing original suffix choice");var before=m.clientView(A);var rng=m.randomTranscript();reject(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),0));reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),99));check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Unauthorized/stale choice mutated state/RNG");m.chooseEffectOption(A,p.resolutionId(),p.step(),0);reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),0));}
    static void drain(ServerMatch m){int cap=20;while(m.triggerOrderPrompt()!=null&&cap-->0){var p=m.triggerOrderPrompt();check(!p.optionalDecision(),"Mandatory trigger became optional");m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());}check(cap>0,"Reaction recursion");}
    static void strictContracts(){
        var a=reaction(POLICY,REACTION);TriggeredAbilityRules.validate(a);
        reject(()->new CardTriggerDefinition("forged","非宝可梦入口",a.effectProgram(),a.eventPolicy(),null));
        reject(()->active(MILL.replace("1}","1,\"as_cost\":true}"))); // Unsupported cost is rejected at data parsing, before any match exists.
        for(String p:List.of(POLICY.replace("false","true"),POLICY.replace("\"NONE\"","\"ONCE_PER_TURN\""),POLICY.replace("\"OPPONENT\"","\"ANY\""),POLICY.replace("false","\"false\""),POLICY.replace("}",",\"watch\":\"SELF\"}")))reject(()->reaction(p,REACTION));
        for(String effects:List.of(REACTION.replace("OPPONENT_ACTIVE","SELF"),REACTION.replace("8}","8,\"as_cost\":true}"),REACTION.replace("8}","8,\"bind\":\"x\"}"),REACTION+","+REACTION,"{\"type\":\"draw\",\"amount\":1}"))reject(()->TriggeredAbilityRules.validate(reaction(POLICY,effects)));
        var policy=(DeckDiscardTriggerPolicy)a.eventPolicy();
        for(var kind:DeckDiscardTriggerPolicy.SourceKind.values())check(policy.matches(B,A,true,A,kind),"Approved cause rejected "+kind);
        check(!policy.matches(B,A,false,A,DeckDiscardTriggerPolicy.SourceKind.POKEMON_ATTACK)&&!policy.matches(A,A,true,A,DeckDiscardTriggerPolicy.SourceKind.ITEM)&&!policy.matches(B,A,true,B,DeckDiscardTriggerPolicy.SourceKind.POKEMON_ABILITY)&&!policy.matches(B,A,true,A,null),"Turn/controller/cause negative escaped");
        reject(()->trainer("ITEM","{\"type\":\"draw\",\"amount\":1}".replace("draw","nonexistent")));
        var m=fixture(MILL);var snapshot=m.clientView(A);var rng=m.randomTranscript();
        reject(()->m.executeEffectProgramByRule(A,field(m,A),a.effectProgram(),EffectTrigger.ON_DISCARDED_FROM_DECK));
        check(snapshot.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Forged trigger mutated state");
    }
    static void loadedSourcesAndRuleNegative(){
        for(String category:List.of("ITEM","SUPPORTER")){
            var m=fixture(attack(0,""),List.of(),trainer(category,MILL));top(m,B,"reactor");int before=deck(m,A);var card=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("item")).findFirst().orElseThrow();m.playTrainer(A,card.instanceId(),null);drain(m);check(deck(m,A)==before-8&&reactions(m)==1&&m.privateView(A).discard().contains(card),"Loaded "+category+" provenance failed");
        }
        var m=fixture(MILL);top(m,B,"reactor");int before=deck(m,A);m.useAbility(A,field(m,A),"produce");drain(m);check(deck(m,A)==before-8&&reactions(m)==1,"Loaded Ability provenance failed");
        var attack=fixture(attack(0,MILL),List.of(),null);top(attack,B,"reactor");before=deck(attack,A);attack.attack(A,"hit");drain(attack);check(deck(attack,A)==before-8&&reactions(attack)==1&&attack.currentPlayer().equals(B),"Loaded attack provenance failed");
        for(boolean generic:List.of(false,true)){
            var rule=fixture(MILL);top(rule,B,"reactor");before=deck(rule,A);
            if(generic)rule.executeEffectProgramByRule(A,field(rule,A),active(MILL).effectProgram(),EffectTrigger.ONCE_PER_TURN);else rule.discardTopDeckCardsByEffect(A,B,1);
            check(deck(rule,A)==before&&reactions(rule)==0,"Public rule hook impersonated loaded source");
        }
        var own=fixture(MILL.replace("OPPONENT_ACTIVE","SELF"));top(own,A,"reactor");before=deck(own,B);own.useAbility(A,field(own,A),"produce");check(deck(own,B)==before&&reactions(own)==0,"Own discard triggered");
    }
    static void suffixAndLastKnown(){
        for(String destination:List.of("hand","deck")){
            var m=fixture(MILL+","+CHOICE);var card=top(m,B,"reactor").getFirst();int before=deck(m,A);m.useAbility(A,field(m,A),"produce");
            check(deck(m,A)==before&&reactions(m)==0&&m.privateView(B).discard().contains(card),"Reaction interrupted original text");
            // A test-only movement isolates captured last-known semantics; current supported cards cannot recover the opponent's discard here.
            check(zone(m,B,"discard").remove(card),"Missing captured physical source");zone(m,B,destination).add(card);
            for(UUID viewer:List.of(A,WATCH)){String json=new Gson().toJson(m.clientView(viewer.equals(A)?A:null));check(!json.contains(card.instanceId().toString()),"Hidden captured source UUID leaked");}
            choose(m);drain(m);check(deck(m,A)==before-9&&reactions(m)==1,"Recovered source canceled/duplicated captured reaction");
            check(m.replayTranscript().stream().noneMatch(e->e.operation().contains(card.instanceId().toString())),"Captured source UUID leaked into replay");
        }
    }
    static void emptyAtCaptureAndMultiple(){
        var m=fixture(MILL+",{\"type\":\"shuffle_hand\",\"target\":\"SELF\"}");m.drawCardsByEffect(A,A,deck(m,A));top(m,B,"reactor");int hand=m.privateView(A).hand().size();m.useAbility(A,field(m,A),"produce");drain(m);check(deck(m,A)==hand-8&&reactions(m)==1,"Empty target at capture dropped mandatory reaction before original shuffle suffix");
        var multi=fixture(MILL.replace("1}","3}")+","+CHOICE);var cards=top(multi,B,"reactor","reactor","reactor");int before=deck(multi,A);multi.useAbility(A,field(multi,A),"produce");choose(multi);var p=multi.triggerOrderPrompt();check(p!=null&&p.trigger()==EffectTrigger.ON_DISCARDED_FROM_DECK&&p.entries().size()==3,"Physical copies not independent");
        String json=new Gson().toJson(p);for(var card:cards)check(!json.contains(card.instanceId().toString()),"Trigger prompt leaked physical UUID");var rng=multi.randomTranscript();reject(()->multi.chooseTriggeredEffect(B,p.id(),p.entries().getFirst().id()));check(rng.equals(multi.randomTranscript()),"Wrong sorter consumed randomness");drain(multi);check(deck(multi,A)==before-1-24&&reactions(multi)==3,"Copies merged, duplicated, or recursively counter-milled");
        var shortDeck=fixture(MILL);top(shortDeck,B,"reactor");shortDeck.drawCardsByEffect(A,A,deck(shortDeck,A)-3);shortDeck.useAbility(A,field(shortDeck,A),"produce");check(deck(shortDeck,A)==0&&reactions(shortDeck)==1&&shortDeck.phase()==MatchPhase.TURN,"Short deck did not mill up to eight without immediate deck-out");
    }
    static void directAndDamageKnockouts(){
        for(String ko:List.of("{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}")){
            var m=fixture(MILL+","+ko+","+CHOICE);top(m,B,"reactor");int before=deck(m,A);UUID victim=field(m,B);m.useAbility(A,field(m,A),"produce");check(field(m,B).equals(victim)&&m.phase()==MatchPhase.TURN&&deck(m,A)==before,"KO committed inside original program");choose(m);drain(m);check(deck(m,A)==before-9&&reactions(m)==1&&m.phase()==MatchPhase.PRIZE_SELECTION&&m.publicView().get(B).activeCardId()==null,"Nonattack deferred direct/damage KO lost or preempted reaction");
        }
        var lethal=fixture(attack(300,MILL+","+CHOICE),List.of(),null);top(lethal,B,"reactor");int before=deck(lethal,A);lethal.attack(A,"hit");check(lethal.phase()==MatchPhase.TURN&&lethal.activePokemonView(B).damage()==300,"Attack KO committed before suffix");choose(lethal);drain(lethal);check(reactions(lethal)==1&&deck(lethal,A)==before-9&&lethal.phase()==MatchPhase.PRIZE_SELECTION,"Attack checkpoint skipped deck reaction");
        // Current player A's doomed host produces a loaded automatic Ability, then B's discarded card must resolve before the KO commit.
        var onKO=KnockoutTriggeredAbilityTest.ability("ko-producer","ANY",true,false,MILL);
        var producer=active("{\"type\":\"knock_out\",\"target\":\"SELF\"}");var nested=fixture(attack(0,""),List.of(producer,onKO),null);top(nested,B,"reactor");before=deck(nested,A);nested.useAbility(A,field(nested,A),"produce");drain(nested);check(reactions(nested)==1&&deck(nested,A)==before-8&&nested.phase()==MatchPhase.PRIZE_SELECTION&&nested.publicView().get(A).activeCardId()==null,"ON_KNOCKOUT second gate skipped captured reaction");
    }
    static void toolCorrectionAndFilteredSlice(){
        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":[{\"type\":\"DAMAGE_COUNTER_COUNT\",\"value\":1,\"comparison\":\">=\"}]}");
        var original=active(MILL+",{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"},{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10},"+CHOICE);
        var m=fixture(attack(0,""),List.of(original),null,tool);UUID host=field(m,A);m.placeDamageCountersByEffect(A,A,host,1);
        var extra=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("tool")).limit(2).map(CardInstance::instanceId).toList();m.searchOwnDeckToHandByEffect(A,extra);
        for(var c:m.privateView(A).hand().stream().filter(x->x.definitionId().equals("tool")).limit(2).toList())m.playTrainer(A,c.instanceId(),host);
        check(m.activePokemonView(A).attachedTools().size()==2,"Tool fixture lacks two attachments");top(m,B,"reactor");int before=deck(m,A);m.useAbility(A,host,"produce");
        var overflow=m.toolOverflowPrompt();check(overflow!=null&&m.phase()==MatchPhase.TURN&&reactions(m)==0&&deck(m,A)==before,"Tool correction did not suspend original program before reaction/KO");
        reject(()->m.discardOverflowTool(B,overflow.id(),overflow.tools().getFirst().instanceId()));m.discardOverflowTool(A,overflow.id(),overflow.tools().getFirst().instanceId());
        check(m.effectChoicePrompt(A)!=null&&reactions(m)==0,"Tool correction lost original suffix");choose(m);drain(m);check(m.phase()==MatchPhase.PRIZE_SELECTION&&deck(m,A)==before-9&&reactions(m)==1&&m.activePokemonView(A).attachedTools().size()==1,"Tool and deck checkpoints lost directKO, duplicated reaction or skipped suffix");
        var counted=BundledCombatData.attack(JsonParser.parseString("""
          {"id":"hit","nameZh":"filtered slice","energyCost":{},"baseDamage":0,"damageType":"METAL","effectSupport":"SUPPORTED","triggers":[
          {"trigger":"BEFORE_ATTACK","effects":[{"type":"discard_top_deck","target":"OPPONENT_ACTIVE","amount":2,"bind":"milled","card_category":"ENERGY","basic_energy_only":true,"energy_type":"WATER"}]},
          {"trigger":"ON_ATTACK","effects":[{"type":"damage_per_bound","target":"OPPONENT_ACTIVE","bind":"milled","amount":20}]}]}
          """).getAsJsonObject(),EffectRegistry.standard());
        var count=fixture(counted,List.of(),null);top(count,B,"water");count.discardTopDeckCardsByEffect(A,B,1);var moved=top(count,B,"reactor","water","grass");before=deck(count,A);count.attack(A,"hit");drain(count);
        check(count.lastAttack().finalDamage()==20&&deck(count,A)==before-8&&reactions(count)==1,"Captured reaction polluted original moved-card filter/bind");
        check(count.privateView(B).discard().containsAll(moved.subList(0,2))&&!count.privateView(B).discard().contains(moved.getLast()),"Captured event moved the filter's excluded next card");
    }
    static void finishDropsPending(){var m=fixture(MILL+","+CHOICE);top(m,B,"reactor");int before=deck(m,A);m.useAbility(A,field(m,A),"produce");var prompt=m.effectChoicePrompt(A);m.concede(B);check(m.phase()==MatchPhase.FINISHED&&deck(m,A)==before&&reactions(m)==0&&!m.hasPendingEffectChoice()&&m.triggerOrderPrompt()==null,"Finish retained pending reaction");reject(()->m.chooseEffectOption(A,prompt.resolutionId(),prompt.step(),0));}
    public static void main(String[] args){strictContracts();loadedSourcesAndRuleNegative();suffixAndLastKnown();emptyAtCaptureAndMultiple();directAndDamageKnockouts();toolCorrectionAndFilteredSlice();finishDropsPending();System.out.println("DeckDiscardTriggeredAbilityTest passed: strict physical-event contract, loaded provenance/public-hook negatives, complete suffix, empty/short deck, independent copies, last-known/privacy and both pre-KO gates");}
}
