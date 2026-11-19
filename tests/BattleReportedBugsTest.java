import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.MatchActionMatcher;
import java.util.*;

/** User-reported regressions: exercise server-issued UI intents and real bundled abilities. */
public final class BattleReportedBugsTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    public static void main(String[] args){munkidori();ultraBall();stadium();cipher();System.out.println("BattleReportedBugsTest passed: Ultra Ball cost/search, occupied Stadium drop, five Munkidori prints, safe quantity selection, three Ciphermaniac prints");}
    static void ultraBall(){
      for(boolean take:List.of(false,true)){
        var m=ModernQuantityRepairTest.game(GeneratedRuleBatch8Test.hit(40,false),List.of(),EnergyType.DARKNESS,"sv1_en_196");
        var s=MatchSession.multiplayer(m,A,B);var card=hand(m,A,"trainer");var v=s.view(A,A);
        var option=v.options().stream().filter(o->o.cardKeys().contains("hand:"+card.instanceId())).findFirst().orElseThrow(()->new AssertionError("Ultra Ball has no UI intent"));
        s.submit(A,A,v.revision(),option.index());var cost=m.effectCardPrompt(A);
        check(cost.minimum()==2&&cost.maximum()==2&&!cost.candidates().contains(card),"Wrong discard cost");
        var selected=cost.candidates().stream().limit(2).map(CardInstance::instanceId).toList();
        CopyAttackTest.reject(()->m.submitEffectCards(A,cost.resolutionId(),cost.step(),selected.subList(0,1)));
        m.submitEffectCards(A,cost.resolutionId(),cost.step(),selected);var search=m.effectCardPrompt(A);
        check(m.clientView(B).effectSelection()==null&&search.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON),"Search privacy/filter");
        var chosen=take?List.of(search.candidates().getFirst().instanceId()):List.<UUID>of();
        m.submitEffectCards(A,search.resolutionId(),search.step(),chosen);
        if(m.effectCardReveal()!=null){var r=m.effectCardReveal();m.acknowledgeEffectReveal(r.confirmingPlayer(),r.id());}
        check(!m.hasPendingEffectChoice()&&m.privateView(A).discard().contains(card)&&m.currentPlayer().equals(A),"Ultra Ball stalled");
        CopyAttackTest.reject(()->s.submit(A,A,v.revision(),option.index()));
      }
      var poor=ModernQuantityRepairTest.game(GeneratedRuleBatch8Test.hit(40,false),List.of(),EnergyType.DARKNESS,"sv1_en_196");
      var card=hand(poor,A,"trainer");poor.discardHandCardsByEffect(A,A,poor.privateView(A).hand().stream().filter(c->!c.equals(card)).skip(1).map(CardInstance::instanceId).toList());
      check(!poor.canBeginTrainerSearch(A,card.instanceId()),"Ultra Ball allowed insufficient cost");
    }
    static void clear(ServerMatch m){while(m.triggerOrderPrompt()!=null){var p=m.triggerOrderPrompt();m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());}}
    static void stadium(){
        var m=StadiumActivationTest.game();var a=StadiumActivationTest.A;var b=StadiumActivationTest.B;
        var old=hand(m,a,"quarry");m.playTrainer(a,old.instanceId(),null);clear(m);
        m.endTurn(a);m.endTurn(b);var card=hand(m,a,"other_quarry");var s=MatchSession.multiplayer(m,a,b);var v=s.view(a,a);
        check(!m.canPlayTrainer(a,hand(m,a,"quarry").instanceId(),null),"Same-name Stadium accepted");
        var options=MatchActionMatcher.forDrop(v,"hand:"+card.instanceId(),"stadium:"+old.instanceId());check(options.size()==1,"Occupied Stadium drop unavailable");
        s.submit(a,a,v.revision(),options.getFirst().index());clear(m);
        check(m.clientView(a).stadium().equals(card)&&m.privateView(a).discard().contains(old),"Stadium replacement ownership");
        check(!m.canPlayTrainer(a,hand(m,a,"quarry").instanceId(),null),"Second Stadium in same turn accepted");
    }
    static void cipher(){
        for(String id:List.of("sv5_en_145","sv5_en_198","sv8-5_en_104"))for(int size:List.of(1,6)){
            var m=ModernQuantityRepairTest.game(GeneratedRuleBatch8Test.hit(40,false),List.of(),EnergyType.PSYCHIC,id);
            m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A).deck();var card=hand(m,A,"trainer");m.playTrainer(A,card.instanceId(),null);
            var p=m.effectCardPrompt(A);check(p.minimum()==Math.min(size,2)&&p.maximum()==2&&m.clientView(B).effectSelection()==null,"Cipher selection/privacy");
            var cards=new ArrayList<>(p.candidates().subList(0,Math.min(size,2)));Collections.reverse(cards);
            var ids=cards.stream().map(CardInstance::instanceId).toList();CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
            m.submitEffectCards(A,p.resolutionId(),p.step(),ids);var deck=m.privateView(A).deck();
            for(int i=0;i<cards.size();i++)check(deck.get(deck.size()-1-i).equals(cards.get(i)),"Cipher ignored top-first order");
            check(new HashSet<>(deck).equals(new HashSet<>(before))&&deck.size()==before.size()&&!m.hasPendingEffectChoice()&&m.effectCardReveal()==null&&m.privateView(A).discard().contains(card),"Cipher loss/privacy/stall");
            CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),ids));
        }
    }
    static void munkidori(){
        for(String id:List.of("me2-5_en_099","sv6_en_095","sv6-5_en_072","sv8-5_en_044","svalt_en_155"))for(int damage:List.of(1,2,3,5)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();var m=ModernQuantityRepairTest.game(GeneratedRuleBatch8Test.hit(40,false),List.of(ability),EnergyType.DARKNESS,"swsh5_en_124");
            var source=m.activePokemonView(A).pokemonId();var target=m.benchPokemonView(B).getFirst().pokemonId();ModernRuleRepair2Test.pay(m,A,source,"energy",1);m.placeDamageCountersByEffect(A,A,source,damage);
            m.useAbility(A,source,ability.id());var own=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,own.resolutionId(),own.step(),source);var foe=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,foe.resolutionId(),foe.step(),target);
            var p=m.effectChoicePrompt(A);int amount=Math.min(3,damage);
            CopyAttackTest.reject(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),0));
            CopyAttackTest.reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),p.optionsZh().size()));
            check(p.optionsZh().size()==amount+1,"Munkidori offered unavailable quantities");
            m.chooseEffectOption(A,p.resolutionId(),p.step(),amount-1);
            check(m.activePokemonView(A).damage()==(damage-amount)*10&&m.benchPokemonView(B).getFirst().damage()==amount*10&&!m.hasPendingEffectChoice(),"Munkidori counter movement/stall");
            check(!m.canUseAbility(A,source,ability.id()),"Munkidori used twice");m.endTurn(A);
        }
        var ability=DATA.pokemon("sv6_en_095").orElseThrow().abilities().getFirst();
        var m=ModernQuantityRepairTest.game(GeneratedRuleBatch8Test.hit(40,false),List.of(ability),EnergyType.DARKNESS,"swsh5_en_124");
        var host=m.activePokemonView(A).pokemonId();var own=m.benchPokemonView(A).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,own,1);check(!m.canUseAbility(A,host,ability.id()),"Munkidori ignored Darkness requirement");
        ModernRuleRepair2Test.pay(m,A,host,"energy",1);m.useAbility(A,host,ability.id());
        var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),own);
        p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),m.activePokemonView(B).pokemonId());
        var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),q.optionsZh().size()-1);
        check(!m.hasPendingEffectChoice()&&m.benchPokemonView(A).getFirst().damage()==10&&m.activePokemonView(B).damage()==0,"Decline transferred counters or stalled");
        m.endTurn(A);
    }
}
