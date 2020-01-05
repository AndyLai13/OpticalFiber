package com.lightel.opticalfiber;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.lightel.opticalfiber.settings.SettingsActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class InspectorActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback, View.OnClickListener, View.OnTouchListener {

    private final static String TAG = InspectorActivity.class.getSimpleName();
    //    SurfaceView mSurfaceView;
    private AutoFitTextureView mTextureView;

    Button mButtonTake;
    Boolean mFlashSupported;
    String mCameraId;
    CameraDevice mCameraDevice;
    CaptureRequest.Builder mPreviewRequestBuilder;
    CaptureRequest mPreviewRequest;
    CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;
    private Integer mSensorOrientation;
    private Size mPreviewSize;
    private File mFile;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    CameraManager mCameraManager;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * 相机状态：显示相机预览。
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * 相机状态：等待焦点被锁定。
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * 等待曝光被Precapture状态。
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * 相机状态：等待曝光的状态是不是Precapture。
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * 相机状态：拍照。
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    private int mState = STATE_PREVIEW;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspector);
        initView();

        mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
        }
    }

    public float finger_spacing = 0;
    public int zoom_level = 1;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            float maxzoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))*10;

            Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int action = event.getAction();
            float current_finger_spacing;

            if (event.getPointerCount() > 1) {
                // Multi touch logic
                current_finger_spacing = getFingerSpacing(event);
                if(finger_spacing != 0){
                    if(current_finger_spacing > finger_spacing && maxzoom > zoom_level){
                        zoom_level++;
                    } else if (current_finger_spacing < finger_spacing && zoom_level > 1){
                        zoom_level--;
                    }
                    int minW = (int) (m.width() / maxzoom);
                    int minH = (int) (m.height() / maxzoom);
                    int difW = m.width() - minW;
                    int difH = m.height() - minH;
                    int cropW = difW /100 *(int)zoom_level;
                    int cropH = difH /100 *(int)zoom_level;
                    cropW -= cropW & 3;
                    cropH -= cropH & 3;
                    Rect zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
                    mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                }
                finger_spacing = current_finger_spacing;
            } else{
                if (action == MotionEvent.ACTION_UP) {
                    //single touch logic
                }
            }

            try {
                mCaptureSession
                        .setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (NullPointerException ex) {
                ex.printStackTrace();
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException("can not access camera.", e);
        }
        return true;
    }

    private static float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * 启动一个HandlerThread
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        /**
         *  当屏幕关闭并重新打开时，SurfaceTexture已经可用，“onSurfaceTextureAvailable”将不被调用。
         *  在这种情况下，我们可以打开相机并从这里开始预览（否则，我们等待SurfaceTextureListener中的表面准备就绪）。
         */

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    /**
     * TextureView 监听
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.e("Andy", "onSurfaceTextureAvailable");
            //当TextureView可用的时候 打开预览摄像头
            Log.e(TAG, "width:" + width + "  height:" + height);
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
//            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    void initView() {
        mTextureView = findViewById(R.id.surfaceView);
        mButtonTake = findViewById(R.id.take_photo);
        mButtonTake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        mTextureView.setOnTouchListener(this);

        mBrightness = findViewById(R.id.brightness);
        mBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                try {
                    updateCameraPreviewSession(progress);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        findViewById(R.id.options).setOnClickListener(this);
        mFile = new File(getExternalFilesDir(null), "pic.jpg");
    }

    SeekBar mBrightness;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 50) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                finish();
            }
        }
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d("Andy", "onImageAvailable");
            //当图片可得到的时候获取图片并保存
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.options:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            default:
                break;
        }
    }

    /**
     * 将JPG保存到指定的文件中。
     */
    private static class ImageSaver implements Runnable {
        /**
         * JPEG图像
         */
        private final Image mImage;
        /**
         * 保存图像的文件
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     * 设置与相机相关的成员变量。
     *
     * @param width  相机预览的可用尺寸宽度
     * @param height 相机预览的可用尺寸的高度
     */
    private void setUpCameraOutputs(int width, int height) {
        try {
            //获取可用摄像头列表
            for (String cameraId : mCameraManager.getCameraIdList()) {
                //获取相机的相关参数
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                // 不使用前置摄像头。
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                // 对于静态图像拍摄，使用最大的可用尺寸。
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                //添加ImageAvailableListener事件，当图片可得到的时候回到，也就是点击拍照的时候
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                // 获取手机旋转的角度以调整图片的方向
                int displayRotation = this.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        // 横屏
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        // 竖屏
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                this.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                // 尝试使用太大的预览大小可能会超出摄像头的带宽限制
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // 我们将TextureView的宽高比与我们选择的预览大小相匹配。
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    //横屏
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    // 竖屏
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // 检查闪光灯是否支持。
                Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Log.d("Andy", "flashAvailable = " + flashAvailable);
                // always return 0 because camera has fixed focus
//                int[] afAvailableModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
//
//                CameraMetadata.CONTROL_AF_MODE_OFF = 0;


                Range<Integer> controlAECompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                if (controlAECompensationRange != null) {
                    minCompensationRange = controlAECompensationRange.getLower();
                    maxCompensationRange = controlAECompensationRange.getUpper();
                }


                mFlashSupported = flashAvailable == null ? false : flashAvailable;
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            //不支持Camera2API
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                    .show(this.getFragmentManager(), FRAGMENT_DIALOG);
        }
    }

//    public int getBrightnessValue() {
//        int absBrightnessRange = maxCompensationRange - minCompensationRange;
//        int value = getValue(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
//
//        Integer brightness =
//
//        return 100* (value - minCompensationRange)/ absBrightnessRange;
//    }

    Integer minCompensationRange, maxCompensationRange;

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * 根据他们的区域比较两个Size
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // 我们在这里投放，以确保乘法不会溢出
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * 开启摄像头
     *
     * @param width
     * @param height
     */
    private void openCamera(int width, int height) {
        //检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        //设置相机输出
        setUpCameraOutputs(width, height);
        //配置变换
        configureTransform(width, height);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            //打开相机预览
            mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void requestCameraPermission() {
//        if (FragmentCompat.shouldShowRequestPermissionRationale(this.get, Manifest.permission.CAMERA)) {
//            new Camera2BasicFragment.ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
//        } else {
//            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
//                    REQUEST_CAMERA_PERMISSION);
//        }
    }


    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
            finish();
        }
    };

    float[][] channels;

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            Log.d("Andy", "null = " + (texture == null));
            if (texture != null) {
                // 将默认缓冲区的大小配置为我们想要的相机预览的大小。 设置分辨率
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                Surface surface = new Surface(texture);
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewRequestBuilder.addTarget(surface);

                mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        if (null == mCameraDevice) {
                            return;
                        }

                        mCaptureSession = session;
                        try {
                            //auto focus
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_OFF);

                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);


//                            CameraCharacteristics mCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);

//                            Range<Integer> range1 = mCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
//                            Rational step = mCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
//
//                            int minExposure = range1.getLower();
//                            int maxExposure = range1.getUpper();
//                            Log.d("Andy", "minExposure = " + minExposure );
//                            Log.d("Andy", "maxExposure = " + maxExposure );
//                            Log.d("Andy", "step = " + step.floatValue());
//
//                            if (minExposure != 0 || maxExposure != 0) {
//
//                                int i = 0;
//                                int all = (-minExposure) + maxExposure;
//                                int time = 100 / all;
//                                int ae = ((i / time) - maxExposure) > maxExposure ? maxExposure : ((i / time) - maxExposure) < minExposure ? minExposure : ((i / time) - maxExposure);
//
//                                Log.d("Andy", "ae = " + ae);
//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae);
//                            }


                            Log.d("Andy", "CONTROL_AE_EXPOSURE_COMPENSATION = " +
                                    mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION));
                            Log.d("Andy", "CONTROL_AE_LOCK = " +
                                    mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_LOCK));

                            // flash light
                            setAutoFlash(mPreviewRequestBuilder);
                            mPreviewRequest = mPreviewRequestBuilder.build();
                            mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        Log.e("Andy", " 开启相机预览并添加事件");

                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e("Andy", " onConfigureFailed 开启预览失败");

                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //
    private void updateCameraPreviewSession(int i) throws CameraAccessException {
        Log.d("Andy", "CONTROL_AE_MODE = " +
                mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE));

        Log.d("Andy", "CONTROL_AE_EXPOSURE_COMPENSATION = " +
                mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION));

        Log.d("Andy", "CONTROL_AE_LOCK = " +
                mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_LOCK));


        CameraCharacteristics mCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);

        Range<Integer> range1 = mCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        Rational step = mCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);

        int minExposure = range1.getLower();
        int maxExposure = range1.getUpper();
        Log.d("Andy", "minExposure = " + minExposure);
        Log.d("Andy", "maxExposure = " + maxExposure);
        Log.d("Andy", "step = " + step.floatValue());

        if (minExposure != 0 || maxExposure != 0) {

            int all = (-minExposure) + maxExposure;
            int time = 100 / all;
            int ae = ((i / time) - maxExposure) > maxExposure ? maxExposure : ((i / time) - maxExposure) < minExposure ? minExposure : ((i / time) - maxExposure);

            Log.d("Andy", "ae = " + ae);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae);
        }

        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);

    }

    private Range<Integer> getRange() {
        CameraCharacteristics chars = null;
        try {
            chars = mCameraManager.getCameraCharacteristics(mCameraId);
            Range<Integer>[] ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Range<Integer> result = null;
            for (Range<Integer> range : ranges) {
                int upper = range.getUpper();
                // 10 - min range upper for my needs
                if (upper >= 10) {
                    if (result == null || upper < result.getUpper().intValue()) {
                        result = range;
                    }
                }
            }
            if (result == null) {
                result = ranges[0];
            }
            return result;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }
    }


    void takePicture() {
        lockFocus();
    }

    /**
     * 将焦点锁定为静态图像捕获的第一步。（对焦）
     */
    private void lockFocus() {
        Log.d("Andy", "lockFocus");
        try {
            // 相机对焦
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // 修改状态
            mState = STATE_WAITING_LOCK;
//            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -6);
            Log.d("Andy", "CONTROL_AE_EXPOSURE_COMPENSATION = " +
                    mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION));
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解锁焦点
     */
    private void unlockFocus() {
        try {
            // 重置自动对焦
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // 将相机恢复正常的预览状态。
            mState = STATE_PREVIEW;
            // 打开连续取景模式
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        Log.d("Andy", "setAutoFlash = "  + mFlashSupported);
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * 处理与JPEG捕获有关的事件
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        //处理
        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    //预览状态
                    break;
                }

                case STATE_WAITING_LOCK: {
                    //等待对焦
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_INACTIVE == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        captureStillPicture();
//                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
//                            mState = STATE_PICTURE_TAKEN;
//                            //对焦完成
//                            captureStillPicture();
//                        } else {
//                            runPrecaptureSequence();
//                        }
                    }
                    break;
                }


            }
        }
    };

    /**
     * 运行捕获静止图像的预捕获序列。 当我们从{@link #lockFocus（）}的{@link #mCaptureCallback}中得到响应时，应该调用此方法。
     */
    private void runPrecaptureSequence() {
        try {
            // 这是如何告诉相机触发的。
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // 告诉 mCaptureCallback 等待preapture序列被设置.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     * 拍摄静态图片。
     */
    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // 这是用来拍摄照片的CaptureRequest.Builder。
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // 使用相同的AE和AF模式作为预览。
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            // Set brightness
            int value = 110;
            int brightness = (int) (minCompensationRange + (maxCompensationRange - minCompensationRange) * (value / 100f));
            captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);


            setAutoFlash(captureBuilder);

            // 方向
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    showToast("图片地址: " + mFile);
                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }
            };
            //停止连续取景
            mCaptureSession.stopRepeating();
            //捕获图片
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
