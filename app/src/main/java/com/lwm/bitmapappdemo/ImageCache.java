package com.lwm.bitmapappdemo;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import android.util.LruCache;

import com.lwm.bitmapappdemo.disk.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ImageCache {

    public static final String TAG = "ImageCache";

    private static ImageCache instance;
    private Context context;

    public static ImageCache getInstance() {
        if (null == instance) {
            synchronized (ImageCache.class) {
                if (null == instance) {
                    instance = new ImageCache();
                }
            }
        }
        return instance;
    }

    // 内存缓存
    private LruCache<String, Bitmap> memoryCache;
    // 磁盘缓存
    private DiskLruCache diskLruCache;

    BitmapFactory.Options options = new BitmapFactory.Options();

    // 复用池
    public static Set<WeakReference<Bitmap>> reuseablePool;

    // 参数 dir 就是最后存储的磁盘缓存的路径
    public void init(Context context, String dir) {

        this.context = context.getApplicationContext();
        // synchronizedSet：带锁的集合
        reuseablePool = Collections.synchronizedSet(new HashSet<WeakReference<Bitmap>>());

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();// 虚拟机提供的可用内存(APP可用的内存)
        memoryCache = new LruCache<String, Bitmap>(memoryClass / 8 * 1024 * 1024) {// 1/8的可用内存用于图片的缓存（APP可用的内存里取1/8）

            /*
             *  return    value占用的内存大小
             */

            @Override
            // sizeOf方法用于计算图片大小
            protected int sizeOf(String key, Bitmap value) {
                // 为了兼容 Android 3.0以前（19以前和以后）
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    // 大于 19 以后（可以压缩复用）
                    return value.getAllocationByteCount();
                }
                // 19 以前复用内存只能使用同样大小的图片才能复用内存
                return value.getByteCount();
            }

            @Override
            // LruCache放满后一些数据会挤出(在 oldValue 挤出)
            // newValue：队头 放入的对象
            // oldValue：队尾 挤出的对象
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                // oldValue需要放入到复用池
                if (oldValue.isMutable()) {
                    // 用 WeakReference 与 引用队列(ReferenceQueue)进行关联
                    reuseablePool.add(new WeakReference<Bitmap>(oldValue, getReferenceQueue()));
                } else {
                    oldValue.recycle();
                }
            }
        };

        try {
            // 手机上打开一个目录
            // 参数三：1 个文件
            diskLruCache = DiskLruCache.open(new File(dir), 1, 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 引用队列
    ReferenceQueue referenceQueue;
    Thread clearReferenceQueue;
    boolean shutDown;

    // 用于主动监听GC的API，加快回收（可以兼容不同的Android版本）
    private ReferenceQueue<Bitmap> getReferenceQueue() {
        if (null == referenceQueue){
            referenceQueue = new ReferenceQueue<Bitmap>();
            // 单开一个线程，去扫描引用队列中GC扫到的内容，交到native层去释放
            clearReferenceQueue = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!shutDown){
                        try {
                            // remove带阻塞功能的
                            Reference<Bitmap> reference = referenceQueue.remove();
                            Bitmap bitmap = reference.get();
                            if (null!=bitmap && !bitmap.isRecycled()){
                                bitmap.recycle(); // 提高加快回收动作（转到 native 层进行回收）
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            clearReferenceQueue.start();
        }
        return referenceQueue;
    }

    /**
     * 加入内存缓存
     */
    public void putBitmapToMemeory(String key,Bitmap bitmap){
        memoryCache.put(key,bitmap);
    }

    public Bitmap getBitmapFromMemory(String key){
        return memoryCache.get(key);
    }

    public void clearMemoryCache(){
        memoryCache.evictAll();
    }

    // 获取复用池中的内容
    public Bitmap getReuseable(int w,int h,int inSampleSize){
        // 3.0以下不理会
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.HONEYCOMB){
            return null;
        }
        Bitmap reuseable = null;
        // 通过迭代器
        Iterator<WeakReference<Bitmap>> iterator = reuseablePool.iterator();
        while (iterator.hasNext()){
            Bitmap bitmap = iterator.next().get();
            if (null!=bitmap){
                // 可以复用
                if (checkInBitmap(bitmap,w,h,inSampleSize)){
                    reuseable = bitmap;
                    iterator.remove();
                    Log.i(TAG, "复用池中找到了");
                    break;
                }else {
                    iterator.remove();
                }
            }
        }
        return reuseable;
    }

    // 检测能不能复用
    private boolean checkInBitmap(Bitmap bitmap, int w, int h, int inSampleSize) {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.KITKAT){
            return bitmap.getWidth()==w && bitmap.getHeight()==h && inSampleSize == 1;
        }
        // 缩放系数大于 1 的就是可以复用
        if (inSampleSize>=1){
            w/=inSampleSize;
            h/=inSampleSize;
        }
        int byteCount = w*h*getPixelsCount(bitmap.getConfig());
        return byteCount<=bitmap.getAllocationByteCount();
    }

    /*
     *  用于获取像素点的不同的格式所需要的字节数
     */
    private int getPixelsCount(Bitmap.Config config) {
        if (config == Bitmap.Config.ARGB_8888){
            return 4;
        }
        return 2;
    }

    // 磁盘缓存的处理
    /*
     *  加入磁盘缓存
     */
    public void putBitMapToDisk(String key,Bitmap bitmap){
        DiskLruCache.Snapshot snapshot = null;
        OutputStream os = null;
        try {
            snapshot = diskLruCache.get(key);
            // 如果缓存中已经有这个文件   不理他
            if (null==snapshot){
                // 如果没有这个文件，就生成这个文件
                DiskLruCache.Editor editor = diskLruCache.edit(key);
                if (null!=editor){
                    os = editor.newOutputStream(0);
                    bitmap.compress(Bitmap.CompressFormat.JPEG,50,os);
                    editor.commit();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (null!=snapshot){
                snapshot.close();
            }
            if (null!=os){
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     *  从磁盘缓存中取
     */
    public Bitmap getBitmapFromDisk(String key,Bitmap reuseable){
        DiskLruCache.Snapshot snapshot = null;
        Bitmap bitmap = null;
        try {
            snapshot = diskLruCache.get(key);
            if (null==snapshot){
                return null;
            }
            // 获取文件输入流，读取 bitmap
            InputStream is = snapshot.getInputStream(0);
            // 解码个图片，写入
            options.inMutable = true;
            options.inBitmap = reuseable;
            bitmap = BitmapFactory.decodeStream(is,null,options);
            if (null != bitmap){
                memoryCache.put(key,bitmap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (null!=snapshot){
                snapshot.close();
            }
        }
        return bitmap;
    }

}
