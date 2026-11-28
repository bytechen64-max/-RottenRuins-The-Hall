package org.bytechen.rottenruins.client.story;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** The same world/view/model chain is used for drawing and HUD visibility. */
public final class StoryTextTransform {
    private StoryTextTransform() {}

    /** The panel faces the player's center at its fixed radial bearing, independent of current view. */
    public static Matrix4f panel(Matrix4f view, Vector3f relativeAnchor, float bearing, float scale) {
        return new Matrix4f(view).translate(relativeAnchor).rotateY(-bearing).scale(-scale, -scale, scale);
    }

    /** Scale/spin around the character cell center, preserving the original line's spacing. */
    public static Matrix4f glyph(Matrix4f panel, float x, float y, float width, StoryGlyphMotion.Pose motion) {
        return new Matrix4f(panel).translate(x + width / 2 + motion.x(), y + 4.5f + motion.y(), 0)
                .rotateZ(motion.rotation()).scale(motion.scale(), motion.scale(), 1)
                .translate(-width / 2, -4.5f, 0);
    }

    public static StoryProjection.Result project(Matrix4f projection, Matrix4f model,
                                                  float halfWidthPixels, float halfHeightPixels) {
        Matrix4f clip = new Matrix4f(projection).mul(model);
        boolean readable = true;
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                Vector4f corner = clip.transform(new Vector4f(x * halfWidthPixels, y * halfHeightPixels, 0, 1));
                readable &= corner.w > 0.05f && Math.abs(corner.x) < corner.w * 0.92f
                        && Math.abs(corner.y) < corner.w * 0.92f
                        && Math.abs(corner.z) < corner.w;
            }
        }
        Vector4f center = clip.transform(new Vector4f(0, 0, 0, 1));
        // No perspective division for direction: dividing by negative w flips rear targets.
        return StoryProjection.direction(readable, center.x, -center.y);
    }
}
