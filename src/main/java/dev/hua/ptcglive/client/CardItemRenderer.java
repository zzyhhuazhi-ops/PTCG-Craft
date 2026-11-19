package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItem;
import dev.hua.ptcglive.CardItems;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

/** The item atlas stays compact; HD resource packs are used only for recently held cards. */
public final class CardItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private record Art(Identifier texture, float aspect) { }
    private final LinkedHashMap<String, Art> cache = new LinkedHashMap<>(32, 0.75f, true);
    private final Set<String> failed = new HashSet<>();
    private final java.util.Map<String,CardArtLoader.Ticket> pending=new java.util.HashMap<>();
    private long lastFrameLoadTick = Long.MIN_VALUE;
    private final java.util.Map<String,Long> frameLastSeen = new java.util.HashMap<>();

    /** Shared by the actual GUI/ground/third-person renderer and its resource-routing regression test. */
    public static Identifier atlasSprite(net.minecraft.resource.ResourceManager resources, String cardId) {
        return CardArtResources.atlasSprite(resources, cardId);
    }

    public void clear() {
        pending.values().forEach(CardArtLoader.Ticket::cancel);pending.clear();
        var manager = MinecraftClient.getInstance().getTextureManager();
        cache.values().forEach(art -> manager.destroyTexture(art.texture()));
        cache.clear();
        failed.clear();
        frameLastSeen.clear();
        lastFrameLoadTick = Long.MIN_VALUE;
    }

    private Art loadArtwork(String cardId) {
        Art known=cache.get(cardId);if(known!=null)return known;
        if(failed.contains(cardId)||pending.containsKey(cardId))return null;
        var resources=MinecraftClient.getInstance().getResourceManager();var selection=CardArtResources.select(resources,cardId);
        var ticket=CardArtLoader.request(resources,selection.resource(),CardArtResources.thumbnail(resources,cardId),selection.highDefinition(),1080,
            Identifier.of(CardItems.MOD_ID,"held/"+cardId),art->{
                pending.remove(cardId);cache.put(cardId,new Art(art.texture(),(float)art.width()/art.height()));
                if(cache.size()>64){var it=cache.entrySet().iterator();var old=it.next();MinecraftClient.getInstance().getTextureManager().destroyTexture(old.getValue().texture());it.remove();}
            },()->{pending.remove(cardId);failed.add(cardId);});
        if(ticket!=null)pending.put(cardId,ticket);return null;
    }

    @Override public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light, int overlay) {
        boolean inHand = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
            || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
        renderCard(stack, matrices, consumers, light, overlay, inHand, false);
    }

    /** Unit quad for a stand: use HD art without changing its physical dimensions. */
    public void renderOnStand(ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers,
                              int light, int overlay) {
        renderCard(stack, matrices, consumers, light, overlay, true, true);
    }

    /** Wall displays share the item cache, but never churn it when a wall exceeds its capacity.
     * At most one new HD face per game tick; distant/overflow slots use the existing sprite atlas. */
    public void renderInFrame(ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers,
                              int light, boolean nearby) {
        renderInFrame(stack,matrices,consumers,light,nearby,1);
    }
    /** Fit a rectangular screen slot without stretching an unusually wide or tall source image. */
    public void renderInFrame(ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers,
                              int light, boolean nearby,float boxAspect) {
        if (!(stack.getItem() instanceof CardItem card)) return;
        Art art = nearby ? cache.get(card.card().id()) : null;
        var client = MinecraftClient.getInstance();
        long tick = client.world == null ? 0 : client.world.getTime();
        String id=card.card().id();
        if(nearby) frameLastSeen.put(id,tick);
        if (nearby && art == null && !failed.contains(id) && tick != lastFrameLoadTick) {
            lastFrameLoadTick = tick;
            if(cache.size()>=64) {
                // Reclaim only faces not shown for two seconds. Never evict a face still being rendered by this wall.
                var iterator=cache.entrySet().iterator();
                while(iterator.hasNext()) {
                    var candidate=iterator.next();
                    Long seen=frameLastSeen.get(candidate.getKey());
                    if(seen!=null && (tick-seen>40 || tick<seen)) {
                        client.getTextureManager().destroyTexture(candidate.getValue().texture());
                        frameLastSeen.remove(candidate.getKey());iterator.remove();break;
                    }
                }
            }
            if(cache.size()<64) art = loadArtwork(id);
        }
        Identifier texture; float u0,u1,v0,v1,aspect;
        if(art != null) {
            texture=art.texture();u0=0;u1=1;v0=0;v1=1;aspect=art.aspect();
        } else {
            texture=SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
            var sprite=client.getBakedModelManager().getAtlas(texture)
                .getSprite(atlasSprite(client.getResourceManager(),card.card().id()));
            u0=sprite.getMinU();u1=sprite.getMaxU();v0=sprite.getMinV();v1=sprite.getMaxV();
            aspect=(float)sprite.getContents().getWidth()/sprite.getContents().getHeight();
        }
        float h=Math.min(1,boxAspect/aspect),w=aspect*h,left=(1-w)/2,bottom=(1-h)/2;
        var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        var matrix=matrices.peek();
        vertex(buffer,matrix,left,bottom,u0,v1,light,OverlayTexture.DEFAULT_UV);
        vertex(buffer,matrix,left+w,bottom,u1,v1,light,OverlayTexture.DEFAULT_UV);
        vertex(buffer,matrix,left+w,bottom+h,u1,v0,light,OverlayTexture.DEFAULT_UV);
        vertex(buffer,matrix,left,bottom+h,u0,v0,light,OverlayTexture.DEFAULT_UV);
    }

    private void renderCard(ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers,
                            int light, int overlay, boolean detailed, boolean fixedDimensions) {
        if (!(stack.getItem() instanceof CardItem card)) return;
        Art art = detailed ? loadArtwork(card.card().id()) : null;
        if (art != null) {
            var world = MinecraftClient.getInstance().world;
            frameLastSeen.put(card.card().id(), world == null ? 0L : world.getTime());
        }
        Identifier texture;
        float minU, maxU, minV, maxV, left, right;
        if (art != null) {
            texture = art.texture();
            minU = 0; maxU = 1; minV = 0; maxV = 1;
            left = fixedDimensions ? 0 : (1 - art.aspect()) / 2; right = 1 - left;
        } else {
            texture = SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
            var sprite = MinecraftClient.getInstance().getBakedModelManager().getAtlas(texture)
                .getSprite(atlasSprite(MinecraftClient.getInstance().getResourceManager(), card.card().id()));
            minU = sprite.getMinU(); maxU = sprite.getMaxU(); minV = sprite.getMinV(); maxV = sprite.getMaxV();
            left = 0; right = 1;
        }
        var buffer = consumers.getBuffer(RenderLayer.getEntityCutout(texture));
        var matrix = matrices.peek();
        vertex(buffer, matrix, left, 0, minU, maxV, light, overlay);
        vertex(buffer, matrix, right, 0, maxU, maxV, light, overlay);
        vertex(buffer, matrix, right, 1, maxU, minV, light, overlay);
        vertex(buffer, matrix, left, 1, minU, minV, light, overlay);
        var back=consumers.getBuffer(RenderLayer.getEntityCutout(BattleCosmetics.cardBack().texture()));
        backVertex(back,matrix,right,0,0,1,light,overlay);
        backVertex(back,matrix,left,0,1,1,light,overlay);
        backVertex(back,matrix,left,1,1,0,light,overlay);
        backVertex(back,matrix,right,1,0,0,light,overlay);
    }

    private static void backVertex(VertexConsumer buffer,MatrixStack.Entry matrix,float x,float y,
                                   float u,float v,int light,int overlay){
        buffer.vertex(matrix,x,y,.499f).color(255,255,255,255).texture(u,v)
            .overlay(overlay).light(light).normal(matrix,0,0,-1);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry matrix, float x, float y,
                               float u, float v, int light, int overlay) {
        buffer.vertex(matrix, x, y, 0.5f).color(255, 255, 255, 255).texture(u, v)
            .overlay(overlay).light(light).normal(matrix, 0, 0, 1);
    }
}
