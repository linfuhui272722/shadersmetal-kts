package com.metallum.shaders.light;

import com.metallum.shaders.ShaderConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks dynamic (moving) light sources for the deferred pass.
 *
 * <p>Inspired by MakeUp-UltraFast's "held torch" lighting, but extended
 * to cover several common light-emitting items and entities. The list is
 * rebuilt every frame from the current world entity list and packed into
 * a tight {@code std::vector<Light>} layout that the Metal fragment shader
 * reads via a uniform buffer.
 *
 * <p>Layout (each light = 32 bytes, vec4-aligned):
 * <pre>
 *   struct Light {
 *     float4 positionAndRadius;   // xyz = world pos, w = radius
 *     float4 colorAndIntensity;   // rgb = color, a = intensity
 *   };
 * </pre>
 */
public final class LightSourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MetallumShaders/Light");

    public static final int LIGHT_SIZE_BYTES = 32;
    public static final int MAX_LIGHTS = 16;

    private static final List<Light> LIGHTS = new ArrayList<>(MAX_LIGHTS);

    private LightSourceManager() {}

    public static List<Light> collect() {
        LIGHTS.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return LIGHTS;
        if (!ShaderConfig.INSTANCE.movingLightSources) return LIGHTS;

        PlayerEntity player = mc.player;

        // 1. Player-held light (torch / lantern / soul torch / etc.)
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        tryAddHeldLight(player, mainHand);
        tryAddHeldLight(player, offHand);

        // 2. Other entities within ~32 blocks carrying light sources
        int scanRadius = 32;
        List<Entity> nearby = new ArrayList<>();
        mc.world.getEntities().forEach(e -> {
            if (e instanceof LivingEntity le && le != player) {
                if (e.distanceTo(player) < scanRadius) nearby.add(e);
            }
        });

        for (Entity e : nearby) {
            if (LIGHTS.size() >= MAX_LIGHTS) break;
            if (e instanceof LivingEntity le) {
                tryAddHeldLight(le, le.getMainHandStack());
                tryAddHeldLight(le, le.getOffHandStack());
            }
            // Some entities are themselves light sources
            if (LIGHTS.size() < MAX_LIGHTS) {
                Light inherent = inherentEntityLight(e);
                if (inherent != null) LIGHTS.add(inherent);
            }
        }

        return LIGHTS;
    }

    private static void tryAddHeldLight(LivingEntity holder, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (LIGHTS.size() >= MAX_LIGHTS) return;

        float radius;
        float r, g, b, intensity;
        boolean flicker;

        if (stack.isOf(Items.TORCH)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius;
            r = 1.00f; g = 0.78f; b = 0.42f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity;
            flicker = true;
        } else if (stack.isOf(Items.SOUL_TORCH)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 0.9f;
            r = 0.30f; g = 0.65f; b = 1.00f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity;
            flicker = true;
        } else if (stack.isOf(Items.LANTERN)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.1f;
            r = 1.00f; g = 0.80f; b = 0.45f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.1f;
            flicker = false;
        } else if (stack.isOf(Items.SOUL_LANTERN)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius;
            r = 0.30f; g = 0.65f; b = 1.00f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.1f;
            flicker = false;
        } else if (stack.isOf(Items.GLOWSTONE)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.2f;
            r = 0.95f; g = 0.95f; b = 0.70f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.3f;
            flicker = false;
        } else if (stack.isOf(Items.SEA_LANTERN)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.1f;
            r = 0.55f; g = 0.85f; b = 1.00f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.2f;
            flicker = false;
        } else if (stack.isOf(Items.END_ROD)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.3f;
            r = 0.95f; g = 0.95f; b = 1.00f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.4f;
            flicker = false;
        } else if (stack.isOf(Items.BLAZE_ROD)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 0.8f;
            r = 1.00f; g = 0.65f; b = 0.20f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 0.9f;
            flicker = true;
        } else if (stack.isOf(Items.REDSTONE_TORCH) || stack.isOf(Items.REDSTONE_BLOCK)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 0.6f;
            r = 1.00f; g = 0.10f; b = 0.10f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 0.7f;
            flicker = false;
        } else if (stack.isOf(Items.OCHRE_FROGLIGHT)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.1f;
            r = 0.95f; g = 0.75f; b = 0.30f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.2f;
            flicker = false;
        } else if (stack.isOf(Items.PEARLESCENT_FROGLIGHT)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.1f;
            r = 0.85f; g = 0.65f; b = 0.95f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.2f;
            flicker = false;
        } else if (stack.isOf(Items.VERDANT_FROGLIGHT)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.1f;
            r = 0.55f; g = 0.85f; b = 0.40f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.2f;
            flicker = false;
        } else if (stack.isOf(Items.CAMPFIRE) || stack.isOf(Items.SOUL_CAMPFIRE)) {
            radius = ShaderConfig.INSTANCE.heldLightRadius * 1.4f;
            boolean soul = stack.isOf(Items.SOUL_CAMPFIRE);
            r = soul ? 0.30f : 1.00f;
            g = soul ? 0.65f : 0.78f;
            b = soul ? 1.00f : 0.42f;
            intensity = ShaderConfig.INSTANCE.heldLightIntensity * 1.5f;
            flicker = true;
        } else {
            return;
        }

        if (flicker) {
            intensity *= 1.0f - ShaderConfig.INSTANCE.torchFlickerStrength
                    * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.013
                    + holder.getId() * 1.7));
        }

        Vec3d pos = holder.getEyePos();
        LIGHTS.add(new Light(
                (float) pos.x, (float) pos.y - 0.2f, (float) pos.z, radius,
                r, g, b, intensity));
    }

    private static Light inherentEntityLight(Entity e) {
        // Magma cube, blaze, glow squid, etc.
        String id = e.getType().toString();
        if (id.contains("blaze")) {
            Vec3d p = e.getPos();
            return new Light((float) p.x, (float) p.y + 0.5f, (float) p.z,
                    10f, 1.0f, 0.7f, 0.2f, 1.2f);
        }
        if (id.contains("magma_cube")) {
            Vec3d p = e.getPos();
            return new Light((float) p.x, (float) p.y, (float) p.z,
                    6f, 1.0f, 0.5f, 0.1f, 1.0f);
        }
        if (id.contains("glow_squid")) {
            Vec3d p = e.getPos();
            return new Light((float) p.x, (float) p.y, (float) p.z,
                    8f, 0.3f, 0.9f, 1.0f, 1.0f);
        }
        if (id.contains("allay")) {
            Vec3d p = e.getPos();
            return new Light((float) p.x, (float) p.y, (float) p.z,
                    5f, 0.6f, 0.9f, 1.0f, 0.8f);
        }
        return null;
    }

    /**
     * Pack the current light list into a tightly-laid-out ByteBuffer
     * suitable for upload as a Metal uniform buffer. The buffer always
     * contains exactly {@link #MAX_LIGHTS} slots (zeroed if unused),
     * preceded by an int count.
     */
    public static ByteBuffer pack() {
        List<Light> lights = collect();
        ByteBuffer buf = ByteBuffer.allocateDirect(4 + MAX_LIGHTS * LIGHT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder());
        buf.putInt(Math.min(lights.size(), MAX_LIGHTS));
        for (int i = 0; i < MAX_LIGHTS; i++) {
            if (i < lights.size()) {
                Light l = lights.get(i);
                buf.putFloat(l.x);
                buf.putFloat(l.y);
                buf.putFloat(l.z);
                buf.putFloat(l.radius);
                buf.putFloat(l.r);
                buf.putFloat(l.g);
                buf.putFloat(l.b);
                buf.putFloat(l.intensity);
            } else {
                for (int j = 0; j < 8; j++) buf.putFloat(0f);
            }
        }
        buf.flip();
        return buf;
    }

    public static final class Light {
        public final float x, y, z, radius;
        public final float r, g, b, intensity;

        public Light(float x, float y, float z, float radius,
                     float r, float g, float b, float intensity) {
            this.x = x; this.y = y; this.z = z; this.radius = radius;
            this.r = r; this.g = g; this.b = b; this.intensity = intensity;
        }
    }
}

