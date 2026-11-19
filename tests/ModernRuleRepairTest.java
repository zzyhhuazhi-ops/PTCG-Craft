import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Bundled card programs on controlled public boards; no client or full-game claim. */
public final class ModernRuleRepairTest {
    static final UUID A=new UUID(0,31601),B=new UUID(0,31602),WATCH=new UUID(0,31603);
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable op){CopyAttackTest.reject(op);}
    static PokemonProfile actual(String id){return DATA.pokemon(id).orElseThrow();}
    static AbilityDefinition ability(String id){return actual(id).abilities().getFirst();}
    static AttackDefinition attack(String id){return actual(id).attacks().getFirst();}
    static PokemonProfile profile(String from,List<AttackDefinition> attacks,List<AbilityDefinition> abilities,EnergyType resistance) {
        return new PokemonProfile(300,0,from,null,0,resistance,resistance==null?0:30,1,attacks,abilities,EnergyType.FIGHTING);
    }
    static ServerMatch game(List<AttackDefinition> attacks,List<AbilityDefinition> abilities,EnergyType energy,EnergyType resistance) {
        var defs=new ArrayList<CardDefinition>();
        for(String id:List.of("own","other","evolved","energy"))defs.add(new CardDefinition(id,id,
            id.equals("energy")?CardCategory.ENERGY:CardCategory.POKEMON,TrainerCategory.NONE,"I",Set.of("international"),
            id.equals("own")||id.equals("other"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var combat=new CombatCatalog(Map.of("own",profile(null,attacks,abilities,null),
            "other",profile(null,List.of(),List.of(),resistance),"evolved",profile("other",List.of(),List.of(),resistance)),Map.of("energy",energy));
        for(int seed=0;seed<1000;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,
                i<24?(owner.equals(A)?"own":"other"):i<30?"evolved":"energy"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("fixture");
    }
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static void pay(ServerMatch m,int amount){for(int i=0;i<amount;i++){
        var energy=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,energy.instanceId(),A,active(m,A));
    }}
    static void evolveOpponent(ServerMatch m,UUID target) {
        m.endTurn(A);
        while(m.privateView(B).hand().stream().noneMatch(c->c.definitionId().equals("evolved")))m.drawCardsByEffect(A,B,1);
        var card=m.privateView(B).hand().stream().filter(c->c.definitionId().equals("evolved")).findFirst().orElseThrow();
        m.evolveByEffect(B,target,card.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        m.endTurn(B);
    }
    public static void main(String[] args){switchOwnership();devolution();resistance();reorder();greedy();missingPrinting();
        System.out.println("ModernRuleRepairTest passed: actual Samurott/Archeops/Gothorita/Boldore/Hydreigon/Seismitoad programs; authority, stale choices, short decks, privacy, actual attacker and KO cause");}
    static void switchOwnership() {
        var a=ability("rsv10-5_en_023");ActivatedAbilityRules.validate(a);
        var m=game(List.of(),List.of(a),EnergyType.WATER,null);UUID own=active(m,A),other=active(m,B),next=bench(m,A),opponent=bench(m,B);
        m.useAbility(A,own,a.id());var p=m.effectPokemonPrompt(A);
        check(p!=null&&p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(next),"own switch target");
        reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),next));
        m.chooseEffectPokemon(A,p.resolutionId(),p.step(),next);var q=m.effectPokemonPrompt(B);
        check(q!=null&&m.pendingChoicePlayer().equals(B)&&active(m,B).equals(other),"opponent must choose its replacement");
        reject(()->m.chooseEffectPokemon(A,q.resolutionId(),q.step(),opponent));
        m.chooseEffectPokemon(B,q.resolutionId(),q.step(),opponent);
        reject(()->m.chooseEffectPokemon(B,q.resolutionId(),q.step(),opponent));
        check(active(m,A).equals(next)&&active(m,B).equals(opponent)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"switch continuation");
        check(!m.canUseAbility(A,own,a.id()),"same Samurott used twice");
        var empty=game(List.of(),List.of(a),EnergyType.WATER,null);
        empty.movePokemonByEffect(A,A,bench(empty,A),LeavePlayDestination.HAND);
        check(!empty.canUseAbility(A,active(empty,A),a.id()),"cannot pay own switch with no bench");
    }
    static void devolution() {
        var a=ability("rsv10-5_en_051");ActivatedAbilityRules.validate(a);
        var m=game(List.of(),List.of(a),EnergyType.FIGHTING,null);UUID source=active(m,A),target=bench(m,B);
        check(!m.canUseAbility(A,source,a.id()),"no evolved target should not be usable");
        evolveOpponent(m,target);m.placeDamageCountersByEffect(A,B,target,3);
        check(!m.canUseAbility(A,bench(m,A),a.id()),"bench Archeops used active-only Ability");
        int hand=m.privateView(B).hand().size();m.useAbility(A,source,a.id());var p=m.effectPokemonPrompt(A);
        check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(target),"basic target included");
        reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,B)));
        m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);
        check(m.benchPokemonView(B).getFirst().topCardId().equals("other")&&m.benchPokemonView(B).getFirst().damage()==30&&m.privateView(B).hand().size()==hand+1,"devolution lost cards/damage");
        check(!m.canUseAbility(A,source,a.id()),"Archeops reused without an evolved target");
        check(!m.hasPendingEffectChoice(),"devolution stalled");
    }
    static void resistance() {
        var a=attack("rsv10-5_en_047");AttackProgramRules.validate(a);
        for(EnergyType r:Arrays.asList(null,EnergyType.FIGHTING,EnergyType.PSYCHIC)){
            var m=game(List.of(a),List.of(),EnergyType.FIGHTING,r);pay(m,1);m.attack(A,a.id());
            check(m.activePokemonView(B).damage()==(r==EnergyType.FIGHTING?50:30),"Smack Down conditional bonus then resistance");
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"damage continuation");
        }
    }
    static void reorder() {
        var a=attack("rsv10-5_en_042");AttackProgramRules.validate(a);
        for(int size:List.of(2,8)) {
            var m=game(List.of(a),List.of(),EnergyType.PSYCHIC,null);pay(m,1);
            m.drawCardsByEffect(A,B,m.privateView(B).deck().size()-size);
            var before=m.privateView(B).deck();var rng=m.randomTranscript();var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
            m.attack(A,a.id());var p=m.effectCardPrompt(A);
            check(p!=null&&p.candidates().size()==Math.min(5,size),"top window/short deck");
            var selected=new ArrayList<>(p.candidates().stream().map(CardInstance::instanceId).toList());Collections.reverse(selected);
            var expected=new ArrayList<>(before.subList(0,before.size()-selected.size()));var top=new ArrayList<>(selected);Collections.reverse(top);
            expected.addAll(top.stream().map(id->before.stream().filter(c->c.instanceId().equals(id)).findFirst().orElseThrow()).toList());
            for(UUID viewer:List.of(B,WATCH))check(session.view(viewer,viewer.equals(B)?B:null).game().effectSelection()==null,"private deck inspection exposed");
            reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),selected));
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),selected.subList(0,1)));
            m.submitEffectCards(A,p.resolutionId(),p.step(),selected);
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),selected));
            // Opponent draws the new top as its turn starts.
            var drawn=expected.removeLast();check(m.privateView(B).deck().equals(expected)&&m.privateView(B).hand().contains(drawn),"reorder and next draw order");
            check(rng.equals(m.randomTranscript())&&!m.hasPendingEffectChoice(),"sorting shuffled or stalled");
        }
    }
    static void greedy() {
        var a=ability("rsv10-5_en_067");TriggeredAbilityRules.validate(a);
        var hit=attack("rsv10-5_en_067");AttackProgramRules.validate(hit);
        for(boolean evolved:List.of(false,true)) {
            var m=game(List.of(hit),List.of(a),EnergyType.DARKNESS,null);pay(m,5);
            if(evolved)evolveOpponent(m,active(m,B));m.placeDamageCountersByEffect(A,B,active(m,B),10);
            m.attack(A,hit.id());DamageTriggeredAbilityTest.finishTriggers(m);
            check(m.phase()==MatchPhase.PRIZE_SELECTION,"Hydreigon attack KO");
            int prizes=evolved?1:2;for(int i=0;i<prizes;i++)m.takePrizeAt(A,0);
            check(m.privateView(A).prizes().size()==6-prizes&&m.phase()!=MatchPhase.PRIZE_SELECTION,"Greedy Eater must award exactly one bonus on a Basic, not stack bench copies");
        }
        var counters=DamageTriggeredAbilityTest.attack(0,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]");
        var m=game(List.of(counters),List.of(a),EnergyType.DARKNESS,null);m.attack(A,counters.id());DamageTriggeredAbilityTest.finishTriggers(m);m.takePrizeAt(A,0);
        check(m.phase()!=MatchPhase.PRIZE_SELECTION&&m.privateView(A).prizes().size()==5,"counters mistaken for attack damage");
        var policy=(KnockoutTriggerPolicy)a.eventPolicy();UUID source=active(m,A),other=bench(m,A),subject=bench(m,B);
        check(!policy.matches(A,other,B,subject,false,true,new EffectTargetResolution.PokemonTarget(A,source)),"another attacker triggered bench Hydreigon");
    }
    static void missingPrinting() {
        var db=BundledRules.loadCardDefinitions();var p=actual("zsv10-5_en_021");
        check(db.find("zsv10-5_en_021").orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"missing ordinary printing");
        check(p.equals(actual("zsv10-5_en_105")),"same printed card must reuse identical combat data");
        for(var a:p.attacks())AttackProgramRules.validate(a);
        var m=game(p.attacks(),List.of(),EnergyType.WATER,null);pay(m,3);m.attack(A,p.attacks().getFirst().id());
        check(m.activePokemonView(B).damage()==140&&!m.hasPendingEffectChoice(),"Round must count both own Round users");
    }
}
