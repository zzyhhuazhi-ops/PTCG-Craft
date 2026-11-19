package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collection.*;
import java.util.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Live-style scrolling sections. Edits remain a draft until the server accepts Apply. */
final class CollectionFilterPanel {
    private record Tile(String group, String value, String label, int x, int y, int width, boolean heading) { }
    private final List<CollectionSearch.Facet> facets;
    private final List<CollectionSearch.Option> formats;
    private final Runnable changed;
    private final SmoothMotion scroll = new SmoothMotion();
    private final List<Tile> tiles = new ArrayList<>();
    private CollectionFilters draft;
    private PhoneLayout.Frame frame;
    private String optionQuery = "";
    private int contentHeight, drawnOffset;
    CollectionFilterPanel(CollectionView view, Runnable changed) {
        facets=view.facets();formats=view.formats();draft=view.filters();this.changed=changed;
    }
    CollectionFilters draft() { return draft; }
    void layout(PhoneLayout.Frame frame) { this.frame=frame;rebuild(); }
    void query(String query) { optionQuery=CollectionSearch.normalizeQuery(query);scroll.jump(0);rebuild(); }
    void clear() { draft=CollectionFilters.ALL;changed.run(); }
    void tick() { scroll.tick(); }
    private int top() { return frame.y()+91; }
    private int bottom() { return frame.bottom()-24; }
    private int maximum() { return Math.max(0,contentHeight-(bottom()-top())); }
    boolean scroll(double vertical) { scroll.move(-vertical*38,0,maximum());return true; }
    private void rebuild() {
        if(frame==null)return;tiles.clear();contentHeight=0;
        section("ownership","显示卡牌",List.of("ALL","OWNED","MISSING","DUPLICATES","INVENTORY","DEPOSITED","NOT_DEPOSITED").stream().map(v->new CollectionSearch.Option(v,CollectionSearch.label(v))).toList());
        section("format","赛制卡池（不代表技能已实现）",formats);
        // Current Live places the regulation-letter section before the other card attributes.
        facets.stream().filter(f->f.id().equals("mark")).forEach(f->section(f.id(),f.label(),f.options()));
        for(var f:facets)if(!f.id().equals("mark"))section(f.id(),f.label(),f.options());
        section("sort","排序",CollectionFilters.SORTS.stream().map(v->new CollectionSearch.Option(v,CollectionSearch.label(v))).toList());
        section("direction","顺序",List.of(new CollectionSearch.Option("ASC","升序／基础在前"),new CollectionSearch.Option("DESC","降序／进化在前")));
        scroll.clamp(0,maximum());
    }
    private void section(String group,String label,List<CollectionSearch.Option> options) {
        var shown=options.stream().filter(o->optionQuery.isEmpty()||CollectionSearch.normalizeQuery(o.value()+" "+o.label()+" "+label).contains(optionQuery)).toList();
        if(shown.isEmpty())return;
        int x=frame.innerX(),w=frame.innerWidth()-6;
        tiles.add(new Tile(group,"",label,x,contentHeight,w,true));contentHeight+=22;
        int cols=group.equals("set")||group.equals("format")||group.equals("direction")?2:3;
        int cell=(w-8-(cols-1)*4)/cols;
        for(int i=0;i<shown.size();i++) {
            var o=shown.get(i);tiles.add(new Tile(group,o.value(),o.label(),x+4+i%cols*(cell+4),contentHeight+i/cols*25,cell,false));
        }
        contentHeight+=((shown.size()+cols-1)/cols)*25+9;
    }
    private boolean selected(Tile tile) {
        return switch(tile.group()) {
            case "ownership"->draft.ownership().equals(tile.value());case "format"->draft.format().equals(tile.value());
            case "sort"->draft.sort().equals(tile.value());case "direction"->draft.descending()==tile.value().equals("DESC");
            default->draft.selected(tile.group(),tile.value());
        };
    }
    void render(DrawContext context,TextRenderer text,int mouseX,int mouseY,float delta) {
        drawnOffset=(int)Math.round(scroll.render(delta));
        context.enableScissor(frame.innerX(),top(),frame.right()-8,bottom());
        Tile hovered=null;
        for(var tile:tiles) {
            int y=top()+tile.y()-drawnOffset,h=tile.heading()?19:21;
            if(y+h<=top()||y>=bottom())continue;
            boolean over=mouseX>=tile.x()&&mouseX<tile.x()+tile.width()&&mouseY>=y&&mouseY<y+h&&mouseY>=top()&&mouseY<bottom();
            if(tile.heading()) {
                PhoneUi.card(context,tile.x(),y,tile.width(),19,PhoneUi.SURFACE);
                context.drawText(text,text.trimToWidth(tile.label(),tile.width()-44),tile.x()+5,y+6,PhoneUi.INK,false);
                if(CollectionFilters.GROUPS.contains(tile.group()))context.drawText(text,"全选",tile.x()+tile.width()-28,y+6,draft.facets().containsKey(tile.group())?PhoneUi.BLUE:PhoneUi.MUTED,false);
            } else {
                boolean checked=selected(tile);
                PhoneUi.card(context,tile.x(),y,tile.width(),21,checked?PhoneUi.SELECTED:over?PhoneUi.SURFACE_ALT:PhoneUi.SURFACE);
                int inset=checked?13:5;
                if(checked)context.drawText(text,"✓",tile.x()+4,y+7,PhoneUi.BLUE,false);
                context.drawText(text,text.trimToWidth(tile.label(),tile.width()-inset-4),tile.x()+inset,y+7,PhoneUi.INK,false);
                if(over)hovered=tile;
            }
        }
        context.disableScissor();
        int viewport=bottom()-top();
        if(maximum()>0) {
            int h=Math.max(12,viewport*viewport/Math.max(1,contentHeight)),y=top()+(int)((viewport-h)*scroll.value()/maximum());
            context.fill(frame.right()-13,top(),frame.right()-10,bottom(),PhoneUi.BORDER);
            context.fill(frame.right()-13,y,frame.right()-10,y+h,PhoneUi.BLUE);
        }
        if(hovered!=null&&text.getWidth(hovered.label())>hovered.width()-18)context.drawTooltip(text,Text.literal(hovered.label()),mouseX,mouseY);
    }
    boolean click(double mouseX,double mouseY) {
        if(mouseY<top()||mouseY>=bottom())return false;
        for(var tile:tiles) {
            int y=top()+tile.y()-drawnOffset,h=tile.heading()?19:21;
            if(mouseX<tile.x()||mouseX>=tile.x()+tile.width()||mouseY<y||mouseY>=y+h)continue;
            if(tile.heading()) {
                if(!CollectionFilters.GROUPS.contains(tile.group())||mouseX<tile.x()+tile.width()-40)return true;
                var all=new TreeSet<String>();facets.stream().filter(f->f.id().equals(tile.group())).findFirst().orElseThrow().options().forEach(o->all.add(o.value()));
                if(draft.facets().getOrDefault(tile.group(),Set.of()).equals(all))draft=draft.clearGroup(tile.group());
                else {var selected=new TreeMap<>(draft.facets());selected.put(tile.group(),all);draft=new CollectionFilters(selected,draft.ownership(),draft.format(),draft.sort(),draft.descending());}
            } else draft=switch(tile.group()) {
                case "ownership"->draft.withOwnership(tile.value());case "format"->draft.withFormat(tile.value());case "sort"->draft.withSort(tile.value());
                case "direction"->draft.descending()==tile.value().equals("DESC")?draft:draft.reverse();default->draft.toggle(tile.group(),tile.value());
            };
            changed.run();return true;
        }
        return true;
    }
}
