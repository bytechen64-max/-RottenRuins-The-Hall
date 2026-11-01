import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** 与实机相同的 SDF、布局和波形线带；只模拟容器槽位与交互状态，不运行 Minecraft。 */
public final class SceneSourceMachines {
    private static final int TEXT=0xFFE3E8EE,MUTED=0xFF92A1B4,GOLD=0xFFE7C46A;
    private static final String[] ELEMENTS={"Light","Fire","Water","Nature","Death","Void","Mysterious"};
    private static final int[] COLORS={0xFFF4AC,0xFF7843,0x53BEFF,0x78D98A,0xBE8BDA,0x8B85FF,0xE7A7EF};
    private static final String[][] WINDOWS={{"9-10","1-2","2-3","+"},{"7-8","2-3","7-8","+"},{"5-6","4-5","4-5","+"},{"3-4","3-4","4-5","+"},{"2-3","7-8","4-6","+"},{"0-1","6-7","1-3","-"},{"0-1","8-10","0-1","+"}};

    public static void render(PreviewCanvas c,float time) throws Exception {
        c.shader("sdf_ui");
        filter(c,time,0); c.save("source_filter_manual");
        filter(c,time,1); c.save("source_filter_quick");
        filter(c,time,2); c.save("source_filter_bands");
        collector(c); c.save("source_collector_modern");
        filter(c,time,1);
    }

    private static void panel(PreviewCanvas c,float x,float y,float width,float height,float radius,int top,int bottom,int border) {
        SdfShapes.roundRect(c,x,y,width,height,radius,top,bottom,border,0.8F);
    }

    private static void shell(PreviewCanvas c,int x,int y,int width,int height,String title) {
        panel(c,x-4,y-2,width+8,height+8,10,0x30000000,0x60000000,0);
        panel(c,x,y,width,height,8,0xFA131E30,0xFA080D18,0x777F795F);
        SdfShapes.line(c,x+16,y+28,x+width-16,y+28,0.35F,0x667C775C);
        c.text(title,x+16,y+12,TEXT,false);
    }

    private static void filter(PreviewCanvas c,float time,int view) throws Exception {
        c.backdrop();
        int width=Math.min(372,c.width-16),height=230,x=(c.width-width)/2,y=(c.height-height)/2;
        shell(c,x,y,width,height,"Source Charge Filter");
        int tabWidth=(width-40)/3;
        String[] tabs={"Manual","Quick tune","Band table"};
        for(int i=0;i<3;i++) {
            int xx=x+16+i*(tabWidth+4);
            panel(c,xx,y+36,tabWidth,20,4,i==view?0x443C5061:0x44131F30,i==view?0x44344750:0x440B1422,i==view?0xCCE7C46A:0x3346576B);
            c.textCentered(tabs[i],xx+tabWidth/2,y+42,i==view?GOLD:TEXT,false);
        }
        if(view==2) {
            panel(c,x+16,y+66,width-32,150,5,0xC0091423,0xC00B1420,0x44476078);
            int inner=width-32;
            int[] columns={Math.round(inner*0.29F),Math.round(inner*0.48F),Math.round(inner*0.66F),Math.round(inner*0.85F)};
            String[] labels={"fc","B","G","phi"};
            for(int i=0;i<4;i++) c.text(labels[i],x+16+columns[i],y+76,GOLD,false);
            for(int i=0;i<7;i++) {
                c.text(ELEMENTS[i],x+25,y+96+i*16,0xFF000000|COLORS[i],false);
                for(int j=0;j<4;j++) c.text(WINDOWS[i][j],x+16+columns[j],y+96+i*16,TEXT,false);
            }
            return;
        }
        wave(c,time,x+16,y+66,width-32,62);
        c.text("FIELD OSCILLOSCOPE",x+26,y+73,MUTED,false);
        c.text("Element: Water",x+17,y+137,0xFF53BEFF,false);
        c.textRight("Power 15/15",x+width-17,y+137,MUTED,false);
        if(view==0) {
            int sliderWidth=(width-42)/2;
            String[] labels={"Frequency fc","Bandwidth B","Gain G","Phase phi"};
            String[] values={"5.5","4.5","4.5","+"};
            float[] positions={0.55F,0.45F,0.45F,0};
            for(int i=0;i<4;i++) {
                int xx=x+16+(i%2)*(sliderWidth+10),yy=y+155+(i/2)*35;
                panel(c,xx,yy,sliderWidth,28,4,0xFF142236,0xFF0D1828,0x44465970);
                c.text(labels[i],xx+8,yy+5,MUTED,false); c.textRight(values[i],xx+sliderWidth-8,yy+5,TEXT,false);
                float end=xx+9+(sliderWidth-18)*positions[i];
                SdfShapes.line(c,xx+9,yy+21,xx+sliderWidth-9,yy+21,1,0xFF30435B);
                if(positions[i]>0) SdfShapes.line(c,xx+9,yy+21,end,yy+21,1,0xFF72CAD6);
                SdfShapes.disc(c,end,yy+21,3,0xFFF4E4B4,GOLD);
            }
        } else {
            int tileWidth=(width-50)/4;
            for(int i=0;i<8;i++) {
                int xx=x+17+(i%4)*(tileWidth+6),yy=y+155+(i/4)*35;
                int accent=i==7?MUTED:0xFF000000|COLORS[i];
                panel(c,xx,yy,tileWidth,27,5,i==2?0xFF203446:0xFF152334,0xFF0C1624,i==2?accent:0x5546556A);
                if(i==2) SdfShapes.line(c,xx+9,yy+24,xx+tileWidth-9,yy+24,0.5F,accent);
                String label=i==7?"Fine tune":ELEMENTS[i];
                while(c.textWidth(label)>tileWidth-12) label=label.substring(0,label.length()-1);
                c.textCentered(label,xx+tileWidth/2,yy+9,accent,false);
            }
        }
    }

    private static void wave(PreviewCanvas c,float time,int x,int y,int width,int height) throws Exception {
        panel(c,x,y,width,height,5,0xFF06111C,0xFF091421,0x55506C7E);
        for(int gx=16;gx<width-8;gx+=24) SdfShapes.line(c,x+gx,y+18,x+gx,y+height-6,0.25F,0x282F5770);
        for(int gy=24;gy<height-4;gy+=16) SdfShapes.line(c,x+8,y+gy,x+width-8,y+gy,0.25F,0x282F5770);
        SdfShapes.line(c,x+8,y+36,x+width-8,y+36,0.3F,0x60445D72);
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{Path.of("build/circuit-test").toUri().toURL()})) {
            Class<?> source=Class.forName("org.bytechen.rottenruins.overworld.energy.SourceWaveform",true,loader);
            Method points=source.getMethod("points",int.class,int.class,int.class,boolean.class,double.class,int.class,float.class,float.class);
            Method mesh=source.getMethod("mesh",float[].class,float.class,float.class,int.class);
            float[] samples=(float[])points.invoke(null,55,45,45,false,(double)time,width,(float)width-16,(float)height-18);
            float[] vertices=(float[])mesh.invoke(null,samples,0.6F,1.0F/Math.max(1,c.pixelScale()),0xFF53BEFF);
            for(int i=0;i<vertices.length;i+=6) { vertices[i]+=x+8;vertices[i+1]+=y+14; }
            c.fillMesh(vertices,vertices.length,vertices.length/6);
        }
    }

    private static void collector(PreviewCanvas c) {
        c.backdrop();
        int x=(c.width-220)/2,y=(c.height-200)/2;
        shell(c,x,y,220,200,"Charge Collector");
        c.textCentered("Charging bottle",x+110,y+29,MUTED,false);
        c.text("Empty",x+48,y+40,MUTED,false); c.text("Filled",x+143,y+40,MUTED,false);
        slot(c,x+55,y+52);slot(c,x+149,y+52);
        for(int row=0;row<3;row++) for(int column=0;column<9;column++) slot(c,x+29+column*18,y+112+row*18);
        for(int column=0;column<9;column++) slot(c,x+29+column*18,y+170);
        c.blit(c.texture("textures/item/mana_bottle.png"),x+55,y+52,16,16);
        c.blit(c.texture("textures/item/water_mana_bottle.png"),x+149,y+52,16,16);
        panel(c,x+81,y+54,58,12,6,0xFF06101A,0xFF06101A,0x55405C6F);
        panel(c,x+83,y+56,54*0.68F,8,4,0xFF94DFE7,0xFF3CA4BE,0);
        c.textCentered("68%",x+110,y+73,TEXT,false);
        c.textCentered("Element: Water",x+110,y+87,0xFF53BEFF,false);
        c.text("Inventory",x+29,y+100,MUTED,false);
    }

    private static void slot(PreviewCanvas c,int x,int y) { panel(c,x-1,y-1,18,18,3,0xFF08121F,0xFF101D2D,0x66566B81); }
}
