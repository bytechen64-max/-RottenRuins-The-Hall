import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.opengl.GL32.*;

/** Exercises the actual shader on a hidden OpenGL context, checking sampled output alpha. */
public final class StoryDissolveShaderTest {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 8);
        long window = GLFW.glfwCreateWindow(64, 64, "story-shader-test", 0, 0);
        if (window == 0) throw new AssertionError("Hidden GL context unavailable");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        int program = glCreateProgram();
        int vertex = compile(args[0], GL_VERTEX_SHADER), fragment = compile(args[1], GL_FRAGMENT_SHADER);
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glBindAttribLocation(program, 0, "Position");
        glBindAttribLocation(program, 1, "Color");
        glBindAttribLocation(program, 2, "UV0");
        glBindAttribLocation(program, 3, "UV2");
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) throw new AssertionError(glGetProgramInfoLog(program));
        glUseProgram(program);
        FloatBuffer identity = BufferUtils.createFloatBuffer(16);
        identity.put(new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}).flip();
        glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, identity);
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, identity);
        glUniform4f(glGetUniformLocation(program, "ColorModulator"), 1,1,1,1);
        glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
        glUniform1i(glGetUniformLocation(program, "Sampler2"), 2);
        texture(0, 32);
        texture(2, 1);
        int vao = glGenVertexArrays(), vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer vertices = BufferUtils.createFloatBuffer(30);
        vertices.put(new float[]{-1,-1,0,0,0, 1,-1,0,1,0, 1,1,0,1,1,
                -1,-1,0,0,0, 1,1,0,1,1, -1,1,0,0,1}).flip();
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0L);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 20, 12L);
        glVertexAttrib4f(1, 1,1,1,1);
        glVertexAttribI2i(3, 0,0);
        glViewport(0,0,64,64);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        long previous = Long.MAX_VALUE;
        for (float amount : new float[]{0, 0.25f, 0.5f, 0.75f, 1}) {
            glClearColor(0,0,0,0);
            glClear(GL_COLOR_BUFFER_BIT);
            glUniform1f(glGetUniformLocation(program, "Dissolve"), amount);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            ByteBuffer pixels = BufferUtils.createByteBuffer(64 * 64 * 4);
            glReadPixels(0,0,64,64,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            long alpha = 0;
            for (int i = 3; i < pixels.capacity(); i += 4) alpha += Byte.toUnsignedInt(pixels.get(i));
            if (alpha > previous) throw new AssertionError("Dissolve alpha increased");
            if (amount == 0 && alpha != 64L * 64 * 255) throw new AssertionError("Initial text lost opacity");
            if (amount == 0.5f && (alpha == 0 || alpha == 64L * 64 * 255)) throw new AssertionError("Missing partial erosion");
            if (amount == 1 && alpha != 0) throw new AssertionError("Text remains after dissolve");
            previous = alpha;
            System.out.println("Dissolve=" + amount + " alpha=" + alpha);
        }
        // A TTF atlas is red-channel coverage, not an RGBA bitmap. Half coverage must stay half transparent.
        glActiveTexture(GL_TEXTURE0);
        int redTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, redTexture);
        ByteBuffer red = BufferUtils.createByteBuffer(4);
        // A tiny glyph mask: empty, antialiased edge, solid stroke, empty.
        red.put(new byte[]{0, (byte) 128, (byte) 255, 0}).flip();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D,0,GL_R8,2,2,0,GL_RED,GL_UNSIGNED_BYTE,red);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glUniform1f(glGetUniformLocation(program, "Dissolve"), 0);
        glUniform1f(glGetUniformLocation(program, "Intensity"), 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES,0,6);
        ByteBuffer ttfPixels = BufferUtils.createByteBuffer(64 * 64 * 4);
        glReadPixels(0,0,64,64,GL_RGBA,GL_UNSIGNED_BYTE,ttfPixels);
        // Sample inside each quadrant, away from the reconstructed smooth boundaries.
        for (int y : new int[]{8, 56}) {
            for (int x : new int[]{8, 56}) {
                int pixel = (y * 64 + x) * 4;
                int coverage = y < 32 ? (x < 32 ? 0 : 128) : (x < 32 ? 255 : 0);
                if (Byte.toUnsignedInt(ttfPixels.get(pixel + 3)) != coverage) {
                    throw new AssertionError("TTF coverage or transparent background changed");
                }
                for (int channel = 0; channel < 3; channel++) {
                    int expectedColor = coverage == 0 ? 0 : 255;
                    if (Byte.toUnsignedInt(ttfPixels.get(pixel + channel)) != expectedColor) {
                        throw new AssertionError("Antialiased TTF edges must retain foreground color without dark fringes");
                    }
                }
            }
        }
        java.util.Set<Integer> edgeAlphas = new java.util.HashSet<>();
        for (int x = 0; x < 64; x++) {
            edgeAlphas.add(Byte.toUnsignedInt(ttfPixels.get((8 * 64 + x) * 4 + 3)));
        }
        if (edgeAlphas.size() < 16) throw new AssertionError("Nearest-neighbor blocks remain at magnified glyph edges");
        if (glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER) != GL_NEAREST) {
            throw new AssertionError("Story smoothing must not change shared font texture filtering");
        }
        // Check the RGBA path independently: a colored bitmap glyph must retain its own color and alpha.
        ByteBuffer rgba = BufferUtils.createByteBuffer(4);
        rgba.put(new byte[]{25, 100, (byte) 200, (byte) 128}).flip();
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,rgba);
        glUniform1f(glGetUniformLocation(program, "Intensity"), 0);
        glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES,0,6);
        ByteBuffer bitmapPixel = BufferUtils.createByteBuffer(4);
        glReadPixels(32,32,1,1,GL_RGBA,GL_UNSIGNED_BYTE,bitmapPixel);
        int[] expected = {25,100,200,128};
        for (int channel = 0; channel < 4; channel++) {
            if (Byte.toUnsignedInt(bitmapPixel.get(channel)) != expected[channel]) {
                throw new AssertionError("RGBA font colors or transparency changed");
            }
        }
        // Two glyphs in one GPU batch must retain independent dissolve values in UV2.x.
        // The global uniform remains zero: the left glyph stays intact and the right glyph is gone.
        texture(0, 1);
        FloatBuffer glyphVertices = BufferUtils.createFloatBuffer(60);
        glyphVertices.put(new float[]{-1,-1,0,0,0, 0,-1,0,1,0, 0,1,0,1,1,
                -1,-1,0,0,0, 0,1,0,1,1, -1,1,0,0,1,
                0,-1,0,0,0, 1,-1,0,1,0, 1,1,0,1,1,
                0,-1,0,0,0, 1,1,0,1,1, 0,1,0,0,1}).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, glyphVertices, GL_STATIC_DRAW);
        int perGlyphBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, perGlyphBuffer);
        var glyphValues = BufferUtils.createIntBuffer(24);
        for (int i = 0; i < 12; i++) glyphValues.put(i < 6 ? 0 : 32767).put(240);
        glyphValues.flip();
        glBufferData(GL_ARRAY_BUFFER, glyphValues, GL_STATIC_DRAW);
        glEnableVertexAttribArray(3);
        glVertexAttribIPointer(3, 2, GL_INT, 8, 0L);
        glUniform1f(glGetUniformLocation(program, "Dissolve"), 0);
        glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES, 0, 12);
        ByteBuffer independentGlyphs = BufferUtils.createByteBuffer(64 * 64 * 4);
        glReadPixels(0,0,64,64,GL_RGBA,GL_UNSIGNED_BYTE,independentGlyphs);
        if (Byte.toUnsignedInt(independentGlyphs.get((32 * 64 + 16) * 4 + 3)) != 255
                || Byte.toUnsignedInt(independentGlyphs.get((32 * 64 + 48) * 4 + 3)) != 0) {
            throw new AssertionError("Per-character dissolve values leaked across glyphs sharing one batch");
        }
        glDeleteBuffers(perGlyphBuffer);
        glDeleteTextures(redTexture);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("GL error");
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println("PASS: GLSL, smooth font coverage/colors, dissolve and independent glyph progress in one GPU batch");
    }

    private static int compile(String file, int type) throws Exception {
        int shader = glCreateShader(type);
        glShaderSource(shader, Files.readString(Path.of(file)));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) throw new AssertionError(glGetShaderInfoLog(shader));
        return shader;
    }

    private static void texture(int unit, int size) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, glGenTextures());
        ByteBuffer white = BufferUtils.createByteBuffer(size * size * 4);
        while (white.hasRemaining()) white.put((byte) 255);
        white.flip();
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,size,size,0,GL_RGBA,GL_UNSIGNED_BYTE,white);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    }
}
