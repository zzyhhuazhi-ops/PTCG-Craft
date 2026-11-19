package dev.hua.ptcglive.rules;

/** Versioned setup protocol, separate from card-pool rotation. Unknown future protocols fail closed. */
public enum SetupProcedure {
    INTERNATIONAL_V1("international-setup-v1");
    private final String id;
    SetupProcedure(String id) { this.id = id; }
    public String id() { return id; }
    public static SetupProcedure fromId(String id) {
        for (var procedure : values()) if (procedure.id.equals(id)) return procedure;
        throw new IllegalArgumentException("不支持的开局流程版本，请更新规则引擎：" + id);
    }
}
