package com.devmaster.reality_spawner.misc;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("reality_spawner")
public class Reality_Spawner {
    public static final Logger LOGGER = LogManager.getLogger("Reality Spawner");
    public static final String MOD_ID = "reality_spawner";

    public Reality_Spawner() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(this);
        RegistryHandler.init();
    }

    private void setup(final FMLCommonSetupEvent event) {

    }
    private void doClientStuff(final FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(RegistryHandler.CONTAINMENT_GLASS.get(), RenderType.cutout());

    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(RegistryHandler.CONTAINMENT_GLASS);
            event.accept(RegistryHandler.CONTAINMENT_BLOCK);
            event.accept(RegistryHandler.REALITY_SPAWNER);
        }
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(RegistryHandler.RANDOM_CHIP);
        }
    }
}