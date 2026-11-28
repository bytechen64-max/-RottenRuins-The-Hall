import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.bytechen.rottenruins.client.combat.slash.trail.SlashTrailGeometry;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.file.*;
import static org.lwjgl.opengl.GL33C.*;

/** Hidden GPU context: compile the real procedural shader and draw both trail variants to PNG. */
public final class SlashTrailPreview {
    private static final int W = 1920, H = 600;
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(W, H, "slash-offscreen-test", 0, 0);
        if (window == 0) throw new IllegalStateException("OpenGL window unavailable");
        GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
        System.out.println("GL=" + glGetString(GL_VERSION));
        Path shaders = Path.of(args[0]);
        String vertex = Files.readString(shaders.resolve("slash_trail.vsh"));
        int procedural = program(vertex, Files.readString(shaders.resolve("slash_trail.fsh")));
        int textureProgram = program(vertex, "#version 150\nin vec4 vertexColor; in vec2 texCoord; uniform sampler2D Sampler0; uniform vec4 ColorModulator; out vec4 fragColor; void main(){fragColor=texture(Sampler0,texCoord)*vertexColor*ColorModulator;}");
        int fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        int output = glGenTextures(); glBindTexture(GL_TEXTURE_2D, output);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("FBO incomplete");
        var sprite = ImageIO.read(Path.of(args[1]).toFile());
        ByteBuffer pixels = MemoryUtil.memAlloc(sprite.getWidth() * sprite.getHeight() * 4);
        for (int y = 0; y < sprite.getHeight(); y++) for (int x = 0; x < sprite.getWidth(); x++) {
            int c = sprite.getRGB(x,y); pixels.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>>24));
        }
        pixels.flip(); int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, sprite.getWidth(), sprite.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        MemoryUtil.memFree(pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glViewport(0,0,W,H); glClearColor(.055F,.07F,.105F,1); glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
        for (int row=0;row<2;row++) for(int pose=0;pose<4;pose++) {
            glViewport(pose*480,row==0?300:0,480,300);
            if(args.length>3) drawTracked(row==0?textureProgram:procedural,texture,pose,row==0?new float[]{1,.71F,.44F}:new float[]{.60F,.82F,1},args.length>4);
            else drawWorld(row==0?textureProgram:procedural,texture,pose,row==0?2:2.5F,row==0?.48F:.62F,
                    row==0?116:128,row==0?new float[]{1,.71F,.44F}:new float[]{.60F,.82F,1});
        }
        ByteBuffer read = MemoryUtil.memAlloc(W*H*4); glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,read);
        BufferedImage image = new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB); int colored = 0;
        for (int y=0;y<H;y++) for(int x=0;x<W;x++) {
            int i=((H-1-y)*W+x)*4; int r=read.get(i)&255,g=read.get(i+1)&255,b=read.get(i+2)&255;
            image.setRGB(x,y,0xFF000000|r<<16|g<<8|b);
            if (r>70 || g>70 || b>70) colored++;
        }
        if(args.length>3)for(int row=0;row<2;row++)for(int pose=0;pose<4;pose++){
            int visible=0;for(int y=row*300;y<(row+1)*300;y++)for(int x=pose*480;x<(pose+1)*480;x++){
                int pixel=image.getRGB(x,y);if(((pixel>>16)&255)>70||((pixel>>8)&255)>70||(pixel&255)>70)visible++;
            }
            if(visible<80)throw new AssertionError("Invisible tracked trail: material="+row+" pose="+pose+" pixels="+visible);
            System.out.println("tracked material="+row+" pose="+pose+" visiblePixels="+visible);
        }
        MemoryUtil.memFree(read);
        if (colored<1000) throw new AssertionError("Trail rendering produced too few visible pixels");
        var graphics=image.createGraphics(); graphics.setColor(new Color(225,235,250));
        String[] names={"R -> L","L -> R","UPPER RIGHT -> LOWER LEFT","UPPER LEFT -> LOWER RIGHT"};
        for(int row=0;row<2;row++)for(int pose=0;pose<4;pose++) {
            int cx=pose*480+240,cy=row*300+150;
            graphics.setColor(new Color(70,80,100));
            graphics.drawLine(cx-5,cy,cx+5,cy);graphics.drawLine(cx,cy-5,cx,cy+5);
            graphics.setColor(new Color(225,235,250));
            graphics.drawString((row==0?"TEXTURE / WORLD VIEW | ":"SHADER / WORLD VIEW | ")+names[pose],pose*480+12,row*300+20);
        }
        graphics.dispose();
        Path out=Path.of(args[2]); Files.createDirectories(out.toAbsolutePath().getParent()); ImageIO.write(image,"png",out.toFile());
        System.out.println("PASS: texture + production procedural GLSL compiled, linked and rendered; coloredPixels="+colored+"; "+out);
        GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
    }
    private static void drawTracked(int program,int texture,int pose,float[] color,boolean affine){
        glUseProgram(program);
        glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,new Matrix4f().get(new float[16]));
        glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,new Matrix4f().perspective((float)Math.toRadians(70),1.6F,.05F,100).get(new float[16]));
        glUniform4f(glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
        glUniform4f(glGetUniformLocation(program,"TrailShape"),.06F,.3F,.42F,.4F);
        glUniform4f(glGetUniformLocation(program,"TrailStyle"),1.25F,1.2F,1.6F,0);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glUniform1i(glGetUniformLocation(program,"Sampler0"),0);
        float dx=pose==0?-1:pose==1?1:pose==2?-.7071F:.7071F,dy=pose<2?0:-.7071F;
        java.util.List<java.util.List<org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point>> sections=new java.util.ArrayList<>();
        for(int i=0;i<=40;i++){
            float distance=-1.05F+2.1F*i/40,old=distance-.0525F;
            var a=java.util.List.of(new org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point(dx*distance,dy*distance,-.85),new org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point(dx*distance,dy*distance,-1.85));
            var b=java.util.List.of(new org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point(dx*old,dy*old,-.85),new org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point(dx*old,dy*old,-1.85));
            if(affine){
                // Ten real frames, four interpolated samples each: unequal projection scale + oblique blade.
                int frame=i/4;float t=(i%4)/4F;
                Matrix4f start=affineFrame(dx,dy,-1.05F+2.1F*frame/10);
                Matrix4f end=affineFrame(dx,dy,-1.05F+2.1F*(frame+1)/10);
                Matrix4f sample=org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailTransforms.interpolate(start,end,t);
                Matrix4f prev=org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailTransforms.interpolate(start,end,Math.max(0,t-.01F));
                if(t==0)prev=affineFrame(dx,dy,-1.05F+2.1F*frame/10-.01F);
                a=affinePoints(sample);b=affinePoints(prev);
            }
            sections.add(org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrailRibbonGeometry.section(a,b,true));
        }
        float[] data=new float[40*8*6*9];int index=0;
        for(int i=0;i<40;i++)for(int j=0;j<8;j++){
            var points=java.util.List.of(sections.get(i).get(j),sections.get(i).get(j+1),sections.get(i+1).get(j+1),sections.get(i+1).get(j));
            for(int k:new int[]{0,1,2,0,2,3}){
                var point=points.get(k);data[index++]=(float)point.x();data[index++]=(float)point.y();data[index++]=(float)point.z();
                data[index++]=color[0];data[index++]=color[1];data[index++]=color[2];data[index++]=.35F+.65F*i/40;
                data[index++]=(k<2?i:i+1)/40F;data[index++]=(k==1||k==2?j+1:j)/8F;
            }
        }
        int vao=glGenVertexArrays(),vbo=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,data,GL_STATIC_DRAW);
        attribute(program,"Position",3,0);attribute(program,"Color",4,12);attribute(program,"UV0",2,28);glDrawArrays(GL_TRIANGLES,0,data.length/9);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);
    }
    private static Matrix4f affineFrame(float dx,float dy,float distance){
        return new Matrix4f().translation(dx*distance,dy*distance,-2.0F).scale(1.4F,1.4F,1).rotateZ(.7F).rotateY(.9F).scale(.65F,.65F,.4F);
    }
    private static java.util.List<org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point> affinePoints(Matrix4f matrix){
        Vector3f root=matrix.transformPosition(new Vector3f(0,0,0)),tip=matrix.transformPosition(new Vector3f(.85F,.9F,.5F));
        return java.util.List.of(new org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point(root.x,root.y,root.z),new org.bytechen.rottenruins.core.combat.slash.TrailHistory.Point(tip.x,tip.y,tip.z));
    }
    private static void drawWorld(int program,int texture,int pose,float radius,float width,float arc,float[] color) {
        glUseProgram(program); float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        float[] view=new Matrix4f().lookAt(new Vector3f(.35F,.85F,.35F),new Vector3f(0,0,-2.1F),new Vector3f(0,1,0)).get(new float[16]);
        glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,view);
        float[] projection=new Matrix4f().perspective((float)Math.toRadians(70),1.6F,.05F,100).get(new float[16]);
        glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,projection);
        glUniform4f(glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
        glUniform4f(glGetUniformLocation(program,"TrailShape"),.06F,.3F,.42F,.4F);
        glUniform4f(glGetUniformLocation(program,"TrailStyle"),1.25F,1.2F,1.6F,0);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glUniform1i(glGetUniformLocation(program,"Sampler0"),0);
        float dx=pose==0?-1:pose==1?1:pose==2?-(float)Math.sqrt(.5):(float)Math.sqrt(.5);
        float dy=pose<2?0:-(float)Math.sqrt(.5);
        var frame=SlashTrailGeometry.frame(new Vector3f(0,0,-1),new Vector3f(dx,dy,0));
        float[] data=new float[80*6*9];int index=0;
        for(int i=0;i<80;i++) {
            float u=i/80F,v=(i+1)/80F;
            double a=Math.toRadians(-arc/2+arc*u),b=Math.toRadians(-arc/2+arc*v);
            Vector3f[] corners={SlashTrailGeometry.point(frame,a,radius,-width/2),SlashTrailGeometry.point(frame,a,radius,width/2),
                    SlashTrailGeometry.point(frame,b,radius,width/2),SlashTrailGeometry.point(frame,b,radius,-width/2)};
            for(int k:new int[]{0,1,2,0,2,3}) {
                data[index++]=corners[k].x;data[index++]=corners[k].y;data[index++]=corners[k].z;
                data[index++]=color[0];data[index++]=color[1];data[index++]=color[2];data[index++]=1;
                data[index++]=k<2?u:v;data[index++]=k==1||k==2?1:0;
            }
        }
        int vao=glGenVertexArrays(),vbo=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,data,GL_STATIC_DRAW);
        attribute(program,"Position",3,0);attribute(program,"Color",4,12);attribute(program,"UV0",2,28);
        glDrawArrays(GL_TRIANGLES,0,data.length/9);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);
    }
    private static void attribute(int p,String name,int size,long offset) {
        int a=glGetAttribLocation(p,name);if(a<0)return;glEnableVertexAttribArray(a);glVertexAttribPointer(a,size,GL_FLOAT,false,36,offset);
    }
    private static int program(String vs,String fs) {
        int v=compile(GL_VERTEX_SHADER,vs),f=compile(GL_FRAGMENT_SHADER,fs),p=glCreateProgram();
        glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);
        if(glGetProgrami(p,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(p));
        glDeleteShader(v);glDeleteShader(f);return p;
    }
    private static int compile(int type,String source) {
        int shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==0)throw new IllegalStateException(glGetShaderInfoLog(shader));return shader;
    }
}
