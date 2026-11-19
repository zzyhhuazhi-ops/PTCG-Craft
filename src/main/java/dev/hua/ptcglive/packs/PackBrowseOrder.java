package dev.hua.ptcglive.packs;
import java.util.*;
public final class PackBrowseOrder {
    private PackBrowseOrder() {}
    public static final List<String> CATEGORIES=List.of("black_white","xy","sun_moon","sword_shield","scarlet_violet","mega_evolution","special");
    public static String category(SetPackDefinition pack) {
        if(pack.custom()&&!pack.setId().startsWith("collection_"))return "special";
        return CATEGORIES.contains(pack.identity().era())?pack.identity().era():"special";
    }
    public static String label(String category) {return switch(category){
        case "black_white"->"黑白";case "xy"->"XY";case "sun_moon"->"日月";case "sword_shield"->"剑盾";
        case "scarlet_violet"->"朱紫";case "mega_evolution"->"超级进化";default->"特殊";
    };}
    /** Unknown dates follow dated releases; never assign a fabricated release date to a custom product. */
    public static final Comparator<SetPackDefinition> NEWEST=Comparator
        .comparing((SetPackDefinition p)->p.identity().releaseDate(),Comparator.reverseOrder())
        .thenComparing(SetPackDefinition::setId);
}
