package dev.hua.ptcglive.spectator;

import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Predicate;

/** Bounded flood fill in the face's vertical plane. Never force-loads a chunk. */
public final class ScreenStructure {
    public static final int MIN_WIDTH=3,MIN_HEIGHT=2,MAX_WIDTH=11,MAX_HEIGHT=7,SCAN_LIMIT=128;
    public record Result(BlockPos origin,int width,int height,boolean valid,Set<BlockPos> cells) {}
    public static Direction right(Direction face){return face.rotateYCounterclockwise();}
    public static BlockPos cell(BlockPos origin,Direction face,int x,int y){return origin.offset(right(face),x).up(y);}
    public static Result detect(BlockPos start,Direction face,Predicate<BlockPos> same,Predicate<BlockPos> loaded){
        var seen=new LinkedHashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(start);
        int minX=0,maxX=0,minY=0,maxY=0;boolean complete=true;
        var right=right(face);
        while(!queue.isEmpty()){
            var pos=queue.removeFirst();
            if(seen.contains(pos))continue;
            if(!loaded.test(pos)){complete=false;continue;}
            if(!same.test(pos))continue;
            if(seen.size()>=SCAN_LIMIT){complete=false;break;}
            seen.add(pos);
            int x=(pos.getX()-start.getX())*right.getOffsetX()+(pos.getZ()-start.getZ())*right.getOffsetZ(),y=pos.getY()-start.getY();
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            for(var next:List.of(pos.offset(right),pos.offset(right.getOpposite()),pos.up(),pos.down()))queue.add(next);
        }
        int width=maxX-minX+1,height=maxY-minY+1;
        boolean valid=complete&&width>=MIN_WIDTH&&width<=MAX_WIDTH&&height>=MIN_HEIGHT&&height<=MAX_HEIGHT&&seen.size()==width*height;
        return new Result(cell(start,face,minX,minY),width,height,valid,Set.copyOf(seen));
    }
    private ScreenStructure(){}
}
