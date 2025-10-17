package com.devmaster.reality_spawner.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;

import net.minecraftforge.common.ToolType;

import java.util.Collections;
import java.util.List;

public class ContainmentBlock extends Block {

    public ContainmentBlock() {
        super(Properties.create(Material.IRON)
                        .hardnessAndResistance(20, 10000)
                        .setRequiresTool()
                        .harvestLevel(2)
                        .notSolid()
                        .sound(SoundType.METAL)
                        .harvestTool(ToolType.PICKAXE));
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

}