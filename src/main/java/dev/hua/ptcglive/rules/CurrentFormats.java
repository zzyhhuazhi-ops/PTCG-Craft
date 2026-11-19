package dev.hua.ptcglive.rules;

import java.util.List;

/** The four selectable formats. Archived snapshots remain available for historical audits. */
public final class CurrentFormats {
    public static final String OPEN = "custom-open-all";
    public static final String STARTER = "starter-deck-100-cn-community-2024";
    public static final String INTERNATIONAL = "standard-international-2026";
    public static final String CHINESE = "standard-cn-2026-0916";
    public static final List<String> IDS = List.of(OPEN, STARTER, INTERNATIONAL, CHINESE);
    private static final List<String> OLD_INTERNATIONAL = List.of("standard-international-2023", "standard-international-2024-0101",
        "standard-international-2024", "standard-international-2025-0101", "standard-international-2025", "standard-international-2026-0101");
    private static final List<String> OLD_CHINESE = List.of("standard-cn-2023", "standard-cn-2024-0101", "standard-cn-2024-0517",
        "standard-cn-2024-0618", "standard-cn-2025-0101", "standard-cn-2025", "standard-cn-2026-0101", "standard-cn-2026-0116",
        "standard-cn-2026-0716", "practice-cn-2024", "practice-cn-2025", "practice-cn-2026-0116");
    private CurrentFormats() { }
    /** Only known retired selections are migrated; unknown third-party IDs are never silently replaced. */
    public static String migrate(String id) {
        if (OLD_INTERNATIONAL.contains(id)) return INTERNATIONAL;
        if (OLD_CHINESE.contains(id)) return CHINESE;
        return id;
    }
    public static int order(String id) {
        int index = IDS.indexOf(id);
        return index < 0 ? IDS.size() : index;
    }
}
