package com.devmaster.reality_spawner.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemUseContext;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class RandomDataChip extends Item {
    private static final Random RAND = new Random();

    public RandomDataChip() {
        super(new Item.Properties().group(ItemGroup.MISC));
    }

    public String getRandomStructure(ServerWorld world) {
        IResourceManager manager = world.getServer()
                .getDataPackRegistries()
                .getResourceManager();

        // Find all NBTs under structures/ that end with _reality.nbt
        Set<String> valid = manager.getAllResourceLocations("structures", path -> path.endsWith("_reality.nbt"))
                .stream()
                .map(ResourceLocation::getPath)
                .map(p -> p.replace("structures/", "").replace(".nbt", ""))
                .collect(Collectors.toSet());

        if (valid.isEmpty()) {
            return null;
        }

        // Randomly pick one
        int index = RAND.nextInt(valid.size());
        return valid.stream().skip(index).findFirst().orElse(null);
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        if (!context.getWorld().isRemote) {
            ServerWorld serverWorld = (ServerWorld) context.getWorld();
            String chosen = getRandomStructure(serverWorld);

            if (chosen != null) {
                context.getPlayer().sendStatusMessage(
                        new StringTextComponent("Random Chip selected: " + chosen), true);
            } else {
                context.getPlayer().sendStatusMessage(
                        new StringTextComponent("No structures ending in _reality found!"), true);
            }
        }
        return ActionResultType.SUCCESS;
    }
}