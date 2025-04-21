package com.jzzh.paddleocr.ocr;

import android.graphics.Bitmap;

import java.util.ArrayList;

/**
 * OCR识别结果回调接口
 * 用于接收OCR识别的成功或失败结果
 */
public abstract class OnImagePredictorListener {
    /**
     * OCR识别成功回调方法
     * @param result 识别结果文本
     * @param list 识别结果详细信息列表
     * @param bitmap 处理后的图像
     */
    public abstract void success(String result, ArrayList<OcrResultModel> list, Bitmap bitmap);

    /**
     * OCR识别失败回调方法
     * @param code 错误代码
     * @param message 错误信息
     */
    public void failure(int code, String message) {
        // 默认空实现，子类可以根据需要重写
    }
}