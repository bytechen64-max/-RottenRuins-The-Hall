package org.bytechen.hall.client.rend.gui;

/**
 * Represents a single active GUI shader overlay effect on the client.
 * <p>
 * Lifecycle (in ticks):
 * <ol>
 *   <li><b>Fade-in phase</b> (0 → fadeInDuration): uIntensity goes 0→1.</li>
 *   <li><b>Hold phase</b> (fadeInDuration → duration - fadeOutDuration): uIntensity stays 1.</li>
 *   <li><b>Fade-out phase</b> (last fadeOutDuration ticks): uIntensity goes 1→0.</li>
 * </ol>
 */
public class GuiShaderEffect {

    private final String effectType;
    private int duration;
    private final int fadeInDuration;
    private final int fadeOutDuration;
    private int age;
    private boolean alive = true;

    /**
     * @param effectType     shader type key (e.g. "horror_voronoi")
     * @param duration       total duration in ticks
     * @param fadeInDuration ticks for fade-in (0→1)
     * @param fadeOutDuration ticks for fade-out (1→0)
     */
    public GuiShaderEffect(String effectType, int duration, int fadeInDuration, int fadeOutDuration) {
        this.effectType = effectType;
        this.duration = duration;
        this.fadeInDuration = fadeInDuration;
        this.fadeOutDuration = fadeOutDuration;
        this.age = 0;
    }

    public void tick() {
        age++;
        if (age >= duration) {
            alive = false;
        }
    }

    public boolean isAlive()               { return alive; }
    public String getEffectType()           { return effectType; }
    public int getAge()                     { return age; }
    public int getDuration()                { return duration; }

    /**
     * Fullscreen intensity: 0→1 during fade-in, 1 during hold, 1→0 during fade-out.
     */
    public float getIntensity() {
        if (age <= 0) return 0f;
        // Fade-in
        if (age <= fadeInDuration) {
            return (float) age / fadeInDuration;
        }
        // Fade-out
        int fadeOutStart = duration - fadeOutDuration;
        if (age >= fadeOutStart) {
            int fadeOutAge = age - fadeOutStart;
            if (fadeOutDuration <= 0) return 1f;
            return 1f - (float) fadeOutAge / fadeOutDuration;
        }
        return 1f;
    }

    /** Refresh / extend by re-triggering from fade-in start. */
    public void refresh(int newDuration, int newFadeIn, int newFadeOut) {
        if (age > newFadeIn) {
            age = 0;
        }
        this.duration = Math.max(this.duration, age + (newDuration - newFadeOut));
    }
}
