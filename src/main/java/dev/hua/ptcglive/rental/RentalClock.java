package dev.hua.ptcglive.rental;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** One world-wide active clock; independent of /time, daylight, loaded chunks and offline wall time. */
public final class RentalClock extends PersistentState {
    public static final Type<RentalClock> TYPE = new Type<>(RentalClock::new, RentalClock::read, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private long millis;
    private long lastNanos=System.nanoTime(), remainderNanos;
    public static RentalClock get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE,"ptcg_rental_clock");
    }
    public long now() { return millis; }
    public void tick(boolean paused) {
        long now=System.nanoTime(),delta=Math.max(0,now-lastNanos);lastNanos=now;
        if(paused)return;
        long elapsed=delta+remainderNanos;remainderNanos=elapsed%1_000_000;
        advance(elapsed/1_000_000);
    }
    public void advance(long elapsedMillis) {
        if(elapsedMillis<0)throw new IllegalArgumentException("时间不能倒退");
        millis=Math.addExact(millis,elapsedMillis);if(elapsedMillis>0)markDirty();
    }
    private static RentalClock read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        var state = new RentalClock(); state.millis = Math.max(0,nbt.getLong("activeMillis")); return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putLong("activeMillis",millis); return nbt;
    }
}
