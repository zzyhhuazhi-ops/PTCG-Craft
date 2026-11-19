package dev.hua.ptcglive.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.*;

/** Screen-local playback: no packets, gameplay timers or shared sound cancellation. */
final class BattleAudio {
    /** Read once when a confirmed timeline is queued; no device settings change. */
    static long outputLatencyNanos() {
        try {
            long context=org.lwjgl.openal.ALC10.alcGetCurrentContext();
            if(context==0)return 0;
            long device=org.lwjgl.openal.ALC10.alcGetContextsDevice(context);
            if(device==0||!org.lwjgl.openal.ALC10.alcIsExtensionPresent(device,"ALC_SOFT_device_clock"))return 0;
            long latency=org.lwjgl.openal.SOFTDeviceClock.alcGetInteger64vSOFT(device,org.lwjgl.openal.SOFTDeviceClock.ALC_DEVICE_LATENCY_SOFT);
            return latency>=0&&latency<=1_000_000_000L?latency:0;
        } catch(RuntimeException|LinkageError unavailable) { return 0; }
    }
    private record Pending(String name,long at,float volume) { }
    private final List<Pending> pending=new ArrayList<>();
    private final List<PositionedSoundInstance> playing=new ArrayList<>();
    void enqueue(String name,long at,float volume) {
        if(name==null)return;
        // A bench-wide effect uses one sound per cue type at the same instant.
        if(pending.stream().anyMatch(p->p.name().equals(name)&&p.at()==at))return;
        if(pending.size()<256)pending.add(new Pending(name,at,volume));
    }
    void tick(long now) {
        var manager=MinecraftClient.getInstance().getSoundManager();
        playing.removeIf(s->!manager.isPlaying(s));
        int started=0;
        for(var iterator=pending.iterator();iterator.hasNext();) {
            var p=iterator.next();if(p.at()>now)continue;
            iterator.remove();
            // Do not burst stale audio after window suspension or a long frame.
            if(now-p.at()>350_000_000L||started>=6)continue;
            var sound=PositionedSoundInstance.master(SoundEvent.of(Identifier.of("ptcglive_mc","battle."+p.name())),1f,p.volume());
            manager.play(sound);playing.add(sound);started++;
        }
    }
    void clear() {
        pending.clear();var manager=MinecraftClient.getInstance().getSoundManager();
        playing.forEach(manager::stop);playing.clear();
    }
}
