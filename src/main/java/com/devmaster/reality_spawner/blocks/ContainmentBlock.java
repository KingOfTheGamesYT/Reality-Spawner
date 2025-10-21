package com.devmaster.reality_spawner.blocks;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

public class ContainmentBlock extends Block {

    public ContainmentBlock() {
        super(BlockBehaviour.Properties.of()
                        .strength(20, 10000)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));}


    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return List.of(new ItemStack(this));
    }

}