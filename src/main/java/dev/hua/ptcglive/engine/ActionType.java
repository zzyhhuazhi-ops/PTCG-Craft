package dev.hua.ptcglive.engine;

/** Wire-stable action identity. Display wording is not an AI command. */
public enum ActionType {
    RECOVER_SUPPORTER,
    ATTACK, ATTACH, EVOLVE, RETREAT, ABILITY, DISCARD_ABILITY, TRAINER, STADIUM, TOOL_ABILITY,
    BENCH, REPLACEMENT, END_TURN, CONCEDE, CARD_DESELECT, CARD_SELECT, CARD_CONFIRM, RETREAT_CONFIRM, RETREAT_CANCEL, DAMAGE_CONFIRM, ENERGY_CONFIRM, ENERGY_ASSIGN, ACK_REVEAL, ACK_MULLIGAN, TARGET_FINISH, TARGET_SELECT, TRIGGER_USE, TRIGGER_SKIP, TRIGGER_ORDER, PRIZE_PICK, PRIZE_VIEW, PRIZE_REVEAL, PRIZE_CONFIRM, PRIZE_SELECT, PRIZE_DESELECT, SWAP_SKIP, COPY_SKIP, PRIZE_SWAP, CHOICE, OVERFLOW, COIN, GO_FIRST, GO_SECOND, MULLIGAN_DRAW, OPEN_ACTIVE, OPEN_CONFIRM, BENCH_CONFIRM, LUCKY_SKIP, LUCKY_USE, FOSSIL_DISCARD, ENERGY_TARGET, OTHER, DOLL_RETURN, COIN_HEADS, COIN_TAILS;
    /** Compatibility for older test fixtures and snapshots, not used to authorize an action. */
    public static ActionType legacy(String label){
        if(label.startsWith("取消选牌"))return CARD_DESELECT;
        if(label.startsWith("选择卡牌"))return CARD_SELECT;
        if(label.startsWith("确认选牌"))return CARD_CONFIRM;
        if(label.startsWith("确认撤退"))return RETREAT_CONFIRM;
        if(label.startsWith("取消撤退"))return RETREAT_CANCEL;
        if(label.startsWith("确认分配"))return DAMAGE_CONFIRM;
        if(label.startsWith("确认能量分配"))return ENERGY_CONFIRM;
        if(label.startsWith("分配 "))return ENERGY_ASSIGN;
        if(label.startsWith("确认已查看"))return ACK_REVEAL;
        if(label.startsWith("确认无基础"))return ACK_MULLIGAN;
        if(label.startsWith("完成目标选择"))return TARGET_FINISH;
        if(label.startsWith("选择效果目标"))return TARGET_SELECT;
        if(label.startsWith("使用触发"))return TRIGGER_USE;
        if(label.startsWith("放弃触发"))return TRIGGER_SKIP;
        if(label.startsWith("优先结算触发"))return TRIGGER_ORDER;
        if(label.startsWith("拿取背面"))return PRIZE_PICK;
        if(label.startsWith("查看对手暗面"))return PRIZE_VIEW;
        if(label.startsWith("翻开对手暗面"))return PRIZE_REVEAL;
        if(label.startsWith("确认选择"))return PRIZE_CONFIRM;
        if(label.startsWith("选择暗面奖赏位置"))return PRIZE_SELECT;
        if(label.startsWith("取消暗面奖赏"))return PRIZE_DESELECT;
        if(label.startsWith("不交换"))return SWAP_SKIP;
        if(label.startsWith("不复制公开"))return COPY_SKIP;
        if(label.startsWith("交换公开奖赏"))return PRIZE_SWAP;
        if(label.startsWith("选择效果 "))return CHOICE;
        if(label.startsWith("弃置超"))return OVERFLOW;
        if(label.startsWith("猜硬币"))return COIN;
        if(label.startsWith("选择自己先手"))return GO_FIRST;
        if(label.startsWith("选择对方先手"))return GO_SECOND;
        if(label.startsWith("重抽补偿"))return MULLIGAN_DRAW;
        if(label.startsWith("设为战斗宝可梦"))return OPEN_ACTIVE;
        if(label.startsWith("确认开局"))return OPEN_CONFIRM;
        if(label.startsWith("完成补抽"))return BENCH_CONFIRM;
        if(label.startsWith("不发动幸运奖励"))return LUCKY_SKIP;
        if(label.startsWith("发动幸运奖励"))return LUCKY_USE;
        if(label.startsWith("紧急入场"))return LUCKY_USE;
        if(label.startsWith("自行弃置"))return FOSSIL_DISCARD;
        if(label.startsWith("收回玩偶"))return DOLL_RETURN;

        if(label.startsWith("攻击：")||label.startsWith("复制攻击：")||label.startsWith("复制牌库顶招式"))return ATTACK;
        if(label.startsWith("附着 "))return ATTACH;
        if(label.startsWith("进化："))return EVOLVE;
        if(label.startsWith("撤退"))return RETREAT;
        if(label.startsWith("使用弃牌区特性"))return DISCARD_ABILITY;
        if(label.startsWith("使用特性"))return ABILITY;
        if(label.startsWith("使用竞技场"))return STADIUM;
        if(label.startsWith("使用VSTAR"))return TOOL_ABILITY;
        if(label.startsWith("使用")||label.startsWith("放置场地"))return TRAINER;
        if(label.startsWith("放入备战区")||label.startsWith("补抽基础")||label.startsWith("备战："))return BENCH;
        if(label.startsWith("选择替补"))return REPLACEMENT;
        if(label.equals("结束回合"))return END_TURN;
        if(label.startsWith("认输"))return CONCEDE;
        return OTHER;
    }
}
