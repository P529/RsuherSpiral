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
    private Direction[] directions = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
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

        RusherHackAPI.getNotificationManager().info("Next chunk at " + targetChunk.toString());
        RusherHackAPI.getNotificationManager().info("Next goal at " + targetChunk.x*16 + " " + targetChunk.z*16);
        RusherHackAPI.getNotificationManager().info("Center at " + startX.getValue() + " " +  startZ.getValue());

    }

    private void setStartPos() {
        if (!startPosZero()) {
            RusherHackAPI.getNotificationManager().info("Case: Start Pos not zero");
            RusherHackAPI.getNotificationManager().info("Start coords: " + startX.getValue().toString() + " " + startZ.getValue().toString());
            assert mc.level != null;
            this.targetChunk = mc.level.getChunk(new BlockPos(startX.getValue().intValue(), 0, startZ.getValue().intValue())).getPos();
            RusherHackAPI.getNotificationManager().info("Target chunk: " + targetChunk.x + " " + targetChunk.z);
            RusherHackAPI.getNotificationManager().info("Center block of target chunk " + targetChunk.getMiddleBlockPosition(120));
            RusherHackAPI.getNotificationManager().info("Region coords of target chunk: " + targetChunk.getRegionX());
            RusherHackAPI.getNotificationManager().info(targetChunk.toString());
        } else {
            RusherHackAPI.getNotificationManager().info("Case: Start Pos zero");
            RusherHackAPI.getNotificationManager().info("Start coords: " + startX.getValue().toString() + " " + startZ.getValue().toString());
            assert mc.player != null;
            this.targetChunk = mc.player.chunkPosition();
            startX.setValue(mc.player.getX());
            startZ.setValue(mc.player.getZ());
            RusherHackAPI.getNotificationManager().info("Target chunk: " + targetChunk.x + " " + targetChunk.z);
            RusherHackAPI.getNotificationManager().info("Center block of target chunk " + targetChunk.getMiddleBlockPosition(120));
            RusherHackAPI.getNotificationManager().info("Region coords of target chunk: " + targetChunk.getRegionX());
            RusherHackAPI.getNotificationManager().info(targetChunk.toString());
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
            RusherHackAPI.getNotificationManager().info(String.format("Reached chunk at %s with iteration %d", currentPos.toString(), step));
            targetChunk = nextTarget();
            step++;
            RusherHackAPI.getNotificationManager().info(String.format("Next chunk at %s", nextTarget().toString()));
        }

        if (checkStorageContainersInRenderDistance()) {
            RusherHackAPI.getNotificationManager().info(String.format("More than %d storage containers found in render distance!", storageAmount.getValue()));
        }
    }

    @Override
    public void onDisable() {
        RusherHackAPI.getNotificationManager().info("We at step: " + step + " and for some reason we do -1");
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

        int containerCount = 0;
        int renderDistance = mc.options.renderDistance().get();

        ChunkPos playerChunkPos = mc.player.chunkPosition();
        int chunkRadius = renderDistance / 16;

        if (lastExceedingChunkPos != null) {
            int dx = playerChunkPos.x - lastExceedingChunkPos.x;
            int dz = playerChunkPos.z - lastExceedingChunkPos.z;
            if (dx * dx + dz * dz <= chunkRadius * chunkRadius * 4) {
                return false;
            }
        }

        for (int xOffset = -chunkRadius; xOffset <= chunkRadius; xOffset++) {
            for (int zOffset = -chunkRadius; zOffset <= chunkRadius; zOffset++) {
                ChunkPos chunkPos = new ChunkPos(playerChunkPos.x + xOffset, playerChunkPos.z + zOffset);
                ChunkAccess chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);

                for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
                    BlockEntity blockEntity = chunk.getBlockEntity(blockPos);
                    if (blockEntity != null && isStorageContainer(blockEntity)) {
                        containerCount++;
                        if (containerCount > storageAmount.getValue()) {
                            lastExceedingChunkPos = playerChunkPos;
                            return true;
                        }
                    }
                }
            }
        }

        return false;
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
