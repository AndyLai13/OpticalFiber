package com.lightel.opticalfiber;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RTSPActivity extends AppCompatActivity implements View.OnClickListener,
        TextureView.SurfaceTextureListener,
        MediaPlayer.OnBufferingUpdateListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnVideoSizeChangedListener {

    ImageButton snapButton;
    String rtspUrl = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov";

    private MediaPlayer mediaPlayer;
    private TextureView textureView;
    public static String TAG = "Andy";

    private static final int REQUEST_PERMISSIONS = 100;
    private static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtsp);

        snapButton = findViewById(R.id.capture_button);
        snapButton.setOnClickListener(this);

        textureView = findViewById(R.id.camera_view);
        textureView.setSurfaceTextureListener(this);

        if (!hasPermissionsGranted(this)) {
            requestPermissions(STORAGE_PERMISSIONS, REQUEST_PERMISSIONS);
        }
    }

    private boolean hasPermissionsGranted(Context context) {
        for (String permission : STORAGE_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                finish();
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.capture_button:
                getBitmap(textureView);
                break;
        }
    }


    public void getBitmap(TextureView vv) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        //You can change "yyyyMMdd_HHmmss as per your requirement
        String currentDateandTime = sdf.format(new Date());

        String mPath = getFilesDir()
                + "/"
                + currentDateandTime + ".jpg";
        Toast.makeText(getApplicationContext(), "Capturing Screenshot: " + mPath, Toast.LENGTH_SHORT).show();

        Bitmap bm = vv.getBitmap();
        if (bm == null)
            Log.e(TAG, "bitmap is null" + bm);

        File imageFile = new File(mPath);
        Log.e(TAG, "imageFile = " + imageFile.getPath());
        try {
            FileOutputStream fout = new FileOutputStream(imageFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 90, fout);
            fout.flush();
            fout.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "IOException");
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Surface s = new Surface(surface);

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(rtspUrl);
            mediaPlayer.setSurface(s);
            mediaPlayer.prepare();

            mediaPlayer.setOnBufferingUpdateListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnVideoSizeChangedListener(this);

            mediaPlayer.start();

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        surface.release();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public void onCompletion(MediaPlayer mp) {

    }

    @Override
    public void onPrepared(MediaPlayer mp) {

    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {

    }
}
