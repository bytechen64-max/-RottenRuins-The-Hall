package org.bytechen.hall.config.data;

import org.bytechen.hall.HallMod;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = HallMod.MODID + "/SplendidingConfig")
public class SplendidingConfig implements ConfigData {

    @Comment("Enable debug mode")
    public boolean debug = false;

    @Comment("Base damage multiplier")
    public float damageMultiplier = 1.0f;

    @Comment("Example integer config")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int exampleValue = 50;

    @Comment("Enable custom shader rendering (item glint, outlines, bloom)")
    public boolean use_shader = true;
}
