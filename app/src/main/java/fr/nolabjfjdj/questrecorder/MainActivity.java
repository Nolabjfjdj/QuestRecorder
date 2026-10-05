package fr.nolabjfjdj.questrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 100;
    private static final int REQUEST_MIC = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("QuestRecorder");
        title.setTextSize(28);

        TextView info = new TextView(this);
        info.setText("Capture une seule application avec MediaProjection.\n\nChoisis PojavLauncher dans le sélecteur Android.");
        info.setTextSize(16);
        info.setPadding(0, 24, 0, 32);

        Button record = new Button(this);
        record.setText("Choisir l'application à enregistrer");
        record.setOnClickListener(v -> requestRecording());

        layout.addView(title);
        layout.addView(info);
        layout.addView(record);
        setContentView(layout);
    }

    private void requestRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_CAPTURE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Intent service = new Intent(this, RecordingService.class);
        service.putExtra("resultCode", resultCode);
        service.putExtra("data", data);
        startForegroundService(service);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_MIC
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestRecording();
        }
    }
}
