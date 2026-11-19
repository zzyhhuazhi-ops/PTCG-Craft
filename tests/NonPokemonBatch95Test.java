import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch95Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm12_en_187");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static void play(ServerMatch m,UUID p,String id){m.playTrainer(p,hand(m,p,id).instanceId(),null);}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.MASTER);}
    static ServerMatch game(TrainerProfile replacement,boolean prism,boolean watch){
        var ids=List.of("basic","basic","basic","basic","energy","tool","trainer","alt","stadium");var defs=new ArrayList<CardDefinition>();
        for(String id:new LinkedHashSet<>(ids))defs.add(new CardDefinition(id,id.equals("alt")?"trainer":id,id.equals("basic")?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,Set.of("trainer","alt","stadium").contains(id)?TrainerCategory.STADIUM:id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,prism&&id.equals("stadium")?Map.of("棱镜之星",1):Map.of(),EffectSupport.SUPPORTED,null,Set.of(),Set.of()));
        var ability=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"stadium-watch\",\"nameZh\":\"Stadium watch\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\"ON_CARD_PLAYED\",\"player\":\"SELF\",\"card\":\"STADIUM\",\"optional\":false,\"usage\":\"NONE\"},\"triggers\":[{\"trigger\":\"ON_CARD_PLAYED\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]}").getAsJsonObject(),EffectRegistry.standard());
        var tool=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"target\":\"SELF\",\"amount\":30}");
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,1,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(100,false)),watch?List.of(ability):List.of(),EnergyType.GRASS)),Map.of("energy",EnergyType.GRASS),Map.of("trainer",profile(IDS.getFirst()),"alt",profile(IDS.getFirst()),"stadium",replacement,"tool",tool));
        for(int seed=0;seed<100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids.get(i%ids.size())));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,25);m.drawCardsByEffect(A,B,25);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->ids.stream().allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id)))))return m;
        }throw new AssertionError("Swell fixture unavailable");
    }
    static ServerMatch ready(UUID actor,TrainerProfile replacement,boolean prism,boolean watch){var m=game(replacement,prism,watch);UUID owner=other(actor);if(owner.equals(B))m.endTurn(A);play(m,owner,"trainer");settle(m);m.endTurn(owner);return m;}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.attack=GeneratedRuleBatch8Test.hit(200,false);c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){ownersAndLimits();noTransientRules();directRemoval();playedEvent();ai();schema();System.out.println("NonPokemonBatch95Test passed: both owners, same-name prints, Stadium play count, old/new discard order and physical owners, Prism replacement, no transient bench shrink/Tool suppression/status cure, direct removal and play listeners, all AI difficulties");}
    static void ownersAndLimits(){for(UUID p:List.of(A,B))for(boolean prism:List.of(false,true)){
        var m=ready(p,DynamicPassiveModifierTest.plainStadium(),prism,false);var old=m.clientView(p).stadium();var incoming=hand(m,p,"stadium");check(!m.canPlayTrainer(p,hand(m,p,"alt").instanceId(),null),"Same-name different printing allowed");var before=m.clientView(p);GeneratedRuleBatch8Test.reject(()->play(m,p,"alt"));check(before.equals(m.clientView(p)),"Rejected same-name play changed field");
        var foreign=hand(m,other(p),"stadium");PairedTrainerPlayTest.zone(m,other(p),"hand").remove(foreign);PairedTrainerPlayTest.zone(m,p,"hand").add(foreign);m.playTrainer(p,foreign.instanceId(),null);settle(m);
        check(m.clientView(p).stadium()==null&&m.privateView(other(p)).discard().contains(old)&&(prism?m.privateView(other(p)).lostZone():m.privateView(other(p)).discard()).contains(foreign)&&!m.privateView(p).discard().contains(foreign),"Discarded Stadium ownership/Prism destination incorrect");
        if(!prism){var discard=m.privateView(other(p)).discard();check(discard.indexOf(old)<discard.indexOf(foreign),"New Stadium discarded before Swell");}
        check(!m.canPlayTrainer(p,incoming.instanceId(),null),"Discarded replacement failed to consume Stadium play");GeneratedRuleBatch8Test.reject(()->m.playTrainer(other(p),hand(m,other(p),"stadium").instanceId(),null));
        m.endTurn(p);m.endTurn(other(p));m.playTrainer(p,incoming.instanceId(),null);check(m.clientView(p).stadium().equals(incoming),"Swell continued affecting later turns");
    }}
    static void noTransientRules(){for(UUID p:List.of(A,B)){
        var m=ready(p,profile("swsh9_en_137"),false,false);while(m.benchPokemonView(p).size()<5)m.playBasicPokemonToBench(p,hand(m,p,"basic").instanceId());play(m,p,"stadium");check(m.benchPokemonView(p).size()==5&&!m.hasPendingEffectChoice()&&m.clientView(p).stadium()==null,"Discarded Collapsed Stadium shrank Bench");
        var n=ready(p,profile("sv6_en_153"),false,false);UUID host=n.activePokemonView(p).pokemonId();n.playTrainer(p,hand(n,p,"tool").instanceId(),host);n.placeDamageCountersByEffect(p,p,host,31);play(n,p,"stadium");check(n.phase()==MatchPhase.TURN&&n.activePokemonView(p).pokemonId().equals(host)&&n.activePokemonView(p).hitPoints()==330&&n.activePokemonView(p).damage()==310,"Discarded Jamming Tower temporarily disabled HP Tool");
        var f=ready(p,profile("sv6_en_149"),false,false);f.attachEnergy(p,hand(f,p,"energy").instanceId(),f.activePokemonView(p).pokemonId());f.applySpecialConditionByEffect(p,p,SpecialCondition.POISONED);play(f,p,"stadium");check(f.activePokemonView(p).conditions().contains(SpecialCondition.POISONED),"Discarded Festival Grounds cured conditions");
        var action=ready(p,profile("sm11_en_197"),false,false);play(action,p,"stadium");check(!action.canActivateStadium(p),"Discarded Stadium exposes activation");
    }}
    static void directRemoval(){for(UUID p:List.of(A,B)){var m=ready(p,DynamicPassiveModifierTest.plainStadium(),false,false);m.discardStadiumByEffect(p);var next=hand(m,p,"stadium");m.playTrainer(p,next.instanceId(),null);check(m.clientView(p).stadium().equals(next),"Direct removal left replacement trap");}}
    static void playedEvent(){for(UUID p:List.of(A,B)){var m=ready(p,DynamicPassiveModifierTest.plainStadium(),false,true);int held=m.privateView(p).hand().size(),watchers=m.benchPokemonView(p).size()+1;play(m,p,"stadium");settle(m);check(m.privateView(p).hand().size()==held-1+watchers,"Discarded replacement lost/doubled hand-play event");}}
    static void ai(){for(var d:AiDifficulty.values()){var m=ready(A,DynamicPassiveModifierTest.plainStadium(),false,false);var session=MatchSession.multiplayer(m,A,B);var view=session.view(A,A);var action=view.options().stream().filter(o->o.type()==ActionType.TRAINER&&o.labelZh().contains("stadium")).findFirst().orElseThrow();session.submit(A,A,view.revision(),action.index());AiCardOperationTest.settle(session,d);check(m.clientView(A).stadium()==null&&m.phase()==MatchPhase.TURN,"AI Stadium replacement stalled "+d);}}
    static void schema(){String marker="{\"type\":\"discard_replacing_stadium\",\"target\":\"SELF\"}";for(String category:List.of("TOOL","ITEM","SUPPORTER"))GeneratedRuleBatch8Test.reject(()->{var invalid=DynamicPassiveModifierTest.trainer(category,marker);TrainerProgramRules.validate(invalid.effectProgram());});var e=EffectRegistry.standard().parse(JsonParser.parseString(marker).getAsJsonObject());GeneratedRuleBatch8Test.reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),EffectTarget.ALL_OWN_POKEMON,Map.of(),List.of(),List.of())));GeneratedRuleBatch8Test.reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),EffectTarget.SELF,Map.of("amount","1"),List.of(),List.of())));}
}
