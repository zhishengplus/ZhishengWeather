/** First award uses the collection's own instance, invitation and continuous flight. */
export class BadgeAwardFlow {
  constructor(){this.entered=false;this.sent=false;this.visibleMillis=0;}
  pause(){this.visibleMillis=0;this.sent=false;}
  step({active,presented,ready,cued,phase,dt,acknowledged=false}){
    if(!active||!presented||!ready||acknowledged)return null;
    if(phase==='overview'&&cued&&!this.entered){this.entered=true;return 'enter';}
    if(phase!=='inspect'){this.visibleMillis=0;return null;}
    this.entered=true;
    this.visibleMillis+=Math.max(0,Math.min(100,dt));
    if(this.visibleMillis>=240&&!this.sent){this.sent=true;return 'presented';}
    return null;
  }
  waiting(phase){return phase==='inspect'&&!this.sent;}
}
