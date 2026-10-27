package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/**
 * Silent native MP3 decoding for the qualification-only file import.
 * PCM is counted in memory and never connected to an output or captured from
 * Prime. Unknown PCM formats and native duration differences refuse the package.
 * This adapter is compile-checked here; Android execution remains unqualified.
 * CLOUD BRIDGE 003-D reuses this exact inspector from the main source set for
 * private cache validation; source-set availability is not MEDIA admission.
 */
final class AndroidStudioAudioInspector implements StudioMultimediaPackage.Decoder {
    private final Context context;

    /** Keep only the application-private temporary-file owner. */
    AndroidStudioAudioInspector(Context context) { this.context=context.getApplicationContext(); }

    /** Count actually decoded PCM frames with bounded input bytes and native execution time. */
    @Override public long decodedDurationMs(byte[] bytes) throws Exception {
        File file=File.createTempFile("studio-mp3-",".mp3",context.getCacheDir());
        MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
        try {
            Files.write(file.toPath(),bytes);extractor.setDataSource(file.getAbsolutePath());
            if(extractor.getTrackCount()!=1) throw new IOException("NATIVE_AUDIO_TRACK_COUNT_UNSUPPORTED");
            MediaFormat format=extractor.getTrackFormat(0);
            if(!"audio/mpeg".equals(format.getString(MediaFormat.KEY_MIME))) throw new IOException("NATIVE_MP3_REQUIRED");
            extractor.selectTrack(0);decoder=MediaCodec.createDecoderByType("audio/mpeg");decoder.configure(format,null,null,0);decoder.start();
            int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),sampleBytes=2;
            boolean inputEnded=false,outputEnded=false;long decodedBytes=0,deadline=System.nanoTime()+15_000_000_000L;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!outputEnded) {
                if(System.nanoTime()>deadline) throw new IOException("NATIVE_MP3_DECODE_TIMEOUT");
                if(!inputEnded) {
                    int index=decoder.dequeueInputBuffer(10_000);
                    if(index>=0) {
                        ByteBuffer input=decoder.getInputBuffer(index);
                        if(input==null) throw new IOException("NATIVE_MP3_INPUT_UNAVAILABLE");
                        int count=extractor.readSampleData(input,0);
                        if(count<0) { decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true; }
                        else { decoder.queueInputBuffer(index,0,count,Math.max(0,extractor.getSampleTime()),0);extractor.advance(); }
                    }
                }
                int index=decoder.dequeueOutputBuffer(info,10_000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output=decoder.getOutputFormat();
                    channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int encoding=output.containsKey(MediaFormat.KEY_PCM_ENCODING)?output.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                    if(encoding==AudioFormat.ENCODING_PCM_FLOAT) sampleBytes=4;
                    else if(encoding!=AudioFormat.ENCODING_PCM_16BIT) throw new IOException("NATIVE_PCM_ENCODING_UNSUPPORTED");
                } else if(index>=0) {
                    decodedBytes+=info.size;
                    if(decodedBytes>64L*1024*1024) throw new IOException("NATIVE_PCM_BUDGET_EXCEEDED");
                    outputEnded=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;decoder.releaseOutputBuffer(index,false);
                }
            }
            if(decodedBytes<1 || channels<1 || rate<1 || decodedBytes%(channels*sampleBytes)!=0) throw new IOException("NATIVE_PCM_FACTS_INVALID");
            return Math.round(decodedBytes/(double)(channels*sampleBytes)/rate*1000d);
        } finally {
            if(decoder!=null) { try { decoder.stop(); } catch(RuntimeException ignored) { /* Release still follows. */ } decoder.release(); }
            extractor.release();Files.deleteIfExists(file.toPath());
        }
    }
}
