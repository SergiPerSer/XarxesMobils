package com.example.opencam;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

public class CameraPreview extends SurfaceView implements
        SurfaceHolder.Callback {
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private Context mContext;
    private Camera.Size mPreviewSize;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        this.mCamera = camera;
        this.mSurfaceHolder = this.getHolder();
        this.mSurfaceHolder.addCallback(this);
        this.mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        this.mContext = context;
    }
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.stopPreview();
        mCamera.release();
    }
    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width,
                               int height) {
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            mPreviewSize = getBestPreviewSize(width, height, parameters.getSupportedPreviewSizes());
            if (mPreviewSize != null) {
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                requestLayout();
            }
            mCamera.setDisplayOrientation(90);
            mCamera.setParameters(parameters);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.e("surfaceChanged", "ERROR:" + e);
        }
    }

    private Camera.Size getBestPreviewSize(int width, int height, java.util.List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }

        // La cámara muestra la imagen girada 90 grados; compara las dimensiones en esa orientación.
        double targetRatio = (double) height / width;
        Camera.Size bestSize = sizes.get(0);
        double bestRatioDifference = Double.MAX_VALUE;
        long bestAreaDifference = Long.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            double ratioDifference = Math.abs(ratio - targetRatio);
            long areaDifference = Math.abs((long) size.width * size.height - (long) width * height);
            if (ratioDifference < bestRatioDifference
                    || (Math.abs(ratioDifference - bestRatioDifference) < 0.01
                    && areaDifference < bestAreaDifference)) {
                bestSize = size;
                bestRatioDifference = ratioDifference;
                bestAreaDifference = areaDifference;
            }
        }
        return bestSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = View.MeasureSpec.getSize(heightMeasureSpec);

        if (mPreviewSize == null) {
            setMeasuredDimension(width, height);
            return;
        }

        // Compensa la rotación de 90 grados aplicada a la cámara.
        int previewWidth = mPreviewSize.height;
        int previewHeight = mPreviewSize.width;
        float scale = Math.min((float) width / previewWidth, (float) height / previewHeight);
        setMeasuredDimension(Math.round(previewWidth * scale), Math.round(previewHeight * scale));
    }
}
