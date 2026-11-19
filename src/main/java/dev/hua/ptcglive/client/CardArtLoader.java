package dev.hua.ptcglive.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** CPU decode on two workers, at most two GPU uploads per world frame, bounded to 16 outstanding images. */
final class CardArtLoader {
    record Art(Identifier texture,int width,int height,boolean highDefinition){}
    static final class Ticket {volatile boolean cancelled;synchronized void cancel(){cancelled=true;}}
    private record Ready(Ticket ticket,Identifier texture,NativeImage image,boolean hd,Consumer<Art> loaded,Runnable failed){}
    private static final ExecutorService WORKERS=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"ptcg-card-decode");t.setDaemon(true);return t;});
    private static final Semaphore SLOTS=new Semaphore(16);
    private static final Set<Ticket> ACTIVE=ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<Ready> READY=new ConcurrentLinkedQueue<>();
    private CardArtLoader(){}
    static void initialize(){
        // MatchHudMixin cancels HUD rendering during duels. Upload before world
        // rendering instead, so decoded cards still reach the GPU with a hidden HUD.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context->drain());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->cancelAll());
    }
    static Ticket request(ResourceManager resources,Identifier source,Identifier fallback,boolean hd,int maxHeight,
                          Identifier texture,Consumer<Art> loaded,Runnable failed){
        if(!SLOTS.tryAcquire())return null;
        var ticket=new Ticket();ACTIVE.add(ticket);
        WORKERS.execute(()->{
            NativeImage image=null;boolean high=hd;
            try{
                if(!ticket.cancelled){
                    try{image=decode(resources,source,maxHeight);}catch(Exception e){
                        if(source.equals(fallback))throw e;image=decode(resources,fallback,maxHeight);high=false;
                    }
                }
            }catch(Exception e){/* The main-thread caller shows a placeholder, never a frame-blocking retry. */}
            synchronized(ticket){
                if(ticket.cancelled){if(image!=null)image.close();release(ticket);}
                else READY.add(new Ready(ticket,texture,image,high,loaded,failed));
            }
        });
        return ticket;
    }
    private static NativeImage decode(ResourceManager resources,Identifier source,int maxHeight)throws Exception{
        NativeImage original;
        try(var stream=resources.open(source)){original=CardImageDecoder.read(stream);}
        if(maxHeight<=0||original.getHeight()<=maxHeight)return original;
        NativeImage scaled=null;
        try{
            scaled=new NativeImage(Math.max(1,(int)Math.round(original.getWidth()*(double)maxHeight/original.getHeight())),maxHeight,false);
            original.resizeSubRectTo(0,0,original.getWidth(),original.getHeight(),scaled);return scaled;
        }catch(RuntimeException|Error e){if(scaled!=null)scaled.close();throw e;}finally{original.close();}
    }
    static void drain(){
        for(int uploads=0;uploads<2;){
            var ready=READY.poll();if(ready==null)return;
            NativeImage image=ready.image();NativeImageBackedTexture texture=null;
            try{
                if(ready.ticket().cancelled)continue;
                if(image==null){ready.failed().run();continue;}
                int width=image.getWidth(),height=image.getHeight();texture=new NativeImageBackedTexture(image);image=null;
                texture.setFilter(false,false);MinecraftClient.getInstance().getTextureManager().registerTexture(ready.texture(),texture);texture=null;uploads++;
                try{ready.loaded().accept(new Art(ready.texture(),width,height,ready.hd()));}
                catch(RuntimeException e){MinecraftClient.getInstance().getTextureManager().destroyTexture(ready.texture());ready.failed().run();}
            }catch(RuntimeException e){ready.failed().run();}
            finally{if(image!=null)image.close();if(texture!=null)texture.close();release(ready.ticket());}
        }
    }
    private static void release(Ticket ticket){if(ACTIVE.remove(ticket))SLOTS.release();}
    static void cancelAll(){ACTIVE.forEach(Ticket::cancel);Ready ready;while((ready=READY.poll())!=null){if(ready.image()!=null)ready.image().close();release(ready.ticket());}}
}
