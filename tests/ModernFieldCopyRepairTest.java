import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernFieldCopyRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    public static void main(String[] args){
        var attack=ModernWindowDamageRepairTest.attack("sv1_en_083",0);Set<Boolean> outcomes=new HashSet<>();
        for(int seed=0;seed<32;seed++)for(boolean bench:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(attack,seed,false,List.of(GeneratedRuleBatch8Test.hit(40,false)));
            UUID source=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
            var options=m.copyAttackOptions(A,attack.id());var selected=options.stream().filter(o->o.pokemonId().equals(source)&&o.attackId().equals("hit")).findFirst().orElseThrow();
            GeneratedRuleBatch8Test.check(options.stream().noneMatch(o->o.owner().equals(A)),"Hypno exposed own source");
            int rng=m.randomTranscript().size();
            CopyAttackTest.reject(()->m.copyAttackWithEnergyDiscards(A,attack.id(),source,selected.entrySequence()+1,"hit",List.of(),List.of()));
            GeneratedRuleBatch8Test.check(m.randomTranscript().size()==rng,"Stale copy consumed randomness");
            m.attack(A,attack.id());
            var coin=m.randomTranscript().subList(rng,m.randomTranscript().size()).stream().filter(r->r.operation().startsWith("coin-copy-attack:")).toList();
            GeneratedRuleBatch8Test.check(coin.size()==1,"Hypno did not use exactly one server coin");boolean heads=coin.getFirst().result()==0;outcomes.add(heads);
            GeneratedRuleBatch8Test.check(m.hasPendingTopDeckAttackCopy()==heads,"Copy selection must follow heads, never precede toss");
            if(heads) {
                var p=m.topDeckAttackCopyPrompt(A);var before=m.randomTranscript();
                var view=MatchSession.multiplayer(m,A,B).view(A,A);
                GeneratedRuleBatch8Test.check(dev.hua.ptcglive.view.CopyAttackChoicePage.active(view)
                    &&view.options().stream().anyMatch(o->o.cardKeys().contains("copy-attack:hit")&&o.cardKeys().contains("copy-source:"+selected.topCardId())),
                    "Heads did not expose the public copied attack");
                CopyAttackTest.reject(()->m.declineTopDeckCopiedAttack(A,p.resolutionId(),p.step()));
                CopyAttackTest.reject(()->m.chooseTopDeckCopiedAttack(B,p.resolutionId(),p.step(),source,"hit",null,List.of(),List.of(),null));
                m.chooseTopDeckCopiedAttack(A,p.resolutionId(),p.step(),source,"hit",null,List.of(),List.of(),null);
                GeneratedRuleBatch8Test.check(before.equals(m.randomTranscript()),"Selecting copied attack tossed another coin");
                CopyAttackTest.reject(()->m.chooseTopDeckCopiedAttack(A,p.resolutionId(),p.step(),source,"hit",null,List.of(),List.of(),null));
            }

            GeneratedRuleBatch8Test.check(m.activePokemonView(B).damage()==(heads?40:0)&&m.benchPokemonView(B).getFirst().damage()==0&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Hypno wrong copied damage target, coin or continuation");
            CopyAttackTest.reject(()->m.copyAttackWithEnergyDiscards(A,attack.id(),source,selected.entrySequence(),"hit",List.of(),List.of()));
        }
        GeneratedRuleBatch8Test.check(outcomes.size()==2,"Hypno missing coin branch");
        System.out.println("ModernFieldCopyRepairTest passed: Hypno Active/Bench public sources, heads/tails, stale entry rejected without coin, original damage target and completed turn");
    }
}
