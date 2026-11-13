package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

/** 法术分类轴（与 {@link MagicType} 流派、{@link UsingType} 使用方式正交）：ACTIVE 需由法杖主动释放；PASSIVE 进魔法槽即持续生效、不可释放。覆写 {@code IMagicBaseItem#spellKind()} 声明，默认 ACTIVE。 */
public enum SpellKind {

    ACTIVE,

    PASSIVE;

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isPassive() {
        return this == PASSIVE;
    }
}
