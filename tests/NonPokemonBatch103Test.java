import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch103Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> TOOLS=List.of("xy5-5_en_023","xy5-5_en_024"),ENERGIES=List.of("xy5-5_en_033","xy5-5_en_034");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static boolean aqua(String id){return id.equals(TOOLS.getFirst())||id.equals(ENERGIES.getFirst());}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String key){return DynamicPassiveModifierTest.hand(m,p,key);}
    static List<UUID> seats(ServerMatch m){return m.clientView(null).players().stream().map(MatchClientView.Player::id).toList();}
    static void settle(ServerMatch m){var ps=seats(m);AiCardOperationTest.settle(MatchSession.multiplayer(m,ps.get(0),ps.get(1)),AiDifficulty.MASTER);}
    static ServerMatch energyGame(String id,boolean wrong){
        return energyGame(id,wrong,GeneratedRuleBatch8Test.hit(100,false));
    }
    static ServerMatch energyGame(String id,boolean wrong,AttackDefinition attack){
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),NonPokemonBatch2Test.DATA.trainer("swsh10_en_155").orElseThrow(),
            NonPokemonBatch2Test.DATA.energyProfile(id).orElseThrow(),attack,List.of(),0,null,
            Map.of("basic",EnergyType.GRASS,"evolved",EnergyType.GRASS),wrong?"Other team":"Team "+(aqua(id)?"Aqua":"Magma")+"'s test");
    }
    static ServerMatch aiGame(String id){
        if(ENERGIES.contains(id))return energyGame(id,false);
        var m=NonPokemonBatch102Test.game(id);UUID host=NonPokemonBatch102Test.add(m,A,aqua(id)?"aqua":"magma");m.switchActiveByEffect(A,A,host);
        m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,aqua(id)?"water":"fighting").instanceId(),A,host);return m;
    }
    static void use(ServerMatch m,UUID p,ServerMatch.BorrowedAttackOption o,UUID target){
        m.attackBorrowed(p,o.holderId(),o.holderEntry(),o.holderTop(),o.abilityId(),o.providerId(),o.providerEntry(),o.providerTop(),o.attackId(),null,target==null?List.of():List.of(target),List.of(),null,-1,null,null);
    }
    public static void main(String[] args){tools();typedDamage();toolAuthority();energyTiming();energyRestrictions();suppression();energyUnits();ai();schema();System.out.println("NonPokemonBatch103Test passed: two team Tool attacks and two double Energy, live holder restrictions, typed payment/active weakness/Bench targeting, poison and confusion, actual attachment-turn expiry across owner/source/move/reattach, suppression and physical two-unit discard, authority and four AI difficulties");}
    static void tools(){for(String id:TOOLS)for(UUID p:List.of(A,B)){
        var m=NonPokemonBatch102Test.game(id);if(p.equals(B))m.endTurn(A);NonPokemonBatch98Test.equip(m,p);check(m.borrowedAttackOptions(p).isEmpty(),"Other team gained Tool attack");
        UUID host=NonPokemonBatch102Test.add(m,p,aqua(id)?"aqua":"magma");m.switchActiveByEffect(p,p,host);NonPokemonBatch98Test.equip(m,p);var o=NonPokemonBatch98Test.option(m,p);
        var unpaid=o;GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->use(m,p,unpaid,null));String wrong=aqua(id)?"fighting":"water";
        m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,hand(m,p,wrong).instanceId(),p,host);check(!m.canPayActiveAttackCost(p,o.attack()),"Wrong type paid team attack");
        m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,hand(m,p,aqua(id)?"water":"fighting").instanceId(),p,host);check(m.canPayActiveAttackCost(p,o.attack()),"Typed team attack unpaid");
        m.evolveByEffect(p,host,hand(m,p,aqua(id)?"ae":"me").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);o=NonPokemonBatch98Test.option(m,p);
        UUID opp=p.equals(A)?B:A;use(m,p,o,aqua(id)?null:active(m,opp));settle(m);
        if(aqua(id))check(m.activePokemonView(opp).conditions().containsAll(Set.of(SpecialCondition.CONFUSED,SpecialCondition.POISONED))&&m.activePokemonView(opp).damage()==10,"Aqua conditions/checkup incorrect");
        else check(m.activePokemonView(opp).damage()==20,"Magma active damage wrong");
    }}
    static void typedDamage(){for(boolean bench:List.of(false,true)){
        var energy=EnergyProfile.fixed(EnergyType.FIGHTING,1);
        var m=DynamicPassiveModifierTest.fixture(NonPokemonBatch2Test.DATA.trainer(TOOLS.getLast()).orElseThrow(),DynamicPassiveModifierTest.plainStadium(),energy,GeneratedRuleBatch8Test.hit(100,false),List.of(),0,null,Map.of("basic",EnergyType.GRASS),"Team Magma's test");
        UUID p=m.currentPlayer(),opp=seats(m).stream().filter(x->!x.equals(p)).findFirst().orElseThrow();m.playTrainer(p,hand(m,p,"tool").instanceId(),active(m,p));m.attachEnergy(p,hand(m,p,"special").instanceId(),active(m,p));
        UUID target=bench?m.benchPokemonView(opp).getFirst().pokemonId():active(m,opp);use(m,p,NonPokemonBatch98Test.option(m,p),target);settle(m);
        check((bench?m.benchPokemonView(opp).getFirst():m.activePokemonView(opp)).damage()==(bench?20:40),"Magma did not use actual holder type/Bench exception");
    }}
    static void toolAuthority(){for(String id:TOOLS)for(boolean reattach:List.of(false,true)){
        var m=aiGame(id);NonPokemonBatch98Test.equip(m,A);var o=NonPokemonBatch98Test.option(m,A);GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->use(m,B,o,null));
        if(reattach){var tool=m.activePokemonView(A).attachedTools().getFirst();m.discardToolByEffect(A,A,active(m,A),tool.instanceId());m.attachToolByEffect(A,A,EffectSourceZone.DISCARD,tool.instanceId(),A,active(m,A));}else m.disableToolEffectsByEffect(A,A,active(m,A));
        GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->use(m,A,o,null));
    }}
    static void energyTiming(){for(String id:ENERGIES)for(int ownerIndex:List.of(0,1))for(EffectSourceZone source:List.of(EffectSourceZone.HAND,EffectSourceZone.DECK,EffectSourceZone.DISCARD)){
        var m=energyGame(id,false);UUID actor=m.currentPlayer(),owner=seats(m).get(ownerIndex);var card=hand(m,owner,"special");
        if(source!=EffectSourceZone.HAND){PairedTrainerPlayTest.zone(m,owner,"hand").remove(card);PairedTrainerPlayTest.zone(m,owner,source==EffectSourceZone.DECK?"deck":"discard").add(card);}
        m.attachEnergyByEffect(actor,owner,source,card.instanceId(),owner,active(m,owner));check(m.activePokemonView(owner).attachedEnergyIds().contains(card.instanceId()),"Valid team Energy not attached");
        UUID bench=m.benchPokemonView(owner).getFirst().pokemonId();m.moveAttachedEnergyByEffect(actor,owner,active(m,owner),owner,bench,List.of(card.instanceId()));m.endTurn(actor);settle(m);
        check(m.privateView(owner).discard().contains(card)&&m.benchPokemonView(owner).getFirst().attachedEnergyIds().isEmpty(),"Energy failed actual attachment-turn expiry after moving");
        UUID now=m.currentPlayer();m.attachEnergyByEffect(now,owner,EffectSourceZone.DISCARD,card.instanceId(),owner,active(m,owner));m.endTurn(now);settle(m);check(m.privateView(owner).discard().contains(card),"Reattached Energy missed new attachment turn");
    }}
    static void energyRestrictions(){for(String id:ENERGIES){
        var wrong=energyGame(id,true);UUID p=wrong.currentPlayer();var card=hand(wrong,p,"special");var before=wrong.clientView(p);check(!wrong.canAttachEnergy(p,card.instanceId(),active(wrong,p)),"Wrong name manual attach offered");GeneratedRuleBatch8Test.reject(()->wrong.attachEnergy(p,card.instanceId(),active(wrong,p)));check(before.equals(wrong.clientView(p)),"Invalid Energy attachment mutated board");
        var m=energyGame(id,false);UUID actor=m.currentPlayer();var attached=hand(m,actor,"special");m.attachEnergy(actor,attached.instanceId(),active(m,actor));m.evolveByEffect(actor,active(m,actor),hand(m,actor,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(m.privateView(actor).discard().contains(attached),"Lost team name did not discard Energy immediately");
    }}
    static void suppression(){for(String id:ENERGIES)for(boolean removeSameTurn:List.of(false,true)){
        var m=energyGame(id,false);UUID p=m.currentPlayer();var card=hand(m,p,"special");m.attachEnergy(p,card.instanceId(),active(m,p));m.playTrainer(p,hand(m,p,"stadium").instanceId(),null);
        if(removeSameTurn)m.discardStadiumByEffect(p);m.endTurn(p);settle(m);
        check(m.privateView(p).discard().contains(card)==removeSameTurn,"Energy expiry ignored live text suppression");
        if(!removeSameTurn){m.discardStadiumByEffect(m.currentPlayer());m.endTurn(m.currentPlayer());settle(m);check(m.activePokemonView(p).attachedEnergyIds().contains(card.instanceId()),"Missed attachment-turn event fired on later turn");
            m.discardAttachedEnergyByEffect(p,p,active(m,p),List.of(card.instanceId()));m.attachEnergyByEffect(p,p,EffectSourceZone.DISCARD,card.instanceId(),p,active(m,p));m.endTurn(p);settle(m);check(m.privateView(p).discard().contains(card),"Suppressed old entry poisoned reattachment");}
    }}
    static void energyUnits(){for(String id:ENERGIES){var m=energyGame(id,false);UUID p=m.currentPlayer();var card=hand(m,p,"special");m.attachEnergy(p,card.instanceId(),active(m,p));EnergyType type=aqua(id)?EnergyType.WATER:EnergyType.FIGHTING;
        var paid=new AttackDefinition("paid","paid",Map.of(type,2),10,EnergyType.GRASS,EffectSupport.SUPPORTED,null);check(m.canPayActiveAttackCost(p,paid),"Double Energy not two typed units");check(!m.canPayActiveAttackCost(p,new AttackDefinition("three","three",Map.of(type,3),10,EnergyType.GRASS,EffectSupport.SUPPORTED,null)),"Double Energy provided too much");
        m.discardAttachedEnergyByEffect(p,p,active(m,p),List.of(card.instanceId()));m.endTurn(p);settle(m);check(m.privateView(p).discard().stream().filter(c->c.equals(card)).count()==1,"Detached energy duplicated at turn end");
        var attack=BeforeDamageAttackTest.attack("","","{\"type\":\"discard_energy\",\"target\":\"SELF\",\"amount\":2,\"minimum\":2,\"energy_type\":\""+type+"\",\"count_mode\":\"UNITS\"}");
        var cost=energyGame(id,false,attack);UUID actor=cost.currentPlayer();var physical=hand(cost,actor,"special");cost.attachEnergy(actor,physical.instanceId(),active(cost,actor));cost.attack(actor,"hit");settle(cost);
        check(cost.privateView(actor).discard().contains(physical)&&cost.activePokemonView(actor).attachedEnergyIds().isEmpty(),"Two-unit attack discard failed with a single physical Energy");
    }}
    static void ai(){for(String id:TOOLS)for(var d:AiDifficulty.values()){var m=aiGame(id);NonPokemonBatch98Test.equip(m,A);var s=MatchSession.multiplayer(m,A,B);var v=s.view(A,A);var option=v.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK&&o.cardKeys().stream().anyMatch(k->k.startsWith("copy-attack:"))).findFirst().orElseThrow();s.submit(A,A,v.revision(),option.index());AiCardOperationTest.settle(s,d);AiCardOperationTest.finish(s,d);check(m.phase()==MatchPhase.FINISHED,"Team Tool AI stalled");}}
    static void schema(){GeneratedRuleBatch8Test.reject(()->TurnEndTriggerPolicy.parse(JsonParser.parseString("{\"trigger\":\"ON_TURN_END\",\"turn\":\"OWN\",\"optional\":false,\"usage\":\"NONE\",\"sourceAttachedThisTurn\":true}").getAsJsonObject()));for(String id:TOOLS){var effect=ToolGrantedAttack.fromProgram(NonPokemonBatch2Test.DATA.trainer(id).orElseThrow().ongoingProgram());AttackProgramRules.validate(ToolGrantedAttack.materialize(effect,EnergyType.GRASS));var a=new HashMap<>(effect.arguments());a.put("holder_name_contains","Team");GeneratedRuleBatch8Test.reject(()->ToolGrantedAttack.validate(new EffectDefinition(effect.type(),effect.target(),a,List.of(),List.of())));}}
}
