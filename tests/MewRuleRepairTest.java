import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.*;
import java.util.*;

/** Real bundled Mew prints, authoritative choices, AI preflight and hidden-state invariance. */
public final class MewRuleRepairTest {
    static final UUID A=GeneratedRuleBatch51Test.A,B=GeneratedRuleBatch51Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static final CardDatabase CARDS=BundledRules.loadCardDefinitions();
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static List<String> prints(String ability){return CARDS.all().stream().map(CardDefinition::id)
        .filter(id->DATA.pokemon(id).map(p->p.abilities().stream().anyMatch(a->a.id().equals(ability))).orElse(false)).sorted().toList();}
    static ServerMatch fixture(String id){return GeneratedRuleBatch51Test.fixture(id,List.of(GeneratedRuleBatch51Test.hit()),List.of(),false);}
    static void handSize(ServerMatch m,int n){var h=m.privateView(A).hand();m.discardHandCardsByEffect(A,A,h.stream().skip(n).map(CardInstance::instanceId).toList());}
    static List<CardInstance> deck(String... ids){var result=new ArrayList<CardInstance>();for(String id:ids)result.add(new CardInstance(UUID.randomUUID(),A,id));return result;}
    static MatchSession.Option choose(MatchSession s,java.util.function.Predicate<MatchSession.Option> filter){
        var v=AiPracticeScenarios.view(s,A);var snap=v.snapshot();var options=snap.options().stream().filter(filter).toList();
        var filtered=new MatchSession.Snapshot(snap.revision(),snap.practice(),snap.controllableSeats(),snap.game(),options,snap.cardNames(),snap.publicCues(),snap.attackBlockZh(),snap.guidance(),snap.events());
        var input=new AiView(filtered,v.cards(),v.energy(),v.deck(),v.turn());
        int index=AiPolicy.choose(input,73,Set.of()).orElseThrow().option();return options.stream().filter(o->o.index()==index).findFirst().orElseThrow();
    }
    static void restart(String id){
        var m=fixture(id);UUID host=m.activePokemonView(A).pokemonId();
        check(!m.canUseAbility(A,host,"restart"),id+" Restart allowed with full hand");handSize(m,1);int before=m.privateView(A).deck().size();
        var s=MatchSession.multiplayer(m,A,B);var v=s.view(A,A);
        var action=MatchActionMatcher.cardFaceActions(v.options(),"field:"+host,true).stream().filter(o->o.labelZh().startsWith("使用特性：")).findFirst().orElseThrow();
        s.submit(A,A,v.revision(),action.index());check(m.privateView(A).hand().size()==3&&m.privateView(A).deck().size()==before-2,id+" Restart must draw to three");
        CopyAttackTest.reject(()->s.submit(A,A,v.revision(),action.index()));handSize(m,0);CopyAttackTest.reject(()->m.useAbility(A,host,"restart"));
        UUID other=m.benchPokemonView(A).stream().filter(p->p.topCardId().equals(id)).findFirst().orElseThrow().pokemonId();
        m.useAbility(A,other,"restart");check(m.privateView(A).hand().size()==3,id+" Different Mew shared once-per-turn counter");
        m.endTurn(A);m.endTurn(B);handSize(m,2);m.useAbility(A,host,"restart");check(m.privateView(A).hand().size()==3,id+" Restart did not reset next turn");
        var disabled=fixture(id);handSize(disabled,0);disabled.disableAbilityEffectsByEffect(A,A,disabled.activePokemonView(A).pokemonId());
        check(!disabled.canUseAbility(A,disabled.activePokemonView(A).pokemonId(),"restart"),id+" Suppressed Restart offered");
        var shortDeck=fixture(id);handSize(shortDeck,0);
        var left=AiPracticeScenarios.zone(shortDeck,A,"deck");var discarded=AiPracticeScenarios.zone(shortDeck,A,"discard");
        while(left.size()>1)discarded.add(left.removeFirst());
        shortDeck.useAbility(A,shortDeck.activePokemonView(A).pokemonId(),"restart");
        check(shortDeck.privateView(A).hand().size()==1&&shortDeck.privateView(A).deck().isEmpty()&&shortDeck.phase()==MatchPhase.TURN,id+" Restart overdraw/empty-deck handling wrong");
    }
    static void genome(String id){
        var m=fixture(id);UUID host=m.activePokemonView(A).pokemonId();GeneratedRuleBatch51Test.pay(m,host,2);
        var s=MatchSession.multiplayer(m,A,B);check(MatchActionMatcher.cardFaceActions(s.view(A,A).options(),"field:"+host,true).stream().noneMatch(MatchActionMatcher::isAttackOption),id+" Genome accepted two Energy");
        var c=m.copyAttackOptions(A,"genome-hacking").getFirst();check(c.effectiveAttack().energyCost().equals(Map.of(EnergyType.COLORLESS,3)),id+" Copied cost replaced Genome cost");
        GeneratedRuleBatch51Test.pay(m,host,1);handSize(m,3);
        var v=s.view(A,A);var action=choose(s,o->MatchActionMatcher.isAvailableAttackOption(o)||o.labelZh().equals("结束回合"));
        check(action.cardKeys().contains("copy-source:plain")&&action.cardKeys().contains("copy-attack:plain-hit"),id+" Copied public attack metadata missing");
        privacy(s);s.submit(A,A,v.revision(),action.index());
        check(m.lastAttack().finalDamage()==30&&m.activePokemonView(B).damage()==30&&m.currentPlayer().equals(B),id+" Genome did not resolve and end turn");
        check(m.activePokemonView(A).attachedEnergyIds().size()==3,id+" Attack cost wrongly discarded Energy");
    }
    static void copiedPayment(String id){
        var m=GeneratedRuleBatch51Test.fixture(id,DATA.pokemon("me4_en_061").orElseThrow().attacks(),List.of(),false);
        m.endTurn(A);var provider=GeneratedRuleBatch51Test.hand(m,B,"provider");m.playBasicPokemonToBench(B,provider.instanceId());
        UUID enemy=m.benchPokemonView(B).stream().filter(p->p.topCardId().equals("provider")).findFirst().orElseThrow().pokemonId();
        m.switchActiveByEffect(B,B,enemy);m.endTurn(B);GeneratedRuleBatch51Test.pay(m,m.activePokemonView(A).pokemonId(),3);
        var session=MatchSession.multiplayer(m,A,B);var snap=session.view(A,A);
        var action=choose(session,o->MatchActionMatcher.isAvailableAttackOption(o)||o.labelZh().equals("结束回合"));
        check(action.cardKeys().contains("copy-attack:metallic-hammer"),id+" AI failed to compare copied attacks");
        session.submit(A,A,snap.revision(),action.index());BattleSelectionRepairTest.finishAi(session);
        check(m.activePokemonView(B).damage()==300&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),id+" Copied Energy payment used provider, wrong units or skipped bonus");
        check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),id+" Copied effect left match stuck");
    }
    static void memory(String id){
        var m=fixture(id);UUID host=m.activePokemonView(A).pokemonId();GeneratedRuleBatch51Test.pay(m,host,1);var s=MatchSession.multiplayer(m,A,B);
        check(m.decisionEnergy(A).get(host).costs().containsKey("borrow|provider|hit"),id+" AI does not see real borrowed attack payment");
        var energy=choose(s,o->o.labelZh().startsWith("附着 ")&&o.cardKeys().contains("field:"+host)||o.labelZh().equals("结束回合"));
        check(energy.labelZh().startsWith("附着 "),id+" AI stopped attaching after native attack was ready");
        var v=s.view(A,A);s.submit(A,A,v.revision(),energy.index());
        var attack=choose(s,o->MatchActionMatcher.isAvailableAttackOption(o)||o.labelZh().equals("结束回合"));
        check(attack.cardKeys().contains("copy-source:provider")&&attack.cardKeys().contains("copy-attack:hit"),id+" AI failed to prefer stronger borrowed attack");
        privacy(s);v=s.view(A,A);s.submit(A,A,v.revision(),attack.index());
        check(m.lastAttack().finalDamage()==70&&m.currentPlayer().equals(B),id+" Borrowed attack did not resolve");
        for(boolean swap:List.of(false,true)){
            var n=fixture(id);UUID original=n.activePokemonView(A).pokemonId();GeneratedRuleBatch51Test.pay(n,original,1);n.attack(A,"teleportation-burst");
            check(n.activePokemonView(B).damage()==30,id+" Teleportation switched before damage");
            var p=n.effectChoicePrompt(A);n.chooseEffectOption(A,p.resolutionId(),p.step(),swap?0:1);
            BattleSelectionRepairTest.finishAi(MatchSession.multiplayer(n,A,B));
            check(n.activePokemonView(A).pokemonId().equals(original)!=swap&&n.currentPlayer().equals(B),id+" Optional Teleportation swap failed");
        }
    }
    static void privacy(MatchSession s){
        var m=s.match();var input=AiPracticeScenarios.view(s,A);var rng=m.randomTranscript();var d=AiPolicy.choose(input,91,Set.of());
        for(UUID p:List.of(A,B))for(String zone:List.of("deck","prizes"))Collections.reverse(AiPracticeScenarios.zone(m,p,zone));
        var hand=AiPracticeScenarios.zone(m,B,"hand");var deck=AiPracticeScenarios.zone(m,B,"deck");var c=hand.set(0,deck.get(0));deck.set(0,c);
        var changed=AiPracticeScenarios.view(s,A);check(input.equals(changed)&&d.equals(AiPolicy.choose(changed,91,Set.of())),"Mew AI read hidden cards/order");
        check(rng.equals(m.randomTranscript()),"Mew decision consumed match RNG");
        UUID watcher=new UUID(0,2525);s.addSpectator(watcher);check(s.view(watcher,null).options().isEmpty(),"Spectator got Mew action authority");
    }
    static void support(){
        for(String id:prints("memory-helix"))check(AiSupport.problems(deck(id,"me4_en_061"),CARDS,DATA).isEmpty(),id+" Own Bench grant blocked");
        for(String id:prints("restart")){
            check(!AiSupport.problems(deck(id),CARDS,DATA).isEmpty(),"Opponent copy allowed without opponent scope audit");
            check(AiSupport.matchProblems(deck(id),deck("me4_en_061"),CARDS,DATA).isEmpty(),id+" Audited opponent Energy choice blocked");
        }
        var bad=GeneratedRuleBatch51Test.attack("unsupported-choice","{}",0,",\"triggers\":[{\"trigger\":\"ON_ATTACK\",\"effects\":[{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"distinct_energy_types\":true}]}]");
        var defs=new CardDatabase(List.of(CARDS.find("sv3-5_en_151").orElseThrow(),CopyAttackTest.card("danger",true)));
        var combat=new CombatCatalog(Map.of("sv3-5_en_151",DATA.pokemon("sv3-5_en_151").orElseThrow(),"danger",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(bad),List.of(),EnergyType.FIRE)),Map.of());
        check(AiSupport.opponentProblems(deck("danger"),defs,combat).isEmpty(),"Human choice wrongly blocked");
        check(AiSupport.matchProblems(deck("sv3-5_en_151"),deck("danger"),defs,combat).isEmpty(),"已验证的复制招式不同属性选能仍被拦截");
    }
    public static void main(String[] args){
        var memory=prints("memory-helix");var genome=prints("restart");check(memory.size()==3&&genome.size()==7,"Printing inventory changed; audit new variants");
        for(String id:memory)memory(id);for(String id:genome){restart(id);genome(id);copiedPayment(id);}support();
        System.out.println("MewRuleRepairTest passed: 3 Memory Helix + 7 Restart/Genome prints; optional switch, draw limits/per-instance turn reset, actual costs, AI public copied attack scoring/borrow energy, private-state invariance, stale requests and opponent-choice preflight");
        System.out.println("Memory Helix: "+memory);System.out.println("Restart / Genome: "+genome);
    }
}
