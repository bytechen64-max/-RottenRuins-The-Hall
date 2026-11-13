package org.bytechen.rottenruins.client.rend;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.network.NetworkHelper;
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
 * 抽象静态特效渲染器基类：子类实现 {@link #getTypeId()} 与 {@link #doRender}，
 * 并在静态初始化块中调用 {@link #registerRenderer} 完成自动注册。
 * @param <T> 特效实例类型，必须继承自 {@link EffectInstance}
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public abstract class AbstractStaticEffectRenderer<T extends AbstractStaticEffectRenderer.EffectInstance> {

    private static final Map<String, AbstractStaticEffectRenderer<?>> RENDERER_REGISTRY = new ConcurrentHashMap<>();

    /** 注册一个渲染器子类实例（单例）。 */
    protected static void registerRenderer(AbstractStaticEffectRenderer<?> renderer) {
        RENDERER_REGISTRY.put(renderer.getTypeId(), renderer);
    }

    private static AbstractStaticEffectRenderer<?> getRenderer(String typeId) {
        return RENDERER_REGISTRY.get(typeId);
    }

    /** 服务端 → 客户端的特效创建/更新 Payload。 */
    public record EffectSyncPayload(String typeId, UUID instanceId, CompoundTag data) {
    }

    /** 客户端处理特效同步包；由网络包处理器调用，建实例/更新都在 ctx.enqueueWork 里做。 */
    public static void handleEffectSyncPacket(EffectSyncPayload payload, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AbstractStaticEffectRenderer<?> renderer = getRenderer(payload.typeId());
            if (renderer != null) {
                renderer.applySyncData(payload.instanceId(), payload.data());
            }
        });
        ctx.get().setPacketHandled(true);
    }

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

        public abstract void readFromNBT(CompoundTag tag);

        public abstract void writeToNBT(CompoundTag tag);
    }

    private final Map<UUID, T> activeInstances = new ConcurrentHashMap<>();

    /** 子类必须实现：该渲染器处理的唯一类型标识符，与网络包里的 typeId 一致。 */
    public abstract String getTypeId();

    /** 子类必须实现：创建特效实例（工厂方法）。 */
    protected abstract T createInstance(UUID id, CompoundTag data);

    /** 子类必须实现：真正的渲染逻辑；partialTick 用于帧间插值。 */
    protected abstract void doRender(PoseStack poseStack, T instance, float partialTick);

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null) {
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

    /** 服务端调用：向所有追踪该实体的客户端发送创建/更新包。 */
    public static void sendEffectToTracking(Entity entity, String typeId, UUID instanceId, CompoundTag data) {
        EffectSyncPayload payload = new EffectSyncPayload(typeId, instanceId, data);
        NetworkHelper.sendToClient(entity, payload);
    }

    public static void sendEffectToAll(String typeId, UUID instanceId, CompoundTag data) {
        EffectSyncPayload payload = new EffectSyncPayload(typeId, instanceId, data);
        NetworkHelper.sendToALLClient(payload);
    }
}