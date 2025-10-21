package com.devmaster.reality_spawner.misc;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;

public class ContainmentProcessor extends StructureProcessor {

    private final BlockPos start;
    private final BlockPos end;

    public ContainmentProcessor(BlockPos start, BlockPos end) {
        this.start = start;
        this.end = end;
    }

    @Nullable
    public StructureTemplate.StructureBlockInfo process(
            LevelReader levelReader,
            BlockPos pos,
            BlockPos templateOrigin,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings) {

        BlockPos worldPos = current.pos();

        // Only place blocks within the interior bounds
        if (worldPos.getX() < start.getX() || worldPos.getX() > end.getX() ||
                worldPos.getY() < start.getY() || worldPos.getY() > end.getY() ||
                worldPos.getZ() < start.getZ() || worldPos.getZ() > end.getZ()) {
            // Skip placement entirely (preserves containment walls)
            return null;
        }

        // Otherwise, allow this block to be placed normally
        return current;
    }

    public static final Codec<ContainmentProcessor> CODEC = Codec.unit(() -> new ContainmentProcessor(BlockPos.ZERO, BlockPos.ZERO));

    @Override
    protected StructureProcessorType<?> getType() {
        // Replace with your registered processor type (you must register this!)
        return RegistryHandler.CONTAINMENT_PROCESSOR.get();
    }
}