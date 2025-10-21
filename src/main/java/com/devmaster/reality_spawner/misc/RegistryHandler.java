package com.devmaster.reality_spawner.misc;

import com.devmaster.reality_spawner.blocks.ContainmentBlock;
import com.devmaster.reality_spawner.blocks.ContainmentGlass;
import com.devmaster.reality_spawner.blocks.RealitySpawner;
import com.devmaster.reality_spawner.items.BlockItemBase;
import com.devmaster.reality_spawner.items.RandomDataChip;

import net.minecraft.core.registries.BuiltInRegistries;


import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RegistryHandler {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Reality_Spawner.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Reality_Spawner.MOD_ID);
    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS = DeferredRegister.create(BuiltInRegistries.STRUCTURE_PROCESSOR.key(), Reality_Spawner.MOD_ID);


    public static void init() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        STRUCTURE_PROCESSORS.register(FMLJavaModLoadingContext.get().getModEventBus());

    }

    public static final RegistryObject<Item> RANDOM_CHIP = ITEMS.register("random_chip", RandomDataChip::new);
    public static final RegistryObject<Block> REALITY_SPAWNER = BLOCKS.register("reality_spawner", RealitySpawner::new);
    public static final RegistryObject<Block> CONTAINMENT_BLOCK = BLOCKS.register("containment_block", ContainmentBlock::new);
    public static final RegistryObject<Block> CONTAINMENT_GLASS = BLOCKS.register("containment_glass", ContainmentGlass::new);

    public static final RegistryObject<Item> REALITY_SPAWNER_ITEM = ITEMS.register("reality_spawner", () -> new BlockItemBase(REALITY_SPAWNER.get()));
    public static final RegistryObject<Item> CONTAINMENT_BLOCK_ITEM = ITEMS.register("containment_block", () -> new BlockItemBase(CONTAINMENT_BLOCK.get()));
    public static final RegistryObject<Item> CONTAINMENT_GLASS_ITEM = ITEMS.register("containment_glass", () -> new BlockItemBase(CONTAINMENT_GLASS.get()));

    public static final RegistryObject<StructureProcessorType<ContainmentProcessor>> CONTAINMENT_PROCESSOR =
            STRUCTURE_PROCESSORS.register("containment_processor",
                    () -> () -> ContainmentProcessor.CODEC);

}