package org.bytechen.rottenruins.overworld.energy;

/** 参数以十分之一为单位保存，窗口两端均包含；不在任一窗口内时没有有效元素。 */
public enum ElementBand {
    LIGHT(90,100,10,20,20,30,false,0xFFF4AC),
    FIRE(70,80,20,30,70,80,false,0xFF7843),
    WATER(50,60,40,50,40,50,false,0x53BEFF),
    NATURE(30,40,30,40,40,50,false,0x78D98A),
    DEATH(20,30,70,80,40,60,false,0xBE8BDA),
    VOID(0,10,60,70,10,30,true,0x8B85FF),
    MYSTERIOUS(0,10,80,100,0,10,false,0xE7A7EF);

    public final int minFc,maxFc,minB,maxB,minG,maxG,color;
    public final boolean negative;

    ElementBand(int minFc,int maxFc,int minB,int maxB,int minG,int maxG,boolean negative,int color) {
        this.minFc=minFc; this.maxFc=maxFc; this.minB=minB; this.maxB=maxB;
        this.minG=minG; this.maxG=maxG; this.negative=negative; this.color=color;
    }

    public String key() { return "gui.rotten_ruins.source.element." + name().toLowerCase(java.util.Locale.ROOT); }

    /** 懒人模式选取窗口中心，避免边界调参误差；最后一项为 0 正相 / 1 反相。 */
    public int[] preset() { return new int[]{(minFc+maxFc)/2,(minB+maxB)/2,(minG+maxG)/2,negative?1:0}; }

    public static ElementBand match(int fc,int bandwidth,int gain,boolean negative) {
        for (ElementBand element : values()) {
            if (element.negative==negative && fc>=element.minFc && fc<=element.maxFc
                    && bandwidth>=element.minB && bandwidth<=element.maxB && gain>=element.minG && gain<=element.maxG) return element;
        }
        return null;
    }
}
