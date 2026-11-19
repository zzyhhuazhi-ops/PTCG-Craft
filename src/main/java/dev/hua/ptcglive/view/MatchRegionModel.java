package dev.hua.ptcglive.view;

import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.field.BattlefieldState.PokemonView;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import java.util.*;

/** Pure read-only presentation over recipient-safe DTOs. No server/private state or arbitrary-card lookup. */
public record MatchRegionModel(UUID matchId, UUID viewingSeat, List<Zone> zones) {
    public record Card(String key, String definitionId, String nameZh, String detailZh) { }
    public record Zone(String id, String nameZh, List<Card> cards, int hiddenCount, String noteZh) {
        public Zone { cards = List.copyOf(cards); }
        public int size() { return cards.size() + hiddenCount; }
    }
    public MatchRegionModel { zones = List.copyOf(zones); }
    public Zone zone(String id) { return zones.stream().filter(z -> z.id().equals(id)).findFirst().orElseThrow(); }
    public List<Card> visibleCards() { return zones.stream().flatMap(z -> z.cards().stream()).toList(); }
    public boolean allowsPreview(UUID originalSeat, String key) {
        return Objects.equals(originalSeat, viewingSeat) && visibleCards().stream().anyMatch(c -> c.key().equals(key));
    }
    public static MatchRegionModel from(MatchSession.Snapshot snapshot) {
        var game = snapshot.game(); Map<String, String> names = snapshot.cardNames(); List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < game.players().size(); i++) {
            var player = game.players().get(i); String prefix = player.id().toString();
            String who = player.id().equals(game.viewingSeat()) ? "我方" : game.viewingSeat() == null ? "席位 " + (i + 1) : "对方";
            List<Card> active = player.active() == null ? List.of() : List.of(pokemon(player.active(), "战斗位", names));
            zones.add(new Zone(prefix + ":active", who + " · 战斗位", active, 0, "尚未放置或尚未公开的卡牌不会显示卡面。"));
            List<Card> bench = new ArrayList<>(), attachments = new ArrayList<>();
            if (player.active() != null) addAttachments(attachments, player.active(), "战斗位", names);
            for (int index = 0; index < player.bench().size(); index++) {
                var field = player.bench().get(index); String where = "备战 " + (index + 1);
                bench.add(pokemon(field, where, names)); addAttachments(attachments, field, where, names);
            }
            zones.add(new Zone(prefix + ":bench", who + " · 备战区", bench, 0, "只展示服务器已公开的备战宝可梦。"));
            zones.add(new Zone(prefix + ":attachments", who + " · 能量／工具／进化底牌", attachments, 0, "已附着工具保留至规则要求移除。"));
            var discard = player.discard().stream().map(c -> card("discard:" + c.instanceId(), c.definitionId(), "公开弃牌", names)).toList();
            zones.add(new Zone(prefix + ":discard", who + " · 弃牌区", discard, 0, "只读查看，不改变弃牌顺序或对局状态。"));
            var lost = player.lostZone().stream().map(c -> card("lost:" + c.instanceId(), c.definitionId(), "公开放逐", names)).toList();
            zones.add(new Zone(prefix + ":lost", who + " · 放逐区", lost, 0, "放逐区为公开区域；通用规则不允许从此区域取回卡牌。"));
            var hand = player.id().equals(game.viewingSeat()) ? game.hand().stream()
                .map(c -> card("hand:" + c.instanceId(), c.definitionId(), "本人手牌", names)).toList() : List.<Card>of();
            zones.add(new Zone(prefix + ":hand", who + " · 手牌", hand, Math.max(0, player.handSize() - hand.size()),
                "他人手牌及决定先后手前的起手牌只显示背面与数量。"));
            zones.add(new Zone(prefix + ":deck", who + " · 牌库", List.of(), player.deckSize(), "任何视角都不接收牌库顺序或卡面。"));
            zones.add(new Zone(prefix + ":prizes", who + " · 奖赏区", List.of(), player.prizeCount(), "仅显示背面；拿奖请返回对局操作面板。"));
        }
        var stadium = game.stadium();
        if (game.effectAttachment() != null) {
            var attachment = game.effectAttachment();
            zones.add(new Zone("effect-attachment", "待附着的能量", attachment.cards().stream()
                .map(c -> card("effect-source:" + c.instanceId(), c.definitionId(), attachment.assignedTargets().containsKey(c.instanceId())
                    ? "已分配到服务器目标；可再次拖动修改" : attachment.actionZh().equals("分配能量")
                        ? "拖到目标宝可梦；每张能量须分别分配" : "拖到目标宝可梦，统一附着所选能量", names)).toList(),
                0, "仅本人可见；选择目标前卡牌仍留在原区域。"));
        }
        if (game.effectReveal() != null) {
            var reveal = game.effectReveal();
            var shown = new ArrayList<Card>();
            for (int i = 0; i < reveal.cardIds().size(); i++) shown.add(card("reveal:" + reveal.id() + ":" + i,
                reveal.cardIds().get(i), "公开展示 · 不含实际卡牌实例编号", names));
            zones.add(reveal.titleZh() != null
                ? new Zone("effect-reveal", reveal.titleZh(), shown, 0, "效果的使用者查看后确认，服务器再继续结算。")
                : new Zone("effect-reveal", "公开展示的检索卡牌", shown, 0, "对手查看后确认，服务器再继续结算。"));
        }
        if(game.toolOverflow()!=null) {
            var overflow=game.toolOverflow();
            zones.add(new Zone("tool-overflow",overflow.bench()?"备战区超出当前上限":"工具超出当前容量",overflow.tools().stream()
                .map(c->card("tool-overflow:"+c.instanceId(),c.definitionId(),"点击弃置此"+(overflow.bench()?"备战宝可梦":"工具")+" · 还需弃置 "+overflow.excess()+" 张",names))
                .toList(),0,overflow.bench()?"备战宝可梦是公开信息；由其所属玩家选择。":"已附着工具是公开信息；只能由其所属玩家选择。"));
        }
        if (game.effectSelection() != null) {
            var selection = game.effectSelection();
            zones.add(new Zone("effect-selection", selection.labelZh(), selection.visibleCards().stream()
                .map(c -> card("effect:" + c.instanceId(), c.definitionId(), !selection.candidates().contains(c) ? "仅查看 · 不符合选牌条件"
                    : selection.selected().contains(c.instanceId()) ? "已选择 · 再次点击取消" : "点击选择", names))
                .toList(), 0, selection.inspectedCards().isEmpty() ? "仅本人可见；按名称与实例排序，不代表牌库顺序。"
                    : "仅本人可见；查看这些牌后，选择符合条件的卡或不选，确认后重洗牌库。"));
        }
        if (!game.resolvingTrainerCards().isEmpty()) {
            zones.add(new Zone("resolving", "正在结算的训练家", game.resolvingTrainerCards().stream().map(played->
                card("resolving:"+played.instanceId(),played.definitionId(),"已公开使用，等待效果选择；完整结算后弃置",names)).toList(),
                0,game.resolvingTrainerCards().size()>1?"两张卡同时使用，效果只结算一次；均不属于手牌或弃牌区。":"此卡不属于手牌或弃牌区。"));
        }
        zones.add(new Zone("stadium", "双方共用 · 场地", game.stadiumCards().stream().map(part->
            card("stadium:"+part.instanceId(),part.definitionId(),game.stadiumCards().size()==2?"传奇竞技场 · 两张卡共同组成一个场地":
                String.join("；",snapshot.guidance().fieldStatus().getOrDefault("stadium:"+part.instanceId(),List.of("公开场地 · 双方适用"))),names)).toList(),0,
            "双方共用一个场地；传奇竞技场由左右两张共同组成，同名场地不能重复放置。"));
        return new MatchRegionModel(game.matchId(), game.viewingSeat(), zones);
    }
    private static Card card(String key, String id, String detail, Map<String, String> names) {
        return new Card(key, id, names.getOrDefault(id, id), detail);
    }
    private static Card pokemon(PokemonView field, String where, Map<String, String> names) {
        String states = field.conditions().isEmpty() ? "无异常" : String.join("、", field.conditions().stream().map(MatchRegionModel::condition).toList());
        String continuous = field.continuousEffectZh().isBlank() ? "" : " · " + field.continuousEffectZh();
        // Stable field key deliberately excludes the top-card ID: a selected
        // battlefield position stays the same when it evolves, while the server
        // still validates every resulting action against the current revision.
        return card("field:" + field.pokemonId(), field.topCardId(),
            where + " · 体力 " + field.hitPoints() + " · 伤害 " + field.damage() + " · 能量 " + field.attachedEnergyIds().size() + " · " + states + continuous, names);
    }
    private static void addAttachments(List<Card> result, PokemonView field, String where, Map<String, String> names) {
        String owner = where + " " + names.getOrDefault(field.topCardId(), field.topCardId());
        // Evolution stack includes the current top card, already shown in Active/Bench; show only underlying cards here.
        for (int i = 0; i + 1 < field.evolutionCardIds().size(); i++) {
            String id = field.evolutionCardIds().get(i);
            result.add(card("evolution:" + field.pokemonId() + ":" + i + ":" + id, id, owner + " · 进化底牌 " + (i + 1), names));
        }
        for (int i = 0; i < field.attachedEnergyCardIds().size(); i++)
            result.add(card("energy:" + field.attachedEnergyIds().get(i), field.attachedEnergyCardIds().get(i), owner + " · 已附着能量", names));
        for (var tool : field.attachedTools())
            result.add(card("tool:" + tool.instanceId(), tool.definitionId(), owner + " · 已附着工具", names));
    }
    private static String condition(SpecialCondition condition) { return switch (condition) {
        case POISONED -> "中毒"; case BURNED -> "灼伤"; case ASLEEP -> "睡眠"; case PARALYZED -> "麻痹"; case CONFUSED -> "混乱";
    }; }
}
