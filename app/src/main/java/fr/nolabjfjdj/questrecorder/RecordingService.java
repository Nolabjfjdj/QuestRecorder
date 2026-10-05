package fr.nolabjfjdj.questrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {
    public static final String ACTION_STOP = "fr.nolabjfjdj.questrecorder.STOP";
    private static final String CHANNEL_ID = "recording";
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int AUDIO_BITRATE = 128000;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private AudioRecord playbackRecord;
    private AudioRecord microphoneRecord;
    private ParcelFileDescriptor outputDescriptor;
    private MediaMuxer muxer;
    private Uri outputUri;
    private final Object muxerLock = new Object();
    private int videoTrack = -1;
    private int audioTrack = -1;
    private boolean muxerStarted;
    private volatile boolean recording;
    private Thread videoThread;
    private Thread audioThread;

    private int width = 1280;
    private int height = 720;
    private int fps = 30;
    private int bitrate = 10_000_000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();

        if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            return START_NOT_STICKY;
        }

        if (recording) {
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = getProjectionIntent(intent);
        if (data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        configure(
                intent.getIntExtra("resolution", 1),
                intent.getIntExtra("orientation", 0),
                intent.getIntExtra("fps", 0),
                intent.getIntExtra("quality", 1)
        );

        startForeground(1, buildNotification());

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopRecording();
            }
        }, null);

        try {
            startRecording();
        } catch (Exception e) {
            e.printStackTrace();
            stopRecording();
        }

        return START_NOT_STICKY;
    }

    private void configure(int resolution, int orientation, int fpsChoice, int quality) {
        if (resolution == 0) {
            width = 600;
            height = 1024;
        } else if (resolution == 1) {
            width = 1280;
            height = 720;
        } else {
            width = 1920;
            height = 1080;
        }

        if (orientation == 1) {
            int temp = width;
            width = height;
            height = temp;
        }

        fps = fpsChoice == 1 ? 60 : 30;

        if (quality == 0) {
            bitrate = fps == 60 ? 8_000_000 : 5_000_000;
        } else if (quality == 2) {
            bitrate = fps == 60 ? 18_000_000 : 14_000_000;
        } else {
            bitrate = fps == 60 ? 12_000_000 : 10_000_000;
        }
    }

    private Intent getProjectionIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra("data", Intent.class);
        }
        return intent.getParcelableExtra("data");
    }

    private void startRecording() throws Exception {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME,
                "QuestRecorder_" + timestamp + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/QuestRecorder");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        outputUri = getContentResolver().insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) {
            throw new IllegalStateException("Impossible de créer le fichier vidéo");
        }

        outputDescriptor = getContentResolver().openFileDescriptor(outputUri, "w");
        if (outputDescriptor == null) {
            throw new IllegalStateException("Impossible d'ouvrir le fichier vidéo");
        }

        muxer = new MediaMuxer(
                outputDescriptor.getFileDescriptor(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        prepareVideoEncoder();
        prepareAudioEncoder();
        prepareAudioRecords();

        virtualDisplay = projection.createVirtualDisplay(
                "QuestRecorder",
                width,
                height,
                getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                videoEncoder.createInputSurface(),
                null,
                null);

        recording = true;
        videoEncoder.start();
        audioEncoder.start();
        playbackRecord.startRecording();
        microphoneRecord.startRecording();

        videoThread = new Thread(this::videoLoop, "QuestRecorder-Video");
        audioThread = new Thread(this::audioLoop, "QuestRecorder-Audio");
        videoThread.start();
        audioThread.start();
    }

    private void prepareVideoEncoder() throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        videoEncoder = MediaCodec.createEncoderByType("video/avc");
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void prepareAudioEncoder() throws Exception {
        MediaFormat format = MediaFormat.createAudioFormat(
                "audio/mp4a-latm", SAMPLE_RATE, CHANNELS);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        audioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void prepareAudioRecords() {
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();

        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer * 2, 16384);

        AudioPlaybackCaptureConfiguration captureConfig =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

        playbackRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build();

        microphoneRecord = new AudioRecord.Builder()
                .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build();
    }

    private void videoLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean eosSent = false;

        while (recording || !eosSent) {
            if (!recording && !eosSent) {
                videoEncoder.signalEndOfInputStream();
                eosSent = true;
            }

            int index = videoEncoder.dequeueOutputBuffer(info, 1000);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (muxerLock) {
                    videoTrack = muxer.addTrack(videoEncoder.getOutputFormat());
                    startMuxerIfReady();
                }
            } else if (index >= 0) {
                ByteBuffer buffer = videoEncoder.getOutputBuffer(index);
                if (buffer != null && info.size > 0 && info.presentationTimeUs >= 0) {
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    writeSample(videoTrack, buffer, info);
                }
                videoEncoder.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    private void audioLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        short[] playback = new short[4096];
        short[] microphone = new short[4096];
        short[] mixed = new short[4096];
        byte[] pcm = new byte[mixed.length * 2];
        long startNs = System.nanoTime();

        while (recording) {
            int playbackCount = playbackRecord.read(
                    playback, 0, playback.length, AudioRecord.READ_BLOCKING);
            int microphoneCount = microphoneRecord.read(
                    microphone, 0, microphone.length, AudioRecord.READ_BLOCKING);
            int count = Math.max(playbackCount, microphoneCount);

            if (count <= 0) {
                continue;
            }

            for (int i = 0; i < count; i++) {
                int p = i < playbackCount ? playback[i] : 0;
                int m = i < microphoneCount ? microphone[i] : 0;
                mixed[i] = (short) ((p + m) / 2);
            }

            int byteCount = count * 2;
            for (int i = 0; i < count; i++) {
                pcm[i * 2] = (byte) (mixed[i] & 0xff);
                pcm[i * 2 + 1] = (byte) ((mixed[i] >> 8) & 0xff);
            }

            int offset = 0;
            while (offset < byteCount && recording) {
                int inputIndex = audioEncoder.dequeueInputBuffer(1000);
                if (inputIndex < 0) continue;

                ByteBuffer input = audioEncoder.getInputBuffer(inputIndex);
                if (input == null) continue;
                input.clear();
                int chunk = Math.min(input.remaining(), byteCount - offset);
                input.put(pcm, offset, chunk);
                long ptsUs = (System.nanoTime() - startNs) / 1000L;
                audioEncoder.queueInputBuffer(inputIndex, 0, chunk, ptsUs, 0);
                offset += chunk;
            }

            drainAudio(info);
        }

        try {
            int inputIndex = audioEncoder.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                long ptsUs = (System.nanoTime() - startNs) / 1000L;
                audioEncoder.queueInputBuffer(
                        inputIndex, 0, 0, ptsUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        } catch (Exception ignored) {
        }

        while (true) {
            if (!drainAudio(info)) break;
        }
    }

    private boolean drainAudio(MediaCodec.BufferInfo info) {
        int index = audioEncoder.dequeueOutputBuffer(info, 0);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            synchronized (muxerLock) {
                audioTrack = muxer.addTrack(audioEncoder.getOutputFormat());
                startMuxerIfReady();
            }
            return true;
        }

        if (index < 0) return true;

        ByteBuffer buffer = audioEncoder.getOutputBuffer(index);
        if (buffer != null && info.size > 0 && info.presentationTimeUs >= 0) {
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            writeSample(audioTrack, buffer, info);
        }
        audioEncoder.releaseOutputBuffer(index, false);
        return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0;
    }

    private void startMuxerIfReady() {
        if (!muxerStarted && videoTrack >= 0 && audioTrack >= 0) {
            muxer.start();
            muxerStarted = true;
            muxerLock.notifyAll();
        }
    }

    private void writeSample(int track, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        synchronized (muxerLock) {
            if (!muxerStarted || track < 0) return;
            muxer.writeSampleData(track, buffer, info);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 10, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("QuestRecorder")
                .setContentText("Vidéo + audio interne + micro")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Arrêter", stopPendingIntent)
                .build();
    }

    private void stopRecording() {
        synchronized (this) {
            if (stopping) return;
            if (!recording && projection == null && videoEncoder == null && audioEncoder == null) {
                stopSelf();
                return;
            }
            stopping = true;
        }

        recording = false;

        if (playbackRecord != null) {
            try { playbackRecord.stop(); } catch (Exception ignored) {}
            try { playbackRecord.release(); } catch (Exception ignored) {}
            playbackRecord = null;
        }
        if (microphoneRecord != null) {
            try { microphoneRecord.stop(); } catch (Exception ignored) {}
            try { microphoneRecord.release(); } catch (Exception ignored) {}
            microphoneRecord = null;
        }

        if (videoThread != null && videoThread != Thread.currentThread()) {
            try { videoThread.join(10000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (audioThread != null && audioThread != Thread.currentThread()) {
            try { audioThread.join(10000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        videoThread = null;
        audioThread = null;

        if (videoEncoder != null) {
            try { videoEncoder.stop(); } catch (Exception ignored) {}
            try { videoEncoder.release(); } catch (Exception ignored) {}
            videoEncoder = null;
        }
        if (audioEncoder != null) {
            try { audioEncoder.stop(); } catch (Exception ignored) {}
            try { audioEncoder.release(); } catch (Exception ignored) {}
            audioEncoder = null;
        }
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) {}
            projection = null;
        }

        synchronized (muxerLock) {
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop();
                } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
                muxer = null;
            }
            muxerStarted = false;
            videoTrack = -1;
            audioTrack = -1;
        }

        if (outputDescriptor != null) {
            try { outputDescriptor.close(); } catch (Exception ignored) {}
            outputDescriptor = null;
        }
        if (outputUri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(outputUri, values, null, null);
            outputUri = null;
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();

        synchronized (this) {
            stopping = false;
        }
    }
    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Enregistrement", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}