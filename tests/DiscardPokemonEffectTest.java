import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Complete-stack discard shares leave-play, while delayed application keeps its original public identity. */
public final class DiscardPokemonEffectTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final String DISCARD="{\"type\":\"discard_pokemon\",\"target\":\"TRIGGER_POKEMON\"}";
    static void check(boolean b,String why){BeforeDamageAttackTest.check(b,why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void atomic(ServerMatch m,Runnable action){GeneratedRuleBatch44Test.assertRejectedAtomic(m,action);}
    static AttackDefinition attack(String body){return DynamicPassiveModifierTest.attack(0,false,false).withEffectProgram(BeforeDamageAttackTest.attack("","",DelayedPokemonEffectsTest.delayed(body)).effectProgram());}
    static ServerMatch fixture(){return fixture(DynamicPassiveModifierTest.plainTool(),List.of());}
    static ServerMatch fixture(TrainerProfile tool,List<AbilityDefinition> abilities){return DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(DISCARD),abilities);}
    static Set<UUID> discard(ServerMatch m,UUID p){var ids=new HashSet<UUID>();for(var c:m.privateView(p).discard())ids.add(c.instanceId());return ids;}
    static void replacement(ServerMatch m){check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(B),"Non-KO removal must wait for target owner's replacement");m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A),"Delayed discard failed to finish turn exactly once");}
    public static void main(String[] args){wholeStack();clearAndReentry();sourceDeparture();prevention();simultaneousTool();noBench();leaveNotKo();repeatTarget();strict();System.out.println("DiscardPokemonEffectTest passed: complete stack to owner discard, no KO or prizes, deferred original target and all cleanup, source departure/protection timing, end-turn Tool order, replacement/loss, leave events and strict composition");}
    static void wholeStack(){
        var m=fixture();UUID target=active(m,B);var expected=new HashSet<UUID>();expected.add(PokemonIdentityTestSupport.physical(m,B,target));
        m.endTurn(A);var evolved=hand(m,B,"evolved");expected.add(evolved.instanceId());m.evolve(B,target,evolved.instanceId());
        var tool=hand(m,B,"tool");expected.add(tool.instanceId());m.playTrainer(B,tool.instanceId(),target);
        for(String id:List.of("energy","special")){var c=hand(m,B,id);expected.add(c.instanceId());m.attachEnergyByEffect(B,B,EffectSourceZone.HAND,c.instanceId(),B,target);}m.endTurn(B);
        var prizes=m.privateView(A).prizes();var rng=m.randomTranscript();int turn=m.turnNumber();m.attack(A,"hit");
        check(active(m,B).equals(target)&&m.activePokemonView(B).damage()==0&&m.turnNumber()==turn+1&&discard(m,B).isEmpty(),"Discard executed early or introduced damage");
        m.endTurn(B);check(discard(m,B).equals(expected)&&m.privateView(A).prizes().equals(prizes)&&m.randomTranscript().equals(rng),"Discard failed exact full-stack ownership/no-prize/no-random contract");
        replacement(m);check(m.turnNumber()==turn+2,"Replacement advanced extra turn");
    }
    static void clearAndReentry(){for(String mode:List.of("switch","evolve","devolve","leave")){
        var m=fixture();UUID target=active(m,B),physical=PokemonIdentityTestSupport.physical(m,B,target);
        if(mode.equals("devolve")){m.endTurn(A);m.evolve(B,target,hand(m,B,"evolved").instanceId());m.endTurn(B);}m.attack(A,"hit");
        switch(mode){case "switch"->{m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());m.switchActiveByEffect(B,B,target);}case "evolve"->m.evolve(B,target,hand(m,B,"evolved").instanceId());case "devolve"->m.devolvePokemonToHandByEffect(B,B,target);case "leave"->{m.movePokemonByEffect(B,B,target,LeavePlayDestination.HAND);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());m.playBasicPokemonToBench(B,physical);m.switchActiveByEffect(B,B,PokemonIdentityTestSupport.logical(m,B,physical));}}
        UUID live=active(m,B);check(!m.activePokemonView(B).continuousEffectZh().contains("延迟"),"Cleared marker survives "+mode);m.endTurn(B);check(active(m,B).equals(live)&&!discard(m,B).contains(physical)&&m.currentPlayer().equals(A),"Delayed discard chased a cleared/new field entry "+mode);
    }}
    static void sourceDeparture(){var m=fixture();UUID source=active(m,A);m.attack(A,"hit");m.movePokemonByEffect(B,A,source,LeavePlayDestination.HAND);m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());m.endTurn(B);replacement(m);}
    static void prevention(){for(boolean before:List.of(false,true)){
        var m=fixture(DynamicPassiveModifierTest.plainTool(),List.of(DelayedPokemonEffectsTest.guard()));
        if(before){m.endTurn(A);m.useAbility(B,active(m,B),"guard");m.endTurn(B);}m.attack(A,"hit");
        if(!before)m.useAbility(B,active(m,B),"guard");m.endTurn(B);
        if(before)check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A)&&discard(m,B).isEmpty(),"Pre-existing protection did not prevent application");else replacement(m);
    }}
    static void simultaneousTool(){for(boolean healFirst:List.of(false,true)){
        // Synthetic Tool has the same relevant end-turn healing shape as the official Leftovers FAQ.
        var tool=PersistentSourceTriggerTest.trainer("TOOL",TurnEndEffectsTest.trigger("{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":20}"));
        var m=fixture(tool,List.of());m.attack(A,"hit");var card=hand(m,B,"tool");m.playTrainer(B,card.instanceId(),active(m,B));m.placeDamageCountersByEffect(B,B,active(m,B),5);m.endTurn(B);
        var p=m.triggerOrderPrompt();check(p!=null&&p.choosingPlayer().equals(B)&&p.entries().size()==2,"Ending player must choose simultaneous delayed/Tool order");
        var selected=p.entries().stream().filter(e->e.owner().equals(healFirst?B:A)).findFirst().orElseThrow();
        atomic(m,()->m.chooseTriggeredEffect(A,p.id(),selected.id()));m.chooseTriggeredEffect(B,p.id(),selected.id());
        // After one resolves the sole remaining effect drains automatically, with a departed Tool becoming inert.
        check(m.privateView(B).discard().contains(card),"Tool was not discarded with its host");replacement(m);
        atomic(m,()->m.chooseTriggeredEffect(B,p.id(),selected.id()));
    }}
    static void noBench(){var m=fixture();m.movePokemonByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId(),LeavePlayDestination.HAND);var prizes=m.privateView(A).prizes();m.attack(A,"hit");m.endTurn(B);check(m.phase()==MatchPhase.FINISHED&&m.privateView(A).prizes().equals(prizes),"Last Pokemon discard should lose without Prize selection");}
    static void leaveNotKo(){
        var ko=KnockoutTriggeredAbilityTest.ability("not-ko","ANY",true,false,"{\"type\":\"draw\",\"amount\":1}");
        var leave=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"leave","nameZh":"离场","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_LEAVE_PLAY","watch":"SELF","optional":false,"usage":"NONE"},
             "triggers":[{"trigger":"ON_LEAVE_PLAY","effects":[{"type":"draw","target":"HAND","amount":1}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var m=fixture(DynamicPassiveModifierTest.plainTool(),List.of(ko,leave));m.attack(A,"hit");int size=m.privateView(B).hand().size();m.endTurn(B);check(m.privateView(B).hand().size()==size+1,"Discard should trigger leave event once but never KO reaction");replacement(m);
    }
    static void repeatTarget(){
        var a=attack(DISCARD+","+DISCARD+",{\"type\":\"draw\",\"amount\":1}");var m=BeforeDamageAttackTest.fixture(a);m.attack(A,"hit");int hand=m.privateView(A).hand().size();UUID bench=m.benchPokemonView(B).getFirst().pokemonId();m.endTurn(B);replacement(m);check(active(m,B).equals(bench)&&m.privateView(A).hand().size()==hand+2,"Repeated bound discard retargeted replacement or lost player-only suffix");
    }
    static void strict(){
        for(String body:List.of(DISCARD.replace("TRIGGER_POKEMON","OPPONENT_ACTIVE"),DISCARD.replace("TRIGGER_POKEMON","HAND"),DISCARD.replace("}",",\"amount\":1}"),DISCARD.replace("}",",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}"),DelayedPokemonEffectsTest.KO+","+DISCARD))BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(attack(body)));
        BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(DISCARD.replace("TRIGGER_POKEMON","OPPONENT_ACTIVE"),"","")));
        BeforeDamageAttackTest.reject(()->TriggeredAbilityRules.validate(TurnEndEffectsTest.ability(DISCARD.replace("TRIGGER_POKEMON","SELF"))));
    }
}
