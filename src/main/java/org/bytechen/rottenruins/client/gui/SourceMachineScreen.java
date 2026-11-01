package org.bytechen.rottenruins.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.bytechen.rottenruins.client.rend.gui.WheelGuiShaders;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.c2s.FilterSettingsPacket;
import org.bytechen.rottenruins.overworld.energy.ElementBand;
import org.bytechen.rottenruins.overworld.energy.SourceWaveform;
import org.bytechen.rottenruins.overworld.inventory.SourceMachineMenu;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 与魔法装载界面共用 SDF 圆角；示波曲线使用单批次、逐物理像素淡出的线带。 */
public final class SourceMachineScreen extends AbstractContainerScreen<SourceMachineMenu> {
    private static final int TEXT=0xFFE3E8EE,MUTED=0xFF92A1B4,GOLD=0xFFE7C46A;
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

    private static void panel(GuiGraphics graphics,float x,float y,float width,float height,float radius,int top,int bottom,int border) {
        if(WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(graphics,x,y,width,height,radius,top,bottom,border,0.8F);
        } else {
            graphics.fillGradient((int)x,(int)y,(int)(x+width),(int)(y+height),top,bottom);
            if((border>>>24)!=0) graphics.renderOutline((int)x,(int)y,(int)width,(int)height,border);
        }
    }

    private static void line(GuiGraphics graphics,float x1,float y1,float x2,float y2,float halfWidth,int color) {
        if(WheelGuiShaders.sdfAvailable()) WheelGuiShaders.sdfLine(graphics,x1,y1,x2,y2,halfWidth,color);
        else graphics.fill((int)Math.min(x1,x2),(int)Math.min(y1,y2),(int)Math.max(x1,x2)+1,(int)Math.max(y1,y2)+1,color);
    }

    @Override
    protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        panel(graphics,x-4,y-2,imageWidth+8,imageHeight+8,10,0x30000000,0x60000000,0);
        panel(graphics,x,y,imageWidth,imageHeight,8,0xFA131E30,0xFA080D18,0x777F795F);
        line(graphics,x+16,y+28,x+imageWidth-16,y+28,0.35F,0x667C775C);
        if(!menu.collector) {
            if(view==2) drawReference(graphics,x,y); else drawWave(graphics,x+16,y+66,imageWidth-32,62);
        } else {
            for(var slot:menu.slots) panel(graphics,x+slot.x-1,y+slot.y-1,18,18,3,0xFF08121F,0xFF101D2D,0x66566B81);
            panel(graphics,x+81,y+54,58,12,6,0xFF06101A,0xFF06101A,0x55405C6F);
            float progress=menu.value(4)/(float)SourceMachineBlockEntity.TOTAL_WORK;
            long now=System.nanoTime();
            double seconds=Math.min(0.1,(now-lastFrame)/1.0e9); lastFrame=now;
            if(progress<shownProgress) shownProgress=progress;
            else shownProgress+=(progress-shownProgress)*(float)(1-Math.exp(-seconds*16));
            if(shownProgress>0.001F) panel(graphics,x+83,y+56,54*shownProgress,8,4,0xFF94DFE7,0xFF3CA4BE,0);
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
            centered(graphics,label,110,87,element==null?MUTED:element.color);
            graphics.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,MUTED,false);
        } else if(view!=2) {
            ElementBand element=ElementBand.match(settings[0],settings[1],settings[2],settings[3]!=0);
            graphics.drawString(font,Component.translatable("gui.rotten_ruins.source.spectrum"),26,73,MUTED,false);
            String power=Component.translatable("gui.rotten_ruins.source.power",menu.value(5)).getString();
            graphics.drawString(font,power,imageWidth-17-font.width(power),137,MUTED,false);
            String status=(element==null?Component.translatable("gui.rotten_ruins.source.unmatched"):
                    Component.translatable("gui.rotten_ruins.source.tuned",Component.translatable(element.key()))).getString();
            graphics.drawString(font,font.plainSubstrByWidth(status,imageWidth-44-font.width(power)),17,137,element==null?MUTED:element.color,false);
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
        panel(graphics,x+16,y+66,imageWidth-32,150,5,0xC0091423,0xC00B1420,0x44476078);
        int inner=imageWidth-32;
        int[] columns={Math.round(inner*0.29F),Math.round(inner*0.48F),Math.round(inner*0.66F),Math.round(inner*0.85F)};
        String[] headers={"fc","B","G","φ"};
        for(int i=0;i<4;i++) graphics.drawString(font,headers[i],x+16+columns[i],y+76,GOLD,false);
        int row=0;
        for(ElementBand element:ElementBand.values()) {
            int yy=y+96+row++*16;
            graphics.drawString(font,Component.translatable(element.key()),x+25,yy,element.color,false);
            String[] values={range(element.minFc,element.maxFc),range(element.minB,element.maxB),range(element.minG,element.maxG),element.negative?"−":"+"};
            for(int i=0;i<4;i++) graphics.drawString(font,values[i],x+16+columns[i],yy,TEXT,false);
        }
    }

    private static String range(int low,int high) { return low/10+"–"+high/10; }
    private static String number(int value) { return String.format(Locale.ROOT,"%.1f",value/10.0); }

    private void drawWave(GuiGraphics graphics,int x,int y,int width,int height) {
        panel(graphics,x,y,width,height,5,0xFF06111C,0xFF091421,0x55506C7E);
        for(int gx=16;gx<width-8;gx+=24) line(graphics,x+gx,y+18,x+gx,y+height-6,0.25F,0x282F5770);
        for(int gy=24;gy<height-4;gy+=16) line(graphics,x+8,y+gy,x+width-8,y+gy,0.25F,0x282F5770);
        line(graphics,x+8,y+36,x+width-8,y+36,0.3F,0x60445D72);
        ElementBand band=ElementBand.match(settings[0],settings[1],settings[2],settings[3]!=0);
        int color=0xFF000000|(band==null?0x8CA8BA:band.color);
        float scale=(float)minecraft.getWindow().getGuiScale();
        float[] points=SourceWaveform.points(settings[0],settings[1],settings[2],settings[3]!=0,System.nanoTime()/1.0e9,width, width-16,height-18);
        float[] mesh=SourceWaveform.mesh(points,0.6F,1.0F/Math.max(1,scale),color);
        graphics.flush();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer=Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        var matrix=graphics.pose().last().pose();
        for(int i=0;i<mesh.length;i+=6) buffer.vertex(matrix,x+8+mesh[i],y+14+mesh[i+1],0).color(mesh[i+2],mesh[i+3],mesh[i+4],mesh[i+5]).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }

    private final class ModeButton extends Button {
        private final int mode;
        ModeButton(int mode,int x,int y,int width) {
            super(x,y,width,20,Component.translatable("gui.rotten_ruins.source.mode."+mode),button->setView(mode),DEFAULT_NARRATION);
            this.mode=mode;
        }
        @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            boolean selected=view==mode;
            panel(graphics,getX(),getY(),width,height,4,selected?0x443C5061:0x44131F30,selected?0x44344750:0x440B1422,
                    selected?0xCCE7C46A:isHoveredOrFocused()?0x997D8C98:0x3346576B);
            centered(graphics,getMessage(),getX()+width/2,getY()+6,selected?GOLD:TEXT);
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
            int accent=element==null?MUTED:0xFF000000|element.color;
            panel(graphics,getX(),getY(),width,height,5,selected?0xFF203446:0xFF152334,0xFF0C1624,
                    selected?accent:isHoveredOrFocused()?0xAA92A1B4:0x5546556A);
            if(selected) line(graphics,getX()+9,getY()+height-3,getX()+width-9,getY()+height-3,0.5F,accent);
            String label=font.plainSubstrByWidth(getMessage().getString(),width-12);
            centered(graphics,label,getX()+width/2,getY()+9,accent);
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
            panel(graphics,getX(),getY(),width,height,4,0xFF142236,0xFF0D1828,isHoveredOrFocused()?0x99E7C46A:0x44465970);
            graphics.drawString(font,Component.translatable("gui.rotten_ruins.source.param."+index+".short"),getX()+8,getY()+5,MUTED,false);
            graphics.drawString(font,display(),getX()+width-8-font.width(display()),getY()+5,TEXT,false);
            float start=getX()+9,end=getX()+width-9,cy=getY()+height-7;
            line(graphics,start,cy,end,cy,1.0F,0xFF30435B);
            if(value>0.001) line(graphics,start,cy,(float)(start+(end-start)*value),cy,1.0F,0xFF72CAD6);
            float cx=(float)(start+(end-start)*value);
            if(WheelGuiShaders.sdfAvailable()) WheelGuiShaders.sdfDisc(graphics,cx,cy,3.0F,0xFFF4E4B4,GOLD);
            else graphics.fill((int)cx-2,(int)cy-2,(int)cx+3,(int)cy+3,GOLD);
        }
    }
}
