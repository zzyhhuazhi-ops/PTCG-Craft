package dev.hua.ptcglive.gametest;

import com.google.gson.*;
import dev.hua.ptcglive.PtcgContent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;

public final class FieldGuideIntegrationTest implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removesOnlyLegacyTutorialBooks(TestContext context) {
        var old = new net.minecraft.item.ItemStack(net.minecraft.item.Items.WRITTEN_BOOK);
        var pages = new ArrayList<net.minecraft.text.RawFilteredPair<net.minecraft.text.Text>>();
        pages.add(net.minecraft.text.RawFilteredPair.of(net.minecraft.text.Text.literal("欢迎来到 PTCG Craft\n\n首次进入时，你会收到宝可梦手机、新手随机卡组和这本书。\n\n先手持手机右键，看看商城、图鉴和卡盒。")));
        for(int i=1;i<13;i++)pages.add(net.minecraft.text.RawFilteredPair.of(net.minecraft.text.Text.literal("旧教程页")));
        var content = new net.minecraft.component.type.WrittenBookContentComponent(net.minecraft.text.RawFilteredPair.of("PTCG Craft 零基础入门"),"PTCG Craft",0,pages,true);
        old.set(net.minecraft.component.DataComponentTypes.WRITTEN_BOOK_CONTENT,content);
        var personal = old.copy();
        personal.set(net.minecraft.component.DataComponentTypes.WRITTEN_BOOK_CONTENT,new net.minecraft.component.type.WrittenBookContentComponent(content.title(),"玩家",0,pages,true));
        var inventory = new net.minecraft.inventory.SimpleInventory(old,personal,new net.minecraft.item.ItemStack(PtcgContent.FIELD_GUIDE),new net.minecraft.item.ItemStack(net.minecraft.item.Items.BOOK));
        context.assertTrue(dev.hua.ptcglive.LegacyTutorialCleanup.removeFrom(inventory),"旧教程没有清理");
        context.assertTrue(inventory.getStack(0).isEmpty(),"旧教程仍在");
        context.assertTrue(!inventory.getStack(1).isEmpty()&&inventory.getStack(2).isOf(PtcgContent.FIELD_GUIDE)&&inventory.getStack(3).isOf(net.minecraft.item.Items.BOOK),"误删其他书籍");
        context.assertTrue(!dev.hua.ptcglive.LegacyTutorialCleanup.removeFrom(inventory),"重复清理改变了其他物品");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void handbookLinksItemsAndLiveRecipesResolve(TestContext context) throws Exception {
        JsonArray entries;
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/ptcglive/guide/zh_cn.json")),StandardCharsets.UTF_8)){
            entries=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
        }
        var ids=new HashSet<String>();var recipes=new HashSet<String>();
        for(var element:entries){var e=element.getAsJsonObject();context.assertTrue(ids.add(e.get("id").getAsString()),"手册条目重复");}
        for(var element:entries){
            var e=element.getAsJsonObject();checkItem(context,e.get("icon").getAsString());
            for(var link:e.getAsJsonArray("links"))context.assertTrue(ids.contains(link.getAsString()),"手册链接失效："+link);
            for(var raw:e.getAsJsonArray("pages")){
                var p=raw.getAsJsonObject();for(var item:p.getAsJsonArray("items"))checkItem(context,item.getAsString());
                String recipe=p.get("recipe").getAsString();if(recipe.isBlank())continue;recipes.add(recipe);
                var found=context.getWorld().getRecipeManager().get(Identifier.of(recipe));
                context.assertTrue(found.isPresent(),"当前世界缺少手册配方："+recipe);
                if(!recipe.endsWith("card_box_dye"))context.assertTrue(!found.orElseThrow().value().getResult(context.getWorld().getRegistryManager()).isEmpty(),"配方产物为空："+recipe);
            }
        }
        context.assertTrue(recipes.contains("ptcglive:field_guide"),"手册缺少补做配方");
        var result=context.getWorld().getRecipeManager().get(Identifier.of("ptcglive:field_guide")).orElseThrow().value().getResult(context.getWorld().getRegistryManager());
        context.assertTrue(result.isOf(PtcgContent.FIELD_GUIDE)&&result.getCount()==1,"手册配方产物错误");
        context.complete();
    }
    private static void checkItem(TestContext c,String id){c.assertTrue(Registries.ITEM.containsId(Identifier.of(id)),"手册引用未注册物品："+id);}
}
