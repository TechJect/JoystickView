package com.zerokol.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.jetbrains.annotations.NotNull;

public class JoystickView extends View implements Runnable {

	public final static long DEFAULT_LOOP_INTERVAL = 100; //ms
    public final static int CIRCLE_COLOR = Color.rgb(51, 181, 229);
    public final static int OUTPUT_SCALE = 32767;

    public static final int TOP = -1;
    public static final int BOTTOM = 1;
    public static final int CENTER = 0;
    public static final int LEFT = -1;
    public static final int RIGHT = 1;


	private OnJoystickMoveListener onJoystickMoveListener;
	private Thread thread = new Thread(this);
	private long loopInterval = DEFAULT_LOOP_INTERVAL;
	private int xPosition = 0;
	private int yPosition = 0;
	private double centerX = 0;
	private double centerY = 0;
	private Paint innerCircle;
	private Paint button;
	private Paint horizontalLine;
	private Paint verticalLine;
	private int joystickRadius;
	private int buttonRadius;
    private int snapX = CENTER;
    private int snapY = CENTER;
    private boolean limitCircular = true;

	public JoystickView(Context context) {
		super(context);
        initJoystickView();
	}
	public JoystickView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initJoystickView();
	}
	public JoystickView(Context context, AttributeSet attrs, int defaultStyle) {
		super(context, attrs, defaultStyle);
		initJoystickView();
	}

	protected void initJoystickView() {
		innerCircle = new Paint();
		innerCircle.setColor(CIRCLE_COLOR);
		innerCircle.setStyle(Paint.Style.STROKE);
        innerCircle.setStrokeWidth(2);

		verticalLine = new Paint();
		verticalLine.setStrokeWidth(2);
		verticalLine.setColor(Color.BLACK);

		horizontalLine = new Paint();
		horizontalLine.setStrokeWidth(2);
		horizontalLine.setColor(Color.BLACK);

		button = new Paint(Paint.ANTI_ALIAS_FLAG);
		button.setColor(CIRCLE_COLOR);
		button.setStyle(Paint.Style.FILL);
	}

    @Override
    protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
        centerX = (newWidth) / 2;
        centerY = (newHeight) / 2;
        resetPosition();
    }

    @Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		//always square
		int d = Math.min(measure(widthMeasureSpec), measure(heightMeasureSpec));
		setMeasuredDimension(d, d);

		buttonRadius = (int) (d / 2 * 0.25);
		joystickRadius = (int) (d / 2 * 0.75);
	}

	private int measure(int measureSpec) {
		int result;

		//Decode the measurement specifications.
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		if (specMode == MeasureSpec.UNSPECIFIED) {
			//Return a default size of 200 if no bounds are specified.
			result = 200;
		} else {
			//As you want to fill the available space always return the full available bounds.
			result = specSize;
		}
		return result;
	}

	@Override
	protected void onDraw(Canvas canvas) {
        //inner circle
        canvas.drawCircle((int) centerX, (int) centerY, joystickRadius / 2, innerCircle);

        //horizontal line
        canvas.drawLine(
                (float) (centerX - joystickRadius), (float) centerY,
                (float) (centerX + joystickRadius), (float) centerY, horizontalLine);

        //vertical line
        canvas.drawLine(
                (float) centerX, (float) (centerY - joystickRadius),
                (float) centerX, (float) (centerY + joystickRadius), verticalLine);

		//move button
		canvas.drawCircle(xPosition, yPosition, buttonRadius, button);
	}

	@Override
	public boolean onTouchEvent(@NotNull MotionEvent event) {
		xPosition = (int) event.getX();
		yPosition = (int) event.getY();
        if(limitCircular) {
            double radialDistance = Math.sqrt((xPosition - centerX) * (xPosition - centerX)
                    + (yPosition - centerY) * (yPosition - centerY));
            if (radialDistance > joystickRadius) {
                xPosition = (int) ((xPosition - centerX) * joystickRadius / radialDistance + centerX);
                yPosition = (int) ((yPosition - centerY) * joystickRadius / radialDistance + centerY);
            }
        }else{
            xPosition = limitRange(xPosition, (int) centerX-joystickRadius, (int) centerX+joystickRadius);
            yPosition = limitRange(yPosition, (int) centerY-joystickRadius, (int) centerY+joystickRadius);
        }

		invalidate();
		if (event.getAction() == MotionEvent.ACTION_UP){
            resetPosition();
			thread.interrupt();
			onJoystickMoveListener.onValueChanged(getPowerX(), getPowerY());
		}

        if (event.getAction() == MotionEvent.ACTION_MOVE && getParent() != null){
            getParent().requestDisallowInterceptTouchEvent(true);
        }


        if (onJoystickMoveListener != null && event.getAction() == MotionEvent.ACTION_DOWN) {
			if (thread != null && thread.isAlive()) {
				thread.interrupt();
			}
			thread = new Thread(this);
			thread.start();
			onJoystickMoveListener.onValueChanged(getPowerX(), getPowerY());
		}
		return true;
	}

    private void resetPosition(){
        xPosition = (int) centerX + snapX*joystickRadius;
        yPosition = (int) centerY + snapY*joystickRadius;
    }

	private int getPowerX() {
        return (int) (OUTPUT_SCALE * (xPosition - centerX) / joystickRadius);
 	}

    private int getPowerY() {
        return (int) (OUTPUT_SCALE * (centerY - yPosition) / joystickRadius);
    }

    public void setSnap(int snapX, int snapY){
        this.snapX = snapX;
        this.snapY = snapY;
        resetPosition();
        invalidate();
    }
    public void setLimitCircular(boolean limitCircular){
        this.limitCircular = limitCircular;
        invalidate();
    }

    public void setOnJoystickMoveListener(OnJoystickMoveListener listener, long repeatInterval) {
		this.onJoystickMoveListener = listener;
		this.loopInterval = repeatInterval;
	}

	public static interface OnJoystickMoveListener {
		public void onValueChanged(int powerX, int powerY);
	}

    //TODO is there a better way than sleeping?
	@Override
	public void run() {
		while (!Thread.interrupted()) {
			post(new Runnable() {
				public void run() {
					onJoystickMoveListener.onValueChanged(getPowerX(), getPowerY());
				}
			});
			try {
				Thread.sleep(loopInterval);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
	
    private static int limitRange(int value, int min, int max){
        return(Math.min(Math.max(value, min), max));
    }
}
