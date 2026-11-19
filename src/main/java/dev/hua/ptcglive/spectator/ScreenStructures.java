package dev.hua.ptcglive.spectator;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Loaded structures only. All calls occur on the server thread. */
public final class ScreenStructures {
    private static final Set<SpectatorScreenEntity> loaded=new HashSet<>();
    private static final Map<ServerWorld,LinkedHashSet<BlockPos>> dirty=new HashMap<>();
    public static void clear(){loaded.clear();dirty.clear();}
    public static List<SpectatorScreenEntity> controllers(){return loaded.stream().filter(e->!e.isRemoved()&&e.controller()).toList();}
    public static void add(SpectatorScreenEntity entity){loaded.add(entity);dirty(entity.getWorld(),entity.getPos());}
    public static void remove(SpectatorScreenEntity entity){loaded.remove(entity);dirty(entity.getWorld(),entity.getPos());}
    public static void dirty(World world,BlockPos pos){
        if(!(world instanceof ServerWorld server))return;
        var pending=dirty.computeIfAbsent(server,w->new LinkedHashSet<>());pending.add(pos.toImmutable());
        for(var direction:Direction.values())pending.add(pos.offset(direction));
    }
    public static SpectatorScreenEntity root(World world,BlockPos pos){
        if(!world.isChunkLoaded(pos)||!(world.getBlockEntity(pos) instanceof SpectatorScreenEntity member))return null;
        if(!member.valid)return member;
        if(!world.isChunkLoaded(member.origin)||!(world.getBlockEntity(member.origin) instanceof SpectatorScreenEntity root)||!root.controller())return null;
        return root;
    }
    public static void tick(){
        for(var entry:dirty.entrySet()){
            var world=entry.getKey();var queue=entry.getValue();
            for(int work=0;work<8&&!queue.isEmpty();work++){
                var pos=queue.iterator().next();queue.remove(pos);
                if(!world.isChunkLoaded(pos)||!(world.getBlockEntity(pos) instanceof SpectatorScreenEntity start))continue;
                var face=start.getCachedState().get(SpectatorScreenBlock.FACING);
                var result=ScreenStructure.detect(pos,face,p->world.getBlockState(p).isOf(SpectatorScreens.BLOCK)
                    &&world.getBlockState(p).get(SpectatorScreenBlock.FACING)==face,world::isChunkLoaded);
                queue.removeAll(result.cells());
                var cells=result.cells().stream().map(world::getBlockEntity).filter(SpectatorScreenEntity.class::isInstance)
                    .map(SpectatorScreenEntity.class::cast).sorted(Comparator.comparingLong(e->e.getPos().asLong())).toList();
                var source=cells.stream().filter(e->e.match!=null||e.pendingHost!=null).findFirst().orElse(start);
                var matches=cells.stream().map(e->e.match).filter(Objects::nonNull).distinct().toList();
                UUID match=result.valid()&&matches.size()==1?matches.getFirst():null;
                boolean names=source.names,animations=source.animations,damage=source.damage;
                UUID pendingHost=source.pendingHost;long pendingTable=source.pendingTable;
                for(var cell:cells){
                    var origin=result.valid()?result.origin():cell.getPos();int w=result.valid()?result.width():1,h=result.valid()?result.height():1;
                    // Keep saved controller configuration while chunks are incomplete;
                    // invalid structures do not render or subscribe until reconstructed.
                    UUID bound=result.valid()?(origin.equals(cell.getPos())?match:null):cell.match;
                    boolean changed=cell.valid!=result.valid()||!cell.origin.equals(origin)||cell.width!=w||cell.height!=h||!Objects.equals(cell.match,bound)
                        ||cell.names!=names||cell.animations!=animations||cell.damage!=damage;
                    cell.valid=result.valid();cell.origin=origin;cell.width=w;cell.height=h;cell.match=bound;
                    if(result.valid()){
                        UUID waiting=origin.equals(cell.getPos())?pendingHost:null;
                        changed|=!Objects.equals(cell.pendingHost,waiting)||cell.pendingTable!=pendingTable;
                        cell.pendingHost=waiting;cell.pendingTable=pendingTable;
                    }
                    cell.names=names;cell.animations=animations;cell.damage=damage;
                    if(changed){cell.finishedAt=-1;cell.sync();}
                }
            }
        }
        dirty.entrySet().removeIf(e->e.getValue().isEmpty());
    }
    private ScreenStructures(){}
}
