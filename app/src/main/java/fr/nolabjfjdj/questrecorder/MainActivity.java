package fr.nolabjfjdj.questrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 100;
    private static final int REQUEST_AUDIO = 101;

    private Spinner resolutionSpinner;
    private Spinner orientationSpinner;
    private Spinner fpsSpinner;
    private Spinner qualitySpinner;

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
        info.setText(
                "Enregistre la capture choisie dans Android.\n\n" +
                "Les vidéos sont enregistrées dans Movies/QuestRecorder.\n" +
                "L'audio interne et le micro sont enregistrés avec la vidéo.\n\n" +
                "Choisis tes réglages avant de démarrer."
        );
        info.setTextSize(16);
        info.setPadding(0, 24, 0, 24);

        resolutionSpinner = addOption(layout, "Résolution", new String[] {
                "600p",
                "720p",
                "1080p"
        });

        orientationSpinner = addOption(layout, "Orientation", new String[] {
                "Paysage",
                "Portrait"
        });

        fpsSpinner = addOption(layout, "Images par seconde", new String[] {
                "30 FPS",
                "60 FPS"
        });

        qualitySpinner = addOption(layout, "Qualité vidéo", new String[] {
                "Économique",
                "Standard",
                "Élevée"
        });

        Button record = new Button(this);
        record.setText("Démarrer l'enregistrement");
        record.setOnClickListener(v -> requestRecording());

        Button stop = new Button(this);
        stop.setText("Arrêter l'enregistrement");
        stop.setOnClickListener(v -> stopRecording());

        layout.addView(title);
        layout.addView(info);
        layout.addView(record);
        layout.addView(stop);

        setContentView(layout);
    }

    private Spinner addOption(LinearLayout layout, String label, String[] values) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(16);
        text.setPadding(0, 12, 0, 4);
        layout.addView(text);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                values
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        layout.addView(spinner);
        return spinner;
    }

    private void requestRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    REQUEST_AUDIO
            );
            return;
        }

        requestScreenCapture();
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CAPTURE
        );
    }

    private void stopRecording() {
        Intent service = new Intent(this, RecordingService.class);
        service.setAction(RecordingService.ACTION_STOP);
        startService(service);
    }

    private void startConfiguredRecording(int resultCode, Intent data) {
        Intent service = new Intent(this, RecordingService.class);
        service.putExtra("resultCode", resultCode);
        service.putExtra("data", data);
        service.putExtra("resolution", resolutionSpinner.getSelectedItemPosition());
        service.putExtra("orientation", orientationSpinner.getSelectedItemPosition());
        service.putExtra("fps", fpsSpinner.getSelectedItemPosition());
        service.putExtra("quality", qualitySpinner.getSelectedItemPosition());
        startForegroundService(service);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestScreenCapture();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_CAPTURE || resultCode != RESULT_OK || data == null) {
            return;
        }

        startConfiguredRecording(resultCode, data);
    }
}