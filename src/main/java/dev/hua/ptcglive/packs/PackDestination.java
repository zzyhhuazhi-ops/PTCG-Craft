package dev.hua.ptcglive.packs;

/** The client chooses only a destination; the server owns every drawn card. */
public enum PackDestination {
    INVENTORY("背包", "CONFIRM_"), COLLECTION("图鉴", "ALBUM_");
    private final String label, prefix;
    PackDestination(String label, String prefix) { this.label=label; this.prefix=prefix; }
    public String labelZh() { return label; }
    public String action(int count) { PackBatchPolicy.validateCount(count); return prefix+count; }
    public static PackDestination forAction(String action) {
        if(PackBatchPolicy.countForAction(action)==0)throw new IllegalArgumentException("未知开包请求");
        return action.startsWith("ALBUM_")?COLLECTION:INVENTORY;
    }
}
