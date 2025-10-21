package com.devmaster.reality_spawner.misc;

import net.minecraft.server.MinecraftServer;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber
public class DelayedTaskScheduler {

    private static final Map<MinecraftServer, List<ScheduledTask>> tasks = new HashMap<>();
    private static final Map<MinecraftServer, Long> tickCounters = new HashMap<>();

    /**
     * Schedule a runnable to execute after a given number of ticks.
     *
     * @param server The server instance
     * @param delayTicks Delay time in ticks (20 = 1 second)
     * @param action Code to run later
     */
    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        long runTick = tickCounters.getOrDefault(server, 0L) + delayTicks;
        tasks.computeIfAbsent(server, s -> new ArrayList<>())
                .add(new ScheduledTask(runTick, action));
    }

    /**
     * Forge event hook called every tick.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long currentTick = tickCounters.getOrDefault(server, 0L) + 1;
        tickCounters.put(server, currentTick);

        List<ScheduledTask> taskList = tasks.get(server);
        if (taskList == null || taskList.isEmpty()) return;

        // Run tasks due this tick
        taskList.removeIf(task -> {
            if (currentTick >= task.runTick) {
                try {
                    task.action.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }
            return false;
        });
    }

    private static class ScheduledTask {
        final long runTick;
        final Runnable action;

        ScheduledTask(long runTick, Runnable action) {
            this.runTick = runTick;
            this.action = action;
        }
    }
}