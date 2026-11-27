package com.example.opencam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    private FrameLayout cameraPreviewLayout;
    private android.hardware.Camera mCamera;
    private CameraPreview mCameraPreview;
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCameraPreview();
                } else {
                    Toast.makeText(this, "Se necesita permiso para usar la cámara", Toast.LENGTH_LONG).show();
                }
            });
    android.hardware.Camera.PictureCallback mPicture = new
            android.hardware.Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
                    byte[] resized = resizeImage(data);
                    File pictureFile = getOutputMediaFile();
                    if (pictureFile == null) {
                        return;
                    }
                    try {
                        FileOutputStream fos = new FileOutputStream(pictureFile);
                        fos.write(resized);
                        fos.close();
                    } catch (Exception e) {
                        Log.e("onPictureTaken", "ERROR:" + e);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        cameraPreviewLayout = findViewById(R.id.cameraView);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview();
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }

    private void startCameraPreview() {
        mCamera = getCameraInstance();
        if (mCamera != null) {
            mCameraPreview = new CameraPreview(this, mCamera);
            FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER);
            cameraPreviewLayout.addView(mCameraPreview, previewParams);
        } else {
            Toast.makeText(this, "No se pudo abrir la cámara del dispositivo", Toast.LENGTH_LONG).show();
        }
    }

    public Camera getCameraInstance() {
        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "No se pudo abrir la cámara", e);
        }
        return camera;
    }
    private File getOutputMediaFile() {
        File picturesDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDirectory == null) {
            Log.e("OpenCam", "No se pudo acceder al almacenamiento de imágenes");
            return null;
        }

        File mediaStorageDir = new File(picturesDirectory, "OpenCam");
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.e("OpenCam", "No se pudo crear el directorio de imágenes");
            return null;
        }

        return new File(mediaStorageDir, "IMG.jpg");
    }
    public void captureCamera(){
        if (mCamera!=null) {
            mCamera.takePicture(null, null, mPicture);
        }
    }
    byte[] resizeImage(byte[] input) {
        Bitmap originalBitmap = BitmapFactory.decodeByteArray(input, 0, input.length);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 107,
                true);
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, blob);
        return blob.toByteArray();
    }

}
