package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import com.google.gson.*;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Coordinates use Minecraft model units (0..16), before display/hand transforms. */
public record AnchorProfile(View firstPerson, View thirdPerson) {
    public record View(boolean enabled, List<Vector3f> points, float start, float end,
                       float lifetimeTicks, float maxDistance, int maxSamples, int subdivisions) {}
    public static AnchorProfile parse(JsonObject root) {
        return new AnchorProfile(view(root, "firstPerson"), view(root, "thirdPerson"));
    }
    private static View view(JsonObject root, String name) {
        JsonObject obj = root.has(name) ? root.getAsJsonObject(name) : new JsonObject();
        JsonArray array = obj.has("points") ? obj.getAsJsonArray("points") : root.getAsJsonArray("points");
        if (array == null || array.size() < 2 || array.size() > 16) throw new IllegalArgumentException("Need 2..16 anchors");
        List<Vector3f> points = new ArrayList<>();
        for (JsonElement element : array) {
            JsonArray a = element.getAsJsonArray();
            if (a.size()!=3) throw new IllegalArgumentException("Anchor must be [x,y,z]");
            Vector3f p = new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
            if (!Float.isFinite(p.x+p.y+p.z) || Math.max(Math.max(Math.abs(p.x),Math.abs(p.y)),Math.abs(p.z))>256)
                throw new IllegalArgumentException("Invalid anchor coordinates");
            points.add(p);
        }
        float start=number(obj,"start",.32F),end=number(obj,"end",.62F),life=number(obj,"lifetimeTicks",7),distance=number(obj,"maxDistance",4);
        int samples=(int)number(obj,"maxSamples",128),sub=(int)number(obj,"subdivisions",4);
        if(start<0||end>1||start>=end||life<.1||life>200||distance<=0||distance>64||samples<2||samples>1024||sub<1||sub>32)
            throw new IllegalArgumentException("Invalid sampling settings");
        return new View(!obj.has("enabled")||obj.get("enabled").getAsBoolean(),List.copyOf(points),start,end,life,distance,samples,sub);
    }
    private static float number(JsonObject object,String key,float fallback) {
        float value=object.has(key)?object.get(key).getAsFloat():fallback;
        if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite setting");
        return value;
    }
}
