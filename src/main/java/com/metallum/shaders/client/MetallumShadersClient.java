package com.metallum.shaders.client;

import com.metallum.shaders.MetallumShadersMod;
import com.metallum.shaders.ShaderConfig;
import com.metallum.shaders.compat.SodiumCompat;
import com.metallum.shaders.metal.MetalBridge;
import com.metallum.shaders.jni.NativeLoader;
import com.metallum.shaders.render.ShaderRenderer;
import com.metallum.shaders.shader.ShaderManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point. Wires up:
 * <ul>
 *   <li>native library load,</li>
 *   <li>Metallum bridge probe,</li>
 *   <li>shader pipeline compilation,</li>
 *   <li>keybindings (F6 toggle, F7 reload, F8 config),</li>
 *   <li>Sodium compatibility probe.</li>
 * </ul>
 */
public final class MetallumShadersClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MetallumShaders/Client");

    public static KeyBinding toggleKey;
    public static KeyBinding reloadKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] Client init starting", MetallumShadersMod.MOD_NAME);

        // 1. Detect Sodium (informational only — we don't conflict)
        SodiumCompat.isLoaded();

        // 2. Load the native shim (no-op on non-macOS)
        NativeLoader.ensureLoaded();

        // 3. Probe Metallum
        if (!MetalBridge.isAvailable()) {
            LOGGER.warn("Metallum not detected. The mod will be inactive until Metallum is installed.");
        } else {
            // 4. Compile shaders
            ShaderManager.init();
        }

        // 5. Keybindings
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.metallum_shaders.toggle", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6, "category.metallum_shaders"));
        reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.metallum_shaders.reload", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7, "category.metallum_shaders"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                ShaderConfig.INSTANCE.enabled = !ShaderConfig.INSTANCE.enabled;
                ShaderConfig.save();
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(
                            "Metallum Shaders: " + (ShaderConfig.INSTANCE.enabled ? "ON" : "OFF")), true);
                }
            }
            while (reloadKey.wasPressed()) {
                ShaderManager.reload();
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(
                            "Metallum Shaders reloaded"), true);
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(ShaderRenderer::shutdown));
    }
}
