package com.qsoft.paddleocr.ocr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import java.io.IOException;

public class OCRPredictor {
    public final int REQUEST_LOAD_MODEL = 0;
    public final int REQUEST_RUN_MODEL = 1;
    public final int RESPONSE_LOAD_MODEL_SUCCESSED = 0;
    public final int RESPONSE_LOAD_MODEL_FAILED = 1;
    public final int RESPONSE_RUN_MODEL_SUCCESSED = 2;
    public final int RESPONSE_RUN_MODEL_FAILED = 3;
    public final int RESPONSE_RUN_MODEL_NO_FILE = 4;

    //    private final String assetModelDirPath = "models/bank";
//    private final String assetlabelFilePath = "labels/ppocr_keys_bank.txt";
    private final String assetModelDirPath = "models/ocr";
    private final String assetlabelFilePath = "labels/ppocr_keys_v1.txt";

    protected Handler sender = null; // Send command to worker thread
    protected HandlerThread worker = null; // Worker thread to load&run model
    protected volatile Predictor predictor = null;

    private OnImagePredictorListener listener;
    @SuppressLint("StaticFieldLeak")
    private static OCRPredictor ocrPredictor;
    private Context context;

    public static OCRPredictor getInstance(Context context) {
        if (ocrPredictor == null) {
            ocrPredictor = new OCRPredictor(context);
        }
        return ocrPredictor;
    }


    public OCRPredictor(Context context) {
        this.context = context;
        init();
    }

    /**
     *
     */
    private void init() {
        worker = new HandlerThread("Predictor Worker");
        worker.start();
        sender = new Handler(worker.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REQUEST_LOAD_MODEL:
                        // Load model and reload test image
                        if (!onLoadModel(context)) {
                            predictor = null;
                            Log.d("OCR", "加载模型失败！");
                        } else {
                            Log.d("OCR", "加载模型成功！");
                        }
                        break;
                    case REQUEST_RUN_MODEL:
                        Bundle bundle = msg.getData();
                        if (predictor == null) {
                            receiver.sendEmptyMessage(RESPONSE_LOAD_MODEL_FAILED);
                        } else {
                            // Run model if model is loaded
                            if (!TextUtils.isEmpty(bundle.getString("imagePath"))) {
                                if (!onRunModel(bundle.getString("imagePath"))) {
                                    receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_FAILED);
                                } else {
                                    receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_SUCCESSED);
                                }
                            } else {
                                ImageBinder imageBinder = (ImageBinder) bundle.getBinder("bitmap");
                                if (imageBinder != null) {
                                    Bitmap bitmap = imageBinder.getBitmap();
                                    if (!onRunModel(bitmap)) {
                                        receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_FAILED);
                                    } else {
                                        receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_SUCCESSED);
                                    }
                                } else {
                                    StringBinder stringBinder = (StringBinder) bundle.getBinder("base64");
                                    if (stringBinder != null) {
                                        String str = stringBinder.getStr();
                                        if (!onRunModelBase64(str)) {
                                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_FAILED);
                                        } else {
                                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_SUCCESSED);
                                        }
                                    } else {
                                        receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_NO_FILE);
                                    }
                                }

                            }
                        }
                        break;
                }
            }
        };
        sender.sendEmptyMessage(REQUEST_LOAD_MODEL);
    }

    @SuppressLint("HandlerLeak")
    private Handler receiver = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
//                case RESPONSE_LOAD_MODEL_SUCCESSED:
//                    sender.sendEmptyMessage(REQUEST_RUN_MODEL);
//                    break;
                case RESPONSE_LOAD_MODEL_FAILED:
                    listener.failure(0, "加载模型失败！");
                    break;
                case RESPONSE_RUN_MODEL_SUCCESSED:
                    listener.success(predictor.outputResult, predictor.outputResultList, predictor.outputImage);
                    break;
                case RESPONSE_RUN_MODEL_FAILED:
                    listener.failure(1, "运行模型失败！");
                    break;
                case RESPONSE_RUN_MODEL_NO_FILE:
                    listener.failure(2, "运行模型失败，请传入待识别图片！");
                    break;
                default:
                    break;
            }
        }
    };

    public void predictor(final String imagePath, final OnImagePredictorListener listener) {
        this.listener = listener;

        Message message = new Message();
        message.what = REQUEST_RUN_MODEL;
        Bundle bundle = new Bundle();
        bundle.putString("imagePath", imagePath);
        message.setData(bundle);

        sender.sendMessage(message);
    }

    public void predictorBitMap(final Bitmap bitmap, final OnImagePredictorListener listener) {
        this.listener = listener;

        Message message = new Message();
        message.what = REQUEST_RUN_MODEL;
        Bundle bundle = new Bundle();
        bundle.putBinder("bitmap", new ImageBinder(bitmap));
        message.setData(bundle);

        sender.sendMessage(message);
    }

    public void predictorBase64(final String base64, final OnImagePredictorListener listener) {
        this.listener = listener;

        Message message = new Message();
        message.what = REQUEST_RUN_MODEL;
        Bundle bundle = new Bundle();
        bundle.putBinder("base64", new StringBinder(base64));
        message.setData(bundle);

        sender.sendMessage(message);
    }


    private static class ImageBinder extends Binder {
        private Bitmap bitmap;

        public ImageBinder(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        Bitmap getBitmap() {
            return bitmap;
        }
    }


    private static class StringBinder extends Binder {
        private String str;

        public StringBinder(String str) {
            this.str = str;
        }

        String getStr() {
            return str;
        }
    }


    /**
     * call in onCreate, model init
     *
     * @return
     */
    private boolean onLoadModel(Context context) {
        if (predictor == null) {
            predictor = new Predictor();
        }
        return predictor.init(context, assetModelDirPath, assetlabelFilePath);
    }

    private boolean onRunModel(String imagePath) {
        try {
            ExifInterface exif = null;
            exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            Log.i("OCR", "rotation " + orientation);
            Bitmap image = BitmapFactory.decodeFile(imagePath);
            image = Utils.rotateBitmap(image, orientation);

            predictor.setInputImage(image);
            return predictor.isLoaded() && predictor.runModel();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean onRunModel(Bitmap image) {
        try {
            predictor.setInputImage(image);
            return predictor.isLoaded() && predictor.runModel();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean onRunModelBase64(String base64) {
        try {
            byte[] bitmapByte = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapByte, 0, bitmapByte.length);
            predictor.setInputImage(bitmap);
            return predictor.isLoaded() && predictor.runModel();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 卸载模型
     */
    private void onUnloadModel() {
        if (predictor != null) {
            predictor.releaseModel();
            predictor = null;
        }
    }

    /**
     * 退出线程
     */
    private void quit() {
        if (worker != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                worker.quitSafely();
            } else {
                worker.quit();
            }
        }
    }

    /**
     * 销毁
     */
    public void onDestroy() {
        onUnloadModel();
        quit();
        ocrPredictor = null;
    }

}