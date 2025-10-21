package com.devmaster.reality_spawner.blocks;

import com.devmaster.reality_spawner.client.render.PreviewRenderer;
import com.devmaster.reality_spawner.items.DataChip;
import com.devmaster.reality_spawner.items.RandomDataChip;
import com.devmaster.reality_spawner.misc.ContainmentProcessor;
import com.devmaster.reality_spawner.misc.DelayedTaskScheduler;
import com.devmaster.reality_spawner.misc.RegistryHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;

public class RealitySpawner extends Block {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RealitySpawner() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.METAL)
                .strength(4.0f, 10000f)
                .requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        Direction facing = state.getValue(FACING);

        // Sneak + right-click toggles preview
        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (PreviewRenderer.isPreviewActiveAt(pos)) {
                PreviewRenderer.clearPreview();
                player.displayClientMessage(Component.literal("Preview cleared."), true);
            } else {
                Set<BlockPos> outline = computeBubbleOutline(pos, facing);
                PreviewRenderer.setPreview(pos, outline);
                player.displayClientMessage(Component.literal("Preview activated."), true);
            }
            return InteractionResult.SUCCESS;
        }

        // Normal right-click with a Data Chip or RandomDataChip
        if (held.getItem() instanceof RandomDataChip || held.getItem() instanceof DataChip) {
            if (!validateBubble(level, pos, player, facing)) return InteractionResult.SUCCESS;

            String structureName = null;
            if (held.getItem() instanceof DataChip dataChip) {
                structureName = dataChip.getStructureName();
            } else if (held.getItem() instanceof RandomDataChip chip) {
                structureName = chip.getRandomStructure((ServerLevel) level);
            }

            if (structureName == null) {
                player.displayClientMessage(Component.literal("No valid structure found for this chip!"), false);
                return InteractionResult.SUCCESS;
            }

            ServerLevel serverLevel = (ServerLevel) level;
            ResourceLocation rl = new ResourceLocation("reality_spawner", structureName);
            Optional<StructureTemplate> optional = serverLevel.getStructureManager().get(rl);
            if (optional.isEmpty()) {
                player.displayClientMessage(Component.literal("Failed to load structure: " + rl), false);
                return InteractionResult.SUCCESS;
            }

            StructureTemplate template = optional.get();
            BlockPos interiorStart = getBubbleInteriorOrigin(pos, facing).offset(1, 0, 1);
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .addProcessor(new ContainmentProcessor(interiorStart, interiorStart.offset(13, 14, 13)))
                    .addProcessor(BlockIgnoreProcessor.AIR);

            spawnBlackHole(serverLevel, interiorStart);

            // Schedule build-up and collapse events
            for (int i = 0; i < 300; i++) {
                int delay = i;
                DelayedTaskScheduler.schedule(serverLevel.getServer(), delay, () -> {
                    spawnBlackHole(serverLevel, getBubbleInteriorOrigin(pos, facing));
                    damageEntitiesInBubble(serverLevel, pos, facing);
                });
            }

            DelayedTaskScheduler.schedule(serverLevel.getServer(), 320, () ->
                    collapseGateway(serverLevel, getBubbleInteriorOrigin(pos, facing)));

            DelayedTaskScheduler.schedule(serverLevel.getServer(), 460, () -> {
                template.placeInWorld(serverLevel, interiorStart, interiorStart, settings, RandomSource.create(), 2);
                serverLevel.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.0f);

                BlockState current = serverLevel.getBlockState(pos);
                if (current.getBlock() instanceof RealitySpawner && current.getValue(ACTIVE)) {
                    serverLevel.setBlock(pos, current.setValue(ACTIVE, false), 3);
                }
            });

            held.shrink(1);
            level.setBlock(pos, state.setValue(ACTIVE, true), 3);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private Set<BlockPos> computeBubbleOutline(BlockPos spawnerPos, Direction facing) {
        Set<BlockPos> outline = new HashSet<>();
        int startY = spawnerPos.getY();
        int endY = spawnerPos.getY() + 15;
        int startX, endX, startZ, endZ;

        switch (facing) {
            case NORTH -> {
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ() - 15;
                endZ = spawnerPos.getZ();
            }
            case SOUTH -> {
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ();
                endZ = spawnerPos.getZ() + 15;
            }
            case WEST -> {
                startX = spawnerPos.getX() - 15;
                endX = spawnerPos.getX();
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
            }
            default -> {
                startX = spawnerPos.getX();
                endX = spawnerPos.getX() + 15;
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
            }
        }

        for (int x = startX; x <= endX; x++)
            for (int y = startY; y <= endY; y++)
                for (int z = startZ; z <= endZ; z++)
                    if (x == startX || x == endX || y == startY || y == endY || z == startZ || z == endZ)
                        outline.add(new BlockPos(x, y, z));

        return outline;
    }

    private void spawnBlackHole(ServerLevel level, BlockPos posCenter) {
        RandomSource rand = level.getRandom();
        double cx = posCenter.getX() + 7.5;
        double cy = posCenter.getY() + 7.5;
        double cz = posCenter.getZ() + 7.5;

        level.playSound(null, posCenter, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 1.0f, 0.5f);
        level.playSound(null, posCenter, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.4f, 1.0f);

        for (int i = 0; i < 200; i++) {
            double angle = rand.nextDouble() * Math.PI * 2;
            double radius = 4.0 * rand.nextDouble();
            double height = (rand.nextDouble() - 0.5) * 5.0;
            double px = cx + Math.cos(angle) * radius;
            double py = cy + height;
            double pz = cz + Math.sin(angle) * radius;
            double mx = (cx - px) * 0.1;
            double my = (cy - py) * 0.1;
            double mz = (cz - pz) * 0.1;

            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 0, mx, my, mz, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 0, mx * 0.5, my * 0.5, mz * 0.5, 0.005);
            if (i % 15 == 0)
                level.sendParticles(ParticleTypes.DRAGON_BREATH, cx, cy, cz, 1, 0, 0, 0, 0);
        }
    }

    private void damageEntitiesInBubble(ServerLevel level, BlockPos spawnerPos, Direction facing) {
        // Compute bubble bounds (same logic as validateBubble)
        int startY = spawnerPos.getY() + 1; // interior only, exclude walls
        int endY = spawnerPos.getY() + 14;
        int startX, endX, startZ, endZ;

        switch (facing) {
            case NORTH -> {
                startX = spawnerPos.getX() - 7;
                endX = spawnerPos.getX() + 6;
                startZ = spawnerPos.getZ() - 14;
                endZ = spawnerPos.getZ() - 1;
            }
            case SOUTH -> {
                startX = spawnerPos.getX() - 7;
                endX = spawnerPos.getX() + 6;
                startZ = spawnerPos.getZ() + 1;
                endZ = spawnerPos.getZ() + 14;
            }
            case WEST -> {
                startX = spawnerPos.getX() - 14;
                endX = spawnerPos.getX() - 1;
                startZ = spawnerPos.getZ() - 7;
                endZ = spawnerPos.getZ() + 6;
            }
            default -> { // EAST
                startX = spawnerPos.getX() + 1;
                endX = spawnerPos.getX() + 14;
                startZ = spawnerPos.getZ() - 7;
                endZ = spawnerPos.getZ() + 6;
            }
        }

        // Define exact interior bounding box (inclusive)
        AABB interiorBox = new AABB(startX, startY, startZ, endX + 1, endY + 1, endZ + 1);

        // Damage only entities inside that interior volume
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, interiorBox);
        for (LivingEntity e : entities) {
            e.hurt(level.damageSources().fellOutOfWorld(), 4.0F);
        }
    }

    private void collapseGateway(ServerLevel level, BlockPos posCenter) {
        double cx = posCenter.getX() + 7.5;
        double cy = posCenter.getY() + 7.5;
        double cz = posCenter.getZ() + 7.5;

        level.playSound(null, posCenter, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2f, 0.6f);
        level.playSound(null, posCenter, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);

        for (int i = 0; i < 300; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = 6.0 * level.random.nextDouble();
            double height = (level.random.nextDouble() - 0.5) * 6.0;
            double px = cx + Math.cos(angle) * radius;
            double py = cy + height;
            double pz = cz + Math.sin(angle) * radius;
            double mx = (cx - px) * 0.3;
            double my = (cy - py) * 0.3;
            double mz = (cz - pz) * 0.3;

            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 0, mx, my, mz, 0.05);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 0, 0, 0, 0, 1.0);
        }
    }

    private boolean validateBubble(Level level, BlockPos spawnerPos, Player player, Direction facing) {
        int startY = spawnerPos.getY();
        int endY = spawnerPos.getY() + 15;
        int startX, endX, startZ, endZ;

        switch (facing) {
            case NORTH -> {
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ() - 15;
                endZ = spawnerPos.getZ();
            }
            case SOUTH -> {
                startX = spawnerPos.getX() - 8;
                endX = spawnerPos.getX() + 7;
                startZ = spawnerPos.getZ();
                endZ = spawnerPos.getZ() + 15;
            }
            case WEST -> {
                startX = spawnerPos.getX() - 15;
                endX = spawnerPos.getX();
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
            }
            default -> {
                startX = spawnerPos.getX();
                endX = spawnerPos.getX() + 15;
                startZ = spawnerPos.getZ() - 8;
                endZ = spawnerPos.getZ() + 7;
            }
        }

        // --- Check for multiple Reality Spawners ---
        int spawnerCount = 0;
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(checkPos).getBlock();
                    if (block instanceof RealitySpawner) {
                        spawnerCount++;
                        if (spawnerCount > 1) {
                            player.displayClientMessage(
                                    Component.literal("Containment barrier contains multiple Reality Spawners! Remove extras."),
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
                    BlockState state = level.getBlockState(checkPos);
                    Block block = state.getBlock();

                    boolean isSpawnerHere = checkPos.equals(spawnerPos);
                    boolean isContainment = (block == RegistryHandler.CONTAINMENT_BLOCK.get() || block == RegistryHandler.CONTAINMENT_GLASS.get());
                    boolean isSpawnerBlock = isSpawnerHere || block instanceof RealitySpawner;

                    if (!isContainment && !isSpawnerBlock) {
                        player.displayClientMessage(Component.literal("Containment Barrier incomplete."), false);
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
                    if (!level.isEmptyBlock(checkPos)) {
                        interiorErrors.add(checkPos);
                    }
                }
            }
        }

        if (!interiorErrors.isEmpty()) {
            PreviewRenderer.setInteriorErrors(interiorErrors);
            player.displayClientMessage(Component.literal("Gateway unable to form: clear interior blocks."), true);
            return false;
        } else {
            PreviewRenderer.clearInteriorErrors();
        }

        return true;
    }

    private BlockPos getBubbleInteriorOrigin(BlockPos spawnerPos, Direction facing) {
        // Adjust so structure spawns 1 block up and inset from front/right walls
        return switch (facing) {
            case NORTH -> spawnerPos.offset(-8, 1, -15);
            case SOUTH -> spawnerPos.offset(-7, 1, 1);
            case WEST -> spawnerPos.offset(-14, 1, -8);
            case EAST -> spawnerPos.offset(1, 1, -7);
            default -> spawnerPos.offset(0, 1, 0); // fallback for UP/DOWN (shouldn’t happen)
        };
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return List.of(new ItemStack(this));
    }

}