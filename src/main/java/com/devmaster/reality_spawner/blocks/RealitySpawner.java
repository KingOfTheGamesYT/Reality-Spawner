package com.devmaster.reality_spawner.blocks;

import com.devmaster.reality_spawner.client.render.PreviewRenderer;
import com.devmaster.reality_spawner.items.DataChip;
import com.devmaster.reality_spawner.items.RandomDataChip;

import com.devmaster.reality_spawner.misc.ContainmentProcessor;
import com.devmaster.reality_spawner.misc.DelayedTaskScheduler;
import com.devmaster.reality_spawner.misc.RegistryHandler;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.loot.LootContext;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.*;
import net.minecraft.util.concurrent.TickDelayedTask;
import net.minecraft.util.math.*;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.template.BlockIgnoreStructureProcessor;
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
        return this.getDefaultState().with(FACING, context.getPlacementHorizontalFacing());
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
            ResourceLocation rl = new ResourceLocation("reality_spawner", structureName);
            Template template = manager.getTemplate(rl);

            if (template == null) {
                player.sendStatusMessage(new StringTextComponent("Failed to load structure: " + rl), false);
                return ActionResultType.SUCCESS;
            }

            BlockPos interiorStart = getBubbleInteriorOrigin(pos, facing).add(1, 0, 1);
            PlacementSettings settings = new PlacementSettings()
                    .addProcessor(new ContainmentProcessor(interiorStart, interiorStart.add(13, 14, 13)))
                    .addProcessor(BlockIgnoreStructureProcessor.AIR);

            spawnBlackHole(serverWorld, interiorStart);

            int currentTick = serverWorld.getServer().getTickCounter();

            // Phase 1–2: Gateway build-up (9 seconds)
            for (int i = 0; i < 300; i++) {
                int tickDelay = i;
                DelayedTaskScheduler.schedule(serverWorld.getServer(), tickDelay, () -> {
                    spawnBlackHole(serverWorld, getBubbleInteriorOrigin(pos, facing));
                    damageEntitiesInBubble(serverWorld, pos, facing);
                });
            }
            // Phase 3: Collapse
            DelayedTaskScheduler.schedule(serverWorld.getServer(), 320, () -> {
                collapseGateway(serverWorld, getBubbleInteriorOrigin(pos, facing));
            });

            // Finally spawn the structure after total ~13 seconds (460 ticks)
            DelayedTaskScheduler.schedule(serverWorld.getServer(), 460, () -> {
                template.func_237144_a_(serverWorld, interiorStart, settings, serverWorld.rand);
            });



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
                                    new StringTextComponent("Containment barrier contains multiple Reality Spawners! Remove extras."),
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
                        player.sendStatusMessage(new StringTextComponent("Containment Barrier incomplete."), false);
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
            player.sendStatusMessage(new StringTextComponent("Gateway unable to form: clear interior blocks."), true);
            return false;
        } else {
            PreviewRenderer.clearInteriorErrors();
        }

        return true;
    }


    private BlockPos getBubbleInteriorOrigin(BlockPos spawnerPos, Direction facing) {
        // Adjust so structure spawns 1 block up and inset from front/right walls
        switch (facing) {
            case NORTH:
                return spawnerPos.add(-8, 1, -15); // back 15, inset 1 from right/front, up 1
            case SOUTH:
                return spawnerPos.add(-7, 1, 1);   // forward-facing, inset from right/front, up 1
            case WEST:
                return spawnerPos.add(-14, 1, -8); // back 14, up 1, inset 1
            case EAST:
            default:
                return spawnerPos.add(1, 1, -7);   // inset 1 from front/right, up 1
        }
    }

    private void spawnBlackHole(ServerWorld world, BlockPos posCenter) {
        double cx = posCenter.getX() + 7.5;
        double cy = posCenter.getY() + 7.5;
        double cz = posCenter.getZ() + 7.5;

        world.playSound(null, posCenter, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 1.0f, 0.5f);
        world.playSound(null, posCenter, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.0f, 0.9f);

        for (int i = 0; i < 200; i++) {
            double angle = world.rand.nextDouble() * Math.PI * 2;
            double radius = 4.0 * world.rand.nextDouble();
            double height = (world.rand.nextDouble() - 0.5) * 5.0;

            double px = cx + Math.cos(angle) * radius;
            double py = cy + height;
            double pz = cz + Math.sin(angle) * radius;

            // Inward motion toward the center
            double mx = (cx - px) * 0.1;
            double my = (cy - py) * 0.1;
            double mz = (cz - pz) * 0.1;

            // Purple void dust + end rod = black-hole glow
            world.spawnParticle(ParticleTypes.PORTAL, px, py, pz, 0, mx, my, mz, 0.01);
            world.spawnParticle(ParticleTypes.END_ROD, px, py, pz, 0, mx * 0.5, my * 0.5, mz * 0.5, 0.005);

            if (i % 15 == 0) {
                world.spawnParticle(ParticleTypes.DRAGON_BREATH,
                        cx + (world.rand.nextGaussian() * 0.2),
                        cy + (world.rand.nextGaussian() * 0.2),
                        cz + (world.rand.nextGaussian() * 0.2),
                        1, // count
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    private void damageEntitiesInBubble(ServerWorld world, BlockPos spawnerPos, Direction facing) {
        // Compute bubble bounds (same as validateBubble)
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

        AxisAlignedBB box = new AxisAlignedBB(startX, startY, startZ, endX + 1, endY + 1, endZ + 1);
        List<LivingEntity> entities = world.getEntitiesWithinAABB(LivingEntity.class, box);
        for (LivingEntity e : entities) {
            e.attackEntityFrom(DamageSource.OUT_OF_WORLD, 4.0F);
        }
    }

    private void collapseGateway(ServerWorld world, BlockPos posCenter) {
        double cx = posCenter.getX() + 7.5;
        double cy = posCenter.getY() + 7.5;
        double cz = posCenter.getZ() + 7.5;

        world.playSound(null, posCenter, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.2f, 0.6f);
        world.playSound(null, posCenter, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 0.8f);

        // Inward particle pull + flash
        for (int i = 0; i < 300; i++) {
            double angle = world.rand.nextDouble() * Math.PI * 2;
            double radius = 6.0 * world.rand.nextDouble();
            double height = (world.rand.nextDouble() - 0.5) * 6.0;

            double px = cx + Math.cos(angle) * radius;
            double py = cy + height;
            double pz = cz + Math.sin(angle) * radius;

            double mx = (cx - px) * 0.3;
            double my = (cy - py) * 0.3;
            double mz = (cz - pz) * 0.3;

            world.spawnParticle(ParticleTypes.PORTAL, px, py, pz, 0, mx, my, mz, 0.05);
            world.spawnParticle(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 0, 0, 0, 0, 1.0);
        }
    }

}