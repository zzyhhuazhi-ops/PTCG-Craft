import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic contracts only. No real card, older print or zero-Prize ItemPokemon is enabled. */
public final class AttackDamageKoProtectionTest {
    static final UUID A=new UUID(0,84001),B=new UUID(0,84002);
    static final String GUARD="""
        {"type":"protect_on_attack_damage_ko","target":"SELF","source":"ATTACK","from":"ANY",
         "scope":"DAMAGE_AND_EFFECTS","duration":"OPPONENT_TURN_END","when":"OPPONENT_NEXT_TURN_START","clear":"BENCH_EVOLVE_LEAVE"}
        """;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Runnable action){BeforeDamageAttackTest.reject(action);}
    static AttackDefinition attack(String id,int damage,String after) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\""+id+"\",\"energyCost\":{},\"baseDamage\":"+damage+",\"effectSupport\":\"SUPPORTED\""+
            (after.isBlank()?"":",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+after+"]}]")+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch game(AttackDefinition... attacks){return game(List.of(attacks),List.of());}
    static ServerMatch game(List<AttackDefinition> attacks,List<AbilityDefinition> defenderAbilities) {
        var definitions=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("alpha","alpha_evolved","beta","beta_evolved","energy")) {
            boolean energy=id.equals("energy"),evolved=id.endsWith("_evolved");
            definitions.add(new CardDefinition(id,id,energy?CardCategory.ENERGY:CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),!energy&&!evolved,energy,60,Map.of(),EffectSupport.SUPPORTED,null));
            if(!energy)profiles.put(id,new PokemonProfile(evolved?350:300,0,evolved?id.substring(0,id.indexOf('_')):null,null,0,null,0,1,attacks,id.startsWith("beta")?defenderAbilities:List.of(),EnergyType.WATER));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.WATER));
        for(int seed=0;seed<1000;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)) {
                String name=owner.equals(A)?"alpha":"beta";var cards=new ArrayList<CardInstance>();
                for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i<18?name:i<28?name+"_evolved":"energy"));decks.add(cards);
            }
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(definitions),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(owner->m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(owner.equals(A)?"alpha":"beta")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(owner->m.privateView(owner).hand().stream().anyMatch(c->c.definitionId().endsWith("_evolved"))))return m;
        }
        throw new AssertionError("No KO-protection fixture");
    }
    static UUID active(ServerMatch m,UUID owner){return m.activePokemonView(owner).pokemonId();}
    static UUID bench(ServerMatch m,UUID owner){return m.benchPokemonView(owner).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID owner,String id){return m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static BattlefieldState battlefield(ServerMatch m) {
        try{var f=ServerMatch.class.getDeclaredField("battlefield");f.setAccessible(true);return (BattlefieldState)f.get(m);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    static boolean protectedNow(ServerMatch m,UUID owner,UUID pokemon) {
        return battlefield(m).preventsImpact(owner,pokemon,new EffectOrigin(EffectOrigin.Kind.ATTACK,owner.equals(A)?B:A,new UUID(0,84999)),EffectProtection.Impact.DAMAGE,filter->true);
    }
    static boolean marked(ServerMatch m,UUID owner){return m.activePokemonView(owner).continuousEffectZh().contains("击倒后防护");}
    static void prepareKo(ServerMatch m){m.placeDamageCountersByEffect(A,B,active(m,B),27);}
    static void settle(ServerMatch m) {
        int budget=30;
        while(budget-->0) {
            if(m.triggerOrderPrompt()!=null){DamageTriggeredAbilityTest.finishTriggers(m);continue;}
            if(m.phase()==MatchPhase.PRIZE_SELECTION){m.takePrizeAt(m.pendingChoicePlayer(),0);continue;}
            if(m.phase()==MatchPhase.CHOOSE_REPLACEMENT){UUID owner=m.pendingChoicePlayer();m.chooseReplacement(owner,bench(m,owner));continue;}
            return;
        }
        throw new AssertionError("KO settlement did not end");
    }
    static void noGuard(ServerMatch m,UUID owner,String reason){check(!marked(m,owner)&&!protectedNow(m,owner,active(m,owner)),reason);}
    static long activations(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().startsWith("击倒后防护生效")).count();}
    public static void main(String[] args) {
        schemaAndCause(); ordinaryAndExpiry(); nonDamageAndSurvival(); retaliationAndKoReaction(); delayedCurrentTurnBoundary();
        pendingInvalidation(); activeInvalidation(); extraOwnTurn(); copiedSource(); finishCleanup(); startTurnBoundary();
        System.out.println("AttackDamageKoProtectionTest passed: confirmed cause, reaction/current-turn timing, original source and clearing tokens, actual next-opponent boundary, copies, expiry, cleanup and strict schema");
    }
    static void schemaAndCause() {
        var effect=EffectRegistry.standard().parse(JsonParser.parseString(GUARD).getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(effect).capability()==EffectExecutionCapability.REQUIRES_ATTACK_CONTEXT,"Wrong execution contract");
        for(boolean confirmed:List.of(false,true))for(boolean damage:List.of(false,true))for(UUID owner:List.of(A,B))
            check(AttackDamageKoProtection.qualifies(A,owner,confirmed,damage)==(confirmed&&damage&&owner.equals(B)),"Qualification depends on wrong event facts");
        // No awards/prizes argument exists; this test does not pretend a zero-Prize runtime card is supported.
        for(var entry:Map.of("source","ABILITY","from","OPPONENT","scope","DAMAGE_ONLY","duration","SOURCE_NEXT_TURN_START","when","NOW","clear","NEVER","target","OWN_ACTIVE","unknown","x").entrySet()) {
            var json=JsonParser.parseString(GUARD).getAsJsonObject();json.addProperty(entry.getKey(),entry.getValue());reject(()->EffectRegistry.standard().parse(json));
        }
        var missing=JsonParser.parseString(GUARD).getAsJsonObject();missing.remove("when");reject(()->EffectRegistry.standard().parse(missing));
        reject(()->AttackProgramRules.validate(attack("bad",30,GUARD+","+GUARD)));
        reject(()->AttackProgramRules.validate(attack("bad",30,"{\"type\":\"repeat\",\"count\":2,\"effects\":["+GUARD+"]}")));
        for(var trigger:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ONCE_PER_TURN,EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ON_KNOCKOUT)) {
            var program=new EffectProgram(List.of(new TriggeredEffects(trigger,List.of(),List.of(effect))));
            reject(()->ResumableProgramRules.validate(program,trigger!=EffectTrigger.ON_CARD_PLAYED,trigger));
        }
        reject(()->AttackProgramRules.validate(attack("bad",30,GUARD).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_KNOCKOUT,List.of(),List.of(effect)))))));
    }
    static void ordinaryAndExpiry() {
        var m=game(attack("guard",30,GUARD),attack("strike",40,"{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"}"));UUID source=active(m,A);prepareKo(m);var rng=m.randomTranscript();m.attack(A,"guard");
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&marked(m,A)&&!protectedNow(m,A,source),"KO guard active before opponent turn or registration lost");
        settle(m);check(m.currentPlayer().equals(B)&&protectedNow(m,A,source)&&!marked(m,A),"Guard not activated exactly at opposing turn");
        check(rng.equals(m.randomTranscript()),"Deterministic guard consumed RNG");m.attack(B,"strike");
        check(m.activePokemonView(A).damage()==0&&!m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Damage/effect guard did not work");
        noGuard(m,A,"Guard did not expire at opposing turn end");check(activations(m)==1,"Guard activated twice");
        m.endTurn(A);m.attack(B,"strike");check(m.activePokemonView(A).damage()>0,"Expired guard still protects");
    }
    static void nonDamageAndSurvival() {
        for(String mode:List.of("nonlethal","zero","counter","direct","own","survive","checkup")) {
            String after=GUARD;int damage=mode.equals("zero")||mode.equals("counter")||mode.equals("own")?0:30;
            if(mode.equals("counter"))after+=",{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}";
            if(mode.equals("direct"))after+=",{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"}";
            if(mode.equals("own"))after+=",{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":30}";
            if(mode.equals("checkup"))after+=",{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"}";
            var abilities=mode.equals("survive")?List.of(DamageTriggeredAbilityTest.ability("guts",true,true,false,"{\"type\":\"survive_attack_damage\",\"target\":\"SELF\",\"remaining_hp\":10}")):List.<AbilityDefinition>of();
            var m=game(List.of(attack("guard",damage,after)),abilities);
            if(mode.equals("survive")||mode.equals("direct"))prepareKo(m);
            if(mode.equals("checkup"))m.placeDamageCountersByEffect(A,B,active(m,B),26);
            m.attack(A,"guard");settle(m);noGuard(m,A,"Nonqualifying cause acquired guard: "+mode);check(activations(m)==0,"Nonqualifying guard activation: "+mode);
            if(mode.equals("survive"))check(m.activePokemonView(B).damage()==290,"Guts did not retain 10 HP");
        }
    }
    static void retaliationAndKoReaction() {
        var m=game(attack("guard",30,GUARD),attack("retaliate",0,"{\"type\":\"retaliate\",\"target\":\"SELF\",\"amount\":3}"));
        m.endTurn(A);m.attack(B,"retaliate");prepareKo(m);m.attack(A,"guard");
        check(m.activePokemonView(A).damage()==30&&!protectedNow(m,A,active(m,A)),"New guard blocked current-attack retaliation");settle(m);check(protectedNow(m,A,active(m,A)),"Surviving retaliation did not qualify");
        var kill=KnockoutTriggeredAbilityTest.ability("revenge","OPPONENT_ATTACK_DAMAGE",true,false,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}");
        var n=game(List.of(attack("guard",30,GUARD)),List.of(kill));UUID old=active(n,A);prepareKo(n);n.attack(A,"guard");settle(n);
        check(!active(n,A).equals(old)&&activations(n)==0,"KO reaction transferred source guard to replacement");noGuard(n,A,"Dead source guard survived");
    }
    static void delayedCurrentTurnBoundary() {
        String delayed="{\"type\":\"delayed_effect\",\"target\":\"OPPONENT_ACTIVE\",\"when\":\"OPPONENT_NEXT_TURN_END\",\"clear\":\"BENCH_EVOLVE_LEAVE\",\"effects\":[{\"type\":\"choice\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"TRIGGER_POKEMON\",\"amount\":3},{\"type\":\"draw\",\"amount\":1}]}]}";
        var m=game(attack("guard",30,GUARD),attack("delay",0,delayed));m.endTurn(A);m.attack(B,"delay");prepareKo(m);m.attack(A,"guard");settle(m);
        check(m.phase()==MatchPhase.TURN_END&&m.currentPlayer().equals(A)&&!protectedNow(m,A,active(m,A)),"Guard became active in current turn-end window");
        var prompt=m.effectChoicePrompt(B);check(prompt!=null,"Expected original-controller delayed choice");m.chooseEffectOption(B,prompt.resolutionId(),prompt.step(),0);
        check(m.activePokemonView(A).damage()==30&&m.currentPlayer().equals(B)&&protectedNow(m,A,active(m,A)),"Delayed effect or next opponent activation order changed");
    }
    static void pendingInvalidation() {
        for(String mode:List.of("switch","evolve","devolve","leave")) {
            var m=extraGame(mode.equals("devolve"));UUID old=active(m,A);
            check(marked(m,A)&&m.currentPlayer().equals(A),"No pending marker in extra own turn");
            if(mode.equals("switch")){m.switchActiveByEffect(A,A,bench(m,A));m.switchActiveByEffect(A,A,old);}
            if(mode.equals("evolve"))m.evolveByEffect(A,old,hand(m,A,"alpha_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            if(mode.equals("devolve"))m.devolvePokemonToHandByEffect(A,A,old);
            if(mode.equals("leave")) {
                UUID physical=PokemonIdentityTestSupport.physical(m,A,old);m.movePokemonByEffect(A,A,old,LeavePlayDestination.HAND);settle(m);
                m.playBasicPokemonToBench(A,physical);UUID returned=PokemonIdentityTestSupport.logical(m,A,physical);
                check(!returned.equals(old),"Reentry did not create a new identity");m.switchActiveByEffect(A,A,returned);
            }
            m.endTurn(A);noGuard(m,A,"Pending source invalidation failed: "+mode);check(activations(m)==0,"Invalid source activated: "+mode);
            if(!mode.equals("leave"))check(active(m,A).equals(old),"Clearing test should preserve logical entry: "+mode);
        }
    }
    static void activeInvalidation() {
        var m=game(attack("guard",30,GUARD));prepareKo(m);m.attack(A,"guard");settle(m);UUID old=active(m,A);
        m.switchActiveByEffect(B,A,bench(m,A));m.switchActiveByEffect(B,A,old);noGuard(m,A,"Active protection survived switching away and back");
    }
    static ServerMatch extraGame(boolean evolved) {
        String extra="{\"type\":\"damage_counter\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":50,\"extra_turn_on_ko\":true,\"previous_turn_attack_ids\":\"guard\"}";
        var m=game(attack("guard",30,GUARD+","+extra));
        m.endTurn(A);m.playBasicPokemonToBench(B,hand(m,B,"beta").instanceId());m.endTurn(B);
        if(evolved)m.evolve(A,active(m,A),hand(m,A,"alpha_evolved").instanceId());
        prepareKo(m);m.attack(A,"guard");var prompt=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),bench(m,B));settle(m);
        return m;
    }
    static void extraOwnTurn() {
        var m=extraGame(false);
        check(m.currentPlayer().equals(A)&&marked(m,A)&&!protectedNow(m,A,active(m,A))&&activations(m)==0,"Extra own turn activated/consumed future protection");
        m.endTurn(A);check(m.currentPlayer().equals(B)&&protectedNow(m,A,active(m,A))&&activations(m)==1,"Real next opponent turn did not activate once");m.endTurn(B);noGuard(m,A,"Opponent end expiry failed after extra own turn");
    }
    static void copiedSource() {
        var selected=CopyAttackTest.selected(",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+GUARD+"]}]");var m=CopyAttackTest.fixture(selected);CopyAttackTest.pay(m);
        UUID source=m.activePokemonView(CopyAttackTest.A).pokemonId();m.placeDamageCountersByEffect(CopyAttackTest.A,CopyAttackTest.B,m.activePokemonView(CopyAttackTest.B).pokemonId(),22);
        var choice=CopyAttackTest.choice(m);CopyAttackTest.copy(m,choice,List.of(),List.of());settle(m);
        check(battlefield(m).preventsImpact(CopyAttackTest.A,source,new EffectOrigin(EffectOrigin.Kind.ATTACK,CopyAttackTest.B,new UUID(0,84999)),EffectProtection.Impact.DAMAGE,filter->true),"Copied guard lost real attacker/controller");
        check(activations(m)==1&&!m.activePokemonView(CopyAttackTest.B).continuousEffectZh().contains("防护"),"Copied guard moved to donor replacement");
    }
    static void finishCleanup() {
        var m=game(attack("guard",30,GUARD));prepareKo(m);m.attack(A,"guard");check(marked(m,A),"Missing pending guard");m.concede(B);check(!marked(m,A),"Finish retained pending guard marker");
        var n=game(attack("guard",30,GUARD+",{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}"));prepareKo(n);n.attack(A,"guard");check(marked(n,A),"Missing pre-KO guard marker");n.concede(B);check(!marked(n,A),"Finish during attack retained guard marker");
    }
    static void startTurnBoundary() {
        // Attach a start-of-turn source after setup so fixture opening is not paused.
        String trigger=PersistentSourceTriggerTest.trigger("start",TurnStartEffectsTest.OWN_START,"{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
        var energy=PersistentSourceTriggerTest.energy(trigger);
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),energy,attack("guard",30,GUARD));
        UUID owner=DynamicPassiveModifierTest.A,opponent=DynamicPassiveModifierTest.B;
        m.attachEnergyByEffect(owner,opponent,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,opponent,"special").instanceId(),opponent,m.benchPokemonView(opponent).getFirst().pokemonId());
        UUID source=m.activePokemonView(owner).pokemonId();m.placeDamageCountersByEffect(owner,opponent,m.activePokemonView(opponent).pokemonId(),27);m.attack(owner,"guard");settle(m);
        check(m.currentPlayer().equals(opponent)&&m.effectChoicePrompt(opponent)!=null,"Expected opposing turn-start continuation");
        check(battlefield(m).preventsImpact(owner,source,new EffectOrigin(EffectOrigin.Kind.ATTACK,opponent,new UUID(0,84999)),EffectProtection.Impact.DAMAGE,filter->true),"Protection activated after ON_TURN_START instead of before it");
        var prompt=m.effectChoicePrompt(opponent);m.chooseEffectOption(opponent,prompt.resolutionId(),prompt.step(),0);check(activations(m)==1,"Turn-start resume repeated activation");
    }
}
