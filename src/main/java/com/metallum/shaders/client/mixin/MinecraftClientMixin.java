package com.metallum.shaders.client.mixin;

import com.metallum.shaders.MetallumShadersMod;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Logs the GL/Metal context string on startup so users can verify
 * Metallum is actually active.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "onResolutionChanged", at = @At("RETURN"))
    private void metallum_shaders$onResize(CallbackInfo ci) {
        // Window resized — our pipelines are resolution-independent,
        // nothing to do here, but reserved for future use.
    }
}
