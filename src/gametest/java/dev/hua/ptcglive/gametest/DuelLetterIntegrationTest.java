package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.duel.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import dev.hua.ptcglive.engine.ServerRandom;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.item.*;
import java.util.*;

public final class DuelLetterIntegrationTest implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void letterFlightIsStraightAndStopsAtWall(TestContext c){
        var owner=c.createMockCreativeServerPlayerInWorld();
        var e=new DuelLetterEntity(DuelLetters.TYPE,c.getWorld());e.setOwner(owner);
        var start=Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(1,20,1)));var velocity=new Vec3d(0.7,0.2,0.3);
        e.setPosition(start);e.setVelocity(velocity);
        for(int i=0;i<8;i++)e.tick();
        c.assertTrue(e.getPos().squaredDistanceTo(start.add(velocity.multiply(8)))<1e-10&&e.getVelocity().equals(velocity),"战书受到重力／阻力偏离直线");
        e.discard();
        c.setBlockState(new BlockPos(2,2,2),net.minecraft.block.Blocks.STONE);
        var wall=new DuelLetterEntity(DuelLetters.TYPE,c.getWorld());wall.setOwner(owner);
        wall.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(1,2,2))));wall.setVelocity(1.5,0,0);wall.tick();
        c.assertTrue(wall.isRemoved(),"高速战书穿墙");
        owner.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hitOpensOnlyTwoTemporarySeatsAndRejectsReplay(TestContext c)throws Exception{
        var a=c.createMockCreativeServerPlayerInWorld();var b=c.createMockCreativeServerPlayerInWorld();var outsider=c.createMockCreativeServerPlayerInWorld();
        int before=PtcgServices.tables().tableCount();int cards=PtcgServices.cardBoxes().snapshot().cards().size();float health=b.getHealth();
        var start=Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(1,15,1)));a.setPosition(start);b.setPosition(start.add(0,0,4));
        var shot=new DuelLetterEntity(DuelLetters.TYPE,c.getWorld());shot.setOwner(a);shot.setPosition(a.getEyePos());shot.setVelocity(0,0,1.5);
        for(int i=0;i<4&&!shot.isRemoved();i++)shot.tick();
        c.assertTrue(shot.isRemoved(),"战书未命中玩家");
        var field=DuelLetters.class.getDeclaredField("ACTIVE");field.setAccessible(true);
        var keys=((Map<CardTableKey,?>)field.get(null)).keySet();var key=keys.stream().filter(k->DuelLetters.accessible(a,k)).findFirst().orElseThrow();
        var lobby=PtcgServices.tables().table(key).orElseThrow();
        c.assertTrue(lobby.isHost(a.getUuid())&&lobby.guest().orElseThrow().equals(b.getUuid())&&lobby.match().isEmpty(),"命中未进入准备或擅自开战");
        c.assertTrue(!DuelLetters.accessible(outsider,key),"第三者可操作临时卡桌");
        c.assertTrue(PtcgServices.tables().snapshot().waitingTables().stream().noneMatch(t->t.key().equals(key)),"临时卡桌写入存档");
        DuelLetters.hit(a,b);DuelLetters.hit(a,a);
        c.assertTrue(PtcgServices.tables().tableCount()==before+1&&b.getHealth()==health,"重复邀战或战书造成伤害");
        c.assertTrue(PtcgServices.cardBoxes().snapshot().cards().size()==cards,"邀战复制实体卡牌");
        DuelLetters.closeFor(c.getWorld().getServer(),a.getUuid());
        c.assertTrue(!DuelLetters.exists(key)&&PtcgServices.tables().table(key).isEmpty(),"退出未清除临时卡桌");
        for(var p:List.of(a,b,outsider))p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void temporarySeatsUseNormalValidationAndReleaseLocks(TestContext c){
        var boxes=new CardBoxService(java.time.Clock.systemUTC());
        var tables=new CardTableManager(PtcgServices.rulesets(),boxes,PtcgServices.cardDatabase(),BundledCombatData.load(),()->new ServerRandom(18));
        var a=UUID.randomUUID();var b=UUID.randomUUID();var rule="standard-international-2026";
        var first=boxes.create(a,"战书甲",rule);var second=boxes.create(b,"战书乙",rule);
        for(var box:List.of(first,second))for(int i=0;i<60;i++)boxes.put(box.ownerId(),box.id(),boxes.mint(box.ownerId(),i<4?"fixture-basic-h":"fixture-energy-h").instanceId());
        var key=new CardTableKey("minecraft:overworld",new BlockPos(77,-2048,0).asLong());
        tables.openTemporary(key,a,b,rule);
        try{tables.start(key,a);throw new AssertionError("未准备可开战");}catch(IllegalStateException expected){}
        tables.selectBox(key,a,first.id());tables.selectBox(key,b,second.id());tables.setReady(key,a,true);tables.setReady(key,b,true);
        var match=tables.start(key,a);
        c.assertTrue(boxes.isLocked(a,first.id())&&boxes.isLocked(b,second.id())&&tables.hasMatch(match.id()),"未复用正式锁定与对战");
        c.assertTrue(tables.snapshot().waitingTables().isEmpty()&&boxes.snapshot().cards().size()==120,"临时卡桌复制卡或持久化大厅");
        tables.removeTemporaryTable(key);
        c.assertTrue(!boxes.isLocked(a,first.id())&&!boxes.isLocked(b,second.id())&&!tables.hasMatch(match.id()),"清理未释放双方卡盒");
        c.assertTrue(boxes.contentsForEditing(a,first.id()).size()==60&&boxes.contentsForEditing(b,second.id()).size()==60,"清理损失卡牌");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void letterCraftingAndCooldown(TestContext c){
        var ingredients=new ArrayList<ItemStack>();for(int i=0;i<9;i++)ingredients.add(new ItemStack(i==4?Items.RED_DYE:Items.PAPER));
        var input=net.minecraft.recipe.input.CraftingRecipeInput.create(3,3,ingredients);
        var recipe=c.getWorld().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,c.getWorld()).orElseThrow();
        var result=recipe.value().craft(input,c.getWorld().getRegistryManager());
        c.assertTrue(result.isOf(DuelLetters.ITEM)&&result.getCount()==8,"战书配方错误");
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"letter-survival");
        var data=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var p=new net.minecraft.server.network.ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),profile,data.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        c.getWorld().getServer().getPlayerManager().onPlayerConnect(connection,p,data);
        p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(DuelLetters.ITEM,2));
        DuelLetters.ITEM.use(c.getWorld(),p,net.minecraft.util.Hand.MAIN_HAND);
        DuelLetters.ITEM.use(c.getWorld(),p,net.minecraft.util.Hand.MAIN_HAND);
        c.assertTrue(p.getMainHandStack().getCount()==1&&p.getItemCooldownManager().isCoolingDown(DuelLetters.ITEM),"重复请求消耗多张战书");
        p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
    }
}
