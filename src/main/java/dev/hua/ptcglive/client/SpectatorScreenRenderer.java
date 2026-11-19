package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.spectator.*;
import dev.hua.ptcglive.view.MatchPresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;

/** One continuous, aspect-preserving canvas per controller, sharing the normal card texture cache. */
public final class SpectatorScreenRenderer implements BlockEntityRenderer<SpectatorScreenEntity> {
    private static final Identifier WHITE=Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final int LIGHT=LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private final CardItemRenderer cards;
    private final Map<String,ItemStack> stacks=new LinkedHashMap<>();
    public SpectatorScreenRenderer(CardItemRenderer cards){this.cards=cards;}
    @Override public void render(SpectatorScreenEntity entity,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        if(entity.valid&&!entity.controller())return;
        var camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();var center=Vec3d.ofCenter(entity.getPos());
        var face=entity.getCachedState().get(SpectatorScreenBlock.FACING);
        double distance=camera.squaredDistanceTo(center);if(distance>64*64||camera.subtract(center).dotProduct(Vec3d.of(face.getVector()))<0)return;
        int width=entity.valid?entity.width:1,height=entity.valid?entity.height:1;
        matrices.push();CardFrameRenderer.orientFrame(matrices,face);
        matrices.translate(-.5,height-.5,.502);matrices.scale(1,-1,1);
        rect(matrices,consumers,0,0,width,height,0xFF05080D,0);
        // Only the outer border is drawn; member blocks never repeat this canvas.
        rect(matrices,consumers,.025,.025,width-.05,.025,0xFF54B9F2,.001);
        rect(matrices,consumers,.025,height-.05,width-.05,.025,0xFF215E8F,.001);
        rect(matrices,consumers,.025,.025,.025,height-.05,0xFF172B41,.001);
        rect(matrices,consumers,width-.05,.025,.025,height-.05,0xFF172B41,.001);
        var fit=ScreenLayout.fit(width,height);matrices.translate(fit.left(),fit.top(),.003);matrices.scale((float)fit.scale(),(float)fit.scale(),.001f);
        var scene=entity.valid?SpectatorScreenClient.scene(entity.match):null;
        if(scene==null){
            text(matrices,consumers,"PTCG-CRAFT",320,200,4,0xFFFFFF);
            text(matrices,consumers,"SPECTATOR MODE",320,265,3,0x65C9FF);
            text(matrices,consumers,entity.valid?"等待连接对战…":"请拼成完整矩形（3×2 至 11×7）",290,330,2,0xB8C9D8);
        }else {
            drawScene(entity,scene,matrices,consumers,distance);
            int lane=0;for(var line:SpectatorScreenClient.chat(entity.getPos(),entity.match)){
                String message=line.message().name()+"："+line.message().text();
                float textWidth=MinecraftClient.getInstance().textRenderer.getWidth(message)*1.5f;
                float progress=Math.clamp((net.minecraft.util.Util.getMeasuringTimeMs()-line.received())/8000f,0,1);
                float x=18+(982-textWidth-18)*(1-progress),y=35+(lane++%6)*24;
                matrices.push();matrices.translate(0,0,30);
                text(matrices,consumers,message,x,y,1.5f,0xFFFFFF);matrices.pop();
            }
        }
        matrices.pop();
    }
    private void drawScene(SpectatorScreenEntity entity,SpectatorScreenClient.Scene scene,MatrixStack m,VertexConsumerProvider out,double distance){
        var game=scene.view.match();double age=scene.age();
        rect(m,out,6,6,988,288,0xFF321F2B,0);rect(m,out,6,306,988,288,0xFF182F48,0);
        for(int i=0;i<game.players().size();i++){
            var p=game.players().get(i);boolean top=i==1;float y=top?13:580;
            String name=entity.names?scene.view.names().getOrDefault(p.id(),top?"玩家 B":"玩家 A"):(top?"玩家 B":"玩家 A");
            text(m,out,name+(p.id().equals(game.currentPlayer())?"  ◀ 当前行动":""),202,y,1.7f,p.id().equals(game.currentPlayer())?0xFFE17A:0xFFFFFF);
            float pileY=top?60:475;
            text(m,out,"牌库 "+p.deckSize(),25,pileY,1.8f,0xFFFFFF);
            text(m,out,"手牌 "+p.handSize(),25,pileY+24,1.8f,0xB9D7EF);
            text(m,out,"奖赏 "+p.prizeCount(),25,pileY+48,1.8f,0xFFE17A);
            for(int prize=0;prize<Math.min(6,p.prizeCount());prize++)rect(m,out,25+prize*22,pileY+72,17,24,0xFF254B91,1);
        }
        for(var card:scene.cards){
            var p=card.pokemon();double shift=0;
            if(entity.animations&&p!=null&&!scene.oldHp.containsKey(p.pokemonId())&&age<.7)shift=16*(1-age/.7);
            drawCard(m,out,card,shift,distance<28*28);
            if(p!=null){
                int hp=Math.max(0,p.hitPoints()-p.damage());int displayed=hp;
                if(entity.animations&&scene.oldHp.containsKey(p.pokemonId()))displayed=(int)Math.round(scene.oldHp.get(p.pokemonId())+(hp-scene.oldHp.get(p.pokemonId()))*Math.min(1,age/.7));
                rect(m,out,card.x(),card.y()-11,card.width(),10,0xEE0A101A,5);
                text(m,out,"HP "+displayed+"/"+p.hitPoints(),(float)card.x()+2,(float)card.y()-10,1,0xFFFFFF);
                rect(m,out,card.x(),card.y()-2,card.width()*Math.clamp((double)displayed/Math.max(1,p.hitPoints()),0,1),2,0xFF50DC89,6);
                if(p.damage()>0)text(m,out,"−"+p.damage(),(float)(card.x()+card.width()-32),(float)(card.y()+8),1.3f,0xFFCC66);
                if(!p.conditions().isEmpty())text(m,out,String.join("/",p.conditions().stream().map(SpectatorScreenRenderer::condition).toList()),(float)card.x(),(float)(card.y()+card.height()+20),1,0xFFE17A);
                if(age<1.2)for(var event:scene.fresh){var cue=event.cue();if(!p.pokemonId().equals(cue.pokemon()))continue;
                    if(entity.animations&&cue.kind()==MatchPresentation.Kind.DAMAGE&&((int)(age*10)%2==0))rect(m,out,card.x(),card.y(),card.width(),card.height(),0x44FFFFFF,7);
                    if(entity.damage&&(cue.kind()==MatchPresentation.Kind.DAMAGE||cue.kind()==MatchPresentation.Kind.HEAL))
                        text(m,out,(cue.kind()==MatchPresentation.Kind.HEAL?"+":"−")+cue.amount(),(float)card.x(),(float)(card.y()+35-age*18),2.5f,cue.kind()==MatchPresentation.Kind.HEAL?0x63FFA5:0xFF7777);
                }
            }else if(card.height()>30)text(m,out,card.label(),(float)card.x(),(float)(card.y()+card.height()+3),1.1f,0xCADCEE);
        }
        rect(m,out,352,291,295,19,0xFF07121E,8);
        text(m,out,"第 "+game.turn()+" 回合 · "+phase(game.phase()),360,295,1.4f,0xFFE17A);
        if(entity.animations&&age<1.3&&!scene.fresh.isEmpty()){
            var event=scene.fresh.getLast();String notice=event.text();
            if(event.cue().kind()==MatchPresentation.Kind.KNOCKOUT)notice="宝可梦气绝";
            if(event.cue().kind()==MatchPresentation.Kind.TURN)notice="回合切换";
            rect(m,out,560,194,410,28,0xEE0A1424,9);text(m,out,trim(notice,190),570,201,1.5f,0xFFE17A);
        }
        if(game.result()!=null){
            String result=game.result().winner()==null?"对战结束 · 平局":entity.names?scene.view.names().getOrDefault(game.result().winner(),"获胜玩家")+" 获胜":"对战结束 · "+(game.players().getFirst().id().equals(game.result().winner())?"玩家 A":"玩家 B")+" 获胜";
            rect(m,out,190,266,620,68,0xF007121F,12);text(m,out,result,220,281,3,0xFFE17A);
            text(m,out,"即将返回待机",420,317,1.3f,0xAFC6DB);
        }
    }
    private static String phase(dev.hua.ptcglive.engine.MatchPhase phase){return switch(phase){case TURN->"对战中";case FINISHED->"已结束";default->"开局 / 选择中";};}
    private static String condition(dev.hua.ptcglive.engine.field.SpecialCondition condition){return switch(condition){case ASLEEP->"睡眠";case PARALYZED->"麻痹";case CONFUSED->"混乱";case POISONED->"中毒";case BURNED->"灼伤";};}
    private void drawCard(MatrixStack m,VertexConsumerProvider out,ScreenLayout.Card card,double shift,boolean nearby){
        ItemStack stack=stacks.get(card.id());if(stack==null){try{stack=CardItems.byDefinitionId(card.id()).getDefaultStack();}catch(IllegalArgumentException ex){return;}
            if(stacks.size()>=256)stacks.remove(stacks.keySet().iterator().next());stacks.put(card.id(),stack);}
        rect(m,out,card.x()-1,card.y()-1,card.width()+2,card.height()+2,0xFF88A9BF,1);
        m.push();m.translate(card.x()+(card.width()-card.height())/2,card.y()+card.height()+shift,2);
        m.scale((float)card.height(),-(float)card.height(),.02f);cards.renderInFrame(stack,m,out,LIGHT,nearby,(float)(card.width()/card.height()));m.pop();
    }
    private static String trim(String text,int width){return MinecraftClient.getInstance().textRenderer.trimToWidth(text,width);}
    private static void text(MatrixStack m,VertexConsumerProvider out,String value,float x,float y,float scale,int color){
        m.push();m.translate(x,y,20);m.scale(scale,scale,1);
        MinecraftClient.getInstance().textRenderer.draw(value,0,0,color,false,m.peek().getPositionMatrix(),out,TextRenderer.TextLayerType.NORMAL,0,LIGHT);m.pop();
    }
    private static void rect(MatrixStack m,VertexConsumerProvider out,double x,double y,double width,double height,int color,double z){
        var v=out.getBuffer(RenderLayer.getEntityTranslucent(WHITE));var entry=m.peek();
        vertex(v,entry,x,y,z,color);vertex(v,entry,x+width,y,z,color);vertex(v,entry,x+width,y+height,z,color);vertex(v,entry,x,y+height,z,color);
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry m,double x,double y,double z,int color){v.vertex(m,(float)x,(float)y,(float)z).color(color).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(LIGHT).normal(m,0,0,1);}
    @Override public boolean rendersOutsideBoundingBox(SpectatorScreenEntity entity){return true;}
    @Override public int getRenderDistance(){return 72;}
}
