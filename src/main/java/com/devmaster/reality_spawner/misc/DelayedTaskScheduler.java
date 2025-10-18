package com.devmaster.reality_spawner.misc;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DelayedTaskScheduler {
    private static final Map<MinecraftServer, List<ScheduledTask>> tasks = new HashMap<>();

    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        tasks.computeIfAbsent(server, s -> new ArrayList<>())
             .add(new ScheduledTask(server.getTickCounter() + delayTicks, action));
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = LogicalSidedProvider.INSTANCE.get(LogicalSide.SERVER);

        if (tasks.containsKey(server)) {
            int currentTick = server.getTickCounter();
            List<ScheduledTask> taskList = tasks.get(server);
            taskList.removeIf(task -> {
                if (currentTick >= task.runTick) {
                    task.action.run();
                    return true;
                }
                return false;
            });
        }
    }

    private static class ScheduledTask {
        final int runTick;
        final Runnable action;
        ScheduledTask(int runTick, Runnable action) {
            this.runTick = runTick;
            this.action = action;
        }
    }
}