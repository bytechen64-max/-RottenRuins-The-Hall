const assert=require('node:assert/strict');
const geometry=require('../slash-anchor-editor/sprite-geometry.js');
const sub=(a,b)=>a.map((n,i)=>n-b[i]),dot=(a,b)=>a.reduce((n,v,i)=>n+v*b[i],0),distance=(a,b)=>Math.hypot(...sub(a,b));
const near=(a,b)=>assert(Math.abs(a-b)<1e-8,`${a} != ${b}`);
for(const d of [[-1,0,0],[1,0,0],[-Math.SQRT1_2,-Math.SQRT1_2,0],[Math.SQRT1_2,-Math.SQRT1_2,0]]){
 for(let i=0;i<=100;i++){
  const angle=(-70+i*1.4)*Math.PI/180,root=[d[0]*.5,d[1]*.5,-.7],tip=root.map((v,j)=>v+1.3*[d[0]*Math.sin(angle),d[1]*Math.sin(angle),-Math.cos(angle)][j]);
  for(const anchors of [[[7.75,4.25,8],[14.25,15.25,8]],[[1,1,5],[4,10,12]]]){
   const f=geometry.frame([root,tip],anchors,d);
   near(dot(f.blade,f.edge),0);near(dot(f.edge,f.normal),0);near(dot(f.blade,f.normal),0);
   near(distance(f.map(anchors[0]),root),0);near(distance(f.map(anchors[1]),tip),0);
   const a=[0,0,8],b=[16,0,8],c=[0,16,8];
   near(distance(f.map(a),f.map(b)),16*f.scale);near(distance(f.map(a),f.map(c)),16*f.scale);
   near(dot(sub(f.map(b),f.map(a)),sub(f.map(c),f.map(a))),0);
  }
 }
}
const one=geometry.mesh(1,1,new Uint8Array([255]));assert.equal(one.length,6);
const adjacent=geometry.mesh(2,1,new Uint8Array([255,255]));assert.equal(adjacent.length,8,'Shared opaque pixel edges must not create internal walls');
assert.equal(geometry.mesh(2,2,new Uint8Array(4)).length,2);
for(const q of adjacent){assert(q.points.every(p=>p.every(Number.isFinite)));assert(q.uv.flat().every(n=>n>=0&&n<=1));}
console.log('PASS: rigid uniform sprite mapping through four 140-degree cuts, exact anchors, real contour thickness, no internal pixel walls');
