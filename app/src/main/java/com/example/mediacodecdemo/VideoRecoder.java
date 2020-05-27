package com.example.mediacodecdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoRecoder {

    private int width;
    private int height;
    private int frameRate;
    private int videoBitRate;

    private int sampleRate;
    private int channel;
    private int audioBitRate;

    private MediaCodec videoCodec;
    private MediaCodec audioCodec;
    private MediaMuxer mediaMuxer;

    private int videoTrackIndex;
    private int audioTrackIndex;

    private volatile boolean mMuxerStarted;
    private boolean mIsInitialized=false;
    private static long count=0;


    private LinkedBlockingQueue<MediaData> videoDataQueue = new LinkedBlockingQueue(500);
    private LinkedBlockingQueue<MediaData> audioDataQueue = new LinkedBlockingQueue(500);
    private Object videoLock = new Object();
    private Object audioLock = new Object();
    private String path= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"test.mp4";
    private Thread encodeAudioThread;
    private Thread encodeVideoThread;


    public void setVideoParams(int width,int height,int frameRate,int videoBitRate){
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.videoBitRate = videoBitRate;
    }

    public void setAudioParams(int channel,int sampleRate,int audioBitRate){
        this.channel = channel;
        this.sampleRate = sampleRate;
        this.audioBitRate = audioBitRate;
    }

    public void init(){

        try {

            MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE,frameRate);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE,videoBitRate);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,5);
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            videoCodec.configure(videoFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoCodec.start();

            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,sampleRate,channel);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE,audioBitRate);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioCodec.configure(audioFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioCodec.start();

            mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            mIsInitialized = true;
            mMuxerStarted = false;
            videoTrackIndex = -1;
            audioTrackIndex = -1;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start(){
        encodeAudioThread = new Thread(encodeAudioRunnable);
        encodeAudioThread.start();

        encodeVideoThread = new Thread(encodeVideoRunnable);
        encodeVideoThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void stop(){
        if(encodeVideoThread!=null){
            encodeVideoThread.interrupt();
        }

        if(encodeAudioThread!=null){
            encodeAudioThread.interrupt();
        }

        if (videoCodec != null) {
            videoCodec.stop();
            videoCodec.release();
            videoCodec = null;
        }

        if (audioCodec != null) {
            audioCodec.stop();
            audioCodec.release();
            audioCodec = null;
        }

        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }
    }

    private Runnable encodeAudioRunnable = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            while(!Thread.interrupted()){

                if(audioDataQueue.isEmpty()){

                    synchronized (audioLock){
                        try {
                            audioLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }else{
                    if(!mIsInitialized){
                        continue;
                    }
                    MediaData mediaData = audioDataQueue.poll();
                    doEncode(audioCodec,mediaData.getData(),mediaData.getOffset(),mediaData.getLength());
                }
            }
        }
    };

    private Runnable encodeVideoRunnable = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            while(!Thread.interrupted()){

                if(videoDataQueue.isEmpty()){
                    synchronized (videoLock){
                        try {
                            videoLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }else{
                    if(!mIsInitialized){
                        continue;
                    }
                    MediaData mediaData = videoDataQueue.poll();
                    doEncode(videoCodec,mediaData.getData(),mediaData.getOffset(),mediaData.getLength());
                }

            }
        }
    };

    public void putAudioData(byte[]data,int offset,int length){
        MediaData mediaData = new MediaData(data,offset,length);
        synchronized (audioLock){
            try {
                audioDataQueue.put(mediaData);
                audioLock.notify();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void putVideoData(byte[]data,int offset,int length){
        MediaData mediaData = new MediaData(data,offset,length);
        synchronized (videoLock){
            try {
                videoDataQueue.put(mediaData);
                videoLock.notify();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void doEncode(MediaCodec mediaCodec,byte data[],int offset,int length){


        ByteBuffer inputBuffers[] = mediaCodec.getInputBuffers();
        ByteBuffer outputBuffers[] = mediaCodec.getOutputBuffers();

        int trackIndex = mediaCodec==videoCodec?videoTrackIndex:audioTrackIndex;
        int inIndex = mediaCodec.dequeueInputBuffer(0);
        if(inIndex>=0){
            ByteBuffer inputBuffer = inputBuffers[inIndex];
            inputBuffer.clear();
            inputBuffer.put(data);
            mediaCodec.queueInputBuffer(inIndex,offset,length,computePresentationTime(count),0);
            count++;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while(!Thread.interrupted()){
            int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,10000);
            if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){

                synchronized (this){
                    if(mediaCodec==videoCodec){
                        Log.e("androidLog","video INFO_OUTPUT_FORMAT_CHANGED");
                        videoTrackIndex = mediaMuxer.addTrack(mediaCodec.getOutputFormat());
                        trackIndex = videoTrackIndex;
                    }else if(mediaCodec==audioCodec){
                        Log.e("androidLog","audio INFO_OUTPUT_FORMAT_CHANGED");
                        audioTrackIndex = mediaMuxer.addTrack(mediaCodec.getOutputFormat());
                        trackIndex = audioTrackIndex;
                    }
                    if(videoTrackIndex!=-1&&audioTrackIndex!=-1){
                        mMuxerStarted = true;
                        mediaMuxer.start();
                        notifyAll();
                    }
                }

            }else if(outIndex==MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
                outputBuffers = mediaCodec.getOutputBuffers();
            }else if(outIndex==MediaCodec.INFO_TRY_AGAIN_LATER){
                break;
            }else if(outIndex<0){
                Log.w("androidLog","drainEncoder unexpected result: "+outIndex);
            }else{

                if((bufferInfo.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){
                    continue;
                }

                if(bufferInfo.size>=0){

                    ByteBuffer outputBuffer = outputBuffers[outIndex];

                    synchronized (this){
                        if(!mMuxerStarted){
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset+bufferInfo.size);
                    mediaMuxer.writeSampleData(trackIndex,outputBuffer,bufferInfo);
                }
                mediaCodec.releaseOutputBuffer(outIndex,false);
            }

        }
    }

    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / frameRate;
    }
}
