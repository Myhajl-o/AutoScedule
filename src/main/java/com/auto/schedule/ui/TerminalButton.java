package com.auto.schedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * Кнопка в термінальному стилі: білий прямокутник із чорною рамкою
 * (рамка задається у {@code res/drawable/bg_button.xml}), а текст
 * малюється білою заливкою з чорною обводкою {@value #TEXT_STROKE_DP}dp.
 *
 * <p>Стандартний {@code TextView} не вміє обводити текст, тому текст
 * малюється вручну: спочатку {@link Paint.Style#STROKE}, потім
 * {@link Paint.Style#FILL}. Сам {@code Button} малює лише фон — колір
 * звичайного тексту зроблено прозорим.</p>
 */
public class TerminalButton extends Button {

  /** Товщина обводки тексту, dp. */
  private static final float TEXT_STROKE_DP = 2f;

  private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public TerminalButton(Context context) {
    this(context, null);
  }

  public TerminalButton(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TerminalButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    float density = getResources().getDisplayMetrics().density;

    setTypeface(Typeface.MONOSPACE);
    setAllCaps(false);
    setTextColor(Color.TRANSPARENT);

    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setStrokeWidth(TEXT_STROKE_DP * density);
    strokePaint.setStrokeJoin(Paint.Join.ROUND);
    strokePaint.setColor(Color.BLACK);

    fillPaint.setStyle(Paint.Style.FILL);
    fillPaint.setColor(Color.WHITE);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    // Button малює фон; текст у нього прозорий.
    super.onDraw(canvas);

    CharSequence text = getText();
    if (text == null || text.length() == 0) {
      return;
    }

    float textSize = getTextSize();
    Typeface typeface = getTypeface();
    strokePaint.setTextSize(textSize);
    strokePaint.setTypeface(typeface);
    fillPaint.setTextSize(textSize);
    fillPaint.setTypeface(typeface);

    String label = text.toString();
    float x = (getWidth() - fillPaint.measureText(label)) / 2f;
    Paint.FontMetrics metrics = fillPaint.getFontMetrics();
    float y = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;

    canvas.drawText(label, x, y, strokePaint);
    canvas.drawText(label, x, y, fillPaint);
  }
}
