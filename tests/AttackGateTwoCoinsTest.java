import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Two attack-attempt flips are outside the declared attack and must both be heads. */
public final class AttackGateTwoCoinsTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static final String GATE="{\"type\":\"attack_coin_gate\",\"target\":\"OPPONENT_ACTIVE\",\"flips\":2}";
    static void check(boolean b,String s){BeforeDamageAttackTest.check(b,s);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static AttackDefinition attack(){return BeforeDamageAttackTest.attack("","",GATE);}
    static ServerMatch game(){return BeforeDamageAttackTest.fixture(attack());}
    static List<RandomAuditEvent> flips(ServerMatch m,int from){return AttackCoinGateTest.events(m,from,"attack-coin-gate:");}
    public static void main(String[] args){outcomes();confusion();lifecycle();immunity();retryExclusion();copied();schema();System.out.println("AttackGateTwoCoinsTest passed: HH/HT/TH/TT, both flips including first tails, pre-Confusion ordering, no failed body, wrong actor/attack atomicity, switch/evolution/expiry, effect immunity, no attack-body retry, copied gate and legacy serialized one-coin compatibility");}
    static void vary(ServerMatch m,int n){if(n>0)m.drawCardsByEffect(B,B,n);m.searchOwnDeckToHandByEffect(B,List.of(m.privateView(B).deck().getFirst().instanceId()));}
    static void outcomes(){var seen=new HashSet<String>();for(int offset=0;offset<24&&seen.size()<4;offset++){
        var m=game();m.attack(A,attack().id());vary(m,offset);int from=m.randomTranscript().size();var view=m.clientView(B);reject(()->m.attack(A,attack().id()));reject(()->m.attack(B,"missing"));check(view.equals(m.clientView(B))&&m.randomTranscript().size()==from,"Invalid declaration consumed RNG/state");
        m.attack(B,attack().id());var f=flips(m,from);check(f.size()==2,"Did not roll exactly two attempt coins");String pattern=""+f.getFirst().result()+f.getLast().result();seen.add(pattern);boolean heads=f.stream().allMatch(e->e.result()==0);
        check(m.activePokemonView(A).damage()==(heads?80:0)&&m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"All-heads success or failed turn/body");int again=m.randomTranscript().size();m.attack(A,attack().id());check(flips(m,again).size()==(heads?2:0),"Failed attack installed its own gate");
    }check(seen.size()==4,"Missing two-coin outcome "+seen);}
    static void confusion(){boolean pass=false,fail=false;for(int offset=0;offset<24&&!(pass&&fail);offset++){
        var m=game();m.attack(A,attack().id());vary(m,offset);m.applySpecialConditionByEffect(B,B,SpecialCondition.CONFUSED);int damage=m.activePokemonView(B).damage(),from=m.randomTranscript().size();m.attack(B,attack().id());var f=flips(m,from);var c=AttackCoinGateTest.events(m,from,"attack-confusion:");check(f.size()==2,"Confusion skipped a gate flip");
        if(f.stream().allMatch(e->e.result()==0)){pass=true;check(c.size()==1&&c.getFirst().sequence()>f.getLast().sequence(),"Confusion must follow both successful gate coins");}else{fail=true;check(c.isEmpty()&&m.activePokemonView(B).damage()==damage,"Failed gate caused Confusion coins/counters");}
    }check(pass&&fail,"Missing confusion gate branches");}
    static void lifecycle(){for(String mode:List.of("SWITCH","EVOLVE","EXPIRE")){
        var m=game();m.attack(A,attack().id());if(mode.equals("SWITCH")){UUID old=active(m,B);m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());m.switchActiveByEffect(B,B,old);}
        if(mode.equals("EVOLVE"))m.evolve(B,active(m,B),DynamicPassiveModifierTest.hand(m,B,"evolved").instanceId());
        if(mode.equals("EXPIRE")){m.endTurn(B);m.endTurn(A);}int from=m.randomTranscript().size();m.attack(B,attack().id());check(flips(m,from).isEmpty(),"Gate survived "+mode);
    }}
    static void immunity(){var m=game();m.endTurn(A);var shield=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield("ATTACK","EFFECTS_ONLY","SELF","LEAVES_PLAY")).getAsJsonObject());m.executeEffectProgramByRule(B,active(m,B),new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(shield)))),EffectTrigger.ON_CARD_PLAYED);m.endTurn(B);m.attack(A,attack().id());check(m.activePokemonView(B).damage()==80,"Effect-only guard prevented printed damage");int from=m.randomTranscript().size();m.attack(B,attack().id());check(flips(m,from).isEmpty(),"Immune defender received gate");}
    static void retryExclusion(){var m=AttackCoinMatchTest.game(attack(),EnergyType.COLORLESS,false);m.attack(A,attack().id());m.playTrainer(B,DynamicPassiveModifierTest.hand(m,B,"tool").instanceId(),active(m,B));int from=m.randomTranscript().size();m.attack(B,attack().id());check(flips(m,from).size()==2&&!m.hasPendingEffectChoice()&&AttackCoinMatchTest.uses(m)==0,"Attempt coins entered attack-body retry");}
    static void copied(){var selected=DamageTriggeredAbilityTest.attack(10,SourceEnergyCounterTest.after(GATE));var m=CopyAttackTest.fixture(CopyAttackTest.copier(),selected,EnergyType.PSYCHIC,0);CopyAttackTest.pay(m);CopyAttackTest.copy(m,CopyAttackTest.choice(m),List.of(),List.of());int from=m.randomTranscript().size();m.attack(CopyAttackTest.B,selected.id());check(flips(m,from).size()==2,"Copied gate lost two-coin policy");}
    static void schema(){var state=new BattlefieldState(A,B,Map.of("base",new PokemonProfile(100,1,null)),new ServerRandom(80));var target=new dev.hua.ptcglive.rules.CardInstance(new UUID(0,8098),B,"base");state.placeInitialActive(B,target);reject(()->state.gateAttackWithCoinUntilOwnerNextTurn(new UUID(0,8099),B,target.instanceId(),2));check(!state.attackCoinGated(B,target.instanceId()),"Foreign effect owner mutated gate");
        for(String bad:List.of(GATE.replace(":2",":0"),GATE.replace(":2",":3"),GATE.replace("OPPONENT_ACTIVE","SELF"),GATE.replace("\"flips\":2","\"flips\":2,\"amount\":1"),GATE.replace("\"flips\":2","\"flips\":2,\"effects\":[{\"type\":\"draw\",\"amount\":1}]")))reject(()->EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject()));
        reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(GATE,"","")));reject(()->TriggeredAbilityRules.validate(DamageTriggeredAbilityTest.ability("bad",true,false,false,GATE)));
        var one=ContinuousEffect.attackCoinGate(A);var two=ContinuousEffect.attackCoinGate(A,2);var gson=new Gson();check(one.damageReduction()==0&&one.attackGateFlips()==1&&two.attackGateFlips()==2,"Legacy persisted representation changed");check(gson.fromJson(gson.toJson(one),ContinuousEffect.class).attackGateFlips()==1&&gson.fromJson(gson.toJson(two),ContinuousEffect.class).attackGateFlips()==2,"Gate persistence lost count");reject(()->ContinuousEffect.attackCoinGate(A,3));
    }
}
