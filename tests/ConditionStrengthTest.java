import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * “Your opponent's Active Pokémon is now Poisoned. Put N damage counters instead of 1 on that Pokémon between turns.”
 * poison / burn with counters: the Pokémon keeps this condition's strength and Pokémon Checkup uses it.
 */
public final class ConditionStrengthTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    public static void main(String[] args){
        strongPoisonEveryCheckup(); newPoisonReplacesOld(); strongBurn(); separatePublicConditionDamage(); invalidData();
        System.out.println("ConditionStrengthTest passed: 加重中毒／灼伤（宝可梦检查按强度放指示物、每次检查都生效、新的中毒取代旧的）");
    }
    static void strongPoisonEveryCheckup(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":3}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"加重中毒被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        // 攻击 80，回合之间中毒放 3 个
        check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED),"没有中毒");
        check(m.activePokemonView(B).damage()==80+30,"宝可梦检查时应放 3 个伤害指示物，实际 "+m.activePokemonView(B).damage());
        m.endTurn(B);
        check(m.activePokemonView(B).damage()==80+30+30,"下一次宝可梦检查仍应放 3 个，实际 "+m.activePokemonView(B).damage());
    }
    static void newPoisonReplacesOld(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":3}");
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        int before=m.activePokemonView(B).damage();
        // B 的回合：B 的攻击使 A 中毒（3 个）；A 这边再被普通中毒取代
        m.applySpecialConditionByEffect(B,B,SpecialCondition.POISONED);
        m.endTurn(B);
        check(m.activePokemonView(B).damage()==before+10,"普通中毒应取代加重中毒（只放 1 个），实际增加 "+(m.activePokemonView(B).damage()-before));
    }
    static void strongBurn(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"burn\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":4}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"加重灼伤被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80+40,"灼伤检查时应放 4 个伤害指示物，实际 "+m.activePokemonView(B).damage());
    }
    static void separatePublicConditionDamage(){
        var attack=BeforeDamageAttackTest.attack("","","{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":3}");
        var match=BeforeDamageAttackTest.fixture(attack);
        match.enablePublicEvents();
        var target=match.activePokemonView(B).pokemonId();
        match.applySpecialConditionByEffect(A,B,SpecialCondition.BURNED);
        match.attack(A,attack.id());
        var hits=match.publicEvents().stream().map(BattleEventJournal.Event::cue)
            .filter(c->c.kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.DAMAGE&&target.equals(c.pokemon())).toList();
        check(hits.stream().map(c->c.amount()).toList().equals(List.of(80,30,20)),"重复或丢失已结算的伤害");
        check(hits.get(1).text().equals("中毒伤害")&&hits.get(2).text().equals("灼伤伤害"),"回合间伤害顺序或原因错误");
    }
    static void invalidData(){
        for(String bad:List.of(
            "{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":1}",
            "{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":61}",
            "{\"type\":\"burn\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":2}",
            "{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":3}",
            "{\"type\":\"paralyze\",\"target\":\"OPPONENT_ACTIVE\",\"counters\":3}"))
            BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","",bad)));
    }
}
