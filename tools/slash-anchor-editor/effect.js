'use strict';
const $=id=>document.getElementById(id),copy=x=>JSON.parse(JSON.stringify(x));
let catalog=[],schema,effect,token,itemData,dirty=false,playing=true,time=0,previousTime=0,view='orbit',yaw=.65,pitch=.65,distance=4.2,drag=null,trailTexture,swordTexture,sparkTexture,swordMesh=[],loadSerial=0;
const labels={opacity:['不透明度','提高可见度，不会关闭透明混合'],brightness:['亮度','增强亮边和材质颜色'],width:['刀光宽度','外缘跟随刀尖，向内扩展扇面'],curvature:['立体弧面','沿挥砍平面法线拱起，不朝屏幕展开'],lifetimeTicks:['残留时间 / tick','20 tick = 1 秒'],coreWidth:['亮边宽度','亮边在刀光横截面中的比例'],fill:['扇面填充','低亮区域的覆盖强度'],glow:['柔光层','增强外围柔和光晕'],noise:['破碎与扰动','增加不规则纹理'],flowSpeed:['流动速度','纹理内部细纹的运动速度'],tailPower:['拖尾收束','越高越集中于新轨迹'],particles:['碎光数量','0 为关闭碎光'],stability:['截面稳定','抑制刀刃方向抖动造成的褶皱，1 为最稳定'],refraction:['空气折射 / 1080p 像素','刀光外缘的背景折射，随分辨率缩放；0 为关闭'],dispersion:['折射色散','折射边缘的微弱红蓝分色']};
const status=(s,error=false)=>{$('status').textContent=s;$('status').className=error?'error':'';};
async function api(url,options){const r=await fetch(url,options),j=await r.json();if(!r.ok)throw Error(j.error||r.statusText);return j;}
function normalize(j){const e={...copy(schema.defaults),...j};if(!['shader','texture','inherit'].includes(e.renderer))throw Error('绘制方式无效');if(!/^#[0-9a-f]{6}$/i.test(e.color))throw Error('颜色必须是 #RRGGBB');for(const [k,[d,min,max]] of Object.entries(schema.parameters))if(!Number.isFinite(e[k])||e[k]<min||e[k]>max)throw Error(labels[k][0]+'超出范围');return e;}
function changed(){dirty=true;$('json').value=JSON.stringify(effect,null,2);status('预览已更新 · 尚未保存');}
function controls(){
 $('material').value=effect.renderer==='inherit'?'shader':effect.renderer;$('color').value=effect.color;$('textureId').value=effect.texture;$('controls').replaceChildren();
 for(const [key,[d,min,max,step]] of Object.entries(schema.parameters)){
  const row=document.createElement('div');row.className='slider';const label=document.createElement('label');label.textContent=labels[key][0];label.htmlFor=key;
  const range=document.createElement('input'),number=document.createElement('input');range.id=key;range.type='range';number.type='number';number.setAttribute('aria-label',labels[key][0]+'数值');
  for(const input of [range,number]){input.min=min;input.max=max;input.step=step;input.value=effect[key];input.disabled=effect.renderer==='texture'&&['coreWidth','fill','glow','noise','flowSpeed','tailPower'].includes(key);if(input.disabled)row.style.opacity='.45';}
  const update=e=>{const v=Number(e.target.value);if(!Number.isFinite(v))return;effect[key]=Math.max(min,Math.min(max,v));range.value=number.value=effect[key];changed();};range.oninput=update;number.onchange=update;
  const help=document.createElement('p');help.className='help';help.textContent=labels[key][1];row.append(label,range,number,help);$('controls').append(row);
 }
 $('json').value=JSON.stringify(effect,null,2);
}
async function load(){const serial=++loadSerial;try{
 const id=$('weapon').value;const [data,asset]=await Promise.all([api('/api/effect?id='+encodeURIComponent(id)),api('/api/item?id='+encodeURIComponent(id))]);
 const e=normalize(data.effect);const [sword,trail,mesh]=await Promise.all([imageTexture(asset.sprite),effectTexture(e.texture),spriteGeometry(asset.sprite)]);if(serial!==loadSerial)return;
 if(swordTexture)gl.deleteTexture(swordTexture);if(trailTexture)gl.deleteTexture(trailTexture);swordTexture=sword;trailTexture=trail;effect=e;itemData=asset;swordMesh=mesh;dirty=false;time=0;controls();$('path').textContent=data.origin;$('chooseItem').textContent=$('weapon').selectedOptions[0].textContent+' ▾';$('selectedId').textContent=id;const meta=catalog.find(x=>x.id===id);status('已载入 '+$('weapon').selectedOptions[0].textContent+(meta&&!meta.slashEnabled?' · 设计预览：此物品尚未接入挥砍接口':''));
 }catch(e){status(e.message,true);}}
async function scanItems(){
 const result=await api('/api/catalog'),old=$('weapon').value;catalog=result.items;$('projectRoot').textContent=result.project;$('weapon').replaceChildren();
 for(const item of catalog){const option=document.createElement('option');option.value=item.id;option.textContent=item.name;$('weapon').append(option);}
 $('weapon').value=catalog.some(x=>x.id===old)?old:catalog.some(x=>x.id==='rotten_ruins:domerite_longsword')?'rotten_ruins:domerite_longsword':catalog[0]?.id;
 renderItems();
}
function renderItems(){
 const query=$('itemSearch').value.trim().toLowerCase(),items=catalog.filter(x=>(!$('onlySlash').checked||x.slashEnabled)&&(x.name+' '+x.id).toLowerCase().includes(query));
 $('itemCount').textContent=`${items.length} / ${catalog.length} 件物品 · ${catalog.filter(x=>x.slashEnabled).length} 件已接入挥砍`;$('itemGrid').replaceChildren();
 for(const item of items){const b=document.createElement('button');b.className='itemCard';const img=document.createElement('img');img.loading='lazy';img.src='/api/icon?id='+encodeURIComponent(item.id);img.alt='';const label=document.createElement('strong');label.textContent=item.name;const id=document.createElement('small');id.textContent=item.id;const tag=document.createElement('em');tag.textContent=item.slashEnabled?'已接入挥砍':item.effect?'已有效果配置':'可设计效果';b.append(img,label,id,tag);b.onclick=()=>{$('weapon').value=item.id;$('itemPicker').close();load();};$('itemGrid').append(b);}
}
$('chooseItem').onclick=()=>{$('itemPicker').showModal();$('itemSearch').focus();};$('closePicker').onclick=()=>$('itemPicker').close();$('itemSearch').oninput=renderItems;$('onlySlash').onchange=renderItems;$('rescan').onclick=async()=>{try{await scanItems();status('已重新扫描项目物品。');}catch(e){status(e.message,true);}};
async function effectTexture(id){const result=await api('/api/texture?id='+encodeURIComponent(id));return imageTexture(result.url);}
$('weapon').onchange=load;$('material').onchange=e=>{effect.renderer=e.target.value;controls();changed();};$('color').oninput=e=>{effect.color=e.target.value;changed();};
$('textureId').onchange=async e=>{try{const texture=await effectTexture(e.target.value.trim());gl.deleteTexture(trailTexture);trailTexture=texture;effect.texture=e.target.value.trim();changed();}catch(error){status(error.message,true);}};
document.querySelectorAll('[data-preset]').forEach(b=>b.onclick=()=>{
 const presets={frost:{renderer:'shader',color:'#97DAFF',width:1.9,curvature:.035,brightness:1.5,glow:.5,fill:.38},fire:{renderer:'shader',color:'#FF8D38',width:2,curvature:.04,brightness:1.6,glow:.65,fill:.48,noise:.7,flowSpeed:2},pixel:{renderer:'texture',color:'#FFD08A',width:1.8,curvature:.03,brightness:1.4,opacity:1},lightning:{renderer:'shader',color:'#C2B2FF',width:1.65,curvature:.035,brightness:1.7,noise:1,coreWidth:.035,flowSpeed:2.5,particles:1.8}};
 effect={...copy(schema.defaults),texture:effect.texture,...presets[b.dataset.preset]};controls();changed();time=.8*cutTicks();
});
function setView(v){view=v;for(const k of ['orbit','first','side'])$(k).classList.toggle('active',k===v);$('viewName').textContent={orbit:'自由视角',first:'第一人称',side:'侧视'}[v];if(v==='side'){yaw=1.5;pitch=.15;}if(v==='orbit'){yaw=.65;pitch=.65;}}
for(const k of ['orbit','first','side'])$(k).onclick=()=>setView(k);$('reset').onclick=()=>{setView('orbit');distance=4.2;};
$('play').onclick=()=>{playing=!playing;$('play').textContent=playing?'暂停':'播放';};$('replay').onclick=()=>{time=0;playing=true;$('play').textContent='暂停';};
$('seek').oninput=e=>{time=+e.target.value*cutTicks();playing=false;$('play').textContent='播放';};
$('pose').onchange=()=>time=0;
async function save(target){try{effect=normalize(effect);const result=await api('/api/save-effect',{method:'POST',headers:{'Content-Type':'application/json','X-Editor-Token':token},body:JSON.stringify({id:$('weapon').value,target,profile:effect})});dirty=false;$('path').textContent=result.path;status(target==='config'?'已保存到游戏配置，按 F3+T 加载。':'已保存到模组资源，重新构建后生效。');}catch(e){status(e.message,true);}}
$('setGameDir').onclick=async()=>{try{const result=await api('/api/game-dir',{method:'POST',headers:{'Content-Type':'application/json','X-Editor-Token':token},body:JSON.stringify({path:$('gamePath').value.trim()})});$('gameDir').textContent='当前游戏实例：'+result.gameDir;status('游戏实例目录已设置。');}catch(e){status(e.message,true);}};
$('saveConfig').onclick=()=>save('config');$('saveSource').onclick=()=>save('source');
$('export').onclick=()=>{try{effect=normalize(effect);const url=URL.createObjectURL(new Blob([JSON.stringify(effect,null,2)+'\n'],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download=$('weapon').value.split(':')[1]+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);}catch(e){status(e.message,true);}};
$('import').onchange=async e=>{try{const loaded=normalize(JSON.parse(await e.target.files[0].text()));const texture=await effectTexture(loaded.texture);gl.deleteTexture(trailTexture);trailTexture=texture;effect=loaded;controls();changed();status('JSON 已导入，请保存到目标目录。');}catch(e){status(e.message,true);}};
window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
const vadd=(a,b)=>a.map((n,i)=>n+b[i]),sub=(a,b)=>a.map((n,i)=>n-b[i]),mul=(a,t)=>a.map(n=>n*t),dot=(a,b)=>a.reduce((s,n,i)=>s+n*b[i],0),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],norm=a=>mul(a,1/(Math.hypot(...a)||1));
const identity=()=>[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
function lookAt(eye,target){const z=norm(sub(eye,target)),x=norm(cross([0,1,0],z)),y=cross(z,x);return [x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1];}
function perspective(aspect){const f=1/Math.tan(70*Math.PI/360),near=.05,far=100;return [f/aspect,0,0,0,0,f,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0];}
const canvas=$('scene'),gl=canvas.getContext('webgl',{antialias:true,alpha:false,preserveDrawingBuffer:true});let program,buffer,loc={},white,sceneTexture;
function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function initGL(){
 const vs='attribute vec3 Position;attribute vec4 Color;attribute vec2 UV0;uniform mat4 ModelViewMat;uniform mat4 ProjMat;varying vec4 vertexColor;varying vec2 texCoord;void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.);vertexColor=Color;texCoord=UV0;}';
 const derivatives=gl.getExtension('OES_standard_derivatives');
 let fs=schema.fragment.replace(/#version 150/g,'').replace(/in vec4 vertexColor;/,'varying vec4 vertexColor;').replace(/in vec2 texCoord;/,'varying vec2 texCoord;').replace('out vec4 fragColor;','').replace(/fragColor/g,'gl_FragColor').replace('void main()', 'void effectMain()');
 let air=schema.airFragment.replace(/#version 150/g,'').replace(/in vec4 vertexColor;/,'').replace(/in vec2 texCoord;/,'').replace('out vec4 fragColor;','').replace(/fragColor/g,'gl_FragColor').replace(/texture\(/g,'texture2D(').replace('void main()', 'void airMain()');if(!derivatives)air=air.replace('vec2(dFdx(texCoord.y),dFdy(texCoord.y))','vec2(0.,1.)');
 fs=(derivatives?'#extension GL_OES_standard_derivatives : enable\n':'')+'precision highp float;\nuniform int PreviewMode;uniform sampler2D PreviewTexture;\n'+fs+air+'\nvoid main(){if(PreviewMode==4){airMain();}else if(PreviewMode==0){effectMain();}else if(PreviewMode==3){gl_FragColor=vertexColor;}else{vec4 c=texture2D(PreviewTexture,texCoord)*vertexColor;if(c.a<.003)discard;gl_FragColor=c;}}';
 program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vs));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fs));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
 for(const name of ['ModelViewMat','ProjMat','ColorModulator','TrailTime','TrailShape','TrailStyle','PreviewMode','PreviewTexture','ScreenTexture','AirScreenSize','AirStyle'])loc[name]=gl.getUniformLocation(program,name);
 buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);for(const [name,size,offset] of [['Position',3,0],['Color',4,12],['UV0',2,28]]){const a=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,size,gl.FLOAT,false,36,offset);}
 gl.enable(gl.BLEND);gl.blendFuncSeparate(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);white=makeTexture(null);sceneTexture=makeTexture(null);gl.bindTexture(gl.TEXTURE_2D,sceneTexture);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
}
function makeTexture(img){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);if(img)gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);else gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([255,255,255,255]));for(const k of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,k,gl.NEAREST);for(const k of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,k,gl.CLAMP_TO_EDGE);return t;}
function imageTexture(url){return new Promise((resolve,reject)=>{if(!url){resolve(makeTexture(null));return;}const img=new Image();img.onload=()=>resolve(makeTexture(img));img.onerror=()=>reject(Error('无法加载预览纹理'));img.src=url;});}
async function spriteGeometry(url){
 if(!url)return [];
 const img=new Image();await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=()=>reject(Error('无法读取物品像素轮廓'));img.src=url;});
 const source=document.createElement('canvas');source.width=img.naturalWidth;source.height=img.naturalHeight;const context=source.getContext('2d');context.drawImage(img,0,0);
 const rgba=context.getImageData(0,0,source.width,source.height).data,alpha=new Uint8Array(source.width*source.height);for(let i=0;i<alpha.length;i++)alpha[i]=rgba[i*4+3];
 return SlashSpriteGeometry.mesh(source.width,source.height,alpha);
}
function vertex(data,p,color,u=0,v=0){data.push(...p,...color,u,v);}
function quad(data,points,color,uv=[[0,0],[0,1],[1,1],[1,0]]){for(const k of [0,1,2,0,2,3])vertex(data,points[k],Array.isArray(color[0])?color[k]:color,...uv[k]);}
function draw(data,mode,texture=white,primitive=gl.TRIANGLES){if(!data.length)return;gl.uniform1i(loc.PreviewMode,mode);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);gl.uniform1i(loc.PreviewTexture,0);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);gl.drawArrays(primitive,0,data.length/9);}
function cutTicks(){return ($('weapon').value.endsWith('longsword')?8:$('weapon').value.endsWith('domerite_sword')?12:10)*.30;}
function direction(){const d=+$('pose').value;return d===0?[-1,0,0]:d===1?[1,0,0]:d===2?[-Math.SQRT1_2,-Math.SQRT1_2,0]:[Math.SQRT1_2,-Math.SQRT1_2,0];}
function anchorsAt(t){const d=direction(),angle=(t-.5)*140*Math.PI/180,axis=[d[0]*Math.sin(angle),d[1]*Math.sin(angle),-Math.cos(angle)],root=[d[0]*(t-.5)*1.3,1.5+d[1]*(t-.5)*1.3,-.7];return [root,vadd(root,mul(axis,1.3))];}
function section(t){const [root,tip]=anchorsAt(t),normal=norm(cross([0,0,-1],direction())),local=norm([t-.003,t,t+.003].map(x=>sub(...anchorsAt(x).slice().reverse())).reduce((a,b)=>vadd(a,norm(b)),[0,0,0])),axis=mul(local,-Math.hypot(...sub(tip,root))),points=[];for(let j=0;j<=8;j++){const v=j/8,delta=mul(vadd(mul(sub(root,tip),1-effect.stability),mul(axis,effect.stability)),(1-v)*effect.width);points.push(vadd(vadd(tip,delta),mul(normal,Math.sin(v*Math.PI)*effect.curvature)));}return points;}
const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
function scene(now){requestAnimationFrame(scene);if(!effect||!gl)return;
 const delta=previousTime?Math.min(.1,(now-previousTime)/1000):0;previousTime=now;if(playing)time=(time+delta*20*+$('speed').value)%(cutTicks()+effect.lifetimeTicks+2);
 if(playing)$('seek').value=Math.min(1,time/cutTicks());
 const rect=canvas.getBoundingClientRect(),ratio=Math.min(2,devicePixelRatio||1),w=Math.round(rect.width*ratio),h=Math.round(rect.height*ratio);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}gl.viewport(0,0,w,h);
 const day=$('day').checked;gl.clearColor(...(day?[.44,.64,.86,1]:[.035,.055,.09,1]));gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.enable(gl.DEPTH_TEST);gl.depthMask(true);
 const target=[0,1.35,-1.25],eye=view==='first'?[0,1.5,0]:vadd(target,[Math.sin(yaw)*Math.cos(pitch)*distance,Math.sin(pitch)*distance,Math.cos(yaw)*Math.cos(pitch)*distance]);
 gl.uniformMatrix4fv(loc.ModelViewMat,false,lookAt(eye,view==='first'?[0,1.5,-2]:target));gl.uniformMatrix4fv(loc.ProjMat,false,perspective(w/h));gl.uniform4f(loc.ColorModulator,1,1,1,1);gl.uniform1f(loc.TrailTime,time/20);
 gl.uniform4f(loc.TrailShape,effect.coreWidth,effect.fill,effect.glow,effect.noise);gl.uniform4f(loc.TrailStyle,effect.brightness,effect.flowSpeed,effect.tailPower,0);
 const ground=[];quad(ground,[[-9,0,-9],[-9,0,6],[9,0,6],[9,0,-9]],day?[.27,.33,.23,1]:[.08,.12,.18,1]);draw(ground,3);
 const grid=[];for(let i=-8;i<=8;i++){vertex(grid,[i,.002,-8],[.2,.27,.35,1]);vertex(grid,[i,.002,6],[.2,.27,.35,1]);vertex(grid,[-8,.002,i],[.2,.27,.35,1]);vertex(grid,[8,.002,i],[.2,.27,.35,1]);}draw(grid,3,white,gl.LINES);
 const tint=[1,3,5].map(i=>parseInt(effect.color.slice(i,i+2),16)/255),trail=[],airTrail=[],sparks=[],end=Math.min(1,time/cutTicks()),start=Math.max(0,(time-effect.lifetimeTicks)/cutTicks()),count=48;
 for(let i=0;i<count;i++){
  const a=start+(end-start)*i/count,b=start+(end-start)*(i+1)/count;if(b-a<1e-6)continue;const pa=section(a),pb=section(b);
  const alpha=t=>effect.opacity*smooth(1-(time-t*cutTicks())/effect.lifetimeTicks)*smooth((t-start)/Math.max(.0001,(end-start)*.14))*smooth((end-t)/Math.max(.0001,(end-start)*.08));
  let rgb=effect.renderer==='texture'?tint.map(n=>Math.min(1,n*effect.brightness)):tint;
  const expand=points=>points.map(p=>vadd(points[0],mul(sub(p,points[0]),1.18))),ea=expand(pa),eb=expand(pb);
  const airAlpha=t=>effect.opacity*Math.sqrt(smooth(1-(time-t*cutTicks())/effect.lifetimeTicks))*smooth((t-start)/Math.max(.0001,(end-start)*.14))*smooth((end-t)/Math.max(.0001,(end-start)*.08));
  for(let j=0;j<8;j++){
   const uv=[[a,j/8],[a,(j+1)/8],[b,(j+1)/8],[b,j/8]];
   quad(trail,[pa[j],pa[j+1],pb[j+1],pb[j]],[[...rgb,alpha(a)],[...rgb,alpha(a)],[...rgb,alpha(b)],[...rgb,alpha(b)]],uv);
   quad(airTrail,[ea[j],ea[j+1],eb[j+1],eb[j]],[[1,1,1,airAlpha(a)],[1,1,1,airAlpha(a)],[1,1,1,airAlpha(b)],[1,1,1,airAlpha(b)]],uv);
  }
 }
 gl.depthMask(false);if(effect.refraction>0){gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,sceneTexture);gl.copyTexImage2D(gl.TEXTURE_2D,0,gl.RGB,0,0,w,h,0);gl.uniform1i(loc.ScreenTexture,1);gl.uniform2f(loc.AirScreenSize,w,h);gl.uniform4f(loc.AirStyle,effect.refraction,effect.dispersion,effect.noise,time/20);draw(airTrail,4);}draw(trail,effect.renderer==='texture'?1:0,trailTexture);
 const right=norm(cross(sub(view==='first'?[0,1.5,-2]:target,eye),[0,1,0])),up=norm(cross(right,sub(view==='first'?[0,1.5,-2]:target,eye)));
 for(let i=0;i<Math.round(28*effect.particles);i++){const birth=i/Math.max(1,28*effect.particles),age=time-birth*cutTicks();if(age<0||age>Math.min(5,effect.lifetimeTicks))continue;
  const [root,tip]=anchorsAt(birth),along=.5+(Math.sin(i*17.13)*.5+.5)*.46;let p=vadd(root,mul(sub(tip,root),along));p=vadd(p,mul(direction(),age*.025));p[1]+=age*.006;const size=.013+(i%7)*.002,a=effect.opacity*.85*smooth(1-age/Math.min(5,effect.lifetimeTicks));
  quad(sparks,[vadd(p,vadd(mul(right,-size),mul(up,-size))),vadd(p,vadd(mul(right,size),mul(up,-size))),vadd(p,vadd(mul(right,size),mul(up,size))),vadd(p,vadd(mul(right,-size),mul(up,size)))],[...tint,a],[[0,0],[1,0],[1,1],[0,1]]);
 }draw(sparks,2,sparkTexture);gl.depthMask(true);
 // Rigid, uniformly scaled sprite model with front/back faces and pixel contour thickness.
 if(time<cutTicks()+1.5&&swordTexture){const pose=anchorsAt(end),profile=itemData.profile[view==='first'?'firstPerson':'thirdPerson'],points=profile.points||itemData.profile.points;
  const frame=SlashSpriteGeometry.frame(pose,points,direction()),blade=[];
  for(const face of swordMesh)quad(blade,face.points.map(frame.map),[face.shade,face.shade,face.shade,1],face.uv);
  draw(blade,2,swordTexture);
 }

}
canvas.onpointerdown=e=>{if(view==='first')return;drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);};canvas.onpointermove=e=>{if(!drag)return;yaw+=(e.clientX-drag[0])*.007;pitch=Math.max(-1.3,Math.min(1.3,pitch+(e.clientY-drag[1])*.007));drag=[e.clientX,e.clientY];};canvas.onpointerup=canvas.onpointercancel=()=>drag=null;canvas.onwheel=e=>{e.preventDefault();distance=Math.max(2,Math.min(14,distance*Math.exp(e.deltaY*.001)));};
async function init(){try{if(!gl)throw Error('当前浏览器不支持 WebGL');const [s,meta]=await Promise.all([api('/api/effect-schema'),api('/api/items')]);schema=s;token=meta.token;$('gamePath').value=meta.gameDir;$('gameDir').textContent='当前游戏实例：'+meta.gameDir;initGL();sparkTexture=await imageTexture(schema.spark);await scanItems();await load();requestAnimationFrame(scene);}catch(e){status(e.stack,true);}}
init();
