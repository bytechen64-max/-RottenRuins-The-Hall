package org.bytechen.rottenruins.core.magic.model;

import java.util.Objects;
import org.bytechen.rottenruins.core.magic.rules.MagicMath;

/**
 * Immutable spell declaration, independent of items, players and the game version.
 * Values are unmodified bases; player attributes and providers are applied by the game layer.
 * Mutable casting, mana and cooldown state must never be stored here.
 */
public final class SpellDefinition {
    public static final float DEFAULT_MIN_CHARGE_RATIO = 0.25f;
    public static final int DEFAULT_KEEP_TRIGGER_INTERVAL = 1;

    private final MagicTypes magicTypes;
    private final UsingType usingType;
    private final SpellKind kind;
    private final int manaCost;
    private final int cooldownTicks;
    private final int chargeTicks;
    private final int keepCostPerSecond;
    private final int keepTriggerInterval;
    private final float damage;
    private final float minChargeRatio;

    private SpellDefinition(Builder builder) {
        magicTypes = builder.magicTypes;
        usingType = builder.usingType;
        kind = builder.kind;
        manaCost = Math.max(0, builder.manaCost);
        cooldownTicks = Math.max(0, builder.cooldownTicks);
        chargeTicks = usingType == UsingType.CHARGE ? Math.max(0, builder.chargeTicks) : 0;
        keepCostPerSecond = usingType == UsingType.KEEP ? Math.max(0, builder.keepCostPerSecond) : 0;
        keepTriggerInterval = usingType == UsingType.KEEP
                ? Math.max(1, builder.keepTriggerInterval) : DEFAULT_KEEP_TRIGGER_INTERVAL;
        damage = Math.max(0f, builder.damage);
        minChargeRatio = MagicMath.ratio(builder.minChargeRatio);
    }

    /** Requires an explicit mode and at least one school; school order also determines display color. */
    public static Builder builder(UsingType usingType, MagicType... magicTypes) {
        return new Builder(usingType, MagicTypes.of(magicTypes));
    }

    /** Allows callers to reuse an existing immutable school combination. */
    public static Builder builder(UsingType usingType, MagicTypes magicTypes) {
        return new Builder(usingType, magicTypes);
    }

    public MagicTypes magicTypes() { return magicTypes; }
    public UsingType usingType() { return usingType; }
    public SpellKind kind() { return kind; }
    public int manaCost() { return manaCost; }
    public int cooldownTicks() { return cooldownTicks; }
    public int chargeTicks() { return chargeTicks; }
    public int keepCostPerSecond() { return keepCostPerSecond; }
    public int keepTriggerInterval() { return keepTriggerInterval; }
    public float damage() { return damage; }
    public float minChargeRatio() { return minChargeRatio; }

    /** Named options avoid confusing the adjacent integer values of the old item constructors. */
    public static final class Builder {
        private final MagicTypes magicTypes;
        private final UsingType usingType;
        private SpellKind kind = SpellKind.ACTIVE;
        private int manaCost;
        private int cooldownTicks;
        private int chargeTicks;
        private int keepCostPerSecond;
        private int keepTriggerInterval = DEFAULT_KEEP_TRIGGER_INTERVAL;
        private float damage;
        private float minChargeRatio = DEFAULT_MIN_CHARGE_RATIO;

        private Builder(UsingType usingType, MagicTypes magicTypes) {
            this.usingType = Objects.requireNonNull(usingType, "usingType");
            this.magicTypes = Objects.requireNonNull(magicTypes, "magicTypes");
        }

        public Builder kind(SpellKind value) { kind = Objects.requireNonNull(value, "kind"); return this; }
        public Builder manaCost(int value) { manaCost = value; return this; }
        public Builder cooldownTicks(int value) { cooldownTicks = value; return this; }
        public Builder chargeTicks(int value) { chargeTicks = value; return this; }
        public Builder keepCostPerSecond(int value) { keepCostPerSecond = value; return this; }
        public Builder keepTriggerInterval(int value) { keepTriggerInterval = value; return this; }
        public Builder damage(float value) { damage = value; return this; }
        public Builder minChargeRatio(float value) { minChargeRatio = value; return this; }

        /** Copies all options into a new immutable definition; the builder may then be reused. */
        public SpellDefinition build() { return new SpellDefinition(this); }
    }
}
