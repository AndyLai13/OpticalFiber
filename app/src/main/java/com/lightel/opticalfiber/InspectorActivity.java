package com.lightel.opticalfiber;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Button;

import java.util.Arrays;

public class InspectorActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

	SurfaceView mSurfaceView;
	Button mButtonTake;
	Boolean mFlashSupported;
	String mCameraId;
	CameraDevice mCameraDevice;
	CaptureRequest.Builder mPreviewRequestBuilder;
	CaptureRequest mPreviewRequest;
	CameraCaptureSession mCaptureSession;
	Handler mBackgroundHandler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_inspector);
		mSurfaceView = findViewById(R.id.surfaceView);
		mButtonTake = findViewById(R.id.take_photo);


		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
		} else {
			initCamera();
		}
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		if (requestCode == 50) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				initCamera();
			} else {
				finish();
			}
		}
	}


	void initCamera() {
		CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		try {
			if (manager != null) {
				for (String cameraId : manager.getCameraIdList()) {
					CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
					Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
					// ignore front camera
					if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
						continue;
					}

					StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
					if (map == null) {
						continue;
					}

					Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
					mFlashSupported = available == null ? false : available;
					mCameraId = cameraId;
				}
			}


		} catch (CameraAccessException | NullPointerException e) {
			e.printStackTrace();
		}

		if (mCameraId != null && manager != null) {
			try {
				if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
					return;
				}
				manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
			} catch (CameraAccessException e) {
				e.printStackTrace();
			}
		}
	}

	CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(@NonNull CameraDevice camera) {
			mCameraDevice = camera;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(@NonNull CameraDevice camera) {
			camera.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(@NonNull CameraDevice camera, int error) {
			onDisconnected(camera);
		}
	};

	void createCameraPreviewSession() {
		try {
			Surface surface = mSurfaceView.getHolder().getSurface();
			mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mPreviewRequestBuilder.addTarget(surface);

			mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
				@Override
				public void onConfigured(@NonNull CameraCaptureSession session) {
					if (null == mCameraDevice) {
						return;
					}

					mCaptureSession = session;
					try {
						//auto focus
						mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
								CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
						// flash light
						//setAutoFlash(mPreviewRequestBuilder);
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
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}
}
