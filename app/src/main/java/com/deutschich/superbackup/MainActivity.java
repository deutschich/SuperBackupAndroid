package com.deutschich.superbackup;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import android.content.pm.PackageManager;
import android.Manifest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_DIRECTORY = 1;
    private static final int REQUEST_CODE_PICK_FOLDER = 2; // Zum Auswählen des Quellordners
    private Uri selectedDirectoryUri = null;
    private Uri selectedFolderUri = null; // Uri für den Quellordner
    private boolean isBackupSelection = true; // Ob Backup oder Restore

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Button zum Verzeichnis auswählen
        Button selectDirectoryButton = findViewById(R.id.select_directory_button);
        selectDirectoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDirectoryPicker(); // Zielverzeichnis auswählen
            }
        });

        // Button für Backup
        Button backupButton = findViewById(R.id.backup_button);
        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isBackupSelection = true; // Beim Klick auf Backup wird die Auswahl für das Backup aktiviert
                if (selectedDirectoryUri == null) {
                    showToast("Bitte wählen Sie ein Zielverzeichnis aus!");
                    return;
                }
                openFolderPicker(); // Nun kann der Benutzer den Quellordner auswählen
            }
        });

        // Button für Wiederherstellung
        Button restoreButton = findViewById(R.id.restore_button);
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isBackupSelection = false; // Beim Klick auf Restore wird die Auswahl für die Wiederherstellung aktiviert
                if (selectedDirectoryUri == null) {
                    showToast("Bitte wählen Sie ein Zielverzeichnis aus!");
                    return;
                }
                openFolderPicker(); // Nun kann der Benutzer den Ordner zum Wiederherstellen auswählen
            }
        });
    }

    // Methode, um das Zielverzeichnis auszuwählen
    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_DIRECTORY);
    }

    // Methode, um den Quellordner auszuwählen
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER);
    }

    // Callback nach Auswahl des Verzeichnisses oder Ordners
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_PICK_DIRECTORY) {
                selectedDirectoryUri = data.getData();
                showToast("Zielverzeichnis ausgewählt: " + selectedDirectoryUri.getPath());
            } else if (requestCode == REQUEST_CODE_PICK_FOLDER) {
                selectedFolderUri = data.getData();
                if (isBackupSelection) {
                    performBackup(selectedFolderUri);
                } else {
                    performRestore(selectedFolderUri);
                }
            }
        }
    }

    // Backup-Methode für einen gesamten Ordner
    private void performBackup(Uri sourceUri) {
        if (selectedDirectoryUri == null || sourceUri == null) {
            showToast("Bitte wählen Sie sowohl Zielverzeichnis als auch Quellordner aus.");
            return;
        }

        DocumentFile sourceDir = DocumentFile.fromTreeUri(this, sourceUri);
        DocumentFile targetDir = DocumentFile.fromTreeUri(this, selectedDirectoryUri);

        // Durchlauf aller Dateien im Quellordner und kopieren ins Ziel
        if (sourceDir != null && sourceDir.isDirectory()) {
            for (DocumentFile file : sourceDir.listFiles()) {
                if (file.isDirectory()) {
                    // Rekursiv für Unterordner
                    DocumentFile targetSubDir = targetDir.createDirectory(file.getName());
                    if (targetSubDir != null) {
                        performBackup(file.getUri()); // Rekursion für Unterordner
                    }
                } else {
                    // Datei kopieren
                    copyFile(file.getUri(), targetDir);
                }
            }
            showToast("Backup erfolgreich!");
        } else {
            showToast("Der ausgewählte Ordner ist ungültig.");
        }
    }

    // Methode zum Kopieren von Dateien
    // Holt den Dateinamen aus einer Uri
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
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

    // Holt den MIME-Typ einer Datei basierend auf ihrer Uri
    private String getMimeType(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private void copyFile(Uri sourceUri, DocumentFile targetDir) {
        try {
            // Den Original-Dateinamen ermitteln
            String fileName = getFileName(sourceUri);
            if (fileName == null) {
                showToast("Fehler: Dateiname konnte nicht ermittelt werden.");
                return;
            }

            // Die Datei im Zielverzeichnis erstellen
            DocumentFile targetFile = targetDir.createFile(getMimeType(sourceUri), fileName);
            if (targetFile == null) {
                showToast("Fehler beim Erstellen der Datei.");
                return;
            }

            // Datei kopieren
            InputStream inputStream = getContentResolver().openInputStream(sourceUri);
            OutputStream outputStream = getContentResolver().openOutputStream(targetFile.getUri());
            if (inputStream == null || outputStream == null) {
                showToast("Fehler beim Öffnen der Datei.");
                return;
            }

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            showToast("Datei gesichert: " + fileName);
        } catch (IOException e) {
            showToast("Fehler beim Kopieren der Datei: " + e.getMessage());
        }
    }


    // Wiederherstellungs-Methode für einen gesamten Ordner
    private void performRestore(Uri backupUri) {
        if (selectedDirectoryUri == null || backupUri == null) {
            showToast("Bitte wählen Sie sowohl Zielverzeichnis als auch Backup-Ordner aus.");
            return;
        }

        DocumentFile sourceDir = DocumentFile.fromTreeUri(this, backupUri);
        DocumentFile targetDir = DocumentFile.fromTreeUri(this, selectedDirectoryUri);

        // Durchlauf aller Dateien im Backup-Ordner und Wiederherstellung ins Ziel
        if (sourceDir != null && sourceDir.isDirectory()) {
            for (DocumentFile file : sourceDir.listFiles()) {
                if (file.isDirectory()) {
                    // Rekursiv für Unterordner
                    DocumentFile targetSubDir = targetDir.createDirectory(file.getName());
                    if (targetSubDir != null) {
                        performRestore(file.getUri()); // Rekursion für Unterordner
                    }
                } else {
                    // Datei wiederherstellen
                    copyFile(file.getUri(), targetDir);
                }
            }
            showToast("Wiederherstellung erfolgreich!");
        } else {
            showToast("Der ausgewählte Ordner ist ungültig.");
        }
    }

    // Einfacher Toast, um Nachrichten anzuzeigen
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Berechtigungsanfrage zur Laufzeit
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showToast("Berechtigung erteilt.");
        } else {
            showToast("Berechtigung abgelehnt.");
        }
    }
}