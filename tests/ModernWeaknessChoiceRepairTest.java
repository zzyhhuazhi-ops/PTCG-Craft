import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class ModernWeaknessChoiceRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv3-5_en_137",0);
        var types=List.of(EnergyType.GRASS,EnergyType.FIRE,EnergyType.WATER,EnergyType.LIGHTNING,EnergyType.PSYCHIC,EnergyType.FIGHTING,EnergyType.DARKNESS,EnergyType.METAL,EnergyType.DRAGON);
        for(int i=0;i<types.size();i++)for(boolean weak:List.of(false,true))for(boolean shield:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(attack,0,weak);var f=EventTargetBindingTest.field(m);UUID target=m.activePokemonView(B).pokemonId();
            var oldType=f.effectiveWeaknessType(B,target);int oldMultiplier=f.effectiveWeaknessMultiplier(B,target);
            if(shield)f.addProtection(B,B,target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            m.attack(A,attack.id());var q=m.effectChoicePrompt(A);check(q.optionsZh().size()==9,"Porygon must offer exactly nine printed types");
            int choice=i;CopyAttackTest.reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),choice));m.chooseEffectOption(A,q.resolutionId(),q.step(),i);
            check(f.effectiveWeaknessType(B,target)==(shield?oldType:types.get(i))&&f.effectiveWeaknessMultiplier(B,target)==oldMultiplier,"Porygon changed weakness amount or ignored protection");
            m.endTurn(B);m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());m.endTurn(A);m.endTurn(B);m.endTurn(A);
            check(f.effectiveWeaknessType(B,target)==(shield?oldType:types.get(i)),"Porygon expired by time or source movement");
            m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());
            check(f.effectiveWeaknessType(B,target)==oldType&&f.effectiveWeaknessMultiplier(B,target)==oldMultiplier&&!m.hasPendingEffectChoice(),"Porygon did not clear on target leaving Active");
        }
        System.out.println("ModernWeaknessChoiceRepairTest passed: nine types, no-weakness preservation, protection, authority, multi-turn duration, source movement and target switching");
    }
}
