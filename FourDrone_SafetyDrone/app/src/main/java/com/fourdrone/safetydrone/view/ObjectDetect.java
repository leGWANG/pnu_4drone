package com.fourdrone.safetydrone.view;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

import com.fourdrone.safetydrone.activity.BebopActivity;
import com.fourdrone.safetydrone.drone.BebopDrone;
import com.fourdrone.safetydrone.drone.Beeper;
import com.fourdrone.safetydrone.R;


public class ObjectDetect extends View {
    private final static String CLASS_NAME = ObjectDetect.class.getSimpleName();

    private final Context ctx;

    private CascadeClassifier objectClassifier;

    private Handler openCVHandler = new Handler();
    private Thread openCVThread = null;

    private BebopVideoView bebopVideoView = null;
    private ImageView cvPreviewView = null;
    private BebopDrone bebopDrone = null;

    private Beeper beepFinsh = null;

    private Rect[] objectsArray = null;

    private Paint paint_green;
    private Paint paint_red;

    private final Object lock = new Object();

    private float mainCenterX = 0;
    private float mainCenterY = 0;
    private float mainBoundRateX = 0;
    private float mainBoundRateY = 0;

    private float mainobjectArea = 25000;

    private float mX = 0;
    private float mY = 0;

    private float centerX = 0;
    private float centerY = 0;

    private float objectCenterX = 0;
    private float objectCenterY = 0;

    private float rateX = 0;
    private float rateY = 0;

    private float top_x = 0;
    private float top_y = 0;

    private long sTime = System.currentTimeMillis();
    private long cTime;

    private boolean isFirst = true;
    private boolean followEnabled = false;

    private boolean gpsFollowOn = false;
    private ToggleButton followBtn;

    public ObjectDetect(Context context) {

        super(context);
        ctx = context;

        objectClassifier = new CascadeClassifier(cascadeFile(R.raw.haarcascade_fullbody));

        // initialize our canvas paint object
        paint_green = new Paint();
        paint_green.setAntiAlias(true);
        paint_green.setColor(Color.parseColor("#22b3ab"));
        paint_green.setStyle(Paint.Style.STROKE);
        paint_green.setStrokeWidth(4f);

        paint_red = new Paint();
        paint_red.setAntiAlias(true);
        paint_red.setStyle(Paint.Style.STROKE);
        paint_red.setColor(Color.RED);
        paint_red.setStrokeWidth(2f);
    }

    public ObjectDetect(Context context, AttributeSet attrs) {
        super(context,attrs);
        ctx = context;

        objectClassifier = new CascadeClassifier(cascadeFile(R.raw.haarcascade_fullbody));


        // initialize our canvas paint object
        paint_green = new Paint();
        paint_green.setAntiAlias(true);
        paint_green.setColor(Color.parseColor("#22b3ab"));
        paint_green.setStyle(Paint.Style.STROKE);
        paint_green.setStrokeWidth(4f);

        paint_red = new Paint();
        paint_red.setAntiAlias(true);
        paint_red.setStyle(Paint.Style.STROKE);
        paint_red.setColor(Color.RED);
        paint_red.setStrokeWidth(2f);
    }

    public ObjectDetect(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        ctx = context;

        // initialize our opencv cascade classifiers
        objectClassifier = new CascadeClassifier(cascadeFile(R.raw.haarcascade_fullbody));

        // initialize our canvas paint object
        paint_green = new Paint();
        paint_green.setAntiAlias(true);
        paint_green.setColor(Color.parseColor("#22b3ab"));
        paint_green.setStyle(Paint.Style.STROKE);
        paint_green.setStrokeWidth(4f);

        paint_red = new Paint();
        paint_red.setAntiAlias(true);
        paint_red.setStyle(Paint.Style.STROKE);
        paint_red.setColor(Color.RED);
        paint_red.setStrokeWidth(2f);
    }

    public void setFollow() {
        followEnabled = true;
    }

    public void resetFollow() {
        followEnabled = false;
    }

    private String cascadeFile(final int id) {
        final InputStream is = getResources().openRawResource(id);

        final File cascadeDir = ctx.getDir("cascade", Context.MODE_PRIVATE);
        final File cascadeFile = new File(cascadeDir, String.format(Locale.US, "%d.xml", id));

        try {
            final FileOutputStream os = new FileOutputStream(cascadeFile);
            final byte[] buffer = new byte[4096];

            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.close();
        } catch (Exception e) {
            Log.e(CLASS_NAME, "unable to open cascade file: " + cascadeFile.getName(), e);
            return null;
        }

        return cascadeFile.getAbsolutePath();
    }

    public void resume(final BebopVideoView bebopVideoView, final ImageView cvPreviewView, final BebopDrone bebopDrone, ToggleButton followBtn, final Beeper beepFinish) {
        if (getVisibility() == View.VISIBLE) {
            this.bebopVideoView = bebopVideoView;
            this.cvPreviewView = cvPreviewView;
            this.bebopDrone = bebopDrone;
            this.followBtn = followBtn;
            this.beepFinsh = beepFinish;



            openCVThread = new CascadingThread(ctx);
            openCVThread.start();
        }
    }

    public void pause() {
        if (getVisibility() == View.VISIBLE) {
            openCVThread.interrupt();
            try {
                openCVThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void ObjectRecognition(Mat mat) {
        if(isFirst == true) {
            mainCenterX = mat.width() / 2;
            mainCenterY = mat.height() / 2;
            mainBoundRateX = mat.width() * 0.05f;
            mainBoundRateY = mat.height() * 0.01f;

            centerX = mainCenterX;
            centerY = mainCenterY;

            rateX = mat.width() * 0.4f;
            rateY = mat.height() * 0.4f;

            top_x = centerX - (rateX) / 2;
            top_y = centerY - (rateY) / 2;

            isFirst=false;
        }
    }

    private void ObjectTrack(Mat mat) {
        if(objectsArray !=null && objectsArray.length>0 ) {
            centerX = objectCenterX;
            centerY = objectCenterY;

            top_x = centerX - (rateX)/2;
            top_y = centerY - (rateY)/2;

            if(top_x < 0)
                top_x = 0;
            if(top_y < 0)
                top_y = 0;
            if(top_x + rateX >= mat.width())
                top_x = mat.width() - rateX;
            if(top_y + rateY >= mat.height())
                top_y = mat.height() - rateY;
        }
    }

    private class CascadingThread extends Thread {
        private final Handler handler;
        boolean interrupted = false;

        private CascadingThread(final Context ctx) {
            handler = new Handler(ctx.getMainLooper());
        }

        @Override
        public void interrupt() {
            interrupted = true;
            followEnabled = false;
            isFirst = true;
            objectsArray = null;
            invalidate();
        }

        @Override
        public void run() {
            final Mat firstMat = new Mat();
            final Mat mat = new Mat();

            Mat submat= new Mat();
            Mat sub_submat = new Mat();
            Rect rect = new Rect();

            while (!interrupted) {
                final Bitmap source = bebopVideoView.getBitmap();
                if (source != null) {
                    Utils.bitmapToMat(source, firstMat);
                    firstMat.assignTo(mat);
                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);

                    final MatOfRect objects = new MatOfRect();

                    final int minRows = Math.round(mat.rows() * 0.07f);

                    final Size minSize = new Size(minRows, minRows);
                    final Size maxSize = new Size(0, 0);


                    ObjectRecognition(mat);
                    ObjectTrack(mat);

                    rect = new Rect((int)top_x,(int)top_y,(int)rateX,(int)rateY);

                    submat = mat.submat(rect);
                    submat.assignTo(sub_submat);


                    objectClassifier.detectMultiScale(sub_submat, objects,1.1, 6, 0, minSize, maxSize);
                    synchronized (lock) {
                        objectsArray = objects.toArray();
                        mX = submat.width() / sub_submat.width();
                        mY = submat.height() / sub_submat.height();
                        objects.release();

                        if (followEnabled  && objectsArray != null && objectsArray.length > 0 & objectCenterX != 0 && objectCenterY != 0) {

                            // 객체가 중심좌표에서 좌우로 갔을때
                            if (Math.abs(objectCenterX - mainCenterX) > mainBoundRateX) {
                                // 객체가 왼쪽에 있는 경우
                                //객체 사이즈 비교
                                if(objectsArray[0].area() != 0){
                                    //객체가 왼쪽에 있으면서 객체가 뒤로 간 경우 + - -
                                    if((mainobjectArea) / objectsArray[0].area() > 1.01f){
                                        bebopDrone.setPitch((byte) 50);
                                        bebopDrone.setYaw((byte) -50);
                                        bebopDrone.setRoll((byte) -40);
                                        bebopDrone.setFlag((byte) 1);

                                    }
                                    //객체가 왼쪽에 있으면서 객체가 앞으로 간 경우 - - -
                                    else if(mainobjectArea / objectsArray[0].area() < 0.9f){
                                        bebopDrone.setPitch((byte) -50);
                                        bebopDrone.setYaw((byte) -50);
                                        bebopDrone.setRoll((byte) -40);
                                        bebopDrone.setFlag((byte) 1);

                                    }
                                    //객체가 왼쪽에 있으면서 적절할 때 0 - -
                                    else{
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setYaw((byte) -50);
                                        bebopDrone.setRoll((byte) -40);
                                        bebopDrone.setFlag((byte) 1);

                                    }
                                }
                                if (mainCenterX > objectCenterX) {
                                    // 객체가 왼쪽에 있으면서 중심좌표에서 상하 범위를 벗어났을때
                                    if(Math.abs(objectCenterY - mainCenterY) > mainBoundRateY * 1.25f){
                                        // 왼쪽에 있으면서 위쪽에 있을떄 pitch yaw roll + - -
                                        if(mainCenterY > objectCenterY){
                                            bebopDrone.setPitch((byte) 50);
                                            bebopDrone.setYaw((byte) -50);
                                            bebopDrone.setRoll((byte) -40);
                                            bebopDrone.setFlag((byte) 1);
                                        }
                                        //왼쪽에 있으면서 아래쪽에 있을때 - - -
                                        else{
                                            bebopDrone.setPitch((byte) -50);
                                            bebopDrone.setYaw((byte) -50);
                                            bebopDrone.setRoll((byte) -40);
                                            bebopDrone.setFlag((byte) 1);
                                        }
                                    }
                                    //객체가 왼쪽에 있으면서 상하범위를 벗어나지 않았을때 0 - -
                                    else{
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setYaw((byte) -50);
                                        bebopDrone.setRoll((byte) -40);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                }
                                // 객체가 오른쪽에 있는 경우
                                else {
                                    //객체 사이즈 비교
                                    if(objectsArray[0].area() != 0){
                                        //객체가 오른쪽에 있으면서 객체가 뒤로 간 경우 + + +
                                        if((mainobjectArea) / objectsArray[0].area() > 1.01f){
                                            bebopDrone.setPitch((byte) 50);
                                            bebopDrone.setYaw((byte) 50);
                                            bebopDrone.setRoll((byte) 40);
                                            bebopDrone.setFlag((byte) 1);

                                        }
                                        //객체가 오른쪽에 있으면서 객체가 앞으로 간 경우 - + +
                                        else if(mainobjectArea / objectsArray[0].area() < 0.9f){
                                            bebopDrone.setPitch((byte) -50);
                                            bebopDrone.setYaw((byte) 50);
                                            bebopDrone.setRoll((byte) 40);
                                            bebopDrone.setFlag((byte) 1);

                                        }
                                        //객체가 오른쪽에 있으면서 적절할 때 0 + +
                                        else{
                                            bebopDrone.setPitch((byte) 0);
                                            bebopDrone.setYaw((byte) 50);
                                            bebopDrone.setRoll((byte) 40);
                                            bebopDrone.setFlag((byte) 1);

                                        }
                                    }
                                    //오른쪽에 있으면서 상하범위를 벗어났을때
                                    if(Math.abs(objectCenterY - mainCenterY) > mainBoundRateY * 1.25f){
                                        //오른쪽에 있으면서 위쪽에 있을때 + + +
                                        if(mainCenterY > objectCenterY){
                                            bebopDrone.setPitch((byte) 50);
                                            bebopDrone.setYaw((byte) 50);
                                            bebopDrone.setRoll((byte) 40);
                                            bebopDrone.setFlag((byte) 1);
                                        }
                                        //오른쪽에 있으면서 아래쪽에 있을때 - + +
                                        else{
                                            bebopDrone.setPitch((byte) -50);
                                            bebopDrone.setYaw((byte) 50);
                                            bebopDrone.setRoll((byte) 40);
                                            bebopDrone.setFlag((byte) 1);
                                        }
                                    }
                                    //오른쪽에 있으면서 상하범위를 벗어나지 않았을때 0 + +
                                    else{
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setYaw((byte) 50);
                                        bebopDrone.setRoll((byte) 40);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                }
                            }
                            //좌우 범위를 벗어나지 않았을때
                            else {
                                if(objectsArray[0].area() != 0){
                                    //객체가 오른쪽에 있으면서 객체가 뒤로 간 경우 + + +
                                    if((mainobjectArea) / objectsArray[0].area() > 1.01f){
                                        bebopDrone.setPitch((byte) 50);
                                        bebopDrone.setYaw((byte) 0);
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setFlag((byte) 1);

                                    }
                                    //객체가 오른쪽에 있으면서 객체가 앞으로 간 경우 - + +
                                    else if(mainobjectArea / objectsArray[0].area() < 0.9f){
                                        bebopDrone.setPitch((byte) -50);
                                        bebopDrone.setYaw((byte) 0);
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setFlag((byte) 1);

                                    }
                                    //객체가 오른쪽에 있으면서 적절할 때 0 + +
                                    else{
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setYaw((byte) 0);
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setFlag((byte) 0);

                                    }
                                }
                                if(Math.abs(objectCenterY - mainCenterY) > mainBoundRateY * 1.25f){
                                    //오른쪽에 있으면서 위쪽에 있을때 + + +
                                    if(mainCenterY > objectCenterY){
                                        bebopDrone.setPitch((byte) 50);
                                        bebopDrone.setYaw((byte) 0);
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    //오른쪽에 있으면서 아래쪽에 있을때 - + +
                                    else{
                                        bebopDrone.setPitch((byte) -50);
                                        bebopDrone.setYaw((byte) 0);
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                }
                                //오른쪽에 있으면서 상하범위를 벗어나지 않았을때 0 + +
                                else{
                                    bebopDrone.setPitch((byte) 0);
                                    bebopDrone.setYaw((byte) 0);
                                    bebopDrone.setRoll((byte) 0);
                                    bebopDrone.setFlag((byte) 0);
                                }

                            }
                            // 드론과 사람사이 거리가 7m 이상일때
                            if(BebopActivity.distance > 7.0 && gpsFollowOn){
                                // lat : 위도 - 가로선(x 축) , lon : 경도 - 세로선(y 축)
                                double latGap = BebopActivity.phone_lat - BebopActivity.drone_lat;
                                double lonGap = BebopActivity.phone_lon - BebopActivity.drone_lon;
                                double drone_yaw = BebopActivity.drone_yaw;

                                // 1사분면 + +
                                if(latGap > 0 && lonGap > 0){
                                    // 0~90도
                                    if(drone_yaw > 0.0 && drone_yaw < 90.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) 5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 90~180도
                                    else if(drone_yaw > 90.0 && drone_yaw < 180.0){
                                        bebopDrone.setRoll((byte) -5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 180~270도
                                    else if(drone_yaw > 180.0 && drone_yaw < 270.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) -5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 270~360도
                                    else if(drone_yaw > 270.0 && drone_yaw < 360.0){
                                        bebopDrone.setRoll((byte) 5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                }
                                // 2 사분면 + -
                                else if(latGap > 0 && lonGap < 0){
                                    // 0~90도
                                    if(drone_yaw > 0.0 && drone_yaw < 90.0){
                                        bebopDrone.setRoll((byte) 5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 90~180도
                                    else if(drone_yaw > 90.0 && drone_yaw < 180.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) 5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 180~270도
                                    else if(drone_yaw > 180.0 && drone_yaw < 270.0){
                                        bebopDrone.setRoll((byte) -5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 270~360도
                                    else if(drone_yaw > 270.0 && drone_yaw < 360.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) -5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                }
                                // 3 사분면 - -
                                else if(latGap < 0 && lonGap < 0){
                                    // 0~90도
                                    if(drone_yaw > 0.0 && drone_yaw < 90.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) -5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 90~180도
                                    else if(drone_yaw > 90.0 && drone_yaw < 180.0){
                                        bebopDrone.setRoll((byte) 5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 180~270도
                                    else if(drone_yaw > 180.0 && drone_yaw < 270.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) 5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 270~360도
                                    else if(drone_yaw > 270.0 && drone_yaw < 360.0){
                                        bebopDrone.setRoll((byte) -5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                }
                                // 4 사분면 - +
                                else if(latGap < 0 && lonGap > 0){
                                    // 0~90도
                                    if(drone_yaw > 0.0 && drone_yaw < 90.0){
                                        bebopDrone.setRoll((byte) -5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 90~180도
                                    else if(drone_yaw > 90.0 && drone_yaw < 180.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) -5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 180~270도
                                    else if(drone_yaw > 180.0 && drone_yaw < 270.0){
                                        bebopDrone.setRoll((byte) 5);
                                        bebopDrone.setPitch((byte) 0);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                    // 270~360도
                                    else if(drone_yaw > 270.0 && drone_yaw < 360.0){
                                        bebopDrone.setRoll((byte) 0);
                                        bebopDrone.setPitch((byte) 5);
                                        bebopDrone.setFlag((byte) 1);
                                    }
                                }
                            }

                        } else {
                            bebopDrone.setYaw((byte) 0);
                            bebopDrone.setGaz((byte) 0);
                            bebopDrone.setRoll((byte) 0);
                            bebopDrone.setPitch((byte) 0);
                            bebopDrone.setFlag((byte) 0);
                        }

                        if(interrupted){
                            objectsArray = null;
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                }
                try {
                    sleep(60);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            firstMat.release();
            mat.release();
            submat.release();
            sub_submat.release();
        }
        private void runOnUiThread(Runnable r) {
            handler.post(r);
        }

    }

    @Override
    protected void onDraw(Canvas canvas) {
        synchronized(lock) {
            if(followBtn != null && followEnabled != true) {
                followBtn.setEnabled(false);
                followBtn.setVisibility(INVISIBLE);
            }
            if (objectsArray != null && objectsArray.length > 0) {
                followBtn.setEnabled(true);
                followBtn.setVisibility(VISIBLE);

                BebopActivity.noDetectCount = 0;

                float objectTLX = (float) objectsArray[0].tl().x * mX;
                float objectBRX = (float) objectsArray[0].br().x * mX;
                float objectTLY = (float) objectsArray[0].tl().y * mY;
                float objectBRY = (float) objectsArray[0].br().y * mY;

                canvas.drawRect(top_x + objectTLX, top_y + objectTLY, top_x + objectBRX, top_y + objectBRY, paint_green);


                objectCenterX = (top_x*2 + objectTLX + objectBRX)/2;
                objectCenterY = (top_y*2 + objectTLY + objectBRY)/2;

                if(objectsArray.length > 1){
                    float objectTLX_2 = (float) objectsArray[1].tl().x * mX;
                    float objectBRX_2 = (float) objectsArray[1].br().x * mX;
                    float objectTLY_2 = (float) objectsArray[1].tl().y * mY;
                    float objectBRY_2 = (float) objectsArray[1].br().y * mY;

                    float objectCenterX_2= (top_x*2 + objectTLX_2 + objectBRX_2)/2;
                    float objectCenterY_2 = (top_y*2 + objectTLY_2 + objectBRY_2)/2;

                    canvas.drawRect(top_x + objectTLX_2, top_y + objectTLY_2, top_x + objectBRX_2, top_y + objectBRY_2, paint_green);

                    BebopActivity.objectDist =  (objectCenterX - objectCenterX_2) * (objectCenterX - objectCenterX_2) + (objectCenterY - objectCenterY_2) * (objectCenterY - objectCenterY_2);
                }
            }


            else {
                if(followBtn != null && followBtn.getVisibility() == View.VISIBLE) BebopActivity.noDetectCount++;
            }
        }
        super.onDraw(canvas);
    }
}