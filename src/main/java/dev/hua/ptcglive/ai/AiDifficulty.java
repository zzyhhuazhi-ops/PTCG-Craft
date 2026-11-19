package dev.hua.ptcglive.ai;

/** Decision budgets are deterministic and never grant access to hidden information. */
public enum AiDifficulty {
    EASY("简单", "熟悉规则：基础出牌与攻击", 0, 0, 0),
    NORMAL("普通", "基础策略：附能、进化与击倒判断", 0, 0, 0),
    HARD("困难", "进阶策略：短程组合与关键资源保留", 24, 384, 4),
    MASTER("大师", "最高策略：扩大组合搜索并评估反击风险", 40, 1600, 5);

    private final String label,description;
    public final int candidates,states,depth;
    AiDifficulty(String label,String description,int candidates,int states,int depth){this.label=label;this.description=description;this.candidates=candidates;this.states=states;this.depth=depth;}
    public String label(){return label;}
    public String description(){return description;}
    public static AiDifficulty parse(String value){
        try{return valueOf(value);}catch(IllegalArgumentException|NullPointerException e){throw new IllegalArgumentException("请选择有效的 AI 难度");}
    }
}
