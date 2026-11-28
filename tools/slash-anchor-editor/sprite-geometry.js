'use strict';
// Shared by the WebGL preview and its geometry regression checks.
(function(root){
 const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),mul=(a,s)=>a.map(v=>v*s);
 const dot=(a,b)=>a.reduce((sum,v,i)=>sum+v*b[i],0),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
 const unit=v=>mul(v,1/(Math.hypot(...v)||1));
 function frame(pose,anchors,direction){
  const a=anchors[0],b=anchors[anchors.length-1],sourceBlade=unit(sub(b,a));
  const sourceEdge=unit(cross(sourceBlade,Math.abs(sourceBlade[2])<.9?[0,0,1]:[0,1,0]));
  const sourceNormal=unit(cross(sourceEdge,sourceBlade));
  const blade=unit(sub(pose[1],pose[0])),normal=unit(cross(direction,[0,0,-1]));
  // The edge must rotate WITH the blade. A fixed sweep direction becomes
  // parallel to the blade near either endpoint and squashes the item to a line.
  const edge=unit(cross(blade,normal)),scale=Math.hypot(...sub(pose[1],pose[0]))/Math.max(.0001,Math.hypot(...sub(b,a)));
  const map=p=>{const delta=sub(p,a);return add(pose[0],mul(add(add(mul(blade,dot(delta,sourceBlade)),mul(edge,dot(delta,sourceEdge))),mul(normal,dot(delta,sourceNormal))),scale));};
  return {map,blade,edge,normal,scale};
 }
 function mesh(width,height,alpha){
  const quads=[],front=8.5,back=7.5;
  quads.push({points:[[0,0,front],[16,0,front],[16,16,front],[0,16,front]],uv:[[0,1],[1,1],[1,0],[0,0]],shade:1});
  quads.push({points:[[16,0,back],[0,0,back],[0,16,back],[16,16,back]],uv:[[1,1],[0,1],[0,0],[1,0]],shade:.92});
  const opaque=(x,y)=>x>=0&&y>=0&&x<width&&y<height&&alpha[y*width+x]>=16;
  // Only expose pixel boundaries, like a generated Minecraft item model.
  for(let y=0;y<height;y++)for(let x=0;x<width;x++){
   if(!opaque(x,y))continue;
   const l=x/width*16,r=(x+1)/width*16,t=16-y/height*16,b=16-(y+1)/height*16;
   const uv=Array.from({length:4},()=>[(x+.5)/width,(y+.5)/height]);
   const side=points=>quads.push({points,uv,shade:.72});
   if(!opaque(x-1,y))side([[l,b,back],[l,b,front],[l,t,front],[l,t,back]]);
   if(!opaque(x+1,y))side([[r,b,front],[r,b,back],[r,t,back],[r,t,front]]);
   if(!opaque(x,y-1))side([[l,t,front],[r,t,front],[r,t,back],[l,t,back]]);
   if(!opaque(x,y+1))side([[l,b,back],[r,b,back],[r,b,front],[l,b,front]]);
  }
  return quads;
 }
 const api={frame,mesh};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.SlashSpriteGeometry=api;
})(globalThis);
