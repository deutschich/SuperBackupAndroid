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
    private Uri targetDirectoryUri = null; // Zielverzeichnis, in das das Backup gespeichert bzw. von dem wiederhergestellt wird
    private Uri sourceFolderUri = null;    // Quellordner, der gesichert werden soll (oder der das Backup enthält)
    private boolean isBackupOperation = true; // true = Backup, false = Wiederherstellung

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Button zum Auswählen des Zielverzeichnisses
        Button selectTargetDirButton = findViewById(R.id.select_target_directory_button);
        selectTargetDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTargetDirectoryPicker();
            }
        });

        // Button zum Auswählen des Quellordners
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
                    showToast(getString(R.string.choose_target_directory));
                    return;
                }
                if (sourceFolderUri == null) {
                    showToast(getString(R.string.choose_source_folder));
                    return;
                }
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
                    showToast(getString(R.string.choose_target_directory));
                    return;
                }
                if (sourceFolderUri == null) {
                    showToast(getString(R.string.choose_source_folder));
                    return;
                }
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

    // Callback nach Auswahl von Ziel- oder Quellordner
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_CODE_PICK_TARGET_DIRECTORY) {
                targetDirectoryUri = uri;
                showToast(String.format(getString(R.string.directory_selected), uri.getPath()));
            } else if (requestCode == REQUEST_CODE_PICK_SOURCE_FOLDER) {
                sourceFolderUri = uri;
                showToast(String.format(getString(R.string.folder_selected), uri.getPath()));
            }
        }
    }

    // Rekursive Backup-Methode: Kopiert den Inhalt des Quellordners in das Zielverzeichnis
    private void performBackup(Uri sourceUri, DocumentFile targetDir) {
        DocumentFile sourceDir = DocumentFile.fromTreeUri(this, sourceUri);
        if (sourceDir == null || !sourceDir.isDirectory()) {
            showToast(getString(R.string.folder_invalid));
            return;
        }
        // Erstelle im Zielverzeichnis einen Ordner mit demselben Namen wie der Quellordner
        DocumentFile newTargetDir = targetDir.createDirectory(sourceDir.getName());
        if (newTargetDir == null) {
            showToast(String.format(getString(R.string.file_copy_error), sourceDir.getName()));
            return;
        }
        for (DocumentFile child : sourceDir.listFiles()) {
            if (child.isDirectory()) {
                performBackup(child.getUri(), newTargetDir);
            } else {
                copyFile(child.getUri(), newTargetDir);
            }
        }
        showToast(getString(R.string.backup_successful));
    }

    // Rekursive Wiederherstellungs-Methode: Kopiert den Inhalt eines Backup-Ordners ins Zielverzeichnis
    private void performRestore(Uri backupUri, DocumentFile targetDir) {
        DocumentFile backupDir = DocumentFile.fromTreeUri(this, backupUri);
        if (backupDir == null || !backupDir.isDirectory()) {
            showToast(getString(R.string.folder_invalid));
            return;
        }
        DocumentFile newTargetDir = targetDir.createDirectory(backupDir.getName());
        if (newTargetDir == null) {
            showToast(String.format(getString(R.string.file_copy_error), backupDir.getName()));
            return;
        }
        for (DocumentFile child : backupDir.listFiles()) {
            if (child.isDirectory()) {
                performRestore(child.getUri(), newTargetDir);
            } else {
                copyFile(child.getUri(), newTargetDir);
            }
        }
        showToast(getString(R.string.restore_successful));
    }

    // Kopiert eine Datei vom Quell-URI in das Zielverzeichnis und behält den Originalnamen und MIME-Typ bei
    private void copyFile(Uri sourceUri, DocumentFile targetDir) {
        try {
            String fileName = getFileName(sourceUri);
            if (fileName == null) {
                showToast(getString(R.string.file_not_found));
                return;
            }
            String mimeType = getMimeType(sourceUri);
            DocumentFile targetFile = targetDir.createFile(mimeType, fileName);
            if (targetFile == null) {
                showToast(String.format(getString(R.string.file_copy_error), fileName));
                return;
            }
            InputStream inputStream = getContentResolver().openInputStream(sourceUri);
            OutputStream outputStream = getContentResolver().openOutputStream(targetFile.getUri());
            if (inputStream == null || outputStream == null) {
                showToast(String.format(getString(R.string.file_copy_error), fileName));
                return;
            }
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            showToast(String.format(getString(R.string.file_copy_error), e.getMessage()));
        }
    }

    // Hilfsmethode: Ermittelt den Dateinamen aus der Uri
    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
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

    // Zeigt eine Toast-Nachricht an
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

