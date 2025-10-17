package com.devmaster.reality_spawner.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.text.StringTextComponent;

public class DataChip extends Item {
    private final String structureName;

    public DataChip(String structureName) {
        super(new Item.Properties().group(ItemGroup.MISC));
        this.structureName = structureName;
    }

    public String getStructureName() {
        return structureName;
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        if (!context.getWorld().isRemote) {
            context.getPlayer().sendStatusMessage(
                    new StringTextComponent("Inserted Data Chip: " + structureName), true);
        }
        return ActionResultType.SUCCESS;
    }
}
