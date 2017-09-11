package com.shuvam.texteditor;
import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.shuvam.texteditor.utils.SharedPrefsUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import io.realm.Realm;
import io.realm.RealmResults;

public class MainActivity extends AppCompatActivity {

    private EditText edt;
    private Button btn;
    private SharedPrefsUtils sp;
    private Intent intent;
    private int SELECT_PICTURE = 1;
    private String mImageName;
    private Button fabSave;
    private Button fabPreview;
    private String TAG = "Log Tag";
    private StorageReference mStorageRef;
    private ArrayList<FileModel> files;
    private int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 100;
    private ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();

        //Firebase Setup
        final FirebaseDatabase firebaseDatabase = setUpFirebase();

        //Real setup
        Realm.init(MainActivity.this);
        final Realm realm = Realm.getDefaultInstance();
        final boolean granted = checkIfPermissionGranted();


        if(sp.getBooleanPreference(this,"FirstTime",true))
        {
            askForPermission();
            sp.setBooleanPreference(this,"FirstTime",false);

            //First time create a Directory
            File folder = new File(Environment.getExternalStorageDirectory() +
                    File.separator + "LipiEditor");

            boolean success = true;
            if (!folder.exists()) {
                success = folder.mkdirs();
            }
            if (success) {
                Log.d("Folder creation ","Successful");
            } else {
                Log.d("Folder creation ","Failed");
            }
        }
        else
        {
            if(granted) {
                realm.beginTransaction();
                RealmResults<FileModel> results = realm.where(FileModel.class).findAll();
                Log.d("The realm results", ":");
                if (results.size() > 0) {

                    for (FileModel fmd : results) {
                        Log.d("Result--", "" + fmd.getTextContent());
                    }

                    edt.setText(results.get(results.size() - 1).getTextContent());
                    File mediaStorageDir = createDir();
                    List<File> fls = Arrays.asList(mediaStorageDir.listFiles());
                    if (fls.size()>0) {
                        for (File ff : fls) {
                            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                            Bitmap bitmap = BitmapFactory.decodeFile(ff.getAbsolutePath(), bmOptions);
                            insertImageToCurrentSelection(bitmap, giveNumber(ff.getName()));
                        }
                    }
                }
                realm.commitTransaction();
            }
            else
            {
                askForPermission();
            }
        }

        fabSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showProgressBar("Saving and syncing with remote storage");
                    boolean grantedhere = checkIfPermissionGranted();
                    if (grantedhere) {
                        File mediaStorageDir = createDir();

                        if (mediaStorageDir != null) {
                            List<File> fls = Arrays.asList(mediaStorageDir.listFiles());
                            Collections.sort(fls, new Comparator<File>() {
                            @Override
                            public int compare(File file, File t1) {
                                return giveNumber(file.getName()) - (giveNumber(t1.getName()));
                            }
                        });

                        String str = edt.getText().toString();
                        int k = 0;
                        for (int i = 0; i < str.length(); i++) {
                            if (str.charAt(i) == '$') {

                                Log.d("Dollar found in: ", "" + i);
                                File f = new File(mediaStorageDir.getAbsoluteFile() + "/File(" + (i + 1) + ").jpg");
                                fls.get(k).renameTo(f);
                                k++;
                            }
                        }
                        Realm realm = Realm.getDefaultInstance();
                        realm.beginTransaction();
                        FileModel fm = realm.createObject(FileModel.class);
                        fm.setTextContent(edt.getText().toString());
                        realm.commitTransaction();


                            if(isNetworkAvailable()) {

                                //Update Firebase database
                                DatabaseReference myRef = firebaseDatabase.getReference("content");
                                myRef.setValue(edt.getText().toString());
                                //Update Firebase storage
                                uploadToFirebase();
                            }
                            else
                            {
                                Toast.makeText(MainActivity.this, "Saved changes locally as network is unavailable", Toast.LENGTH_SHORT).show();
                            }

                    }
                }
                else
                    {
                        Log.d("ask for"," permission");
                        askForPermission();
                    }
            }
            });
        fabPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean networkAvailable = isNetworkAvailable();
                if(!networkAvailable)
                {
                    Toast.makeText(MainActivity.this, "Please get online to preview", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Intent i = new Intent(MainActivity.this,PreviewActivity.class);
                    startActivity(i);
                }
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {
               intent = new Intent();
               intent.setType("image/*");
               intent.setAction(Intent.ACTION_GET_CONTENT);
               startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
           }
       });
    }

    private void showProgressBar(String message) {
        pd.setMessage(message);
        pd.setCancelable(false);
        pd.show();
    }

    @NonNull
    private File createDir() {
        return new File(Environment.getExternalStorageDirectory()
                                + "/LipiEditor");
    }

    private FirebaseDatabase setUpFirebase() {
        mStorageRef = FirebaseStorage.getInstance().getReference();
        return FirebaseDatabase.getInstance();
    }

    private void init() {
        edt = (EditText) findViewById(R.id.myEditText);
        btn = (Button)findViewById(R.id.btn_upload_img);
        fabSave = (Button)findViewById(R.id.fab_save_btn);
        fabPreview = (Button)findViewById(R.id.fab_preview_btn);
        sp = new SharedPrefsUtils();
        pd = new ProgressDialog(this);
        files = new ArrayList<>();
    }

    private void askForPermission() {

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);

    }

    private boolean checkIfPermissionGranted() {

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            return false;
        }
        else
            return true;
    }

    private int giveNumber(String example) {
        String answer = example.substring(example.indexOf("(")+1,example.indexOf(")"));
        int a = Integer.parseInt(answer);
        return a;
    }

    private void uploadToFirebase() {
        StorageReference imagesRef = mStorageRef.child("images");
        File mediaStorageDir = createDir();
        final List<File> fls = Arrays.asList(mediaStorageDir.listFiles());
        int count =0;
        for (final File fle: fls)
        {
            count++;
            Uri file = Uri.fromFile(new File(mediaStorageDir.getAbsoluteFile()+"/"+fle.getName()));
            StorageReference riversRef = imagesRef.child(fle.getName());
            final int finalCount = count;

            riversRef.putFile(file)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            // Get a URL to the uploaded content
                           // Uri downloadUrl = taskSnapshot.getDownloadUrl();
                            Log.d("Upload","Successful");
                            if(finalCount ==fls.size()) {
                                pd.cancel();
                                Toast.makeText(MainActivity.this, "Synced successfully", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            // Handle unsuccessful uploads
                            // ...
                        }
                    });
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_PICTURE && resultCode == RESULT_OK && data != null && data.getData() != null) {

            Uri uri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),uri);
                insertImageToCurrentSelection(bitmap);
                boolean granted = checkIfPermissionGranted();
                if(granted)
                saveInfo(uri, bitmap);
                else
                    askForPermission();
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveInfo(Uri uri, Bitmap bitmap) {
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.d(TAG,
                    "Error creating media file, check storage permissions: ");// e.getMessage());
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            BitmapDrawable drawable1 = new BitmapDrawable(getResources(), bitmap);
            drawable1.setBounds(0, 0, 100, 100);
            bitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, false);
            bitmap.compress(Bitmap.CompressFormat.PNG, 50, fos);
            fos.close();

        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
    }

    private  File getOutputMediaFile(){
        File mediaStorageDir = createDir();

        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        File mediaFile;
        mImageName="File("+edt.getSelectionStart()+")"+".jpg";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    public void insertImageToCurrentSelection(Bitmap Bitmap) {
        BitmapDrawable drawable = new BitmapDrawable(getResources(), Bitmap);
        drawable.setBounds(0, 0, 100, 100);
        int selectionCursor = edt.getSelectionStart();
        edt.getText().insert(selectionCursor, "$");
        selectionCursor = edt.getSelectionStart();
        SpannableStringBuilder builder = new SpannableStringBuilder(edt.getText());
        builder.setSpan(new ImageSpan(drawable), selectionCursor - ".".length(), selectionCursor,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        edt.setText(builder);
        edt.setSelection(selectionCursor);
    }


    public void insertImageToCurrentSelection(Bitmap Bitmap, int pos) {
        BitmapDrawable drawable = new BitmapDrawable(getResources(), Bitmap);
        drawable.setBounds(0, 0, 100, 100);
        int selectionCursor = pos;
        SpannableStringBuilder builder = new SpannableStringBuilder(edt.getText());
        builder.setSpan(new ImageSpan(drawable), selectionCursor - ".".length(), selectionCursor,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        edt.setText(builder);
        edt.setSelection(selectionCursor);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
