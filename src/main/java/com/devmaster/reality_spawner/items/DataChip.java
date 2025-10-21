package com.devmaster.reality_spawner.items;


import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class DataChip extends Item {
    private final String structureName;

    public DataChip(String structureName) {
        super(new Item.Properties());
        this.structureName = structureName;
    }

    public String getStructureName() {
        return structureName;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            context.getPlayer().sendSystemMessage(
                    Component.literal("Inserted Data Chip: " + structureName));
        }
        return InteractionResult.SUCCESS;
    }
}
