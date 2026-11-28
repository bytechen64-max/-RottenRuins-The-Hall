package org.bytechen.rottenruins.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.c2s.FilterSettingsPacket;
import org.bytechen.rottenruins.overworld.energy.ElementBand;
import org.bytechen.rottenruins.overworld.energy.SourceWaveform;
import org.bytechen.rottenruins.overworld.inventory.SourceMachineMenu;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 可替换的像素纹理底板；文字、元素、示波曲线和进度实时叠加。 */
public final class SourceMachineScreen extends AbstractContainerScreen<SourceMachineMenu> {
    private static final int TEXT=0xFF404040,MUTED=0xFF404040,LIGHT=0xFFE8E8D8;
    private static final ResourceLocation MANUAL=texture("filter_manual"),QUICK=texture("filter_quick"),
            BANDS=texture("filter_bands"),COLLECTOR=texture("collector"),WIDGETS=texture("widgets");
    private static int rememberedView;
    private final int[] settings=new int[4];
    private final TuningSlider[] sliders=new TuningSlider[4];
    private final List<Button> presets=new ArrayList<>();
    private int view=rememberedView;
    private boolean dirty,editing,awaitingAck;
    private long lastSend,lastFrame;
    private float shownProgress;

    public SourceMachineScreen(SourceMachineMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);
        imageWidth=menu.collector?220:372;
        imageHeight=menu.collector?200:230;
        inventoryLabelX=29; inventoryLabelY=100;
    }

    @Override
    protected void init() {
        imageWidth=menu.collector?220:Math.min(372,width-16);
        super.init();
        presets.clear();
        shownProgress=menu.value(4)/(float)SourceMachineBlockEntity.TOTAL_WORK;
        lastFrame=System.nanoTime();
        if (!menu.collector) {
            int tabWidth=(imageWidth-40)/3;
            for(int i=0;i<3;i++) addRenderableWidget(new ModeButton(i,leftPos+16+i*(tabWidth+4),topPos+36,tabWidth));
            int sliderWidth=(imageWidth-42)/2;
            for(int i=0;i<4;i++) {
                settings[i]=menu.value(i);
                sliders[i]=addRenderableWidget(new TuningSlider(i,leftPos+16+(i%2)*(sliderWidth+10),topPos+155+(i/2)*35,sliderWidth));
            }
            int tileWidth=(imageWidth-50)/4;
            for(ElementBand element:ElementBand.values()) {
                int i=element.ordinal();
                presets.add(addRenderableWidget(new PresetButton(element,leftPos+17+(i%4)*(tileWidth+6),topPos+155+(i/4)*35,tileWidth)));
            }
            presets.add(addRenderableWidget(new PresetButton(null,leftPos+17+3*(tileWidth+6),topPos+190,tileWidth)));
            updateVisibility();
        }
    }

    private void setView(int value) {
        view=rememberedView=value;
        editing=false;
        updateVisibility();
    }

    private void updateVisibility() {
        for(TuningSlider slider:sliders) { slider.visible=view==0; slider.active=slider.visible; }
        for(Button preset:presets) { preset.visible=view==1; preset.active=preset.visible; }
    }

    private void applyPreset(ElementBand element) {
        int[] values=element.preset();
        System.arraycopy(values,0,settings,0,4);
        for(TuningSlider slider:sliders) slider.sync();
        editing=false; dirty=true;
        sendChanges();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if(!menu.collector) {
            long now=System.currentTimeMillis();
            if(dirty && now-lastSend>=100) sendChanges();
            boolean agrees=true;
            for(int i=0;i<4;i++) if(settings[i]!=menu.value(i)) agrees=false;
            if(agrees) awaitingAck=false;
            if(!editing && !dirty && (!awaitingAck || now-lastSend>1500)) {
                for(int i=0;i<4;i++) { settings[i]=menu.value(i); sliders[i].sync(); }
                awaitingAck=false;
            }
        }
    }

    private void sendChanges() {
        if(!dirty) return;
        NetworkHelper.sendToServer(new FilterSettingsPacket(menu.containerId,settings[0],settings[1],settings[2],settings[3]!=0));
        dirty=false; awaitingAck=true; lastSend=System.currentTimeMillis();
    }

    @Override public void onClose() { sendChanges(); super.onClose(); }

    @Override
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics);
        super.render(graphics,mouseX,mouseY,partialTick);
        renderTooltip(graphics,mouseX,mouseY);
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("rotten_ruins","textures/gui/source_machine/"+name+".png");
    }

    /** Stretch only the middle strip: keep borders and the two control columns intact. */
    private static void horizontal(GuiGraphics graphics,ResourceLocation texture,int x,int y,int width,int height,
                                   int u,int v,int sourceWidth,int border,int textureWidth,int textureHeight) {
        int[] source={0,border,sourceWidth-border,sourceWidth};
        int[] target={0,border,width-border,width};
        for(int i=0;i<3;i++) graphics.blit(texture,x+target[i],y,target[i+1]-target[i],height,
                (float)(u+source[i]),(float)v,source[i+1]-source[i],height,textureWidth,textureHeight);
    }

    private static void buttonFace(GuiGraphics graphics,int x,int y,int width,int height,boolean selected,boolean hovered) {
        int u=selected?20:hovered?40:0;
        int[] source={0,2,18,20},xx={0,2,width-2,width},yy={0,2,height-2,height};
        for(int row=0;row<3;row++) for(int col=0;col<3;col++)
            graphics.blit(WIDGETS,x+xx[col],y+yy[row],xx[col+1]-xx[col],yy[row+1]-yy[row],
                    (float)(u+source[col]),(float)source[row],source[col+1]-source[col],source[row+1]-source[row],80,20);
        if(selected && hovered) graphics.renderOutline(x,y,width,height,0xFFFFFFFF);
    }

    @Override
    protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        if(!menu.collector) {
            // Manual wells are rendered by each widget, so they follow narrow-window layout.
            if(view==0) {
                horizontal(graphics,MANUAL,x,y,imageWidth,155,0,0,372,16,372,230);
                horizontal(graphics,QUICK,x,y+155,imageWidth,63,0,155,372,16,372,230);
                horizontal(graphics,MANUAL,x,y+218,imageWidth,12,0,218,372,16,372,230);
            } else horizontal(graphics,view==2?BANDS:QUICK,x,y,imageWidth,imageHeight,0,0,372,16,372,230);
            if(view==2) drawReference(graphics,x,y); else drawWave(graphics,x+16,y+66,imageWidth-32,62);
        } else {
            graphics.blit(COLLECTOR,x,y,0,0,220,200,220,200);
            float progress=menu.value(4)/(float)SourceMachineBlockEntity.TOTAL_WORK;
            long now=System.nanoTime();
            double seconds=Math.min(0.1,(now-lastFrame)/1.0e9); lastFrame=now;
            if(progress<shownProgress) shownProgress=progress;
            else shownProgress+=(progress-shownProgress)*(float)(1-Math.exp(-seconds*16));
            int filled=Math.round(54*shownProgress);
            if(filled>0) {
                graphics.fill(x+83,y+56,x+83+filled,y+64,0xFF3994AF);
                graphics.fill(x+83,y+56,x+83+filled,y+57,0xFFA4E5EC);
            }
            centered(graphics,menu.value(4)*100/SourceMachineBlockEntity.TOTAL_WORK+"%",x+110,y+73,TEXT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {
        graphics.drawString(font,title,16,12,TEXT,false);
        if(menu.collector) {
            centered(graphics,Component.translatable("gui.rotten_ruins.source."+collectorStatus()),110,29,MUTED);
            graphics.drawString(font,Component.translatable("gui.rotten_ruins.source.input_bottle"),48,40,MUTED,false);
            graphics.drawString(font,Component.translatable("gui.rotten_ruins.source.output_bottle"),143,40,MUTED,false);
            ElementBand element=elementFromMenu();
            Component label=element==null?Component.translatable("gui.rotten_ruins.source.unmatched"):
                    Component.translatable("gui.rotten_ruins.source.tuned",Component.translatable(element.key()));
            String status=label.getString();
            int textX=110-font.width(status)/2;
            graphics.fill(textX-12,87,textX-4,95,element==null?0xFF777777:0xFF000000|element.color);
            graphics.drawString(font,status,textX,87,TEXT,false);
            graphics.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,MUTED,false);
        } else if(view!=2) {
            ElementBand element=ElementBand.match(settings[0],settings[1],settings[2],settings[3]!=0);
            graphics.drawString(font,Component.translatable("gui.rotten_ruins.source.spectrum"),26,73,0xFFB9C3AF,false);
            String power=Component.translatable("gui.rotten_ruins.source.power",menu.value(5)).getString();
            graphics.drawString(font,power,imageWidth-17-font.width(power),137,MUTED,false);
            String status=(element==null?Component.translatable("gui.rotten_ruins.source.unmatched"):
                    Component.translatable("gui.rotten_ruins.source.tuned",Component.translatable(element.key()))).getString();
            graphics.fill(17,137,25,145,element==null?0xFF777777:0xFF000000|element.color);
            graphics.drawString(font,font.plainSubstrByWidth(status,Math.max(0,imageWidth-56-font.width(power))),29,137,TEXT,false);
        }
    }

    private void centered(GuiGraphics graphics,Component text,int centerX,int y,int color) {
        graphics.drawString(font,text,centerX-font.width(text)/2,y,color,false);
    }

    private void centered(GuiGraphics graphics,String text,int centerX,int y,int color) {
        graphics.drawString(font,text,centerX-font.width(text)/2,y,color,false);
    }

    private ElementBand elementFromMenu() {
        int id=menu.value(6);
        return id>0 && id<=ElementBand.values().length?ElementBand.values()[id-1]:null;
    }

    private String collectorStatus() {
        if(menu.value(5)==0) return "no_power";
        if(menu.value(7)==0) return "no_filter";
        if(menu.value(7)==2) return "invalid_band";
        if(menu.value(7)==3) return "conflict";
        if(!menu.getSlot(1).getItem().isEmpty()) {
            ElementBand element=elementFromMenu();
            var output=menu.getSlot(1).getItem();
            if(element!=null && (!output.is(SourceMachineBlockEntity.bottle(element)) || output.getCount()>=output.getMaxStackSize())) return "output_full";
        }
        return menu.getSlot(0).hasItem()?"charging":"insert_bottle";
    }

    private void drawReference(GuiGraphics graphics,int x,int y) {
        int inner=imageWidth-32;
        int[] columns={Math.round(inner*0.29F),Math.round(inner*0.48F),Math.round(inner*0.66F),Math.round(inner*0.85F)};
        String[] headers={"fc","B","G","φ"};
        for(int i=0;i<4;i++) graphics.drawString(font,headers[i],x+16+columns[i],y+76,LIGHT,false);
        int row=0;
        for(ElementBand element:ElementBand.values()) {
            int yy=y+96+row++*16;
            String label=font.plainSubstrByWidth(Component.translatable(element.key()).getString(),Math.max(0,columns[0]-14));
            graphics.drawString(font,label,x+25,yy,element.color,false);
            String[] values={range(element.minFc,element.maxFc),range(element.minB,element.maxB),range(element.minG,element.maxG),element.negative?"−":"+"};
            for(int i=0;i<4;i++) graphics.drawString(font,values[i],x+16+columns[i],yy,LIGHT,false);
        }
    }

    private static String range(int low,int high) { return low/10+"–"+high/10; }
    private static String number(int value) { return String.format(Locale.ROOT,"%.1f",value/10.0); }

    private void drawWave(GuiGraphics graphics,int x,int y,int width,int height) {
        ElementBand band=ElementBand.match(settings[0],settings[1],settings[2],settings[3]!=0);
        int color=0xFF000000|(band==null?0x8CA8BA:band.color);
        float[] points=SourceWaveform.points(settings[0],settings[1],settings[2],settings[3]!=0,
                System.nanoTime()/1.0e9,Math.max(2,width-16),width-17,height-20);
        for(int i=0;i<points.length;i+=2) {
            int px=x+8+(int)points[i],py=y+14+(int)points[i+1];
            graphics.fill(px,py,px+1,py+1,color);
        }
    }

    private final class ModeButton extends Button {
        private final int mode;
        ModeButton(int mode,int x,int y,int width) {
            super(x,y,width,20,Component.translatable("gui.rotten_ruins.source.mode."+mode),button->setView(mode),DEFAULT_NARRATION);
            this.mode=mode;
        }
        @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            boolean selected=view==mode;
            buttonFace(graphics,getX(),getY(),width,height,selected,isHoveredOrFocused());
            String label=font.plainSubstrByWidth(getMessage().getString(),Math.max(0,width-8));
            graphics.drawString(font,label,getX()+(width-font.width(label))/2,getY()+6,LIGHT,true);
        }
    }

    private final class PresetButton extends Button {
        private final ElementBand element;
        PresetButton(ElementBand element,int x,int y,int width) {
            super(x,y,width,27,Component.translatable(element==null?"gui.rotten_ruins.source.fine_tune":element.key()),
                    button->{ if(element==null) setView(0); else applyPreset(element); },DEFAULT_NARRATION);
            this.element=element;
            if(element!=null) {
                int[] preset=element.preset();
                setTooltip(Tooltip.create(Component.translatable("gui.rotten_ruins.source.preset_hint",Component.translatable(element.key()),
                        number(preset[0]),number(preset[1]),number(preset[2]),preset[3]==0?"+":"−")));
            }
        }
        @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            boolean selected=element!=null && ElementBand.match(settings[0],settings[1],settings[2],settings[3]!=0)==element;
            buttonFace(graphics,getX(),getY(),width,height,selected,isHoveredOrFocused());
            if(element!=null) graphics.fill(getX()+5,getY()+9,getX()+9,getY()+17,0xFF000000|element.color);
            int start=element==null?4:12,end=width-4;
            String label=font.plainSubstrByWidth(getMessage().getString(),Math.max(0,end-start));
            graphics.drawString(font,label,getX()+start+(end-start-font.width(label))/2,getY()+9,LIGHT,true);
        }
    }

    private final class TuningSlider extends AbstractSliderButton {
        private final int index;
        TuningSlider(int index,int x,int y,int width) {
            super(x,y,width,28,Component.empty(),settings[index]/(index==3?1.0:100.0));
            this.index=index;
            setTooltip(Tooltip.create(Component.translatable("gui.rotten_ruins.source.param."+index+".hint")));
            updateMessage();
        }
        void sync() { value=settings[index]/(index==3?1.0:100.0); updateMessage(); }
        private String display() { return index==3?(settings[index]==0?"+":"−"):number(settings[index]); }
        @Override protected void updateMessage() { setMessage(Component.translatable("gui.rotten_ruins.source.param."+index,display())); }
        @Override protected void applyValue() {
            settings[index]=(int)Math.round(value*(index==3?1:100));
            value=settings[index]/(index==3?1.0:100.0); dirty=true; updateMessage();
        }
        private void valueFromMouse(double mouseX) {
            value=Math.max(0,Math.min(1,(mouseX-getX()-9)/(width-18)));
            applyValue();
        }
        @Override public void onClick(double mouseX,double mouseY) { editing=true; valueFromMouse(mouseX); }
        @Override public void onDrag(double mouseX,double mouseY,double dragX,double dragY) { valueFromMouse(mouseX); }
        @Override public void onRelease(double mouseX,double mouseY) { super.onRelease(mouseX,mouseY); editing=false; sendChanges(); }
        @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
            if(keyCode==262 || keyCode==263) {
                settings[index]=Math.max(0,Math.min(index==3?1:100,settings[index]+(keyCode==262?1:-1)));
                sync(); dirty=true; return true;
            }
            return super.keyPressed(keyCode,scanCode,modifiers);
        }
        @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            horizontal(graphics,MANUAL,getX(),getY(),width,height,16+(index%2)*175,155+(index/2)*35,165,12,372,230);
            if(isHoveredOrFocused()) graphics.renderOutline(getX(),getY(),width,height,0xFFFFFFFF);
            String label=font.plainSubstrByWidth(Component.translatable("gui.rotten_ruins.source.param."+index+".short").getString(),
                    Math.max(0,width-24-font.width(display())));
            graphics.drawString(font,label,getX()+8,getY()+5,MUTED,false);
            graphics.drawString(font,display(),getX()+width-8-font.width(display()),getY()+5,TEXT,false);
            int cx=getX()+9+(int)Math.round((width-18)*value);
            graphics.blit(WIDGETS,cx-4,getY()+16,60,0,8,10,80,20);
        }
    }
}
