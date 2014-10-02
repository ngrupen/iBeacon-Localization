package com.example.aclient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.view.View;
 
public class PathView extends View{
    // private variables
    private final int BUFFER_LEN = 512;
    private float pathBuffer[][];
    private int buffHead;
    private int buffTail;
    private int buffNumVals = 0;
    private double current_x;
    private double current_y;
    private double current_theta;
    private float lineToDraw[][];
    private float plot_scale;
    Paint paint = new Paint();
     
    public PathView(Context context) {
        super(context);
         
        pathBuffer = new float[2][BUFFER_LEN];
        current_x = 0;
        current_y = 0;
        buffHead = 0;
        buffTail = 0;
        buffNumVals = 0;
        current_theta = -Math.PI/2;
        lineToDraw = new float[2][2];
         
        plot_scale = 4.0f;
    }
 
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
         
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.YELLOW);
        paint.setStrokeWidth(8);
 
        int center_x = getWidth()/2;
        int center_y = getHeight()/2;
         
        float start_x = pathBuffer[0][buffTail];
        float start_y = pathBuffer[1][buffTail];
        //center_x = (int)(plot_scale*start_x);
        //center_y = (int)(plot_scale*start_y);
         
        float delta_x = start_x - center_x;
        float delta_y = start_y - center_y;
        delta_x = 0;
        delta_y = 0;
 
        float last_x = center_x;
        float last_y = center_y;
 
        if( buffNumVals > 0){
            for(int i=buffTail; i!=buffHead; i=(i+1)%BUFFER_LEN){
                float next_x = center_x - delta_x + plot_scale*pathBuffer[0][i];
                float next_y = center_y - delta_y + plot_scale*pathBuffer[1][i];
 
                canvas.drawLine(last_x, last_y, next_x, next_y, paint);
                //Log.i("inloc", "(" + last_x + "," + last_y + ")-->(" + next_x + "," + next_y + ")");
                last_x = next_x;
                last_y = next_y;
            }
        }
         
        canvas.drawLine(center_x + lineToDraw[0][0], center_y + lineToDraw[0][1],
                center_x + lineToDraw[1][0], center_y + lineToDraw[1][1], paint);
    }
     
    // ---- update path functions ---
    public void addToBuffer(float x, float y){
        pathBuffer[0][buffHead] = x;
        pathBuffer[1][buffHead] = y;
        if( buffNumVals < BUFFER_LEN ){
            buffNumVals++;
        }
        if( buffNumVals < BUFFER_LEN ){
            buffHead = (buffHead + 1)%BUFFER_LEN;
        }else{
            buffHead = (buffHead + 1)%BUFFER_LEN;
            buffTail = (buffTail + 1)%BUFFER_LEN;
        }
         
    }
     
    public void addStep(double strideLength){
        float new_x = (float)(current_x + strideLength*Math.cos(current_theta));
        float new_y = (float)(current_y + strideLength*Math.sin(current_theta));
 
        addToBuffer(new_x, new_y);
 
        current_x = new_x;
        current_y = new_y;
    }
     
    public void addTurn(double turnAngle){
        current_theta += -Math.toRadians(turnAngle);
    }
 
    public void clearAll(Canvas canvas){
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }
}

