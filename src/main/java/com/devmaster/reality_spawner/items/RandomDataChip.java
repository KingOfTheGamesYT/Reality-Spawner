package com.devmaster.reality_spawner.items;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class RandomDataChip extends Item {
    private static final Random RAND = new Random();

    public RandomDataChip() {
        super(new Item.Properties());
    }

    /**
     * Picks a random structure from datapack resources ending in "_reality.nbt"
     */
    public String getRandomStructure(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ResourceManager manager = server.getResourceManager();

        Set<String> valid = manager.listResources("structures",
                        path -> path.getPath().endsWith("_reality.nbt"))
                .keySet()
                .stream()
                .map(ResourceLocation::getPath)
                .map(p -> p.replace("structures/", "").replace(".nbt", ""))
                .collect(Collectors.toSet());

        if (valid.isEmpty()) {
            return null;
        }

        int index = RAND.nextInt(valid.size());
        return valid.stream().skip(index).findFirst().orElse(null);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) context.getLevel();
            String chosen = getRandomStructure(serverLevel);

            if (chosen != null) {
                context.getPlayer().sendSystemMessage(
                        Component.literal("Random Chip selected: " + chosen)
                );
            } else {
                context.getPlayer().sendSystemMessage(
                        Component.literal("No structures ending in _reality found!")
                );
            }
        }
        return InteractionResult.SUCCESS;
    }
}