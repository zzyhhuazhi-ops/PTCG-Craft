import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Printed Gengar / Gengar ex: every physical hand attachment, including effects, is one event. */
public final class GnawingCurseTest {
    static final UUID A=GeneratedRuleBatch28Test.A,B=GeneratedRuleBatch28Test.B;
    static final Map<String,PokemonProfile> PROFILES=profiles();
    static final String ATTACH="{\"type\":\"attach_energy\",\"target\":\"OWN_ACTIVE\",\"source\":\"HAND\",\"amount\":2,\"minimum\":2,\"target_scope\":\"OWN_ACTIVE\"}";
    static Map<String,PokemonProfile> profiles(){try{var rows=new LinkedHashMap<String,PokemonProfile>();for(String line:Files.readAllLines(Path.of("outputs/pokemon-gnawing-curse-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();rows.put(r.get("id").getAsString(),BundledCombatData.pokemon(r.getAsJsonObject("conversion").getAsJsonObject("combatEntry"),EffectRegistry.standard()));}return rows;}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static AbilityDefinition ability(String id){return PROFILES.get(id).abilities().getFirst();}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static int damage(ServerMatch m,UUID p,UUID host){return EventTargetBindingTest.field(m).pokemonView(p,host).damage();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static CardInstance hand(ServerMatch m,UUID p){return m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();}
    static CardInstance source(ServerMatch m,UUID owner){var c=NonPokemonBatch133Test.put(m,owner,"source");m.playBasicPokemonToBench(owner,c.instanceId());return c;}
    static ServerMatch game(String id,UUID owner,boolean special){var c=new GeneratedRuleBatch28Test.Config();c.source=ability(id);c.trainer=ComposedTrainerIntegrationTest.item(ATTACH);if(special)c.specialEnergy=EnergyProfile.fixed(EnergyType.COLORLESS,3);var m=GeneratedRuleBatch28Test.fixture(c);if(owner.equals(B))m.endTurn(A);source(m,owner);m.endTurn(owner);return m;}
    static void settle(ServerMatch m){NonPokemonBatch103Test.settle(m);}
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    public static void main(String[] args){attachments();trainer();filters();stackAndSuppress();attackKnockout();ai();System.out.println("GnawingCurseTest passed: 4 source profiles, both seats, manual/Trainer/attack attachments, one event per physical multi-unit card, hand ownership, deck/discard/move exclusions, stacking/suppression, damage before attacker KO and 32 four-tier AI matches");}
    static void attachments(){for(String id:PROFILES.keySet())for(UUID owner:List.of(A,B))for(boolean bench:List.of(false,true))for(boolean special:List.of(false,true))for(boolean manual:List.of(false,true)){
        var m=game(id,owner,special);UUID actor=other(owner),target=bench?m.benchPokemonView(actor).getFirst().pokemonId():active(m,actor);var e=hand(m,actor);
        if(manual)m.attachEnergy(actor,e.instanceId(),target);else m.attachEnergyByEffect(actor,actor,EffectSourceZone.HAND,e.instanceId(),actor,target);settle(m);
        check(damage(m,actor,target)==20,"Missed/manual-only or counted energy units rather than cards: "+id);
        check(m.energyAttached(actor)==manual,"Effect attachment consumed manual attachment");
    }}
    static void trainer(){for(String id:PROFILES.keySet())for(UUID owner:List.of(A,B)){
        var m=game(id,owner,false);UUID actor=other(owner);var selected=m.privateView(actor).hand().stream().filter(c->c.definitionId().equals("energy")).limit(2).toList();
        var trainer=m.privateView(actor).hand().stream().filter(c->c.definitionId().equals("trainer")).findFirst().orElseThrow();m.playTrainer(actor,trainer.instanceId(),null);choose(m,actor,selected);settle(m);
        check(m.activePokemonView(actor).damage()==40&&m.currentPlayer().equals(actor)&&!m.energyAttached(actor),"Trainer attachment did not produce two deferred events");
    }}
    static void filters(){for(String id:PROFILES.keySet())for(UUID owner:List.of(A,B))for(String origin:List.of("DECK","DISCARD","OTHER_HAND","OWN_FIELD","MOVE")){
        var m=game(id,owner,false);UUID actor=other(owner),target=active(m,actor);CardInstance energy;
        switch(origin){
            case "DECK"->{energy=m.privateView(actor).deck().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergyByEffect(actor,actor,EffectSourceZone.DECK,energy.instanceId(),actor,target);}
            case "DISCARD"->{energy=hand(m,actor);m.discardHandCardsByEffect(actor,actor,List.of(energy.instanceId()));m.attachEnergyByEffect(actor,actor,EffectSourceZone.DISCARD,energy.instanceId(),actor,target);}
            case "OTHER_HAND"->{energy=hand(m,owner);m.attachEnergyByEffect(actor,owner,EffectSourceZone.HAND,energy.instanceId(),actor,target);}
            case "OWN_FIELD"->{energy=hand(m,owner);m.attachEnergyByEffect(actor,owner,EffectSourceZone.HAND,energy.instanceId(),owner,active(m,owner));}
            case "MOVE"->{energy=hand(m,actor);UUID bench=m.benchPokemonView(actor).getFirst().pokemonId();m.attachEnergyByEffect(actor,actor,EffectSourceZone.HAND,energy.instanceId(),actor,bench);settle(m);m.moveAttachedEnergyByEffect(actor,actor,bench,actor,target,List.of(energy.instanceId()));}
        }
        settle(m);check(m.activePokemonView(actor).damage()==0&&m.activePokemonView(owner).damage()==0,"Wrong attachment source or transfer triggered Curse: "+origin);
    }}
    static void stackAndSuppress(){for(String id:PROFILES.keySet())for(UUID owner:List.of(A,B))for(boolean disabled:List.of(false,true)){
        var m=game(id,owner,false);UUID actor=other(owner);m.endTurn(actor);source(m,owner);
        if(disabled)for(var host:m.benchPokemonView(owner))if(host.topCardId().equals("source"))m.disableAbilityEffectsByEffect(owner,owner,host.pokemonId());
        m.endTurn(owner);m.attachEnergy(actor,hand(m,actor).instanceId(),active(m,actor));settle(m);check(m.activePokemonView(actor).damage()==(disabled?0:40),"Copies failed to stack or suppressed Ability triggered");
    }}
    static void attackKnockout(){for(String id:PROFILES.keySet()){
        var c=new GeneratedRuleBatch28Test.Config();c.ownHp=100;c.source=ability(id);c.attack=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"arm\",\"nameZh\":\"攻击前附能\",\"energyCost\":{},\"baseDamage\":100,\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"BEFORE_ATTACK\",\"effects\":["+ATTACH+"]}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=GeneratedRuleBatch28Test.fixture(c);m.endTurn(A);source(m,B);m.endTurn(B);UUID attacker=active(m,A);m.placeDamageCountersByEffect(A,A,attacker,9);
        var selected=m.privateView(A).hand().stream().filter(x->x.definitionId().equals("energy")).limit(2).toList();m.attack(A,"arm");choose(m,A,selected);settle(m);
        check(m.activePokemonView(B).damage()==100&&!active(m,A).equals(attacker),"Hand-attachment Curse prevented declared attack damage or failed to KO afterwards");
    }}
    static void ai(){for(String id:PROFILES.keySet())for(UUID owner:List.of(A,B))for(var level:AiDifficulty.values()){
        var m=game(id,owner,false);UUID actor=other(owner);var card=m.privateView(actor).hand().stream().filter(c->c.definitionId().equals("trainer")).findFirst().orElseThrow();m.playTrainer(actor,card.instanceId(),null);
        var s=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(s,level);check(m.activePokemonView(actor).damage()==40,"AI skipped hand-attachment events");AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"AI Curse match stalled "+level);
    }}
}
