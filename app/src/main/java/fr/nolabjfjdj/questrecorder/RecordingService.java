package fr.nolabjfjdj.questrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Environment;
import android.provider.Settings;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {
    public static final String ACTION_STOP = "fr.nolabjfjdj.questrecorder.STOP";
    private static final String CHANNEL_ID = "recording";

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder recorder;
    private File outputFile;
    private boolean recording;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FPS = 30;
    private static final int BITRATE = 10_000_000;

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

    private Intent getProjectionIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra("data", Intent.class);
        }
        return intent.getParcelableExtra("data");
    }

    private void startRecording() throws Exception {
        File movies = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (movies == null) {
            throw new IllegalStateException("Storage unavailable");
        }

        File folder = new File(movies, "QuestRecorder");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create output folder");
        }

        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());

        outputFile = new File(folder, "QuestRecorder_" + timestamp + ".mp4");

        recorder = new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(WIDTH, HEIGHT);
        recorder.setVideoFrameRate(FPS);
        recorder.setVideoEncodingBitRate(BITRATE);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        recorder.prepare();

        int density = getResources().getDisplayMetrics().densityDpi;

        virtualDisplay = projection.createVirtualDisplay(
                "QuestRecorder",
                WIDTH,
                HEIGHT,
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
                .setContentText("Enregistrement vidéo 1280×720")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Arrêter", stopPendingIntent)
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

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (projection != null) {
            projection.stop();
            projection = null;
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
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
