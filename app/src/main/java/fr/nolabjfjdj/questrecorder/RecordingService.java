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
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {
    public static final String ACTION_STOP = "fr.nolabjfjdj.questrecorder.STOP";
    private static final String CHANNEL_ID = "recording";

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder recorder;
    private ParcelFileDescriptor outputDescriptor;
    private Uri outputUri;
    private boolean recording;

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

        if (orientation == 0) {
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
        values.put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/QuestRecorder"
        );
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        outputUri = getContentResolver().insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
        );

        if (outputUri == null) {
            throw new IllegalStateException("Impossible de créer le fichier vidéo");
        }

        outputDescriptor = getContentResolver().openFileDescriptor(outputUri, "w");
        if (outputDescriptor == null) {
            throw new IllegalStateException("Impossible d'ouvrir le fichier vidéo");
        }

        recorder = new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setVideoSize(width, height);
        recorder.setVideoFrameRate(fps);
        recorder.setVideoEncodingBitRate(bitrate);
        recorder.setAudioEncodingBitRate(128_000);
        recorder.setOutputFile(outputDescriptor.getFileDescriptor());
        recorder.prepare();

        int density = getResources().getDisplayMetrics().densityDpi;

        virtualDisplay = projection.createVirtualDisplay(
                "QuestRecorder",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.getSurface(),
                null,
                null
        );

        recorder.start();
        recording = true;
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                10,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("QuestRecorder")
                .setContentText(
                        "Enregistrement " + width + "×" + height + " à " + fps + " FPS"
                )
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .addAction(
                        android.R.drawable.ic_media_pause,
                        "Arrêter",
                        stopPendingIntent
                )
                .build();
    }

    private void stopRecording() {
        if (!recording && recorder == null && projection == null) {
            stopSelf();
            return;
        }

        recording = false;

        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }

            try {
                recorder.reset();
            } catch (Exception ignored) {
            }

            recorder.release();
            recorder = null;
        }

        if (outputDescriptor != null) {
            try {
                outputDescriptor.close();
            } catch (Exception ignored) {
            }
            outputDescriptor = null;
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (projection != null) {
            projection.stop();
            projection = null;
        }

        if (outputUri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(outputUri, values, null, null);
            outputUri = null;
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
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
                CHANNEL_ID,
                "Enregistrement",
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
    }
}