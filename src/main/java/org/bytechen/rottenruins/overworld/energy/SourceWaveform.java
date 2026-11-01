package org.bytechen.rottenruins.overworld.energy;

/** 不依赖 Minecraft 的示波仪采样与带抗锯齿边缘的线带，供实机和离屏预览共用。 */
public final class SourceWaveform {
    private SourceWaveform() {}

    public static float[] points(int fc,int bandwidth,int gain,boolean negative,double time,int count,float width,float height) {
        if (count<2 || width<=0 || height<8) throw new IllegalArgumentException("Invalid waveform dimensions");
        float[] result=new float[count*2];
        ElementBand band=ElementBand.match(fc,bandwidth,gain,negative);
        for(int i=0;i<count;i++) {
            double t=i/(double)(count-1);
            double angle=(t*(0.08+fc*0.08)-time*(0.1+fc*0.007))*Math.PI*2;
            double signal=Math.sin(angle);
            if(band==ElementBand.FIRE) signal*=Math.pow(Math.max(0,Math.sin(t*16-time*3)),4);
            else if(band==ElementBand.NATURE) signal=0.8*signal+0.2*Math.sin(angle*2);
            else if(band==ElementBand.DEATH) signal=(signal+0.3*Math.sin(angle*7.3))*Math.exp(-2*(t*3%1));
            else if(band==ElementBand.VOID) signal=0.6+0.4*Math.cos(angle);
            else if(band==ElementBand.MYSTERIOUS) signal=0.4*Math.sin(angle*0.2)+0.15*Math.sin(angle*13);
            else if(band!=ElementBand.LIGHT && band!=ElementBand.WATER) signal+=bandwidth/500.0*Math.sin(angle*3.7);
            double yy=height/2-signal*(height*0.38)*gain/100.0*(negative?-1:1);
            result[i*2]=(float)(t*width);
            result[i*2+1]=(float)Math.max(3,Math.min(height-3,yy));
        }
        return result;
    }

    /** 顶点格式 x,y,r,g,b,a；线带核心和外缘之间按一个物理像素淡出，一次批量绘制。 */
    public static float[] mesh(float[] points,float halfWidth,float feather,int argb) {
        if(points.length<4 || points.length%2!=0 || halfWidth<=0 || feather<=0) throw new IllegalArgumentException("Invalid line mesh");
        int count=points.length/2;
        float[] vertices=new float[(count-1)*18*6];
        float[][] nodes=new float[count][8];
        for(int i=0;i<count;i++) {
            int previous=Math.max(0,i-1),next=Math.min(count-1,i+1);
            float dx=points[next*2]-points[previous*2],dy=points[next*2+1]-points[previous*2+1];
            float length=(float)Math.max(0.0001,Math.hypot(dx,dy));
            float nx=-dy/length,ny=dx/length;
            float[] distances={halfWidth+feather,halfWidth,-halfWidth,-halfWidth-feather};
            for(int edge=0;edge<4;edge++) {
                nodes[i][edge*2]=points[i*2]+nx*distances[edge];
                nodes[i][edge*2+1]=points[i*2+1]+ny*distances[edge];
            }
        }
        int cursor=0;
        for(int i=0;i<count-1;i++) for(int band=0;band<3;band++) {
            int[] row={i,i+1,i+1,i,i+1,i};
            int[] edge={band,band,band+1,band,band+1,band+1};
            for(int vertex=0;vertex<6;vertex++) {
                int e=edge[vertex];
                vertices[cursor++]=nodes[row[vertex]][e*2];
                vertices[cursor++]=nodes[row[vertex]][e*2+1];
                vertices[cursor++]=((argb>>16)&255)/255.0F;
                vertices[cursor++]=((argb>>8)&255)/255.0F;
                vertices[cursor++]=(argb&255)/255.0F;
                vertices[cursor++]=(e==0 || e==3)?0:((argb>>>24)&255)/255.0F;
            }
        }
        return vertices;
    }
}
