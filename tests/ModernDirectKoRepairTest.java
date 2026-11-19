import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernDirectKoRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    public static void main(String[] args){
        for(String id:List.of("sv4_en_124","sv4_en_229","sv4_en_251","sv4_en_262","sv8-5_en_162","svbsp_en_067"))
            for(String mode:List.of("normal","EFFECTS_ONLY","DAMAGE_ONLY","both-ko"))run(id,mode);
        for(String id:List.of("sv6_en_040","sv6_en_192","sv6_en_212","sv8-5_en_017","sv8-5_en_148"))for(int mode:List.of(0,1,2))blaze(id,mode);
        System.out.println("ModernDirectKoRepairTest passed: six Frenzied Gouging prints and five Dynamic Blaze prints; actual KO gating, shields, recoil, simultaneous KO, prizes, replacements and conditional Energy discard timing");
    }
    static void run(String id,String mode){
        var attack=DATA.pokemon(id).orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);
        var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ATTACK",mode.equals("DAMAGE_ONLY")?"DAMAGE_ONLY":"EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
        var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(guard),attack,EnergyType.DARKNESS,0);
        UUID a=m.activePokemonView(A).pokemonId(),b=m.activePokemonView(B).pokemonId();
        m.endTurn(A);if(mode.endsWith("ONLY"))m.useAbility(B,b,guard.id());m.endTurn(B);
        ModernRuleRepair2Test.pay(m,A,a,"energy",3);if(mode.equals("both-ko"))m.placeDamageCountersByEffect(A,A,a,30);
        int bDiscard=m.privateView(B).discard().size(),aDiscard=m.privateView(A).discard().size();
        m.attack(A,attack.id());
        if(mode.equals("EFFECTS_ONLY")){
            check(m.activePokemonView(B).pokemonId().equals(b)&&m.activePokemonView(A).damage()==0&&m.currentPlayer().equals(B),"blocked KO caused recoil/prizes or stalled");return;
        }
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"direct KO omitted prizes");
        if(!mode.equals("both-ko"))check(m.activePokemonView(A).damage()==200,"successful KO must cause 200 self-damage");
        int steps=0;while((m.phase()==MatchPhase.PRIZE_SELECTION||m.phase()==MatchPhase.CHOOSE_REPLACEMENT)&&steps++<12){
            UUID who=m.pendingChoicePlayer();if(m.phase()==MatchPhase.PRIZE_SELECTION)m.takePrizeAt(who,0);else m.chooseReplacement(who,m.benchPokemonView(who).getFirst().pokemonId());
        }
        check(steps<12&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"KO continuation stalled");
        check(m.privateView(B).discard().size()==bDiscard+1&&m.privateView(A).prizes().size()==5,"defender discard/prize count");
        if(mode.equals("both-ko"))check(m.privateView(A).discard().size()==aDiscard+4&&m.privateView(B).prizes().size()==5,"simultaneous KO lost source Energy/prize");
    }
    static void blaze(String id,int mode){
        var attack=DATA.pokemon(id).orElseThrow().attacks().stream().filter(a->a.id().equals("dynamic-blaze")).findFirst().orElseThrow();AttackProgramRules.validate(attack);
        var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.FIRE,EnergyType.FIRE,DATA.trainer("swsh5_en_124").orElseThrow(),attack);
        m.endTurn(A);if(mode>0)m.evolveByEffect(B,m.activePokemonView(B).pokemonId(),ModernRuleRepair2Test.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(mode==2)m.placeDamageCountersByEffect(B,B,m.activePokemonView(B).pokemonId(),30);m.endTurn(B);
        ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",3);m.attack(A,attack.id());
        check(m.lastAttack().finalDamage()==(mode==0?140:280),"Dynamic Blaze conditional damage");
        check(m.activePokemonView(A).attachedEnergyIds().size()==(mode==0?3:0),"Dynamic Blaze discard must match evolved defender even on KO");
        if(mode==2){check(m.phase()==MatchPhase.PRIZE_SELECTION,"Blaze KO prizes");m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());}
        check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Blaze continuation");
    }
}
