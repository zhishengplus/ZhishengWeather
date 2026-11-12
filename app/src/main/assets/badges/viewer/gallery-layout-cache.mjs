/** Collection geometry changes with its content, not with its scroll position.
 * Keep exact CSS-pixel coordinates; moving the viewport only translates them.
 */
export class GalleryLayoutCache {
  constructor(){this.geometry=null;this.sampled=null;}
  invalidate(){this.geometry=null;this.sampled=null;}
  sample(scrollTop,height,measure){
    if(!this.geometry){
      this.geometry=measure().map(({id,value,rect:r})=>({id,value,
        x:r.left+r.width/2,y:r.top+r.height/2+scrollTop,scale:r.width*.88,
        top:r.top+scrollTop,bottom:r.bottom+scrollTop}));
    }
    if(this.sampled?.scrollTop===scrollTop&&this.sampled.height===height)return this.sampled;
    const rects=new Map(),near=[];
    for(const r of this.geometry){
      const point={x:r.x,y:r.y-scrollTop,scale:r.scale,top:r.top-scrollTop,bottom:r.bottom-scrollTop};
      rects.set(r.id,point);
      if(point.bottom>-120&&point.top<height+120)near.push(r.value);
    }
    return this.sampled={scrollTop,height,rects,near};
  }
}
