import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardEffectAuditCatalog;
import dev.hua.ptcglive.rules.EffectSupport;

/** Fails closed if a future supported card is added without effect-text evidence. */
public final class CardEffectAuditTest {
    public static void main(String[] args) {
        var database = BundledRules.loadCardDefinitions();
        var audit = CardEffectAuditCatalog.loadBundled();
        long supported = database.all().stream().filter(card -> card.effectSupport() == EffectSupport.SUPPORTED).count();
        if (supported != audit.auditedCardIds().size()) throw new AssertionError("已支持卡与审核来源数量不一致");
        if (audit.evidence("sv5_en_129") == null || audit.evidence("sd100_cn_144") == null
                || audit.evidence("sd100_cn_373") == null || audit.evidence("sd100_cn_315") == null)
            throw new AssertionError("真实卡或已审核预组卡缺少来源证据");
        for (var card : database.all())
            if ((card.effectSupport() == EffectSupport.SUPPORTED) != (audit.evidence(card.id()) != null))
                throw new AssertionError("卡牌支持状态与逐卡证据不一致：" + card.id());
        System.out.println("CardEffectAuditTest passed: " + supported + " supported cards require one audited source each");
    }
}
