import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;
import dev.hua.ptcglive.rules.*;

/** A Pokemon-local prohibition changes the destination of an effect attempt, not its successful attach count. */
public final class HandEnergyLockEffectAttachmentTest {
    static final UUID A=HandSelfEvolutionEffectTest.A,B=HandSelfEvolutionEffectTest.B;
    static final String ATTACH="""
        {"type":"attach_energy","target":"OWN_ACTIVE","source":"HAND","amount":1,"minimum":1,"basic_energy_only":true,"energy_type":"WATER","target_scope":"OWN_ACTIVE","bind":"attached"}
        """.trim();
    static final String BONUS="""
        {"type":"conditional","conditions":[{"type":"BOUND_COUNT","bind":"attached","value":1}],"effects":[{"type":"draw","amount":2}]}
        """.trim();
    static void check(boolean v,String s){HandSelfEvolutionEffectTest.check(v,s);}
    static void reject(Runnable r){HandSelfEvolutionEffectTest.reject(r);}
    static CardInstance hand(ServerMatch m,String id){return HandSelfEvolutionEffectTest.hand(m,A,id);}
    static ServerMatch game(String effects){
        var watched=EnergyTriggeredAbilityTest.ability("watch","SELF",false,"NONE","{\"type\":\"draw\",\"amount\":1}");
        return HandSelfEvolutionEffectTest.fixture(true,List.of(watched),List.of(),ComposedTrainerIntegrationTest.item(effects));
    }
    static UUID lock(ServerMatch m){var id=m.activePokemonView(A).pokemonId();HandSelfEvolutionEffectTest.field(m).preventHandEnergy(B,A,id);return id;}
    static void play(ServerMatch m){m.playTrainer(A,hand(m,"component").instanceId(),null);}
    static void complete(ServerMatch m,List<UUID> ids){var q=m.effectCardPrompt(A);m.submitEffectCards(A,q.resolutionId(),q.step(),ids);if(m.hasPendingAttachmentTarget()){var target=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,target.resolutionId(),target.step(),target.targets().getFirst());}}
    public static void main(String[] args){manualAndDirect();selection(false);selection(true);distribution();invalidBulk();owner();sourceZones();paidCost();hostRestriction();System.out.println("HandEnergyLockEffectAttachmentTest passed");}
    static void manualAndDirect(){
        var m=game(ATTACH);var host=lock(m);var water=hand(m,"energy");var before=m.privateView(A);var rng=m.randomTranscript();
        check(!m.canAttachEnergy(A,water.instanceId(),host),"manual lock absent");reject(()->m.attachEnergy(A,water.instanceId(),host));check(before.equals(m.privateView(A)),"manual reject moved Energy");
        int deck=m.privateView(A).deck().size();m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,water.instanceId(),A,host);
        check(m.privateView(A).discard().contains(water)&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"direct effect did not discard");
        var special=hand(m,"special");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,special.instanceId(),A,host);
        check(m.privateView(A).discard().stream().filter(c->c.instanceId().equals(special.instanceId())).count()==1,"multi-unit Energy was not one physical card");
        check(m.privateView(A).deck().size()==deck&&m.triggerOrderPrompt()==null&&!m.energyAttached(A)&&rng.equals(m.randomTranscript()),"discard attempt fired event/quota/RNG");
        m.attachEnergy(A,hand(m,"energy").instanceId(),m.benchPokemonView(A).getFirst().pokemonId());check(m.energyAttached(A),"attempt consumed manual quota");
    }
    static void selection(boolean decline){
        var m=game((decline?ATTACH.replace("\"minimum\":1","\"minimum\":0"):ATTACH)+","+BONUS);lock(m);var card=hand(m,"component");
        check(m.canPlayTrainer(A,card.instanceId(),null),"blocked attempt removed meaningful effect");int deck=m.privateView(A).deck().size();var rng=m.randomTranscript();play(m);var q=m.effectCardPrompt(A);
        check(!q.candidates().isEmpty()&&q.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Water filter or locked candidate lost");var water=q.candidates().getFirst();var before=m.privateView(A);
        reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(water.instanceId())));reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(hand(m,"fire").instanceId())));
        check(before.equals(m.privateView(A)),"invalid response consumed energy");complete(m,decline?List.of():List.of(water.instanceId()));
        check(m.privateView(A).deck().size()==deck&&!m.hasPendingEffectChoice()&&rng.equals(m.randomTranscript()),"failed attachment counted as success or triggered draw");
        check(decline?m.privateView(A).hand().contains(water):m.privateView(A).discard().contains(water),"optional/forced attempt destination wrong");
        check(m.replayTranscript().stream().anyMatch(e->e.operation().equals("动作计数绑定：attached；数量=0")),"successful attach count must be0");
    }
    static void distribution(){
        String split="{\"type\":\"attach_energy\",\"target\":\"SELECT_OWN_POKEMON\",\"source\":\"HAND\",\"amount\":2,\"minimum\":2,\"basic_energy_only\":true,\"energy_type\":\"WATER\",\"target_scope\":\"OWN_FIELD\",\"distribution\":\"ANY\"}";
        var m=game(split);var locked=lock(m);var bench=m.benchPokemonView(A).getFirst().pokemonId();int deck=m.privateView(A).deck().size();var rng=m.randomTranscript();play(m);var q=m.effectCardPrompt(A);var cards=q.candidates().stream().limit(2).toList();
        m.submitEffectCards(A,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());var p=m.effectAttachmentPrompt(A);
        check(m.energyDistributionTargets(A,p.resolutionId(),p.step(),cards.getFirst().instanceId()).contains(locked),"locked host excluded from distribution");
        m.assignEffectEnergyDistribution(A,p.resolutionId(),p.step(),cards.getFirst().instanceId(),locked);var before=m.privateView(A);reject(()->m.confirmEffectEnergyDistribution(A,p.resolutionId(),p.step()));check(before.equals(m.privateView(A)),"partial distribution moved card");
        m.assignEffectEnergyDistribution(A,p.resolutionId(),p.step(),cards.getLast().instanceId(),bench);m.confirmEffectEnergyDistribution(A,p.resolutionId(),p.step());DeckEvolutionEffectTest.resolveTriggers(m);
        check(m.privateView(A).discard().contains(cards.getFirst())&&!m.activePokemonView(A).attachedEnergyIds().contains(cards.getFirst().instanceId()),"locked group attached");
        check(HandSelfEvolutionEffectTest.field(m).pokemonView(A,bench).attachedEnergyIds().contains(cards.getLast().instanceId())&&m.privateView(A).deck().size()==deck-1,"unlocked group did not attach/fire exactly one event");
        check(rng.equals(m.randomTranscript())&&!m.energyAttached(A),"distribution used RNG/quota");
    }
    static void invalidBulk(){
        var m=game(ATTACH);var host=lock(m);var water=hand(m,"energy");var before=m.privateView(A);var policy=new ServerMatch.EnergyAttachmentPolicy(2,2,ServerMatch.InPlayTargetScope.OWN_ACTIVE,true,Set.of());
        reject(()->m.attachEnergyByEffect(A,policy,A,EffectSourceZone.HAND,List.of(water.instanceId(),UUID.randomUUID()),A,host));
        reject(()->m.attachEnergyByEffect(A,policy,A,EffectSourceZone.HAND,List.of(water.instanceId(),water.instanceId()),A,host));check(before.equals(m.privateView(A)),"invalid bulk discarded valid prefix");
    }
    @SuppressWarnings("unchecked") static List<CardInstance> mutableHand(ServerMatch m,UUID owner){try{var f=ServerMatch.class.getDeclaredMethod("state",UUID.class);f.setAccessible(true);Object s=f.invoke(m,owner);var h=s.getClass().getDeclaredField("hand");h.setAccessible(true);return (List<CardInstance>)h.get(s);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void owner(){
        var m=game(ATTACH);var host=lock(m);var foreign=HandSelfEvolutionEffectTest.hand(m,B,"energy");
        // Explicit synthetic control fixture; move one real physical card, do not clone it or claim a supported control-changing card.
        mutableHand(m,B).remove(foreign);mutableHand(m,A).add(foreign);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,foreign.instanceId(),A,host);
        check(m.privateView(B).discard().contains(foreign)&&!m.privateView(A).discard().contains(foreign),"blocked Energy must return to actual owner discard");
    }
    static void sourceZones(){
        var m=game(ATTACH);var host=lock(m);var water=hand(m,"energy");m.discardHandCardsByEffect(A,A,List.of(water.instanceId()));m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,water.instanceId(),A,host);
        check(m.activePokemonView(A).attachedEnergyIds().contains(water.instanceId()),"Pokemon hand lock blocked discard source");
        var deckEnergy=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,deckEnergy.instanceId(),A,host);
        check(m.activePokemonView(A).attachedEnergyIds().contains(deckEnergy.instanceId()),"Pokemon hand lock blocked deck source");
    }
    static void paidCost(){
        String cost="{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"card_category\":\"ENERGY\",\"as_cost\":true,\"requires_effect\":true}";
        var m=game(cost+","+ATTACH+","+BONUS);lock(m);var paid=hand(m,"fire");int deck=m.privateView(A).deck().size();
        check(m.canPlayTrainer(A,hand(m,"component").instanceId(),null),"requires_effect rejected meaningful discard attempt");play(m);var costPrompt=m.effectCardPrompt(A);
        m.submitEffectCards(A,costPrompt.resolutionId(),costPrompt.step(),List.of(paid.instanceId()));var water=m.effectCardPrompt(A).candidates().getFirst();complete(m,List.of(water.instanceId()));
        check(m.privateView(A).discard().contains(paid)&&m.privateView(A).discard().contains(water)&&m.privateView(A).deck().size()==deck,"paid cost refunded or failed attempt counted as attachment");
    }
    static void hostRestriction(){
        var m=SpecialEnergyAttachmentTest.fixture(SpecialEnergyAttachmentTest.restricted());UUID owner=SpecialEnergyAttachmentTest.A,opponent=SpecialEnergyAttachmentTest.B,host=m.activePokemonView(owner).pokemonId();
        HandSelfEvolutionEffectTest.field(m).preventHandEnergy(opponent,owner,host);var special=SpecialEnergyAttachmentTest.card(m,"special");var before=m.privateView(owner);
        reject(()->m.attachEnergyByEffect(owner,owner,EffectSourceZone.HAND,special.instanceId(),owner,host));
        check(before.equals(m.privateView(owner)),"Pokemon lock bypassed independent host restriction or discarded illegal candidate");
    }

}
