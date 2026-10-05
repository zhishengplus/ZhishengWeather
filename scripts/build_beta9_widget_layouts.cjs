/* Legacy beta9 template generator. beta10.3 XML also has host-size adjustments
 * and roomy variants; do not overwrite those by running this script wholesale. */
const fs = require('node:fs');
const path = require('node:path');
const base = path.join(__dirname, '..', 'app', 'src', 'main', 'res');
function attrs(o) { return Object.entries(o).map(([k,v])=>'android:'+k+'="'+v+'"').join(' '); }
function view(tag, id, w, h, other={}, content='') {
 return '<'+tag+' '+attrs({...(id?{id:'@+id/'+id}:{}),layout_width:w,layout_height:h,...other})+'>'+content+'</'+tag+'>';
}
const linear=(id,w,h,o,c)=>view('LinearLayout',id,w,h,o,c);
const col=(id,w,h,o,c)=>linear(id,w,h,{orientation:'vertical',...o},c);
const row=(id,w,h,o,c)=>linear(id,w,h,{orientation:'horizontal',gravity:'center_vertical',...o},c);
const text=(id,size,o={})=>view('TextView',id,'wrap_content','wrap_content',{textSize:size+'sp',textColor:'#1B3346',fontFamily:'sans-serif',textStyle:'bold',includeFontPadding:'false',maxLines:'1',ellipsize:'end',...o});
const image=(id,w,h,o={})=>view('ImageView',id,w,h,{scaleType:'fitCenter',contentDescription:'',...o});
const gap=(height)=>view('TextView',null,'1dp',height,{importantForAccessibility:'no'});
const fluidGap=()=>view('TextView',null,'1dp','0dp',{layout_weight:'1',importantForAccessibility:'no'});
const clock=(size)=>view('TextClock','widget_time','match_parent','wrap_content',{format12Hour:'h:mm',format24Hour:'HH:mm',fontFamily:'sans-serif',textStyle:'bold',textSize:size+'sp',includeFontPadding:'false',textColor:'#1B3346',maxLines:'1'});
const date=()=>view('TextClock','widget_date','match_parent','wrap_content',{format12Hour:'M月d日 E',format24Hour:'M月d日 E',fontFamily:'sans-serif',textStyle:'normal',textSize:'12sp',includeFontPadding:'false',textColor:'#496171',maxLines:'1'});
// beta9 final: alarm indicator removed from the visual language; time remains a click target.
const alarm=()=>'';
// The home screen should read as a weather card, not as a control panel. Refresh and
// settings remain available through the widget actions, but their glyphs are hidden.
const tools=()=>row(null,'match_parent','18dp',{},text('widget_footer',10,{layout_width:'0dp',layout_weight:'1',fontFamily:'sans-serif'})+image('widget_refresh','1dp','1dp',{visibility:'gone',src:'@drawable/ph_arrow_clockwise'})+image('widget_edit','1dp','1dp',{visibility:'gone',src:'@drawable/ph_gear'}));
const city=()=>row('widget_city_hit','match_parent','24dp',{},image('widget_pin','11dp','11dp',{src:'@drawable/ph_map_pin'})+text('widget_city',12,{layout_marginStart:'4dp',layout_width:'0dp',layout_weight:'1'})+text('widget_footer',9,{layout_marginStart:'8dp',fontFamily:'sans-serif',textStyle:'normal'})+text('widget_badge',10));
const hero=(size,iconSize)=>row('widget_weather_hit','match_parent','wrap_content',{},col(null,'0dp','wrap_content',{layout_weight:'1'},text('widget_temp',size,{fontFamily:'sans-serif-medium'})+text('widget_condition',12,{layout_marginTop:'3dp',fontFamily:'sans-serif'}))+image('widget_icon',iconSize+'dp',iconSize+'dp'));
const metrics=()=>row('widget_metrics','match_parent','42dp',{},[1,2,3].map(i=>col(null,'0dp','match_parent',{layout_weight:'1',gravity:'center_vertical'},text('widget_metric_label_'+i,10)+text('widget_metric_'+i,14,{layout_marginTop:'3dp'}))).join(''));
const forecast=()=>linear('widget_forecast','match_parent','0dp',{orientation:'horizontal',gravity:'center_vertical',layout_weight:'1'},'');
function layout(style,kind,compact=false) {
 const classic=style==='classic';
 let content='';
 if(kind==='pulse') content=row(null,'match_parent','match_parent',{},
   col('widget_clock_hit','0dp','match_parent',{layout_weight:'1.34',gravity:'center_vertical',paddingStart:'6dp'},clock(36)+date())+
   col('widget_weather_hit','0dp','match_parent',{layout_weight:'1',gravity:'center_vertical|end',layout_marginStart:'12dp'},row(null,'wrap_content','wrap_content',{gravity:'center_vertical|end'},image('widget_icon','42dp','42dp')+col(null,'wrap_content','wrap_content',{layout_marginStart:'7dp',gravity:'end'},text('widget_temp',28)+text('widget_city',11,{layout_marginTop:'2dp',fontFamily:'sans-serif'}))))+
   image('widget_edit','1dp','1dp',{visibility:'gone',src:'@drawable/ph_gear'}));
 if(kind==='now') {
   const nowCity=row('widget_city_hit','match_parent','16dp',{},
     text('widget_city',compact?11:12,{layout_width:'0dp',layout_weight:'1'})+
     (compact?'':text('widget_footer',9,{layout_marginStart:'5dp',fontFamily:'sans-serif',textStyle:'normal'})));
   const nowHero=row('widget_weather_hit','match_parent',compact?'40dp':'58dp',{},
     text('widget_temp',compact?32:40,{layout_width:'0dp',layout_weight:'1'})+
     image('widget_icon',compact?'34dp':'52dp',compact?'34dp':'52dp'));
   if(compact) content=col(null,'match_parent','match_parent',{},nowCity+nowHero+
     text('widget_condition',11,{layout_width:'match_parent',layout_height:'16dp',gravity:'center_vertical'})+
     text('widget_range',11,{layout_width:'match_parent',layout_height:'17dp',gravity:'center_vertical'}));
   else {
     const flex=()=>view('TextView',null,'1dp','0dp',{layout_weight:'1',importantForAccessibility:'no'});
     const weatherLine=row(null,'match_parent','20dp',{},
       text('widget_condition',12,{layout_width:'0dp',layout_weight:'1'})+
       text('widget_range',11,{fontFamily:'sans-serif',textStyle:'normal'}));
     const readings=row('widget_metrics','match_parent','28dp',{},[1,2].map(i=>
       row(null,'0dp','match_parent',{layout_weight:'1'},
         text('widget_metric_label_'+i,10,{fontFamily:'sans-serif',textStyle:'normal'})+
         text('widget_metric_'+i,13,{layout_marginStart:'4dp'}))).join(''));
     content=col(null,'match_parent','match_parent',{},nowCity+flex()+nowHero+weatherLine+flex()+readings);
   }
 }
 if(kind==='band') content=col(null,'match_parent','match_parent',{},row(null,'match_parent','48dp',{},col('widget_weather_hit','0dp','match_parent',{layout_weight:'1',gravity:'center_vertical'},row(null,'wrap_content','wrap_content',{},text('widget_temp',32)+text('widget_condition',11,{layout_marginStart:'6dp',fontFamily:'sans-serif'})))+col(null,'wrap_content','match_parent',{gravity:'end',layout_marginEnd:'7dp'},text('widget_city',12)+text('widget_range',11,{layout_marginTop:'4dp',fontFamily:'sans-serif'})+text('widget_footer',9,{layout_marginTop:'2dp',fontFamily:'sans-serif',textStyle:'normal'}))+image('widget_icon','48dp','48dp'))+forecast());
 if(kind==='week'&&!classic) {
   const weekWeather=col('widget_weather_hit','0dp','match_parent',{layout_weight:'1',gravity:'center_vertical|end'},
     row(null,'wrap_content','wrap_content',{},image('widget_icon','54dp','54dp')+col(null,'wrap_content','wrap_content',{layout_marginStart:'6dp',gravity:'end'},text('widget_temp',28)+text('widget_city',11,{layout_marginTop:'2dp',fontFamily:'sans-serif'})))+text('widget_footer',9,{layout_marginTop:'4dp',fontFamily:'sans-serif',textStyle:'normal'}));
   const weekHero=row(null,'match_parent','86dp',{},
     col('widget_clock_hit','0dp','match_parent',{layout_weight:'1.25',gravity:'center_vertical'},clock(42)+date()+text('widget_lunar',10,{layout_marginTop:'1dp',fontFamily:'sans-serif'}))+weekWeather);
   content=col(null,'match_parent','match_parent',{},weekHero+forecast());
 }
 if(kind==='week'&&classic) content=col(null,'match_parent','match_parent',{},row(null,'match_parent','26dp',{},text('widget_city',12,{layout_width:'0dp',layout_weight:'1'})+text('widget_footer',9,{layout_marginEnd:'10dp',fontFamily:'sans-serif',textStyle:'normal'})+text('widget_range',12))+linear('widget_forecast','match_parent','0dp',{orientation:'vertical',layout_weight:'1'},''));
 if(kind==='scene') {
   const sceneHero=row('widget_scene_hero','match_parent',compact?'64dp':'82dp',{},
     col('widget_clock_hit','0dp','match_parent',{layout_weight:'1.05',gravity:'center_vertical'},clock(compact?32:39)+date()+text('widget_lunar',10,{layout_marginTop:'2dp',fontFamily:'sans-serif'}))+
     col('widget_weather_hit','0dp','match_parent',{layout_weight:'1',gravity:'center_vertical|end'},
       row(null,'wrap_content',compact?'37dp':'48dp',{gravity:'center_vertical|end'},image('widget_icon',compact?'36dp':'46dp',compact?'36dp':'46dp')+text('widget_temp',compact?28:34,{layout_marginStart:'4dp'}))+
       row(null,'wrap_content','18dp',{gravity:'end'},text('widget_condition',11,{fontFamily:'sans-serif'})+text('widget_range',10,{layout_marginStart:'7dp',fontFamily:'sans-serif'}))));
   const sceneBriefing=row('widget_scene_briefing','match_parent',compact?'36dp':'40dp',{gravity:'center_vertical',paddingStart:'5dp',paddingEnd:'7dp'},
     view('TextView',null,'2dp',compact?'17dp':'23dp',{background:classic?'#A5C9B6':'#527F98'})+
     view('TextView',null,'8dp','1dp')+
     text('widget_scene_briefing_text',compact?11:12,{layout_width:'0dp',layout_weight:'1',maxLines:'2',textStyle:'normal',fontFamily:'sans-serif-medium'})+
     image('widget_scene_girl',compact?'27dp':'34dp',compact?'27dp':'34dp',{layout_marginStart:'4dp',visibility:'gone'}));
   const metricRow=(start)=>row(null,'match_parent',compact?'32dp':'34dp',{},[start,start+1,start+2].map(i=>
     row('widget_metric_hit_'+i,'0dp','match_parent',{layout_weight:'1',gravity:'center_vertical'},
       col(null,'match_parent','wrap_content',{},text('widget_metric_label_'+i,9,{fontFamily:'sans-serif',textStyle:'normal'})+
         text('widget_metric_'+i,compact?9:11,{layout_marginTop:'1dp'})))).join(''));
   const sceneMetrics=col('widget_metrics','match_parent',compact?'66dp':'70dp',{},metricRow(1)+gap('2dp')+metricRow(4));
   const title=(id,label)=>text(id,10,{text:label,fontFamily:'sans-serif-medium',layout_width:'match_parent'});
   const sceneHours=compact?'':gap('7dp')+title('widget_scene_hourly_title','接下来几小时')+
     linear('widget_hourly_forecast','match_parent','70dp',{orientation:'horizontal',gravity:'center_vertical'},'');
   const sceneDays=gap(compact?'3dp':'7dp')+title('widget_scene_day_title','未来五天 · 日历')+
     linear('widget_forecast','match_parent',compact?'54dp':'66dp',{orientation:'horizontal',gravity:'center_vertical'},'');
   content=col(null,'match_parent','match_parent',{},city()+gap('3dp')+sceneHero+gap('4dp')+sceneBriefing+
     gap(compact?'3dp':'7dp')+sceneMetrics+sceneHours+sceneDays);
 }
 const shape=classic?'widget_classic_surface':'widget_vista_surface';
 const hidden=text('widget_alarm_hit',1,{visibility:'gone'})+text('widget_alarm',1,{visibility:'gone'})+text('widget_alarm_label',1,{visibility:'gone'})+
   text('widget_secondary',1,{visibility:'gone'})+
   (kind==='now'?text('widget_air',1,{visibility:'gone'}):'');
 const root='<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" '+attrs({id:'@+id/widget_root',layout_width:'match_parent',layout_height:'match_parent'})+'>'+
 image('widget_surface','match_parent','match_parent',{src:'@drawable/'+shape,scaleType:'fitXY',...(kind==='now'?{layout_gravity:'center'}:{})})+
 col('widget_content','match_parent','match_parent',{padding:kind==='pulse'?'12dp':classic?'14dp':'16dp',...(kind==='now'?{layout_gravity:'center'}:{})},content)+hidden+'</FrameLayout>';
 let result = root;
 if(kind==='pulse') result=result.replace('android:padding="12dp"','android:padding="10dp"');
 if(kind==='now') result=result.replace(/android:padding="\d+dp"/,'android:padding="8dp"');
 if(kind==='scene') result=result.replace(/android:padding="\d+dp"/,'android:padding="16dp"');
 if(compact) {
   result=result.replace(/android:padding="\d+dp"/g,'android:padding="'+(kind==='scene'?'16':'8')+'dp"');
   if(kind==='pulse') result=result.replaceAll('42dp','32dp');
   if(kind==='band') result=result.replace('android:layout_height="48dp"','android:layout_height="40dp"').replaceAll('48dp','36dp')
       .replace('android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal"','android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical"')
       .replace('android:layout_marginStart="6dp"','android:layout_marginStart="0dp"');
   if(kind==='week'&&!classic) result=result.replace('android:layout_height="86dp"','android:layout_height="62dp"').replaceAll('54dp','34dp');
   if(kind==='week'&&classic) result=result.replace('android:layout_height="26dp"','android:layout_height="20dp"');
   // Compact cards reserve the full remaining height for forecasts.
   if(kind==='band'||kind==='week'&&classic) result=result.replace(/<LinearLayout android:layout_width="match_parent" android:layout_height="18dp"[^>]*>.*?<\/LinearLayout>/,'');
 }
 // Classic keeps the same readable weight but changes its palette and geometry.
 return '<?xml version="1.0" encoding="utf-8"?>\n'+result.replaceAll('><','>\n<')+'\n';
}
for (const style of ['vista','classic']) for (const kind of ['pulse','now','band','week','scene']) {
 for(const compact of [false,true]) {
 const prefix='widget_'+style+'_'+kind+(compact?'_compact':'');
 fs.writeFileSync(path.join(base,'layout',prefix+'.xml'),layout(style,kind,compact));
 for(const tone of ['light','dark']) {
  const shadow=layout(style,kind,compact).replace(/ android:shadow(Color|Radius|Dy)="[^"]*"/g,'').replace(/<(TextView|TextClock) /g, '<$1 android:shadowColor="'+(tone==='light'?'#B8000000':'#33000000')+'" android:shadowRadius="1.5" android:shadowDy="0.5" ');
  fs.writeFileSync(path.join(base,'layout',prefix+'_legible_'+tone+'.xml'),shadow);
 }
 }
 const [w,h,c,r]=kind==='pulse'?[250,60,4,1]:kind==='now'?[110,110,2,2]:kind==='scene'?[250,280,4,4]:[250,kind==='week'?140:120,4,2];
 fs.writeFileSync(path.join(base,'xml','widget_'+style+'_'+kind+'.xml'),'<?xml version="1.0" encoding="utf-8"?>\n<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android" '+attrs({
 minWidth:w+'dp',minHeight:h+'dp',minResizeWidth:w+'dp',minResizeHeight:h+'dp',targetCellWidth:c,targetCellHeight:r,updatePeriodMillis:'0',resizeMode:'horizontal|vertical',widgetCategory:'home_screen',widgetFeatures:'reconfigurable',initialLayout:'@layout/widget_'+style+'_'+kind,previewLayout:'@layout/widget_'+style+'_'+kind,configure:'com.zhisheng.weather.widget.WidgetConfigActivity'
 })+' />\n');
}
const hour=col('widget_forecast_cell','0dp','match_parent',{layout_weight:'1',gravity:'center'},text('widget_cell_label',10,{gravity:'center'})+image('widget_cell_icon','30dp','30dp',{layout_marginTop:'1dp'})+text('widget_cell_temp',11,{layout_marginTop:'1dp'})+text('widget_cell_extra',9,{layout_marginTop:'1dp'}));
const weekDay=(align)=>col('widget_forecast_cell','0dp','match_parent',{layout_weight:'1',gravity:'center_vertical|'+align},text('widget_cell_label',11,{gravity:align})+image('widget_cell_icon','36dp','36dp',{layout_marginTop:'1dp'})+text('widget_cell_temp',11,{layout_marginTop:'1dp'}));
const dayrow=row('widget_forecast_cell','match_parent','0dp',{layout_weight:'1'},text('widget_cell_label',12,{layout_width:'42dp'})+image('widget_cell_icon','22dp','22dp')+text('widget_cell_temp',12,{layout_width:'65dp',gravity:'center'})+image('widget_cell_chart','0dp','8dp',{layout_weight:'1',scaleType:'fitXY'})+text('widget_cell_extra',10,{layout_width:'38dp',gravity:'end'}));
const sceneDay=(compact)=>col('widget_forecast_cell','0dp','match_parent',{layout_weight:'1',gravity:'center'},
 text('widget_cell_label',10,{layout_width:'match_parent',gravity:'center'})+
 image('widget_cell_icon',compact?'25dp':'34dp',compact?'25dp':'34dp',{layout_marginTop:'1dp'})+
 text('widget_cell_temp',compact?8:9,{layout_marginTop:'1dp',layout_width:'match_parent',gravity:'center'}));
for(const compact of [false,true]) fs.writeFileSync(path.join(base,'layout','widget_forecast_scene'+(compact?'_compact':'')+'.xml'),
 '<?xml version="1.0" encoding="utf-8"?>\n'+sceneDay(compact).replace('<LinearLayout','<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"').replaceAll('><','>\n<')+'\n');
for(const [name,xml] of [['column',hour],['week',weekDay('center')],['week_start',weekDay('start')],['week_end',weekDay('end')],['row',dayrow]]) for(const compact of [false,true]) {
 const content=compact?xml.replaceAll('30dp','24dp').replaceAll('32dp','24dp').replaceAll('36dp','24dp').replaceAll('22dp','18dp'):xml;
 fs.writeFileSync(path.join(base,'layout','widget_forecast_'+name+(compact?'_compact':'')+'.xml'),'<?xml version="1.0" encoding="utf-8"?>\n'+content.replace('<LinearLayout','<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"').replaceAll('><','>\n<')+'\n');
}
// The system owns notification background and text colors, including dark mode.
const nt=(id,size,bold=false,other={})=>view('TextView',id,'wrap_content','wrap_content',{textAppearance:'@style/TextAppearance.Compat.Notification'+(bold?'.Title':''),textSize:size+'sp',fontFamily:'sans-serif',textStyle:bold?'bold':'normal',includeFontPadding:'false',maxLines:'1',ellipsize:'end',...other});
const notificationHeader=(compact)=>row(null,'match_parent',compact?'48dp':'64dp',{},
 image('notification_icon',compact?'40dp':'48dp',compact?'40dp':'48dp')+
 col(null,'wrap_content','wrap_content',{gravity:'center',layout_marginStart:'4dp',layout_marginEnd:'12dp'},nt('notification_temp',compact?28:34,true)+(compact?'':nt('notification_today_range',11,false,{layout_marginTop:'2dp'})))+
 col(null,'0dp','wrap_content',{layout_weight:'1',gravity:'end'},
 nt('notification_title',compact?13:15,true,{layout_width:'match_parent',gravity:'end'})+
 nt('notification_condition',12,false,{layout_width:'match_parent',gravity:'end',layout_marginTop:'3dp'})+(compact?'':nt('notification_update',10,false,{layout_marginTop:'3dp',gravity:'end'}))));
const notificationDays=row(null,'match_parent','72dp',{},[1,2,3].map(i=>col(null,'0dp','match_parent',{layout_weight:'1',gravity:'center'},
 nt('notification_day_'+i,11)+image('notification_icon_'+i,'32dp','32dp',{layout_marginTop:'3dp'})+nt('notification_range_'+i,12,true,{layout_marginTop:'3dp'}))).join(''));
const notificationMetrics=[0,1].map(r=>row(null,'match_parent','40dp',{},[1,2,3].map(c=>{
 const i=r*3+c;
 return col(null,'0dp','match_parent',{layout_weight:'1',gravity:'center'},nt('notification_metric_label_'+i,10)+nt('notification_metric_'+i,12,true,{layout_marginTop:'4dp'}));
}).join(''))).join('');
const notificationDivider=()=>image(null,'match_parent','1dp',{background:'#22808080',layout_marginTop:'4dp',layout_marginBottom:'4dp'});
for(const compact of [true,false]) {
 const body=notificationHeader(compact)+(compact?'':notificationDivider()+notificationMetrics+notificationDivider()+notificationDays);
 const xml=col(null,'match_parent','wrap_content',{},body).replace('<LinearLayout','<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"');
 fs.writeFileSync(path.join(base,'layout',compact?'widget_notification_compact.xml':'widget_notification.xml'),'<?xml version="1.0" encoding="utf-8"?>\n'+xml.replaceAll('><','>\n<')+'\n');
}
console.log('Generated 10 widgets, provider definitions, forecast cells and notification layouts.');
