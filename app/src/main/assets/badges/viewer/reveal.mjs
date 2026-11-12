import {Quaternion,Vector3} from './vendor/three.module.js';

/** A single studio entrance. Input or lifecycle pause takes ownership immediately. */
export class BadgeReveal {
  active=false;
  start(pose,now,reducedMotion=false){
    this.active=!reducedMotion;this.startedAt=now;this.duration=1600;
    this.to=new Quaternion().fromArray(pose.q).normalize();
    this.from=new Quaternion().setFromAxisAngle(new Vector3(0,1,0),-1.15)
      .multiply(new Quaternion().setFromAxisAngle(new Vector3(1,0,0),.22)).multiply(this.to);
    this.current=this.active?this.from.clone():this.to.clone();
  }
  tick(now){
    if(!this.active)return null;
    const time=Math.min(1,Math.max(0,(now-this.startedAt)/this.duration));
    const eased=1-Math.pow(1-time,4);
    this.current.copy(this.from).slerp(this.to,eased).normalize();
    if(time===1)this.active=false;
    return this.current.toArray();
  }
  cancel(){if(!this.active)return null;this.active=false;return this.current.toArray();}
}
