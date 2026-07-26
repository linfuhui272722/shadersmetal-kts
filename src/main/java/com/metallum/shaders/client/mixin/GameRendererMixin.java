package com.metallum.shaders.client.mixin;

import com.metallum.shaders.render.ShaderRenderer;
import com.metallum.shaders.shader.ShaderManager;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reloads our Metal pipelines whenever vanilla reloads its shaders
 * (F3+T, resource pack switch, etc.).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "reload", at = @At("RETURN"))
    private void metallum_shaders$onReload(CallbackInfo ci) {
        // ShaderManager.reload() is safe to call repeatedly.
        ShaderManager.reload();
    }
}
