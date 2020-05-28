package com.example.mediacodecdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;

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
    private MediaCodec videoCodec;
    private MediaCodec audioCodec;
    private LinkedBlockingQueue<MediaData> videoDataQueue = new LinkedBlockingQueue(500);
    private LinkedBlockingQueue<MediaData> audioDataQueue = new LinkedBlockingQueue(500);
    private Object videoLock = new Object();
    private Object audioLock = new Object();
    private byte[] videoConfigByte;
    private byte[] audioConfigByte;
    private String path= Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"test.mp4";
    private volatile FileOutputStream fileOutputStream;
    private Thread videoEncodeThread;
    private Thread audioEncodeThread;

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
        videoEncodeThread = new Thread(encodeVideoRunnable);
        videoEncodeThread.start();

        audioEncodeThread = new Thread(encodeAudioRunnable);
        audioEncodeThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void stop(){
        if(videoEncodeThread !=null){
            videoEncodeThread.interrupt();
        }
        if(videoCodec !=null){
            videoCodec.stop();
            videoCodec.release();
        }
        try {
            if(fileOutputStream!=null){
                fileOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    Runnable encodeVideoRunnable = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
//            doVideoEncode();
        }
    };

    Runnable encodeAudioRunnable = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            doAudioEncode();
        }
    };

    public void putVideoData(byte[]data, int offset, int length){
        MediaData mediaData = new MediaData(data,offset,length);
//        synchronized (videoLock){
//            try {
//                videoDataQueue.put(mediaData);
//                videoLock.notify();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
    }

    public void putAudioData(byte[]data, int offset, int length){
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

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void doVideoEncode(){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,width*height*5);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,30);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        try {
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        videoCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoCodec.start();

        long count=0;
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
                MediaData mediaData = videoDataQueue.poll();
                long startMs = System.currentTimeMillis();
                ByteBuffer[] inputBuffers = videoCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = videoCodec.getOutputBuffers();
                int inIndex = videoCodec.dequeueInputBuffer(0);
                if(inIndex>=0){
                    ByteBuffer inputBuffer = inputBuffers[inIndex];
                    inputBuffer.put(mediaData.getData());
                    videoCodec.queueInputBuffer(inIndex,0,mediaData.getLength(),computePresentationTime(count),0);
                    count++;
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outIndex = videoCodec.dequeueOutputBuffer(bufferInfo,10000);
                while (outIndex>=0){
                    try {
                        ByteBuffer outputBuffer = outputBuffers[outIndex];
                        byte[] outData=new byte[bufferInfo.size];
                        outputBuffer.get(outData);
                        if(bufferInfo.flags==MediaCodec.BUFFER_FLAG_CODEC_CONFIG){
                            videoConfigByte = outData;
                        }else if(bufferInfo.flags==MediaCodec.BUFFER_FLAG_KEY_FRAME){
                            byte[] keyframe = new byte[bufferInfo.size+ videoConfigByte.length];
                            System.arraycopy(videoConfigByte,0,keyframe,0, videoConfigByte.length);
                            System.arraycopy(outData,0,keyframe, videoConfigByte.length,outData.length);
                            synchronized (fileOutputStream){
                                fileOutputStream.write(keyframe,0,keyframe.length);
                            }
                        }else{
                            synchronized (fileOutputStream){
                                fileOutputStream.write(outData,0,outData.length);
                            }
                        }

                        videoCodec.releaseOutputBuffer(outIndex,false);
                        outIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    }catch (IOException e){
                        e.printStackTrace();
                    }

                }

            }

        }
    }



    private void doAudioEncode(){
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,44100,2);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,128*1000);
        try {
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        audioCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();

        long count=0;
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
                MediaData mediaData = audioDataQueue.poll();
                long startMs = System.currentTimeMillis();
                ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();
                int inIndex = audioCodec.dequeueInputBuffer(0);
                if(inIndex>=0){
                    ByteBuffer inputBuffer = inputBuffers[inIndex];
                    inputBuffer.put(mediaData.getData());
                    audioCodec.queueInputBuffer(inIndex,0,mediaData.getLength(),computePresentationTime(count),0);
                    count++;
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outIndex = audioCodec.dequeueOutputBuffer(bufferInfo,10000);
                while (outIndex>=0){
                    try {

                        int outBitSize = bufferInfo.size;
                        int outPacketSize = outBitSize+7;
                        ByteBuffer outputBuffer = outputBuffers[outIndex];
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset+outBitSize);
//                        byte[] outData=new byte[bufferInfo.size];
//                        outputBuffer.get(outData);
                        byte audioData[] = new byte[outPacketSize];
                        addADTStoPacket(audioData,outPacketSize);
                        outputBuffer.get(audioData,7, outBitSize);
                        outputBuffer.position(bufferInfo.offset);
                        synchronized (fileOutputStream){
                            fileOutputStream.write(audioData,0,audioData.length);
                        }

                        audioCodec.releaseOutputBuffer(outIndex,false);
                        outIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    }catch (IOException e){
                        e.printStackTrace();
                    }

                }

            }


        }
    }


    /**
     59      * 添加ADTS头
     60      * @param packet
     61      * @param packetLen
     62      */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 2; // CPE


        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }


    long mVStartTime;
    long mVLastPts;
    public long recordImage(byte[] image) throws Exception {
        long pts;
        if (mVStartTime == -1) {
            mVStartTime = System.nanoTime();
            pts = 0;
        } else {
            pts = (System.nanoTime() - mVStartTime) / 1000;
        }
        if (pts <= mVLastPts) {
            pts += (mVLastPts - pts) + 1000;
        }
        mVLastPts = pts;
        return pts;
    }

    private long computePresentationTime(long frameIndex) {

        long pts;
        if (mVStartTime == -1) {
            mVStartTime = System.nanoTime();
            pts = 0;
        } else {
            pts = (System.nanoTime() - mVStartTime) / 1000;
        }
        if (pts <= mVLastPts) {
            pts += (mVLastPts - pts) + 1000;
        }
        mVLastPts = pts;
        return  pts;
//        return 132 + frameIndex * 1000000 / frameRate;
    }

}
