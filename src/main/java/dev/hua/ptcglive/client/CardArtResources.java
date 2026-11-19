package dev.hua.ptcglive.client;

import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardItems;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves optional external full-size art and builtin thumbnail fallbacks. */
public final class CardArtResources {
    private static final Identifier ALIAS_INDEX = Identifier.of(CardItems.ASSET_NAMESPACE, "card_art_aliases.json");
    private static long generation;
    private static Map<String, String> aliases = Map.of();
    private static long aliasesGeneration = -1;
    public record Selection(Identifier resource, boolean highDefinition) { }

    private CardArtResources() { }
    public static long generation() { return generation; }
    public static void invalidate() { generation++; aliases = Map.of(); aliasesGeneration = -1; }

    public static Selection select(ResourceManager resources, String cardId) {
        Identifier original = Identifier.of(CardItems.ASSET_NAMESPACE, "card_art/" + cardId + ".jpg");
        if (resources.getResource(original).isPresent()) return new Selection(original, true);
        String sourceCard = aliases(resources).get(cardId);
        if (sourceCard != null) {
            Identifier aliased = Identifier.of(CardItems.ASSET_NAMESPACE, "card_art/" + sourceCard + ".jpg");
            if (resources.getResource(aliased).isPresent()) return new Selection(aliased, true);
        }
        // A historical reprint has no duplicate thumbnail. Its alias
        // always points to the same compact image that its item model uses.
        return new Selection(thumbnail(resources, cardId), false);
    }

    /**
     * Keeps reprints such as Start Deck 100 compact: a card points at the exact
     * existing HD scan in a selected expansion pack instead of duplicating it.
     */
    private static Map<String, String> aliases(ResourceManager resources) {
        if (aliasesGeneration == generation) return aliases;
        Map<String, String> loaded = new LinkedHashMap<>();
        resources.getResource(ALIAS_INDEX).ifPresent(resource -> {
            try (var stream = resource.getInputStream(); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("aliases")) return;
                root.getAsJsonObject("aliases").entrySet().forEach(entry -> loaded.put(entry.getKey(), entry.getValue().getAsString()));
            } catch (Exception ignored) {
                // A bad optional alias index must never stop the thumbnail fallback.
            }
        });
        aliases = Map.copyOf(loaded);
        aliasesGeneration = generation;
        return aliases;
    }

    public static Identifier thumbnail(String cardId) {
        return Identifier.of(CardItems.ASSET_NAMESPACE, "card_thumb/" + cardId + ".jpg");
    }

    /** Returns the compact image belonging to an aliased reprint's verified source card. */
    public static Identifier thumbnail(ResourceManager resources, String cardId) {
        String sourceCard = aliases(resources).get(cardId);
        return thumbnail(sourceCard == null ? cardId : sourceCard);
    }

    /**
     * Builtin item rendering bypasses the JSON model's layer0 when emitting its
     * quad. Resolve the same reprint alias here, otherwise SD100 cards request
     * nonexistent per-reprint PNGs even though their models and HD views work.
     * This is an atlas sprite ID, not a file path (no textures/ or .png suffix).
     */
    public static Identifier atlasSprite(ResourceManager resources, String cardId) {
        String sourceCard = aliases(resources).getOrDefault(cardId, cardId);
        if (resources.getResource(Identifier.of(CardItems.ASSET_NAMESPACE, "textures/item/cards/" + sourceCard + ".png")).isEmpty())
            return Identifier.ofVanilla("item/paper");
        return Identifier.of(CardItems.ASSET_NAMESPACE, "item/cards/" + sourceCard);
    }

    /** The filename is derived from the immutable set ID shown in every card tooltip. */
    public static String requiredHighDefinitionPack(String setId) {
        return "PTCG Craft 全卡图资源包（与 Mod 一同提供）";
    }

    /**
     * A historical reprint can deliberately reuse a scan in a normal expansion
     * pack, so the missing-art hint must name that pack rather than a fictitious
     * SD100 archive.
     */
    public static String requiredHighDefinitionPack(String cardId, String setId) {
        return requiredHighDefinitionPack(setId);
    }
}
