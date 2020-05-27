package com.example.mediacodecdemo;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;

import com.example.mediacodecdemo.databinding.ActivityMainBinding;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import io.reactivex.functions.Consumer;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

public class MainActivity extends AppCompatActivity {

    String TAG ="androidLog";
    TextureView textureView;
    Surface mSurface;

    String path= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"a.mp4";
//    String path="http://vfx.mtime.cn/Video/2019/03/21/mp4/190321153853126488.mp4";
    CameraCapture cameraCapture;
    AudioRecorder audioRecorder;
    VideoRecoder videoRecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
        ActivityMainBinding activityMainBinding = DataBindingUtil.setContentView(this,R.layout.activity_main);

        RxPermissions rxPermissions = new RxPermissions(this);
        rxPermissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
                .subscribe((flag)->{
                    if(flag){
                        activityMainBinding.textureView.setSurfaceTextureListener(surfaceTextureListener);
                    }
                });

    }

    boolean flag =true;
    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mSurface = new Surface(surface);
//            SurfaceHolder surfaceHolder = new S
//            VideoDecoder videoDecoder = new VideoDecoder(mSurface,path);
//            videoDecoder.start();

            if(flag){
                flag = false;
                videoRecoder = new VideoRecoder();

                cameraCapture = new CameraCapture(surface);
                videoRecoder.setVideoParams(cameraCapture.getWidth(),cameraCapture.getHeight(),cameraCapture.getFrameRate(),1024*1000);
                cameraCapture.setCameraRecordCallback(new CameraCapture.CameraRecordCallback() {
                    @Override
                    public void onRecordImage(byte[] data) {
                        try {
                            videoRecoder.putVideoData(data,0,data.length);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                cameraCapture.startCamera();

                audioRecorder = new AudioRecorder();
                videoRecoder.setAudioParams(audioRecorder.getChannels(),audioRecorder.getSampleRate(),128*1000);
                audioRecorder.setRecordCallback(new AudioRecorder.AudioRecordCallback() {
                    @Override
                    public void onRecordSample(byte[] data) {
                        try {
                            videoRecoder.putAudioData(data,0,data.length);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                audioRecorder.start();

                videoRecoder.init();
                videoRecoder.start();
            }


        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {

            cameraCapture.stopCamera();
            audioRecorder.stop();
            videoRecoder.stop();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
}
