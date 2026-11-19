package dev.hua.ptcglive.collectibles;

import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Each occupied cell is real. Only the origin owns inventory and drops a machine item. */
public final class CollectibleStructure {
    public static final IntProperty X=IntProperty.of("part_x",0,3), Y=IntProperty.of("part_y",0,3), Z=IntProperty.of("part_z",0,2);
    public static int width(CollectibleBlocks.Kind k){return k==CollectibleBlocks.Kind.EXCHANGE?4:k==CollectibleBlocks.Kind.HOLOGRAM?2:1;}
    public static int height(CollectibleBlocks.Kind k){return k==CollectibleBlocks.Kind.EXCHANGE?4:k==CollectibleBlocks.Kind.HOLOGRAM?2:1;}
    public static int depth(CollectibleBlocks.Kind k){return k==CollectibleBlocks.Kind.EXCHANGE?3:k==CollectibleBlocks.Kind.HOLOGRAM?2:1;}
    public static boolean origin(BlockState s){return s.get(X)==0&&s.get(Y)==0&&s.get(Z)==0;}
    public static BlockPos cell(BlockPos root,Direction face,int x,int y,int z){return root.offset(face.rotateYClockwise(),x).up(y).offset(face.getOpposite(),z);}
    public static BlockPos root(BlockPos pos,BlockState s){return cell(pos,s.get(Properties.HORIZONTAL_FACING),-s.get(X),-s.get(Y),-s.get(Z));}
    public static CollectibleEntity entity(World world,BlockPos pos){
        var s=world.getBlockState(pos);if(!(s.getBlock() instanceof CollectibleBlock))return null;
        var root=root(pos,s);return world.isChunkLoaded(root)&&world.getBlockEntity(root) instanceof CollectibleEntity e?e:null;
    }
    public static boolean fits(ItemPlacementContext c,BlockState s){
        var kind=((CollectibleBlock)s.getBlock()).kind;int w=width(kind),h=height(kind),d=depth(kind);var world=c.getWorld();
        for(int x=0;x<w;x++)for(int y=0;y<h;y++)for(int z=0;z<d;z++){
            var p=cell(c.getBlockPos(),s.get(Properties.HORIZONTAL_FACING),x,y,z);
            if(!world.isInBuildLimit(p)||!world.getWorldBorder().contains(p)||!world.isChunkLoaded(p)
                ||!world.getBlockState(p).canReplace(c)||c.getPlayer()!=null&&!world.canPlayerModifyAt(c.getPlayer(),p)
                ||!world.canPlace(s.with(X,x).with(Y,y).with(Z,z),p,ShapeContext.absent()))return false;
        }return true;
    }
    public static void fill(World world,BlockPos root,BlockState s){
        int w=width(((CollectibleBlock)s.getBlock()).kind),h=height(((CollectibleBlock)s.getBlock()).kind),d=depth(((CollectibleBlock)s.getBlock()).kind);
        for(int x=0;x<w;x++)for(int y=0;y<h;y++)for(int z=0;z<d;z++)if(x+y+z>0)
            world.setBlockState(cell(root,s.get(Properties.HORIZONTAL_FACING),x,y,z),s.with(X,x).with(Y,y).with(Z,z),Block.NOTIFY_ALL);
    }
    /** Old one-cell machines expand only into free loaded space; never overwrite a player's build. */
    public static boolean expandLegacy(World world,BlockPos root,BlockState s){
        int w=width(((CollectibleBlock)s.getBlock()).kind),h=height(((CollectibleBlock)s.getBlock()).kind),d=depth(((CollectibleBlock)s.getBlock()).kind);
        for(int x=0;x<w;x++)for(int y=0;y<h;y++)for(int z=0;z<d;z++)if(x+y+z>0){
            var p=cell(root,s.get(Properties.HORIZONTAL_FACING),x,y,z);if(!world.isInBuildLimit(p)||!world.getWorldBorder().contains(p)||!world.isChunkLoaded(p))return false;
            var old=world.getBlockState(p);if(!old.isAir()&&!(old.isOf(s.getBlock())&&root(p,old).equals(root)))return false;
        }
        var machine=(CollectibleEntity)world.getBlockEntity(root);
        if(((CollectibleBlock)s.getBlock()).kind==CollectibleBlocks.Kind.EXCHANGE){
            // Both former footprints (4x2x2 and 2x4x3) fit inside the new footprint.
            machine.resizing=true;
            try{fill(world,root,s);}finally{machine.resizing=false;}
        }else fill(world,root,s);return true;
    }
    public static void remove(World world,BlockPos pos,BlockState old){
        var root=root(pos,old);
        if(!origin(old)){
            if(world.getBlockEntity(root) instanceof CollectibleEntity entity&&entity.resizing)return;
            if(world.isChunkLoaded(root)&&world.getBlockState(root).isOf(old.getBlock()))world.breakBlock(root,true);
            return;
        }
        int w=width(((CollectibleBlock)old.getBlock()).kind),h=height(((CollectibleBlock)old.getBlock()).kind),d=depth(((CollectibleBlock)old.getBlock()).kind);
        for(int x=0;x<(((CollectibleBlock)old.getBlock()).kind==CollectibleBlocks.Kind.EXCHANGE?4:w);x++)for(int y=0;y<h;y++)for(int z=0;z<d;z++)if(x+y+z>0){
            var p=cell(root,old.get(Properties.HORIZONTAL_FACING),x,y,z);if(!world.isChunkLoaded(p))continue;
            var s=world.getBlockState(p);if(s.isOf(old.getBlock())&&root(p,s).equals(root))world.removeBlock(p,false);
        }
    }
    private CollectibleStructure(){}
}
