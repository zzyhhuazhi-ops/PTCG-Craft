package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.*;
import dev.hua.ptcglive.engine.field.EnergyType;

/** Shared battle surfaces. Layout, authorized actions and the pack-opening renderer are independent. */
final class BattleUi {
    static final int INK=0xFF28465C, MUTED=0xFF607F91, CYAN=0xFF69DDE7, MINT=0xFF9AEADD,
        PAPER=0xFFF7FCFF, LINE=0xFFD1E4EC, BLUE=0xFF4E9FDF, RED=0xFFD77D8D;
    private BattleUi() { }
    private static final net.minecraft.util.Identifier FONT=net.minecraft.util.Identifier.of("ptcglive_mc","battle_ui");
    private static final net.minecraft.util.Identifier[] TYPE_ICONS=Arrays.stream(EnergyType.values())
        .map(t->net.minecraft.util.Identifier.of("ptcglive_mc","textures/gui/types/"+t.name().toLowerCase(Locale.ROOT)+".png"))
        .toArray(net.minecraft.util.Identifier[]::new);
    static Text label(String text){return Text.literal(text).styled(s->s.withFont(FONT));}
    static void text(DrawContext c,TextRenderer f,String text,int x,int y,int color,boolean shadow,int scale){
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);
        c.drawText(f,label(text),0,0,color,false);c.getMatrices().pop();
    }
    static void centeredText(DrawContext c,TextRenderer f,String text,int x,int y,int color,boolean shadow,int scale){
        text(c,f,text,x-f.getWidth(label(text))*scale/2,y,color,false,scale);
    }
    /** Rounded scanlines include coverage at the arc edge; no square Minecraft corners. */
    static void round(DrawContext c,int x,int y,int w,int h,int radius,int color) {
        if(w<=0||h<=0)return;
        int r=Math.min(radius,Math.min(w,h)/2);
        c.fill(x,y+r,x+w,y+h-r,color);
        for(int dy=0;dy<r;dy++) {
            double inset=r-Math.sqrt(Math.max(0,r*r-Math.pow(r-dy-.5,2))); int whole=(int)Math.ceil(inset);
            for(int side=0;side<2;side++) { int yy=side==0?y+dy:y+h-1-dy;
                c.fill(x+whole,yy,x+w-whole,yy+1,color);
                if(whole>0){int edge=fade(color,whole-inset);
                    c.fill(x+whole-1,yy,x+whole,yy+1,edge); c.fill(x+w-whole,yy,x+w-whole+1,yy+1,edge);}
            }
        }
    }
    private static int fade(int color,double a){return ((int)((color>>>24)*Math.clamp(a,0,1))<<24)|(color&0xFFFFFF);}
    static void panel(DrawContext c,Rect a,int k){
        round(c,a.x(),a.y()+3*k,a.width(),a.height(),10*k,0x383A708F);
        round(c,a.x(),a.y(),a.width(),a.height(),10*k,LINE);
        round(c,a.x()+k,a.y()+k,a.width()-2*k,a.height()-2*k,9*k,PAPER);
    }
    static void table(DrawContext c,PtcgLiveTableLayout l,boolean target){
        int w=l.screenWidth(),h=l.screenHeight(),cy=l.centerY();
        c.fillGradient(0,0,w,h,0xFFF0F2FF,0xFFBBE5F9);
        var mat=BattleCosmetics.playmat();
        if(mat!=null){
            BattleCosmetics.draw(c,mat,0,0,w,h);
            if(target)BattleSceneEffects.focus(c,l.stadiumX(),l.stadiumY(),l.benchWidth(),l.benchHeight(),CYAN,System.nanoTime()/1e9);
            return;
        }
        double rx=w*.58,ry=h*.66,cx=w*.48;
        c.draw();c.enableScissor(0,0,w,cy);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy,rx,ry,0xFFEBA2A3);
        c.draw();c.disableScissor();
        c.draw();c.enableScissor(0,cy,w,h);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy,rx,ry,0xFF70C7EE);
        c.draw();c.disableScissor();
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy,rx-8*l.ui(),ry-8*l.ui(),0xFFF9FCFF);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy,rx-11*l.ui(),ry-11*l.ui(),0xFFD6DEEF);
        c.draw();c.enableScissor(0,cy,w,h);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy,rx-11*l.ui(),ry-11*l.ui(),0xFFA7D4F0);
        c.draw();c.disableScissor();
        double radius=Math.min(w*.26,h*.39);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),w*.5,cy,radius,radius,0x52FFFFFF);
        BattleEffectMesh.ring(BattleEffects.mesh(c),w*.5,cy,radius*.93,1,2*l.ui(),0x80FFFFFF);
        BattleEffects.line(c,w*.5-radius*.9,cy,w*.5+radius*.9,cy,2*l.ui(),0x90FFFFFF);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),w*.5,cy,radius*.14,radius*.14,0xD9FFFFFF);
        // Wide diffuse light and a restrained edge highlight give the mat a satin finish.
        BattleEffectMesh.glow(BattleEffects.mesh(c),w*.23,h*.15,w*.42,h*.48,0x66FFFFFF);
        BattleEffectMesh.glow(BattleEffects.mesh(c),w*.82,h*.89,w*.36,h*.29,0x2692F2FF);
        if(target)BattleSceneEffects.focus(c,l.stadiumX(),l.stadiumY(),l.benchWidth(),l.benchHeight(),CYAN,System.nanoTime()/1e9);
    }
    static void tray(DrawContext c,Rect r,int edge){
        if(edge!=0){round(c,r.x(),r.y(),r.width(),r.height(),5,edge==CYAN?0xCB69DDE7:0x75FFFFFF);
            round(c,r.x()+1,r.y()+1,r.width()-2,r.height()-2,4,0x24FFFFFF);}
    }
    static void emptySlot(DrawContext c,TextRenderer f,String label,int x,int y,int w,int h,int k){
        round(c,x,y,w,h,5*k,0xBCFFFFFF);round(c,x+k,y+k,w-2*k,h-2*k,4*k,0x443A80A5);
        if(label!=null&&f.getWidth(label)*k<w-4*k)BattleUi.centeredText(c,f,label,x+w/2,y+h/2-4*k,MUTED,false,k);
    }
    static void card(DrawContext c,TextRenderer f,CardArt art,String name,int x,int y,int w,int h,int edge){
        round(c,x,y+2,w,h,4,0x32334D65);round(c,x,y,w,h,3,0xFFF8FDFF);
        if(art!=null)CardPainter.art(c,art,x+1,y+1,w-2,h-2);
        else BattleUi.centeredText(c,f,f.trimToWidth(name,Math.max(1,w-4)),x+w/2,y+h/2-4,MUTED,false,1);
    }
    static void cardBack(DrawContext c,int x,int y,int w,int h){
        round(c,x,y+2,w,h,4,0x38304B66);
        BattleCosmetics.draw(c,BattleCosmetics.cardBack(),x,y,w,h);
    }
    static void countTab(DrawContext c,TextRenderer f,int count,int right,int bottom,int k){
        String text=Integer.toString(count);int w=(f.getWidth(text)+8)*k;
        round(c,right-w,bottom-10*k,w,11*k,5*k,PAPER);BattleUi.text(c,f,text,right-w+4*k,bottom-9*k,INK,false,k);
    }
    static void hp(DrawContext c,TextRenderer f,int remaining,EnergyType type,int right,int top,int k){
        String value=Integer.toString(remaining);int icon=11*k;
        int x=right-(type==null?0:icon+2*k)-f.getWidth(label(value))*k,y=top+k;
        text(c,f,value,x-k,y,INK,false,k);text(c,f,value,x+k,y,INK,false,k);
        text(c,f,value,x,y-k,INK,false,k);text(c,f,value,x,y+k,INK,false,k);
        text(c,f,value,x,y,PAPER,false,k);
        if(type!=null){
            BattleEffectMesh.ellipse(BattleEffects.mesh(c),right-icon*.5,top+icon*.5,icon*.5,icon*.5,PAPER);
            c.draw(); // Flush the GUI circle before the immediate texture draw.
            c.drawTexture(TYPE_ICONS[type.ordinal()],right-icon+k,top+k,0,0,icon-2*k,icon-2*k,icon-2*k,icon-2*k);
        }
    }
    static void energyOrb(DrawContext c,int x,int y,int diameter,int rgb){
        double r=diameter*.5,cx=x+r,cy=y+r;int ink=0xFF253D50;
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy+1,r,r,0x4054758A);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy,r,r,0xFFF8FDFF);
        BattleEffectMesh.ellipse(BattleEffects.mesh(c),cx,cy,r-1,r-1,rgb==0x9e9e9e?0xFFD7DAD5:0xFF000000|rgb);
        if(rgb==0x9e9e9e)BattleEffectMesh.ring(BattleEffects.mesh(c),cx,cy,r-.3,1,.45,0xA788979A);
        var m=c.getMatrices();m.push();m.translate(cx,cy,1);double s=r*.55;
        switch(rgb){
            case 0x4caf50 -> {m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(35));
                BattleEffectMesh.ellipse(BattleEffects.mesh(c),0,0,s*.55,s,ink);BattleEffects.line(c,0,s,0,-s,0.7,0xFFAEEDAF);}
            case 0xe53935 -> BattleEffectMesh.flame(BattleEffects.mesh(c),s*1.4,s*1.5,.1,ink);
            case 0x1e88e5 -> {
                s=r*.75;var out=BattleEffects.mesh(c);int waterInk=0xFF253D50;
                BattleEffectMesh.ellipse(out,0,s*.18,s*.65,s*.68,waterInk);
                out.vertex(0,-s,waterInk);out.vertex(-s*.59,0,waterInk);
                out.vertex(s*.59,0,waterInk);out.vertex(0,-s,waterInk);
                BattleEffectMesh.ellipse(out,s*.23,s*.42,s*.22,s*.13,0xFF1E88E5);
            }
            case 0xfdd835 -> {BattleEffects.line(c,s*.5,-s,-s*.4,0,1.2,ink);BattleEffects.line(c,-s*.4,0,s*.4,0,1.2,ink);BattleEffects.line(c,s*.4,0,-s*.5,s,1.2,ink);}
            case 0x9c4dcc -> {
                // Pointed eye outline and pupil remain distinct at the small GUI size.
                s=r*.78;var out=BattleEffects.mesh(c);int psychicInk=0xFF35213F;
                for(int layer=0;layer<2;layer++){
                    double sx=s*(layer==0?1:.67),sy=s*(layer==0?.65:.35);
                    int tint=layer==0?psychicInk:0xFF9C4DCC;
                    for(int i=0;i<12;i++){
                        double a=i*Math.PI/12,b=(i+1)*Math.PI/12;
                        double ax=-sx+2*sx*i/12,bx=-sx+2*sx*(i+1)/12;
                        out.vertex(ax,-Math.sin(a)*sy,tint);out.vertex(ax,Math.sin(a)*sy,tint);
                        out.vertex(bx,Math.sin(b)*sy,tint);out.vertex(bx,-Math.sin(b)*sy,tint);
                    }
                }
                BattleEffectMesh.ellipse(out,0,0,s*.25,s*.3,psychicInk);
            }
            case 0xc0702a -> {c.fill((int)-s,(int)-s,(int)s,(int)s,ink);BattleEffects.line(c,-s,0,s,0,.7,0xFFF4C897);}
            case 0x37474f -> {BattleEffectMesh.ellipse(BattleEffects.mesh(c),0,0,s,s,0xFFE7F6FF);BattleEffectMesh.ellipse(BattleEffects.mesh(c),s*.4,-s*.25,s*.8,s*.8,0xFF37474F);}
            case 0x9e9e9e -> {
                // Three separated bent bands and an inverted central triangle.
                // Keep the symbol legible inside the small, white energy badge.
                s=r*.8;var out=BattleEffects.mesh(c);int steelInk=0xFF28332E;
                for(int i=0;i<3;i++){
                    double a=Math.PI+i*Math.PI*2/3,cos=Math.cos(a),sin=Math.sin(a);
                    for(int part=0;part<3;part++){
                        double ax=part==0?-.62:part==1?-.45:.45,ay=part==0?-.48:-.9;
                        double bx=part==0?-.45:part==1?.45:.62,by=part==2?-.48:-.9;
                        double dx=part==0?-.30:part==1?-.25:.25,dy=part==0?-.48:-.58;
                        double ex=part==0?-.25:part==1?.25:.30,ey=part==2?-.48:-.58;
                        out.vertex((ax*cos-ay*sin)*s,(ax*sin+ay*cos)*s,steelInk);
                        out.vertex((dx*cos-dy*sin)*s,(dx*sin+dy*cos)*s,steelInk);
                        out.vertex((ex*cos-ey*sin)*s,(ex*sin+ey*cos)*s,steelInk);
                        out.vertex((bx*cos-by*sin)*s,(bx*sin+by*cos)*s,steelInk);
                    }
                }
                out.vertex(-s*.45,-s*.32,steelInk);out.vertex(0,s*.48,steelInk);
                out.vertex(s*.45,-s*.32,steelInk);out.vertex(-s*.45,-s*.32,steelInk);
            }
            default -> {for(int i=0;i<6;i++){double a=i*Math.PI/3;BattleEffects.line(c,0,0,Math.cos(a)*s,Math.sin(a)*s,.9,ink);}}
        }
        m.pop();
    }
    static void damageBadge(DrawContext c,int damage,int cx,int cy,int k){
        var f=net.minecraft.client.MinecraftClient.getInstance().textRenderer;String text=Integer.toString(damage);int w=(f.getWidth(text)+8)*k;
        round(c,cx-w/2,cy-6*k,w,13*k,6*k,0xFFF4CC8C);BattleUi.centeredText(c,f,text,cx,cy-4*k,INK,false,k);
    }
    static void button(DrawContext c,TextRenderer f,String title,Rect r,boolean primary,boolean active,double hover,int k){
        int radius=Math.max(3,Math.min(r.height()/2,9*k));
        if(active&&hover>0)round(c,r.x()-k,r.y()-k,r.width()+2*k,r.height()+2*k,radius+k,BattleEffects.alpha(CYAN,hover*.65));
        round(c,r.x(),r.y()+2*k,r.width(),r.height(),radius,0x804F91AF);
        round(c,r.x(),r.y(),r.width(),r.height(),radius,active?primary?CYAN:PAPER:0xFFD4E1E8);
        if(active&&primary)round(c,r.x()+2*k,r.y()+2*k,r.width()-4*k,r.height()-4*k,Math.max(1,radius-2*k),0xFFCCF8F5);
        BattleUi.centeredText(c,f,f.trimToWidth(title,Math.max(1,r.width()/k-6)),r.x()+r.width()/2,
            r.y()+(r.height()-8*k)/2,active?INK:MUTED,false,k);
    }
    static void tooltip(DrawContext c,TextRenderer f,Text text,int x,int y){tooltip(c,f,List.of(text),x,y);}
    static void tooltip(DrawContext c,TextRenderer f,List<Text> text,int x,int y){
        var lines=new ArrayList<OrderedText>();for(var row:text)lines.addAll(f.wrapLines(row,220));orderedTooltip(c,f,lines,x,y);
    }
    static void orderedTooltip(DrawContext c,TextRenderer f,List<OrderedText> lines,int mx,int my){
        if(lines.isEmpty())return;
        var window=net.minecraft.client.MinecraftClient.getInstance().getWindow();int sw=window.getScaledWidth(),sh=window.getScaledHeight();
        int w=Math.min(sw-12,lines.stream().mapToInt(f::getWidth).max().orElse(20)+16);
        int count=Math.min(lines.size(),Math.max(1,(sh-24)/11)),h=count*11+12;
        int x=Math.clamp(mx+10,6,sw-w-6),y=Math.clamp(my+12,6,sh-h-6);
        c.draw();c.getMatrices().push();c.getMatrices().translate(0,0,700);panel(c,new Rect(x,y,w,h),1);
        for(int i=0;i<count;i++){
            OrderedText row=lines.get(i);
            OrderedText styled=visitor->row.accept((index,style,codepoint)->visitor.accept(index,style.withFont(FONT).withColor(INK),codepoint));
            c.drawText(f,styled,x+8,y+6+i*11,INK,false);
        }
        c.getMatrices().pop();
    }
}
