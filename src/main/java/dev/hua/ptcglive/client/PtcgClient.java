package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItem;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.PtcgContent;
import dev.hua.ptcglive.network.TableHistoryPayload;
import dev.hua.ptcglive.network.TableLobbyPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.ColorHelper;

public final class PtcgClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        PackCardFoil.register();
        BattleGlowShader.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.hua.ptcglive.duel.DuelLetters.TYPE,DuelLetterRenderer::new);
        UseItemCallback.EVENT.register((player,world,hand)->{
            var stack=player.getStackInHand(hand);
            if(world.isClient && stack.isOf(PtcgContent.FIELD_GUIDE)) {
                MinecraftClient.getInstance().setScreen(new FieldGuideScreen());
                return TypedActionResult.success(stack);
            }
            if(world.isClient&&player instanceof net.minecraft.client.network.AbstractClientPlayerEntity p
                &&stack.isOf(dev.hua.ptcglive.duel.DuelLetters.ITEM)&&!player.getItemCooldownManager().isCoolingDown(stack.getItem()))
                DuelLetterAnimation.start(p,hand,stack);
            return TypedActionResult.pass(stack);
        });
        // Keep each cell raycastable, but do not draw its black selection box across a single enlarged card.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BLOCK_OUTLINE.register((context,outline)->
            !(outline.blockState().getBlock() instanceof dev.hua.ptcglive.display.CardFrameBlock frame && frame.enlarged()));
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(dev.hua.ptcglive.rental.RentalBlocks.SCREEN_HANDLER,RentalScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.RecycleState.ID,(payload,context)->context.client().execute(()->{
            var view=payload.json().isEmpty()?null:new com.google.gson.Gson().fromJson(payload.json(),dev.hua.ptcglive.economy.RecyclingView.class);
            if(payload.openScreen()&&view!=null)context.client().setScreen(new RecyclingScreen(view,
                context.client().currentScreen instanceof PhoneScreen phone ? phone : null));
            else if(context.client().currentScreen instanceof RecyclingScreen screen&&(view==null||screen.sessionId().equals(view.session())))screen.update(view,payload.messageZh());
        }));
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.AiPracticeState.ID,(payload,context)->context.client().execute(()->{
            if(context.client().currentScreen instanceof AiPracticeScreen screen&&screen.tablePos().equals(payload.tablePos()))screen.update(payload);
            else if(context.client().currentScreen instanceof CardTableScreen||context.client().currentScreen instanceof MatchTableScreen)context.client().setScreen(new AiPracticeScreen(payload));
        }));
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.BattleSocialState.ID,(payload,context)->context.client().execute(()->{
            if(context.client().currentScreen instanceof BattleSocialScreen screen)screen.update(payload);
            else if(context.client().currentScreen instanceof MatchTableScreen screen)screen.socialNotice(payload.message());
        }));
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.hua.ptcglive.shop.CardMerchants.TYPE, CardMerchantRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.PhoneStatePayload.ID, (payload, context) -> context.client().execute(() -> {
            var view = payload.json().isEmpty() ? null : new com.google.gson.Gson().fromJson(payload.json(), dev.hua.ptcglive.economy.PhoneView.class);
            if (payload.openScreen() && view != null) context.client().setScreen(new PhoneScreen(view));
            else if (context.client().currentScreen instanceof PhoneScreen screen
                && (view == null || screen.sessionId().equals(view.sessionId()))) screen.update(view, payload.messageZh());
        }));
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.CollectionStatePayload.ID, (payload, context) -> context.client().execute(() -> {
            var view = payload.json().isEmpty() ? null : new com.google.gson.Gson().fromJson(payload.json(), dev.hua.ptcglive.collection.CollectionView.class);
            if (payload.openScreen() && view != null) {
                var current = context.client().currentScreen;
                context.client().setScreen(new PhoneCollectionScreen(view, current instanceof PhoneScreen phone ? phone : null));
            }
            else {
                var current = context.client().currentScreen;
                CardEncyclopediaScreen screen = current instanceof CardEncyclopediaScreen shown ? shown
                    : current instanceof CardViewScreen preview && preview.parentScreen() instanceof CardEncyclopediaScreen parent ? parent : null;
                PhoneCollectionScreen phoneScreen = current instanceof PhoneCollectionScreen shown ? shown
                    : current instanceof CardViewScreen preview && preview.parentScreen() instanceof PhoneCollectionScreen parent ? parent : null;
                if (screen != null && (view == null || screen.sessionId().equals(view.sessionId()))) screen.update(view, payload.message());
                else if (phoneScreen != null && (view == null || phoneScreen.sessionId().equals(view.sessionId()))) phoneScreen.update(view, payload.message());
            }
        }));
        // The first generated texture layer is a grayscale front/flap mask.  Its
        // tint comes solely from the stack's server-synchronised dyed-color component.
        // Since 1.21 item tints are ARGB and the renderer uses their alpha: both
        // layers must be fully opaque, or the box renders invisible (an undyed
        // box's white default and the untinted base layer used to have alpha 0).
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 0
            ? ColorHelper.Argb.fullAlpha(DyedColorComponent.getColor(stack, dev.hua.ptcglive.CardBoxItem.DEFAULT_BOX_COLOR)) : 0xFFFFFFFF,
            PtcgContent.CARD_BOX);
        var renderer = new CardItemRenderer();
        SpectatorScreenClient.initialize();
        CardViewScreen.initializeHistory();
        CardArtLoader.initialize();
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(dev.hua.ptcglive.collectibles.CollectibleBlocks.ENTITY, context -> new CollectibleRenderer(renderer));
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(dev.hua.ptcglive.collectibles.CollectibleBlocks.HANDLER,CollectibleScreen::new);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(dev.hua.ptcglive.collectibles.CollectibleBlocks.ALBUM_HANDLER,AlbumScreen::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
            dev.hua.ptcglive.spectator.SpectatorScreens.ENTITY, context -> new SpectatorScreenRenderer(renderer));
        CardFrameClientOptions.load();
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
            dev.hua.ptcglive.rental.RentalBlocks.ENTITY, context -> new RentalRenderer(renderer));
        dev.hua.ptcglive.display.CardFrames.BLOCKS.values().forEach(block ->
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(block, net.minecraft.client.render.RenderLayer.getCutout()));
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
            dev.hua.ptcglive.display.CardFrames.ENTITY, context -> new CardFrameRenderer(renderer));
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
            PtcgContent.CARD_DISPLAY_CASE_ENTITY, context -> new CardDisplayCaseBlockEntityRenderer(renderer));
        CardItems.cards().forEach(card -> BuiltinItemRendererRegistry.INSTANCE.register(card, renderer));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> renderer.clear());
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override public net.minecraft.util.Identifier getFabricId() {
                    return net.minecraft.util.Identifier.of(CardItems.MOD_ID, "card_art_cache");
                }
                @Override public void reload(net.minecraft.resource.ResourceManager manager) {
                    CardArtLoader.cancelAll();renderer.clear(); CardArtResources.invalidate();
                    BattleAnimationResources.reload(manager);
                    BattleCosmetics.reload(manager);
                }
            });
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.BoxStatePayload.ID, (payload, context) -> context.client().execute(() -> {
            var snapshot = payload.snapshotJson().isEmpty() ? null : new com.google.gson.Gson().fromJson(payload.snapshotJson(), dev.hua.ptcglive.collection.BoxEditorSession.Snapshot.class);
            if (payload.openScreen() && snapshot != null) {
                var current = context.client().currentScreen;
                if (current instanceof PhoneScreen phone) context.client().setScreen(new PhoneCardBoxScreen(snapshot, phone));
                else {
                    var screen = new CardBoxScreen(snapshot,current instanceof AiPracticeScreen?current:null); context.client().setScreen(screen); screen.update(snapshot, payload.messageZh());
                }
            } else {
                var current = context.client().currentScreen;
                CardBoxScreen screen = current instanceof CardBoxScreen shown ? shown
                    : current instanceof CardViewScreen preview && preview.parentScreen() instanceof CardBoxScreen parent ? parent : null;
                PhoneCardBoxScreen phoneScreen = current instanceof PhoneCardBoxScreen shown ? shown
                    : current instanceof CardViewScreen preview && preview.parentScreen() instanceof PhoneCardBoxScreen parent ? parent : null;
                if (screen != null && (snapshot == null || screen.sessionId().equals(snapshot.sessionId()))) screen.update(snapshot, payload.messageZh());
                else if (phoneScreen != null && (snapshot == null || phoneScreen.sessionId().equals(snapshot.sessionId()))) phoneScreen.update(snapshot, payload.messageZh());
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.PackStatePayload.ID, (payload, context) -> context.client().execute(() -> {
            var snapshot = payload.snapshotJson().isEmpty() ? null : new com.google.gson.Gson().fromJson(payload.snapshotJson(), dev.hua.ptcglive.packs.PackUiSnapshot.class);
            if (payload.openScreen() && snapshot != null) {
                if (context.client().currentScreen instanceof PackOpeningScreen screen && screen.sessionId().equals(snapshot.sessionId()))
                    screen.update(snapshot, payload.messageZh());
                else context.client().setScreen(new PackOpeningScreen(snapshot,snapshot.phase()==dev.hua.ptcglive.packs.PackUiSnapshot.Phase.BROWSE?context.client().currentScreen:null));
            } else if (context.client().currentScreen instanceof PackOpeningScreen screen
                && (snapshot == null || screen.sessionId().equals(snapshot.sessionId()))) screen.closeFromServer(payload.messageZh());
        }));
        ClientPlayNetworking.registerGlobalReceiver(dev.hua.ptcglive.network.MatchStatePayload.ID, (payload, context) -> context.client().execute(() -> {
            var snapshot = payload.snapshotJson().isEmpty() ? null : new com.google.gson.Gson().fromJson(payload.snapshotJson(), dev.hua.ptcglive.engine.MatchSession.Snapshot.class);
            // A match opens directly on the draggable tabletop. The legacy text
            // panel remains only as a compatibility fallback for old screens.
            if (payload.openScreen() && snapshot != null) context.client().setScreen(new MatchTableScreen(snapshot));
            else {
                var current = context.client().currentScreen;
                MatchDisplay display = current instanceof MatchDisplay visible ? visible
                    : current instanceof CardViewScreen preview && preview.parentScreen() instanceof MatchDisplay parent ? parent : null;
                if (display != null && (snapshot == null || display.matchId().equals(snapshot.game().matchId()))) {
                    display.update(snapshot, payload.messageZh());
                      // Every update invalidates issued attack indices, including while a large card is open.
                      if (current instanceof CardViewScreen preview && preview.parentScreen() instanceof MatchTableScreen table)
                          context.client().setScreen(table);
                    if (current instanceof CardViewScreen preview && preview.parentScreen() instanceof MatchRegionScreen region
                            && !region.previewAllowed()) context.client().setScreen(region);
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(TableLobbyPayload.ID, (payload, context) -> context.client().execute(() -> {
            if(payload.success()&&payload.messageZh().startsWith("战书邀战："))
                context.client().setScreen(new CardTableScreen(payload.tablePos()));
            if(payload.success()&&context.client().currentScreen instanceof MatchTableScreen&&payload.messageZh().startsWith("请重新选卡盒"))
                context.client().setScreen(new CardTableScreen(payload.tablePos()));
            if (context.client().currentScreen instanceof CardTableScreen screen && screen.tablePos().equals(payload.tablePos()))
                screen.applyLobby(payload.success(), payload.messageZh(), payload.snapshotJson());
        }));
        ClientPlayNetworking.registerGlobalReceiver(TableHistoryPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof CardTableScreen screen && screen.tablePos().equals(payload.tablePos()))
                screen.applyHistory(payload.historyJson());
        }));
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (world.isClient && stack.getItem() instanceof CardItem card) {
                MinecraftClient.getInstance().setScreen(new CardViewScreen(card.card()));
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient && world.getBlockState(hit.getBlockPos()).isOf(PtcgContent.CARD_TABLE)) {
                MinecraftClient.getInstance().setScreen(new CardTableScreen(hit.getBlockPos()));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
}
