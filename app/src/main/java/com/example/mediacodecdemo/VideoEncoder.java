package com.example.mediacodecdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoEncoder {

    private int width;
    private int height;
    private int frameRate;
    private int bitRate;
    private MediaCodec mediaCodec;
    private LinkedBlockingQueue<MediaData> mediaQueue = new LinkedBlockingQueue(500);
    private Object lockObj = new Object();
    private byte[] configByte;
    private String path= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"test.mp4";
    private FileOutputStream fileOutputStream;
    private Thread encodeThread;

    public VideoEncoder(int width, int height, int frameRate, int bitRate){
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.bitRate = bitRate;
        try {
            fileOutputStream = new FileOutputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void start(){
        encodeThread = new Thread(encodeRunnable);
        encodeThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void stop(){
        if(encodeThread!=null){
            encodeThread.interrupt();
        }
        if(mediaCodec!=null){
            mediaCodec.stop();
            mediaCodec.release();
        }
        try {
            if(fileOutputStream!=null){
                fileOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    Runnable encodeRunnable = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            doEncode();
        }
    };

    public void putData(byte[]data,int offset,int length){
        MediaData mediaData = new MediaData(data,offset,length);
        synchronized (lockObj){
            try {
                mediaQueue.put(mediaData);
                lockObj.notify();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void doEncode(){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,width*height*5);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,30);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        long count=0;
        while(!Thread.interrupted()){

            synchronized (lockObj){
                if(mediaQueue.isEmpty()){
                    try {
                        lockObj.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else{
                    MediaData mediaData = mediaQueue.poll();
                    long startMs = System.currentTimeMillis();
                    ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                    ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                    int inIndex = mediaCodec.dequeueInputBuffer(0);
                    if(inIndex>=0){
                        ByteBuffer inputBuffer = inputBuffers[inIndex];
                        inputBuffer.put(mediaData.getData());
                        mediaCodec.queueInputBuffer(inIndex,0,mediaData.getLength(),computePresentationTime(count),0);
                        count++;
                    }

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,10000);
                    while (outIndex>=0){
                        try {
                            ByteBuffer outputBuffer = outputBuffers[outIndex];
                            byte[] outData=new byte[bufferInfo.size];
                            outputBuffer.get(outData);
                            if(bufferInfo.flags==MediaCodec.BUFFER_FLAG_CODEC_CONFIG){
                                configByte = outData;
                            }else if(bufferInfo.flags==MediaCodec.BUFFER_FLAG_KEY_FRAME){
                                byte[] keyframe = new byte[bufferInfo.size+configByte.length];
                                System.arraycopy(configByte,0,keyframe,0,configByte.length);
                                System.arraycopy(outData,0,keyframe,configByte.length,outData.length);
                                fileOutputStream.write(keyframe,0,keyframe.length);
                            }else{
                                fileOutputStream.write(outData,0,outData.length);
                            }

                            mediaCodec.releaseOutputBuffer(outIndex,false);
                            outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                        }catch (IOException e){
                            e.printStackTrace();
                        }

                    }

                }
            }
        }
    }

    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / frameRate;
    }

}
