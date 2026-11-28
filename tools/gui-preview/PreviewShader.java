import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按项目里的 {@code shaders/core/<name>.json} 加载一个 Minecraft core shader。
 *
 * <p>这不是"随便编译两个 glsl"：MC 的 {@code ShaderInstance} 会读 json 里的
 * {@code attributes}（决定顶点格式和 attribute location）、{@code samplers}
 * （纹理单元）、{@code uniforms}（默认值）和 {@code blend}（真正的混合状态）。
 * 这些语义在实机里是 Java 侧的簿记、不是 GLSL 本身，所以离屏预览必须自己复现，
 * 否则画面会"看起来有东西但和游戏里不一样"（例如 {@code BlurScale} 忘了设就是 0）。</p>
 *
 * <p>属性顺序 = json {@code attributes} 数组顺序，location 依次 0,1,2…（与 MC 一致）；
 * uniform 默认值在链接后立刻写入，场景再用 {@link #set} 覆盖。</p>
 */
final class PreviewShader {

    /** 每个 uniform 的 json 声明（名字/类型/数量/默认值/GL location）。 */
    private static final class Uniform {
        final String name;
        final String type;
        final int count;
        final int location;
        float[] values;

        Uniform(String name, String type, int count, int location, float[] values) {
            this.name = name;
            this.type = type;
            this.count = count;
            this.location = location;
            this.values = values;
        }
    }

    final String name;
    final int program;
    final List<String> attributes;
    /** 每个顶点的 float 数（所有 attribute 的 size 之和）。 */
    final int vertexFloats;
    /** 与 {@link #attributes} 对应的每个 attribute 的分量数。 */
    final int[] attributeSizes;

    private final Map<String, Uniform> uniforms = new LinkedHashMap<>();
    private final List<String> samplers = new ArrayList<>();
    private final Map<String, PreviewTexture> samplerBindings = new LinkedHashMap<>();
    private final Map<String, Integer> samplerUnits = new LinkedHashMap<>();
    private int srcBlend = GL11.GL_SRC_ALPHA;
    private int dstBlend = GL11.GL_ONE_MINUS_SRC_ALPHA;

    private PreviewShader(String name, int program, List<String> attributes, int[] sizes) {
        this.name = name;
        this.program = program;
        this.attributes = attributes;
        this.attributeSizes = sizes;
        int total = 0;
        for (int size : sizes) {
            total += size;
        }
        this.vertexFloats = total;
    }

    static PreviewShader load(Path shaderDir, String shaderName) throws IOException {
        Path jsonFile = shaderDir.resolve(shaderName + ".json");
        if (!Files.isRegularFile(jsonFile)) {
            throw new IOException("找不到着色器 json：" + jsonFile);
        }
        Map<String, Object> def = MiniJson.load(jsonFile);

        String vertexRef = MiniJson.getStr(def, "vertex", shaderName);
        String fragmentRef = MiniJson.getStr(def, "fragment", shaderName);
        Path vsh = shaderDir.resolve(stripNamespace(vertexRef) + ".vsh");
        Path fsh = shaderDir.resolve(stripNamespace(fragmentRef) + ".fsh");
        String vertexSource = Files.readString(vsh);
        String fragmentSource = Files.readString(fsh);

        List<String> attributes = new ArrayList<>();
        List<Object> attrJson = MiniJson.getArr(def, "attributes");
        if (attrJson != null) {
            for (Object o : attrJson) {
                attributes.add(MiniJson.str(o));
            }
        }
        int[] sizes = new int[attributes.size()];
        for (int i = 0; i < attributes.size(); i++) {
            sizes[i] = attributeSize(attributes.get(i));
        }

        int vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource, vsh.toString());
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource, fsh.toString());
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        for (int i = 0; i < attributes.size(); i++) {
            GL20.glBindAttribLocation(program, i, attributes.get(i));
        }
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            String log = GL20.glGetProgramInfoLog(program);
            throw new IOException("链接失败 " + shaderName + ":\n" + log);
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);

        PreviewShader shader = new PreviewShader(shaderName, program, attributes, sizes);
        shader.readUniforms(def);
        shader.readSamplers(def);
        shader.readBlend(def);
        shader.applyDefaults();
        return shader;
    }

    private void readUniforms(Map<String, Object> def) {
        GL20.glUseProgram(program);
        List<Object> uniformsJson = MiniJson.getArr(def, "uniforms");
        if (uniformsJson == null) {
            return;
        }
        for (Object o : uniformsJson) {
            Map<String, Object> u = MiniJson.obj(o);
            String uName = MiniJson.getStr(u, "name", "");
            String type = MiniJson.getStr(u, "type", "float");
            int count = MiniJson.get(u, "count") == null ? 1 : MiniJson.i(MiniJson.get(u, "count"));

            float[] values = new float[count];
            List<Object> valueJson = MiniJson.getArr(u, "values");
            if (valueJson != null) {
                for (int i = 0; i < valueJson.size() && i < count; i++) {
                    Object v = valueJson.get(i);
                    if (v instanceof Number) {
                        values[i] = MiniJson.f(v);
                    }
                }
            }
            int location = GL20.glGetUniformLocation(program, uName);
            uniforms.put(uName, new Uniform(uName, type, count, location, values));
        }
    }

    private void readSamplers(Map<String, Object> def) {
        List<Object> samplersJson = MiniJson.getArr(def, "samplers");
        if (samplersJson == null) {
            return;
        }
        for (int i = 0; i < samplersJson.size(); i++) {
            Map<String, Object> s = MiniJson.obj(samplersJson.get(i));
            String sName = MiniJson.getStr(s, "name", "");
            samplers.add(sName);
            samplerUnits.put(sName, i);
        }
    }

    private void readBlend(Map<String, Object> def) {
        Map<String, Object> blend = MiniJson.getObj(def, "blend");
        if (blend == null) {
            return;
        }
        srcBlend = blendFactor(MiniJson.getStr(blend, "srcrgb", "srcalpha"));
        dstBlend = blendFactor(MiniJson.getStr(blend, "dstrgb", "1-srcalpha"));
    }

    private void applyDefaults() {
        GL20.glUseProgram(program);
        for (Uniform u : uniforms.values()) {
            if (u.location < 0 || u.values == null || u.values.length == 0) {
                continue;
            }
            if ("matrix4x4".equals(u.type) && u.values.length >= 16) {
                glUniformMatrix(u.location, u.values);
            } else if ("int".equals(u.type)) {
                GL20.glUniform1i(u.location, (int) u.values[0]);
            } else {
                glUniformFloats(u.location, u.values);
            }
        }
        // sampler 的纹理单元是固定的：json samplers 的顺序 = unit 0,1,2…
        for (Map.Entry<String, Integer> e : samplerUnits.entrySet()) {
            int loc = GL20.glGetUniformLocation(program, e.getKey());
            if (loc >= 0) {
                GL20.glUniform1i(loc, e.getValue());
            }
        }
        // 默认 ProjMat / ModelViewMat 用单位阵；画之前 canvas 会再写一次真实矩阵
        setMat4("ModelViewMat", IDENTITY);
    }

    /** 场景里设置 uniform（1/2/3/4 个 float；json 里的 count 决定走哪个 glUniformNf）。 */
    void set(String uniformName, float... values) {
        Uniform u = uniforms.get(uniformName);
        if (u == null || u.location < 0 || values.length == 0) {
            return;
        }
        // 必须显式切 program：GL 的 glUniform* 作用于"当前 program"，
        // 而场景可能刚画过另一支 shader（缓存命中时更容易漏）—— 不切会把值写进上一次的 program。
        GL20.glUseProgram(program);
        glUniformFloats(u.location, values);
    }

    void setMat4(String uniformName, float[] columnMajor16) {
        Uniform u = uniforms.get(uniformName);
        if (u == null || u.location < 0) {
            return;
        }
        GL20.glUseProgram(program);
        glUniformMatrix(u.location, columnMajor16);
    }

    /**
     * 给 json 里声明的 sampler 绑定一张贴图。真正 glBindTexture 在绘制前由
     * {@link #bindSamplers()} 执行 —— 与 MC 的 {@code ShaderInstance.setSampler}
     * 时机一致（apply 时绑定），避免"设了但被后一次绘制覆盖"。
     */
    void setSampler(String samplerName, PreviewTexture texture) {
        samplerBindings.put(samplerName, texture);
    }

    void bindSamplers() {
        for (String samplerName : samplers) {
            PreviewTexture texture = samplerBindings.get(samplerName);
            Integer unit = samplerUnits.get(samplerName);
            if (texture == null || unit == null) {
                continue;
            }
            texture.bind(unit);
        }
    }

    void applyBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(srcBlend, dstBlend);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    void applyAlphaBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    // ── 内部 ────────────────────────────────────────────────────────

    private static final float[] IDENTITY = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1};

    private static void glUniformFloats(int location, float[] values) {
        switch (values.length) {
            case 1 -> GL20.glUniform1f(location, values[0]);
            case 2 -> GL20.glUniform2f(location, values[0], values[1]);
            case 3 -> GL20.glUniform3f(location, values[0], values[1], values[2]);
            default -> GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
        }
    }

    private static void glUniformMatrix(int location, float[] columnMajor16) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(columnMajor16).flip();
        GL20.glUniformMatrix4fv(location, false, buffer);
    }

    private static int compile(int type, String source, String label) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IOException("编译失败 " + label + ":\n" + log);
        }
        return shader;
    }

    private static String stripNamespace(String reference) {
        int colon = reference.indexOf(':');
        return colon < 0 ? reference : reference.substring(colon + 1);
    }

    private static int attributeSize(String attributeName) {
        return switch (attributeName) {
            case "UV0", "UV1", "UV2" -> 2;
            case "Color" -> 4;
            case "Normal" -> 3;
            default -> 3;   // Position 等
        };
    }

    private static int blendFactor(String token) {
        String t = token.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (t) {
            case "zero" -> GL11.GL_ZERO;
            case "one" -> GL11.GL_ONE;
            case "srccolor" -> GL11.GL_SRC_COLOR;
            case "1srccolor", "oneminussrccolor" -> GL11.GL_ONE_MINUS_SRC_COLOR;
            case "srcalpha" -> GL11.GL_SRC_ALPHA;
            case "1srcalpha", "oneminussrcalpha" -> GL11.GL_ONE_MINUS_SRC_ALPHA;
            case "dstcolor" -> GL11.GL_DST_COLOR;
            case "1dstcolor", "oneminusdstcolor" -> GL11.GL_ONE_MINUS_DST_COLOR;
            case "dstalpha" -> GL11.GL_DST_ALPHA;
            case "1dstalpha", "oneminusdstalpha" -> GL11.GL_ONE_MINUS_DST_ALPHA;
            default -> GL11.GL_ONE;
        };
    }
}
