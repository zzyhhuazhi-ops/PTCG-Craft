import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch113Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("bw9_en_100");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static int damage(ServerMatch m,UUID p,UUID id){return EventTargetBindingTest.field(m).pokemonView(p,id).damage();}
    static void settle(ServerMatch m){NonPokemonBatch103Test.settle(m);}
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();}
    static ServerMatch game(EnergyProfile energy,List<AbilityDefinition> abilities){return DynamicPassiveModifierTest.fixture(NonPokemonBatch112Test.profile(NonPokemonBatch112Test.BADGE),profile(),energy,GeneratedRuleBatch8Test.hit(100,false),abilities,0,null,Map.of("basic",EnergyType.GRASS,"evolved",EnergyType.GRASS),"Solrock");}
    static ServerMatch game(){return game(EnergyProfile.fixed(EnergyType.COLORLESS,2),List.of());}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.attack=GeneratedRuleBatch8Test.hit(300,false);c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    static void stadium(ServerMatch m,UUID controller,UUID turn){if(!m.currentPlayer().equals(controller))m.endTurn(m.currentPlayer());m.playTrainer(controller,hand(m,controller,"stadium").instanceId(),null);if(!m.currentPlayer().equals(turn))m.endTurn(m.currentPlayer());}
    public static void main(String[] args){attachments();affiliation();simultaneous();healingOrder();abilityAndImmunity();ai();System.out.println("NonPokemonBatch113Test passed: both Stadium controllers/players/positions, own-hand manual/effect attachment per physical card, deck/discard/foreign hand/failed attachment exclusions, live and printed Plasma membership, Herbal Energy full-HP healing order and authority, mandatory healing continuation, Ability suffix before counters, Stadium immunity, lethal counters and four AI difficulties");}
    static void attachments(){for(UUID p:List.of(A,B))for(UUID controller:List.of(A,B))for(boolean onBench:List.of(false,true))for(String mode:List.of("manual","hand","deck","discard","foreign","removed","blocked")){
        var m=game();stadium(m,controller,p);UUID host=onBench?bench(m,p):active(m,p),sourceOwner=mode.equals("foreign")?other(p):p;var c=hand(m,sourceOwner,"energy");var source=mode.equals("deck")?EffectSourceZone.DECK:mode.equals("discard")?EffectSourceZone.DISCARD:EffectSourceZone.HAND;
        if(source!=EffectSourceZone.HAND){PairedTrainerPlayTest.zone(m,sourceOwner,"hand").remove(c);PairedTrainerPlayTest.zone(m,sourceOwner,source==EffectSourceZone.DECK?"deck":"discard").add(c);}
        if(mode.equals("removed"))m.discardStadiumByEffect(p);if(mode.equals("blocked"))EventTargetBindingTest.field(m).preventHandEnergy(other(p),p,host);
        if(mode.equals("manual"))m.attachEnergy(p,c.instanceId(),host);else m.attachEnergyByEffect(p,sourceOwner,source,c.instanceId(),p,host);settle(m);
        check(damage(m,p,host)==(Set.of("manual","hand").contains(mode)?20:0),"Frozen City wrong attachment origin/controller/position: "+p+controller+mode+onBench);
        check(EventTargetBindingTest.field(m).pokemonView(p,host).attachedEnergyIds().contains(c.instanceId())!=mode.equals("blocked"),"Attachment identity/failed attachment changed");
    }}
    static void affiliation(){for(UUID p:List.of(A,B))for(boolean onBench:List.of(false,true))for(String mode:List.of("live","disabled","removed")){
        var m=game();stadium(m,other(p),p);UUID host=onBench?bench(m,p):active(m,p);var badge=hand(m,p,"tool");m.attachToolByEffect(p,p,EffectSourceZone.HAND,badge.instanceId(),p,host);if(mode.equals("disabled"))m.disableToolEffectsByEffect(p,p,host);if(mode.equals("removed"))m.discardToolByEffect(p,p,host,badge.instanceId());m.attachEnergy(p,hand(m,p,"energy").instanceId(),host);settle(m);check(damage(m,p,host)==(mode.equals("live")?0:20),"Frozen City ignored current Badge affiliation");
    }for(UUID p:List.of(GeneratedRuleBatch8Test.A,GeneratedRuleBatch8Test.B))for(boolean onBench:List.of(false,true)){
        var m=NonPokemonBatch112Test.game(IDS.getFirst(),Set.of(PokemonFamily.TEAM_PLASMA),GeneratedRuleBatch8Test.hit(100,false));if(p.equals(GeneratedRuleBatch8Test.B))m.endTurn(GeneratedRuleBatch8Test.A);m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);UUID host=onBench?bench(m,p):active(m,p);m.attachEnergy(p,hand(m,p,"energy").instanceId(),host);settle(m);check(damage(m,p,host)==0,"Printed Plasma exemption missing");
    }}
    static void simultaneous(){for(UUID p:List.of(A,B))for(boolean special:List.of(false,true)){
        var m=game();stadium(m,other(p),p);UUID host=bench(m,p);var cards=m.privateView(p).hand().stream().filter(c->c.definitionId().equals(special?"special":"energy")).limit(special?1:2).toList();int count=cards.size();check(count==(special?1:2),"Missing multi-Energy fixture");m.attachEnergyByEffect(p,new ServerMatch.EnergyAttachmentPolicy(count,count,ServerMatch.InPlayTargetScope.OWN_FIELD,false,Set.of()),p,EffectSourceZone.HAND,cards.stream().map(CardInstance::instanceId).toList(),p,host);settle(m);check(damage(m,p,host)==20*count,"City counted energy units rather than physical cards");
    }var m=game();stadium(m,A,A);UUID host=bench(m,A);m.placeDamageCountersByEffect(A,A,host,29);int prizes=m.privateView(B).prizes().size();m.attachEnergy(A,hand(m,A,"energy").instanceId(),host);settle(m);check(m.benchPokemonView(A).stream().noneMatch(v->v.pokemonId().equals(host))&&m.privateView(B).prizes().size()==prizes-1,"Frozen City lethal counters failed KO checkpoint");}
    static void healingOrder(){for(UUID p:List.of(A,B))for(UUID controller:List.of(A,B))for(boolean offTurn:List.of(false,true))for(boolean healFirst:List.of(false,true))for(int counters:List.of(0,1,29)){
        var m=game(NonPokemonBatch2Test.DATA.energyProfile("xy3_en_103").orElseThrow(),List.of());stadium(m,controller,offTurn?other(p):p);UUID host=bench(m,p);if(counters>0)m.placeDamageCountersByEffect(m.currentPlayer(),p,host,counters);var en=hand(m,p,"special");m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,en.instanceId(),p,host);
        var q=m.triggerOrderPrompt();check(q!=null&&q.choosingPlayer().equals(m.currentPlayer())&&q.entries().size()==2,"Herbal/City ordering missing or belongs to wrong player at HP "+counters);
        var selected=q.entries().stream().filter(e->e.sourceKind()==(healFirst?EffectOrigin.Kind.ENERGY:EffectOrigin.Kind.TRAINER)).findFirst().orElseThrow();UUID chooser=q.choosingPlayer();var before=m.clientView(p);GeneratedRuleBatch8Test.reject(()->m.chooseTriggeredEffect(other(chooser),q.id(),selected.id()));check(before.equals(m.clientView(p)),"Wrong owner changed attachment reactions");m.chooseTriggeredEffect(chooser,q.id(),selected.id());settle(m);
        if(counters==29&&!healFirst){check(m.benchPokemonView(p).stream().noneMatch(v->v.pokemonId().equals(host)),"Lethal City counters should settle before subsequent Energy healing");continue;}int expected=healFirst?Math.max(0,counters*10-30)+20:Math.max(0,counters*10+20-30);check(damage(m,p,host)==expected,"Herbal order or healing before KO wrong: "+counters+" first="+healFirst);GeneratedRuleBatch8Test.reject(()->m.chooseTriggeredEffect(chooser,q.id(),selected.id()));
    }}
    static void abilityAndImmunity(){
        String effects="{\"type\":\"attach_energy\",\"target\":\"SELF\",\"source\":\"HAND\",\"amount\":1,\"minimum\":1,\"basic_energy_only\":true,\"target_scope\":\"OWN_FIELD\"},{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":30}";var ability=ResumableAbilityTest.ability("attach-heal",effects);var m=game(EnergyProfile.fixed(EnergyType.COLORLESS,2),List.of(ability));stadium(m,A,A);UUID host=active(m,A);m.placeDamageCountersByEffect(A,A,host,1);m.useAbility(A,host,"attach-heal");var q=m.effectCardPrompt(A);m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(hand(m,A,"energy").instanceId()));settle(m);check(damage(m,A,host)==20,"Ability suffix healing must complete before City reaction");
        var immune=game(EnergyProfile.fixed(EnergyType.COLORLESS,2),NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities());stadium(immune,B,A);UUID protectedHost=active(immune,A);immune.attachEnergy(A,hand(immune,A,"energy").instanceId(),protectedHost);settle(immune);check(damage(immune,A,protectedHost)==0,"New Moon failed to block City Stadium effect");
    }
    static void ai(){for(var level:AiDifficulty.values()){var m=aiGame(IDS.getFirst());UUID a=GeneratedRuleBatch8Test.A,b=GeneratedRuleBatch8Test.B;m.playTrainer(a,hand(m,a,"trainer").instanceId(),null);m.attachEnergy(a,hand(m,a,"energy").instanceId(),active(m,a));var s=MatchSession.multiplayer(m,a,b);AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Frozen City AI stalled: "+level);}}
}
