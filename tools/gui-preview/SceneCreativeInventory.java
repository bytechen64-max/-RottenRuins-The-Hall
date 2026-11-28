/** Real 195x136 vanilla geometry, generated atlases, and the selected D + 1 metal nameplate.
 * Item icons are representative project textures; this does not run Minecraft menus.
 */
public final class SceneCreativeInventory {
    private static final String BASE = "textures/gui/creative_inventory/";
    private static final String[] ITEMS = {
        "hall_tendon", "infection_bone", "bonecrusher_core", "domite_crystal",
        "domerite_ingot", "hard_fragments", "live_meat", "void_rune", "acid_sac",
        "host_meat", "death_star", "true_copper", "crimson_vow", "syringe",
        "silent_daylight", "domerite_sword", "crude_sword", "magic_cata"
    };
    private static final String[] MAGIC = {
        "wand_fire", "wand_water", "wand_nature", "wand_light", "wand_void",
        "wand_death", "wand_mysterious", "fire_ball", "tidal_pulse"
    };

    public static void render(PreviewCanvas c, float time) {
        draw(c);
    }

    private static void draw(PreviewCanvas c) {
        c.backdrop();
        c.text("ROTTEN RUINS / CREATIVE INVENTORY",30,20,0xFFBDCCDF,true);
        c.text("D + 1 / blue slate, silver nameplates",30,35,0xFF8585BD,false);
        c.pushTransform();
        c.translate(30,100);
        c.scale(2,2);
        panel(c,false);
        c.popTransform();

        c.text("MAGIC / 1x GUI",435,68,0xFFBDCCDF,false);
        c.pushTransform();
        c.translate(435,108);
        panel(c,true);
        c.popTransform();
        c.text("Palette",435,270,0xFFBDCCDF,false);
        int[] colors = {0xFF29313F,0xFF0D131E,0xFF667A94,0xFFB8CBDE,0xFF8585BD};
        for(int i=0;i<colors.length;i++) c.fill(435+i*34,285,28,15,colors[i]);
        c.text("195 x 136 / vanilla slots",435,315,0xFF8D9E96,false);
        c.text("256 x 256 / RGBA atlases",435,329,0xFF8D9E96,false);
        c.text("Fixed silver titles",435,343,0xFF8D9E96,false);
        c.text("Generated pixel textures + project item artwork",30,380,0xFF82978F,false);
    }

    private static void panel(PreviewCanvas c,boolean magic) {
        PreviewTexture bg=c.texture(BASE+"tab_items.png");
        PreviewTexture tabs=c.texture(BASE+"tabs.png");
        // Atlas sampling matches renderBg / renderTabButton (default 256x256).
        crop(c,tabs,0,-28,26,32,0,32);
        c.blit(c.texture("textures/item/void_rune.png"),5,-19,16,16);
        crop(c,bg,0,0,195,136,0,0);
        c.text("Rotten Ruins",8,6,0xFFBDCCDF,false);
        crop(c,tabs,175,magic?72:35,12,15,232,0);
        for(int row=0;row<5;row++) {
            if(row==0 || (!magic && row==3)) continue;
            for(int col=0;col<9;col++) {
                String name=magic?MAGIC[(row*9+col)%MAGIC.length]:ITEMS[((row-1)*9+col)%ITEMS.length];
                String path="textures/item/"+name+".png";
                c.blit(c.texture(path),9+18*col,18+18*row,16,16);
            }
        }
        for(int col=0;col<5;col++)
            c.blit(c.texture("textures/item/"+ITEMS[12+col]+".png"),9+col*18,112,16,16);
        // Same hover color supplied by RegisterTab.withSlotColor.
        c.fill(9+18*3,36,16,16,0x40667A94);
        nameplate(c,0,magic?"Magic":"Materials");
        if(!magic) nameplate(c,3,"Weapons & Tools");
    }

    private static void crop(PreviewCanvas c,PreviewTexture tex,int x,int y,int w,int h,int u,int v) {
        c.blit(tex,x,y,w,h,u/256f,v/256f,(u+w)/256f,(v+h)/256f);
    }

    private static void nameplate(PreviewCanvas c,int row,String text) {
        int y=17+18*row;
        crop(c,c.texture(BASE+"dividers.png"),8,y,162,18,0,0);
        // Same 142px title limit as CreativeTabDividerRenderer.
        if(c.textWidth(text)>142) {
            while(!text.isEmpty() && c.textWidth(text+"...")>142)
                text=text.substring(0,text.offsetByCodePoints(text.length(),-1));
            text+="...";
        }
        c.textCentered(text,89,y+4,0xFFBDCCDF,true);
    }
}
