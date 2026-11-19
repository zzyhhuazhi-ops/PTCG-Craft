package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collectibles.CardCondition;
import dev.hua.ptcglive.rental.RentalBlockEntity;
import dev.hua.ptcglive.rental.RentalProduction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Catalog previews describe possible grades, never pretend an ungraded collection copy is appraised. */
final class CardRentalInfo {
    private CardRentalInfo() { }
    static String summary(dev.hua.ptcglive.CardCatalog.Entry card){return "基础 "+RentalBlockEntity.rate(card.id(),card.rarityZh())+"币/分";}
    static List<Text> append(List<Text> existing,dev.hua.ptcglive.CardCatalog.Entry card){
        var lines=new ArrayList<>(existing);int rate=RentalBlockEntity.rate(card.id(),card.rarityZh());
        lines.add(Text.literal(dev.hua.ptcglive.CardRarity.classificationLabel(card.id())).formatted(Formatting.GRAY));
        String tier=dev.hua.ptcglive.CardRarity.economy(card.id(),card.rarityZh());
        lines.add(Text.literal("计价档："+(tier.equals("PROMO")?"宣传卡（基础档）":tier.equals("UNKNOWN")?"待核对（暂按基础档）":tier)).formatted(Formatting.GRAY));
        lines.add(Text.literal("租赁每分钟收益 · v1/v2/v3 相同").formatted(Formatting.GOLD));
        lines.add(Text.literal("品质：普通 / 红石×1.5 / 青金石×2").formatted(Formatting.GRAY));
        for(var grade:CardCondition.values()){
            String label=grade==CardCondition.ORDINARY?"未鉴定／平平无奇":grade.label;
            lines.add(Text.literal(label+"："+amount(rate,grade.percent,2)+" / "+amount(rate,grade.percent,3)+" / "+amount(rate,grade.percent,4)));
        }
        lines.add(Text.literal("单位：宝可币/分；不足1币的部分累计结算").formatted(Formatting.GRAY));
        return lines;
    }
    private static String amount(int rate,int percent,int multiplier){
        return BigDecimal.valueOf(RentalProduction.incomeEighthsPerMinute(rate,percent,multiplier))
            .divide(BigDecimal.valueOf(8)).stripTrailingZeros().toPlainString();
    }
}
