package com.qt.mediccodecdemo;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;

public class VideoJointActivity extends AppCompatActivity {
    private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/1q2.mp4";
    private static final String OutVideo = Environment.getExternalStorageDirectory() + "/outVideo.mp4";
    private MediaMuxer mediaMuxer;
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private MediaExtractor mediaExtractor;
    private int intputFileIndex=0;
    private MediaFormat videoFormat;
    private int videoTrackIndex;
    private int audioTrackIndex;
    private MediaFormat audioFormat;
    private ArrayList<String>inputFiles;
    private boolean isEndOfThisFileStream;
    private boolean isEndOfTranscode;
    private static final int ResultOK = 1;
    private static final int ResultFail = -10;
    private MediaCodec videoDecoder;
    private MediaCodec audioDecoder;
    private Surface encoderInputSurface;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_joint);
    }
    public void onClick(View view){
        initInput();
        setOutMediaFormat();
        createEncoderAndMuxer();
        transcode();
    }

    private void transcode() {
        isEndOfTranscode=false;
        isEndOfThisFileStream=false;
        while (!isEndOfTranscode){
            stepDecoder();
        }
    }


    private void stepDecoder() {
        if(mediaExtractor==null||isEndOfThisFileStream){
            createExtractorAndDecoder();
        }
        Thread videoThread=new Thread(new Runnable() {
            @Override
            public void run() {
                int index=0;
                boolean isEosVideo=false;

                while(!Thread.interrupted()){
                    if(!isEosVideo) {
                        Log.e("a","run");
                        index=videoDecoder.dequeueInputBuffer(1000);
                        ByteBuffer buffer = videoDecoder.getInputBuffers()[index];
                        int sampleSize = mediaExtractor.readSampleData(buffer, 0);
                        if (sampleSize > 0) {
                            videoDecoder.queueInputBuffer(index, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                        } else {
                            videoDecoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEosVideo = true;
                        }
                        Log.e("c","run");
                    }

                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outIndex=0;
                outIndex=videoDecoder.dequeueOutputBuffer(info,1000);
                if(outIndex==MediaCodec.INFO_TRY_AGAIN_LATER){

                }else if(outIndex==MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){

                }else if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){

                }else {
                    encodeVideo();
                    videoDecoder.releaseOutputBuffer(outIndex, true);
                    //调用Endcoder;
                    mediaExtractor.advance();
                    Log.e("c","run");
                }

            }
        });
        videoThread.start();
        final Thread audioThread= new Thread(new Runnable() {
            @Override
            public void run() {
                int index=0;
                boolean isEosAudio=false;
                while (!Thread.interrupted()){
                    if(!isEosAudio){
                        Log.e("a","run");
                        index=audioDecoder.dequeueInputBuffer(1000);
                        ByteBuffer buffer = audioDecoder.getInputBuffers()[index];
                        int sampleSize=mediaExtractor.readSampleData(buffer,0);
                        if(sampleSize>0){
                            audioDecoder.queueInputBuffer(index,0,sampleSize,mediaExtractor.getSampleTime(),0);
                        }else {
                            audioDecoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEosAudio=true;
                        }
                    }
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    int outIndex=0;
                    ByteBuffer audioDecodeOutBuffer;
                    outIndex=videoDecoder.dequeueOutputBuffer(info,1000);
                    int bufferLenght=videoDecoder.getOutputBuffers().length;
                    audioDecodeOutBuffer=videoDecoder.getOutputBuffers()[outIndex];
                    if(outIndex==MediaCodec.INFO_TRY_AGAIN_LATER){

                    }else if(outIndex==MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){

                    }else if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){

                    }else {
                        //调用audioEncoder

                        encodeAudio(audioDecodeOutBuffer,info);
                        audioDecoder.releaseOutputBuffer(outIndex, true);
                        mediaExtractor.advance();
                        //调用Endcoder;
                    }
                }
            }

        });
        audioThread.start();

    }

    private void encodeVideo() {

        while (true){
            int videoFormatTrackIndex=0;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            int videoEncoderOutPutBufferIndex=videoEncoder.dequeueOutputBuffer(info,1000);
            if(videoEncoderOutPutBufferIndex>=0){
                mediaMuxer.writeSampleData(videoFormatTrackIndex,videoEncoder.getOutputBuffers()[videoEncoderOutPutBufferIndex],info);
            }else {
                if(videoEncoderOutPutBufferIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat newFormat=videoEncoder.getOutputFormat();
                    videoFormatTrackIndex=mediaMuxer.addTrack(newFormat);
                }else if(videoEncoderOutPutBufferIndex==MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){

                }else if(videoEncoderOutPutBufferIndex==MediaCodec.INFO_TRY_AGAIN_LATER){

                }

            }
        }

    }

    private void encodeAudio(ByteBuffer audioDecodeOutBuffer, MediaCodec.BufferInfo info) {
        while (true) {
            int encodeInputIndex = audioEncoder.dequeueInputBuffer(1000);

            if (encodeInputIndex >= 0) {
                if(audioDecodeOutBuffer.position()>0) {
                    ByteBuffer encodeInPutBuffer = audioEncoder.getInputBuffers()[encodeInputIndex];
                    encodeInPutBuffer.put(audioDecodeOutBuffer);
                    audioEncoder.queueInputBuffer(encodeInputIndex, 0, encodeInPutBuffer.position(), info.presentationTimeUs, 0);
                    //开启音频Mux
                    audioMux();
                }else {
                    audioEncoder.queueInputBuffer(encodeInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
                break;
            } else {
                //没有得到编码器输入缓冲就继续尝试
            }
        }
    }

    private void audioMux() {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        int mMuxerAudioTrackIndex=0;
        while (true){
            int audioEncodeOutBufferIndex=audioEncoder.dequeueOutputBuffer(info,1000);
            if(audioEncodeOutBufferIndex>=0){
                ByteBuffer outBuffer=audioEncoder.getOutputBuffers()[audioEncodeOutBufferIndex];
                outBuffer.position(info.offset);
                outBuffer.limit(info.offset+info.size);
                mediaMuxer.writeSampleData(mMuxerAudioTrackIndex,outBuffer,info);
            }else {
                if(audioEncodeOutBufferIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){

                    MediaFormat newFormat=audioEncoder.getOutputFormat();
                    mMuxerAudioTrackIndex=mediaMuxer.addTrack(newFormat);

                }else if(audioEncodeOutBufferIndex==MediaCodec.INFO_TRY_AGAIN_LATER){

                }else if(audioEncodeOutBufferIndex==MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){

                }
            }
        }
    }

    private void createExtractorAndDecoder() {
        if(mediaExtractor!=null){
            mediaExtractor.release();
            videoDecoder.stop();
            videoDecoder.release();
            audioDecoder.stop();
            audioDecoder.release();
        }
        isEndOfThisFileStream=false;

        mediaExtractor=new MediaExtractor();
        String path=inputFiles.get(intputFileIndex);
        intputFileIndex++;
        try {
            mediaExtractor.setDataSource(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int trackCount=mediaExtractor.getTrackCount();
        for(int i=0;i<trackCount;i++){
            MediaFormat format=mediaExtractor.getTrackFormat(i);
            if(format.getString(MediaFormat.KEY_MIME).startsWith("video/")){
                videoFormat=format;
                videoTrackIndex=i;
                mediaExtractor.selectTrack(i);
            }
            if(format.getString(MediaFormat.KEY_MIME).startsWith("audio/")){
                audioFormat=format;
                audioTrackIndex=i;
                mediaExtractor.selectTrack(i);
            }
        }
        try {
            videoDecoder=MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
            videoDecoder.configure(videoFormat,encoderInputSurface,null,0);
            videoDecoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            audioDecoder=MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
            audioDecoder.configure(audioFormat,null,null,0);
            audioDecoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initInput() {
        inputFiles=new ArrayList<String>();
        inputFiles.add(SAMPLE);
        inputFiles.add(SAMPLE);
    }

    private void setOutMediaFormat() {
        mediaExtractor=new MediaExtractor();
        try {
            mediaExtractor.setDataSource(SAMPLE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int trackCount=mediaExtractor.getTrackCount();
        for(int i=0;i<trackCount;i++){
          MediaFormat format=mediaExtractor.getTrackFormat(i);
            if(format.getString(MediaFormat.KEY_MIME).startsWith("video/")){
                videoFormat=format;
                videoTrackIndex=i;
            }
            if(format.getString(MediaFormat.KEY_MIME).startsWith("audio/")){
                audioFormat=format;
                audioTrackIndex=i;
            }
        }
        mediaExtractor.release();
        mediaExtractor=null;

    }

    private void createEncoderAndMuxer() {
        try {
            mediaMuxer=new MediaMuxer(OutVideo,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoEncoder= MediaCodec.createEncoderByType("video/avc");
            encoderInputSurface=videoEncoder.createInputSurface();
            videoEncoder.configure(videoFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
            audioEncoder.configure(audioFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 

}
