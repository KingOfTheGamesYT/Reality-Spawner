package com.devmaster.reality_spawner.client.render;

import com.devmaster.reality_spawner.blocks.RealitySpawner;
import com.devmaster.reality_spawner.misc.RegistryHandler;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PreviewRenderer {

    private static final Set<BlockPos> previewPositions = new HashSet<>();
    private static final Set<BlockPos> interiorErrors = new HashSet<>();
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
    public static void onRenderWorld(RenderLevelStageEvent event) {
        // Only draw after solid blocks & entities (use AFTER_ENTITIES stage)
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Auto-expire after 5 minutes
        if (System.currentTimeMillis() - activationTime > 300_000L) {
            clearPreview();
            return;
        }

        // Clear preview if spawner removed
        if (spawnerPos != null && mc.level.isEmptyBlock(spawnerPos)) {
            clearPreview();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;

        // --- Draw containment bubble outline ---
        for (BlockPos pos : previewPositions) {
            BlockState state = mc.level.getBlockState(pos);
            float r, g, b;

            if (pos.equals(spawnerPos) || state.getBlock() instanceof RealitySpawner) {
                r = 0f; g = 1f; b = 0f; // green
            } else if (state.getBlock() == RegistryHandler.CONTAINMENT_BLOCK.get()
                    || state.getBlock() == RegistryHandler.CONTAINMENT_GLASS.get()) {
                r = 0f; g = 1f; b = 0f; // green
            } else {
                r = 1f; g = 0f; b = 0f; // red
            }

            LevelRenderer.renderLineBox(
                    poseStack,
                    buffer.getBuffer(RenderType.lines()),
                    pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ,
                    pos.getX() + 1 - camX, pos.getY() + 1 - camY, pos.getZ() + 1 - camZ,
                    r, g, b, 0.8f
            );
        }

        // --- Draw interior errors (bright red) ---
        for (BlockPos pos : interiorErrors) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    buffer.getBuffer(RenderType.lines()),
                    pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ,
                    pos.getX() + 1 - camX, pos.getY() + 1 - camY, pos.getZ() + 1 - camZ,
                    1f, 0f, 0f, 1f
            );
        }

        buffer.endBatch(RenderType.lines());
    }
}