package com.fourdrone.safetydrone.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.fourdrone.safetydrone.drone.BebopDrone;
import com.fourdrone.safetydrone.view.BebopVideoView;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.fourdrone.safetydrone.R;
import com.fourdrone.safetydrone.drone.Beeper;
import com.fourdrone.safetydrone.view.ObjectDetect;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

public class BebopActivity extends AppCompatActivity {

    public static double drone_lat;
    public static double drone_lon;
    public static float drone_yaw;
    public static double phone_lat = 0.0;
    public static double phone_lon = 0.0;

    public static final int LEVEL_LAND = 1;
    public static final int LEVEL_TAKEOFF = 0;

    private static final String TAG = "BebopActivity";
    private BebopDrone mBebopDrone;

    private ProgressDialog mConnectionProgressDialog;
    private ProgressDialog mDownloadProgressDialog;

    private BebopVideoView mVideoView;
    private ObjectDetect mObjectDetect;
    private ImageView mImageView;

    private ImageButton mTakeOffLandBt;
    private ImageButton mAdditionalBt;
    private GridLayout mAddtionalItems;
    private ToggleButton mDetectBt;

    private ByteBuffer mSpsBuffer;
    private ByteBuffer mPpsBuffer;
    private int mNbMaxDownload;
    private int mCurrentDownloadIndex;
    private boolean isDetect = false;
    private boolean isFollow = false;

    private ToggleButton followBtn;

    private Beeper beep;
    private Beeper beepFinish;

    private TextView gpsDrone;
    private TextView gpsPhone;

    private  boolean isAdditional = false;

    /** the current location of the phone - potentially only going to be determined once */
    private Location mUserLocation;

    /** location manager for getting GPS position of user */
    private LocationManager mLocationManager;

    private TextView battery;
    private TextView dist;
    private String number;

    public static double distance = 0.0;
    public static int noDetectCount = 0;

    private TextView droneyaw_view;
    private TextView warningView;
    private TextView warningPeople;

    private boolean warningViewOn = false;
    private boolean warningSmsSend = false;
    private boolean warningPeoViewOn = false;

    public static float objectDist = 80000;

    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bebop);

        initIHM();

        SharedPreferences pref = getSharedPreferences("key", MODE_PRIVATE);
        final SharedPreferences.Editor editor = pref.edit();

        number = pref.getString("phone", "");

        // get a location manager
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        mBebopDrone = new BebopDrone(this, service);
        mBebopDrone.addListener(mBebopListener);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the bebop drone is connecting
        if ((mBebopDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mBebopDrone.getConnectionState())))
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            // if the connection to the Bebop fails, finish the activity
            if (!mBebopDrone.connect()) {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mBebopDrone != null)
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            if (!mBebopDrone.disconnect()) {
                finish();
            }
        }
    }
    @Override
    public void onDestroy()
    {
        mBebopDrone.dispose();
        super.onDestroy();
    }

    private void initIHM() {
        mVideoView = (BebopVideoView) findViewById(R.id.videoView);
        mVideoView.setSurfaceTextureListener(mVideoView);

        mObjectDetect = (ObjectDetect)findViewById(R.id.faceDetect);
        mImageView = (ImageView)findViewById(R.id.imageView);

        battery = (TextView)findViewById(R.id.battery);
        dist = (TextView)findViewById(R.id.dist_text);

        followBtn = (ToggleButton)findViewById(R.id.followBtn);
        followBtn.setEnabled(false);

        followBtn.setVisibility(View.INVISIBLE);

        gpsDrone = (TextView)findViewById(R.id.gps_drone);
        gpsPhone = (TextView)findViewById(R.id.gps_phone);

        droneyaw_view = (TextView)findViewById(R.id.droneyaw_view);
        warningView = (TextView)findViewById(R.id.warning_view);
        warningView.setVisibility(View.GONE);
        warningPeople = (TextView)findViewById(R.id.people_warning);
        warningPeople.setVisibility(View.GONE);

        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

        warningView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                warningViewOn = false;
                warningView.setVisibility(View.GONE);
                noDetectCount = 0;
            }
        });

        warningPeople.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //전송
                    SmsManager smsManager = SmsManager.getDefault();

                    String msg = "사용자에게 위험 상황이 발생했습니다. 사용자 위치 : " + "https://www.google.co.kr/maps/place/" + phone_lat + "+" + phone_lon;

                    ArrayList<String> parts = smsManager.divideMessage(msg);
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null);

                    Toast.makeText(getApplicationContext(), "보호자에게 위험 상황 알림과 사용자 위치를 보냈습니다.", Toast.LENGTH_LONG).show();

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), "알림 문자 전송 에러", Toast.LENGTH_LONG).show();
                }

            }
        });

        followBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isFollow){
                    isFollow = false;
                    mObjectDetect.resetFollow();

                    followBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.start_off));
                    Toast toast = Toast.makeText(getApplicationContext(), "Finish", Toast.LENGTH_LONG);
                    toast.show();
                }else{
                    isFollow = true;
                    mObjectDetect.setFollow();

                    followBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.start_on));
                    Toast toast = Toast.makeText(getApplicationContext(), "Start", Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });

        beep = new Beeper(this, R.raw.beep_repeat2);
        beepFinish = new Beeper(this, R.raw.beep_camera);

        mAddtionalItems = (GridLayout)findViewById(R.id.additionalMenuItems);
        mAddtionalItems.setEnabled(false);
        mAddtionalItems.setVisibility(View.INVISIBLE);

        mDetectBt = (ToggleButton)findViewById(R.id.Detect);
        mDetectBt.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){

                if(isDetect){ // 서비스 종료
                    isDetect = false;
                    mObjectDetect.pause();
                    mDetectBt.setBackgroundDrawable(getResources().getDrawable(R.drawable.service_off));
                    isFollow = false;
                    followBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.start_off));

                    try {
                        //전송
                        SmsManager smsManager = SmsManager.getDefault();

                        String msg = "사용자가 목적지에 안전하게 도착했습니다. 사용자 위치 : " + "https://www.google.co.kr/maps/place/" + phone_lat + "+" + phone_lon;

                        ArrayList<String> parts = smsManager.divideMessage(msg);
                        smsManager.sendMultipartTextMessage(number, null, parts, null, null);

                        Toast.makeText(getApplicationContext(), "안전 귀가 완료. 보호자에게 도착 알림과 위치를 보냈습니다.", Toast.LENGTH_LONG).show();

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), "알림 문자 전송 에러", Toast.LENGTH_LONG).show();
                    }

                }else{
                    isDetect = true;
                    noDetectCount = 0;

                    try {
                        //전송
                        SmsManager smsManager = SmsManager.getDefault();

                        String msg = "사용자가 안전 귀가 서비스를 시작했습니다. 사용자 위치 : " + "https://www.google.co.kr/maps/place/" + phone_lat + "+" + phone_lon;

                        ArrayList<String> parts = smsManager.divideMessage(msg);
                        smsManager.sendMultipartTextMessage(number, null, parts, null, null);

                        Toast.makeText(getApplicationContext(), "보호자에게 서비스 시작 알림과 귀가 시작위치를 보냈습니다.", Toast.LENGTH_LONG).show();

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), "알림 문자 전송 에러", Toast.LENGTH_LONG).show();
                    }

                    mObjectDetect.resume(mVideoView, mImageView, mBebopDrone, followBtn, beepFinish);
                    mDetectBt.setBackgroundDrawable(getResources().getDrawable(R.drawable.service_on));
                }
            }
        });


        mAdditionalBt = (ImageButton)findViewById(R.id.additionalMenu);
        mAdditionalBt.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                if(isAdditional){
                    isAdditional = false;
                    mAddtionalItems.setEnabled(false);
                    mAddtionalItems.setVisibility(View.INVISIBLE);
                }else{
                    isAdditional = true;
                    mAddtionalItems.setEnabled(true);
                    mAddtionalItems.setVisibility(View.VISIBLE);
                }
            }
        });

        mTakeOffLandBt = (ImageButton) findViewById(R.id.btn_takeoff_land);
        mTakeOffLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (mBebopDrone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        mBebopDrone.takeOff();
                        mTakeOffLandBt.setBackgroundDrawable(getResources().getDrawable(R.drawable.land));
                        break;
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        mBebopDrone.land();
                        mTakeOffLandBt.setBackgroundDrawable(getResources().getDrawable(R.drawable.take_off));
                        break;
                    default:
                }
            }
        });

        findViewById(R.id.btn_gaz_up).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setGaz((byte) 50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setGaz((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.btn_gaz_down).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setGaz((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setGaz((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.btn_yaw_left).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setYaw((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setYaw((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.btn_yaw_right).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setYaw((byte) 50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setYaw((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.btn_forward).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setPitch((byte) 50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setPitch((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.btn_back).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setPitch((byte) -50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setPitch((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.btn_roll_left).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setRoll((byte) -50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setRoll((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.btn_roll_right).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setRoll((byte) 50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setRoll((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
    }

    private final BebopDrone.Listener mBebopListener = new BebopDrone.Listener() {
        @Override
        public void onPositionChanged(double lat, double lon, double alt) {
            // convert to a Location for easier handling
            Location dronePos = new Location("Bebop");
            dronePos.setLatitude(lat);
            dronePos.setLongitude(lon);
            dronePos.setAltitude(alt);

            if(mUserLocation != null){
                if(lat != 500.0){
                    distance = mUserLocation.distanceTo(dronePos);
                }

                if(distance != 0.0){
                    dist.setText(String.format(Locale.US, "거리:%.4fM", distance));
                }
            }

            drone_lat = lat;
            drone_lon = lon;

            if(drone_lat == 500.0){
                gpsDrone.setText("D : 드론 GPS 정보 없음");
            }
            else{
                gpsDrone.setText(String.format(Locale.US, "D : %.6f, %.6f", drone_lat, drone_lon));
            }

        }

        @Override
        public void onAttitudeChanged(float roll, float pitch, float yaw) {
            drone_yaw = (yaw + 360) % 360;
            droneyaw_view.setText(String.format(Locale.US, "드론 각도 : %.2f도", drone_yaw));

            if(noDetectCount > 100 && !warningViewOn){
                vibrator.vibrate(1000);
                warningView.setVisibility(View.VISIBLE);
                warningViewOn = true;
            }

            if(noDetectCount > 200 && !warningSmsSend){
                warningSmsSend = true;
                vibrator.vibrate(1000);
                try {
                    //전송
                    SmsManager smsManager = SmsManager.getDefault();

                    String msg = "사용자에게 위험 상황이 발생했습니다. 사용자 위치 : " + "https://www.google.co.kr/maps/place/" + phone_lat + "+" + phone_lon;

                    ArrayList<String> parts = smsManager.divideMessage(msg);
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null);

                    Toast.makeText(getApplicationContext(), "보호자에게 위험 상황 알림과 사용자 위치를 보냈습니다.", Toast.LENGTH_LONG).show();

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), "알림 문자 전송 에러", Toast.LENGTH_LONG).show();
                }
            }

            if(7000 < objectDist && objectDist < 75000 && !warningPeoViewOn){
                warningPeoViewOn = true;
                warningPeople.setVisibility(View.VISIBLE);
                vibrator.vibrate(1000);

            }
            if(objectDist > 75000 && warningPeople.getVisibility() == View.VISIBLE){
                warningPeople.setVisibility(View.GONE);
                warningPeoViewOn = false;
            }
        }

        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            battery.setText("배터리:" + batteryPercentage + "%");
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setImageLevel(LEVEL_TAKEOFF);
                    mTakeOffLandBt.setEnabled(true);
                    break;
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setImageLevel(LEVEL_LAND);
                    mTakeOffLandBt.setEnabled(true);
                    break;
                default:
                    mTakeOffLandBt.setEnabled(false);
            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            if (codec.getType() == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
                ARControllerCodec.H264 codecH264 = codec.getAsH264();

                mSpsBuffer = ByteBuffer.wrap(codecH264.getSps().getByteData());
                mPpsBuffer = ByteBuffer.wrap(codecH264.getPps().getByteData());
            }
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            mVideoView.displayFrame(mSpsBuffer, mPpsBuffer, frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            mDownloadProgressDialog.dismiss();

            mNbMaxDownload = nbMedias;
            mCurrentDownloadIndex = 1;

            if (nbMedias > 0) {
                mDownloadProgressDialog = new ProgressDialog(BebopActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(false);
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setMessage("Downloading medias");
                mDownloadProgressDialog.setMax(mNbMaxDownload * 100);
                mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);
                mDownloadProgressDialog.setProgress(0);
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mBebopDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            mDownloadProgressDialog.setProgress(((mCurrentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            mCurrentDownloadIndex++;
            mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);

            if (mCurrentDownloadIndex > mNbMaxDownload) {
                mDownloadProgressDialog.dismiss();
                mDownloadProgressDialog = null;
            }
        }
    };

    // get phone gps
    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            mUserLocation = location;

            phone_lat = mUserLocation.getLatitude();
            phone_lon = mUserLocation.getLongitude();
            gpsPhone.setText(String.format(Locale.US, "P : %.6f, %.6f", phone_lat, phone_lon));
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };
}