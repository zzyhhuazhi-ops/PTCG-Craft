package dev.hua.ptcglive.client;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** An illustrated field guide: index on the left, paginated articles and live recipes on the right. */
public final class FieldGuideScreen extends Screen {
    private static final int PAPER=0xfff2e9cf, INK=0xff302c27, MUTED=0xff75664f, ACCENT=0xff315e63;
    private FieldGuideData.Book book;
    private FieldGuideData.Entry entry;
    private List<FieldGuideData.Entry> filtered=List.of();
    private final Deque<String> history=new ArrayDeque<>();
    private String section="全部", query="";
    private int indexStart, page, x,y,w,h,left,right,bodyW;
    private List<Leaf> leaves=List.of();
    private TextFieldWidget search;
    private final List<HotItem> hotItems=new ArrayList<>();
    private record Leaf(FieldGuideData.Page source,List<OrderedText> lines,boolean visual) { }
    private record HotItem(int x,int y,ItemStack stack) { }
    public FieldGuideScreen(){super(Text.literal("PTCG Craft · 玩家手册"));}
    @Override protected void init(){
        if(book==null){book=FieldGuideData.load(client.getResourceManager());entry=book.entries().getFirst();}
        w=Math.min(740,width-12);h=Math.min(440,height-12);x=(width-w)/2;y=(height-h)/2;
        left=Math.max(130,Math.min(200,w/3));right=x+left+18;bodyW=w-left-34;
        search=addDrawableChild(new TextFieldWidget(textRenderer,x+12,y+32,left-20,18,Text.literal("检索手册")));
        search.setText(query);search.setPlaceholder(Text.literal("搜索物品 / 玩法"));
        search.setChangedListener(value->{query=value;indexStart=0;rebuildIndex();});
        button("×",x+w-29,y+8,20,()->close());
        button("目录",right,y+8,38,()->{section="全部";query="";indexStart=0;search.setText("");rebuildIndex();});
        button("返回",right+42,y+8,38,()->{if(!history.isEmpty())open(history.pop(),false);});
        button("‹",right,y+h-26,26,()->turn(-1));
        button("›",x+w-40,y+h-26,26,()->turn(1));
        rebuildIndex();paginate();
    }
    private void button(String label,int px,int py,int span,Runnable action){
        addDrawableChild(new GuideButton(px,py,span,label,action));
    }
    private final class GuideButton extends ButtonWidget {
        GuideButton(int px,int py,int span,String label,Runnable action){super(px,py,span,18,Text.literal(label),b->action.run(),DEFAULT_NARRATION_SUPPLIER);}
            @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
                c.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isSelected()?0xffd4c6a8:0xffe4d8bb);
                c.drawCenteredTextWithShadow(textRenderer,getMessage(),getX()+getWidth()/2,getY()+5,ACCENT);
            }
    }
    private List<String> sections(){var names=new ArrayList<String>();names.add("全部");book.entries().stream().map(FieldGuideData.Entry::section).distinct().forEach(names::add);return names;}
    private int sectionRows(){return (sections().size()+1)/2;}
    private int indexTop(){return y+58+sectionRows()*18+8;}
    private int indexCapacity(){return Math.max(1,(y+h-30-indexTop())/23);}
    private void rebuildIndex(){
        String needle=query.toLowerCase(Locale.ROOT);
        filtered=book.entries().stream().filter(e->section.equals("全部")||section.equals(e.section()))
            .filter(e->needle.isBlank()||(e.title()+e.id()+e.pages().stream().map(FieldGuideData.Page::text).reduce("",String::concat)).toLowerCase(Locale.ROOT).contains(needle)).toList();
        indexStart=Math.clamp(indexStart,0,Math.max(0,filtered.size()-indexCapacity()));
    }
    private void open(String id,boolean remember){
        book.entries().stream().filter(e->e.id().equals(id)).findFirst().ifPresent(next->{
            if(remember&&entry!=null&&!entry.id().equals(id))history.push(entry.id());
            entry=next;page=0;paginate();
            setFocused(null);
        });
    }
    private void paginate(){
        var output=new ArrayList<Leaf>();
        int available=h-100-(entry.links().isEmpty()?0:18);
        for(var p:entry.pages()){
            var lines=textRenderer.wrapLines(Text.literal(p.text()),Math.max(40,bodyW));
            boolean visual=!p.recipe().isBlank()||!p.items().isEmpty();
            int decoration=!p.recipe().isBlank()?100:visual?40:0;
            int cursor=0;
            do{
                int capacity=Math.max(1,(available-(visual?decoration:0))/12);
                int end=Math.min(lines.size(),cursor+capacity);
                output.add(new Leaf(p,List.copyOf(lines.subList(cursor,end)),visual));
                cursor=end;visual=false;
            }while(cursor<lines.size());
        }
        leaves=List.copyOf(output);page=Math.clamp(page,0,Math.max(0,leaves.size()-1));
    }
    private void turn(int direction){page=Math.clamp(page+direction,0,leaves.size()-1);}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta) { }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        hotItems.clear();
        c.fill(0,0,width,height,0x880d171b);
        c.fill(x-3,y+3,x+w+4,y+h+5,0x88000000);
        c.fill(x,y,x+w,y+h,0xff243f44);c.fill(x+5,y+5,x+w-5,y+h-5,0xffbb9c64);
        c.fill(x+8,y+7,x+left+7,y+h-7,0xffe8ddc2);c.fill(x+left+11,y+7,x+w-8,y+h-7,PAPER);
        c.fill(x+left+6,y+10,x+left+12,y+h-10,0xffac9672);
        for(int yy=y+18;yy<y+h-14;yy+=23)c.fill(x+left+8,yy,x+left+10,yy+10,0xffd3c1a0);
        c.drawText(textRenderer,textRenderer.trimToWidth("PTCG CRAFT / 玩家手册",left-22),x+13,y+15,ACCENT,false);
        var sections=sections();
        for(int i=0;i<sections.size();i++){
            int sx=x+12+i%2*(left-20)/2,sy=y+58+i/2*18,sw=(left-20)/2-3;
            boolean selected=section.equals(sections.get(i));
            c.fill(sx,sy,sx+sw,sy+16,selected?ACCENT:0xffdbceb0);
            c.drawText(textRenderer,textRenderer.trimToWidth(sections.get(i),sw-6),sx+3,sy+4,selected?0xfffaf2db:INK,false);
        }
        for(int i=0;i<indexCapacity()&&indexStart+i<filtered.size();i++){
            var row=filtered.get(indexStart+i);int sy=indexTop()+i*23;
            if(row.id().equals(entry.id()))c.fill(x+11,sy,x+left-7,sy+22,0xffcfc6a8);
            c.drawItem(stack(row.icon()),x+13,sy+2);
            c.drawText(textRenderer,textRenderer.trimToWidth(row.title(),left-49),x+33,sy+7,INK,false);
        }
        c.drawText(textRenderer,filtered.isEmpty()?"没有匹配条目":(indexStart+1)+"–"+Math.min(filtered.size(),indexStart+indexCapacity())+" / "+filtered.size()+" · 滚轮",x+13,y+h-20,MUTED,false);
        c.drawText(textRenderer,textRenderer.trimToWidth(entry.section()+" / "+entry.title(),bodyW),right,y+34,ACCENT,false);
        c.fill(right,y+47,right+bodyW,y+48,0xffcbb78e);
        if(!leaves.isEmpty()){
            var leaf=leaves.get(page);int ty=y+55;
            if(leaf.visual()){
                if(!leaf.source().recipe().isBlank()){drawRecipe(c,leaf.source().recipe(),right,ty);ty+=100;}
                else {int ix=right;for(String item:leaf.source().items()){if(ix+18>right+bodyW)break;drawItem(c,stack(item),ix,ty+4);ix+=28;if(ix+18<right+bodyW)c.drawText(textRenderer,"→",ix-9,ty+9,MUTED,false);}ty+=40;}
            }
            for(var line:leaf.lines()){c.drawText(textRenderer,line,right,ty,INK,false);ty+=12;}
        }
        if(!entry.links().isEmpty()){
            int span=bodyW/entry.links().size();
            for(int i=0;i<entry.links().size();i++){
                String target=entry.links().get(i);int lx=right+i*span;
                var linked=book.entries().stream().filter(e->e.id().equals(target)).findFirst();
                linked.ifPresent(e->c.drawText(textRenderer,textRenderer.trimToWidth("→ "+e.title(),span-3),lx,y+h-45,ACCENT,false));
            }
        }
        c.drawCenteredTextWithShadow(textRenderer,(page+1)+" / "+leaves.size(),right+bodyW/2,y+h-21,MUTED);
        super.render(c,mx,my,delta);
        for(var item:hotItems)if(mx>=item.x()&&mx<item.x()+18&&my>=item.y()&&my<item.y()+18){c.drawItemTooltip(textRenderer,item.stack(),mx,my);break;}
    }
    private static ItemStack stack(String id){var key=Identifier.tryParse(id);return key==null?ItemStack.EMPTY:Registries.ITEM.get(key).getDefaultStack();}
    private void drawItem(DrawContext c,ItemStack item,int px,int py){
        if(item.isEmpty())return;c.drawItem(item,px,py);c.drawItemInSlot(textRenderer,item,px,py);hotItems.add(new HotItem(px,py,item));
    }
    private void slot(DrawContext c,int sx,int sy){c.fill(sx-1,sy-1,sx+18,sy+18,0xfffcf7e9);c.fill(sx-1,sy-1,sx+17,sy+17,0xff645c4e);c.fill(sx,sy,sx+17,sy+17,0xffb7af9e);}
    private void drawRecipe(DrawContext c,String id,int px,int py){
        var found=client.world==null?Optional.<net.minecraft.recipe.RecipeEntry<?>>empty():client.world.getRecipeManager().get(Identifier.of(id));
        c.drawText(textRenderer,"工作台 · 3 × 3",px,py,ACCENT,false);
        for(int i=0;i<9;i++)slot(c,px+i%3*19,py+17+i/3*19);
        int ox=px+94,oy=py+36;slot(c,ox,oy);c.drawText(textRenderer,"→",px+70,oy+5,ACCENT,false);
        if(found.isEmpty()){c.drawText(textRenderer,"当前世界未启用此配方",px,py+80,MUTED,false);return;}
        var recipe=found.get().value();
        if(id.equals("ptcglive:card_box_dye")){
            drawItem(c,stack("ptcglive:card_box"),px,py+17);drawItem(c,stack("minecraft:blue_dye"),px+19,py+17);
            var dyed=stack("ptcglive:card_box");dyed.set(net.minecraft.component.DataComponentTypes.DYED_COLOR,new net.minecraft.component.type.DyedColorComponent(0x334cb2,true));drawItem(c,dyed,ox,oy);
            c.drawText(textRenderer,"无序 · 蓝色染料示例",px,py+80,MUTED,false);return;
        }
        var ingredients=recipe.getIngredients();int rw=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
        long cycle=System.currentTimeMillis()/1500;
        for(int i=0;i<ingredients.size();i++){
            var choices=ingredients.get(i).getMatchingStacks();if(choices.length==0)continue;
            int col=i%rw,row=i/rw;if(row<3)drawItem(c,choices[(int)(cycle%choices.length)],px+col*19,py+17+row*19);
        }
        drawItem(c,recipe.getResult(client.world.getRegistryManager()),ox,oy);
        c.drawText(textRenderer,textRenderer.trimToWidth(recipe instanceof ShapedRecipe?"按格摆放 · 可替换材料轮换显示":"无序合成 · 材料位置不限",bodyW),px,py+80,MUTED,false);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0){
            var sections=sections();for(int i=0;i<sections.size();i++){
                int sx=x+12+i%2*(left-20)/2,sy=y+58+i/2*18;
                if(mx>=sx&&mx<sx+(left-20)/2-3&&my>=sy&&my<sy+16){section=sections.get(i);indexStart=0;rebuildIndex();return true;}
            }
            if(mx>=x+11&&mx<x+left-7&&my>=indexTop()&&my<indexTop()+indexCapacity()*23){
                int i=indexStart+(int)(my-indexTop())/23;if(i<filtered.size()){open(filtered.get(i).id(),true);return true;}
            }
            if(!entry.links().isEmpty()&&mx>=right&&mx<right+bodyW&&my>=y+h-48&&my<y+h-31){
                int i=Math.min(entry.links().size()-1,(int)(mx-right)/(bodyW/entry.links().size()));open(entry.links().get(i),true);return true;
            }
            for(var item:hotItems)if(mx>=item.x()&&mx<item.x()+18&&my>=item.y()&&my<item.y()+18){
                String id=Registries.ITEM.getId(item.stack().getItem()).toString();
                var target=book.entries().stream().filter(e->e.icon().equals(id)&&!e.id().equals(entry.id())).findFirst();
                if(target.isPresent()){open(target.get().id(),true);return true;}
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(vertical==0)return false;if(mx<right){indexStart+=(vertical>0?-2:2);rebuildIndex();}else turn(vertical>0?-1:1);return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!search.isFocused()){if(key==GLFW.GLFW_KEY_RIGHT){turn(1);return true;}if(key==GLFW.GLFW_KEY_LEFT){turn(-1);return true;}}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean shouldPause(){return false;}
}
