package com.akhil.cameraxjavademo;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.icu.text.DateFormat;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.navigation.NavigationView;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ExecutorService cameraExecutor;
    private Button capture;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private PreviewView previewView;
    private TextView timerView;
    private ImageCapture imageCapture;
    private CameraSelector lensFacing;
    public  ScaleGestureDetector scaleGestureDetector;
    public  CameraControl cControl;
    public  CameraInfo cInfo;
    private Camera camera;
    private boolean ringMode = false;
    private long timestamp;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"};

    private int REQUEST_CODE_PERMISSIONS = 1001;
    float zoomLevel = 0f;
    private int permissionGranted=0;

    private int timerNote =10;
    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);

        if(! allPermissionsGranted()){
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS);
        } else {
            permissionGranted=1;
        }
        capture = findViewById(R.id.bCapture);
        lensFacing = CameraSelector.DEFAULT_BACK_CAMERA;
        startCameraX();
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                capturePhoto();
            }
        });
        cameraExecutor = Executors.newSingleThreadExecutor();
    }
    @Override
    protected void onResume(){
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    private Executor getExecuter() {
        return ContextCompat.getMainExecutor(this);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @SuppressLint({"UnsafeOptInUsageError", "WrongConstant"})
    private void startCameraX() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        OrientationEventListener orientationEventListener = new OrientationEventListener(getApplicationContext(), SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                int rotation;
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                } else {
                    rotation = Surface.ROTATION_0;
                }
                if (imageCapture != null) {
                    imageCapture.setTargetRotation(rotation);
                }
            }
        };
        orientationEventListener.enable();
        cameraProviderFuture.addListener(()->{
            imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(flashMode).build();
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            preview.setTargetRotation(getWindowManager().getDefaultDisplay().getRotation());
            pinchToZoom();
            try {
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();
                processCameraProvider.unbindAll();
                camera = processCameraProvider.bindToLifecycle((LifecycleOwner) this, lensFacing, preview, imageCapture);
                cControl = camera.getCameraControl();
                cInfo = camera.getCameraInfo();
                //AutoFocus Every X Seconds
                MeteringPointFactory AFfactory = new SurfaceOrientedMeteringPointFactory((float)previewView.getWidth(),(float)previewView.getHeight());
                float centerWidth = (float)previewView.getWidth()/2;
                float centerHeight = (float)previewView.getHeight()/2;
                Log.d("Preview Width:" , " "+centerWidth*2);
                Log.d("Preview Height:" , " "+centerHeight*2);
                MeteringPoint AFautoFocusPoint = AFfactory.createPoint(centerWidth, centerHeight);
                try {
                    FocusMeteringAction action = new FocusMeteringAction.Builder(AFautoFocusPoint,FocusMeteringAction.FLAG_AF).setAutoCancelDuration(1,TimeUnit.SECONDS).build();
                    cControl.startFocusAndMetering(action);
                }catch (Exception e){
                }
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecuter());
        //setupPinchToZoomAndTapToFocus(previewView);
        pinchToZoom();
        //setUpZoomSlider();
    }
    private void flipCamera() {
        if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) lensFacing = CameraSelector.DEFAULT_BACK_CAMERA;
        else if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA;
        startCameraX();
    }


    private void pinchToZoom() {//current zoom
        ScaleGestureDetector.SimpleOnScaleGestureListener listener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float lastScaleFactor = 1.0f;

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                Float zoomRatio = cInfo.getZoomState().getValue().getZoomRatio();
                Float minZoomRatio = cInfo.getZoomState().getValue().getMinZoomRatio();
                Float maxZoomRatio = cInfo.getZoomState().getValue().getMaxZoomRatio();
                float scaleFactor = detector.getScaleFactor();
                if (lastScaleFactor == 0f || (Math.signum(scaleFactor) == Math.signum(lastScaleFactor))) {
                    cControl.setZoomRatio(Math.max(minZoomRatio, Math.min(zoomRatio * scaleFactor, maxZoomRatio)));
                    lastScaleFactor = scaleFactor;
                    Log.d("scaleFactor", "Scale value"+scaleFactor);
                } else {
                    lastScaleFactor = 0f;
                }
                return true;
            }
        };
        scaleGestureDetector = new ScaleGestureDetector(getBaseContext(), listener);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private String date = null;
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void capturePhoto() {
        timestamp = System.currentTimeMillis();
        date = DateFormat.getDateTimeInstance().format(new Date(timestamp));
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "OnePhoto"+System.currentTimeMillis()+"_");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/JPEG");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/원본사진");
        imageCapture.takePicture( new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build(),
                getExecuter(),
                new ImageCapture.OnImageSavedCallback() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri uri = outputFileResults.getSavedUri();
                        Toast.makeText(MainActivity.this, "Saved at: " + uri,  Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "사진 저장 오류: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    public boolean allPermissionsGranted(){
        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }
    public native String InsertSignature(String a, String b);
    public native String VerifySig(String a, String b);
}
