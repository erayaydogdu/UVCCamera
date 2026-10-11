/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest0;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;

public class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
	private static final boolean DEBUG = true;	// TODO set false when production
	private static final String TAG = "MainActivity";

	// preview size requested from the capture device
	private static final int PREVIEW_WIDTH = 1920;
	private static final int PREVIEW_HEIGHT = 1080;

    private final Object mSync = new Object();
    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
	private UVCCamera mUVCCamera;
	private SurfaceView mUVCCameraView;
	// for open&start / stop&close camera preview
	private ImageButton mCameraButton;
	private Surface mPreviewSurface;
	private boolean isActive, isPreview;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mCameraButton = (ImageButton)findViewById(R.id.camera_button);
		mCameraButton.setOnClickListener(mOnClickListener);

		mUVCCameraView = (SurfaceView)findViewById(R.id.camera_surface_view);
		mUVCCameraView.getHolder().addCallback(mSurfaceViewCallback);
		// keep the aspect ratio of the preview instead of stretching it over the whole screen
		findViewById(R.id.container).addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(final View v, final int left, final int top, final int right, final int bottom,
				final int oldLeft, final int oldTop, final int oldRight, final int oldBottom) {
				if ((right - left != oldRight - oldLeft) || (bottom - top != oldBottom - oldTop)) {
					updatePreviewLayout(right - left, bottom - top);
				}
			}
		});

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
	}

	private void updatePreviewLayout(final int parentWidth, final int parentHeight) {
		final SurfaceView view = mUVCCameraView;
		if ((view == null) || (parentWidth <= 0) || (parentHeight <= 0)) return;
		int width = parentWidth;
		int height = parentWidth * PREVIEW_HEIGHT / PREVIEW_WIDTH;
		if (height > parentHeight) {
			height = parentHeight;
			width = parentHeight * PREVIEW_WIDTH / PREVIEW_HEIGHT;
		}
		final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)view.getLayoutParams();
		if ((params.width != width) || (params.height != height)) {
			params.width = width;
			params.height = height;
			view.post(new Runnable() {
				@Override
				public void run() {
					view.setLayoutParams(params);
				}
			});
		}
	}

	@Override
	public void onWindowFocusChanged(final boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			// hide the status bar and the navigation bar, swipe from the edge to show them temporarily
			getWindow().getDecorView().setSystemUiVisibility(
				View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
				| View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		// USB permission for UVC devices is refused on Android 9+ without CAMERA permission
		checkPermissionCamera();
		synchronized (mSync) {
			if (mUSBMonitor != null) {
				mUSBMonitor.register();
			}
		}
	}

	@Override
	protected void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");
		synchronized (mSync) {
			if (mUSBMonitor != null) {
				mUSBMonitor.unregister();
			}
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		synchronized (mSync) {
			isActive = isPreview = false;
			if (mUVCCamera != null) {
				mUVCCamera.destroy();
				mUVCCamera = null;
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.destroy();
				mUSBMonitor = null;
			}
		}
		mUVCCameraView = null;
		mCameraButton = null;
		super.onDestroy();
	}

	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (mUVCCamera == null) {
				// XXX calling CameraDialog.showDialog is necessary at only first time(only when app has no permission).
				CameraDialog.showDialog(MainActivity.this);
			} else {
				synchronized (mSync) {
					mUVCCamera.destroy();
					mUVCCamera = null;
					isActive = isPreview = false;
				}
			}
		}
	};

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "onAttach:");
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
			// start the preview without going through the device selection dialog
			synchronized (mSync) {
				if ((mUVCCamera == null) && (mUSBMonitor != null)
					&& mUSBMonitor.getDeviceList(DeviceFilter.getDeviceFilters(MainActivity.this, R.xml.device_filter)).contains(device)) {
					mUSBMonitor.requestPermission(device);
				}
			}
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "onConnect:");
			synchronized (mSync) {
				if (mUVCCamera != null) {
					mUVCCamera.destroy();
				}
				isActive = isPreview = false;
			}
			queueEvent(new Runnable() {
				@Override
				public void run() {
					synchronized (mSync) {
						final UVCCamera camera = new UVCCamera();
						camera.open(ctrlBlock);
						if (DEBUG) Log.i(TAG, "supportedSize:" + camera.getSupportedSize());
						try {
							camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
						} catch (final IllegalArgumentException e) {
							try {
								// fallback to YUV mode
								camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
							} catch (final IllegalArgumentException e1) {
								camera.destroy();
								return;
							}
						}
						mPreviewSurface = mUVCCameraView.getHolder().getSurface();
						if (mPreviewSurface != null) {
							isActive = true;
							camera.setPreviewDisplay(mPreviewSurface);
							camera.startPreview();
							isPreview = true;
						}
						synchronized (mSync) {
							mUVCCamera = camera;
						}
					}
				}
			}, 0);
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			// XXX you should check whether the comming device equal to camera device that currently using
			queueEvent(new Runnable() {
				@Override
				public void run() {
					synchronized (mSync) {
						// the Surface belongs to the SurfaceView and is reused when the device comes back,
						// so it must not be released here
						if (mUVCCamera != null) {
							mUVCCamera.destroy();
							mUVCCamera = null;
						}
						isActive = isPreview = false;
					}
				}
			}, 0);
		}

		@Override
		public void onDettach(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "onDettach:");
			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
		}
	};

	/**
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	@Override
	public void onDialogResult(boolean canceled) {
		if (canceled) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// FIXME
				}
			}, 0);
		}
	}

	private final SurfaceHolder.Callback mSurfaceViewCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(final SurfaceHolder holder) {
			if (DEBUG) Log.v(TAG, "surfaceCreated:");
		}

		@Override
		public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
			if ((width == 0) || (height == 0)) return;
			if (DEBUG) Log.v(TAG, "surfaceChanged:");
			mPreviewSurface = holder.getSurface();
			synchronized (mSync) {
				if (isActive && !isPreview && (mUVCCamera != null)) {
					mUVCCamera.setPreviewDisplay(mPreviewSurface);
					mUVCCamera.startPreview();
					isPreview = true;
				}
			}
		}

		@Override
		public void surfaceDestroyed(final SurfaceHolder holder) {
			if (DEBUG) Log.v(TAG, "surfaceDestroyed:");
			synchronized (mSync) {
				if (mUVCCamera != null) {
					mUVCCamera.stopPreview();
				}
				isPreview = false;
			}
			mPreviewSurface = null;
		}
	};
}
