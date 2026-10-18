package io.github.dovecoteescapee.byedpi.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** A real focusable button. All artwork is a native vector-like shield; no web view. */
class ShieldButton @JvmOverloads constructor(context:Context, attrs:AttributeSet?=null):View(context,attrs) {
    var connected=false;set(value){field=value;invalidate()}
    var busy=false;set(value){field=value;invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init {isClickable=true;isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val d=resources.displayMetrics.density;val cx=width/2f;val cy=height/2f
        val radius=minOf(width/2f-12*d,height/2f-8*d)
        paint.style=Paint.Style.FILL;paint.color=Color.parseColor(if(connected) "#493B1E" else "#29251B");paint.alpha=if(isPressed)255 else 230
        canvas.drawCircle(cx,cy,radius,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;paint.color=Color.parseColor(if(connected) "#FFD15C" else "#A58A43");paint.alpha=255
        canvas.drawCircle(cx,cy,radius-1*d,paint)
        paint.style=Paint.Style.FILL;paint.color=Color.parseColor("#171717");canvas.drawCircle(cx,cy,radius-16*d,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=5*d;paint.strokeJoin=Paint.Join.ROUND;paint.strokeCap=Paint.Cap.ROUND
        paint.color=Color.parseColor(if(busy) "#B7B1A5" else "#FFD15C")
        val scale=radius/100f
        val shield=Path().apply {moveTo(cx,cy-35*scale);lineTo(cx+29*scale,cy-21*scale);lineTo(cx+29*scale,cy+3*scale);cubicTo(cx+29*scale,cy+25*scale,cx+14*scale,cy+40*scale,cx,cy+46*scale);cubicTo(cx-14*scale,cy+40*scale,cx-29*scale,cy+25*scale,cx-29*scale,cy+3*scale);lineTo(cx-29*scale,cy-21*scale);close()}
        canvas.drawPath(shield,paint)
        if(connected) {paint.strokeWidth=3*d;canvas.drawLine(cx-12*scale,cy+4*scale,cx-3*scale,cy+13*scale,paint);canvas.drawLine(cx-3*scale,cy+13*scale,cx+15*scale,cy-8*scale,paint)}
        if(hasFocus()){paint.color=Color.WHITE;paint.strokeWidth=2*d;canvas.drawCircle(cx,cy,radius+4*d,paint)}
    }
    override fun drawableStateChanged(){super.drawableStateChanged();invalidate()}
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.Button";info.isClickable=true}
    override fun performClick():Boolean {super.performClick();return true}
}
