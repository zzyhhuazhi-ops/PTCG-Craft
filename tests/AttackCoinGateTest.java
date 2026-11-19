import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * attack_coin_gate: “If the Defending Pokémon tries to attack during your opponent's next turn, your opponent flips a coin.
 * If tails, that attack does nothing.” (newer wording: “… that attack doesn't happen.”)
 */
public final class AttackCoinGateTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static final String GATE="{\"type\":\"attack_coin_gate\",\"target\":\"OPPONENT_ACTIVE\"}";
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static List<RandomAuditEvent> events(ServerMatch m,int from,String prefix){
        var all=m.randomTranscript();
        return all.subList(from,all.size()).stream().filter(e->e.operation().startsWith(prefix)).toList();
    }
    public static void main(String[] args){
        alternatingAttacks(); expiresAtOwnerTurnStart(); clearedBySwitch(); gateBeforeConfusion(); invalidData();
        System.out.println("AttackCoinGateTest passed: 防守宝可梦下个回合攻击前掷硬币，反面招式不发生");
    }
    /** Both players use the gating attack; a tails attack never happens, so it also places no new gate. */
    static void alternatingAttacks(){
        var a=BeforeDamageAttackTest.attack("","",GATE);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"攻击前掷币效果被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        var gated=new HashMap<UUID,Boolean>(Map.of(A,false,B,false));
        boolean heads=false,tails=false;
        for(int i=0;i<24&&!(heads&&tails);i++){
            UUID attacker=m.currentPlayer(),defender=attacker.equals(A)?B:A;
            int from=m.randomTranscript().size(),before=m.activePokemonView(defender).damage();
            m.attack(attacker,a.id());
            var flips=events(m,from,"attack-coin-gate:"+attacker);
            int dealt=m.activePokemonView(defender).damage()-before;
            if(!gated.get(attacker)){
                check(flips.isEmpty()&&dealt==80,"没有被施加效果的宝可梦不应掷币");
                gated.put(defender,true);
            } else {
                check(flips.size()==1,"被施加效果的宝可梦攻击前应恰好掷 1 次");
                boolean head=flips.getFirst().result()==0;
                check(dealt==(head?80:0),"掷币结果与招式是否发生不一致：正面="+head+" 伤害="+dealt);
                gated.put(defender,head);
                heads|=head; tails|=!head;
            }
            gated.put(attacker,false);
            check(m.currentPlayer().equals(defender),"攻击后（含反面）应轮到对手");
            if(m.activePokemonView(defender).damage()>0)
                m.healDamageByEffect(defender,defender,m.activePokemonView(defender).pokemonId(),m.activePokemonView(defender).damage());
        }
        check(heads&&tails,"多次攻击没有同时出现正面和反面");
    }
    static void expiresAtOwnerTurnStart(){
        var gate=BeforeDamageAttackTest.attack("","",GATE);
        var m=BeforeDamageAttackTest.fixture(gate);
        m.attack(A,gate.id());
        m.endTurn(B);
        m.endTurn(A);
        int from=m.randomTranscript().size();
        m.attack(B,gate.id());
        check(events(m,from,"attack-coin-gate:").isEmpty(),"效果应在施加方下个回合开始时失效");
    }
    static void clearedBySwitch(){
        var gate=BeforeDamageAttackTest.attack("","",GATE);
        var m=BeforeDamageAttackTest.fixture(gate);
        m.attack(A,gate.id());
        UUID first=m.activePokemonView(B).pokemonId();
        m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());
        m.switchActiveByEffect(B,B,first);
        int from=m.randomTranscript().size();
        m.attack(B,gate.id());
        check(events(m,from,"attack-coin-gate:").isEmpty(),"换到备战区再换回来后，招式效果应已清除");
    }
    /** Official advanced rules A-01 step2-a precedes Confusion step2-b. */
    static void gateBeforeConfusion(){
        boolean heads=false,tails=false;
        for(int offset=0;offset<24&&(!heads||!tails);offset++){
            var gate=BeforeDamageAttackTest.attack("","",GATE);
            var m=BeforeDamageAttackTest.fixture(gate);m.attack(A,gate.id());
            if(offset>0)m.drawCardsByEffect(B,B,offset);
            m.searchOwnDeckToHandByEffect(B,List.of(m.privateView(B).deck().getFirst().instanceId()));
            m.applySpecialConditionByEffect(B,B,SpecialCondition.CONFUSED);
            int damage=m.activePokemonView(B).damage(),from=m.randomTranscript().size();m.attack(B,gate.id());
            var all=m.randomTranscript().subList(from,m.randomTranscript().size());
            var confusion=all.stream().filter(e->e.operation().startsWith("attack-confusion:")).toList();
            var flips=all.stream().filter(e->e.operation().startsWith("attack-coin-gate:")).toList();
            check(flips.size()==1,"attack-failure gate must be checked first");
            if(flips.getFirst().result()!=0){tails=true;check(confusion.isEmpty()&&m.activePokemonView(B).damage()==damage,"failed gate must not flip Confusion or cause its counters");}
            else {heads=true;check(confusion.size()==1&&flips.getFirst().sequence()<confusion.getFirst().sequence(),"Confusion must follow successful gate");}
        }
        check(heads&&tails,"gate coin branches not both covered");
    }
    static void invalidData(){
        BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","","{\"type\":\"attack_coin_gate\",\"target\":\"SELF\"}")));
        BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","","{\"type\":\"attack_coin_gate\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}")));
        BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(GATE,"","")));
    }
}
