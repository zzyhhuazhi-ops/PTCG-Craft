import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class ModernPrizeRevealRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){
        for(String id:List.of("sv6-5_en_021","sv6-5_en_071"))for(int slot=0;slot<=6;slot++)for(boolean weak:List.of(false,true)){
            var attack=ModernWindowDamageRepairTest.attack(id,1);var m=ModernWindowDamageRepairTest.game(attack,0,weak);var prizes=m.privateView(A).prizes();int rng=m.randomTranscript().size();
            m.attack(A,attack.id());var p=m.effectChoicePrompt(A);check(p.optionsZh().size()==7&&m.activePokemonView(B).damage()==0,"Prize choice missing or damage happened first");
            check(m.clientView(B).players().stream().filter(v->v.id().equals(A)).findFirst().orElseThrow().faceUpPrizes().isEmpty(),"Prize face leaked before choosing");
            int option=slot;CopyAttackTest.reject(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),option));m.chooseEffectOption(A,p.resolutionId(),p.step(),option);
            check(m.activePokemonView(B).damage()==(slot<6?160:80)*(weak?2:1)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Wrong conditional damage or continuation");
            var face=m.clientView(B).players().stream().filter(v->v.id().equals(A)).findFirst().orElseThrow().faceUpPrizes();
            check(face.size()==(slot<6?1:0)&&m.privateView(A).prizes().equals(prizes)&&m.randomTranscript().size()==rng,"Prize reveal moved cards, exposed extra face or consumed RNG");
            if(slot<6)check(face.getFirst().slot()==slot&&face.getFirst().cardId().equals(prizes.get(slot).definitionId()),"Prize reveal ignored chosen position");
            CopyAttackTest.reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),option));
        }
        for(String id:List.of("sv6-5_en_021","sv6-5_en_071")){
            var attack=ModernWindowDamageRepairTest.attack(id,1);var m=ModernWindowDamageRepairTest.game(attack,0,false);
            for(int i=0;i<6;i++){
                m.attack(A,attack.id());var p=m.effectChoicePrompt(A);check(p.optionsZh().size()==7-i,"Previously face-up prize offered again");m.chooseEffectOption(A,p.resolutionId(),p.step(),0);m.endTurn(B);
            }
            int before=m.activePokemonView(B).damage();m.attack(A,attack.id());check(!m.hasPendingEffectChoice()&&m.activePokemonView(B).damage()==before+80&&m.currentPlayer().equals(B),"All face-up prizes stalled or gave bonus");
        }
        System.out.println("ModernPrizeRevealRepairTest passed: two prints all six positions and decline, public face after selection only, weakness, successive exclusions, all-face-up fallback, no movement or duplicate submission");
    }
}
