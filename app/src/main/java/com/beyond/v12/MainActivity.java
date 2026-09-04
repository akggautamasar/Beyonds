package com.beyond.v12;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.view.*;
import android.widget.*;
import java.nio.ByteBuffer;
import java.util.*;

public class MainActivity extends Activity {
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private PreviewView preview;
    private TextView status;
    private int frames = 0, valid = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        preview = new PreviewView(this);
        root.addView(preview, new LinearLayout.LayoutParams(-1,0,1));
        status = new TextView(this);
        status.setText("Beyond V12\nRequesting camera permission…");
        status.setTextSize(18);
        status.setPadding(24,18,24,18);
        root.addView(status, new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 10);
        else openCamera();
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==10 && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) openCamera();
        else status.setText("Camera permission is required.");
    }

    private void openCamera() {
        try {
            CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE);
            String id=cm.getCameraIdList()[0];
            CameraCharacteristics c=cm.getCameraCharacteristics(id);
            android.util.Size[] sizes=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.YUV_420_888);
            android.util.Size best=sizes[0];
            for(android.util.Size s:sizes) if(s.getWidth()*s.getHeight()>best.getWidth()*best.getHeight()) best=s;
            reader=ImageReader.newInstance(best.getWidth(),best.getHeight(),ImageFormat.YUV_420_888,3);
            reader.setOnImageAvailableListener(r -> {
                Image im=null;
                try {
                    im=r.acquireLatestImage();
                    if(im==null)return;
                    frames++;
                    int w=im.getWidth(), h=im.getHeight();
                    ByteBuffer y=im.getPlanes()[0].getBuffer();
                    int sample=0;
                    for(int i=0;i<Math.min(y.remaining(),w*h);i+=Math.max(1,w*h/4000)) sample += y.get(i)&255;
                    if(sample > 0) valid++;
                    final String s="Beyond V12 • LIVE CAMERA\nFrames: "+frames+
                            "\nCaptured: "+valid+"\n\nCamera2 stream active\nFinder/RS decoder: next layer";
                    runOnUiThread(() -> status.setText(s));
                } finally { if(im!=null)im.close(); }
            },null);
            cm.openCamera(id,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){ camera=c; startSession(); }
                @Override public void onDisconnected(CameraDevice c){ c.close(); }
                @Override public void onError(CameraDevice c,int e){ c.close(); status.setText("Camera error: "+e); }
            },null);
        } catch(Exception e){ status.setText("Camera start failed: "+e.getMessage()); }
    }

    private void startSession() {
        try {
            Surface s=reader.getSurface();
            camera.createCaptureSession(Collections.singletonList(s), new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession cs){
                    session=cs;
                    try {
                        CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(s);
                        b.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        b.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
                        cs.setRepeatingRequest(b.build(),null,null);
                        status.setText("Beyond V12 • LIVE CAMERA\nCamera2 stream active\nPoint at a Beyond screen.");
                    }catch(Exception e){status.setText("Capture failed: "+e.getMessage());}
                }
                @Override public void onConfigureFailed(CameraCaptureSession cs){status.setText("Camera session failed.");}
            },null);
        }catch(Exception e){status.setText("Session error: "+e.getMessage());}
    }

    @Override protected void onDestroy(){
        if(session!=null)try{session.close();}catch(Exception ignored){}
        if(camera!=null)try{camera.close();}catch(Exception ignored){}
        if(reader!=null)reader.close();
        super.onDestroy();
    }

    static class PreviewView extends View {
        Paint p=new Paint(1);
        PreviewView(android.content.Context c){super(c);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);}
        protected void onDraw(Canvas c){
            c.drawColor(Color.BLACK); p.setColor(Color.WHITE);
            float l=getWidth()*.08f,t=getHeight()*.18f,r=getWidth()*.92f,b=getHeight()*.82f;
            c.drawRect(l,t,r,b,p); p.setStyle(Paint.Style.FILL); p.setTextSize(28);
            c.drawText("BEYOND V12",24,50,p); p.setStyle(Paint.Style.STROKE);
        }
    }
}
