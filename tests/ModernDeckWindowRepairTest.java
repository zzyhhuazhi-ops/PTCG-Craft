import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual bundled programs; controlled boards exercise private windows and server continuations. */
public final class ModernDeckWindowRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable action){CopyAttackTest.reject(action);}
    static CardInstance hand(ServerMatch m,String id){return ModernRuleRepair2Test.hand(m,A,id);}
    public static void main(String[] args){
        for(String id:List.of("sv3-5_en_156","sv3-5_en_194","sv6_en_143","sv8-5_en_102","sv6_en_151","sv6_en_205","sv8-5_en_127"))
            for(int mode:List.of(0,1,2))trainer(id,mode);
        for(String id:List.of("sv6_en_131","sv6_en_186","svbsp_en_118"))for(int mode:List.of(0,1,2))ability(id,mode);
        thievul();
        for(String id:List.of("sv4_en_172","sv4_en_241","sv4_en_258","sv5_en_147","sv5_en_200","sv8-5_en_107"))
            for(int size:List.of(1,2,3,8))fixedWindow(id,size);
        for(int size:List.of(1,2,8))for(int branch:List.of(0,1))deduction(size,branch);
        System.out.println("ModernDeckWindowRepairTest passed: 10 printed top-window searches, eligibility, private/revealed cards, zero/max selection, short deck, shuffle, stale submits, Thievul evolution");
    }
    static ServerMatch game(TrainerProfile trainer,List<AbilityDefinition> abilities){
        return GeneratedRuleBatch8Test.fixture(abilities,EnergyType.GRASS,EnergyType.GRASS,trainer,GeneratedRuleBatch8Test.hit(500,false));
    }
    static void trainer(String id,int mode){
        var trainer=DATA.trainer(id).orElseThrow();TrainerProgramRules.validate(trainer.effectProgram());
        var m=game(trainer,List.of());var card=hand(m,"trainer");
        boolean hassel=id.equals("sv6_en_151")||id.equals("sv6_en_205");
        if(hassel){
            check(!m.canPlayTrainer(A,card.instanceId(),null),"Hassel usable without last-turn KO");
            reject(()->m.playTrainer(A,card.instanceId(),null));
            m.endTurn(A);m.attack(B,"hit");m.takePrizeAt(B,0);m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
        }
        var definition=GeneratedRuleBatch8Test.entry(id).getAsJsonArray("effects");
        // Extract the real search contract via its parsed effects, including Hassel's condition wrapper.
        var e=findSearch(definition);
        if(mode==2)m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-1);
        var before=m.privateView(A);var rng=m.randomTranscript();
        check(m.canPlayTrainer(A,card.instanceId(),null)&&rng.equals(m.randomTranscript()),"preflight changed RNG or refused search");
        m.playTrainer(A,card.instanceId(),null);
        verify(m,e,before,mode);
        check(m.privateView(A).discard().contains(card),"Trainer wasn't discarded");
    }
    static EffectDefinition findSearch(com.google.gson.JsonArray array){
        for(var item:array){var o=item.getAsJsonObject();if(o.get("type").getAsString().equals("search_deck")){
            var args=new LinkedHashMap<String,String>();for(var entry:o.entrySet())if(!Set.of("type","target").contains(entry.getKey()))args.put(entry.getKey(),entry.getValue().getAsString());
            return new EffectDefinition("search_deck",EffectTarget.DECK,args,List.of(),List.of());
        }if(o.has("effects")){var nested=findSearch(o.getAsJsonArray("effects"));if(nested!=null)return nested;}}
        throw new AssertionError("missing search");
    }
    static void ability(String id,int mode){
        var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();
        var m=game(DATA.trainer("sv3-5_en_156").orElseThrow(),List.of(ability));
        UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        reject(()->m.useAbility(A,bench,ability.id()));
        if(mode==2)m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-1);
        var before=m.privateView(A);m.useAbility(A,active,ability.id());
        var e=findSearch(GeneratedRuleBatch8Test.entry(id).getAsJsonArray("abilities").get(0).getAsJsonObject().getAsJsonArray("effects"));
        verify(m,e,before,mode);reject(()->m.useAbility(A,active,ability.id()));
    }
    static void verify(ServerMatch m,EffectDefinition e,ServerMatch.PrivatePlayerView before,int mode){
        int top=Integer.parseInt(e.arguments().get("top_count")),maximum=Integer.parseInt(e.arguments().get("amount"));
        var window=before.deck().subList(Math.max(0,before.deck().size()-top),before.deck().size());
        var p=m.clientView(A).effectSelection();
        var expected=window.stream().filter(c->{var d=m.cardDefinition(c.definitionId());
            if(e.arguments().containsKey("card_kinds"))return Set.of("basic","rulebox","energy").contains(c.definitionId());
            if(e.arguments().getOrDefault("card_category","").equals("POKEMON"))return d.category()==CardCategory.POKEMON;
            if(e.arguments().containsKey("trainer_category"))return d.trainerCategory()==TrainerCategory.SUPPORTER;
            return true;}).toList();
        check(m.clientView(B).effectSelection()==null,"opponent sees private deck window");
        List<CardInstance> chosen=List.of();
        if(p!=null){
            check(new HashSet<>(p.candidates()).equals(new HashSet<>(expected)),"candidate filter/window mismatch: "+e);
            check(p.minimum()==0&&p.maximum()==maximum,"printed optional maximum");
            chosen=mode==0?List.of():p.candidates().stream().limit(maximum).toList();var ids=chosen.stream().map(CardInstance::instanceId).toList();
            reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));
            var outside=before.deck().stream().filter(c->!window.contains(c)).findFirst();outside.ifPresent(c->reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(c.instanceId()))));
            m.submitEffectCards(A,p.resolutionId(),p.step(),ids);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),ids));
        }else check(expected.isEmpty(),"nonempty window silently skipped");
        var reveal=m.effectCardReveal();
        check((reveal!=null)==(!chosen.isEmpty()&&Boolean.parseBoolean(e.arguments().getOrDefault("reveal","false"))),"reveal contract");
        if(reveal!=null){check(reveal.cardIds().stream().sorted().toList().equals(chosen.stream().map(CardInstance::definitionId).sorted().toList()),"revealed unchosen cards");m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());}
        var remaining=new HashSet<>(before.deck());remaining.removeAll(chosen);
        check(new HashSet<>(m.privateView(A).deck()).equals(remaining)&&m.privateView(A).hand().containsAll(chosen),"deck/hand conservation");
        check(m.randomTranscript().stream().filter(r->r.operation().equals("effect-search-deck:"+A)).count()==Math.max(0,remaining.size()-1),"remainder shuffle count");
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"window search stalled");
    }
    static void thievul(){
        var ability=DATA.pokemon("sv4_en_120").orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
        for(int count:List.of(0,1,3)){
            var m=ModernRuleRepair2Test.selectionGame("sv10_en_175",List.of(ability),0);
            while(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("evolved")))m.drawCardsByEffect(A,A,1);
            while(m.privateView(B).hand().stream().filter(c->c.definitionId().equals("energy")).count()<count)m.drawCardsByEffect(A,B,1);
            var excess=m.privateView(B).hand().stream().filter(c->c.definitionId().equals("energy")).skip(count).map(CardInstance::instanceId).toList();
            if(!excess.isEmpty())m.discardHandCardsByEffect(A,B,excess);
            var before=m.privateView(B);m.evolveByEffect(A,m.activePokemonView(A).pokemonId(),hand(m,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            var trigger=m.triggerOrderPrompt();m.decideTriggeredEffect(A,trigger.id(),true);var reveal=m.effectCardReveal();
            check(reveal!=null&&reveal.cardIds().size()==before.hand().size(),"Thievul reveal");m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());
            var p=m.clientView(A).effectSelection();
            if(count>0){check(p!=null&&p.minimum()==Math.min(2,count)&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Thievul exact Energy selection");
                var ids=p.candidates().stream().limit(Math.min(2,count)).map(CardInstance::instanceId).toList();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));m.submitEffectCards(A,p.resolutionId(),p.step(),ids);}
            check(m.privateView(B).hand().size()==before.hand().size()-Math.min(2,count)&&!m.hasPendingEffectChoice(),"Thievul conservation/continuation");
        }
    }
    static void fixedWindow(String id,int size){
        var m=game(DATA.trainer(id).orElseThrow(),List.of());var trainer=hand(m,"trainer");
        m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);
        var before=m.privateView(A);var e=findSearch(GeneratedRuleBatch8Test.entry(id).getAsJsonArray("effects"));int top=Integer.parseInt(e.arguments().get("top_count"));
        var window=before.deck().subList(Math.max(0,size-top),size);var uninspected=before.deck().subList(0,Math.max(0,size-top));
        m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);
        check(p.minimum()==Math.min(2,size)&&new HashSet<>(p.candidates()).equals(new HashSet<>(window)),"mandatory count/short window: "+id);
        check(m.clientView(B).effectSelection()==null&&m.effectCardReveal()==null,"private window disclosed");
        var chosen=p.candidates().stream().limit(Math.min(2,size)).toList();var ids=chosen.stream().map(CardInstance::instanceId).toList();
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
        reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));
        m.submitEffectCards(A,p.resolutionId(),p.step(),ids);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),ids));
        var rest=new ArrayList<>(window);rest.removeAll(chosen);var after=m.privateView(A);
        boolean discard=e.arguments().get("remainder").equals("DISCARD");
        if(discard){check(after.deck().equals(uninspected)&&after.discard().containsAll(rest),"Explorer did not discard exact remainder");
            check(after.discard().size()==before.discard().size()+rest.size()+1,"Explorer discarded wrong count");}
        else {check(after.deck().subList(rest.size(),after.deck().size()).equals(uninspected),"Rika changed unseen deck order");
            check(new HashSet<>(after.deck().subList(0,rest.size())).equals(new HashSet<>(rest)),"Rika remainder not at bottom");
            check(m.randomTranscript().stream().filter(r->r.operation().equals("effect-window-shuffle-bottom:"+A)).count()==Math.max(0,rest.size()-1),"Rika shuffled more than remainder");}
        check(after.hand().containsAll(chosen)&&after.hand().size()==before.hand().size()-1+chosen.size()&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"mandatory window continuation/conservation");
    }
    static void deduction(int size,int branch){
        var m=game(DATA.trainer("sv8_en_171").orElseThrow(),List.of());var trainer=hand(m,"trainer");
        m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A);
        var window=before.deck().subList(Math.max(0,size-3),size);var unseen=before.deck().subList(0,Math.max(0,size-3));
        m.playTrainer(A,trainer.instanceId(),null);var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),branch);
        var p=m.effectCardPrompt(A);check(p.minimum()==window.size()&&new HashSet<>(p.candidates()).equals(new HashSet<>(window)),"Deduction inspected wrong count");
        var ids=p.candidates().stream().map(CardInstance::instanceId).toList();reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
        m.submitEffectCards(A,p.resolutionId(),p.step(),ids);
        var deck=m.privateView(A).deck();
        if(branch==1){check(deck.subList(window.size(),size).equals(unseen)&&new HashSet<>(deck.subList(0,window.size())).equals(new HashSet<>(window)),"Deduction bottom branch");}
        else {check(deck.subList(0,unseen.size()).equals(unseen),"Deduction reordered unseen cards");
            check(new HashSet<>(deck.subList(unseen.size(),size)).equals(new HashSet<>(window)),"Deduction top lost cards");}
        check(!m.hasPendingEffectChoice()&&m.effectCardReveal()==null&&m.privateView(A).hand().size()==before.hand().size()-1,"Deduction continuation/privacy");
    }
}
