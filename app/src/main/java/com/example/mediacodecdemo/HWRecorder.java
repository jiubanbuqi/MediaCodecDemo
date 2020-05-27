package com.example.mediacodecdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class HWRecorder {

    public static final int MEDIA_TYPE_VIDEO = 1;
    public static final int MEDIA_TYPE_AUDIO = 2;
    public static final int MEDIA_TYPE_UNKNOWN = 0;

    public static final String MIME_TYPE_AVC = "video/avc";
    public static final String MIME_TYPE_AAC = "audio/mp4a-latm";
    private MediaCodec videoCodec;
    private MediaCodec audioCodec;
    private MediaMuxer mediaMuxer;
    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec.BufferInfo audioBufferInfo;
    private int videoTrackIndex;
    private int audioTrackIndex;
    private volatile boolean mMuxerStarted;
    private long mVStartTime;
    private long mAStartTime;
    private long mVLastPts;
    private long mALastPts;



    private static MediaCodecInfo getCodeInfo(String mimeType){
        final int numCodecs = MediaCodecList.getCodecCount();
        for(int i=0;i<numCodecs;i++){
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if(!codecInfo.isEncoder()){
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for(String type:types){
                if(type.equalsIgnoreCase(mimeType)){
                    return codecInfo;
                }
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void init(int width, int height, int colorFormat, int bitRate, int sampleRate, int channels, String dstFilePath){

        if(getCodeInfo(MIME_TYPE_AVC)==null||getCodeInfo(MIME_TYPE_AAC)==null){
            Log.e("androidLog","cannot find suitable codec");
        }

        MediaFormat videoFormat = MediaFormat.createVideoFormat(MIME_TYPE_AVC,width,height);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE,bitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE,sampleRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,5);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,colorFormat);

        try {
            videoCodec = MediaCodec.createEncoderByType(MIME_TYPE_AVC);
            videoCodec.configure(videoFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }


        MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE_AAC,sampleRate,channels);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE,128*1000);
        try {
            audioCodec = MediaCodec.createEncoderByType(MIME_TYPE_AAC);
            audioCodec.configure(audioFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File file = new File(dstFilePath);
        if (file.exists() && !file.delete()) {
            Log.w("androidLog", "delete file failed");
        }

        try {
            mediaMuxer = new MediaMuxer(dstFilePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMuxerStarted = false;
        audioTrackIndex=-1;
        videoTrackIndex=-1;
        mVLastPts=-1;
        mALastPts=-1;
        mVStartTime=-1;
        mAStartTime=-1;
        audioBufferInfo = new MediaCodec.BufferInfo();
        videoBufferInfo = new MediaCodec.BufferInfo();

        Log.e("androidLog","初始化完毕");
    }

    @SuppressWarnings("WeakerAccess")
    public void recordImage(byte[] image) throws Exception {
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
        Log.e("androidLog","录制视频");
        doRecord(videoCodec, videoBufferInfo, image, pts);
    }

    @SuppressWarnings("WeakerAccess")
    public void recordSample(byte[] sample) throws Exception {
        long pts;
        if (mAStartTime == -1) {
            mAStartTime = System.nanoTime();
            pts = 0;
        } else {
            pts = (System.nanoTime() - mAStartTime) / 1000;
        }
        if (pts <= mALastPts) {
            pts += (mALastPts - pts) + 1000;
        }
        mALastPts = pts;
        Log.e("androidLog","录制音频");
        doRecord(audioCodec, audioBufferInfo, sample, pts);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void doRecord(MediaCodec mediaCodec, MediaCodec.BufferInfo bufferInfo, byte[]data, long pts){

        int inIndex = mediaCodec.dequeueInputBuffer(0);
        ByteBuffer []inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer []outputBuffers = mediaCodec.getOutputBuffers();
        if(inIndex>=0){
            ByteBuffer inputBuffer = inputBuffers[inIndex];
            inputBuffer.clear();
            inputBuffer.put(data);
            mediaCodec.queueInputBuffer(inIndex,0,data.length,pts,0);
        }

        int trackIndex = mediaCodec==videoCodec?videoTrackIndex:audioTrackIndex;
        while (!Thread.interrupted()){
            int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,10000);
            if(outIndex==MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
                outputBuffers = mediaCodec.getOutputBuffers();
            }else if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                trackIndex = addTrackIndex(mediaCodec);
            }else if(outIndex==MediaCodec.INFO_TRY_AGAIN_LATER){
                break;
            }else if(inIndex<0){
                Log.w("androidLog","drainEncode unexpeted result:"+inIndex);
            }else{
                if((bufferInfo.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){
                    continue;
                }

                if(bufferInfo.size!=0){
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

    private int addTrackIndex(MediaCodec mediaCodec){

        int trackIndex;
        synchronized (this){
            MediaFormat format = mediaCodec.getOutputFormat();
            if(getMediaType(format)==MEDIA_TYPE_VIDEO){
                videoTrackIndex = mediaMuxer.addTrack(format);
                trackIndex = videoTrackIndex;
            }else{
                audioTrackIndex = mediaMuxer.addTrack(format);
                trackIndex = audioTrackIndex;
            }

            if(videoTrackIndex!=-1&&audioTrackIndex!=-1){
                mMuxerStarted = true;
                mediaMuxer.start();
                notifyAll();
            }
        }
        return trackIndex;
    }


    public static int getMediaType(MediaFormat format) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime.startsWith("video/")) {
            return MEDIA_TYPE_VIDEO;
        } else if (mime.startsWith("audio/")) {
            return MEDIA_TYPE_AUDIO;
        }
        return MEDIA_TYPE_UNKNOWN;
    }



    public void stop() {
        try {
            release();
        } catch (Exception e) {
            Log.e("androidLog", "stop exception occur: " + e.getLocalizedMessage());
        }
    }

    private void release() throws Exception {
        if (videoCodec != null) {
            videoCodec.stop();
            videoCodec.release();
            videoCodec = null;
        }

        if (videoCodec != null) {
            videoCodec.stop();
            videoCodec.release();
            videoCodec = null;
        }

        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }
    }




}
