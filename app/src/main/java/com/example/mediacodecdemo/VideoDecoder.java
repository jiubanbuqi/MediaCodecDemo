package com.example.mediacodecdemo;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoDecoder {

    private String TAG = "androidLog";
    private Surface surface;
    private String path;
    private Thread videoDecodeThread;
    private Thread audioDecodeThread;
    private AudioPlayer audioPlayer;

    public VideoDecoder(Surface surface,String path){
        this.surface = surface;
        this.path = path;
    }

    public void start(){

        videoDecodeThread = new Thread(videoRunnable);
        videoDecodeThread.start();

        audioDecodeThread = new Thread(audioRunnable);
        audioDecodeThread.start();
    }

    Runnable videoRunnable= new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            doDecode("video/");
        }
    };

    Runnable audioRunnable= new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            doDecode("audio/");
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void doDecode(String mediaType){
        MediaCodec mediaCodec = null;
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(int i=0;i<mediaExtractor.getTrackCount();i++){
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if(mime.startsWith(mediaType)){
                mediaExtractor.selectTrack(i);
                try {
                    mediaCodec = MediaCodec.createDecoderByType(mime);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(mediaType.startsWith("video/")){
                    mediaCodec.configure(mediaFormat,surface,null,0);
                }else if(mediaType.startsWith("audio/")){
                    mediaCodec.configure(mediaFormat,null,null,0);
                    int channel = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
                    audioPlayer = new AudioPlayer(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            channel,AudioFormat.ENCODING_PCM_16BIT);
                    audioPlayer.init();
                }
                break;
            }
        }
        mediaCodec.start();

        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        long startMs = System.currentTimeMillis();
        boolean bIsEos =false;

        while(!Thread.interrupted()){

            if(!bIsEos){
                int inIndex = mediaCodec.dequeueInputBuffer(0);
                if(inIndex>=0){
                    ByteBuffer inputBuffer = inputBuffers[inIndex];
                    int sampleSize = mediaExtractor.readSampleData(inputBuffer,0);
                    Log.e(TAG,"sampleSize="+sampleSize);
                    if(sampleSize<0){
                        mediaCodec.queueInputBuffer(inIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        bIsEos = true;
                        Log.d(TAG,"读取结束");
                    }else{
                        mediaCodec.queueInputBuffer(inIndex,0,sampleSize,mediaExtractor.getSampleTime(),0);
                        mediaExtractor.advance();
                    }
                }
            }



            int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,10000);

            switch (outIndex){
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG,"New format "+mediaCodec.getOutputFormat());
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG,"INFO_OUTPUT_BUFFERS_CHANGED");
                    outputBuffers = mediaCodec.getOutputBuffers();
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG,"dequeueOutputBuffer timed out!");
                    break;
                default:
                    ByteBuffer outputBuffer = outputBuffers[outIndex];
                    Log.e("androidLog","bufferInfo.presentationTimeUs="+bufferInfo.presentationTimeUs / 1000);
                    while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                        SystemClock.sleep(10);
                    }
                    if (bufferInfo.size != 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    }
                    if(mediaType.startsWith("audio/")){
                        byte[]outData=new byte[bufferInfo.size];
                        outputBuffer.get(outData);
                        outputBuffer.clear();
                        audioPlayer.play(outData,0,bufferInfo.size);
                    }
                    mediaCodec.releaseOutputBuffer(outIndex,true);
                    break;
            }


            if((bufferInfo.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){
                Log.d(TAG,"BUFFER_FLAG_END_OF_STREAM");
                break;
            }

        }

    }
}
