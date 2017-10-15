package com.qt.mediccodecdemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.R.attr.format;
import static android.media.AudioTrack.WRITE_BLOCKING;

public class VideoPlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/1q2.mp4";
    private PlayerThread mPlayer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        setContentView(sv);
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("1", Thread.currentThread().getStackTrace()[2].getMethodName()); //函数名
        if (mPlayer == null) {
            mPlayer = new PlayerThread(holder.getSurface());
            mPlayer.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPlayer != null) {
            mPlayer.interrupt();
        }
        Log.d("2", Thread.currentThread().getStackTrace()[2].getMethodName()); //函数名
    }

    private class PlayerThread extends Thread {

        private Surface surface;

        public MediaExtractor extractor;
        private MediaCodec videoDecoder;
        private MediaCodec audioDecoder;
        MediaFormat audioFormat;
        MediaFormat videoFormat;
        private Object sync=new Object();

        private int videoTrackIndex;
        private  int audioTrackIndex;
        public PlayerThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            extractor = new MediaExtractor();
            try {
                extractor.setDataSource(SAMPLE);
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    videoFormat=format;

                    videoTrackIndex=i;
                    extractor.selectTrack(i);
                    try {
                        videoDecoder = MediaCodec.createDecoderByType(mime);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    videoDecoder.configure(format, surface, null, 0);
                    continue;
                }
                if(mime.startsWith("audio/")){
                    audioFormat=format;
                    audioTrackIndex=i;
                    extractor.selectTrack(i);
                    try {
                        audioDecoder=MediaCodec.createDecoderByType(mime);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    audioDecoder.configure(format,null,null,0);
                    continue;
                }
            }

            VideoThread videoThread = new VideoThread();
            videoThread.start();
            AudioThread audioThread =new AudioThread();
            audioThread.start();


        }

        private class VideoThread extends Thread {


            public VideoThread() {

            }

            @Override
            public void run() {
                super.run();


                if (videoDecoder == null) {
                    Log.e("DecodeActivity", "Can't find video info!");
                    return;
                }

                videoDecoder.start();

                ByteBuffer[] inputBuffers = videoDecoder.getInputBuffers();
                ByteBuffer[] outputBuffers = videoDecoder.getOutputBuffers();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean isEOS = false;
                long startMs = System.currentTimeMillis();

                while (!Thread.interrupted()) {
                    if (!isEOS) {
                        int inIndex = videoDecoder.dequeueInputBuffer(10000);
                        if (inIndex >= 0) {
                            //System.out.println("index===>"+inIndex);
                                ByteBuffer buffer = inputBuffers[inIndex];

                                int trackIndex = extractor.getSampleTrackIndex();


                                if(trackIndex!=videoTrackIndex){

                                    synchronized (sync){
                                        try {
                                            sync.notifyAll();
                                            sync.wait();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            System.out.println("index V===>"+trackIndex);
                                int sampleSize = extractor.readSampleData(buffer, 0);

                                if (sampleSize < 0) {
                                    Log.d("DecodeActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                                    videoDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    isEOS = true;
                                } else {
                                    videoDecoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);

                                    extractor.advance();
                                }
                            }

                        try {
                            sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    }

                    int outIndex = videoDecoder.dequeueOutputBuffer(info, 10000);
                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                            outputBuffers = videoDecoder.getOutputBuffers();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.d("DecodeActivity", "New format " + videoDecoder.getOutputFormat());
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                            break;
                        default:
                            ByteBuffer buffer = outputBuffers[outIndex];
                            Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + buffer);
                            // We use a very simple clock to keep the video FPS, or the video
                            // playback will be too fast
                            while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                                try {
                                    sleep(20);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                    break;
                                }
                            }
                            videoDecoder.releaseOutputBuffer(outIndex, true);

                            break;
                    }

                    // All decoded frames have been rendered, we can stop playing now
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                        break;
                    }
                }

                videoDecoder.stop();
                videoDecoder.release();
                extractor.release();
            }
        }

        private class AudioThread extends Thread {
            @Override
            public void run() {
                int channelCount=audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

                int mAudioSampleRate=audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                final int min_buf_size = AudioTrack.getMinBufferSize(mAudioSampleRate,
                        (channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                        AudioFormat.ENCODING_PCM_16BIT);
                final int max_input_size = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                int mAudioInputBufSize =  min_buf_size > 0 ? min_buf_size * 4 : max_input_size;

                AudioTrack audioTrack=new AudioTrack(AudioManager.STREAM_MUSIC,
                        mAudioSampleRate,
                        channelCount==1? AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        mAudioInputBufSize,
                        AudioTrack.MODE_STREAM);
                try {
                    audioTrack.play();
                }catch (RuntimeException e){
                    e.printStackTrace();

                    audioTrack.release();
                }

                audioDecoder.start();
                ByteBuffer []inputBuffers=audioDecoder.getInputBuffers();
                ByteBuffer []outputBuffers=audioDecoder.getOutputBuffers();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean isEOS = false;
                long startMs = System.currentTimeMillis();

                while (!Thread.interrupted()) {
                    if (!isEOS) {

                        int inIndex = audioDecoder.dequeueInputBuffer(10000);
                        if (inIndex >= 0) {
                            //System.out.println("index===>"+inIndex);
                            ByteBuffer buffer = inputBuffers[inIndex];
                            int trackIndex = extractor.getSampleTrackIndex();
                            if(trackIndex!=audioTrackIndex){

                                synchronized (sync){
                                    try {
                                        sync.notifyAll();
                                        sync.wait();

                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            System.out.println("index A===>"+extractor.getSampleTrackIndex());
                                int sampleSize = extractor.readSampleData(buffer, 0);
                                if (sampleSize < 0) {
                                    Log.d("DecodeActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                                    audioDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    isEOS = true;
                                } else {
                                    audioDecoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);

                                    extractor.advance();
                                }
                            }
                        }

                    int outIndex = audioDecoder.dequeueOutputBuffer(info, 10000);
                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                            outputBuffers = audioDecoder.getOutputBuffers();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.d("DecodeActivity", "New format " + audioDecoder.getOutputFormat());
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                            break;
                        default:

                            ByteBuffer buffer = outputBuffers[outIndex];
                            Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + buffer);
                            // We use a very simple clock to keep the video FPS, or the video
                            // playback will be too fast
                            while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                                try {
                                    sleep(20);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                    break;
                                }
                            }
                            int realBufferSize=info.size;
                            final byte[] chunk = new byte[realBufferSize];
                            buffer.get(chunk);
                            buffer.clear();
                            audioTrack.write(chunk,0,realBufferSize);

                            audioDecoder.releaseOutputBuffer(outIndex, false);
                            break;
                    }

                    // All decoded frames have been rendered, we can stop playing now
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                        break;
                    }
                }

                audioDecoder.stop();
                audioDecoder.release();
            }
        }
    }
}
