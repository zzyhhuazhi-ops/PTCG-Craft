package dev.hua.ptcglive.client;

import java.util.*;
import net.minecraft.resource.ResourceManager;

/** Resource packs replace definitions by resource ID. Invalid entries retain bundled fallback. */
final class BattleAnimationResources {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("PTCG-BattleAnimation");
    static void reload(ResourceManager manager) {
        var parsed=new ArrayList<BattleAnimationEngine.Definition>();
        manager.findResources("battle_animations",id->id.getPath().endsWith(".json")).entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).forEach(entry->{
                try(var reader=entry.getValue().getReader()) {
                    parsed.add(BattleAnimationEngine.Definition.parse(reader));
                }catch(Exception e){LOG.warn("Ignored invalid battle animation {}: {}",entry.getKey(),e.toString());}
            });
        BattleAnimationEngine.replace(parsed);
    }
}
