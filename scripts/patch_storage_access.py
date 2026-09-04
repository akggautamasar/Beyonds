from pathlib import Path

p = Path('app/src/main/java/com/beyond/v12/MainActivity.java')
s = p.read_text(encoding='utf-8')

s = s.replace('import android.provider.OpenableColumns;\n', 'import android.provider.OpenableColumns;\nimport android.provider.DocumentsContract;\n')
s = s.replace('static final int PICK_FILE=101,PICK_VIDEO=102,CAMERA=103;', 'static final int PICK_FILE=101,PICK_VIDEO=102,CAMERA=103,SAVE_OUTPUT=104;')
s = s.replace('Button encode,decode,cameraBtn; TextView status; ExecutorService exec=Executors.newSingleThreadExecutor();', 'Button encode,decode,cameraBtn; TextView status; ExecutorService exec=Executors.newSingleThreadExecutor(); File pendingSaveFile; String pendingSaveName; String pendingSaveMime;')
s = s.replace('info.setText("Works offline. Original bytes are verified with SHA-256.\\nCamera mode: show a Beyond video full-screen on another device and point the camera at it.");', 'info.setText("Works offline. Original bytes are verified with SHA-256.\\nOutputs are saved wherever you choose in the Android file picker.\\nCamera mode: show a Beyond video full-screen on another device and point the camera at it.");')

old_result = '    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;Uri u=d.getData();if(r==PICK_FILE)encodeFile(u);else if(r==PICK_VIDEO)decodeVideo(u);}'
new_result = '''    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==SAVE_OUTPUT){
            if(c==RESULT_OK&&d!=null&&d.getData()!=null){
                Uri u=d.getData();
                File f=pendingSaveFile; String n=pendingSaveName;
                if(f!=null) exec.execute(()->{try{copyFileToUri(f,u);f.delete();status("SAVED\\n"+n+"\\nLocation chosen in the system file picker.");}catch(Exception e){status("Save failed: "+e.getMessage());}});
            }else status("Save cancelled. The generated output remains in app cache until the app is closed.");
            return;
        }
        if(c!=RESULT_OK||d==null)return;
        Uri u=d.getData();
        if(r==PICK_FILE)encodeFile(u);else if(r==PICK_VIDEO)decodeVideo(u);
    }'''
if old_result not in s: raise SystemExit('onActivityResult pattern not found')
s = s.replace(old_result, new_result)

old_encode = '    void encodeFile(Uri uri){exec.execute(()->{try{String name=getName(uri);byte[] data=readAll(uri);status("Encoding "+name+"\\n"+data.length+" bytes…");File out=new File(getExternalFilesDir(null),safe(name)+".beyond.mp4");VideoCodec.encode(data,name,out,p->status("Encoding… "+p+"%"));status("DONE\\n"+out.getAbsolutePath()+"\\n\\n"+data.length+" bytes encoded.\\nSHA-256: "+hex(sha(data)));}catch(Exception e){status("Encode failed: "+e);}});}'
new_encode = '''    void encodeFile(Uri uri){exec.execute(()->{try{String name=getName(uri);byte[] data=readAll(uri);status("Encoding "+name+"\\n"+data.length+" bytes…");File out=new File(getCacheDir(),safe(name)+".beyond.mp4");VideoCodec.encode(data,name,out,p->status("Encoding… "+p+"%"));status("ENCODED\\n"+data.length+" bytes\\nSHA-256: "+hex(sha(data))+"\\nChoose where to save the Beyond video…");requestSave(out,safe(name)+".beyond.mp4","video/mp4");}catch(Exception e){status("Encode failed: "+e);}});}'''
if old_encode not in s: raise SystemExit('encodeFile pattern not found')
s = s.replace(old_encode, new_encode)

old_decode = '    void decodeVideo(Uri uri){exec.execute(()->{try{status("Reading Beyond video…");File tmp=new File(getCacheDir(),"input.mp4");copy(uri,tmp);Packet.Result r=VideoCodec.decode(tmp,p->status("Decoding… "+p+"%"));File out=new File(getExternalFilesDir(null),safe(r.name));write(out,r.data);status("DONE\\nRecovered: "+out.getAbsolutePath()+"\\nSize: "+r.data.length+" bytes\\nSHA-256: "+hex(sha(r.data)));}catch(Exception e){status("Decode failed: "+e.getMessage());}});}'
new_decode = '''    void decodeVideo(Uri uri){exec.execute(()->{try{status("Reading Beyond video…");File tmp=new File(getCacheDir(),"input.mp4");copy(uri,tmp);Packet.Result r=VideoCodec.decode(tmp,p->status("Decoding… "+p+"%"));File out=new File(getCacheDir(),safe(r.name));write(out,r.data);tmp.delete();status("DECODED\\nRecovered: "+r.data.length+" bytes\\nSHA-256: "+hex(sha(r.data))+"\\nChoose where to save the recovered file…");requestSave(out,safe(r.name),"application/octet-stream");}catch(Exception e){status("Decode failed: "+e.getMessage());}});}'''
if old_decode not in s: raise SystemExit('decodeVideo pattern not found')
s = s.replace(old_decode, new_decode)

anchor = '    void stopCamera(){try{if(session!=null)session.close();if(cam!=null)cam.close();if(reader!=null)reader.close();}catch(Exception ignored){}}'
insert = '''    void requestSave(File file,String name,String mime){
        pendingSaveFile=file; pendingSaveName=name; pendingSaveMime=mime;
        runOnUiThread(()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(pendingSaveMime==null?"application/octet-stream":pendingSaveMime);
            i.putExtra(Intent.EXTRA_TITLE,pendingSaveName);
            startActivityForResult(i,SAVE_OUTPUT);
        });
    }
    void copyFileToUri(File file,Uri uri)throws Exception{
        InputStream in=new FileInputStream(file);
        OutputStream out=getContentResolver().openOutputStream(uri,"w");
        if(out==null){in.close();throw new IOException("Cannot open selected destination");}
        byte[] b=new byte[65536]; int n;
        try{while((n=in.read(b))>0)out.write(b,0,n);out.flush();}finally{in.close();out.close();}
    }
'''
if anchor not in s: raise SystemExit('camera anchor not found')
s = s.replace(anchor, anchor+'\n'+insert)

p.write_text(s,encoding='utf-8')
print('Storage Access Framework output saving patched successfully.')
