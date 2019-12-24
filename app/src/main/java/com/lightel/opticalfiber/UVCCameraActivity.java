package com.lightel.opticalfiber;

import android.animation.Animator;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.utils.ViewAnimationHelper;
import com.serenegiant.widget.CameraViewInterface;
import com.serenegiant.widget.UVCCameraTextureView;

public class UVCCameraActivity extends BaseActivity implements CameraDialog.CameraDialogParent,
        View.OnClickListener {

    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "MainActivity";

    /**
     * set true if you want to record movie using MediaSurfaceEncoder
     * (writing frame data into Surface camera from MediaCodec
     * by almost same way as USBCameratest2)
     * set false if you want to record movie using MediaVideoEncoder
     */
    private static final boolean USE_SURFACE_ENCODER = false;

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 1280;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 960;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 1;

    protected static final int SETTINGS_HIDE_DELAY_MS = 2500;

    /**
     * for accessing USB
     */
    private USBMonitor mUSBMonitor;
    /**
     * Handler to execute camera related methods sequentially on private thread
     */
    private UVCCameraHandler mCameraHandler;
    /**
     * for camera preview display
     */
    private CameraViewInterface mUVCCameraView;
    /**
     * for open&start / stop&close camera preview
     */
    private ToggleButton mCameraButton;
    /**
     * button for start/stop recording
     */
    private ImageButton mCaptureButton;

    private View mBrightnessButton, mContrastButton, mBtnSettings, mBtnSave;


    private View mResetButton;
    private View mToolsLayout, mValueLayout;
    private SeekBar mSettingSeekbar;

    private Spinner mSpinnerFiberType;


    String[] fiberType = {"SM", "MM", "MPO"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uvccamera);

        mCameraButton = findViewById(R.id.camera_button);
        mCaptureButton = findViewById(R.id.capture_button);
        mBrightnessButton = findViewById(R.id.btnBrightness);
        mContrastButton = findViewById(R.id.btnContrast);
        mResetButton = findViewById(R.id.reset_button);
        mToolsLayout = findViewById(R.id.tools_layout);
        mValueLayout = findViewById(R.id.value_layout);
        mSettingSeekbar = findViewById(R.id.setting_seekbar);
        mUVCCameraView = findViewById(R.id.camera_view);
        mBtnSettings = findViewById(R.id.btnSettings);
        mBtnSave = findViewById(R.id.btnSaveImageAndReport);
        mSpinnerFiberType = findViewById(R.id.spinnerFiberType);

        mCaptureButton.setOnClickListener(this);
        mBrightnessButton.setOnClickListener(this);
        mContrastButton.setOnClickListener(this);
        mResetButton.setOnClickListener(this);
        mBtnSettings.setOnClickListener(this);
        mBtnSave.setOnClickListener(this);

        mSpinnerFiberType.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, fiberType));

        mSpinnerFiberType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        Toast.makeText(UVCCameraActivity.this, "fiber type = " + "SM", Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        Toast.makeText(UVCCameraActivity.this, "fiber type = " + "MM", Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        Toast.makeText(UVCCameraActivity.this, "fiber type = " + "MPO", Toast.LENGTH_SHORT).show();
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
        ((UVCCameraTextureView) mUVCCameraView).setOnLongClickListener(mOnLongClickListener);
        mSettingSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

        mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);

        mCaptureButton.setVisibility(View.INVISIBLE);
//        mToolsLayout.setVisibility(View.INVISIBLE);
        mValueLayout.setVisibility(View.INVISIBLE);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
                USE_SURFACE_ENCODER ? 0 : 1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (DEBUG) Log.v(TAG, "onStart:");
        mUSBMonitor.register();
        if (mUVCCameraView != null)
            mUVCCameraView.onResume();
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.v(TAG, "onStop:");
        mCameraHandler.close();
        if (mUVCCameraView != null)
            mUVCCameraView.onPause();
        setCameraButton(false);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.v(TAG, "onDestroy:");
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
        mCameraButton = null;
        mCaptureButton = null;
        super.onDestroy();
    }

    /**
     * event handler when click camera / capture button
     */
    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.capture_button:
                if (mCameraHandler.isOpened()) {
                    if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
                        if (!mCameraHandler.isRecording()) {
                            mCaptureButton.setColorFilter(0xffff0000);    // turn red
                            mCameraHandler.startRecording();
                        } else {
                            mCaptureButton.setColorFilter(0);    // return to default color
                            mCameraHandler.stopRecording();
                        }
                    }
                }
                break;
            case R.id.btnBrightness:
                showSettings(UVCCamera.PU_BRIGHTNESS);
                break;
            case R.id.btnContrast:
                showSettings(UVCCamera.PU_CONTRAST);
                break;
            case R.id.reset_button:
                resetSettings();
                break;
            case R.id.btnSettings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.btnSaveImageAndReport:
                startActivity(new Intent(this, SaveImageReportActivity.class));
                break;
        }
    }

    private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
            = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
            switch (compoundButton.getId()) {
                case R.id.camera_button:
                    if (isChecked && !mCameraHandler.isOpened()) {
                        CameraDialog.showDialog(UVCCameraActivity.this);
                    } else {
                        mCameraHandler.close();
                        setCameraButton(false);
                    }
                    break;
            }
        }
    };

    /**
     * capture still image when you long click on preview image(not on buttons)
     */
    private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(final View view) {
            switch (view.getId()) {
                case R.id.camera_view:
                    if (mCameraHandler.isOpened()) {
                        if (checkPermissionWriteExternalStorage()) {
                            mCameraHandler.captureStill();
                        }
                        return true;
                    }
            }
            return false;
        }
    };

    private void setCameraButton(final boolean isOn) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCameraButton != null) {
                    try {
                        mCameraButton.setOnCheckedChangeListener(null);
                        mCameraButton.setChecked(isOn);
                    } finally {
                        mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
                    }
                }
                if (!isOn && (mCaptureButton != null)) {
                    mCaptureButton.setVisibility(View.INVISIBLE);
                }
            }
        }, 0);
        updateItems();
    }

    private void startPreview() {
        final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
        mCameraHandler.startPreview(new Surface(st));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCaptureButton.setVisibility(View.VISIBLE);
            }
        });
        updateItems();
    }

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(UVCCameraActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (DEBUG) Log.v(TAG, "onConnect:");
            mCameraHandler.open(ctrlBlock);
            startPreview();
            updateItems();
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.v(TAG, "onDisconnect:");
            if (mCameraHandler != null) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mCameraHandler.close();
                    }
                }, 0);
                setCameraButton(false);
                updateItems();
            }
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(UVCCameraActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
            setCameraButton(false);
        }
    };

    /**
     * to access from CameraDialog
     *
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (DEBUG) Log.v(TAG, "onDialogResult:canceled=" + canceled);
        if (canceled) {
            setCameraButton(false);
        }
    }

    //================================================================================
    private boolean isActive() {
        return mCameraHandler != null && mCameraHandler.isOpened();
    }

    private boolean checkSupportFlag(final int flag) {
        return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
    }

    private int getValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
    }

    private int setValue(final int flag, final int value) {
        return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
    }

    private int resetValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
    }

    private void updateItems() {
        runOnUiThread(mUpdateItemsOnUITask, 100);
    }

    private final Runnable mUpdateItemsOnUITask = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            final int visible_active = isActive() ? View.VISIBLE : View.INVISIBLE;
//            mToolsLayout.setVisibility(visible_active);
//            mBrightnessButton.setVisibility(
//                    checkSupportFlag(UVCCamera.PU_BRIGHTNESS)
//                            ? visible_active : View.INVISIBLE);
//            mContrastButton.setVisibility(
//                    checkSupportFlag(UVCCamera.PU_CONTRAST)
//                            ? visible_active : View.INVISIBLE);
        }
    };

    private int mSettingMode = -1;

    /**
     * 設定画面を表示
     *
     * @param mode
     */
    private final void showSettings(final int mode) {
        if (DEBUG) Log.v(TAG, String.format("showSettings:%08x", mode));
        hideSetting(false);
        if (isActive()) {
            switch (mode) {
                case UVCCamera.PU_BRIGHTNESS:
                case UVCCamera.PU_CONTRAST:
                    mSettingMode = mode;
                    mSettingSeekbar.setProgress(getValue(mode));
                    ViewAnimationHelper.fadeIn(mValueLayout, -1, 0, mViewAnimationListener);
                    break;
            }
        }
    }

    private void resetSettings() {
        if (isActive()) {
            switch (mSettingMode) {
                case UVCCamera.PU_BRIGHTNESS:
                case UVCCamera.PU_CONTRAST:
                    mSettingSeekbar.setProgress(resetValue(mSettingMode));
                    break;
            }
        }
        mSettingMode = -1;
        ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
    }

    /**
     * 設定画面を非表示にする
     *
     * @param fadeOut trueならばフェードアウトさせる, falseなら即座に非表示にする
     */
    protected final void hideSetting(final boolean fadeOut) {
        removeFromUiThread(mSettingHideTask);
        if (fadeOut) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
                }
            }, 0);
        } else {
            try {
                mValueLayout.setVisibility(View.GONE);
            } catch (final Exception e) {
                // ignore
            }
            mSettingMode = -1;
        }
    }

    protected final Runnable mSettingHideTask = new Runnable() {
        @Override
        public void run() {
            hideSetting(true);
        }
    };

    /**
     * 設定値変更用のシークバーのコールバックリスナー
     */
    private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
            // 設定が変更された時はシークバーの非表示までの時間を延長する
            if (fromUser) {
                runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
            }
        }

        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
            // シークバーにタッチして値を変更した時はonProgressChangedへ
            // 行かないみたいなのでここでも非表示までの時間を延長する
            runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
            if (isActive() && checkSupportFlag(mSettingMode)) {
                switch (mSettingMode) {
                    case UVCCamera.PU_BRIGHTNESS:
                    case UVCCamera.PU_CONTRAST:
                        setValue(mSettingMode, seekBar.getProgress());
                        break;
                }
            }    // if (active)
        }
    };

    private final ViewAnimationHelper.ViewAnimationListener
            mViewAnimationListener = new ViewAnimationHelper.ViewAnimationListener() {
        @Override
        public void onAnimationStart(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
        }

        @Override
        public void onAnimationEnd(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
            final int id = target.getId();
            switch (animationType) {
                case ViewAnimationHelper.ANIMATION_FADE_IN:
                case ViewAnimationHelper.ANIMATION_FADE_OUT: {
                    final boolean fadeIn = animationType == ViewAnimationHelper.ANIMATION_FADE_IN;
                    if (id == R.id.value_layout) {
                        if (fadeIn) {
                            runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
                        } else {
                            mValueLayout.setVisibility(View.GONE);
                            mSettingMode = -1;
                        }
                    } else if (!fadeIn) {
//					target.setVisibility(View.GONE);
                    }
                    break;
                }
            }
        }

        @Override
        public void onAnimationCancel(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
        }
    };
}
