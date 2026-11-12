package com.zhisheng.weather.ui.badge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import com.zhisheng.weather.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.components.WeatherDialogWindow
import com.zhisheng.weather.ui.theme.LocalInterfaceStyle
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengMono

private data class BadgeChromeColors(val classic: Boolean, val dark: Boolean, val ink: Color, val muted: Color,
    val accent: Color, val panel: Color, val edge: Color)
@Composable private fun chrome(): BadgeChromeColors {
    val classic=LocalInterfaceStyle.current==InterfaceStyle.CLASSIC_TERMINAL
    val dark=classic||!LocalZhishengPalette.current.isLight
    return BadgeChromeColors(classic,dark,if(dark)Color(0xFFE5F0E9)else Color(0xFF193C33),
        if(dark)Color(0xFF9AB4A5)else Color(0xFF55786A),if(dark)Color(0xFF9AD7AE)else Color(0xFF245B48),
        if(classic)Color(0xFF0B1410)else if(dark)Color(0xFF162B23)else Color(0xFFF0F8F2),
        if(classic)Color(0xFF355443)else if(dark)Color(0xFF4B6E5D)else Color.White)
}
private val LocalBadgePrimary = staticCompositionLocalOf { false }

/** Transparent host window; secure-policy and dismissal semantics stay with each caller. */
@Composable
internal fun BadgeDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null, properties: DialogProperties = DialogProperties()) {
    Dialog(onDismissRequest,properties=DialogProperties(dismissOnBackPress=properties.dismissOnBackPress,
        dismissOnClickOutside=properties.dismissOnClickOutside,securePolicy=properties.securePolicy,
        usePlatformDefaultWidth=true,decorFitsSystemWindows=false)) {
        WeatherDialogWindow(overlay=true)
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color(0xFF04110B).copy(alpha=.48f))
                .clickable(enabled=properties.dismissOnClickOutside,interactionSource=remember { MutableInteractionSource() },indication=null,onClick=onDismissRequest))
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(18.dp),contentAlignment=Alignment.Center) {
                BadgeDialogPanel(title,text,confirmButton,dismissButton,
                    Modifier.widthIn(max=430.dp).fillMaxWidth().heightIn(max=maxHeight)
                        .pointerInput(Unit) { detectTapGestures(onTap={}) })
            }
        }
    }
}

@Composable
internal fun BadgeDialogPanel(title: @Composable () -> Unit, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier) {
    val c=chrome()
    val shape=if(c.classic)CutCornerShape(topEnd=18.dp,bottomStart=10.dp)else RoundedCornerShape(28.dp)
    CompositionLocalProvider(LocalContentColor provides c.ink) {
        BoxWithConstraints(modifier.shadow(if(c.classic)12.dp else 24.dp,shape).clip(shape)
            .background(Brush.linearGradient(listOf(c.panel,if(c.dark)c.panel.copy(alpha=.97f)else Color(0xFFDDECE2))))
            .border(.8.dp,Brush.linearGradient(listOf(c.edge,c.edge.copy(alpha=.28f),c.edge.copy(alpha=.65f))),shape)) {
          val compact=maxHeight<400.dp
          // Short windows (landscape or IME) scroll the whole panel so the field never gets zero height.
          Column(Modifier.fillMaxWidth().then(if(compact)Modifier.verticalScroll(rememberScrollState())else Modifier)
            .padding(if(compact)18.dp else 24.dp)) {
            Box(Modifier.width(26.dp).height(2.dp).background(c.accent.copy(alpha=.65f)))
            Spacer(Modifier.height(16.dp))
            ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold)) { title() }
            Spacer(Modifier.height(18.dp))
            Box(if(compact)Modifier else Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color=c.muted,fontSize=14.sp,lineHeight=23.sp)) { text() }
            }
            Spacer(Modifier.height(24.dp))
            // Stacked full-width actions remain readable at large font sizes and with the IME open.
            CompositionLocalProvider(LocalBadgePrimary provides true) { Box(Modifier.fillMaxWidth()) { confirmButton() } }
            dismissButton?.let { Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { it() } }
          }
        }
    }
}

@Composable
internal fun BadgeAction(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = LocalBadgePrimary.current, contentPadding: PaddingValues = PaddingValues(horizontal=18.dp,vertical=12.dp),
    content: @Composable RowScope.() -> Unit) {
    val c=chrome();val interaction=remember { MutableInteractionSource() };val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).97f else 1f,tween(100),label="badge-action-press")
    val shape=if(c.classic)CutCornerShape(topEnd=8.dp,bottomStart=5.dp)else RoundedCornerShape(16.dp)
    val fill=if(primary)c.accent else c.accent.copy(alpha=if(c.dark).08f else .055f)
    val ink=if(primary&&c.dark)Color(0xFF0B1C12)else if(primary)Color(0xFFF4FFF6)else c.accent
    Row(modifier.then(if(primary)Modifier.fillMaxWidth()else Modifier).heightIn(min=48.dp)
        .graphicsLayer { scaleX=scale;scaleY=scale }.alpha(if(enabled)1f else .38f).clip(shape)
        .background(fill).border(.7.dp,c.accent.copy(alpha=if(primary).15f else .16f),shape)
        .clickable(enabled=enabled,role=Role.Button,interactionSource=interaction,indication=null,onClick=onClick).padding(contentPadding),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize=14.sp,fontWeight=FontWeight.Medium)) { content() }
        }
    }
}

@Composable
internal fun BadgeIconControl(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c=chrome();val shape=if(c.classic)CutCornerShape(10.dp)else RoundedCornerShape(24.dp)
    val interaction=remember { MutableInteractionSource() };val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).92f else 1f,tween(100),label="badge-icon-press")
    Box(modifier.size(48.dp).graphicsLayer { scaleX=scale;scaleY=scale }.clip(shape)
        .background(Brush.linearGradient(listOf(c.edge.copy(alpha=if(c.dark).08f else .55f),c.accent.copy(alpha=.035f))))
        .border(.7.dp,c.edge.copy(alpha=.45f),shape)
        .clickable(role=Role.Button,interactionSource=interaction,indication=null,onClick=onClick),contentAlignment=Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides c.ink) { content() }
    }
}

@Composable
internal fun BadgeMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if(expanded)BadgeDialog(onDismiss,title={Text("徽章管理")},text={Column(Modifier.fillMaxWidth()) { content() }},
        confirmButton={BadgeAction(onDismiss) { Text("完成") }})
}

@Composable
internal fun BadgeMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, enabled: Boolean = true) {
    val c=chrome();val interaction=remember { MutableInteractionSource() };val pressed by interaction.collectIsPressedAsState()
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(if(c.classic)4.dp else 12.dp))
        .background(c.accent.copy(alpha=if(pressed).1f else 0f)).alpha(if(enabled)1f else .38f)
        .clickable(enabled=enabled,role=Role.Button,interactionSource=interaction,indication=null,onClick=onClick).padding(horizontal=10.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(14.dp).background(c.accent.copy(alpha=.42f)))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color=c.ink,fontSize=15.sp)) { text() } }
        Canvas(Modifier.padding(start=12.dp).size(12.dp)) {
            val x=size.width*.35f;val y=size.height*.2f
            drawLine(c.muted,Offset(x,y),Offset(size.width*.65f,size.height*.5f),1.dp.toPx())
            drawLine(c.muted,Offset(size.width*.65f,size.height*.5f),Offset(x,size.height*.8f),1.dp.toPx())
        }
    }
}

@Composable
internal fun BadgeCodeField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    BadgeInputField(value, onValueChange, label, modifier, secret = true)
}

@Composable
internal fun BadgeIdField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    BadgeInputField(value, onValueChange, label, modifier, secret = false)
}

@Composable
private fun BadgeInputField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier, secret: Boolean) {
    val c=chrome();val interaction=remember { MutableInteractionSource() };val focused by interaction.collectIsFocusedAsState()
    val shape=RoundedCornerShape(if(c.classic)6.dp else 16.dp)
    Column(modifier.padding(top=14.dp)) {
        Text(label,color=c.muted,fontSize=12.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(value,onValueChange,modifier=Modifier.fillMaxWidth().heightIn(min=58.dp).semantics { contentDescription=label }.clip(shape)
            .background(if(c.dark)Color.Black.copy(alpha=.22f)else Color.White.copy(alpha=.55f))
            .border(if(focused)1.dp else .6.dp,c.accent.copy(alpha=if(focused).7f else .22f),shape).padding(16.dp),
            textStyle=MaterialTheme.typography.bodyMedium.copy(color=c.ink,fontSize=15.sp,
                fontFamily=if(secret)ZhishengMono else MaterialTheme.typography.bodyMedium.fontFamily),
            singleLine=true,visualTransformation=if(secret)PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource=interaction,cursorBrush=SolidColor(c.accent),
            keyboardOptions=KeyboardOptions(keyboardType=if(secret)KeyboardType.Password else KeyboardType.Text),
            decorationBox={inner->Box { if(value.isEmpty())Text("粘贴或输入",color=c.muted.copy(alpha=.65f),fontSize=14.sp);inner() }})
    }
}

@Composable
internal fun BadgePreparing() {
    val c=chrome()
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(if(i==1)5.dp else 3.dp).clip(RoundedCornerShape(3.dp))
                .background(c.accent.copy(alpha=if(i==1).8f else .35f)))
        }
        Text("正在准备徽章",color=c.muted,fontSize=12.sp,modifier=Modifier.padding(start=5.dp))
    }
}
