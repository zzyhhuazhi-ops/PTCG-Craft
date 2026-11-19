package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.engine.field.BattlefieldState.PokemonView;
import dev.hua.ptcglive.view.BattleCardActions.Row;
import dev.hua.ptcglive.view.MatchRegionModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.*;

/** A transient focus layer owned by the table, never another Screen. */
final class BattleCardPanel {
    final MatchRegionModel.Card card;
    final long revision;
    final List<Row> rows;
    private final PokemonView pokemon;
    private final long opened = System.nanoTime();
    private long closed;
    private double closeFrom;
    private int page;
    private BattleCardPanelLayout layout;
    BattleCardPanel(MatchRegionModel.Card card, PokemonView pokemon, long revision, List<Row> rows) {
        this.card = card; this.pokemon = pokemon; this.revision = revision; this.rows = rows;
    }
    private double progress() {
        return closed == 0 ? BattleMotion.out((System.nanoTime() - opened) / 240_000_000.0)
            : closeFrom * (1 - BattleMotion.smooth((System.nanoTime() - closed) / 180_000_000.0));
    }
    void close() { if (closed == 0) { closeFrom = progress(); closed = System.nanoTime(); } }
    boolean closing() { return closed != 0; }
    boolean finished() { return closing() && progress() <= 0; }
    boolean animating() { return closed==0 ? System.nanoTime()-opened<240_000_000L : !finished(); }
    void resize(int width, int height) { layout = BattleCardPanelLayout.of(width, height, rows.size(), page); page = layout.page(); }
    void resize(int width,int height,boolean centered){layout=centered?BattleCardPanelLayout.centered(width,height,rows.size(),page):BattleCardPanelLayout.of(width,height,rows.size(),page);page=layout.page();}
    void turn(int delta) { page = Math.clamp(page + delta, 0, layout.pages() - 1); }
    boolean contains(double x, double y) { return layout.panel().contains(x, y) || layout.card().contains(x, y); }
    List<ButtonWidget> buttons(IntConsumer submit, Runnable cancel, Runnable rebuild) {
        var buttons = new ArrayList<ButtonWidget>(); int k = layout.scale();
        for (int i = layout.first(); i < layout.end(); i++) {
            Row row = rows.get(i);
            var button = new FocusButton(layout.row(i), row.title(), row, b -> {
                if (!closing() && progress() >= .98 && row.enabled()) submit.accept(row.option().index());
            });
            button.active = row.enabled();
            buttons.add(button);
        }
        buttons.add(new FocusButton(layout.close(), "收起", null, b -> cancel.run()));
        if (layout.pages() > 1) {
            var area = layout.panel();
            var previous = new FocusButton(new Rect(area.x() + 8*k, area.bottom() - 28*k, 26*k, 21*k), "‹", null,
                b -> { turn(-1); rebuild.run(); }); previous.active = page > 0; buttons.add(previous);
            var next = new FocusButton(new Rect(area.right() - 34*k, area.bottom() - 28*k, 26*k, 21*k), "›", null,
                b -> { turn(1); rebuild.run(); }); next.active = page < layout.pages() - 1; buttons.add(next);
        }
        return buttons;
    }
    void draw(DrawContext c, CardBoxArtCache artwork, Rect origin, int width, int height, PocketTableCamera camera, PocketTableCamera.Pose pose) {
        double p = progress(); int k = layout.scale(); var font = MinecraftClient.getInstance().textRenderer;
        c.fill(0, 0, width, height, fade(0xA6102033, p));
        Rect target = layout.card();
        int x = mix(origin.x(), target.x(), p), y = mix(origin.y(), target.y(), p),
            w = mix(origin.width(), target.width(), p), h = mix(origin.height(), target.height(), p);
        if(camera!=null&&pose!=null)PocketTableRenderer.inspect(c,camera,pose,artwork.get(card.definitionId()),target,p);
        else {
        BattleSceneEffects.focus(c,x,y,w,h,BattleSceneEffects.CYAN,System.nanoTime()/1e9);
        BattleUi.round(c,x-2*k,y-2*k,w+4*k,h+4*k,7*k,0xFFF4FDFF);
        CardPainter.slotCard(c,font,artwork.get(card.definitionId()),card.nameZh(),x,y,w,h);
        }
        c.draw();
        c.getMatrices().push(); c.getMatrices().translate(0, (1-p)*14*k, 1);
        var a = layout.panel();
        BattleUi.round(c,a.x()-2*k,a.y()+4*k,a.width()+4*k,a.height(),13*k,fade(0x60102640,p));
        BattleUi.round(c,a.x(),a.y(),a.width(),a.height(),12*k,fade(0xFFF5FBFE,p));
        if (p > .3) {
            BattleUi.text(c,font,font.trimToWidth(card.nameZh(),(a.width()-24*k)/k),a.x()+12*k,a.y()+10*k,0xFF213A51,false,k);
            String status = pokemon == null ? "公开卡牌" : "附能 " + pokemon.attachedEnergyCardIds().size();
            if(layout.pages()>1)status+="    "+(page+1)+"/"+layout.pages();
            BattleUi.text(c,font,status,a.x()+12*k,a.y()+25*k,0xFF536B7C,false,k);
            if (pokemon != null) {
                int remaining=Math.max(0,pokemon.hitPoints()-pokemon.damage());
                BattleUi.hp(c,font,remaining,pokemon.pokemonType(),a.right()-12*k,a.y()+24*k,k);
            }
        }
        c.getMatrices().pop();
    }
    private static int fade(int color,double opacity) { return ((int)((color>>>24)*Math.clamp(opacity,0,1))<<24)|(color&0xFFFFFF); }
    private static int mix(int a,int b,double p) { return (int)Math.round(a+(b-a)*p); }
    private final class FocusButton extends ButtonWidget {
        private final Row row;
        private final BattleMotion.ButtonHover focus=new BattleMotion.ButtonHover();
        FocusButton(Rect r,String title,Row row,PressAction action) {
            super(r.x(),r.y(),r.width(),r.height(),Text.literal(title),action,DEFAULT_NARRATION_SUPPLIER);this.row=row;
        }
        @Override public void onPress() { if(!closing()&&progress()>=.98)super.onPress(); }
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta) {
            int k=layout.scale(); double p=progress(); if(p<.05)return;
            long now=System.nanoTime();
            double hover=focus.sample(isSelected(),now);
            var font=MinecraftClient.getInstance().textRenderer; int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            c.getMatrices().push();c.getMatrices().translate(0,(1-p)*14*k-(active?hover*k:0),2);
            int tint=row!=null&&row.attack()&&pokemon!=null?BattleEffects.color(pokemon.pokemonType()):0xFF9AE9DF;
            if(active&&hover>0)BattleUi.round(c,x-2*k,y-2*k,w+4*k,h+4*k,10*k,fade(0xFF69E4EB,.55*p*hover));
            BattleUi.round(c,x,y+2*k,w,h,9*k,fade(0xFFB5C9D4,.7*p));
            BattleUi.round(c,x,y,w,h,9*k,fade(active?0xFFFFFFFF:0xFFE4EAEE,p));
            if(row!=null&&row.attack())BattleUi.round(c,x+2*k,y+2*k,w-4*k,h-4*k,7*k,fade(active?tint:0xFFD9E0E5,.85*p));
            else if(row!=null&&active){
                BattleUi.round(c,x,y,w,h,9*k,fade(0xFF9AE9DF,p));
                BattleUi.round(c,x+2*k,y+2*k,w-4*k,h-4*k,7*k,fade(0xFFFFFFFF,p));
            }
            if(p>.35) {
                int ink=active?0xFF20364A:0xFF627783;
                if(row==null)BattleUi.centeredText(c,font,getMessage().getString(),x+w/2,y+(h-8*k)/2,ink,false,k);
                else {
                    int numberWidth=row.damage().isBlank()?0:font.getWidth(row.damage())+10;
                    BattleUi.text(c,font,font.trimToWidth(row.title(),(w-18*k)/k-numberWidth),x+9*k,y+7*k,ink,false,k);
                    if(!row.damage().isBlank())BattleUi.text(c,font,row.damage(),x+w-(9+font.getWidth(row.damage()))*k,y+7*k,ink,false,k);
                    String cost=row.cost().isBlank()?row.attack()?"招式":"操作":row.cost();
                    BattleUi.text(c,font,font.trimToWidth(cost,(w-18*k)/k),x+9*k,y+21*k,ink,false,k);
                    String hint=row.detail()+(row.damage().isBlank()?"":" · 预估伤害");
                    BattleUi.text(c,font,font.trimToWidth(hint,(w-18*k)/k),x+9*k,y+33*k,ink,false,k);
                }
            }
            c.getMatrices().pop();
            if(row!=null&&focus.tooltipReady(now))
                BattleUi.tooltip(c,font,Text.literal(row.tooltip()),mx,my);
        }
    }
}
