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
import org.rusherhack.client.api.setting.BindSetting;
import org.rusherhack.core.bind.key.NullKey;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NullSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;

public class BasefinderModule extends ToggleableModule {

    private final NumberSetting<Double> chunkDistanceSetting = new NumberSetting<>("Chunk Distance", 7.0, 0.0, 20.0)
            .incremental(1);

    private final NumberSetting<Double> startStepSetting = new NumberSetting<>("Start Step", 0.0, 0.0, 20.0)
            .incremental(1);

    private final NullSetting startPositionSetting = new NullSetting("Start Position");

    private static final NumberSetting<Double> startX = new NumberSetting<>("Start X", 0.0, -29999999.0, 29999999.0)
            .incremental(1);

    private static final NumberSetting<Double> startZ = new NumberSetting<>("Start Z", 0.0, -29999999.0, 29999999.0)
            .incremental(1);

    private final NumberSetting<Double> storageAmount = new NumberSetting<>("Chest amount", 25.0, 0.0, 500.0)
            .incremental(1);

    private final BooleanSetting saveStepSetting = new BooleanSetting("Save Step", "Saves the current step upon disable.", true);

    int step;
    ChunkPos targetChunk;
    Direction[] directions = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    public BasefinderModule() {
        super("BasefinderSpiral", "Module that spirals and area and saves n number of chest locations to a file.", ModuleCategory.WORLD);

        this.startPositionSetting.addSubSettings(startX, startZ);

        this.registerSettings(
                this.chunkDistanceSetting,
                this.startStepSetting,
                this.saveStepSetting,
                this.startPositionSetting
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

        RusherHackAPI.getNotificationManager().info(("Next chunk at " + targetChunk.toString()));
    }

    private void setStartPos() {
        if (!startPosZero()) {
            targetChunk = mc.level.getChunk(new BlockPos(startX.getValue().intValue(), 0, startZ.getValue().intValue())).getPos();
        } else {
            this.targetChunk = mc.player.chunkPosition();
            startX.setValue(mc.player.getX());
            startZ.setValue(mc.player.getZ());
        }
    }

    private static boolean startPosZero() {
        return startX.getValue() == 0.0 && startZ.getValue() == 0.0;
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
            RusherHackAPI.getNotificationManager().info(String.format("More than %f storage containers found in render distance!", storageAmount.getValue()));
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
        if (saveStepSetting.getValue())
        {
            startStepSetting.setValue(step);
            assert mc.player != null;
        }
    }

    private ChunkPos nextTarget() {
        int index = step % directions.length;
        Vec3i directionVec = directions[index].getNormal();
        int nextDistance = (step + 1) * chunkDistanceSetting.getValue().intValue();
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

    private double toDegree(double rad) {
        return rad * 180 / Math.PI;
    }

    private boolean checkStorageContainersInRenderDistance() {
        if (mc.level == null || mc.player == null) return false;

        int containerCount = 0;
        int renderDistance = mc.options.renderDistance().get();

        ChunkPos playerChunkPos = mc.player.chunkPosition();
        int chunkRadius = renderDistance / 16;

        for (int xOffset = -chunkRadius; xOffset <= chunkRadius; xOffset++) {
            for (int zOffset = -chunkRadius; zOffset <= chunkRadius; zOffset++) {
                ChunkPos chunkPos = new ChunkPos(playerChunkPos.x + xOffset, playerChunkPos.z + zOffset);
                ChunkAccess chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);

                for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
                    BlockEntity blockEntity = chunk.getBlockEntity(blockPos);
                    if (blockEntity != null && isStorageContainer(blockEntity)) {
                        containerCount++;
                        if (containerCount > storageAmount.getValue()) {
                            return true; // Early exit if we already found too many containers
                        }
                    }
                }
            }
        }

        return false;
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
