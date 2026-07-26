package com.metallum.shaders.client.mixin;

import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables vanilla's {@code PostEffectProcessor} when our shaders are
 * active, so vanilla's {@code creeper.json} / {@code spider.json} etc.
 * overlays don't fight our composite pass for the framebuffer.
 *
 * <p>When the user toggles our shaders off (F6), vanilla post effects
 * resume normally.
 */
@Mixin(PostEffectProcessor.class)
public abstract class PostChainMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void metallum_shaders$suppressVanillaPost(float tickDelta, CallbackInfo ci) {
        if (com.metallum.shaders.ShaderConfig.INSTANCE.enabled
                && com.metallum.shaders.shader.ShaderManager.isAvailable()) {
            ci.cancel();
        }
    }
}
