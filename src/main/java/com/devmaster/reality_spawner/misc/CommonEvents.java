package com.devmaster.reality_spawner.misc;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "reality_spawner", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        DelayedTaskScheduler.onServerTick(event);
    }
}
