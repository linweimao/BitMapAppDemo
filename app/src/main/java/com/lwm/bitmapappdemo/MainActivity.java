package com.lwm.bitmapappdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "linweimao";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageCache.getInstance().init(this, Environment.getExternalStorageDirectory()+"/dn");

        ListView listView = findViewById(R.id.listView);
        listView.setAdapter(new MyAdapter(this));
//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.logo);
        /*
            // 像素点的压缩
            Bitmap bitmap = ImageResize.resizeBitmap(getApplicationContext(),R.drawable.logo,80,80,false);
        */

        // 像素点的压缩+内存复用
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.logo);
        Bitmap bitmap2 = ImageResize.resizeBitmap(getApplicationContext(),R.drawable.logo,80,80,false,bitmap);
        i(bitmap2);
    }

    private void i(Bitmap bitmap) {
        Log.i(TAG, "lwm 图片："+bitmap.getWidth()+"X"+bitmap.getHeight()+
                "内存大小是："+bitmap.getByteCount());
    }

}