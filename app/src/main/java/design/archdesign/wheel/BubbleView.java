package design.archdesign.wheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** The small glowing ring that floats on the screen. */
class BubbleView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean pressed;

    BubbleView(Context c) {
        super(c);
        float d = getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * d);
        ring.setColor(0xFF00E5FF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(3f * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(0xFFFF9A1F);
        dot.setColor(0xFF00E5FF);
    }

    void setPressedLook(boolean p) {
        pressed = p;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - ring.getStrokeWidth();
        fill.setShader(new RadialGradient(cx, cy * 0.8f, r * 1.2f, 0xEE0E2742, 0xEE03080F, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        c.drawCircle(cx, cy, r, ring);
        float a = r * 0.72f;
        c.drawArc(cx - a, cy - a, cx + a, cy + a, 200, 110, false, arc);
        c.drawCircle(cx, cy, r * (pressed ? 0.34f : 0.26f), dot);
    }
}
