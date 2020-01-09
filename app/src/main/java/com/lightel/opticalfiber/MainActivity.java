package com.lightel.opticalfiber;

import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.serenegiant.usb.USBMonitor;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private USBMonitor mUSBMonitor;
    Button mBtnDI1000;
    Button mBtnDI1000L;
    Button mBtnDI2000;
    Button mBtnDI3000;
    Button mBtnDI5000;
    Button mBtnSettings;

    Probe DI1000;
    Probe DI1000L;
    Probe DI2000;
    Probe DI3000;
    Probe DI5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);
        initViews();

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mUSBMonitor.register();

        initProbe();
        updateWifiProbeCapability();
        enableProbeButtons();
    }

    void initViews() {
        mBtnDI1000 = findViewById(R.id.di1000);
        mBtnDI1000L = findViewById(R.id.di1000l);
        mBtnDI2000 = findViewById(R.id.di2000);
        mBtnDI3000 = findViewById(R.id.di3000);
        mBtnDI5000 = findViewById(R.id.di5000);
        mBtnSettings = findViewById(R.id.settings);

        mBtnDI1000.setOnClickListener(this);
        mBtnDI1000L.setOnClickListener(this);
        mBtnDI2000.setOnClickListener(this);
        mBtnDI3000.setOnClickListener(this);
        mBtnDI5000.setOnClickListener(this);
        mBtnSettings.setOnClickListener(this);
    }

    void initProbe() {
        if (DI1000 == null) {
            DI1000 = new Probe("DI1000", false);
        }

        if (DI1000L == null) {
            DI1000L = new Probe("DI1000L", false);
        }

        if (DI2000 == null) {
            DI2000 = new Probe("DI2000", false);
        }

        if (DI3000 == null) {
            DI3000 = new Probe("DI3000", false);
        }

        if (DI5000 == null) {
            DI5000 = new Probe("DI500", false);
        }
    }

    void enableProbeButtons() {
        Log.d("Andy", "enableProbeButtons");
        mBtnDI1000.setEnabled(DI1000.enable);
        mBtnDI1000L.setEnabled(DI1000L.enable);
        mBtnDI2000.setEnabled(DI2000.enable);
        mBtnDI3000.setEnabled(DI3000.enable);
        mBtnDI5000.setEnabled(DI5000.enable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.di1000:
                startActivity(new Intent(this, InspectorActivity.class));
                break;
            case R.id.di1000l:
                break;
            case R.id.di2000:
                startActivity(new Intent(this, UVCCameraActivity.class));
                break;
            case R.id.di3000:
                startActivity(new Intent(this, RTSPActivity.class));
                break;
            case R.id.di5000:
            case R.id.settings:
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.droidlogic.tv.settings");
                if (launchIntent != null) {
                    startActivity(launchIntent);//null pointer check in case package name was not found
                }
                break;
        }
    }

    void updateUSBProbeCapability(UsbDevice device, boolean enable) {
        runOnUiThread(() -> {
            initProbe();
            String vid = Integer.toHexString(device.getVendorId()).toUpperCase();
            String pid = Integer.toHexString(device.getProductId()).toUpperCase();
            String probeId = vid + ":" + pid;
            Log.d("Andy", "probeID = " + probeId + ", enable = " + enable);
            switch (probeId) {
                case "0AC8:3410":
                case "0AC8/3420":
                case "0AC8/C100":
                    DI1000.enable = enable;
                    DI1000L.enable = enable;
                case "2AAD:6503":
                    DI2000.enable = enable;
                    break;
            }
            enableProbeButtons();
        });
    }

    void updateWifiProbeCapability() {
        DI3000.enable = isDI3000Available();
        DI5000.enable = isDI5000Available();
        enableProbeButtons();
    }

    boolean isDI3000Available() {
        return isNetworkConnected() && isDI3000RTSPUrlAvailable();
    }

    boolean isDI5000Available() {
        return isNetworkConnected() && isDI5000RTSPUrlAvailable();
    }

    boolean isNetworkConnected() {
        return false;
    }

    boolean isDI3000RTSPUrlAvailable() {
        return false;
    }

    boolean isDI5000RTSPUrlAvailable() {
        return false;
    }


    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            updateUSBProbeCapability(device, true);
            Log.d("Andy", "USB_DEVICE_ATTACHED");
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
        }

        @Override
        public void onDettach(final UsbDevice device) {
            updateUSBProbeCapability(device, false);
            Log.d("Andy", "USB_DEVICE_DETACHED");
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    private class Probe {
        String probeName;
        boolean enable;

        Probe(String probeName, boolean enable) {
            this.probeName = probeName;
            this.enable = enable;
        }
    }
}
