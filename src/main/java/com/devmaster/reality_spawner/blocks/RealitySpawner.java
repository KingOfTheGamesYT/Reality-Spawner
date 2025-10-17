package com.devmaster.reality_spawner.blocks;

import com.devmaster.reality_spawner.client.render.PreviewRenderer;
import com.devmaster.reality_spawner.items.DataChip;
import com.devmaster.reality_spawner.items.RandomDataChip;

import com.devmaster.reality_spawner.misc.RegistryHandler;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.loot.LootContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraftforge.common.ToolType;

import java.util.*;

public class RealitySpawner extends Block {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RealitySpawner() {
        super(AbstractBlock.Properties
                .create(Material.IRON)
                .hardnessAndResistance(4.0f, 10000)
                .setRequiresTool()
                .harvestLevel(2)
                .sound(SoundType.METAL)
                .harvestTool(ToolType.PICKAXE));

        this.setDefaultState(this.stateContainer.getBaseState()
                .with(FACING, Direction.NORTH)
                .with(ACTIVE, false)); // default inactive

    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.getDefaultState().with(FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (world.isRemote) {
            return ActionResultType.SUCCESS;
        }

        ItemStack held = player.getHeldItem(hand);
        Direction facing = state.get(FACING);

        // Sneak + right-click toggles preview
        if (player.isSneaking() && held.isEmpty()) {
            if (PreviewRenderer.isPreviewActiveAt(pos)) {
                PreviewRenderer.clearPreview();
                player.sendStatusMessage(new StringTextComponent("Preview cleared."), true);
            } else {
                Set<BlockPos> outline = computeBubbleOutline(pos, facing);
                PreviewRenderer.setPreview(pos, outline);
                player.sendStatusMessage(new StringTextComponent("Preview activated."), true);
            }
            return ActionResultType.SUCCESS;
        }

        // Normal right-click with Data Chip
        if (held.getItem() instanceof SwordItem || held.getItem() instanceof RandomDataChip) {
            boolean valid = validateBubble(world, pos, player, facing);
            if (!valid) return ActionResultType.SUCCESS;

            String structureName = null;
            if (held.getItem() instanceof DataChip) {
                structureName = ((DataChip) held.getItem()).getStructureName();
            } else if (held.getItem() instanceof RandomDataChip) {
                structureName = ((RandomDataChip) held.getItem()).getRandomStructure((ServerWorld) world);
            }

            if (structureName == null) {
                player.sendStatusMessage(new StringTextComponent("No valid structure found for this chip!"), false);
                return ActionResultType.SUCCESS;
            }

            ServerWorld serverWorld = (ServerWorld) world;
            TemplateManager manager = serverWorld.getStructureTemplateManager();
            ResourceLocation rl = new ResourceLocation("realityspawner", structureName);
            Template template = manager.getTemplate(rl);

            if (template == null) {
                player.sendStatusMessage(new StringTextComponent("Failed to load structure: " + rl), false);
                return ActionResultType.SUCCESS;
            }

            BlockPos start = getBubbleInteriorOrigin(pos, facing);
            template.func_237144_a_( // placeInWorld
                    serverWorld,
                    start,
                    new PlacementSettings(),
                    serverWorld.rand
            );

            player.sendStatusMessage(new StringTextComponent("Reality spawned: " + structureName), false);
            held.shrink(1);

            // Set to active (on) after successful activation
            world.setBlockState(pos, state.with(ACTIVE, true), 3);

            return ActionResultType.SUCCESS;

        }

        return ActionResultType.PASS;
    }

    private Set<BlockPos> computeBubbleOutline(BlockPos spawnerPos, Direction facing) {
        Set<BlockPos> outline = new HashSet<>();
        int startX, endX, startY, endY, startZ, endZ;

        // Bottom of cube is same Y as spawner; top is +15 so height = 16
        startY = spawnerPos.getY();
        endY = spawnerPos.getY() + 15;

        // Compute bounds so the front face *includes* the spawner block
        switch (facing) {
            case NORTH:
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ() - 15; // extend 15 behind -> front face at spawner
                endZ = spawnerPos.getZ();
                break;
            case SOUTH:
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ();
                endZ = spawnerPos.getZ() + 15;
                break;
            case WEST:
                startX = spawnerPos.getX() - 15;
                endX = spawnerPos.getX();
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
                break;
            case EAST:
            default:
                startX = spawnerPos.getX();
                endX = spawnerPos.getX() + 15;
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
                break;
        }

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    boolean isEdge = (x == startX || x == endX || y == startY || y == endY || z == startZ || z == endZ);
                    if (isEdge) {
                        outline.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        return outline;
    }

    private boolean validateBubble(World world, BlockPos spawnerPos, PlayerEntity player, Direction facing) {
        int startX, endX, startY, endY, startZ, endZ;

        startY = spawnerPos.getY();
        endY = spawnerPos.getY() + 15;

        switch (facing) {
            case NORTH:
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ() - 15;
                endZ = spawnerPos.getZ();
                break;
            case SOUTH:
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ();
                endZ = spawnerPos.getZ() + 15;
                break;
            case WEST:
                startX = spawnerPos.getX() - 15;
                endX = spawnerPos.getX();
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
                break;
            case EAST:
            default:
                startX = spawnerPos.getX();
                endX = spawnerPos.getX() + 15;
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
                break;
        }

        // --- Check for multiple Reality Spawners ---
        int spawnerCount = 0;

        // Check all blocks in the cube (edges + interior) for RealitySpawner instances
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    if (block instanceof RealitySpawner) {
                        spawnerCount++;
                        // Short-circuit early if we find more than one
                        if (spawnerCount > 1) {
                            player.sendStatusMessage(
                                    new StringTextComponent("Containment frame contains multiple Reality Spawners! Remove extras."),
                                    false
                            );
                            return false;
                        }
                    }
                }
            }
        }

        // --- Validate wall structure ---
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    boolean isEdge = (x == startX || x == endX || y == startY || y == endY || z == startZ || z == endZ);
                    if (!isEdge) continue;

                    BlockPos checkPos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(checkPos);
                    Block block = state.getBlock();

                    boolean isSpawnerHere = checkPos.equals(spawnerPos);
                    boolean isContainment = (block == RegistryHandler.CONTAINMENT_BLOCK.get() || block == RegistryHandler.CONTAINMENT_GLASS.get());
                    boolean isSpawnerBlock = isSpawnerHere || block instanceof RealitySpawner;

                    if (!isContainment && !isSpawnerBlock) {
                        player.sendStatusMessage(new StringTextComponent("Containment bubble incomplete."), false);
                        return false;
                    }
                }
            }
        }

        // --- Interior check ---
        Set<BlockPos> interiorErrors = new HashSet<>();
        for (int x = startX + 1; x < endX; x++) {
            for (int y = startY + 1; y < endY; y++) {
                for (int z = startZ + 1; z < endZ; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    if (checkPos.equals(spawnerPos)) continue;
                    if (!world.isAirBlock(checkPos)) {
                        interiorErrors.add(checkPos);
                    }
                }
            }
        }

        if (!interiorErrors.isEmpty()) {
            PreviewRenderer.setInteriorErrors(interiorErrors);
            player.sendStatusMessage(new StringTextComponent("Containment area obstructed: clear interior blocks."), false);
            return false;
        } else {
            PreviewRenderer.clearInteriorErrors();
        }

        // --- Entity damage unchanged ---
        AxisAlignedBB box = new AxisAlignedBB(startX, startY, startZ, endX + 1, endY + 1, endZ + 1);
        List<LivingEntity> entities = world.getEntitiesWithinAABB(LivingEntity.class, box);
        for (LivingEntity e : entities) {
            if (!(e instanceof PlayerEntity && e.getUniqueID().equals(player.getUniqueID()))) {
                e.attackEntityFrom(DamageSource.OUT_OF_WORLD, 4.0F);
            }
        }

        return true;
    }


    private BlockPos getBubbleInteriorOrigin(BlockPos spawnerPos, Direction facing) {
        // Interior origin should line up with computeBubbleOutline / validateBubble bounds
        // This returns the position where the structure template will be placed.
        switch (facing) {
            case NORTH:
                return spawnerPos.add(-7, 0, -14); // one less because front face included
            case SOUTH:
                return spawnerPos.add(-7, 0, 1);
            case WEST:
                return spawnerPos.add(-14, 0, -7);
            case EAST:
            default:
                return spawnerPos.add(1, 0, -7);
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

}