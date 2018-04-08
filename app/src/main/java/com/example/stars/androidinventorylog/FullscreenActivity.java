package com.example.stars.androidinventorylog;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.Arrays;

public class FullscreenActivity extends AppCompatActivity {
    private static final boolean AUTO_HIDE              = true;
    private static final int     AUTO_HIDE_DELAY_MILLIS = 3000;
    private CameraCaptureSession cameraCaptureSession;
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int     UI_ANIMATION_DELAY        = 300;
    private static final int     REQUEST_CAMERA_PERMISSION = 100;
    private final        Handler mHideHandler              = new Handler ( );
    private HandlerThread mBackgroundHandlerThread;
    private Handler       mBackgroundHandler;
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener ( ) {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide ( AUTO_HIDE_DELAY_MILLIS );
            }
            return false;
        }
    };
    private CameraManager cameraManager;
    private String        cameraId;
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener ( ) {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera ( );
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener ( ) {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image;
            image = reader.acquireNextImage ( );
            image.close ( );

        }
    };
    private TextureView textureView;
    private View        mContentView;
    private final Runnable mHidePart2Runnable = new Runnable ( ) {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility ( View.SYSTEM_UI_FLAG_LOW_PROFILE
                                                         | View.SYSTEM_UI_FLAG_FULLSCREEN
                                                         | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                                         | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                                         | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                                         | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION );
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable ( ) {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar ( );
            if (actionBar != null) {
                actionBar.show ( );
            }
            mControlsView.setVisibility ( View.VISIBLE );
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable ( ) {
        @Override
        public void run() {
            hide ( );
        }
    };
    private CameraDevice cameraDevice;

    private void openCamera() {
        try {
            String[] cameraIdList = cameraManager.getCameraIdList ( );
            for (int i = 0; i < cameraIdList.length; i++) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics ( cameraIdList[i] );
                if (characteristics.get ( CameraCharacteristics.LENS_FACING ) == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = cameraIdList[i];
                    break;
                }
            }
            if (ActivityCompat.checkSelfPermission ( this, Manifest.permission.CAMERA ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions ( this, new String[]{
                        Manifest.permission.CAMERA,
                }, REQUEST_CAMERA_PERMISSION );
                return;
            }
            cameraManager.openCamera ( cameraId, new CameraDevice.StateCallback ( ) {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession ( );
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    cameraDevice.close ( );
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    cameraDevice.close ( );
                    cameraDevice = null;
                }
            }, null );
        } catch (CameraAccessException e) {
            e.printStackTrace ( );
        }


    }

    private void createCaptureSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture ( );
            assert texture != null;
            texture.setDefaultBufferSize ( 1920, 1080 );
            Surface                      surface               = new Surface ( texture );
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest ( CameraDevice.TEMPLATE_PREVIEW );

            ImageReader imageReader      = ImageReader.newInstance ( 1920, 1080, ImageFormat.YUV_420_888, 5 );
            Surface     imgReaderSurface = imageReader.getSurface ( );

            captureRequestBuilder.addTarget ( imgReaderSurface );
            captureRequestBuilder.addTarget ( surface );
            captureRequestBuilder.set ( CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO );

            cameraDevice.createCaptureSession ( Arrays.asList ( surface, imgReaderSurface ), new CameraCaptureSession.StateCallback ( ) {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null)
                        return;
                    cameraCaptureSession = session;
                    try {
                        session.setRepeatingRequest ( captureRequestBuilder.build ( ), new CameraCaptureSession.CaptureCallback ( ) {
                        }, mBackgroundHandler );
                    } catch (CameraAccessException e) {
                        e.printStackTrace ( );
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText ( FullscreenActivity.this, "Configure failed!", Toast.LENGTH_SHORT ).show ( );
                }
            }, null );
            imageReader.setOnImageAvailableListener ( onImageAvailableListener, mBackgroundHandler );

        } catch (CameraAccessException e) {
            e.printStackTrace ( );
        }
    }

    private void stopBackgroundThread() {
        if (mBackgroundHandlerThread != null) {
            mBackgroundHandlerThread.quitSafely ( );
            try {
                mBackgroundHandlerThread.join ( );
                mBackgroundHandlerThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace ( );
            }
        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread ( "Camera Background" );
        mBackgroundHandlerThread.start ( );
        mBackgroundHandler = new Handler ( mBackgroundHandlerThread.getLooper ( ) );

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate ( savedInstanceState );
        setContentView ( R.layout.activity_fullscreen );
        startBackgroundThread ( );
        mVisible = true;
        mControlsView = findViewById ( R.id.fullscreen_content_controls );
        mContentView = findViewById ( R.id.fullscreen_content );

        textureView = (TextureView) mContentView;
        textureView.setSurfaceTextureListener ( textureListener );

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener ( new View.OnClickListener ( ) {
            @Override
            public void onClick(View view) {
                toggle ( );
            }
        } );

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById ( R.id.dummy_button ).setOnTouchListener ( mDelayHideTouchListener );

        cameraManager = (CameraManager) getSystemService ( Context.CAMERA_SERVICE );
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate ( savedInstanceState );

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide ( 100 );
    }

    @Override
    protected void onPause() {
        super.onPause ( );
        stopBackgroundThread ( );
    }

    private void toggle() {
        if (mVisible) {
            hide ( );
        } else {
            show ( );
        }
    }

    @Override
    protected void onResume() {
        super.onResume ( );
        startBackgroundThread ( );
        if (textureView.isAvailable ( ))
            openCamera ( );
        else
            textureView.setSurfaceTextureListener ( textureListener );
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar ( );
        if (actionBar != null) {
            actionBar.hide ( );
        }
        mControlsView.setVisibility ( View.GONE );
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks ( mShowPart2Runnable );
        mHideHandler.postDelayed ( mHidePart2Runnable, UI_ANIMATION_DELAY );
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility ( View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                                     | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION );
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks ( mHidePart2Runnable );
        mHideHandler.postDelayed ( mShowPart2Runnable, UI_ANIMATION_DELAY );
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks ( mHideRunnable );
        mHideHandler.postDelayed ( mHideRunnable, delayMillis );
    }

}
