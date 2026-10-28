import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 离屏编译一对 .vsh/.fsh 并链接，用来在没有游戏的情况下验证 GLSL。
 * 用法: java ShaderCompileTest <name.vsh> <name.fsh> [<pair2.vsh> <pair2.fsh> ...]
 */
public class ShaderCompileTest {

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) {
            System.out.println("RESULT=NO_GLFW");
            return;
        }
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        long window = GLFW.glfwCreateWindow(64, 64, "glsl-check", 0L, 0L);
        if (window == 0L) {
            System.out.println("RESULT=NO_WINDOW");
            GLFW.glfwTerminate();
            return;
        }
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL_VERSION=" + GL20.glGetString(GL20.GL_VERSION));
        System.out.println("GLSL=" + GL20.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));

        boolean allOk = true;
        for (int i = 0; i + 1 < args.length; i += 2) {
            allOk &= checkPair(args[i], args[i + 1]);
        }

        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println("RESULT=" + (allOk ? "ALL_OK" : "FAILED"));
        System.exit(allOk ? 0 : 1);
    }

    private static boolean checkPair(String vsPath, String fsPath) throws Exception {
        int vs = compile(vsPath, GL20.GL_VERTEX_SHADER);
        int fs = compile(fsPath, GL20.GL_FRAGMENT_SHADER);
        if (vs == 0 || fs == 0) {
            if (vs != 0) GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
            return false;
        }
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vs);
        GL20.glAttachShader(prog, fs);
        GL20.glLinkProgram(prog);
        boolean ok = GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL20.GL_TRUE;
        String log = GL20.glGetProgramInfoLog(prog).trim();
        System.out.println((ok ? "LINK OK   " : "LINK FAIL ") + vsPath + " + " + fsPath);
        if (!log.isEmpty()) {
            System.out.println("  " + log.replace("\n", "\n  "));
        }
        GL20.glDeleteProgram(prog);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return ok;
    }

    private static int compile(String path, int type) throws Exception {
        String source = Files.readString(Path.of(path));
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        boolean ok = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_TRUE;
        String log = GL20.glGetShaderInfoLog(shader).trim();
        System.out.println((ok ? "COMPILE OK   " : "COMPILE FAIL ") + path);
        if (!log.isEmpty()) {
            System.out.println("  " + log.replace("\n", "\n  "));
        }
        if (!ok) {
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
