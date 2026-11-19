import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** New supported cards must never introduce an unimplemented whole-attack retry combination. */
public final class AttackCoinCatalogTest {
    static boolean coin(EffectDefinition e){return e.type().equals("coin_flip")||e.effects().stream().anyMatch(AttackCoinCatalogTest::coin);}
    public static void main(String[] args) {
        var data=BundledCombatData.load();int attacks=0,coins=0;var problems=new ArrayList<String>();
        for(var card:BundledRules.loadCardDefinitions().all())for(var profile:data.pokemon(card.id()).stream().toList())for(var attack:profile.attacks()) {
            if(attack.effectSupport()!=EffectSupport.SUPPORTED)continue;
            attacks++;
            boolean has=attack.coinDamage()!=null||attack.coinProtection()!=null||attack.conditions().stream().anyMatch(c->c.coinCondition()!=null)
                ||attack.effectProgram().triggers().stream().flatMap(b->b.effects().stream()).anyMatch(AttackCoinCatalogTest::coin);
            if(!has)continue;
            coins++;
            try {AttackCoinRetry.requireSupportedPlan(attack);}
            catch(RuntimeException e){problems.add(card.id()+"/"+attack.id()+": "+e.getMessage());}
        }
        if(!problems.isEmpty())throw new AssertionError("Extend attack-wide retry support before promoting these card programs: "+problems);
        if(attacks<14000||coins<1700)throw new AssertionError("Expected complete bundled attack catalog");
        System.out.println("AttackCoinCatalogTest passed: "+attacks+" supported attack definitions, all "+coins+" coin attacks compatible with whole-attack retry; includes attacks on otherwise unsupported cards and all source types for copying.");
    }
}
