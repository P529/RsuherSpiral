package me.wasteofti.Modules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventQuit;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NullSetting;
import org.rusherhack.core.setting.NumberSetting;

import java.util.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;

public class BasefinderModule extends ToggleableModule {

    private final NumberSetting<Integer>chunkDistanceSetting = new NumberSetting<>("Chunk Distance", 7, 0, 20)
            .incremental(1);

    private final NumberSetting<Integer> startStepSetting = new NumberSetting<>("Start Step", 0, 0, 20)
            .incremental(1);

    private final NullSetting startPositionSetting = new NullSetting("Start Position");

    private static final NumberSetting<Integer> startX = new NumberSetting<>("Start X", 0, -29999999, 29999999)
            .incremental(1);

    private static final NumberSetting<Integer> startZ = new NumberSetting<>("Start Z", 0, -29999999, 29999999)
            .incremental(1);

    private final NumberSetting<Integer> storageAmount = new NumberSetting<>("Chest amount", 25, 0, 500)
            .incremental(1);

    private final BooleanSetting saveStepSetting = new BooleanSetting("Save Step", "Saves the current step upon disable.", true);

    int step;
    private final Direction[] directions = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private ChunkPos targetChunk;
    private ChunkPos lastExceedingChunkPos = null;

    public BasefinderModule() {
        super("BasefinderSpiral", "Module that spirals and area and saves n number of chest locations to a file.", ModuleCategory.WORLD);

        this.startPositionSetting.addSubSettings(startX, startZ);

        this.registerSettings(
                this.chunkDistanceSetting,
                this.startStepSetting,
                this.saveStepSetting,
                this.startPositionSetting,
                this.storageAmount
        );
    }

    @Override
    public void onEnable() {
        if (mc.level == null || mc.player == null) return;
        setStartPos();

        step = 0;

        for (int i = 0; i < startStepSetting.getValue(); i++) {
            targetChunk = nextTarget();
            step++;
        }

    }

    private void setStartPos() {
        if (!startPosZero()) {
            assert mc.level != null;
            this.targetChunk = mc.level.getChunk(startX.getValue(), startZ.getValue()).getPos();
        } else {
            assert mc.player != null;
            this.targetChunk = mc.player.chunkPosition();
            startX.setValue(mc.player.getX());
            startZ.setValue(mc.player.getZ());
        }
    }

    private static boolean startPosZero() {
        return startX.getValue() == 0 && startZ.getValue() == 0;
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) return;

        if (targetChunk == null) {
            setStartPos();
        }

        rotateTo(
            mc.player.getEyePosition(),
            targetChunk.getMiddleBlockPosition(0).getCenter()
        );

        ChunkPos currentPos = mc.player.chunkPosition();
        if (currentPos.x == targetChunk.x && currentPos.z == targetChunk.z) {
            RusherHackAPI.getNotificationManager().info(String.format("Reached chunk at %s after iteration %d", currentPos.toString(), step));
            targetChunk = nextTarget();
            step++;
            RusherHackAPI.getNotificationManager().info(String.format("Next chunk at %s", nextTarget().toString()));
        }

        if (checkStorageContainersInRenderDistance()) {
            RusherHackAPI.getNotificationManager().info(String.format("More than %d storage containers found in render distance!", storageAmount.getValue()));
        }
    }

    public void logStashToFile(int x, int y, int z) {
        Path configPath = RusherHackAPI.getConfigPath();

        File basefinderPluginFolder = new File(String.valueOf(configPath), "BasefinderPlugin");

        if (!basefinderPluginFolder.exists()) {
            basefinderPluginFolder.mkdir();
        }

        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File coordinatesFile = new File(basefinderPluginFolder, date + ".txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(coordinatesFile, true))) {
            writer.write("Coordinates: X=" + x + ", Y=" + y + ", Z=" + z);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
        }
    }

    @Override
    public void onDisable() {
        closingActions();
    }

    @Subscribe
    public void onQuit(EventQuit event) {
        closingActions();
    }

    private void closingActions() {
        if (saveStepSetting.getValue()) {
            startStepSetting.setValue(step);
        }
    }

    private ChunkPos nextTarget() {
        int index = step % directions.length;
        Vec3i directionVec = directions[index].getNormal();
        int nextDistance = (step + 1) * chunkDistanceSetting.getValue();
        int nextX = targetChunk.x + directionVec.getX() * nextDistance;
        int nextZ = targetChunk.z + directionVec.getZ() * nextDistance;
        return new ChunkPos(nextX, nextZ);
    }

    private void rotateTo(Vec3 eyes, Vec3 to) {
        if (mc.player == null) return;

        double diffX = to.x() - eyes.x;
        double diffZ = to.z() - eyes.z;
        double yawRad = Math.atan2(diffZ, diffX);

        mc.player.setYRot((float) Mth.wrapDegrees(toDegree(yawRad) - 90.0));
    }

    private boolean checkStorageContainersInRenderDistance() {
        if (mc.level == null || mc.player == null) return false;

        List<BlockPos> chestPositions = new ArrayList<>();
        int renderDistanceChunks = mc.options.renderDistance().get();

        ChunkPos playerChunkPos = mc.player.chunkPosition();

        if (lastExceedingChunkPos != null && isWithinRenderDistance(playerChunkPos, lastExceedingChunkPos, renderDistanceChunks)) {
            return false;
        }

        for (int xOffset = -renderDistanceChunks; xOffset <= renderDistanceChunks; xOffset++) {
            for (int zOffset = -renderDistanceChunks; zOffset <= renderDistanceChunks; zOffset++) {
                ChunkPos chunkPos = new ChunkPos(playerChunkPos.x + xOffset, playerChunkPos.z + zOffset);
                ChunkAccess chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);

                for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
                    BlockEntity blockEntity = chunk.getBlockEntity(blockPos);
                    if (blockEntity != null && isStorageContainer(blockEntity)) {
                        chestPositions.add(blockPos);
                    }
                }
            }
        }

        if (chestPositions.size() > storageAmount.getValue()) {
            BlockPos largestClusterCenter = findLargestClusterCenter(chestPositions, 10);

            if (largestClusterCenter != null) {
                lastExceedingChunkPos = new ChunkPos(largestClusterCenter);
                logStashToFile(largestClusterCenter.getX(), largestClusterCenter.getY(), largestClusterCenter.getZ());
                return true;
            }
        }

        return false;
    }

    private BlockPos findLargestClusterCenter(List<BlockPos> chestPositions, int clusterRadius) {
        Map<BlockPos, List<BlockPos>> clusters = new HashMap<>();

        for (BlockPos pos : chestPositions) {
            boolean addedToCluster = false;

            for (BlockPos clusterKey : clusters.keySet()) {
                if (isWithinClusterRadius(pos, clusterKey, clusterRadius)) {
                    clusters.get(clusterKey).add(pos);
                    addedToCluster = true;
                    break;
                }
            }

            if (!addedToCluster) {
                clusters.put(pos, new ArrayList<>(Collections.singletonList(pos)));
            }
        }

        List<BlockPos> largestCluster = null;
        for (List<BlockPos> cluster : clusters.values()) {
            if (largestCluster == null || cluster.size() > largestCluster.size()) {
                largestCluster = cluster;
            }
        }

        if (largestCluster != null && !largestCluster.isEmpty()) {
            return calculateClusterCenter(largestCluster);
        }

        return null;
    }

    private boolean isWithinClusterRadius(BlockPos pos1, BlockPos pos2, int radius) {
        int dx = Math.abs(pos1.getX() - pos2.getX());
        int dz = Math.abs(pos1.getZ() - pos2.getZ());
        return dx <= radius && dz <= radius;
    }

    private BlockPos calculateClusterCenter(List<BlockPos> cluster) {
        int sumX = 0;
        int sumY = 0;
        int sumZ = 0;

        for (BlockPos pos : cluster) {
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
        }

        int centerX = sumX / cluster.size();
        int centerY = sumY / cluster.size();
        int centerZ = sumZ / cluster.size();

        return new BlockPos(centerX, centerY, centerZ);
    }

    private boolean isWithinRenderDistance(ChunkPos currentPos, ChunkPos lastPos, int renderDistanceChunks) {
        int dx = currentPos.x - lastPos.x;
        int dz = currentPos.z - lastPos.z;
        int distanceSquared = dx * dx + dz * dz;
        int renderDistance = renderDistanceChunks * 16;
        return distanceSquared <= renderDistance;
    }

    private double toDegree(double rad) {
        return rad * 180 / Math.PI;
    }

    private boolean isStorageContainer(BlockEntity blockEntity) {
        BlockEntityType<?> type = blockEntity.getType();
        return type == BlockEntityType.CHEST ||
               type == BlockEntityType.BARREL ||
               type == BlockEntityType.SHULKER_BOX ||
               type == BlockEntityType.ENDER_CHEST ||
               type == BlockEntityType.TRAPPED_CHEST;
    }

    public static void setStartPos(double x, double z) {
        startX.setValue(x);
        startZ.setValue(z);
    }
}
