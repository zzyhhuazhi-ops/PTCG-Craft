package dev.hua.ptcglive.client;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Display-only multi-select; the caller's authoritative pool and transaction remain unchanged. */
final class RarityFilterScreen extends Screen {
    private final Screen parent;private final List<String> rarities;private final Set<String> selected;private final Runnable changed;
    RarityFilterScreen(Screen parent,List<String> rarities,Set<String> selected,Runnable changed) {
        super(Text.literal("勾选需要显示的罕度"));this.parent=parent;this.rarities=List.copyOf(rarities);this.selected=selected;this.changed=changed;
    }
    @Override protected void init() {
        int rows=Math.max(1,(height-84)/24),cols=Math.max(2,(rarities.size()+rows-1)/rows),w=Math.min(160,(width-24)/cols);
        int left=(width-cols*w)/2;
        for(int i=0;i<rarities.size();i++) {
            String rarity=rarities.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal((selected.contains(rarity)?"☑ ":"☐ ")+rarity),b->{if(!selected.remove(rarity))selected.add(rarity);changed.run();clearAndInit();})
                .dimensions(left+(i/rows)*w,32+(i%rows)*24,w-4,20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("全选"),b->{selected.addAll(rarities);changed.run();clearAndInit();}).dimensions(width/2-122,height-32,78,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("清空"),b->{selected.clear();changed.run();clearAndInit();}).dimensions(width/2-40,height-32,78,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"),b->close()).dimensions(width/2+42,height-32,78,20).build());
    }
    @Override public void render(DrawContext ctx,int x,int y,float delta){ctx.fill(0,0,width,height,0xFF172638);ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xFFFFFF);super.render(ctx,x,y,delta);}
    @Override public void renderBackground(DrawContext ctx,int x,int y,float delta){}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
