import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Engine contracts use explicit synthetic printing metadata; actual card data remains a separate gate. */
public final class CedricQuizTest {
    static final UUID A=NonPokemonBatch127Test.A,B=NonPokemonBatch127Test.B;
    static final PrintedHeight SMALL=new PrintedHeight(PrintedHeight.Unit.INCH,28);
    static final PrintedHeight LARGE=new PrintedHeight(PrintedHeight.Unit.INCH,36);
    static void check(boolean ok,String why){NonPokemonBatch127Test.check(ok,why);}
    static void reject(Runnable r){NonPokemonBatch127Test.reject(r);}
    static ServerMatch game(){
        var c=new GeneratedRuleBatch28Test.Config();
        c.trainer=WillCoinMatchTest.trainer("{\"type\":\"quiz_select_printed_height\",\"target\":\"HAND\"}");
        c.attack=GeneratedRuleBatch8Test.hit(300,false);c.defenderAttack=c.attack;
        var m=GeneratedRuleBatch28Test.fixture(c);
        install(m,Map.of("attacker",SMALL,"new",LARGE,"defender",SMALL));return m;
    }
    static CardDatabase database(ServerMatch m){try{var f=ServerMatch.class.getDeclaredField("definitions");f.setAccessible(true);return (CardDatabase)f.get(m);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void install(ServerMatch m,Map<String,PrintedHeight> heights){replace(m,new CardDatabase(database(m).all(),heights));}
    static void replace(ServerMatch m,CardDatabase db){try{var f=ServerMatch.class.getDeclaredField("definitions");f.setAccessible(true);f.set(m,db);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static CardInstance select(ServerMatch m,UUID player,String key){return NonPokemonBatch127Test.select(m,player,key);}
    static void choose(ServerMatch m,UUID player,int option){NonPokemonBatch127Test.choose(m,player,option);}
    static void answer(ServerMatch m,UUID player,String label){
        int pages=m.effectChoicePrompt(player).optionsZh().size();
        for(int page=0;page<pages;page++){
            choose(m,player,page);var options=m.effectChoicePrompt(player).optionsZh();
            for(int i=0;i<options.size()-1;i++)if(options.get(i).endsWith("回答："+label)){choose(m,player,i);return;}
            choose(m,player,options.size()-1);
        }
        throw new AssertionError("Missing public height answer: "+label);
    }
    public static void main(String[] args){outcomes();privacy();authority();eligibility();shortDeck();unitsAndPages();ai();schema();System.out.println("CedricQuizTest passed: printing-specific height, private choice/public name, opponent-only paged answers, draw/return, invalid request atomicity, no species fallback, short decks, units and all four AI difficulties. Bundled height coverage is not claimed.");}
    static void outcomes(){for(UUID owner:List.of(A,B))for(boolean correct:List.of(false,true)){
        var m=game();if(owner.equals(B))m.endTurn(A);UUID other=NonPokemonBatch127Test.other(owner);
        int own=m.privateView(owner).hand().size(),opp=m.privateView(other).hand().size();
        var card=select(m,owner,owner.equals(A)?"attacker":"defender");
        check(m.pendingChoicePlayer().equals(other),"Owner answered own quiz");
        check(m.privateView(owner).hand().size()==own-2&&!m.privateView(owner).hand().contains(card),"Secret Pokemon not set aside");
        answer(m,other,(correct?SMALL:LARGE).labelZh());
        check(!m.hasPendingEffectChoice()&&m.privateView(owner).hand().contains(card),"Height quiz lost its card or continuation");
        check(m.privateView(owner).hand().size()==own-1+(correct?0:3)&&m.privateView(other).hand().size()==opp+(correct?3:0),"Wrong player/draw count");
        check(!m.mayPlaySupporter(owner),"Height quiz did not consume Supporter use");
    }}
    static void privacy(){List<String> labels=null;for(String selected:List.of("attacker","new")){
        var m=game();var original=database(m);var defs=new ArrayList<CardDefinition>();
        for(var d:original.all())defs.add(d.id().equals("new")?new CardDefinition(d.id(),"attacker",d.category(),d.trainerCategory(),d.regulationMark(),d.regions(),d.basicPokemon(),d.basicEnergy(),d.copyLimit(),d.specialDeckLimits(),d.effectSupport(),d.unsupportedReasonZh(),d.pokemonFamilies(),d.pokemonTypes(),d.battleStyles()):d);
        replace(m,new CardDatabase(defs,Map.of("attacker",SMALL,"new",LARGE,"defender",SMALL)));
        int replay=m.replayTranscript().size();var card=select(m,A,selected);
        var q=m.effectChoicePrompt(B);if(labels==null)labels=q.optionsZh();else check(labels.equals(q.optionsZh()),"Hidden printing changed answer universe");
        check(q.optionsZh().stream().allMatch(s->s.contains("已公布宝可梦“attacker”")),"Wrong public announcement");
        check(m.replayTranscript().subList(replay,m.replayTranscript().size()).stream().noneMatch(e->e.operation().contains(card.instanceId().toString())||e.operation().contains("INCH:")),"Quiz leaked private card or height");
        var session=MatchSession.multiplayer(m,A,B);check(session.view(A,A).options().stream().noneMatch(o->o.labelZh().contains("选择身高范围")),"Owner can submit opponent answer");
        answer(m,B,SMALL.labelZh());
    }}
    static void authority(){for(int stage:List.of(0,1)){
        var m=game();var card=select(m,A,"attacker");if(stage==1)choose(m,B,0);
        var q=m.effectChoicePrompt(B);var own=m.privateView(A);var opp=m.privateView(B);
        reject(()->m.effectChoicePrompt(A));reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),0));
        reject(()->m.chooseEffectOption(B,UUID.randomUUID(),q.step(),0));reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step()+1,0));
        reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),-1));reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),q.optionsZh().size()));reject(()->m.endTurn(A));
        check(own.equals(m.privateView(A))&&opp.equals(m.privateView(B)),"Invalid guess changed zones");
        choose(m,B,0);reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),0));m.concede(B);
        check(!m.hasPendingEffectChoice()&&m.privateView(A).hand().stream().filter(c->c.instanceId().equals(card.instanceId())).count()==1,"Concede lost/duplicated secret Pokemon");
    }}
    static void eligibility(){var m=game();install(m,Map.of());var source=NonPokemonBatch127Test.hand(m,A,"trainer");var before=m.privateView(A);
        check(!m.canPlayTrainer(A,source.instanceId(),null),"Missing metadata substituted species height");reject(()->m.playTrainer(A,source.instanceId(),null));check(before.equals(m.privateView(A)),"Ineligible quiz consumed cards");
        var n=game();WillCoinMatchTest.play(n,A);check(n.effectCardPrompt(A).candidates().stream().allMatch(c->database(n).printedHeight(c.definitionId()).isPresent()),"Unprinted/unknown height offered");
    }
    static void shortDeck(){for(boolean correct:List.of(false,true))for(int count:List.of(0,2)){
        var m=game();UUID drawer=correct?B:A;var deck=NonPokemonBatch127Test.zone(m,drawer,"deck");var removed=new ArrayList<>(deck.subList(count,deck.size()));deck.removeAll(removed);NonPokemonBatch127Test.zone(m,drawer,"discard").addAll(removed);
        int before=m.privateView(drawer).hand().size();select(m,A,"attacker");answer(m,B,(correct?SMALL:LARGE).labelZh());
        check(m.privateView(drawer).hand().size()==before+count-(correct?0:1)&&m.phase()==MatchPhase.TURN,"Short-deck effect draw incorrectly lost match");
    }}
    static void unitsAndPages(){
        var m=game();var defs=new ArrayList<>(database(m).all());var heights=new LinkedHashMap<String,PrintedHeight>();heights.put("attacker",LARGE);heights.put("new",new PrintedHeight(PrintedHeight.Unit.CM,91));
        for(int i=1;i<=95;i++){String id="height-"+i;defs.add(new CardDefinition(id,id,CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,4,Map.of(),EffectSupport.SUPPORTED,null));heights.put(id,new PrintedHeight(PrintedHeight.Unit.CM,i));}
        replace(m,new CardDatabase(defs,heights));select(m,A,"attacker");check(m.effectChoicePrompt(B).optionsZh().size()==3,"Height answers not paged");
        int before=m.privateView(A).hand().size();answer(m,B,"0.91米");check(m.privateView(A).hand().size()==before+4,"Approximate species/unit conversion accepted as exact printed answer");
        check(new PrintedHeight(PrintedHeight.Unit.CM,70).labelZh().equals("0.7米"),"Metric label rounding");
    }
    static void ai(){for(var level:AiDifficulty.values()){var m=game();WillCoinMatchTest.play(m,A);var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"Height quiz AI stalled "+level);}}
    static void schema(){reject(()->new PrintedHeight(PrintedHeight.Unit.CM,0));reject(()->new CardDatabase(database(game()).all(),Map.of("trainer",SMALL)));
        reject(()->CedricQuiz.validate(new EffectDefinition(CedricQuiz.SELECT,EffectTarget.DECK,Map.of(),List.of(),List.of())));
        reject(()->CedricQuiz.validate(new EffectDefinition(CedricQuiz.SELECT,EffectTarget.HAND,Map.of("amount","3"),List.of(),List.of())));
    }
}
