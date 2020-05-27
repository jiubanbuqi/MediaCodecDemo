package com.example.mediacodecdemo;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.RequiresApi;

import java.io.IOException;

public class CameraCapture {

    private SurfaceTexture surfaceTexture;
    private Camera camera;
    private int cameraFacing= Camera.CameraInfo.CAMERA_FACING_BACK;
    private int width=640;
    private int height=480;
    private int frameRate=30;
    private CameraRecordCallback cameraRecordCallback;

    public CameraCapture(SurfaceTexture surfaceTexture){
        this.surfaceTexture = surfaceTexture;
    }

    public void setCameraRecordCallback(CameraRecordCallback cameraRecordCallback){
        this.cameraRecordCallback = cameraRecordCallback;
    }

    public int getWidth() {
        return height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public void startCamera(){
        if(camera==null){
            try {
                camera = Camera.open(cameraFacing);
                Camera.Parameters parameters = camera.getParameters();
                parameters.setPreviewFrameRate(frameRate);
                parameters.setPreviewSize(width,height);
                parameters.setPictureSize(width,height);
                if(cameraFacing== Camera.CameraInfo.CAMERA_FACING_BACK){
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                }

                camera.setParameters(parameters);
                camera.setPreviewTexture(surfaceTexture);
                camera.setPreviewCallback(previewCallback);
                camera.startPreview();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void stopCamera(){
        if(camera!=null){
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            if(cameraRecordCallback!=null){
                data = NV21ToNV12(data,width,height);
                data = NV21_rotate_to_90(data,width,height);
                cameraRecordCallback.onRecordImage(data);
            }
        }
    };


    public interface CameraRecordCallback {
        // start 在哪个线程调用，就运行在哪个线程
        void onRecordImage(byte[] data);
    }


    private byte[] NV21ToNV12(byte[] nv21, int width, int height) {
        byte[] nv12 = new byte[width * height * 3 / 2];
        int frameSize = width * height;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) { nv12[i] = nv21[i]; }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j - 1] = nv21[j + frameSize];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j] = nv21[j + frameSize - 1];
        }
        return nv12;
    }


    private byte[] NV21_rotate_to_90(byte[] nv21_data, int width, int height)
    {
        int y_size = width * height;
        int buffser_size = y_size * 3 / 2;
        byte[] nv21_rotated = new byte[buffser_size];
        // Rotate the Y luma

        int i = 0;
        int startPos = (height - 1)*width;
        for (int x = 0; x < width; x++)
        {
            int offset = startPos;
            for (int y = height - 1; y >= 0; y--)
            {
                nv21_rotated[i] = nv21_data[offset + x];
                i++;
                offset -= width;
            }
        }

        // Rotate the U and V color components
        i = buffser_size - 1;
        for (int x = width - 1; x > 0; x = x - 2)
        {
            int offset = y_size;
            for (int y = 0; y < height / 2; y++)
            {
                nv21_rotated[i] = nv21_data[offset + x];
                i--;
                nv21_rotated[i] = nv21_data[offset + (x - 1)];
                i--;
                offset += width;
            }
        }
        return nv21_rotated;
    }
}
