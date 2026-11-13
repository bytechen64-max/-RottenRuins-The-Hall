package org.bytechen.rottenruins.utils;

import com.mojang.logging.LogUtils;
import org.bytechen.rottenruins.HallMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/** 全模组通用小工具：资源路径、夹取。 */
public class ModUtils {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation modLoc(String path) { return ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path); }
    public static ResourceLocation mcLoc(String path) { return ResourceLocation.withDefaultNamespace(path); }
    /** 保留签名便于书写，实现直接用原版 {@link Mth#clamp}。 */
    public static float clamp(float value, float min, float max) { return Mth.clamp(value, min, max); }

}
