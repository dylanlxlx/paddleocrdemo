package com.qsoft.paddleocr.ocr;

import android.graphics.Bitmap;

import java.util.ArrayList;

public abstract class OnImagePredictorListener {
    public abstract void success(String result, ArrayList<OcrResultModel> list, Bitmap bitmap);

    public void failure(int code, String message) {
    }
}