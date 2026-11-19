package dev.hua.ptcglive.engine;

import java.util.ArrayList;
import java.util.List;

/** Public coin outcomes only; never transmit raw random-operation labels, seeds or hidden-zone draws. */
public final class PublicCoinResults {
    private PublicCoinResults() { }
    public static List<String> describe(List<RandomAuditEvent> events) {
        var result=new ArrayList<String>();
        for(var event:events) {
            String label=event.bound()==2?label(event.operation()):null;
            if(label!=null)result.add("硬币·"+label+"："+(event.result()==0?"正面":"反面"));
        }
        return List.copyOf(result);
    }
    private static String label(String operation) {
        if(operation.equals("coin-flip"))return "开局";
        if(operation.startsWith("attack-confusion:"))return "混乱";
        if(operation.startsWith("trainer-attempt-coin:"))return "训练家使用前判定";
        if(operation.equals("retreat-attempt-coin")||operation.startsWith("retreat-attempt-coin:"))return "撤退前判定";
        if(operation.startsWith("attack-coin-gate:"))return "攻击前判定";
        if(operation.startsWith("coin-copy-attack:"))return "复制招式";
        if(operation.startsWith("attack-coin:")||operation.startsWith("attack-effect-coin:"))return "招式";
        if(operation.startsWith("attack-condition:"))return "招式状态";
        if(operation.startsWith("attack-protection-coin:"))return "招式防护";
        if(operation.startsWith("ability-coin-prevent-damage:"))return "特性防护";
        if(operation.startsWith("effect-coin:"))return "卡牌效果";
        if(operation.startsWith("lucky-bonus:"))return "幸运奖励";
        if(operation.startsWith("coin-prize-reduction:"))return "奖赏减少";
        if(operation.startsWith("checkup-burn:"))return "灼伤恢复";
        if(operation.startsWith("checkup-asleep:"))return "睡眠恢复";
        return null;
    }
}
