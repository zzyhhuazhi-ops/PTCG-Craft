package dev.hua.ptcglive.mixin;

import dev.hua.ptcglive.client.CardViewScreen;
import dev.hua.ptcglive.client.MatchDisplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide only the underlying Minecraft HUD while a duel owns the screen; never change user options. */
@Mixin(InGameHud.class)
abstract class MatchHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ptcglive$clearDuelUnderlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        var screen = MinecraftClient.getInstance().currentScreen;
        if (screen instanceof MatchDisplay
            || screen instanceof CardViewScreen preview && preview.parentScreen() instanceof MatchDisplay) ci.cancel();
    }
}
