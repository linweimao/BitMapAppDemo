package com.lwm.bitmapappdemo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

// 用来优化图片
public class ImageResize {
    // int maxW,int maxH ———— 限定像素点的宽和高
    // reusable：内存复用位置
    public static Bitmap resizeBitmap(Context context, int id, int maxW, int maxH, boolean hasAlpha,Bitmap reusable) {
        Resources resources = context.getResources();
        BitmapFactory.Options options = new BitmapFactory.Options();
//        Bitmap bitmap = BitmapFactory.decodeResource(resources,R.drawable.logo);
        // 只解码出相关参数信息(宽、高信息等)，不会真实产生图片
        options.inJustDecodeBounds = true; // 解码动作开关（开）
        BitmapFactory.decodeResource(resources, id, options);
        // 根据宽、高进行缩放
        int w = options.outWidth;  // 图片真实输出的宽
        int h = options.outHeight; // 图片真实输出的高
        // 设置缩放系数(见图片)
        options.inSampleSize = calcuteInSampleSize(w, h, maxW, maxH);
        if (!hasAlpha) {
            // 不需要透明度，使用 RGB_565(5+6+5=16位)
            options.inPreferredConfig = Bitmap.Config.RGB_565;
        }
        options.inJustDecodeBounds = false; // 处理完进行关闭
        // 设置成能复用
        options.inMutable=true;
        // 设置复用需要的内存位置
        options.inBitmap = reusable;
        // 返回设置过后的真实图片
        return BitmapFactory.decodeResource(resources, id, options);
    }

    // 计算缩放系数
    // w,h：原图的宽和高
    // maxW,maxH：缩放后的宽和高
    private static int calcuteInSampleSize(int w, int h, int maxW, int maxH) {
        int inSampleSize = 1;
        if (w > maxW && h > maxH) {
            inSampleSize = 2;
            while ((w / inSampleSize > maxW) && (h / inSampleSize > maxH)) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
