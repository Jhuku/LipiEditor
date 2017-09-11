package com.shuvam.texteditor;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.widget.EditText;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;

public class PreviewActivity extends AppCompatActivity {

    private EditText edtPreview;
    private String TAG = "HelloPreview";
    private StorageReference mStorageRef;
    private int pos = 0;
    private ArrayList<Bitmap> bmps;
    private int index=0;
    private int k=0;
    private ProgressDialog pd;
    private String editTextContent;
    private ArrayList<String> bitmapUris;
    private ArrayList<Integer> positions;
    private ArrayList<Integer> positionsOfBmps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        init();
        mStorageRef = FirebaseStorage.getInstance().getReference();
        showProgressBar();

        // Write a message to the database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("content");
        myRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                String value = dataSnapshot.getValue(String.class);
                Log.d(TAG, "Value is: " + value);
                edtPreview.setText(value);
                editTextContent = edtPreview.getText().toString();

                loadImages();
                //loadImagesFromLocal();
            }
            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    private void init() {
        edtPreview = (EditText)findViewById(R.id.edt_preview_text);
        bmps = new ArrayList<>();
        positions = new ArrayList<>();
        positionsOfBmps = new ArrayList<>();
        bitmapUris = new ArrayList<>();
        pd = new ProgressDialog(this);
    }

    private void showProgressBar() {
        pd.setMessage("Loading content");
        pd.setCancelable(false);
        pd.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
    private void loadImages() {
        for(int i=0; i<editTextContent.length(); i++)
        {
            if(editTextContent.charAt(i)=='$')
            {
                Log.d("Pos",""+i);
                positions.add(i);
                pos = i;

                mStorageRef.child("images/File("+(i+1)+").jpg").getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        bitmapUris.add(uri.toString());
                        Log.d("Bitmap got","Success: "+uri.toString());
                        k++;
                        if(k== occurrence())
                        {
                            endofallfun();
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("Getting Bitmap","Failed");
                    }
                });
            }
            }
    }
    private int occurrence() {
        int count =0;
        for(int i =0; i<editTextContent.length(); i++)
        {
            if(editTextContent.charAt(i)=='$')
                count++;
        }
        return count;
    }
    private void endofallfun() {
        Log.d("I am the end","of all");
        for (String a: bitmapUris)
        {
            Log.d("Uri: ",a);
            positionsOfBmps.add(giveNumber(a));
        }
        new LoadBitmapAsync().execute();
    }
    private class LoadBitmapAsync extends AsyncTask<Object, Object, ArrayList<Bitmap>> {
        @Override
        protected ArrayList<Bitmap> doInBackground(Object... strings) {
            try {
             for (index=0; index<bitmapUris.size(); index++)
             {
                 URL url = new URL(bitmapUris.get(index));
                 HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                 connection.setDoInput(true);
                 connection.connect();
                 InputStream input;
                 input = connection.getInputStream();
                 Bitmap myBitmap = BitmapFactory.decodeStream(input);
                 bmps.add(myBitmap);
             }
                return bmps;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(ArrayList<Bitmap> bitmap) {
            super.onPostExecute(bitmap);
            Log.d("Bitmap Load","Success: "+pos);
            int t =0;
            for(Bitmap bmpx: bitmap)
            {
                insertImageToCurrentSelection(bmpx,positionsOfBmps.get(t));
                t++;
            }
            pd.cancel();
        }
    }
    public void insertImageToCurrentSelection(Bitmap Bitmap, int pos) {

        BitmapDrawable drawable = new BitmapDrawable(getResources(), Bitmap);
        drawable.setBounds(0, 0, 100, 100);
        int selectionCursor = pos;
        SpannableStringBuilder builder = new SpannableStringBuilder(edtPreview.getText());
        builder.setSpan(new ImageSpan(drawable), selectionCursor - ".".length(), selectionCursor,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        edtPreview.setText(builder);
        edtPreview.setSelection(selectionCursor);
    }
    private int giveNumber(String example) {

        try {
            String url = URLDecoder.decode(example, "UTF-8");
            String answer = url.substring(url.indexOf("(")+1,url.indexOf(")"));
            int a = Integer.parseInt(answer);
            return a;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
