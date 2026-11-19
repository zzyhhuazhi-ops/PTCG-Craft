package dev.hua.ptcglive.packs;

import dev.hua.ptcglive.engine.RandomAuditEvent;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only draw and ownership grant. No client-selected card IDs are accepted. */
public final class PackOpeningService {
    public record Result(String setId, String dataVersion, List<CardInstance> cards, List<RandomAuditEvent> audit) { }
    private final CardBoxService ownership;
    public PackOpeningService(CardBoxService ownership) { this.ownership = ownership; }
    /** Physical-pack preview result: transient IDs are not ownership entries. */
    public Result drawPhysical(UUID owner, SetPackDefinition pack, ServerRandom random) {
        int start = random.transcript().size(); List<CardInstance> opened = new ArrayList<>();
        for (PackSlot slot : pack.slots()) for (int i = 0; i < slot.count(); i++)
            opened.add(new CardInstance(UUID.randomUUID(), owner, choose(pack.pools().get(slot.poolId()), random, "pack:" + pack.setId() + ":" + slot.poolId())));
        if (opened.size() != pack.cardsPerPack()) throw new IllegalStateException("卡包配置数量错误");
        return new Result(pack.setId(), pack.dataVersion(), List.copyOf(opened), List.copyOf(random.transcript().subList(start, random.transcript().size())));
    }
    public Result open(UUID owner, SetPackDefinition pack, ServerRandom random) {
        int start = random.transcript().size(); List<CardInstance> opened = new ArrayList<>();
        for (PackSlot slot : pack.slots()) for (int i = 0; i < slot.count(); i++) {
            String cardId = choose(pack.pools().get(slot.poolId()), random, "pack:" + pack.setId() + ":" + slot.poolId());
            opened.add(ownership.mint(owner, cardId));
        }
        if (opened.size() != pack.cardsPerPack()) throw new IllegalStateException("卡包配置未产生 " + pack.cardsPerPack() + " 张卡");
        return new Result(pack.setId(), pack.dataVersion(), List.copyOf(opened), random.transcript().subList(start, random.transcript().size()));
    }
    /** Deposit existing server draws without drawing again; restore cards and discoveries on failure. */
    public void storeInCollection(UUID owner, SetPackDefinition pack, List<Result> results, Runnable persistAudit) {
        PackBatchPolicy.validateCount(results.size());
        var pool=dev.hua.ptcglive.collection.CollectionProgress.poolIds(pack);
        var instances=new java.util.HashSet<UUID>();
        for(var result:results) {
            if(!pack.setId().equals(result.setId())||!pack.dataVersion().equals(result.dataVersion())||result.cards().size()!=pack.cardsPerPack())
                throw new IllegalArgumentException("开包结果与当前卡包不符");
            for(var card:result.cards())if(!owner.equals(card.ownerId())||!pool.contains(card.definitionId())||!instances.add(card.instanceId()))
                throw new IllegalArgumentException("开包卡牌归属、卡池或实例无效");
        }
        ownership.transaction(()->{
            for(var result:results)for(var card:result.cards())ownership.mint(owner,card.definitionId());
            return null;
        },persistAudit);
    }
    /** Exact relative chance shown to the player for a particular slot, derived from opening weights. */
    public static double probability(Map<String, Integer> weights, String cardId) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum(); Integer weight = weights.get(cardId);
        return weight == null ? 0 : (double) weight / total;
    }
    private static String choose(Map<String, Integer> weights, ServerRandom random, String operation) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum(); int draw = random.nextInt(operation, total);
        int cursor = 0; for (Map.Entry<String, Integer> entry : weights.entrySet()) { cursor += entry.getValue(); if (draw < cursor) return entry.getKey(); }
        throw new IllegalStateException("卡包权重无法选择结果");
    }
}
