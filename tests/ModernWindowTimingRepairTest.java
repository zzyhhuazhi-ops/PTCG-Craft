import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernWindowTimingRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable r){CopyAttackTest.reject(r);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    static ServerMatch game(AttackDefinition attack,List<AbilityDefinition> abilities,EnergyType type,String trainer){return ModernQuantityRepairTest.game(attack,abilities,type,trainer);}
    public static void main(String[] args){energyWindows();moth();mixer();blower();calculation();payments();crustle();drifblim();System.out.println("ModernWindowTimingRepairTest passed: 12 prints; private Energy windows, bottom-only shuffle, entry transfer, whole-deck discard, field attachments/stadium, exact reorder, paid damage and post-damage timing");}
    /** Controlled physical deck setup; no cards created or duplicated. */
    static List<CardInstance> window(ServerMatch m,List<String> names,boolean shortDeck){
        var deck=GeneratedRuleBatch47Test.zone(m,A,"deck");var h=GeneratedRuleBatch47Test.zone(m,A,"hand");
        if(shortDeck){h.addAll(deck);deck.clear();}
        var chosen=new ArrayList<CardInstance>();for(String id:names){var c=hand(m,A,id);h.remove(c);chosen.add(c);}deck.addAll(chosen);return chosen;
    }
    static void energyWindows(){
        for(String id:List.of("sv4_en_055","sv5_en_114","svbsp_en_090"))for(boolean take:List.of(false,true))for(boolean small:List.of(false,true)){
            boolean metal=!id.equals("sv4_en_055");var profile=DATA.pokemon(id).orElseThrow();var attack=metal?GeneratedRuleBatch8Test.hit(40,false):profile.attacks().getFirst();
            var abilities=metal?profile.abilities():List.<AbilityDefinition>of();var m=game(attack,abilities,EnergyType.METAL,"swsh5_en_124");UUID source=m.activePokemonView(A).pokemonId();
            if(!metal)ModernRuleRepair2Test.pay(m,A,source,"energy",1);
            var names=small?List.of("energy","special"):metal?List.of("energy","special","wrong","basic"):List.of("energy","special","wrong","basic","tool");
            var window=window(m,names,small);var before=m.privateView(A);var unseen=before.deck().subList(0,before.deck().size()-window.size());
            if(metal)m.useAbility(A,source,abilities.getFirst().id());else m.attack(A,attack.id());
            var p=m.effectCardPrompt(A);var eligible=window.stream().filter(c->c.definitionId().equals("energy")||!metal&&c.definitionId().equals("wrong")).toList();
            check(new HashSet<>(p.candidates()).equals(new HashSet<>(eligible))&&m.clientView(B).effectSelection()==null,"Top Energy filtering/privacy");
            var chosen=take?eligible:List.<CardInstance>of();var ids=chosen.stream().map(CardInstance::instanceId).toList();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));m.submitEffectCards(A,p.resolutionId(),p.step(),ids);
            if(metal&&!chosen.isEmpty()){var q=m.effectAttachmentPrompt(A);for(int i=0;i<chosen.size();i++)m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),chosen.get(i).instanceId(),i%2==0?source:m.benchPokemonView(A).getFirst().pokemonId());m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step());}
            var rest=new ArrayList<>(window);rest.removeAll(chosen);var deck=m.privateView(A).deck();
            var expected=new HashSet<>(before.deck());expected.removeAll(chosen);check(new HashSet<>(deck).equals(expected)&&deck.size()==expected.size(),"Top Energy lost/duplicated cards");
            if(metal){check(new HashSet<>(deck.subList(0,rest.size())).equals(new HashSet<>(rest))&&deck.subList(rest.size(),deck.size()).equals(unseen),"Metang shuffled unseen deck or misplaced remainder");check(!m.canUseAbility(A,source,abilities.getFirst().id()),"Metang used twice");}
            else check(m.activePokemonView(A).attachedEnergyIds().containsAll(ids)&&m.currentPlayer().equals(B),"Dondozo didn't attach to self or end attack");
            check(!m.hasPendingEffectChoice(),"Top Energy window stalled");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),ids));
        }
    }
    static void moth(){
        var ability=DATA.pokemon("sv4_en_187").orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
        var m=game(GeneratedRuleBatch8Test.hit(40,false),List.of(ability),EnergyType.FIRE,"swsh5_en_124");UUID old=m.activePokemonView(A).pokemonId(),source=m.benchPokemonView(A).getFirst().pokemonId();
        var fire=hand(m,A,"energy");var special=hand(m,A,"special");var water=hand(m,A,"wrong");var own=hand(m,B,"special");
        for(var c:List.of(fire,special,water))m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,c.instanceId(),A,old);m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,own.instanceId(),A,source);
        m.switchActiveByEffect(A,A,source);m.decideTriggeredEffect(A,m.triggerOrderPrompt().id(),true);var p=m.effectCardPrompt(A);
        check(new HashSet<>(p.candidates()).equals(Set.of(fire,special)),"Moth must include physical Fire-providing Special, exclude Water and own attachments");
        m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(fire.instanceId(),special.instanceId()));
        check(m.activePokemonView(A).attachedEnergyIds().containsAll(List.of(fire.instanceId(),special.instanceId(),own.instanceId()))&&m.benchPokemonView(A).getFirst().attachedEnergyIds().equals(List.of(water.instanceId()))&&!m.hasPendingEffectChoice(),"Moth transfer lost Energy or target");
    }
    static void mixer(){
        for(int count:List.of(0,1,5)){
            var m=game(GeneratedRuleBatch8Test.hit(40,false),List.of(),EnergyType.GRASS,"sv8_en_164");var before=m.privateView(A);var trainer=hand(m,A,"trainer");m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);
            check(p.minimum()==0&&p.maximum()==5&&new HashSet<>(p.candidates()).equals(new HashSet<>(before.deck())),"Mixer search not full deck/up to five");
            var chosen=p.candidates().stream().limit(count).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
            check(m.privateView(A).discard().containsAll(chosen)&&m.privateView(A).discard().contains(trainer)&&m.privateView(A).deck().size()==before.deck().size()-count&&!m.hasPendingEffectChoice(),"Mixer movement/continuation");
        }
    }
    static void blower(){
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.WATER,2),GeneratedRuleBatch8Test.hit(40,false),List.of(),0,DATA.trainer("sv8_en_182").orElseThrow());
        var removed=new ArrayList<CardInstance>();for(UUID host:List.of(m.activePokemonView(B).pokemonId(),m.benchPokemonView(B).getFirst().pokemonId())){removed.add(AtomicAttachmentDiscardTest.tool(m,B,host));removed.add(AtomicAttachmentDiscardTest.energy(m,B,B,host,"special"));AtomicAttachmentDiscardTest.energy(m,B,B,host,"energy");}
        var kept=AtomicAttachmentDiscardTest.tool(m,A,m.activePokemonView(A).pokemonId());var stadium=hand(m,A,"stadium");m.playTrainer(A,stadium.instanceId(),null);m.playTrainer(A,hand(m,A,"item").instanceId(),null);
        check(m.privateView(B).discard().containsAll(removed)&&m.privateView(A).discard().contains(stadium),"Blower omitted field attachments/stadium");
        for(var v:List.of(m.activePokemonView(B),m.benchPokemonView(B).getFirst()))check(v.attachedTools().isEmpty()&&v.attachedEnergyIds().size()==1,"Blower removed Basic or left Special Energy");
        check(m.activePokemonView(A).attachedTools().contains(kept)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Blower touched own Tools/stalled");
    }
    static void calculation(){
        var a=DATA.pokemon("sv5_en_080").orElseThrow().attacks().getFirst();
        for(int size:List.of(1,3,8)){var m=game(a,List.of(),EnergyType.PSYCHIC,"swsh5_en_124");ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A).deck();m.attack(A,a.id());var p=m.effectCardPrompt(A);
            check(p.minimum()==Math.min(4,size)&&p.maximum()==Math.min(4,size)&&m.clientView(B).effectSelection()==null,"Calculation short-window count/privacy: "+p.minimum()+"/"+p.maximum());
            var ordered=new ArrayList<>(p.candidates());Collections.reverse(ordered);m.submitEffectCards(A,p.resolutionId(),p.step(),ordered.stream().map(CardInstance::instanceId).toList());var deck=m.privateView(A).deck();
            for(int i=0;i<ordered.size();i++)check(deck.get(deck.size()-1-i).equals(ordered.get(i)),"Calculation top-first order ignored");check(deck.subList(0,Math.max(0,size-4)).equals(before.subList(0,Math.max(0,size-4)))&&m.currentPlayer().equals(B),"Calculation changed unseen order/stalled");}
    }
    static void payments(){
        for(String id:List.of("sv2_en_132","sv6-5_en_005"))for(boolean enough:List.of(false,true)){
            var attack=DATA.pokemon(id).orElseThrow().attacks().get(1);boolean grass=id.equals("sv6-5_en_005");var m=game(attack,List.of(),grass?EnergyType.GRASS:EnergyType.DARKNESS,"swsh5_en_124");ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",grass?1:3);
            if(!enough){var ids=m.privateView(A).hand().stream().filter(c->!grass||c.definitionId().equals("energy")).map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(A,A,ids);}
            m.attack(A,attack.id());var p=m.clientView(A).effectSelection();if(enough){check(p!=null&&p.minimum()==1&&p.maximum()==1,"Mandatory attack discard");if(grass)check(p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Power Shot accepted wrong or Special energy");m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));}
            check(m.lastAttack().finalDamage()==(enough?(grass?170:140):0)&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Attack damage without payment or stalled");
        }
    }
    static void crustle(){
        for(String id:List.of("sv4_en_007","sv4_en_183"))for(boolean evolve:List.of(false,true)){
            var attack=DATA.pokemon(id).orElseThrow().attacks().get(1);var cfg=new TemporaryReceivedDamageTest.Config(attack);cfg.energy=EnergyType.GRASS;var m=TemporaryReceivedDamageTest.game(cfg);TemporaryReceivedDamageTest.pay(m,A,attack);m.attack(A,attack.id());
            if(evolve)TemporaryReceivedDamageTest.evolve(m,B);m.attack(B,"hit");check(m.activePokemonView(A).damage()==(evolve?40:140),"Crustle must reduce evolved attacker only");
            m.endTurn(A);m.attack(B,"hit");check(m.lastAttack().finalDamage()==140,"Crustle protection did not expire");
        }
    }
    static void drifblim(){
        var attack=DATA.pokemon("sv7_en_061").orElseThrow().attacks().getFirst();var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(),attack,EnergyType.PSYCHIC,0,Map.of("basic","Drifloon","evolved","Drifblim"));
        m.evolveByEffect(A,m.activePokemonView(A).pokemonId(),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.playBasicPokemonToBench(A,hand(m,A,"wrongmon").instanceId());ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);m.attack(A,attack.id());
        check(m.lastAttack().finalDamage()==100&&m.activePokemonView(A).damage()==30,"Drifblim count/self timing");for(var p:m.benchPokemonView(A))check(p.damage()==(p.topCardId().equals("basic")?30:0),"Drifblim damaged unrelated bench");check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Drifblim stalled");
    }
}
