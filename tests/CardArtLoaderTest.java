package dev.hua.ptcglive.client;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real worker queue and cancellation test, no GL context or Minecraft window. */
public final class CardArtLoaderTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static Object field(String name)throws Exception{var f=CardArtLoader.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    public static void main(String[] args)throws Exception{
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var worker=new AtomicReference<String>();
        var resources=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(proxy,method,values)->{
            if(method.getName().equals("open")){worker.set(Thread.currentThread().getName());started.countDown();release.await(5,TimeUnit.SECONDS);throw new IOException("fixture missing image");}
            throw new UnsupportedOperationException(method.getName());
        });
        var id=Identifier.of("ptcglive","test");var callbacks=new AtomicInteger();
        for(int i=0;i<16;i++)check(CardArtLoader.request(resources,id,id,false,64,id,a->callbacks.incrementAndGet(),callbacks::incrementAndGet)!=null,"Queue rejected below capacity");
        check(CardArtLoader.request(resources,id,id,false,64,id,a->{},()->{})==null,"Unbounded decode queue");
        check(started.await(5,TimeUnit.SECONDS)&&worker.get().startsWith("ptcg-card-decode"),"Decode on caller thread");
        CardArtLoader.cancelAll();release.countDown();
        var slots=(Semaphore)field("SLOTS");long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(slots.availablePermits()!=16&&System.nanoTime()<deadline){CardArtLoader.drain();Thread.sleep(10);}
        check(slots.availablePermits()==16&&callbacks.get()==0,"Cancelled/closed views leaked work or received stale callbacks");
        CardArtLoader.request(resources,id,id,false,64,id,a->{throw new AssertionError("Corrupt image loaded");},callbacks::incrementAndGet);
        var ready=(java.util.Queue<?>)field("READY");deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(ready.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(10);
        check(!ready.isEmpty()&&callbacks.get()==0,"Completion ran on worker");CardArtLoader.drain();
        check(callbacks.get()==1&&slots.availablePermits()==16,"Failure completion leaked queue slot");
        System.out.println("CardArtLoaderTest passed: bounded 16 jobs, off-thread reads, cancellation/reload, render-thread completion, failure recovery");
    }
}
