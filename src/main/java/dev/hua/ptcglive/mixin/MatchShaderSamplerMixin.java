package dev.hua.ptcglive.mixin;

import dev.hua.ptcglive.client.MatchDisplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/** Reuse vanilla's linked sampler locations while a duel owns the screen. */
@Mixin(ShaderProgram.class)
abstract class MatchShaderSamplerMixin {
    @Shadow @Final private List<String> samplerNames;
    @Shadow @Final private List<Integer> loadedSamplerIds;
    @Shadow @Final private int glRef;

    @Redirect(method="bind",at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/GlUniform;getUniformLocation(ILjava/lang/CharSequence;)I"),require=0)
    private int ptcglive$linkedSampler(int program,CharSequence name) {
        if(program==glRef&&MinecraftClient.getInstance().currentScreen instanceof MatchDisplay){
            int index=samplerNames.indexOf(name);
            if(index>=0&&index<loadedSamplerIds.size())return loadedSamplerIds.get(index);
        }
        // Preserve vanilla behavior outside battles and for unfamiliar bindings.
        return GlUniform.getUniformLocation(program,name);
    }
}
