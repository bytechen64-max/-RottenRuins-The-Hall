import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Two design proposals, deliberately separate from the live screen and baseline scene. */
public final class SceneSourceMachineStyles {
    private static final int INK=0xFF404040, WHITE=0xFFE8E8D8, WATER=0xFF53BEFF;
    private static final String[] ELEMENTS={"Light","Fire","Water","Nature","Death","Void","Mysterious"};
    private static final int[] COLORS={0xFFF4AC,0xFF7843,0x53BEFF,0x78D98A,0xBE8BDA,0x8B85FF,0xE7A7EF};
    private static final String[][] WINDOWS={{"9-10","1-2","2-3","+"},{"7-8","2-3","7-8","+"},{"5-6","4-5","4-5","+"},{"3-4","3-4","4-5","+"},{"2-3","7-8","4-6","+"},{"0-1","6-7","1-3","-"},{"0-1","8-10","0-1","+"}};
    private static final Map<String,PreviewTexture> TEXTURES=new HashMap<>();
    private static float[] samples;

    public static void render(PreviewCanvas c,float time) throws Exception {
        // Use the actual project's waveform sampling, rather than a decorative sine substitute.
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{Path.of("build/circuit-test").toUri().toURL()})) {
            Class<?> source=Class.forName("org.bytechen.rottenruins.overworld.energy.SourceWaveform",true,loader);
            samples=(float[])source.getMethod("points",int.class,int.class,int.class,boolean.class,double.class,int.class,float.class,float.class)
                    .invoke(null,55,45,45,false,(double)time,324,323F,42F);
        }
        for(int style=0;style<2;style++) {
            String prefix=style==0?"a_drawn":"b_texture";
            for(int view=0;view<3;view++) {
                c.backdrop();
                filter(c,(c.width-372)/2,(c.height-230)/2,style,view);
                c.save(prefix+"_filter_"+new String[]{"manual","quick","bands"}[view]);
            }
            c.backdrop(); collector(c,(c.width-220)/2,(c.height-200)/2,style);
            c.save(prefix+"_collector");
        }
        // Comparison at original GUI coordinates: 800x560 or larger, no fractional scaling.
        c.backdrop();
        int left=(c.width-768)/2,right=left+396;
        c.text("A / DRAWN - VANILLA BEVELS",left,18,WHITE,true);
        c.text("B / TEXTURED - EDITABLE PIXEL SKIN",right,18,WHITE,true);
        filter(c,left,40,0,0); filter(c,right,40,1,0);
        collector(c,left+76,302,0); collector(c,right+76,302,1);
        c.textCentered("Same layout, slots and tuning values / 1 GUI pixel = 1 texture pixel",c.width/2,526,WHITE,true);
    }

    private static PreviewTexture texture(String name) throws Exception {
        if(!TEXTURES.containsKey(name)) TEXTURES.put(name,PreviewTexture.load(Path.of("src/main/resources/assets/rotten_ruins/textures/gui/source_machine/"+name+".png")));
        return TEXTURES.get(name);
    }

    private static void bevel(PreviewCanvas c,int x,int y,int w,int h,int face,boolean sunken) {
        c.fill(x,y,w,h,0xFF373737);
        c.fill(x+1,y+1,w-2,h-2,face);
        int light=sunken?0xFF555555:0xFFFFFFFF,dark=sunken?0xFFECECEC:0xFF555555;
        c.fill(x+1,y+1,w-2,1,light); c.fill(x+1,y+1,1,h-2,light);
        c.fill(x+1,y+h-2,w-2,1,dark); c.fill(x+w-2,y+1,1,h-2,dark);
    }

    private static void shell(PreviewCanvas c,int x,int y,int w,int h,String title) {
        bevel(c,x,y,w,h,0xFFC6C6C6,false);
        c.fill(x+3,y+3,w-6,1,0xFFDBDBDB);
        c.text(title,x+16,y+12,INK,false);
    }

    /** Nine-slice only the button face; preserve two-pixel borders at any width. */
    private static void button(PreviewCanvas c,int x,int y,int w,int h,int style,boolean selected) throws Exception {
        if(style==0) { bevel(c,x,y,w,h,selected?0xFF777777:0xFF929292,selected); return; }
        PreviewTexture t=texture("widgets");
        int offset=selected?20:0;
        int[] src={0,2,18,20},dx={0,2,w-2,w},dy={0,2,h-2,h};
        for(int row=0;row<3;row++) for(int col=0;col<3;col++)
            c.blit(t,x+dx[col],y+dy[row],dx[col+1]-dx[col],dy[row+1]-dy[row],
                    (offset+src[col])/80F,src[row]/20F,(offset+src[col+1])/80F,src[row+1]/20F);
    }

    private static void filter(PreviewCanvas c,int x,int y,int style,int view) throws Exception {
        if(style==0) shell(c,x,y,372,230,"Source Charge Filter");
        else {
            c.blit(texture("filter_"+new String[]{"manual","quick","bands"}[view]),x,y,372,230);
            c.text("Source Charge Filter",x+16,y+12,INK,false);
        }
        String[] tabs={"Manual","Quick tune","Band table"};
        for(int i=0;i<3;i++) {
            int xx=x+16+i*114;
            button(c,xx,y+36,110,20,style,i==view);
            c.textCentered(tabs[i],xx+55,y+42,WHITE,true);
        }
        if(view==2) {
            if(style==0) bevel(c,x+16,y+66,340,150,0xFF303030,true);
            int[] cols={99,163,224,289};
            String[] heads={"fc","B","G","phi"};
            for(int j=0;j<4;j++) c.text(heads[j],x+16+cols[j],y+76,WHITE,false);
            for(int i=0;i<7;i++) {
                c.text(ELEMENTS[i],x+25,y+96+i*16,0xFF000000|COLORS[i],false);
                for(int j=0;j<4;j++) c.text(WINDOWS[i][j],x+16+cols[j],y+96+i*16,WHITE,false);
            }
            return;
        }
        if(style==0) {
            bevel(c,x+16,y+66,340,62,0xFF202725,true);
            for(int gx=32;gx<348;gx+=24) c.fill(x+gx,y+84,1,38,0xFF34423A);
            for(int gy=90;gy<125;gy+=16) c.fill(x+24,y+gy,324,1,0xFF34423A);
        }
        c.text("FIELD OSCILLOSCOPE",x+26,y+73,0xFFB9C3AF,false);
        for(int i=0;i<samples.length;i+=2) c.fill(x+24+(int)samples[i],y+80+(int)samples[i+1],1,1,WATER);
        // Square swatch keeps colored element text readable on a light background.
        c.fill(x+17,y+137,8,8,WATER);
        c.text("Element: Water",x+29,y+137,INK,false);
        c.textRight("Power 15/15",x+355,y+137,INK,false);
        if(view==0) {
            String[] labels={"Frequency fc","Bandwidth B","Gain G","Phase phi"},values={"5.5","4.5","4.5","+"};
            float[] positions={.55F,.45F,.45F,0};
            for(int i=0;i<4;i++) {
                int xx=x+16+(i%2)*175,yy=y+155+(i/2)*35;
                if(style==0) {
                    c.text(labels[i],xx+2,yy+3,INK,false); c.textRight(values[i],xx+163,yy+3,INK,false);
                    bevel(c,xx+9,yy+19,147,5,0xFF656565,true);
                } else {
                    c.text(labels[i],xx+8,yy+5,INK,false); c.textRight(values[i],xx+157,yy+5,INK,false);
                }
                int cx=xx+9+Math.round(147*positions[i]);
                if(style==0) bevel(c,cx-4,yy+16,8,10,0xFFC6C6C6,false);
                else c.blit(texture("widgets"),cx-4,yy+16,8,10,60/80F,0,68/80F,.5F);
            }
        } else {
            for(int i=0;i<8;i++) {
                int xx=x+17+(i%4)*86,yy=y+155+(i/4)*35;
                button(c,xx,yy,80,27,style,i==2);
                if(i<7) c.fill(xx+5,yy+9,4,8,0xFF000000|COLORS[i]);
                String label=i==7?"Fine tune":ELEMENTS[i];
                c.textCentered(label,xx+44,yy+9,WHITE,true);
            }
        }
    }

    private static void slot(PreviewCanvas c,int x,int y) { bevel(c,x-1,y-1,18,18,0xFF8B8B8B,true); }

    private static void collector(PreviewCanvas c,int x,int y,int style) throws Exception {
        if(style==0) {
            shell(c,x,y,220,200,"Charge Collector");
            slot(c,x+55,y+52); slot(c,x+149,y+52);
            for(int row=0;row<3;row++) for(int col=0;col<9;col++) slot(c,x+29+col*18,y+112+row*18);
            for(int col=0;col<9;col++) slot(c,x+29+col*18,y+170);
            bevel(c,x+81,y+54,58,12,0xFF666666,true);
        } else {
            c.blit(texture("collector"),x,y,220,200);
            c.text("Charge Collector",x+16,y+12,INK,false);
        }
        c.textCentered("Charging bottle",x+110,y+29,INK,false);
        c.text("Empty",x+48,y+40,INK,false); c.text("Filled",x+143,y+40,INK,false);
        c.blit(c.texture("textures/item/mana_bottle.png"),x+55,y+52,16,16);
        c.blit(c.texture("textures/item/water_mana_bottle.png"),x+149,y+52,16,16);
        c.fill(x+83,y+56,37,8,0xFF3994AF); c.fill(x+83,y+56,37,1,0xFFA4E5EC);
        c.textCentered("68%",x+110,y+73,INK,false);
        c.fill(x+65,y+87,8,8,WATER); c.text("Element: Water",x+77,y+87,INK,false);
        c.text("Inventory",x+29,y+100,INK,false);
    }
}
