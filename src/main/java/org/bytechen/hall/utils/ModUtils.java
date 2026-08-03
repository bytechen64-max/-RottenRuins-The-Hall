package org.bytechen.hall.utils;

import com.mojang.logging.LogUtils;
import org.bytechen.hall.HallMod;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class ModUtils {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation modLoc(String path) { return ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path); }
    public static ResourceLocation mcLoc(String path) { return ResourceLocation.withDefaultNamespace(path); }
    public static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

}
