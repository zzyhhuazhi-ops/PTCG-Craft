package dev.hua.ptcglive.rules;

/** Per-turn action caps frozen in a ruleset snapshot. */
public record TurnActionRules(int energyAttachmentsFromHand, int retreats, int supporters, int stadiums) {
    public static final TurnActionRules INTERNATIONAL_STANDARD = new TurnActionRules(1, 1, 1, 1);
    public TurnActionRules {
        if (energyAttachmentsFromHand < 0 || retreats < 0 || supporters < 0 || stadiums < 0)
            throw new IllegalArgumentException("每回合动作上限不能为负数");
    }
}
