import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.*;
import java.util.*;

public final class BattleSelectionRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static CardInstance hand(ServerMatch m,String id){return ModernRuleRepair2Test.hand(m,A,id);}
    static boolean confirm(MatchSession.Snapshot s){return s.options().stream().anyMatch(o->o.enabled()&&o.labelZh().startsWith("确认选牌"));}
    static void pick(MatchSession s,UUID id){var v=s.view(A,A);var o=MatchActionMatcher.forCards(v,List.of("effect:"+id)).getFirst();s.submit(A,A,v.revision(),o.index());}
    static void finishAi(MatchSession s){
        for(int i=0;i<30&&s.match().hasPendingEffectChoice();i++){
            UUID seat=s.match().pendingChoicePlayer();var v=AiPracticeScenarios.view(s,seat);
            var random=s.match().randomTranscript();var d=AiPolicy.choose(v,123,Set.of()).orElseThrow();
            check(random.equals(s.match().randomTranscript()),"AI consumed match RNG");
            s.submit(seat,seat,v.snapshot().revision(),d.option());
        }
        check(!s.match().hasPendingEffectChoice(),"AI choice loop did not finish");
    }
    static void energy(boolean ai,boolean special){
        var attack=DATA.pokemon("me4_en_061").orElseThrow().attacks().get(1);
        var m=ModernRuleRepair2Test.game(attack,EnergyType.METAL);UUID active=m.activePokemonView(A).pokemonId();
        ModernRuleRepair2Test.pay(m,A,active,"energy",3);
        ModernRuleRepair2Test.pay(m,A,active,"special",1);
        var before=new HashSet<>(m.activePokemonView(A).attachedEnergyIds());
        m.attack(A,attack.id());var choice=m.effectChoicePrompt(A);
        // The first real option opts in; the optional decline is added by the engine.
        m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(m.hasPendingCardSelection(),"Metallic Hammer did not request Energy selection");
        var s=MatchSession.multiplayer(m,A,B);var p=m.effectCardPrompt(A);
        check(EffectCardPage.from(s.view(A,A),0).cards().size()==4,"Energy faces missing from selection overlay");
        check(!confirm(s.view(A,A)),"Empty Energy payment can be confirmed");
        if(ai)finishAi(s);
        else {
            var first=p.candidates().stream().filter(c->c.definitionId().equals(special?"special":"energy")).findFirst().orElseThrow();
            pick(s,first.instanceId());check(!confirm(s.view(A,A)),"1 or 2 units accepted for 3-unit discard");
            var rest=p.candidates().stream().filter(c->c.definitionId().equals("energy")&&!c.equals(first)).toList();
            pick(s,rest.getFirst().instanceId());
            check(confirm(s.view(A,A))==special,"Confirmation ignored real energy units");
            if(!special)pick(s,rest.get(1).instanceId());
            check(confirm(s.view(A,A)),"Complete 3-unit payment rejected");
            var v=s.view(A,A);var done=v.options().stream().filter(o->o.labelZh().startsWith("确认选牌")).findFirst().orElseThrow();
            s.submit(A,A,v.revision(),done.index());
            CopyAttackTest.reject(()->s.submit(A,A,v.revision(),done.index()));
        }
        check(m.activePokemonView(B).damage()==300,"Metallic Hammer damage changed");
        check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Attack did not end after payment");
        check(m.privateView(A).discard().stream().filter(c->before.contains(c.instanceId())).count()>=2,"Energy did not reach discard");
    }
    static ServerMatch copyFixture(int start,int metalCount){
        var slow=DATA.pokemon("sv7_en_058").orElseThrow().attacks();
        var hammer=DATA.pokemon("me4_en_061").orElseThrow().attacks();
        var db=new CardDatabase(List.of(CopyAttackTest.card("copy",true),CopyAttackTest.card("provider",true),CopyAttackTest.card("energy",false),CopyAttackTest.card("metal",false)));
        var combat=new CombatCatalog(Map.of("copy",new PokemonProfile(600,0,null,null,0,null,0,1,slow,List.of(),EnergyType.PSYCHIC),
            "provider",new PokemonProfile(600,0,null,null,0,null,0,1,hammer,List.of(),EnergyType.METAL)),Map.of("energy",EnergyType.PSYCHIC,"metal",EnergyType.METAL));
        for(int seed=start;seed<start+200;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){
                var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i%4==0?"copy":i%4==1?"provider":i%4==2?"energy":"metal"));decks.add(cards);
            }
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,db,combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(o->m.privateView(o).hand().stream().noneMatch(c->c.definitionId().equals("copy"))))continue;
            for(UUID o:List.of(A,B)){var h=m.privateView(o).hand();m.chooseOpeningActive(o,h.stream().filter(c->c.definitionId().equals("copy")).findFirst().orElseThrow().instanceId());
                h.stream().filter(c->c.definitionId().equals("provider")).findFirst().ifPresent(c->m.placeOpeningBench(o,c.instanceId()));m.startAfterOpening(o);}
            OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);
            for(int i=0;i<2;i++){var c=m.privateView(A).deck().stream().filter(x->x.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,c.instanceId(),A,m.activePokemonView(A).pokemonId());}
            for(int i=0;i<metalCount;i++){var c=m.privateView(A).deck().stream().filter(x->x.definitionId().equals("metal")).findFirst().orElseThrow();m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,c.instanceId(),A,m.activePokemonView(A).pokemonId());}
            if(m.privateView(A).deck().getLast().definitionId().equals("provider"))return m;
        }throw new AssertionError("No copy fixture");
    }
    static void copy(){
        for(int seed:List.of(0,17,43))for(int metalCount:List.of(0,1,2,3)){
            var m=copyFixture(seed,metalCount);var s=MatchSession.multiplayer(m,A,B);
            m.attack(A,"seek-inspiration");
            var reveal=m.effectCardReveal();check(reveal!=null,"Top card was not publicly revealed");
            var response=AiPracticeScenarios.view(s,reveal.confirmingPlayer());
            s.submit(reveal.confirmingPlayer(),reveal.confirmingPlayer(),response.snapshot().revision(),AiPolicy.choose(response,123,Set.of()).orElseThrow().option());
            check(m.hasPendingTopDeckAttackCopy(),"No copied attack choice");
            var v=AiPracticeScenarios.view(s,A);var chosen=AiPolicy.choose(v,123,Set.of()).orElseThrow();
            check(v.snapshot().options().get(chosen.option()).cardKeys().contains("copy-attack:metallic-hammer"),"AI declined or failed to evaluate revealed copied attack");
            finishAi(s);
            check(m.activePokemonView(B).damage()==300,"Copied Metallic Hammer with zero/partial/full Metal Energy must add 150 when opted in");
            check(m.activePokemonView(A).attachedEnergyIds().size()==2,"Copied attack discarded non-Metal Energy");
            check(m.currentPlayer().equals(B),"Copied attack continuation stalled");
        }
    }
    static void support(){
        var cards=BundledRules.loadCardDefinitions();var deck=new ArrayList<CardInstance>();
        for(String id:List.of("me4_en_061","sv7_en_058","sv6-5_en_018"))deck.add(new CardInstance(UUID.randomUUID(),A,id));
        check(AiSupport.problems(deck,cards,DATA).isEmpty(),"Tested energy / own-deck copy still blocked: "+AiSupport.problems(deck,cards,DATA));
        var mixed=new ArrayList<>(deck);mixed.add(new CardInstance(UUID.randomUUID(),A,"sv9_en_098"));
        check(AiSupport.problems(mixed,cards,DATA).isEmpty(),"已验证的己方备战招式复制仍被拦截");
    }
    static void stadium(){
        check(MatchActionMatcher.isArenaPlay(new MatchSession.Option(0,"放置场地：测试",List.of("hand:one"))),"Empty Stadium drag rejected");
        check(!MatchActionMatcher.isArenaPlay(new MatchSession.Option(0,"放置场地：测试",List.of("hand:one"),false)),"Disabled Stadium action allowed");
        check(!MatchActionMatcher.isArenaPlay(new MatchSession.Option(0,"攻击：测试")),"Attack accepted as Stadium drop");
    }
    public static void main(String[] args){stadium();energy(false,false);energy(false,true);energy(true,false);copy();support();System.out.println("BattleSelectionRepairTest passed: Stadium drag, selectable Energy faces, unit-aware confirmation, single/multi-unit payment, AI completion, copied Metallic Hammer 12 seed/energy scenarios and bounded support gates");}
}
