package com.lwm.bitmapappdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

public class MyAdapter extends BaseAdapter {

    private static final String TAG = "MyAdapter";
    
    private Context context;

    public MyAdapter(Context context) {
        this.context = context;
    }

    @Override
    public int getCount() {
        return 999;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (null == convertView) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item,null);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        /*
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.logo);
        holder.iv.setImageBitmap(bitmap);
        */

        // 三级缓存
        // 从内存
        Bitmap bitmap = ImageCache.getInstance().getBitmapFromMemory(String.valueOf(position));
        if (bitmap == null){ // 如果内存没数据，就去复用池找
            // 从复用池（复用池的东西一般不直接拿来用）
            Bitmap reuseable = ImageCache.getInstance().getReuseable(80,80,1);
            // 从磁盘
            // 磁盘拿到数据的话，就在复用池(reuseable)里复用存放
            bitmap = ImageCache.getInstance().getBitmapFromDisk(String.valueOf(position),reuseable);
            if (bitmap == null){ // 如果磁盘中也没缓存,就从网络下载
                // 从网络
                bitmap = ImageResize.resizeBitmap(context,R.drawable.logo,80,80,false,reuseable);
                ImageCache.getInstance().putBitmapToMemeory(String.valueOf(position),bitmap);
                ImageCache.getInstance().putBitMapToDisk(String.valueOf(position),bitmap);
                Log.i(TAG, "网络获取了图片");
            }else {
                Log.i(TAG, "磁盘获取了图片");
            }
        }else {
            Log.i(TAG, "内存获取了图片");
        }
        holder.iv.setImageBitmap(bitmap);
        return convertView;
    }

    class ViewHolder {
        ImageView iv;

        public ViewHolder(View view) {
            iv = view.findViewById(R.id.iv);
        }
    }


}
