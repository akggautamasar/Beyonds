package com.beyond.v12;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.nio.ByteBuffer;
import java.util.*;

public class MainActivity extends Activity {
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private TextureView preview;
    private TextView status;
    private int frames = 0, valid = 0;
    private Size cameraSize;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        preview = new TextureView(this);
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int w, int h) {
                openCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int w, int h) {
                configureTransform(w, h);
            }
            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) { return true; }
            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) { }
        });
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        status = new TextView(this);
        status.setText("Beyond V12\nRequesting camera permission…");
        status.setTextSize(18);
        status.setPadding(24,18,24,18);
        root.addView(status, new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 10);
        } else if (preview.isAvailable()) {
            openCamera();
        }
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==10 && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) {
            if (preview.isAvailable()) openCamera();
        } else status.setText("Camera permission is required.");
    }

    private void openCamera() {
        if (camera != null) return;
        try {
            CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE);
            String id=cm.getCameraIdList()[0];
            CameraCharacteristics c=cm.getCameraCharacteristics(id);
            android.hardware.camera2.params.StreamConfigurationMap map =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new IllegalStateException("No camera stream configuration");

            Size[] sizes=map.getOutputSizes(ImageReader.class);
            Size best=sizes[0];
            for(Size s:sizes) {
                if(s.getWidth()*s.getHeight()>best.getWidth()*best.getHeight() &&
                        s.getWidth()*s.getHeight()<=1920*1080) best=s;
            }
            cameraSize=best;
            reader=ImageReader.newInstance(best.getWidth(),best.getHeight(),android.graphics.ImageFormat.YUV_420_888,3);
            reader.setOnImageAvailableListener(r -> {
                Image im=null;
                try {
                    im=r.acquireLatestImage();
                    if(im==null)return;
                    frames++;
                    ByteBuffer y=im.getPlanes()[0].getBuffer();
                    int limit=Math.min(y.remaining(),im.getWidth()*im.getHeight());
                    int step=Math.max(1,limit/4000);
                    long sample=0;
                    for(int i=0;i<limit;i+=step) sample += y.get(i)&255;
                    if(sample>0) valid++;
                    final String s="Beyond V12 • LIVE CAMERA\nFrames: "+frames+
                            "\nCaptured: "+valid+"\n\nCamera2 stream active\nFinder/RS decoder: next layer";
                    runOnUiThread(() -> status.setText(s));
                } finally { if(im!=null)im.close(); }
            },null);

            cm.openCamera(id,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){
                    camera=c;
                    startSession();
                }
                @Override public void onDisconnected(CameraDevice c){ c.close(); camera=null; }
                @Override public void onError(CameraDevice c,int e){
                    c.close(); camera=null;
                    runOnUiThread(() -> status.setText("Camera error: "+e));
                }
            },null);
        } catch(Exception e){ status.setText("Camera start failed: "+e.getMessage()); }
    }

    private void startSession() {
        try {
            if (!preview.isAvailable()) throw new IllegalStateException("Preview surface unavailable");
            android.graphics.SurfaceTexture texture=preview.getSurfaceTexture();
            texture.setDefaultBufferSize(cameraSize.getWidth(),cameraSize.getHeight());
            Surface previewSurface=new Surface(texture);
            Surface readerSurface=reader.getSurface();
            List<Surface> outputs=Arrays.asList(previewSurface,readerSurface);

            camera.createCaptureSession(outputs, new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession cs){
                    session=cs;
                    try {
                        CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(previewSurface);
                        b.addTarget(readerSurface);
                        b.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        b.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
                        cs.setRepeatingRequest(b.build(),null,null);
                        configureTransform(preview.getWidth(),preview.getHeight());
                        status.setText("Beyond V12 • LIVE CAMERA\nCamera2 preview active\nPoint at a Beyond screen.");
                    }catch(Exception e){status.setText("Capture failed: "+e.getMessage());}
                }
                @Override public void onConfigureFailed(CameraCaptureSession cs){status.setText("Camera session failed.");}
            },null);
        }catch(Exception e){status.setText("Session error: "+e.getMessage());}
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (preview == null || cameraSize == null || viewWidth == 0 || viewHeight == 0) return;
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0,0,viewWidth,viewHeight);
        RectF bufferRect = new RectF(0,0,cameraSize.getHeight(),cameraSize.getWidth());
        float centerX=viewRect.centerX(), centerY=viewRect.centerY();
        bufferRect.offset(centerX-bufferRect.centerX(),centerY-bufferRect.centerY());
        matrix.setRectToRect(viewRect,bufferRect,Matrix.ScaleToFit.FILL);
        float scale=Math.max((float)viewWidth/cameraSize.getHeight(),(float)viewHeight/cameraSize.getWidth());
        matrix.setScale(scale,scale,centerX,centerY);
        preview.setTransform(matrix);
    }

    @Override protected void onDestroy(){
        if(session!=null)try{session.close();}catch(Exception ignored){}
        if(camera!=null)try{camera.close();}catch(Exception ignored){}
        if(reader!=null)reader.close();
        super.onDestroy();
    }
}
