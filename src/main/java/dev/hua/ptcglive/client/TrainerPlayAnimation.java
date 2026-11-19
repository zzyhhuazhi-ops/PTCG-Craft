package dev.hua.ptcglive.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

/** Only a public confirmed card ID is read. No effect resolution or hidden-card access. */
final class TrainerPlayAnimation {
    private static ShaderProgram trainerShader,toolShader;
    private enum Style {
        ITEM(0xFF10F8FF,"advance.item"),SUPPORTER(0xFFFFD132,"advance.supporter"),TOOL(0xFFD395FF,"advance.tool"),STADIUM(0xFF72F838,"advance.stadium");
        final int color;final String clock,frameGain,coreTint;
        Style(int color,String clock){this.color=color;this.clock=clock;this.frameGain=clock.replace("advance.","frame.gain.");this.coreTint=clock.replace("advance.","core.tint.");}
    }
    private static final Map<String,Style> STYLES=loadStyles();
    private static Map<String,Style> loadStyles(){
        // Read the existing public presentation index, without initializing the server rule registry.
        try(var in=TrainerPlayAnimation.class.getResourceAsStream("/data/ptcglive/collection-card-facts.json")){
            if(in==null)throw new IllegalStateException("Missing public card facts");
            var data=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var colors=new HashMap<String,Style>();
            for(var e:data.entrySet()){
                var f=e.getValue().getAsJsonObject();if(!f.get("category").getAsString().equals("TRAINER"))continue;
                Style style=switch(f.get("subtype").getAsString()){
                    case "SUPPORTER"->Style.SUPPORTER;case "TOOL"->Style.TOOL;case "ITEM"->Style.ITEM;case "STADIUM"->Style.STADIUM;default->null;
                };
                if(style!=null)colors.put(e.getKey(),style);
            }
            return Map.copyOf(colors);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    static int color(String id){return STYLES.getOrDefault(id,Style.ITEM).color;}
    static boolean item(String id){return id!=null&&STYLES.get(id)==Style.ITEM;}
    static boolean supporter(String id){return id!=null&&STYLES.get(id)==Style.SUPPORTER;}
    static boolean tool(String id){return id!=null&&STYLES.get(id)==Style.TOOL;}
    static AnimationTimeline timeline(String id){return tool(id)?BattleAnimationEngine.tool():BattleAnimationEngine.trainer();}
    static boolean calibratedReveal(String id){return item(id)||supporter(id)||tool(id)||id!=null&&STYLES.get(id)==Style.STADIUM;}
    static double focal(String id,Rect center,EvolutionAnimation.Pose pose,double ms){
        return calibratedReveal(id)?center.height()*timeline(id).sample(supporter(id)?"supporter.camera.focal":tool(id)?"tool.camera.focal":"item.camera.focal",ms,2.85):pose.height()*2.4;
    }
    static List<BattleAnimationEngine.Sound> sounds(boolean opponent){
        var timeline=BattleAnimationEngine.trainer();double delay=opponent?timeline.sample("item.sound.opponentDelay",0,0):0;
        return BattleAnimationEngine.sounds(timeline).stream().map(s->new BattleAnimationEngine.Sound(s.name(),s.offsetMs()+delay)).toList();
    }
    static double flashTime(String id,double ms){return flashTime(id,ms,false);}
    static double flashTime(String id,double ms,boolean opponent){return ms+timeline(id).sample(opponent&&item(id)?"advance.item.opponent":opponent&&tool(id)?"advance.tool.opponent":STYLES.getOrDefault(id,Style.ITEM).clock,ms,0);}
    static EvolutionAnimation.Pose pose(Rect hand,Rect center,boolean opponent,String cardId,double ms){
        var t=timeline(cardId);double p=t.sample(opponent?"card.launch.opponent":"card.launch.own",ms,1);
        String profile=supporter(cardId)?"supporter":tool(cardId)?"tool":"item";
        if(opponent&&calibratedReveal(cardId)){
            double size=t.sample(profile+".opponent.size",ms,1),angle=t.sample(profile+".opponent.angle",ms,0);
            return new EvolutionAnimation.Pose(center.x()+center.width()/2.,
                center.y()+center.height()/2.+center.height()*t.sample(profile+".opponent.drop",ms,0),
                center.width()*size,center.height()*size,angle/180-1,t.sample(profile+".opponent.roll",ms,0),0,
                t.sample(profile+".opponent.pitch",ms,0));
        }
        if(!opponent&&calibratedReveal(cardId)){
            // The confirmed own-item reveal starts near the center at half size.
            // The preceding manual drag is a separate interaction in the footage.
            double size=t.sample(profile+".card.size",ms,1);
            return new EvolutionAnimation.Pose(center.x()+center.width()/2.,
                center.y()+center.height()/2.+center.height()*t.sample(profile+".card.drop",ms,0),
                center.width()*size,center.height()*size,1,t.sample(profile+".card.roll",ms,0),
                t.sample(profile+".card.yaw",ms,0),t.sample(profile+".card.pitch",ms,0));
        }
        // The observed opponent tool turns about one source frame before the supporter.
        // This clock affects only the public card pose, never effect or game resolution.
        double turnMs=ms+(opponent&&STYLES.getOrDefault(cardId,Style.ITEM)==Style.TOOL?t.sample("card.turn.advance.tool",ms,0):0);
        double s=t.sample(opponent?"card.size.opponent":"card.size",turnMs,1);
        return new EvolutionAnimation.Pose(
            hand.x()+hand.width()/2.+(center.x()+center.width()/2.-hand.x()-hand.width()/2.)*p,
            hand.y()+hand.height()/2.+(center.y()+center.height()/2.-hand.y()-hand.height()/2.)*p,
            center.width()*(.30+.70*s),center.height()*(.30+.70*s),opponent?t.sample("card.flip",turnMs,1):1,
            t.sample("card.tilt",ms,0)*(opponent?-1:1),t.sample(opponent?"card.yaw.opponent":"card.yaw.own",turnMs,0),(1-p)*-12);
    }
    static void register(){CoreShaderRegistrationCallback.EVENT.register(c->{
        c.register(Identifier.of("ptcglive","battle_trainer"),VertexFormats.POSITION_TEXTURE_COLOR,p->trainerShader=p);
        c.register(Identifier.of("ptcglive","battle_tool"),VertexFormats.POSITION_TEXTURE_COLOR,p->toolShader=p);
    });}
    static void flash(DrawContext c,Rect center,String cardId,double ms,boolean opponent,BattleAnimationEngine.Quality quality){
        var t=timeline(cardId);
        if(quality==BattleAnimationEngine.Quality.OFF||ms<0||ms>=t.durationMs)return;
        var style=STYLES.getOrDefault(cardId,Style.ITEM);
        double frameGain=t.sample(style.frameGain,ms,1);
        ms=flashTime(cardId,ms,opponent);
        String suffix=style==Style.ITEM||style==Style.STADIUM?".item":style==Style.SUPPORTER?".supporter":"";
        boolean calibrated=calibratedReveal(cardId);
        double flash=t.sample("flash"+suffix+(style==Style.SUPPORTER&&opponent?".opponent":""),ms,t.sample("flash",ms,0)),frame=t.sample("frame.alpha"+suffix,ms,t.sample("frame.alpha",ms,0)),sparks=t.sample("sparks",ms,0);
        if(style==Style.TOOL&&opponent)frame=t.sample("frame.alpha.opponent",ms,frame);
        if(flash+frame+sparks<=0)return;
        int color=color(cardId);
        var shader=style==Style.TOOL?toolShader:trainerShader;
        if(shader==null){BattleSceneEffects.shine(c,center,Math.max(0,(ms-980)/1000),.5);return;}
        c.draw();RenderSystem.enableBlend();
        // Premultiplied coverage plus emitted light: the core obscures, the halo brightens.
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE,GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();RenderSystem.disableDepthTest();
        try{
            RenderSystem.setShader(()->shader);
            if(style==Style.TOOL)shader.getUniformOrDefault("ToolStyle").set(1f);
            shader.getUniformOrDefault("Phase").set((float)flash,(float)(frame*frameGain),(float)t.sample("frame.scale"+suffix,ms,1),(float)((ms+(style==Style.TOOL?166.666667:0))/1000));
            shader.getUniformOrDefault("Echo").set((float)t.sample("echo.alpha"+suffix,ms,t.sample("echo.alpha",ms,0)),(float)t.sample("echo.scale"+suffix,ms,t.sample("echo.scale",ms,1)),(float)sparks,quality==BattleAnimationEngine.Quality.HIGH?1f:quality==BattleAnimationEngine.Quality.MEDIUM?.65f:.35f);
            shader.getUniformOrDefault("Contour").set((float)t.sample("core.open"+suffix,ms,t.sample("core.open",ms,0)),(float)t.sample("frame.width"+suffix,ms,.095),(float)t.sample("echo.width",ms,.055),(float)t.sample("core.open.width",ms,.78));
            shader.getUniformOrDefault("Hue").set(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,(float)t.sample(style.coreTint,ms,.1));
            double apertureHeight=t.sample(style==Style.SUPPORTER?"supporter.aperture.height":"item.aperture.height",ms,.78);
            if(style==Style.TOOL&&opponent)apertureHeight=t.sample("tool.aperture.height.opponent",ms,apertureHeight);
            shader.getUniformOrDefault("Material").set(calibrated?1f:0f,calibrated?1.5f:1f,calibrated?1.15f:1f,(float)apertureHeight);
            shader.getUniformOrDefault("Finish").set(style==Style.SUPPORTER?.09f:style==Style.TOOL?.07f:.025f,
                style==Style.SUPPORTER||style==Style.TOOL?1f:.97f,
                style==Style.SUPPORTER||style==Style.TOOL?.7f:.85f,
                style==Style.SUPPORTER?.35f:style==Style.TOOL?.2f:0f);
            flashQuad(c,center);
        }finally{RenderSystem.defaultBlendFunc();RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.disableBlend();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);}
    }
    /** Tool-only premultiplied light, using the observed 4:3 badge contour. */
    static boolean toolBadge(DrawContext c,Rect icon,double ms,String prefix){
        var shader=toolShader;
        if(shader==null)return false;
        if(ms<0||ms>=900)return true;
        var t=BattleAnimationEngine.tool();boolean remove=!prefix.equals("attach.");
        float core=(float)t.sample(prefix+"core",ms,0),frame=(float)t.sample(prefix+"frame",ms,0);
        if(core+frame<=0)return true;
        float scale=(float)t.sample(prefix+"scale",ms,1);
        c.draw();RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE,GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();RenderSystem.disableDepthTest();
        try{
            RenderSystem.setShader(()->shader);
            boolean returning=prefix.equals("return.");
            shader.getUniformOrDefault("ToolStyle").set(returning?3f:2f);
            float badgeScale=scale*(remove?1f:.94f);
            shader.getUniformOrDefault("Phase").set(core,frame,badgeScale,(float)(ms/1000));
            shader.getUniformOrDefault("Echo").set(remove?0f:frame,badgeScale,remove?0f:(float)t.sample("attach.flash",ms,0),0f);
            shader.getUniformOrDefault("Contour").set((float)t.sample(prefix+"opening",ms,0),.025f,.02f,1f);
            shader.getUniformOrDefault("Hue").set(returning?.824f:remove?.93f:.9f,returning?.812f:remove?.9f:.66f,returning?.827f:1f,returning?1f:0f);
            shader.getUniformOrDefault("Material").set(1f,1.1f,1f,1f);
            shader.getUniformOrDefault("Finish").set(.035f,1f,.9f,.3f);
            flashQuad(c,icon);
        }finally{RenderSystem.defaultBlendFunc();RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.disableBlend();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);}
        return true;
    }
    private static void flashQuad(DrawContext c,Rect center){
        float x=center.x()-center.width()*.4f,y=center.y()-center.height()*.4f,w=center.width()*1.8f,h=center.height()*1.8f;
        var m=c.getMatrices().peek().getPositionMatrix();var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(m,x,y,0).texture(0,0).color(-1);b.vertex(m,x,y+h,0).texture(0,1).color(-1);
        b.vertex(m,x+w,y+h,0).texture(1,1).color(-1);b.vertex(m,x+w,y,0).texture(1,0).color(-1);
        BufferRenderer.drawWithGlobalProgram(b.end());
    }
}
