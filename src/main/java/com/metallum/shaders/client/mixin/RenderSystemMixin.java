package com.metallum.shaders.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Metallum replaces the GL pipeline with Metal; this mixin is a no-op
 * stub that exists only so we can intercept any future GL state leak
 * (e.g. a Sodium pass that flips depth-write back on) and reset it
 * before our fullscreen-triangle draw.
 *
 * <p>Currently a placeholder for forward-compatibility.
 */
@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {

    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void metallum_shaders$beforeFlip(long window, CallbackInfo ci) {
        // No-op for now; reserved for future use.
    }
}
