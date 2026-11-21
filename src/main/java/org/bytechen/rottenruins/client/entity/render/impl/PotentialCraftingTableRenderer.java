package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.client.entity.model.PotentialCraftingTableModel;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪台主体：geo 模型 + 悬停在方块外 1 格的 15 份材料。
 * <p>
 * 平时材料绕方块缓慢环绕；合成期间停在开始那一刻的角度上，按进度逐份炸成粒子消失，
 * 碎掉的位置留空、其余不重排。
 * <p>
 * 两部分必须由同一个 BER 画：{@code BlockEntityRenderers} 是 Map 覆盖写，
 * 给同一方块实体注册两次只会留下最后一次。geo 渲染器用组合而不是继承，
 * 因为 GeckoLib 的 render 是擦除到 BlockEntity 的方法，直接覆盖会撞签名。
 */
public final class PotentialCraftingTableRenderer implements BlockEntityRenderer<PotentialCraftingTableBlockEntity> {

    /** 环绕半径（格）。 */
    private static final double RADIUS = 1.05d;

    /** 离方块底面的高度。 */
    private static final double HEIGHT = 1.35d;

    /** 物品缩放。 */
    private static final float ITEM_SCALE = 0.5f;

    /** 一圈用多少秒（正常环绕时）。 */
    private static final float ORBIT_SECONDS_PER_TURN = 25.0f;

    /** 上下浮动幅度（格）与频率。 */
    private static final float BOB_AMPLITUDE = 0.05f;
    private static final float BOB_SPEED = 1.0f;

    /** 单帧最多推进多少秒：切窗口/掉帧回来时不会瞬移。 */
    private static final float MAX_FRAME_DELTA = 0.1f;

    /** 一份材料碎裂时的碎片粒子与星点数量。 */
    private static final int SHATTER_FRAGMENTS = 14;
    private static final int SHATTER_SPARKS = 6;

    /** 每个方块实体累计的呼吸时间与上一帧时刻（客户端私有状态）。 */
    private static final Map<Long, Float> ANIMATION_SECONDS = new HashMap<>();
    private static final Map<Long, Long> ANIMATION_FRAME_MILLIS = new HashMap<>();

    /** 上一帧已经碎裂掉了几份材料。 */
    private static final Map<Long, Integer> SHATTERED = new HashMap<>();

    /** 开始合成那一刻的环绕相位：合成期间停在这个角度上，不再转动。 */
    private static final Map<Long, Float> ORBIT_FREEZE = new HashMap<>();

    /** 本次合成开始时的材料份数：合成期间按它分角度，材料碎裂时其余的不跟着挪。 */
    private static final Map<Long, Integer> CRAFT_COUNTS = new HashMap<>();

    private final GeoBlockRenderer<PotentialCraftingTableBlockEntity> geo =
            new GeoBlockRenderer<>(new PotentialCraftingTableModel());

    @Override
    public void render(PotentialCraftingTableBlockEntity station, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        geo.render(station, partialTick, pose, buffer, light, overlay);
        renderMaterials(station, pose, buffer, light, overlay);
    }

    private static void renderMaterials(PotentialCraftingTableBlockEntity station, PoseStack pose,
                                        MultiBufferSource buffer, int light, int overlay) {
        Level level = station.getLevel();
        if (level == null) {
            return;
        }
        long key = station.getBlockPos().asLong();
        float seconds = advanceSeconds(key);

        // 先收集实际有材料的格子：按个数平均分整圈，位置固定，不再旋转。
        List<ItemStack> stacks = new ArrayList<>(PotentialCraftingTableBlockEntity.RING_SLOTS);
        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            ItemStack stack = station.getItem(index);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        int count = stacks.size();
        if (count == 0) {
            SHATTERED.put(key, 0);
            CRAFT_COUNTS.remove(key);
            return;
        }
        // 合成期间沿用开始时的份数做角度分母：碎掉一份只是留个空位，剩下的不重排。
        int layout = count;
        if (station.isCrafting()) {
            Integer started = CRAFT_COUNTS.get(key);
            if (started == null) {
                CRAFT_COUNTS.put(key, count);
            } else {
                layout = Math.max(started, count);
            }
        } else {
            CRAFT_COUNTS.remove(key);
        }
        double step = 360.0d / layout;

        // 平时整圈缓慢环绕；合成期间冻结在开始那一刻的角度，只保留碎裂。
        float orbit = seconds * (360.0f / ORBIT_SECONDS_PER_TURN);
        if (station.isCrafting()) {
            Float frozen = ORBIT_FREEZE.get(key);
            if (frozen == null) {
                frozen = orbit % 360.0f;
                ORBIT_FREEZE.put(key, frozen);
            }
            orbit = frozen;
        } else {
            ORBIT_FREEZE.remove(key);
        }

        int shattered = shatteredCount(station, layout);
        shatter(level, key, stacks, count, step, orbit, shattered);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (int index = shattered; index < count; index++) {
            ItemStack stack = stacks.get(index);
            double angle = Math.toRadians(index * step + orbit);
            double x = Math.cos(angle) * RADIUS;
            double z = Math.sin(angle) * RADIUS;
            double y = HEIGHT + Mth.sin(seconds * BOB_SPEED + index) * BOB_AMPLITUDE;

            pose.pushPose();
            pose.translate(0.5d + x, y, 0.5d + z);
            pose.mulPose(Axis.YP.rotationDegrees((float) (index * step + orbit) + 90.0f));
            pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, pose, buffer,
                    level, index);
            pose.popPose();
        }
    }

    /** 合成进度对应的"已碎裂几份材料"；没在合成时一份都不碎。 */
    private static int shatteredCount(PotentialCraftingTableBlockEntity station, int layout) {
        if (!station.isCrafting()) {
            return 0;
        }
        return Mth.clamp((int) (station.progressPermille() * (long) layout / 1000L), 0, layout);
    }

    /**
     * 累计呼吸时间：按帧间隔推进且单帧封顶，界面开关、暂停、切窗口都不会产生跳变。
     */
    private static float advanceSeconds(long key) {
        long now = Util.getMillis();
        Float previous = ANIMATION_SECONDS.get(key);
        Long lastFrame = ANIMATION_FRAME_MILLIS.get(key);
        float seconds = previous == null ? 0.0f : previous;
        if (lastFrame != null) {
            float delta = Math.min(MAX_FRAME_DELTA, (now - lastFrame) / 1000.0f);
            if (delta > 0.0f) {
                seconds += delta;
            }
        }
        ANIMATION_SECONDS.put(key, seconds);
        ANIMATION_FRAME_MILLIS.put(key, now);
        return seconds;
    }

    /** 新碎掉的那几份材料各自在原地炸成碎片 + 星点，然后从画面上消失。 */
    private static void shatter(Level level, long key, List<ItemStack> stacks, int count, double step, float orbit, int shattered) {
        Integer previous = SHATTERED.get(key);
        int before = previous == null ? 0 : previous;
        SHATTERED.put(key, shattered);
        if (shattered <= before) {
            return;
        }
        for (int index = before; index < shattered && index < count; index++) {
            ItemStack stack = stacks.get(index);
            double angle = Math.toRadians(index * step + orbit);
            double px = 0.5d + Math.cos(angle) * RADIUS;
            double pz = 0.5d + Math.sin(angle) * RADIUS;
            double py = HEIGHT;
            ItemParticleOption fragment = new ItemParticleOption(ParticleTypes.ITEM, stack);
            for (int piece = 0; piece < SHATTER_FRAGMENTS; piece++) {
                double dx = (level.random.nextDouble() - 0.5d) * 0.22d;
                double dy = (level.random.nextDouble() - 0.5d) * 0.22d + 0.08d;
                double dz = (level.random.nextDouble() - 0.5d) * 0.22d;
                level.addParticle(fragment, px + dx * 0.5d, py + dy * 0.5d, pz + dz * 0.5d, dx, dy, dz);
            }
            for (int spark = 0; spark < SHATTER_SPARKS; spark++) {
                double dx = (level.random.nextDouble() - 0.5d) * 0.3d;
                double dy = (level.random.nextDouble() - 0.5d) * 0.3d + 0.05d;
                double dz = (level.random.nextDouble() - 0.5d) * 0.3d;
                level.addParticle(ParticleTypes.END_ROD, px, py, pz, dx, dy, dz);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PotentialCraftingTableBlockEntity station) {
        return true;
    }
}
