package dev.hua.ptcglive.mixin;

import dev.hua.ptcglive.RegistryAliases;
import java.util.Optional;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Registry codecs (item stacks, villager data) decode IDs through
 * {@code getEntry(Identifier)}.  When that lookup misses, a retired
 * {@code ptcglive:} ID is resolved to its replacement; every other lookup is untouched.
 */
@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryAliasMixin<T> {
    @Shadow public abstract RegistryKey<? extends Registry<T>> getKey();
    @Shadow public abstract Optional<RegistryEntry.Reference<T>> getEntry(Identifier id);

    @Inject(method = "getEntry(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void ptcglive$resolveLegacyId(Identifier id, CallbackInfoReturnable<Optional<RegistryEntry.Reference<T>>> result) {
        if (result.getReturnValue().isPresent()) return;
        Identifier replacement = RegistryAliases.resolve(getKey(), id);
        if (replacement != null) result.setReturnValue(getEntry(replacement));
    }
}
