package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import org.jetbrains.annotations.NotNull;

/**
 * 法杖等级。等级决定"对<b>同流派</b>法术"的固定伤害加成：每升一级 +2 点。
 *
 * <p>目前所有法杖都是 {@link #COMMON}，且还没有进阶途径。</p>
 */
public enum StaffGrade {

    /** 普通：无额外加成。 */
    COMMON("common", 0f),

    /** 史诗：同流派法术 +2 点。 */
    EPIC("epic", 2f),

    /** 传说：同流派法术 +4 点。 */
    LEGENDARY("legendary", 4f);

    private final String id;
    private final float spellBonus;

    StaffGrade(String id, float spellBonus) {
        this.id = id;
        this.spellBonus = spellBonus;
    }

    @NotNull
    public String getId() {
        return id;
    }

    /** 同流派法术的固定伤害加成（点）。 */
    public float spellBonus() {
        return spellBonus;
    }

    @NotNull
    public String getTranslationKey() {
        return "staff_grade.rotten_ruins." + id;
    }

    @NotNull
    public static StaffGrade byId(@NotNull String id) {
        for (StaffGrade grade : values()) {
            if (grade.id.equals(id)) {
                return grade;
            }
        }
        return COMMON;
    }
}
