package com.deutschich.superbackup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_TARGET_DIRECTORY = 1;
    private static final int REQUEST_CODE_PICK_SOURCE_FOLDER = 2;
    private Uri targetDirectoryUri = null; // Zielverzeichnis (wo das Backup gespeichert wird)
    private Uri sourceFolderUri = null;    // Quellordner (der gesichert werden soll)
    private boolean isBackupOperation = true; // true: Backup, false: Restore (für Wiederherstellung)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Button, um das Zielverzeichnis auszuwählen
        Button selectTargetDirButton = findViewById(R.id.select_target_directory_button);
        selectTargetDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTargetDirectoryPicker();
            }
        });

        // Button, um den Quellordner auszuwählen (für Backup oder Wiederherstellung)
        Button selectSourceFolderButton = findViewById(R.id.select_source_folder_button);
        selectSourceFolderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSourceFolderPicker();
            }
        });

        // Button, um Backup zu starten
        Button backupButton = findViewById(R.id.backup_button);
        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isBackupOperation = true;
                if (targetDirectoryUri == null) {
                    showToast("Bitte wählen Sie zuerst ein Zielverzeichnis aus!");
                    return;
                }
                if (sourceFolderUri == null) {
                    showToast("Bitte wählen Sie einen Quellordner aus, der gesichert werden soll!");
                    return;
                }
                // Starte den rekursiven Backup-Vorgang
                DocumentFile targetDir = DocumentFile.fromTreeUri(MainActivity.this, targetDirectoryUri);
                performBackup(sourceFolderUri, targetDir);
            }
        });

        // Button, um Wiederherstellung zu starten
        Button restoreButton = findViewById(R.id.restore_button);
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isBackupOperation = false;
                if (targetDirectoryUri == null) {
                    showToast("Bitte wählen Sie zuerst ein Zielverzeichnis aus!");
                    return;
                }
                if (sourceFolderUri == null) {
                    showToast("Bitte wählen Sie einen Ordner aus, der das Backup enthält!");
                    return;
                }
                // Starte den rekursiven Wiederherstellungs-Vorgang
                DocumentFile targetDir = DocumentFile.fromTreeUri(MainActivity.this, targetDirectoryUri);
                performRestore(sourceFolderUri, targetDir);
            }
        });
    }

    // Öffnet den SAF-Dialog, um das Zielverzeichnis auszuwählen
    private void openTargetDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_TARGET_DIRECTORY);
    }

    // Öffnet den SAF-Dialog, um den Quellordner auszuwählen
    private void openSourceFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_SOURCE_FOLDER);
    }

    // Callback nach Auswahl von Verzeichnissen
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_CODE_PICK_TARGET_DIRECTORY) {
                targetDirectoryUri = uri;
                showToast("Zielverzeichnis ausgewählt: " + uri.getPath());
            } else if (requestCode == REQUEST_CODE_PICK_SOURCE_FOLDER) {
                sourceFolderUri = uri;
                showToast("Quellordner ausgewählt: " + uri.getPath());
            }
        }
    }

    // Rekursive Backup-Methode: Kopiert den Inhalt des Quellordners in das Zielverzeichnis
    private void performBackup(final Uri sourceUri, final DocumentFile targetDir) {
        // Backup in einem neuen Thread ausführen, um den UI-Thread nicht zu blockieren
        new Thread(new Runnable() {
            @Override
            public void run() {
                DocumentFile sourceDir = DocumentFile.fromTreeUri(MainActivity.this, sourceUri);
                if (sourceDir == null || !sourceDir.isDirectory()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showToast("Der ausgewählte Quellordner ist ungültig.");
                        }
                    });
                    return;
                }

                // Erstelle einen Ordner im Zielverzeichnis mit dem gleichen Namen wie der Quellordner
                DocumentFile newTargetDir = targetDir.createDirectory(sourceDir.getName());
                if (newTargetDir == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showToast("Fehler beim Erstellen des Zielordners für " + sourceDir.getName());
                        }
                    });
                    return;
                }

                // Durchlaufe alle Dateien und Unterordner im Quellordner
                for (DocumentFile file : sourceDir.listFiles()) {
                    if (file.isDirectory()) {
                        // Rekursiver Aufruf für Unterordner
                        performBackup(file.getUri(), newTargetDir);
                    } else {
                        // Datei kopieren
                        copyFile(file.getUri(), newTargetDir);
                    }
                }

                // Nach Abschluss im UI-Thread benachrichtigen
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Backup von Ordner " + sourceDir.getName() + " erfolgreich!");
                    }
                });
            }
        }).start();
    }


    // Rekursive Wiederherstellungs-Methode: Kopiert den Inhalt eines Backup-Ordners in das Zielverzeichnis
    private void performRestore(Uri backupUri, DocumentFile targetDir) {
        DocumentFile backupDir = DocumentFile.fromTreeUri(this, backupUri);
        if (backupDir == null || !backupDir.isDirectory()) {
            showToast("Der ausgewählte Backup-Ordner ist ungültig.");
            return;
        }

        // Erstelle einen Ordner im Zielverzeichnis mit dem gleichen Namen wie der Backup-Ordner
        DocumentFile newTargetDir = targetDir.createDirectory(backupDir.getName());
        if (newTargetDir == null) {
            showToast("Fehler beim Erstellen des Zielordners für " + backupDir.getName());
            return;
        }

        // Durchlaufe alle Dateien und Unterordner im Backup-Ordner
        for (DocumentFile child : backupDir.listFiles()) {
            if (child.isDirectory()) {
                performRestore(child.getUri(), newTargetDir);
            } else {
                copyFile(child.getUri(), newTargetDir);
            }
        }
        showToast("Wiederherstellung von Ordner " + backupDir.getName() + " erfolgreich!");
    }

    // Kopiert eine Datei von der Quelle ins Zielverzeichnis und behält den Originalnamen und MIME-Typ bei
    private void copyFile(Uri sourceUri, DocumentFile targetDir) {
        try {
            // Hole den Originaldateinamen
            String fileName = getFileName(sourceUri);
            if (fileName == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Fehler: Dateiname konnte nicht ermittelt werden.");
                    }
                });
                return;
            }
            // Bestimme den MIME-Typ
            String mimeType = getMimeType(sourceUri);
            // Erstelle die Zieldatei im Zielverzeichnis
            DocumentFile targetFile = targetDir.createFile(mimeType, fileName);
            if (targetFile == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Fehler beim Erstellen der Datei: " + fileName);
                    }
                });
                return;
            }
            InputStream inputStream = getContentResolver().openInputStream(sourceUri);
            OutputStream outputStream = getContentResolver().openOutputStream(targetFile.getUri());
            if (inputStream == null || outputStream == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Fehler beim Öffnen der Datei: " + fileName);
                    }
                });
                return;
            }
            byte[] buffer = new byte[4096];  // Größerer Puffer (4 KB)
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            final String errorMsg = "Fehler beim Kopieren der Datei: " + e.getMessage();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showToast(errorMsg);
                }
            });
        }
    }


    // Hilfsmethode: Ermittelt den Dateinamen einer Uri
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    // Hilfsmethode: Ermittelt den MIME-Typ einer Datei anhand der Uri
    private String getMimeType(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    // Einfache Methode, um Toast-Meldungen anzuzeigen
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
