import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class ModernPlayerCoinRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        boolean checkupHeads=false;
        for(String id:List.of("sv3-5_en_054","sv3-5_en_175"))for(int seed=0;seed<12;seed++)for(boolean legacy:List.of(false,true))for(boolean switchTarget:List.of(false,true))for(boolean guard:List.of(false,true)){
            var attack=ModernWindowDamageRepairTest.attack(id,0);var follow=ModernWindowDamageRepairTest.attack(legacy?"sv1_en_012":"sv3-5_en_073",legacy?0:1);
            var m=ModernWindowDamageRepairTest.game(attack,seed,false,List.of(follow));m.drawCardsByEffect(A,B,20);
            UUID target=switchTarget?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
            ModernRuleRepair2Test.pay(m,B,target,"energy",1);ModernRuleRepair2Test.pay(m,B,target,"special",1);
            if(guard)EventTargetBindingTest.field(m).addProtection(B,B,m.activePokemonView(B).pokemonId(),new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            m.attack(A,attack.id());check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Overthink did not finish attack");
            m.switchActiveByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());if(switchTarget)m.switchActiveByEffect(B,B,target);
            check(m.flipCoinsByEffect(B,20).stream().allMatch(c->c==CoinSide.TAILS),"Player restriction lost after switching or blocked by Pokemon protection");
            EventTargetBindingTest.field(m).applySpecialCondition(A,SpecialCondition.ASLEEP);int rng=m.randomTranscript().size();
            m.attack(B,follow.id());check(m.lastAttack().finalDamage()==(legacy?30:0)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Legacy/named attack coins ignored Overthink");
            var coins=m.randomTranscript().subList(rng,m.randomTranscript().size());
            check(coins.stream().filter(c->!c.operation().startsWith("checkup")).allMatch(c->c.result()==1),"Audit logged raw heads instead of treated tails");
            checkupHeads|=coins.stream().anyMatch(c->c.operation().startsWith("checkup")&&c.result()==0);
            check(m.flipCoinsByEffect(A,20).contains(CoinSide.HEADS),"Opponent restriction leaked to source player");
            m.endTurn(A);check(m.flipCoinsByEffect(B,20).contains(CoinSide.HEADS),"Restriction persisted past one opposing turn");
        }
        check(checkupHeads,"Pokemon Checkup coins were incorrectly forced");
        for(int seed=0;seed<32;seed++){
            var forced=new ServerRandom(seed);var control=new ServerRandom(seed);
            for(int i=0;i<20;i++){check(!forced.coinHeads("coin",true),"Forced heads");control.nextInt("coin",2);}
            check(forced.nextInt("shuffle",60)==control.nextInt("shuffle",60),"Coin treatment changed shuffle RNG consumption");
            check(forced.transcript().subList(0,20).stream().allMatch(c->c.result()==1),"False audit coin result");
        }
        System.out.println("ModernPlayerCoinRepairTest passed: Psyduck two prints, 192 player-level restriction scenarios, legacy/named attack coins, source/target switching, Pokemon protection, independent players, checkup exclusion and expiry; 32 RNG isolation seeds");
    }
}
