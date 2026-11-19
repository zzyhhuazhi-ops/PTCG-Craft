package dev.hua.ptcglive.compat.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.PtcgServices;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/** Optional Cobblemon 1.8 event bridge. Loaded only when Fabric reports Cobblemon present. */
public final class CobblemonIntegration {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("ptcglive-cobblemon");
    private static CobblemonRewardTable rewards;

    private CobblemonIntegration() { }

    public static void initialize() {
        if (rewards != null) return;
        rewards = CobblemonRewardTable.load(PtcgServices.cardCatalog());
        CobblemonEvents.POKEMON_CAPTURED.subscribe((Consumer<PokemonCapturedEvent>) event ->
            grant(event.getPlayer(), event.getPokemon(), "捕捉"));
        CobblemonEvents.BATTLE_FAINTED.subscribe((Consumer<BattleFaintedEvent>) event -> {
            var defeated = event.getKilled();
            if (defeated.getActor().getType() != ActorType.WILD) return;
            var players = new LinkedHashSet<ServerPlayerEntity>();
            for (var actor : defeated.getActor().getSide().getOppositeSide().getActors())
                if (actor instanceof PlayerBattleActor playerActor) players.add(playerActor.getEntity());
            players.forEach(player -> grant(player, defeated.getOriginalPokemon(), "击败"));
        });
        LOGGER.info("Enabled Cobblemon rewards for {} species", rewards.speciesCount());
    }

    private static void grant(ServerPlayerEntity player, Pokemon pokemon, String action) {
        String species = pokemon.getSpecies().getResourceIdentifier().getPath();
        var cards = rewards.roll(species, new java.util.Random(player.getRandom().nextLong()));
        if (cards.isEmpty()) {
            LOGGER.warn("No registered PTCG card matches Cobblemon species {}", species);
            return;
        }
        var names = new java.util.ArrayList<String>();
        for (var card : cards) {
            var stack = new ItemStack(CardItems.byDefinitionId(card.id()));
            if (!player.getInventory().insertStack(stack) || !stack.isEmpty()) player.dropItem(stack, false);
            names.add(card.nameZh() + "（" + card.rarityZh() + "）");
        }
        player.currentScreenHandler.sendContentUpdates();
        player.sendMessage(Text.literal("[PTCG Craft] " + action + " "
            + pokemon.getSpecies().getTranslatedName().getString() + "，获得 " + String.join("、", names) + "。"), false);
    }
}
