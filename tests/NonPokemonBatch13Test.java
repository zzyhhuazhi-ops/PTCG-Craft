import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class NonPokemonBatch13Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void fill(ServerMatch m,UUID owner){while(m.benchPokemonView(owner).size()<5)m.playBasicPokemonToBench(owner,DynamicPassiveModifierTest.hand(m,owner,"basic").instanceId());}
    public static void main(String[] args){avery();collapsed();System.out.println("NonPokemonBatch13Test passed: successful draw gates opponent-owned discard, no prizes or permanent Avery limit, Stadium controller discards first and four-bench limit expires");}
    static void avery(){
        for(boolean emptyDeck:List.of(false,true)){
            var m=NonPokemonBatch2Test.game("swsh6_en_130",EnergyType.GRASS,1);fill(m,A);m.endTurn(A);fill(m,B);m.endTurn(B);
            if(emptyDeck)m.drawCardsByEffect(A,A,m.privateView(A).deck().size());var card=NonPokemonBatch2Test.trainer(m);
            check(m.canPlayTrainer(A,card.instanceId(),null)==!emptyDeck,"Avery accepted a no-draw play");if(emptyDeck)continue;
            int hand=m.privateView(A).hand().size(),prizes=m.privateView(A).prizes().size();m.playTrainer(A,card.instanceId(),null);
            check(m.privateView(A).hand().size()==hand+2,"Avery did not draw three before Bench discard");
            for(int i=0;i<2;i++){
                var prompt=m.toolOverflowPrompt();check(prompt!=null&&prompt.bench()&&prompt.owner().equals(B),"Avery gave discard choice to wrong player");var selected=prompt.tools().getFirst().instanceId();
                GeneratedRuleBatch8Test.reject(()->m.discardOverflowTool(A,prompt.id(),selected));m.discardOverflowTool(B,prompt.id(),selected);
            }
            NonPokemonBatch2Test.settle(m);check(m.benchPokemonView(A).size()==5&&m.benchPokemonView(B).size()==3&&m.privateView(A).prizes().size()==prizes,"Avery affected own Bench or awarded prizes");
            m.endTurn(A);m.playBasicPokemonToBench(B,DynamicPassiveModifierTest.hand(m,B,"basic").instanceId());check(m.benchPokemonView(B).size()==4,"Avery installed a permanent Bench limit");
        }
    }
    static void collapsed(){
        var m=NonPokemonBatch2Test.game("swsh11_en_215",EnergyType.GRASS,1);fill(m,A);m.endTurn(A);fill(m,B);
        m.playTrainer(B,DynamicPassiveModifierTest.hand(m,B,"trainer").instanceId(),null);
        for(UUID owner:List.of(B,A)){
            var prompt=m.toolOverflowPrompt();check(prompt!=null&&prompt.bench()&&prompt.owner().equals(owner),"Collapsed Stadium did not discard controller's Bench first");
            m.discardOverflowTool(owner,prompt.id(),prompt.tools().getFirst().instanceId());
        }
        check(m.benchLimit(A)==4&&m.benchLimit(B)==4&&m.benchPokemonView(A).size()==4&&m.benchPokemonView(B).size()==4,"Collapsed Stadium did not enforce four on both sides");
        m.discardStadiumByEffect(B);check(m.benchLimit(A)==5&&m.benchLimit(B)==5,"Collapsed Stadium restriction remained after leaving");
        m.playBasicPokemonToBench(B,DynamicPassiveModifierTest.hand(m,B,"basic").instanceId());
    }
}
