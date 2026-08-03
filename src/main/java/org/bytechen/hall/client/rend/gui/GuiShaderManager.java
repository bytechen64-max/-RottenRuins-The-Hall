package org.bytechen.hall.client.rend.gui;

import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side singleton that manages active GUI shader overlay effects.
 * <p>
 * Effects are keyed by unique instance UUID.  The manager ticks all effects
 * each client tick and provides per-effect render parameters.
 *
 * <h3>Adding a new visual effect type</h3>
 * <ol>
 *   <li>Create the shader .vsh / .fsh / .json in
 *       {@code assets/hall/shaders/core/}.</li>
 *   <li>Register the {@code ShaderInstance} in
 *       {@link org.bytechen.hall.client.rend.SplendidingShaders}.</li>
 *   <li>Define a unique effect type key constant below.</li>
 *   <li>Add a render branch in
 *       {@link GuiShaderRenderer#getShader} for the new key.</li>
 *   <li>Call {@link #addEffect(GuiShaderEffect)} from the packet handler.</li>
 * </ol>
 */
public final class GuiShaderManager {

    private static final GuiShaderManager INSTANCE = new GuiShaderManager();

    private final Map<UUID, GuiShaderEffect> activeEffects = new ConcurrentHashMap<>();

    private GuiShaderManager() {}

    public static GuiShaderManager getInstance() { return INSTANCE; }

    // ── Tick ───────────────────────────────────────────────────────

    public void tick() {
        Iterator<Map.Entry<UUID, GuiShaderEffect>> it = activeEffects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GuiShaderEffect> entry = it.next();
            GuiShaderEffect effect = entry.getValue();
            effect.tick();
            if (!effect.isAlive()) {
                it.remove();
            }
        }
    }

    // ── Effect management ──────────────────────────────────────────

    public UUID addEffect(GuiShaderEffect effect) {
        return addEffect(UUID.randomUUID(), effect);
    }

    public UUID addEffect(UUID instanceId, GuiShaderEffect effect) {
        GuiShaderEffect existing = activeEffects.get(instanceId);
        if (existing != null) {
            existing.refresh(effect.getDuration(), effect.getDuration() / 2, effect.getDuration() / 3);
        } else {
            activeEffects.put(instanceId, effect);
        }
        return instanceId;
    }

    public void removeEffect(UUID instanceId) { activeEffects.remove(instanceId); }

    public void removeAllByType(String effectType) {
        activeEffects.values().removeIf(e -> e.getEffectType().equals(effectType));
    }

    public void clearAll() { activeEffects.clear(); }

    public boolean hasActiveEffects() { return !activeEffects.isEmpty(); }

    public Collection<GuiShaderEffect> getActiveEffects() {
        return Collections.unmodifiableCollection(activeEffects.values());
    }

    // ── Type key constants — add new keys here ────────────────────

    public static final String TYPE_HORROR_VORONOI = "horror_voronoi";

    // ── Serialisation ──────────────────────────────────────────────

    public static CompoundTag writeEffectData(String effectType, int duration,
                                               int fadeInDuration, int fadeOutDuration) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", effectType);
        tag.putInt("duration", duration);
        tag.putInt("fadeInDuration", fadeInDuration);
        tag.putInt("fadeOutDuration", fadeOutDuration);
        return tag;
    }

    public static GuiShaderEffect fromNbt(CompoundTag tag) {
        String type = tag.getString("type");
        int duration = tag.getInt("duration");
        int fadeIn = tag.getInt("fadeInDuration");
        int fadeOut = tag.getInt("fadeOutDuration");
        return new GuiShaderEffect(type, duration, fadeIn, fadeOut);
    }
}
