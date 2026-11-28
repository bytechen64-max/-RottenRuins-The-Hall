#version 150
in vec4 vertexColor;
in vec2 texCoord;
uniform vec4 ColorModulator;
uniform float TrailTime;
uniform vec4 TrailShape;
uniform vec4 TrailStyle;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
void main(){
 float u=clamp(texCoord.x,0.,1.),v=clamp(texCoord.y,0.,1.),clock=TrailTime*TrailStyle.y;
 // Unbroken outer silhouette, with fine erosion confined to the inner wake.
 float n=noise(vec2(u*17.,v*7.));
 float rim=.91+TrailShape.w*.012*sin(u*23.);
 float d=abs(v-rim),core=exp(-d*d/max(.0001,TrailShape.x*TrailShape.x));
 float halo=exp(-d*d/.014)*TrailShape.z;
 float wake=smoothstep(.08,.48,v)*(1.-smoothstep(rim-.025,rim+.025,v));
 float flow=noise(vec2(u*12.-clock*.65,v*6.+clock*.15));
 float filaments=pow(.5+.5*sin(v*64.+u*11.+flow*3.-clock*3.),9.);
 float inner=mix(1.,smoothstep(.13,.67,n+v*.5),TrailShape.w);
 float secondary=exp(-pow((v-(.72+.015*sin(u*15.+clock)))/.013,2.))*.32;
 float tail=pow(smoothstep(0.,.38,u),TrailStyle.z),head=1.-smoothstep(.99,1.,u);
 float energy=core+halo+wake*TrailShape.y*(.55+.45*flow+filaments*.7)*inner+secondary;
 float alpha=clamp(energy*tail*head,0.,1.)*vertexColor.a;
 if(alpha<.003)discard;
 vec3 cool=vertexColor.rgb*vec3(.45,.65,.95);
 vec3 palette=mix(cool,vertexColor.rgb,smoothstep(.2,.85,v));
 palette=mix(palette,vec3(1.),clamp(core*.85+filaments*.12,0.,.95));
 fragColor=vec4(palette*TrailStyle.x,alpha)*ColorModulator;
}
