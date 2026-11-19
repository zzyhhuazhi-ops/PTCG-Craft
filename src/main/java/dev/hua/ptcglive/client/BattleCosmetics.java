package dev.hua.ptcglive.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Client-only resource-pack skins. Reload atomically; never change card identity or coin results. */
final class BattleCosmetics {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("PTCG-Cosmetics");
    static final Identifier DEFINITION=Identifier.of("ptcglive","cosmetics.json");
    private static final Art DEFAULT_BACK=new Art(Identifier.of("ptcglive","textures/gui/pack_opening/card_back.png"),850,1187);
    record Art(Identifier texture,int width,int height) implements CardArt { }
    private record Skins(CardArt back,CardArt heads,CardArt tails,CardArt mat) { }
    private static volatile Skins skins=new Skins(DEFAULT_BACK,null,null,null);
    private BattleCosmetics() { }
    static CardArt cardBack(){return skins.back();}
    static CardArt coin(boolean heads){var current=skins;return heads?current.heads():current.tails();}
    static CardArt playmat(){return skins.mat();}

    static void reload(ResourceManager manager){
        JsonObject definition=new JsonObject();
        try{
            var resource=manager.getResource(DEFINITION);
            if(resource.isPresent())try(var reader=resource.get().getReader()){
                definition=JsonParser.parseReader(reader).getAsJsonObject();
            }
        }catch(Exception e){LOG.warn("Invalid cosmetics definition; using defaults: {}",e.toString());}
        CardArt fallback=load(manager,DEFAULT_BACK.texture());
        CardArt back=load(manager,definition,"card_back"),heads=load(manager,definition,"coin_heads"),tails=load(manager,definition,"coin_tails");
        // A partial coin skin must not make both outcomes look like the same face.
        if(heads==null||tails==null){heads=null;tails=null;}
        skins=new Skins(back!=null?back:fallback!=null?fallback:DEFAULT_BACK,heads,tails,load(manager,definition,"playmat"));
    }
    private static CardArt load(ResourceManager manager,JsonObject definition,String key){
        try{return definition.has(key)&&!definition.get(key).isJsonNull()?load(manager,Identifier.of(definition.get(key).getAsString())):null;}
        catch(Exception e){LOG.warn("Invalid cosmetic {}: {}",key,e.toString());return null;}
    }
    private static CardArt load(ResourceManager manager,Identifier texture){
        try{
            var resource=manager.getResource(texture);
            if(resource.isEmpty()){LOG.warn("Missing cosmetic texture {}",texture);return null;}
            try(var input=resource.get().getInputStream();var image=NativeImage.read(input)){
                return new Art(texture,image.getWidth(),image.getHeight());
            }
        }catch(Exception e){LOG.warn("Invalid cosmetic texture {}: {}",texture,e.toString());return null;}
    }
    static void draw(DrawContext context,CardArt art,int x,int y,int width,int height){
        if(art==null||width<=0||height<=0)return;
        context.draw();
        boolean blending=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        try{context.drawTexture(art.texture(),x,y,width,height,0,0,art.width(),art.height(),art.width(),art.height());}
        finally{if(!blending)com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
    }
}
