package com.devmaster.reality_spawner.client.render;

import com.devmaster.reality_spawner.blocks.RealitySpawner;
import com.devmaster.reality_spawner.misc.RegistryHandler;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PreviewRenderer {

    private static Set<BlockPos> previewPositions = new HashSet<>();
    private static Set<BlockPos> interiorErrors = new HashSet<>();
    private static BlockPos spawnerPos = null;
    private static long activationTime = 0L;

    public static void setPreview(BlockPos spawner, Set<BlockPos> positions) {
        previewPositions.clear();
        previewPositions.addAll(positions);
        spawnerPos = spawner;
        activationTime = System.currentTimeMillis();
    }

    public static void clearPreview() {
        previewPositions.clear();
        interiorErrors.clear();
        spawnerPos = null;
        activationTime = 0L;
    }

    public static boolean isPreviewActiveAt(BlockPos pos) {
        return spawnerPos != null && spawnerPos.equals(pos);
    }

    public static void setInteriorErrors(Set<BlockPos> errors) {
        interiorErrors.clear();
        interiorErrors.addAll(errors);
    }

    public static void clearInteriorErrors() {
        interiorErrors.clear();
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        if (previewPositions.isEmpty() && interiorErrors.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null || mc.player == null) return;

        // Auto-expire after 5 minutes
        if (System.currentTimeMillis() - activationTime > 300_000) {
            clearPreview();
            return;
        }

        // Clear preview if spawner removed
        if (spawnerPos != null && mc.world.isAirBlock(spawnerPos)) {
            clearPreview();
            return;
        }

        MatrixStack matrixStack = event.getMatrixStack();
        IRenderTypeBuffer.Impl buffer = mc.getRenderTypeBuffers().getBufferSource();

        double camX = mc.getRenderManager().info.getProjectedView().x;
        double camY = mc.getRenderManager().info.getProjectedView().y;
        double camZ = mc.getRenderManager().info.getProjectedView().z;

        // --- Draw bubble outline ---
        for (BlockPos pos : previewPositions) {
            BlockState state = mc.world.getBlockState(pos);
            float r, g, b;

            // ✅ RealitySpawner block should be considered valid
            if (pos.equals(spawnerPos) || state.getBlock() instanceof RealitySpawner) {
                r = 0f; g = 1f; b = 0f; // green
            }
            // ✅ Containment blocks
            else if (state.getBlock() == RegistryHandler.CONTAINMENT_BLOCK.get() || state.getBlock() == RegistryHandler.CONTAINMENT_GLASS.get()) {
                r = 0f; g = 1f; b = 0f; // green
            }
            // ❌ Missing/incorrect blocks
            else {
                r = 1f; g = 0f; b = 0f; // red
            }

            WorldRenderer.drawBoundingBox(matrixStack,
                    buffer.getBuffer(RenderType.getLines()),
                    pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ,
                    pos.getX() + 1 - camX, pos.getY() + 1 - camY, pos.getZ() + 1 - camZ,
                    r, g, b, 0.8f);
        }

        // --- Draw interior errors (bright red) ---
        for (BlockPos pos : interiorErrors) {
            WorldRenderer.drawBoundingBox(matrixStack,
                    buffer.getBuffer(RenderType.getLines()),
                    pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ,
                    pos.getX() + 1 - camX, pos.getY() + 1 - camY, pos.getZ() + 1 - camZ,
                    1f, 0f, 0f, 1.0f);
        }

        buffer.finish();
    }
}

