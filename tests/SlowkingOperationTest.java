import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.*;
import java.util.*;

/** Exercise the issued human/AI actions, including the declaration before any public reveal. */
public final class SlowkingOperationTest {
    static final UUID A=BattleSelectionRepairTest.A,B=BattleSelectionRepairTest.B;
    static final String BASIC="sv7_en_057",SLOW="sv7_en_058",DONOR="me4_en_061";
    static final CombatCatalog DATA=BundledCombatData.load();
    static final CardDatabase CARDS=BundledRules.loadCardDefinitions();
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ServerMatch fixture(String top){
        var defs=new ArrayList<CardDefinition>();for(String id:List.of(BASIC,SLOW,DONOR,"sv2_en_086"))defs.add(CARDS.find(id).orElseThrow());
        defs.add(CopyAttackTest.card("target",true));defs.add(CopyAttackTest.card("energy",false));
        var profiles=new HashMap<String,PokemonProfile>();for(String id:List.of(BASIC,SLOW,DONOR,"sv2_en_086"))profiles.put(id,DATA.pokemon(id).orElseThrow());
        profiles.put("target",new PokemonProfile(600,0,null,null,0,null,0,1,List.of()));
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.PSYCHIC));
        for(int seed=0;seed<200;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID p:List.of(A,B)){
                var cards=new ArrayList<CardInstance>();
                for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,
                    p.equals(A)?i<12?BASIC:i<24?SLOW:i<36?DONOR:i<40?"sv2_en_086":"energy":i<20?"target":"energy"));
                decks.add(cards);
            }
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(BASIC))||m.privateView(B).hand().stream().noneMatch(c->c.definitionId().equals("target")))continue;
            for(UUID p:List.of(A,B)){String basic=p.equals(A)?BASIC:"target";
                m.chooseOpeningActive(p,m.privateView(p).hand().stream().filter(c->c.definitionId().equals(basic)).findFirst().orElseThrow().instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);
            var evolution=m.privateView(A).hand().stream().filter(c->c.definitionId().equals(SLOW)).findFirst();if(evolution.isEmpty())continue;
            m.evolve(A,m.activePokemonView(A).pokemonId(),evolution.get().instanceId());
            m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().map(CardInstance::instanceId).toList());
            for(int n=0;n<2;n++){var e=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
                m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,e.instanceId(),A,m.activePokemonView(A).pokemonId());}
            // Test-only controlled hidden order; the AI still receives only the authenticated view.
            @SuppressWarnings("unchecked") var states=(Map<UUID,Object>)AiPracticeScenarios.field(m,"players");
            @SuppressWarnings("unchecked") var deck=(List<CardInstance>)AiPracticeScenarios.field(states.get(A),"deck");
            var card=deck.stream().filter(c->c.definitionId().equals(top)).findFirst().orElseThrow();deck.remove(card);deck.add(card);
            return m;
        }
        throw new AssertionError("actual evolved Slowking fixture");
    }
    static void submit(MatchSession s,UUID actor,MatchSession.Snapshot view,MatchSession.Option option){s.submit(actor,actor,view.revision(),option.index());}
    static void human(){
        var m=fixture(DONOR);var s=MatchSession.multiplayer(m,A,B);
        var v=s.view(A,A);String source="field:"+m.activePokemonView(A).pokemonId();
        var declaration=MatchActionMatcher.cardFaceActions(v.options(),source,true).stream()
            .filter(o->o.commandId().equals("seek-inspiration")).findFirst().orElseThrow();
        s.submit(A,A,v.revision(),declaration.index());
        var reveal=m.effectCardReveal();check(reveal!=null,"declaration did not reveal the discarded top card");
        var response=s.view(reveal.confirmingPlayer(),reveal.confirmingPlayer());
        var ack=response.options().stream().filter(o->o.type()==ActionType.ACK_REVEAL).findFirst().orElseThrow();
        s.submit(reveal.confirmingPlayer(),reveal.confirmingPlayer(),response.revision(),ack.index());
        v=s.view(A,A);
        var exposed=EffectChoicePage.all(v);
        check(exposed.stream().anyMatch(o->o.enabled()&&o.cardKeys().contains("copy-attack:metallic-hammer")),
            "human has no visible copied attack button: "+v.options());
        var copied=exposed.stream().filter(o->o.cardKeys().contains("copy-attack:metallic-hammer")).findFirst().orElseThrow();
        check(MatchActionMatcher.isAvailableAttackOption(copied),"copied attack is not recognized by the card-face panel");
        var pages=new HashSet<Integer>();var first=EffectChoicePage.from(v,0);
        for(int n=0;n<first.pages();n++)EffectChoicePage.from(v,n).options().forEach(o->pages.add(o.index()));
        check(pages.contains(copied.index()),"copy choice is lost while paginating");
        var old=v;CopyAttackTest.reject(()->s.submit(B,B,old.revision(),copied.index()));
        submit(s,A,v,copied);
        CopyAttackTest.reject(()->s.submit(A,A,old.revision(),copied.index()));
        var choice=s.view(A,A);var yes=EffectChoicePage.all(choice).stream().filter(o->o.type()==ActionType.CHOICE).findFirst().orElseThrow();
        submit(s,A,choice,yes);
        check(m.activePokemonView(B).damage()==300&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"human copied attack did not finish");
        check(m.activePokemonView(A).attachedEnergyIds().size()==2,"copied attack paid donor's Metal cost");
    }
    static void ai(AiDifficulty difficulty,String top){
        var m=fixture(top);var s=MatchSession.multiplayer(m,A,B);var used=new HashSet<String>();
        var topCard=m.privateView(A).deck().getLast();
        for(int n=0;n<80&&m.currentPlayer().equals(A)&&m.result()==null;n++){
            UUID actor=m.pendingChoicePlayer();if(actor==null)actor=m.currentPlayer();
            var v=AiPracticeScenarios.view(s,actor);var rng=m.randomTranscript();
            var d=AiPolicy.choose(v,47,used,difficulty).orElseThrow();
            check(m.randomTranscript().equals(rng),"AI decision changed match RNG");
            var option=v.snapshot().options().stream().filter(o->o.index()==d.option()).findFirst().orElseThrow();
            s.submit(actor,actor,d.revision(),d.option());var key=AiPolicy.optionalKey(option);if(!key.isEmpty())used.add(key);
        }
        check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"AI did not complete its turn: "+difficulty);
        check(m.activePokemonView(B).damage()==(top.equals(DONOR)?300:0),"wrong copied attack outcome: "+difficulty+"/"+top);
        check(m.privateView(A).discard().contains(topCard),"AI failed to discard the actual top card: "+difficulty);
    }
    static void noEligibleCopy(String top){
        var m=fixture(top);var s=MatchSession.multiplayer(m,A,B);
        var topCard=m.privateView(A).deck().getLast();
        if(top.equals("energy")){
            var energy=m.activePokemonView(A).attachedEnergyIds().getFirst();
            m.discardAttachedEnergyByEffect(A,A,m.activePokemonView(A).pokemonId(),List.of(energy));
            var low=s.view(A,A);check(low.options().stream().filter(o->o.commandId().equals("seek-inspiration")).noneMatch(MatchActionMatcher::isAvailableAttackOption),"insufficient energy exposed an attack");
            var replacement=m.privateView(A).discard().stream().filter(c->c.instanceId().equals(energy)).findFirst().orElseThrow();
            m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,replacement.instanceId(),A,m.activePokemonView(A).pokemonId());
        }
        var v=s.view(A,A);var attack=v.options().stream().filter(o->o.enabled()&&o.commandId().equals("seek-inspiration")).findFirst().orElseThrow();
        submit(s,A,v,attack);
        var reveal=m.effectCardReveal();if(reveal!=null){var ack=s.view(reveal.confirmingPlayer(),reveal.confirmingPlayer());submit(s,reveal.confirmingPlayer(),ack,ack.options().stream().filter(o->o.type()==ActionType.ACK_REVEAL).findFirst().orElseThrow());}
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.activePokemonView(B).damage()==0,"ineligible top card stalled the turn: "+top);
        check(m.privateView(A).discard().contains(topCard),"ineligible top card not discarded");
    }
    static void emptyDeckAndPrivacy(){
        var m=fixture(DONOR);var s=MatchSession.multiplayer(m,A,B);
        var before=m.randomTranscript();
        for(UUID actor:List.of(A,B)){
            String json=new com.google.gson.Gson().toJson(s.view(actor,actor));
            for(UUID owner:List.of(A,B))for(var card:m.privateView(owner).deck())check(!json.contains(card.instanceId().toString()),"hidden deck instance leaked before declaration");
        }
        check(m.randomTranscript().equals(before),"rendering options consumed randomness");
        m.drawCardsByEffect(A,A,m.privateView(A).deck().size());
        var v=s.view(A,A);var attack=v.options().stream().filter(o->o.enabled()&&o.commandId().equals("seek-inspiration")).findFirst().orElseThrow();
        submit(s,A,v,attack);
        check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice()&&m.activePokemonView(B).damage()==0,"empty deck left an unresolved copy selection");
    }
    public static void main(String[] args){
        var failures=new ArrayList<String>();
        try{human();}catch(Exception|AssertionError e){failures.add("human: "+e);e.printStackTrace();}
        try{emptyDeckAndPrivacy();}catch(Exception|AssertionError e){failures.add("empty/privacy: "+e);e.printStackTrace();}
        for(String top:List.of("energy","sv2_en_086"))try{noEligibleCopy(top);}catch(Exception|AssertionError e){failures.add(top+": "+e);e.printStackTrace();}
        for(var difficulty:AiDifficulty.values())for(String top:List.of(DONOR,"energy","sv2_en_086"))try{ai(difficulty,top);}catch(Exception|AssertionError e){failures.add(difficulty+"/"+top+": "+e);e.printStackTrace();}
        check(failures.isEmpty(),failures.toString());
        System.out.println("SlowkingOperationTest passed: human declaration/copy UI and all four AI difficulties through issued actions");
    }
}
