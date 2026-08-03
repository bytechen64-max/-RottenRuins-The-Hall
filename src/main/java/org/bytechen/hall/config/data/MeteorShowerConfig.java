package org.bytechen.hall.config.data;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import org.bytechen.hall.HallMod;

@Config(name = HallMod.MODID + "/MeteorConfig")
public class MeteorShowerConfig implements ConfigData {

    // ═══════════════════════════════════════════════════════════════
    // 事件生成参数（MeteorShowerEvent 使用）
    // ═══════════════════════════════════════════════════════════════

    @Comment("是否启用陨石雨事件")
    @ConfigEntry.Gui.Tooltip
    public boolean enable = true;

    @Comment("基础间隔（tick）")
    @ConfigEntry.Gui.Tooltip
    public int intervalBase = 3600;

    @Comment("随机偏移（± tick）")
    @ConfigEntry.Gui.Tooltip
    public int intervalJitter = 60;

    @Comment("水平随机半径（格）")
    @ConfigEntry.Gui.Tooltip
    public double spawnRadius = 64.0;

    @Comment("地面以上的生成高度")
    @ConfigEntry.Gui.Tooltip
    public double heightAboveGround = 100.0;

    @Comment("下落速度（负Y）")
    @ConfigEntry.Gui.Tooltip
    public double fallSpeed = -1.5;

    @Comment("生成失败后的重试间隔（tick）")
    @ConfigEntry.Gui.Tooltip
    public int retryDelay = 100;

    // ═══════════════════════════════════════════════════════════════
    // 陨石落地行为参数（MeteoriteEntity 使用）
    // ═══════════════════════════════════════════════════════════════

    @Comment("AoE 每 tick 魔法伤害")
    @ConfigEntry.Gui.Tooltip
    public float aoeDamage = 5.0f;

    @Comment("AoE 二十面体最大缩放倍数")
    @ConfigEntry.Gui.Tooltip
    public float icosaMaxScale = 10.0f;

    @Comment("总消退时间（tick）：展开+保持+收缩+停留")
    @ConfigEntry.Gui.Tooltip
    public int fadeTicks = 170;

    @Comment("停留期（tick）：收缩结束后等待圆环消散，期间无伤害")
    @ConfigEntry.Gui.Tooltip
    public int lingerDuration = 60;

    @Comment("粒子密度（每立方米粒子数）")
    @ConfigEntry.Gui.Tooltip
    public double particleDensity = 15.0;

    @Comment("撞击后扩散 Hall 方块的最小半径")
    @ConfigEntry.Gui.Tooltip
    public int spreadRadiusMin = 3;

    @Comment("撞击后扩散 Hall 方块的最大半径")
    @ConfigEntry.Gui.Tooltip
    public int spreadRadiusMax = 5;

    @Comment("撞击后扩散 Hall 方块的最小数量")
    @ConfigEntry.Gui.Tooltip
    public int spreadCountMin = 8;

    @Comment("撞击后扩散 Hall 方块的最大数量")
    @ConfigEntry.Gui.Tooltip
    public int spreadCountMax = 16;

    @Comment("撞击点生成矿石的概率（0~1）")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    @ConfigEntry.Gui.Tooltip
    public int oreChancePercent = 50;

    @Comment("冲击波角度范围（度）")
    @ConfigEntry.Gui.Tooltip
    public float shockwaveAngle = 45.0f;

    @Comment("冲击波速度")
    @ConfigEntry.Gui.Tooltip
    public float shockwaveSpeed = 32.0f;

    @Comment("冲击波持续时间（tick）")
    @ConfigEntry.Gui.Tooltip
    public int shockwaveDuration = 30;

    @Comment("冲击波初始缩放")
    @ConfigEntry.Gui.Tooltip
    public float shockwaveInitialScale = 1.0f;

    @Comment("冲击波缩放衰减率")
    @ConfigEntry.Gui.Tooltip
    public float shockwaveShrink = 0.06f;
}