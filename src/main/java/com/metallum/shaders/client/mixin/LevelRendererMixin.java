package com.metallum.shaders.client.mixin;

import com.metallum.shaders.render.ShaderRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code LevelRenderer#renderLevel} 返回前注入，运行我们的后处理链。
 *
 * <p>用 {@code @At("RETURN")} 而不是引用具体方法（如 renderWeather），
 * 因为 MC 26.2 的方法签名可能变化，RETURN 更稳妥。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void metallum_shaders$afterWorldRender(
            net.minecraft.client.util.math.MatrixStack matrices,
            float tickDelta,
            long limitTime,
            boolean renderBlockOutline,
            Camera camera,
            org.joml.Matrix4f positionMatrix,
            org.joml.Matrix4f projectionMatrix,
            CallbackInfo ci) {
        // 运行延迟光照 + 后处理链。如果禁用或 Metallum 缺失则为 no-op。
        ShaderRenderer.render(camera, tickDelta);
    }
}
