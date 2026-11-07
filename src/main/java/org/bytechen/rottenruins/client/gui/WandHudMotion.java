package org.bytechen.rottenruins.client.gui;

/** Client presentation only: springs use seconds so feedback is independent of frame rate. */
public final class WandHudMotion {
    public static final class Spring {
        public float value;
        private float velocity;

        public void step(float target, float seconds) {
            // Analytic underdamped spring; no Euler instability after a slow frame.
            double dt = Math.max(0, Math.min(0.1, seconds)), omega = 23, damping = 0.72;
            double decay = damping * omega, frequency = omega * Math.sqrt(1 - damping * damping);
            double a = value - target, b = (velocity + decay * a) / frequency;
            double cos = Math.cos(frequency * dt), sin = Math.sin(frequency * dt), e = Math.exp(-decay * dt);
            value = (float) (target + e * (a * cos + b * sin));
            velocity = (float) (e * ((-decay * a + frequency * b) * cos
                    + (-decay * b - frequency * a) * sin));
        }
    }

    public final Spring playerHover = new Spring(), wandHover = new Spring(), iconHover = new Spring();
    public final Spring focus = new Spring();
    public float player, wand, playerTrail, wandTrail, playerFlash, wandFlash;
    public float ready, switched, resize;
    private float previousPlayer, previousWand, previousCooldown;
    private boolean initialized;
    private String spellKey = "", wandKey = "";

    public void update(float dt, WandHudGeometry.Target target, float playerMana, float wandMana,
                       float cooldown, String spell, String reserve) {
        dt = Math.max(0, Math.min(0.1F, dt));
        boolean newSpell = !spell.equals(spellKey), newWand = !reserve.equals(wandKey);
        if (!initialized) {
            player = playerTrail = previousPlayer = playerMana;
            wand = wandTrail = previousWand = wandMana;
        } else {
            if (newSpell) switched = 1;
            else if (previousCooldown > 0 && cooldown <= 0) ready = 1;
            if (playerMana < previousPlayer - 0.008F) playerFlash = 1;
            if (!newWand && wandMana < previousWand - 0.008F) wandFlash = 1;
        }
        if (newSpell) ready = 0;
        if (newWand) { wand = wandTrail = wandMana; wandFlash = 0; }
        float fast = 1 - (float) Math.exp(-dt / 0.065F);
        float slow = 1 - (float) Math.exp(-dt / 0.38F);
        player += (playerMana - player) * fast;
        wand += (wandMana - wand) * fast;
        playerTrail = Math.max(player, playerTrail + (playerMana - playerTrail) * slow);
        wandTrail = Math.max(wand, wandTrail + (wandMana - wandTrail) * slow);
        playerHover.step(target == WandHudGeometry.Target.PLAYER ? 1 : 0, dt);
        wandHover.step(target == WandHudGeometry.Target.WAND ? 1 : 0, dt);
        iconHover.step(target == WandHudGeometry.Target.ICON ? 1 : 0, dt);
        focus.step(target == WandHudGeometry.Target.NONE ? 0 : 1, dt);
        playerFlash = Math.max(0, playerFlash - dt * 2.4F);
        wandFlash = Math.max(0, wandFlash - dt * 2.4F);
        ready = Math.max(0, ready - dt / 0.65F);
        switched = Math.max(0, switched - dt / 0.35F);
        resize = Math.max(0, resize - dt / 0.22F);
        previousPlayer = playerMana; previousWand = wandMana; previousCooldown = cooldown;
        spellKey = spell; wandKey = reserve; initialized = true;
    }
}
