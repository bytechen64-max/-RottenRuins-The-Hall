package org.bytechen.hall.client.rend;

import org.bytechen.hall.HallMod;


import org.bytechen.hall.network.NetworkHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 抽象静态特效渲染器基类。
 * 子类需要实现 {@link #getTypeId()} 和 {@link #doRender}，
 * 并在静态初始化块中调用 {@link #registerRenderer} 完成自动注册。
 *
 * @param <T> 特效实例数据类型，必须继承自 {@link EffectInstance}
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public abstract class AbstractStaticEffectRenderer<T extends AbstractStaticEffectRenderer.EffectInstance> {

    // ========== 全局注册表 ==========
    private static final Map<String, AbstractStaticEffectRenderer<?>> RENDERER_REGISTRY = new ConcurrentHashMap<>();

    /**
     * 注册一个渲染器子类实例（单例）。
     */
    protected static void registerRenderer(AbstractStaticEffectRenderer<?> renderer) {
        RENDERER_REGISTRY.put(renderer.getTypeId(), renderer);
    }

    /**
     * 根据类型标识获取渲染器。
     */
    private static AbstractStaticEffectRenderer<?> getRenderer(String typeId) {
        return RENDERER_REGISTRY.get(typeId);
    }

    // ========== 网络包处理（通用） ==========

    /**
     * 网络包 Payload，由服务端发送到客户端，触发特效创建/更新。
     */
    public record EffectSyncPayload(String typeId, UUID instanceId, CompoundTag data) {
        // 这里假设使用 CompoundTag 传输参数，实际可自定义序列化方式
    }

    /**
     * 在客户端处理收到的特效同步包。
     * 该方法应由网络包处理器调用（见后文集成示例）。
     */
    public static void handleEffectSyncPacket(EffectSyncPayload payload, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AbstractStaticEffectRenderer<?> renderer = getRenderer(payload.typeId());
            if (renderer != null) {
                renderer.applySyncData(payload.instanceId(), payload.data());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ========== 实例数据基类 ==========

    public static abstract class EffectInstance {
        protected UUID id;
        protected int age;
        protected int maxAge;
        protected boolean alive = true;

        public EffectInstance(UUID id, int maxAge) {
            this.id = id;
            this.maxAge = maxAge;
            this.age = 0;
        }

        public void tick() {
            age++;
            if (age >= maxAge) {
                alive = false;
            }
        }

        public boolean isAlive() { return alive; }
        public UUID getId() { return id; }
        public int getAge() { return age; }
        public float getLifeProgress() { return (float) age / maxAge; }

        /**
         * 从网络数据中更新参数（由子类实现具体反序列化）。
         */
        public abstract void readFromNBT(CompoundTag tag);

        /**
         * 将当前状态写入网络数据（用于服务端发送时）。
         */
        public abstract void writeToNBT(CompoundTag tag);
    }

    // ========== 每个渲染器子类的实例管理 ==========
    private final Map<UUID, T> activeInstances = new ConcurrentHashMap<>();

    /**
     * 子类必须实现：返回该渲染器处理的唯一类型标识符（与网络包中的 typeId 一致）。
     */
    public abstract String getTypeId();

    /**
     * 子类必须实现：创建特效实例的具体对象（工厂方法）。
     */
    protected abstract T createInstance(UUID id, CompoundTag data);

    /**
     * 子类必须实现：真正的渲染逻辑。
     * @param poseStack 矩阵栈
     * @param instance 当前要绘制的特效实例
     * @param partialTick 部分 tick 插值
     */
    protected abstract void doRender(PoseStack poseStack, T instance, float partialTick);

    // ========== 通用 Tick 与渲染事件 ==========

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null) {
            // 遍历所有已注册的渲染器，更新各自的实例
            for (AbstractStaticEffectRenderer<?> renderer : RENDERER_REGISTRY.values()) {
                renderer.tickInstances();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (AbstractStaticEffectRenderer<?> renderer : RENDERER_REGISTRY.values()) {
            renderer.renderAll(poseStack, event.getPartialTick());
        }

        poseStack.popPose();
    }

    private void tickInstances() {
        Iterator<Map.Entry<UUID, T>> it = activeInstances.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, T> entry = it.next();
            T inst = entry.getValue();
            inst.tick();
            if (!inst.isAlive()) {
                it.remove();
            }
        }
    }

    private void renderAll(PoseStack poseStack, float partialTick) {
        for (T inst : activeInstances.values()) {
            doRender(poseStack, inst, partialTick);
        }
    }

    // ========== 网络数据应用 ==========
    public void applySyncData(UUID instanceId, CompoundTag data) {
        T existing = activeInstances.get(instanceId);
        if (existing != null) {
            existing.readFromNBT(data);
        } else {
            T newInst = createInstance(instanceId, data);
            if (newInst != null) {
                activeInstances.put(instanceId, newInst);
            }
        }
    }

    // ========== 供服务端调用的发送接口 ==========
    /**
     * 服务端调用此方法，向所有追踪该实体的客户端发送特效创建/更新包。
     * 示例：NetworkHelper.sendToClient(entity, payload);
     */
    public static void sendEffectToTracking(Entity entity, String typeId, UUID instanceId, CompoundTag data) {
        EffectSyncPayload payload = new EffectSyncPayload(typeId, instanceId, data);
        NetworkHelper.sendToClient(entity, payload);
    }

    public static void sendEffectToAll(String typeId, UUID instanceId, CompoundTag data) {
        EffectSyncPayload payload = new EffectSyncPayload(typeId, instanceId, data);
        NetworkHelper.sendToALLClient(payload);
    }
}