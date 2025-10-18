package com.devmaster.reality_spawner.misc;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.gen.feature.template.*;

import javax.annotation.Nullable;

public class ContainmentProcessor extends StructureProcessor {

    private final BlockPos start;
    private final BlockPos end;

    public ContainmentProcessor(BlockPos start, BlockPos end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public Template.BlockInfo process(
            IWorldReader world,
            BlockPos pos,
            BlockPos templateOrigin,
            Template.BlockInfo original,
            Template.BlockInfo current,
            PlacementSettings settings,
            @Nullable Template template) {

        BlockPos worldPos = current.pos;

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

    @Override
    protected IStructureProcessorType<?> getType() {
        return IStructureProcessorType.BLOCK_IGNORE;
    }
}

