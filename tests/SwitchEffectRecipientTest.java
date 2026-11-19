import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** C-03/C-04 affect the outgoing Active; C-05 affects the selected Bench. Synthetic component contracts. */
public final class SwitchEffectRecipientTest {
    static final UUID A=DynamicPassiveModifierTest.A, B=DynamicPassiveModifierTest.B;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Runnable action){BeforeDamageAttackTest.reject(action);}
    static UUID active(ServerMatch m,UUID owner){return m.activePokemonView(owner).pokemonId();}
    static UUID bench(ServerMatch m,UUID owner){return m.benchPokemonView(owner).getFirst().pokemonId();}
    static AbilityDefinition guard(String source,String relation,String scope){
        return ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield(source,scope,"SELF","LEAVES_PLAY")
            .replace("\"from\":\"OPPONENT\"","\"from\":\""+relation+"\""));
    }
    static AbilityDefinition observer(){
        return BundledCombatData.ability(JsonParser.parseString("""
            {"id":"switch-observer","nameZh":"换位观察","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_SWITCH","watch":"SELF","optional":false,"usage":"NONE"},
             "effects":[{"type":"draw","amount":1}]}
            """).getAsJsonObject(),EffectRegistry.standard());
    }
    static String switching(String opcode,boolean bind){
        return "{\"type\":\""+opcode+"\",\"target\":\""+
            (Set.of("switch","retreat").contains(opcode)?"SELECT_OWN_POKEMON":"SELECT_OPPONENT_POKEMON")+"\""+
            (opcode.equals("retreat")?",\"mode\":\"FREE\"":bind?",\"bind\":\"switched\"":"")+"}";
    }
    static String suffix(){return """
        ,{"type":"conditional","conditions":[{"type":"BOUND_COUNT","bind":"switched","value":1}],
          "effects":[{"type":"draw","amount":2}]},{"type":"draw","amount":1}
        """;}
    static ServerMatch fixture(AttackDefinition attack,List<AbilityDefinition> abilities,TrainerProfile item){
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),
            DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),attack,abilities,0,item);
    }
    static long switches(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().equals("已审核效果交换战斗宝可梦")).count();}
    static long observations(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().startsWith("结算触发特性：switch-observer；")).count();}
    static void protectOpponent(ServerMatch m,boolean protectedActive){
        m.endTurn(A);m.useAbility(B,protectedActive?active(m,B):bench(m,B),"guard");m.endTurn(B);
    }
    static void choose(ServerMatch m,String opcode,UUID target){
        UUID chooser=opcode.equals("force_switch")?B:A;
        var p=m.effectPokemonPrompt(chooser);
        check(p.targets().stream().anyMatch(t->t.pokemonId().equals(target)),"Protected destination disappeared from issued candidates: "+opcode);
        var rng=m.randomTranscript();var before=m.clientView(chooser);
        reject(()->m.chooseEffectPokemon(chooser.equals(A)?B:A,p.resolutionId(),p.step(),target));
        check(before.equals(m.clientView(chooser))&&rng.equals(m.randomTranscript()),"Unauthorized choice mutated state/RNG");
        m.chooseEffectPokemon(chooser,p.resolutionId(),p.step(),target);
        reject(()->m.chooseEffectPokemon(chooser,p.resolutionId(),p.step(),target));
    }
    static void attackMatrix(String opcode,boolean protectedActive,String scope){
        var attack=BeforeDamageAttackTest.attack("","",switching(opcode,true)+suffix());
        var m=fixture(attack,List.of(guard("ATTACK","OPPONENT",scope),observer()),null);
        protectOpponent(m,protectedActive);UUID old=active(m,B),replacement=bench(m,B);
        int hand=m.privateView(A).hand().size();long moves=switches(m),events=observations(m);
        m.attack(A,attack.id());choose(m,opcode,replacement);
        boolean success=scope.equals("DAMAGE_ONLY")||(opcode.equals("force_switch")?!protectedActive:protectedActive);
        check(active(m,B).equals(success?replacement:old),opcode+" wrong recipient: protectedActive="+protectedActive+" scope="+scope);
        check(m.privateView(A).hand().size()==hand+(success?3:1),"Switch bind did not retain actual0/1 across suffix: "+opcode);
        check(switches(m)==moves+(success?1:0)&&observations(m)==events+(success?1:0),"Blocked switch emitted movement/ON_SWITCH or success duplicated it");
        check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Switch did not complete attack exactly once");
    }
    static void abilityMatrix(String opcode,boolean protectedActive){
        var operation=ResumableAbilityTest.ability("move",switching(opcode,true)+suffix());
        var m=fixture(DynamicPassiveModifierTest.attack(0,false,false),List.of(guard("ABILITY","OPPONENT","EFFECTS_ONLY"),operation,observer()),null);
        protectOpponent(m,protectedActive);UUID old=active(m,B),replacement=bench(m,B);
        int hand=m.privateView(A).hand().size();long events=observations(m);
        m.useAbility(A,active(m,A),operation.id());choose(m,opcode,replacement);
        boolean success=opcode.equals("force_switch")?!protectedActive:protectedActive;
        check(active(m,B).equals(success?replacement:old)&&m.privateView(A).hand().size()==hand+(success?3:1),"Ability origin or affected-owner chooser confused recipient: "+opcode);
        check(observations(m)==events+(success?1:0),"Ability switch event incorrect");
    }
    static void own(String opcode,boolean protectedActive){
        var operation=ResumableAbilityTest.ability("move",switching(opcode,false));
        var m=fixture(DynamicPassiveModifierTest.attack(0,false,false),List.of(guard("ABILITY","OWN","EFFECTS_ONLY"),operation,observer()),null);
        UUID old=active(m,A),replacement=bench(m,A);m.useAbility(A,protectedActive?old:replacement,"guard");
        m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        int hand=m.privateView(A).hand().size();long events=observations(m);
        m.useAbility(A,old,operation.id());choose(m,opcode,replacement);
        check(active(m,A).equals(protectedActive?old:replacement),"Own "+opcode+" checked incoming Bench or ignored outgoing OWN guard");
        check(m.privateView(A).hand().size()==hand+(protectedActive?0:1)&&observations(m)==events+(protectedActive?0:1),"Own switch event count incorrect");
        var oldView=m.activePokemonView(A).pokemonId().equals(old)?m.activePokemonView(A):m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(old)).findFirst().orElseThrow();
        check(oldView.conditions().contains(SpecialCondition.POISONED)==protectedActive,"Blocked switch cleared conditions or successful switch retained them");
    }
    static void trainer(String opcode,boolean legacy,boolean protectedActive,String guardSource){
        boolean own=opcode.equals("switch");
        var item=legacy?new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(own?TrainerEffect.Opcode.SWITCH_ACTIVE:TrainerEffect.Opcode.SWITCH_OPPONENT_ACTIVE,0,0))
            :ComposedTrainerIntegrationTest.item(switching(opcode,false));
        var m=fixture(DynamicPassiveModifierTest.attack(0,false,false),List.of(guard(guardSource,own?"OWN":"OPPONENT","EFFECTS_ONLY"),observer()),item);
        UUID owner=own?A:B;if(own)m.useAbility(A,protectedActive?active(m,A):bench(m,A),"guard");else protectOpponent(m,protectedActive);
        UUID old=active(m,owner),replacement=bench(m,owner);long events=observations(m);
        var card=DynamicPassiveModifierTest.hand(m,A,"item");
        m.playTrainer(A,card.instanceId(),legacy?replacement:null);
        if(!legacy)choose(m,opcode,replacement);
        boolean affectedGuard=opcode.equals("gust")?!protectedActive:protectedActive;
        boolean success=!guardSource.equals("TRAINER")||!affectedGuard;
        check(active(m,owner).equals(success?replacement:old),"Trainer recipient/origin incorrect: "+opcode+" legacy="+legacy+" guard="+guardSource);
        check(m.privateView(A).discard().contains(card)&&observations(m)==events+(success?1:0),"Trainer consumption or ON_SWITCH completion incorrect");
    }
    static void staleChoice(){
        var operation=ResumableAbilityTest.ability("move",switching("force_switch",false));
        var m=fixture(DynamicPassiveModifierTest.attack(0,false,false),List.of(operation),null);
        UUID old=active(m,B),replacement=bench(m,B);m.useAbility(A,active(m,A),operation.id());var p=m.effectPokemonPrompt(B);
        var before=m.clientView(B);var rng=m.randomTranscript();
        reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),old));
        reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),bench(m,A)));
        check(before.equals(m.clientView(B))&&rng.equals(m.randomTranscript()),"Invalid field selection mutated pending state");
        // Test-only mutation creates a stale issued Bench identity without granting a second match action.
        EventTargetBindingTest.field(m).removePokemonByEffect(B,replacement);
        var stale=m.clientView(B);reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),replacement));
        check(stale.equals(m.clientView(B))&&rng.equals(m.randomTranscript())&&active(m,B).equals(old),"Stale Bench selection switched or consumed randomness");
        m.concede(B);check(!m.hasPendingEffectChoice(),"Concede retained stale switch prompt");
    }
    public static void main(String[] args){
        for(String opcode:List.of("force_switch","gust"))for(boolean active:List.of(false,true)){
            for(String scope:List.of("EFFECTS_ONLY","DAMAGE_ONLY"))attackMatrix(opcode,active,scope);
            abilityMatrix(opcode,active);trainer(opcode,false,active,"TRAINER");
        }
        for(boolean active:List.of(false,true)){
            own("switch",active);own("retreat",active);
            trainer("gust",true,active,"TRAINER");trainer("switch",true,active,"TRAINER");
        }
        trainer("force_switch",false,true,"ATTACK");trainer("gust",true,false,"ABILITY");
        staleChoice();
        System.out.println("SwitchEffectRecipientTest passed: C03/C04 Active and C05 selected Bench, original Attack/Ability/Trainer origins, own guards, bind0/1, exactly-once events, chooser/stale authority and unchanged suffix");
    }
}
