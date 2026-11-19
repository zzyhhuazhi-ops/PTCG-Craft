import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic contracts for the incoming event; actual printed-card coverage lives in batch96 separately. */
public final class BenchToActiveEnergyAbilityTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final String POLICY="{\"trigger\":\"ON_BENCH_TO_ACTIVE\",\"watch\":\"SELF\",\"turn\":\"OWN\",\"optional\":true,\"usage\":\"ONCE_PER_TURN\"}";
    static final String MOVE="{\"type\":\"move_energy\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":120,\"minimum\":1,\"destination_scope\":\"OWN_FIELD\",\"destination_ref\":\"SELF\",\"energy_type\":\"METAL\",\"count_mode\":\"CARDS\"}";
    static final String SWITCH="{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}";
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static void reject(Runnable r){try{r.run();}catch(IllegalStateException|IllegalArgumentException expected){return;}throw new AssertionError("Invalid operation accepted");}
    static EffectDefinition effect(String text){return EffectRegistry.standard().parse(JsonParser.parseString(text).getAsJsonObject());}
    static AbilityDefinition road(String policy,String effects){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"road\",\"nameZh\":\"金属转移测试\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":"+policy+",\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static AbilityDefinition road(){return road(POLICY,MOVE);}
    static TrainerProfile item(String effects){return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(){return game(List.of(),null,DynamicPassiveModifierTest.plainTool(),EnergyProfile.fixed(EnergyType.METAL,2));}
    static ServerMatch game(List<AbilityDefinition> extra,TrainerProfile item,TrainerProfile tool,EnergyProfile energy){
        var abilities=new ArrayList<AbilityDefinition>();abilities.add(road());abilities.addAll(extra);
        return DynamicPassiveModifierTest.fixture(tool,DynamicPassiveModifierTest.plainStadium(),energy,
            DynamicPassiveModifierTest.attack(0,false,false),abilities,0,item);
    }
    static UUID active(ServerMatch m,UUID owner){return m.activePokemonView(owner).pokemonId();}
    static UUID bench(ServerMatch m){return m.benchPokemonView(A).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID owner,String id){return DynamicPassiveModifierTest.hand(m,owner,id);}
    static CardInstance attach(ServerMatch m,UUID owner,UUID host,String id){var c=hand(m,owner,id);m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,c.instanceId(),A,host);return c;}
    static BattlefieldState.PokemonView view(ServerMatch m,UUID host){return m.activePokemonView(A).pokemonId().equals(host)?m.activePokemonView(A):m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(host)).findFirst().orElseThrow();}
    static UUID addBench(ServerMatch m){var c=hand(m,A,"basic");m.playBasicPokemonToBench(A,c.instanceId());return m.benchPokemonView(A).getLast().pokemonId();}
    static void optional(ServerMatch m,boolean use){var p=m.triggerOrderPrompt();check(p!=null&&p.optionalDecision()&&p.choosingPlayer().equals(A),"Missing owner optional prompt");m.decideTriggeredEffect(A,p.id(),use);}
    static void select(ServerMatch m,List<CardInstance> cards){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void atomicallyRejected(ServerMatch m,Runnable r){var a=m.clientView(A);var b=m.clientView(B);var rng=m.randomTranscript();reject(r);check(a.equals(m.clientView(A))&&b.equals(m.clientView(B))&&rng.equals(m.randomTranscript()),"Rejected request changed state or RNG");}
    static long ends(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().startsWith("进入回合末结算")).count();}
    static void expectNextOnce(ServerMatch m,int turn,int hand,long ends){check(m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.privateView(B).hand().size()==hand+1&&ends(m)==ends+1,"Turn end/checkup/draw was skipped or repeated");}
    static void strictContracts(){
        var a=road();TriggeredAbilityRules.validate(a);var e=effect(MOVE);check(AttachedEnergyEffect.from(e).maximum()==120,"Exact120 shape rejected");
        for(String p:List.of(POLICY.replace("true","false"),POLICY.replace("true","\"true\""),POLICY.replace("OWN","ANY"),POLICY.replace("SELF","OWN"),POLICY.replace("ONCE_PER_TURN","NONE"),POLICY.replace("}",",\"x\":1}")))reject(()->road(p,MOVE));
        for(String body:List.of(MOVE.replace("120","61"),MOVE.replace("120","121"),MOVE.replace("120","119"),MOVE.replace("\"minimum\":1","\"minimum\":0"),MOVE.replace("ALL_OWN_POKEMON","OWN_BENCH"),MOVE.replace("METAL","WATER"),MOVE.replace("CARDS","UNITS"),MOVE.replace("}",",\"bind\":\"x\"}"),MOVE+","+MOVE))reject(()->TriggeredAbilityRules.validate(road(POLICY,body)));
        reject(()->new CardTriggerDefinition("bad","bad",a.effectProgram(),a.eventPolicy(),null));
        reject(()->effect("{\"type\":\"move_energy\",\"target\":\"SELF\",\"amount\":61,\"minimum\":0,\"destination_scope\":\"OWN_BENCH\"}"));
        reject(()->new EffectCardSelectionSpec(0,61,null,null,false,Set.of()));
        var candidates=new ArrayList<EffectSelectionTransaction.Candidate>();
        for(int i=0;i<121;i++)candidates.add(new EffectSelectionTransaction.Candidate(new UUID(96,i+1),new EffectTargetResolution.PokemonTarget(A,new UUID(0,1))));
        reject(()->EffectSelectionTransaction.open(A,1,1,61,EffectSelectionTransaction.Visibility.PUBLIC_FIELD,candidates.subList(0,61)));
        reject(()->EffectSelectionTransaction.openForOtherOwnMetalEnergy(A,1,e,candidates));
        reject(()->EffectSelectionTransaction.openForOtherOwnMetalEnergy(A,1,e,List.of()));
        reject(()->EffectSelectionTransaction.openForOtherOwnMetalEnergy(A,1,e,List.of(new EffectSelectionTransaction.Candidate(new UUID(96,1),new EffectTargetResolution.PokemonTarget(B,new UUID(0,1))))));
        reject(()->EffectSelectionTransaction.openForOtherOwnMetalEnergy(A,1,e,List.of(candidates.getFirst(),candidates.getFirst())));
        reject(()->EffectSelectionTransaction.openForTarget(A,1,1,61,EffectTarget.SELECT_OWN_POKEMON,candidates.subList(0,61)));
        var tx=EffectSelectionTransaction.openForOtherOwnMetalEnergy(A,1,e,candidates.subList(0,120));
        check(tx.promptFor(A).maximum()==120&&tx.promptFor(A).candidateIds().size()==120,"120 transaction truncated");
        reject(()->tx.submit(A,tx.transactionId(),1,candidates.stream().map(EffectSelectionTransaction.Candidate::id).toList()));
        reject(()->tx.submit(B,tx.transactionId(),1,List.of(candidates.getFirst().id())));
        check(!tx.consumed(),"Rejected121 consumed transaction");
        check(tx.submit(A,tx.transactionId(),1,candidates.subList(0,61).stream().map(EffectSelectionTransaction.Candidate::id).toList()).size()==61,"61 subset could not commit");
        var m=game();atomicallyRejected(m,()->m.executeEffectProgramByRule(A,active(m,A),a.effectProgram(),EffectTrigger.ON_BENCH_TO_ACTIVE));
        atomicallyRejected(m,()->m.useAbility(A,bench(m),"road"));
    }
    static void physicalAndUsage(){
        var m=game();UUID donor=active(m,A),source=bench(m);var x=attach(m,A,donor,"special");var y=attach(m,B,donor,"special");var wrong=attach(m,A,donor,"energy");var own=attach(m,A,source,"special");
        int turn=m.turnNumber();m.switchActiveByEffect(A,A,source);var q=m.triggerOrderPrompt();atomicallyRejected(m,()->m.decideTriggeredEffect(B,q.id(),true));optional(m,true);
        var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==2&&new HashSet<>(p.candidates()).equals(Set.of(x,y)),"Donor/source/type/foreign-owner selection incorrect");
        atomicallyRejected(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));atomicallyRejected(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(x.instanceId(),x.instanceId())));atomicallyRejected(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(own.instanceId())));atomicallyRejected(m,()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(x.instanceId())));
        select(m,List.of(y));check(view(m,source).attachedEnergyIds().containsAll(List.of(own.instanceId(),y.instanceId()))&&view(m,donor).attachedEnergyIds().containsAll(List.of(x.instanceId(),wrong.instanceId()))&&y.ownerId().equals(B),"Whole Special card or original ownership lost");
        check(m.currentPlayer().equals(A)&&m.turnNumber()==turn&&!m.hasPendingEffectChoice(),"Manual switch ended turn");atomicallyRejected(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(x.instanceId())));
        m.switchActiveByEffect(A,A,donor);optional(m,false);m.switchActiveByEffect(A,A,source);check(m.triggerOrderPrompt()==null,"Same entry used twice");
        m.endTurn(A);m.endTurn(B);m.switchActiveByEffect(A,A,donor);optional(m,false);m.switchActiveByEffect(A,A,source);optional(m,false);
        m.switchActiveByEffect(A,A,donor);optional(m,false);m.switchActiveByEffect(A,A,source);optional(m,true);select(m,List.of(x));check(view(m,source).attachedEnergyIds().contains(x.instanceId()),"Decline consumed next opportunity or next turn remained limited");
    }
    static void suffixIdentityAndSuppression(){
        var twice=item(SWITCH+","+SWITCH+",{\"type\":\"draw\",\"amount\":1}");var m=game(List.of(),twice,DynamicPassiveModifierTest.plainTool(),EnergyProfile.fixed(EnergyType.METAL,2));
        UUID original=active(m,A),source=bench(m);var energy=attach(m,A,original,"special");var card=hand(m,A,"item");int hand=m.privateView(A).hand().size();m.playTrainer(A,card.instanceId(),null);
        var first=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,first.resolutionId(),first.step(),source);check(m.triggerOrderPrompt()==null,"Event interrupted original suffix");var second=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,second.resolutionId(),second.step(),original);
        check(active(m,A).equals(original)&&m.privateView(A).hand().size()==hand,"Suffix did not finish before offer");optional(m,true);check(m.effectCardPrompt(A).candidates().equals(List.of(energy)),"Returned Bench source missed new Active donor");select(m,List.of(energy));check(view(m,source).attachedEnergyIds().contains(energy.instanceId()),"Destination followed current Active");optional(m,false);
        var disable=item(SWITCH+",{\"type\":\"disable_abilities\",\"target\":\"OWN_ACTIVE\"}");var latched=game(List.of(),disable,DynamicPassiveModifierTest.plainTool(),EnergyProfile.fixed(EnergyType.METAL,2));UUID target=bench(latched);var e=attach(latched,A,active(latched,A),"special");latched.playTrainer(A,hand(latched,A,"item").instanceId(),null);var p=latched.effectPokemonPrompt(A);latched.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);optional(latched,true);select(latched,List.of(e));
        var before=game();attach(before,A,active(before,A),"special");target=bench(before);before.disableAbilityEffectsByEffect(A,A,target);before.switchActiveByEffect(A,A,target);check(before.triggerOrderPrompt()==null,"Suppressed at capture still offered");
        var none=game();none.switchActiveByEffect(A,A,bench(none));check(none.triggerOrderPrompt()==null&&none.currentPlayer().equals(A),"No potential offered or ended manual turn");
    }
    static AbilityDefinition change(String id,String trigger){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\"旧事件\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\""+trigger+"\",\"watch\":\"SELF\",\"optional\":false,\"usage\":\"NONE\"},\"effects\":[{\"type\":\"draw\",\"amount\":1}]}").getAsJsonObject(),EffectRegistry.standard());}
    static void oldEventsAndNoProducers(){
        for(String trigger:List.of("ON_SWITCH","ON_RETREAT")){
            var m=game(List.of(change("legacy",trigger)),null,DynamicPassiveModifierTest.plainTool(),EnergyProfile.fixed(EnergyType.METAL,3));UUID old=active(m,A),source=bench(m);var pay=attach(m,A,old,"special");var kept=attach(m,A,old,"special");int hand=m.privateView(A).hand().size();
            if(trigger.equals("ON_SWITCH"))m.switchActiveByEffect(A,A,source);else m.retreat(A,source,List.of(pay.instanceId()));
            var order=m.triggerOrderPrompt();check(order!=null&&!order.optionalDecision()&&order.entries().size()==2&&order.choosingPlayer().equals(A),"Same movement did not share old/new event ordering");
            var legacy=order.entries().stream().filter(e->e.nameZh().equals("旧事件")).findFirst().orElseThrow();
            check(legacy.pokemonId().equals(trigger.equals("ON_SWITCH")?source:old),"Old event subject changed");m.chooseTriggeredEffect(A,order.id(),legacy.id());check(m.privateView(A).hand().size()==hand+1,"Old event lost or duplicated");optional(m,false);
        }
        var m=game();check(m.triggerOrderPrompt()==null,"Opening triggered");UUID fresh=addBench(m);check(m.triggerOrderPrompt()==null,"Direct Bench entry triggered");m.evolve(A,bench(m),hand(m,A,"evolved").instanceId());check(m.triggerOrderPrompt()==null,"Evolution triggered");
        m.switchActiveByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId());check(m.triggerOrderPrompt()==null,"Opponent-turn movement triggered");
    }
    static BattlefieldState board(ServerMatch m){try{var f=ServerMatch.class.getDeclaredField("battlefield");f.setAccessible(true);return (BattlefieldState)f.get(m);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void staleAndFinish(){
        var m=game();UUID donor=active(m,A),source=bench(m);var energy=attach(m,A,donor,"special");m.switchActiveByEffect(A,A,source);optional(m,true);var p=m.effectCardPrompt(A);
        // Explicit fixture mutation isolates the exact physical attachment recheck during a pending transaction.
        board(m).moveAttachedEnergyByEffect(A,donor,A,source,List.of(energy.instanceId()));atomicallyRejected(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(energy.instanceId())));m.concede(B);check(m.phase()==MatchPhase.FINISHED&&m.triggerOrderPrompt()==null&&!m.hasPendingEffectChoice(),"Finish retained pending transaction");
    }
    static void changedCurrentProvision(){
        var energy=BundledCombatData.specialEnergy(JsonParser.parseString("{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"NOT\",\"conditions\":[{\"type\":\"IS_ACTIVE\"}]}],\"energyUnits\":[[\"METAL\"]]}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=game(List.of(),null,DynamicPassiveModifierTest.plainTool(),energy);UUID donor=active(m,A),source=bench(m);var card=attach(m,A,donor,"special");m.switchActiveByEffect(A,A,source);optional(m,true);var p=m.effectCardPrompt(A);check(p.candidates().equals(List.of(card)),"Current donor provision was not used at offer");
        // Explicit fixture mutation changes provision while preserving this physical attachment and incarnation.
        board(m).switchActiveByEffect(A,donor);atomicallyRejected(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(card.instanceId())));
        check(view(m,donor).attachedEnergyIds().contains(card.instanceId())&&!view(m,source).attachedEnergyIds().contains(card.instanceId()),"Stale Metal provision moved an ineligible whole card");m.concede(B);
    }
    record ConfusionGame(ServerMatch match,UUID first,UUID survivor,UUID donor,CardInstance energy,int turn,int nextHand,long ends){}
    static ConfusionGame confusion(String mode){
        String benchOnly="{\"type\":\"NOT\",\"conditions\":[{\"type\":\"IS_ACTIVE\"}]}";
        var tool=mode.equals("HP")?DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"amount\":100,\"conditions\":["+benchOnly+"]}")
            :mode.equals("TOOL")?DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"amount\":100},{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":["+benchOnly+"]}")
            :DynamicPassiveModifierTest.plainTool();
        for(int offset=0;offset<12;offset++){
            var m=game(List.of(),null,tool,EnergyProfile.fixed(EnergyType.METAL,2));UUID first=bench(m),survivor=addBench(m),donor=addBench(m);CardInstance energy=mode.equals("NONE")?null:attach(m,A,donor,"special");
            if(mode.equals("HP")||mode.equals("TOOL")){
                int n=mode.equals("TOOL")?2:1;for(int i=0;i<n;i++)m.playTrainer(A,hand(m,A,"tool").instanceId(),first);
                m.placeDamageCountersByEffect(A,A,first,mode.equals("TOOL")?45:35);
            }
            UUID dying=active(m,A);m.placeDamageCountersByEffect(A,A,dying,27);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);
            // Select a deterministic failed-confusion fixture without claiming a printed coin effect.
            try{var f=ServerMatch.class.getDeclaredField("random");f.setAccessible(true);for(int i=0;i<offset;i++)((ServerRandom)f.get(m)).nextInt("fixture-offset",2);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            int turn=m.turnNumber(),hand=m.privateView(B).hand().size();long ends=ends(m);m.attack(A,"hit");
            if(m.phase()==MatchPhase.PRIZE_SELECTION){m.takePrizeAt(B,0);return new ConfusionGame(m,first,survivor,donor,energy,turn,hand,ends);}
        }throw new AssertionError("No failed-confusion fixture");
    }
    static void falseContinueCascades(){
        for(String mode:List.of("ACCEPT","DECLINE","NONE","HP","TOOL","CONCEDE")){
            var f=confusion(mode);var m=f.match();m.chooseReplacement(A,f.first());
            if(mode.equals("HP"))check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.triggerOrderPrompt()==null,"Dead promotion offered rescue before HP KO");
            if(mode.equals("TOOL")){
                var p=m.toolOverflowPrompt();check(p!=null&&m.currentPlayer().equals(A)&&m.triggerOrderPrompt()==null,"Tool correction skipped ending-turn hold");
                atomicallyRejected(m,()->m.discardOverflowTool(B,p.id(),p.tools().getFirst().instanceId()));m.discardOverflowTool(A,p.id(),p.tools().getFirst().instanceId());
                check(m.phase()==MatchPhase.PRIZE_SELECTION,"Tool loss did not cause secondary HP KO");
            }
            if(mode.equals("HP")||mode.equals("TOOL")){m.takePrizeAt(B,0);m.chooseReplacement(A,f.survivor());}
            if(mode.equals("NONE")){expectNextOnce(m,f.turn(),f.nextHand()+1,f.ends());continue;} // first KO Prize is in B's hand.
            check(m.currentPlayer().equals(A)&&m.turnNumber()==f.turn(),"Advanced before incoming opportunity");
            if(mode.equals("CONCEDE")){m.concede(B);check(m.phase()==MatchPhase.FINISHED&&ends(m)==f.ends(),"Concession started turn end");continue;}
            boolean use=!mode.equals("DECLINE");optional(m,use);if(use)select(m,List.of(f.energy()));
            expectNextOnce(m,f.turn(),f.nextHand()+(mode.equals("HP")||mode.equals("TOOL")?2:1),f.ends());
        }
    }
    static void wholeMoveBeforeKO(){
        var special=BundledCombatData.specialEnergy(JsonParser.parseString("{\"energyUnits\":[[\"METAL\"]],\"effects\":[{\"type\":\"hit_points_modifier\",\"amount\":100}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=game(List.of(),null,DynamicPassiveModifierTest.plainTool(),special);UUID first=active(m,A),source=bench(m),second=addBench(m);var a=attach(m,A,first,"special");var b=attach(m,A,second,"special");m.placeDamageCountersByEffect(A,A,first,35);m.placeDamageCountersByEffect(A,A,second,35);m.switchActiveByEffect(A,A,source);optional(m,true);select(m,List.of(a,b));
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&view(m,source).attachedEnergyIds().containsAll(List.of(a.instanceId(),b.instanceId()))&&m.benchPokemonView(A).stream().noneMatch(v->v.pokemonId().equals(first)||v.pokemonId().equals(second)),"Move settled first donor before whole group");m.takePrizeAt(B,0);m.takePrizeAt(B,0);check(m.currentPlayer().equals(A)&&m.phase()==MatchPhase.TURN,"Manual movement KO ended current turn");
    }
    public static void main(String[] args){strictContracts();physicalAndUsage();suffixIdentityAndSuppression();oldEventsAndNoProducers();staleAndFinish();changedCurrentProvision();falseContinueCascades();wholeMoveBeforeKO();System.out.println("BenchToActiveEnergyAbilityTest passed: strict120 capability, physical ownership/usage/identity, captured timing, old event ordering, stale authority and HP/Tool false-continue completion");}
}
