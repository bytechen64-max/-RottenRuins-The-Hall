package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import com.google.gson.JsonObject;

/** Pure JSON visual settings; damage, cooldowns and animation remain server-controlled. */
public record SlashEffect(String renderer,String texture,String color,float opacity,float brightness,float width,
                          float curvature,float lifetimeTicks,float coreWidth,float fill,float glow,float noise,
                          float flowSpeed,float tailPower,float particles,float stability,float refraction,float dispersion) {
    public static final SlashEffect DEFAULT=new SlashEffect("inherit","rotten_ruins:textures/effect/slash_crescent.png","inherit",.96F,1.4F,1.65F,.035F,8,.045F,.55F,.48F,.3F,1.2F,1.3F,1,.85F,6F,.15F);
    public static SlashEffect parse(JsonObject j){
        String renderer=text(j,"renderer",DEFAULT.renderer),texture=text(j,"texture",DEFAULT.texture),color=text(j,"color",DEFAULT.color);
        if(!renderer.equals("inherit")&&!renderer.equals("texture")&&!renderer.equals("shader"))throw new IllegalArgumentException("Unknown effect renderer");
        if(!texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||texture.contains(".."))throw new IllegalArgumentException("Invalid texture ID");
        if(!color.equals("inherit")&&!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Color must be #RRGGBB");
        return new SlashEffect(renderer,texture,color,n(j,"opacity",DEFAULT.opacity,0,1),n(j,"brightness",DEFAULT.brightness,.1F,3),
                n(j,"width",DEFAULT.width,.25F,4),n(j,"curvature",DEFAULT.curvature,0,.5F),n(j,"lifetimeTicks",DEFAULT.lifetimeTicks,1,40),
                n(j,"coreWidth",DEFAULT.coreWidth,.01F,.25F),n(j,"fill",DEFAULT.fill,0,1),n(j,"glow",DEFAULT.glow,0,1),
                n(j,"noise",DEFAULT.noise,0,1),n(j,"flowSpeed",DEFAULT.flowSpeed,0,4),n(j,"tailPower",DEFAULT.tailPower,.3F,4),n(j,"particles",DEFAULT.particles,0,3),
                n(j,"stability",DEFAULT.stability,0,1),n(j,"refraction",DEFAULT.refraction,0,12),n(j,"dispersion",DEFAULT.dispersion,0,1));
    }
    private static String text(JsonObject j,String k,String d){return j.has(k)?j.get(k).getAsString():d;}
    private static float n(JsonObject j,String k,float d,float min,float max){float v=j.has(k)?j.get(k).getAsFloat():d;if(!Float.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("Invalid effect field: "+k);return v;}
    public int tint(int fallback){return color.equals("inherit")?fallback:0xff000000|Integer.parseInt(color.substring(1),16);}
}
