#version 150
in vec4 vertexColor;
in vec2 texCoord;
uniform sampler2D ScreenTexture;
uniform vec2 AirScreenSize;
uniform vec4 AirStyle; // displacement at 1080p, dispersion, turbulence, clock
out vec4 fragColor;
void main(){
 float u=clamp(texCoord.x,0.,1.),v=clamp(texCoord.y,0.,1.);
 if(AirStyle.x<=0.)discard;
 vec2 pixel=1./AirScreenSize,screen=gl_FragCoord.xy*pixel;
 vec2 gradient=vec2(dFdx(texCoord.y),dFdy(texCoord.y));
 vec2 normal=gradient/max(length(gradient),.00001),tangent=vec2(-normal.y,normal.x);
 // The expanded world mesh puts the pressure wave OUTSIDE the bright blade rim.
 float band=exp(-pow((v-.86)/.15,2.));
 float flow=sin(u*23.+v*9.-AirStyle.w*5.)*.6+sin(u*43.-v*13.+AirStyle.w*3.)*.4;
 float pressure=(.8+flow*AirStyle.z*.35)*band;
 float envelope=smoothstep(0.,.14,u)*(1.-smoothstep(.94,1.,u))*vertexColor.a;
 float scale=max(.25,AirScreenSize.y/1080.);
 vec2 offset=(normal+tangent*flow*AirStyle.z*.35)*pixel*AirStyle.x*scale*pressure;
 vec2 margin=pixel*1.5;
 vec3 color=texture(ScreenTexture,clamp(screen+offset,margin,1.-margin)).rgb;
 color.r=texture(ScreenTexture,clamp(screen+offset*(1.+AirStyle.y),margin,1.-margin)).r;
 color.b=texture(ScreenTexture,clamp(screen+offset*(1.-AirStyle.y),margin,1.-margin)).b;
 fragColor=vec4(color,clamp(band*envelope*.95,0.,1.));
}
