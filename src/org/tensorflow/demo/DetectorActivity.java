/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import androidx.fragment.app.Fragment;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.icu.text.Edits;
import android.location.Location;
import android.location.LocationListener;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentPagerAdapter;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;
import org.tensorflow.demo.vision_module.Compass;
import org.tensorflow.demo.vision_module.InstanceHashTable;
//import org.tensorflow.demo.vision_module.InstanceTimeBuffer;
import org.tensorflow.demo.vision_module.MyCallback;
import org.tensorflow.demo.vision_module.MapRequest;
import org.tensorflow.demo.vision_module.MyGps;
import org.tensorflow.demo.vision_module.OcrRequest;
import org.tensorflow.demo.vision_module.SOTWFormatter;
import org.tensorflow.demo.vision_module.Sector;
import org.tensorflow.demo.vision_module.Service;
import org.tensorflow.demo.vision_module.Voice;
import org.tensorflow.demo.vision_module.senario;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity<Resultlabel> extends CameraActivity implements OnImageAvailableListener {


    private static final Logger LOGGER = new Logger();

    // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
    // must be manually placed in the assets/ directory by the user.
    // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
    // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
    // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise

    private static final String YOLO_MODEL_FILE = "file:///android_asset/my-tiny-yolo.pb";
    private static final int YOLO_INPUT_SIZE = 416;
    private static final String YOLO_INPUT_NAME = "input";
    private static final String YOLO_OUTPUT_NAMES = "output";
    private static final int YOLO_BLOCK_SIZE = 32;

    private enum DetectorMode {
        YOLO;
    }

    private static final DetectorMode MODE = DetectorMode.YOLO;

    // Minimum detection confidence to track a detection.
    public static final float MINIMUM_CONFIDENCE_YOLO = 0.5f;

    private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;

    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private long lastProcessingTimeMs1;
    private long lastDetectStartTime = 0;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private Bitmap cropSignBitmap = null;
    private float bitmapWidth = 0;
    private float bitmapHeight = 0;
    private int N = 5; // N * N ?????????

    private static final int BUFFERTIME = 3;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    public MultiBoxTracker tracker;

    private OverlayView trackingOverlay;

    private byte[] luminanceCopy;

    private BorderedText borderedText;

    private RequestQueue requestQueue;
    private LocationRequest locationRequest;
    private MyGps myGps;
    private Service service;
    private Voice voice;
    private Compass compass;
    private SOTWFormatter sotwFormatter;
    private Sector curSector = new Sector(false);
    private boolean dotFlag = false;
    private boolean yoloFirstStartFlag = false;

    public InstanceMatrix instanceMatrix = new InstanceMatrix();

    TensorFlowYoloDetector tensorFlowYoloDetector = new TensorFlowYoloDetector();
    TreeSet<String> arr;
    ArrayList<String> Deduplicated_labellist;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ImageButton detectedClass = findViewById(R.id.cameraclick);
        detectedClass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tensorFlowYoloDetector.clone == null) {
                    Log.e("???", "clone is empty? = null");
                } else {
                    // TreeSet?????? ????????? ????????????.
                    arr = new TreeSet<>(tensorFlowYoloDetector.clone);    // treeset??? labellist??? ??????
                    Deduplicated_labellist = new ArrayList<String>(arr); //??????????????? treeset??? ????????????
                    for (String i : Deduplicated_labellist) { //for?????? ?????? ????????????
                        System.out.println("?????? ??? = " + i);
                    }
                }
                String front = "";
                for (int i = 0; i < Deduplicated_labellist.size(); i++) {
                    front += Deduplicated_labellist.get(i) + "  ";
                }
                voice.TTS("?????????" + front + "?????? ????????????.");
//                Toast.makeText(getApplicationContext(), Deduplicated_images, Toast.LENGTH_SHORT).show();
//                LOGGER.i("%s  %s", "??????????????? ?????? ??? : ", tensorFlowYoloDetector.hangul_class);
//                voice.TTS("?????????" + tensorFlowYoloDetector.hangul_class + " ??? ????????????.");
            }
        });

        // 5 * 5 ????????? InstanceBuffer ?????????
        instanceMatrix.initMat(5, 5);


        // GPS??? ??????????????? On Dialog
        createLocationRequest();
        turn_on_GPS_dialog();


        //Gps
        myGps = new MyGps(DetectorActivity.this, locationListener);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                myGps.startGps(DetectorActivity.this.service);
                Log.e("thread", "run: start");
            }
        }, 0);

        //Compass
        compass = new Compass(this);
        sotwFormatter = new SOTWFormatter(this); // ?????? ??????,,????????? ?????? N,NW ..????????? ??????..
        Compass.CompassListener cl = getCompassListener();
        compass.setListener(cl);

        // Voice
        voice = new Voice(this, null);

        // API Server
        requestQueue = Volley.newRequestQueue(DetectorActivity.this);  // ?????? ???

        // Service
        service = new Service();

    }


    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        detector = TensorFlowYoloDetector.create(
                getAssets(),
                YOLO_MODEL_FILE,
                YOLO_INPUT_SIZE,
                YOLO_INPUT_NAME,
                YOLO_OUTPUT_NAMES,
                YOLO_BLOCK_SIZE);

        int cropSize = YOLO_INPUT_SIZE;

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
//    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
//
//    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            //tracker.drawDebug(canvas);
                        }
                    }
                });

        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        if (!isDebug()) {
                            return;
                        }

                        final Vector<String> lines = new Vector<String>();

                        lines.add("");
                        lines.add("");
                        lines.add("Compass: " + sotwFormatter.format(service.getAzimuth()));
                        lines.add("");
                        lines.add("GPS");
                        lines.add(" Latitude: " + service.getLatitude());
                        lines.add(" Longitude: " + service.getLongitude());
                        lines.add("");
                        lines.add("Src Station: " + service.getSource_Station());
                        lines.add("Dst Station: " + service.getDest_Station());
                        lines.add("");

                        borderedText.drawLines(canvas, 10, canvas.getHeight() - 100, lines);
                    }
                });
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
        tracker.onFrame(
                previewWidth,
                previewHeight,
                getLuminanceStride(),
                sensorOrientation,
                originalLuminance,
                timestamp);
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @TargetApi(Build.VERSION_CODES.N)
                    @Override
                    public void run() {
                        if (!DetectorActivity.this.yoloFirstStartFlag) {
                            DetectorActivity.this.yoloFirstStartFlag = true;
                            voice.TTS("?????? ??????! Vision, ?????? ???????????????.");
                        }
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();

                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        DetectorActivity.this.lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                        if (bitmapHeight == 0 || bitmapWidth == 0) {
                            DetectorActivity.this.bitmapHeight = croppedBitmap.getHeight();
                            DetectorActivity.this.bitmapWidth = croppedBitmap.getWidth();
                            Log.e("bitmapSize", "width: " + bitmapWidth);
                            Log.e("bitmapSize", "height: " + bitmapWidth);
                        }
                        // Canvas On/Off ?????? ???????????????
                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_YOLO;

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();


// ???????????? ????????? ????????? ???????????? ?????????
                        for (final Classifier.Recognition resultb : results) {
                            // dot block??? ??????????????? check
                            Classifier.Recognition result = resultb.clone();
                            if (result.getIdx() == 0) dotFlag = true;
                            curSector.setCurSector(result.getIdx());
                            final RectF location = result.getLocation();

                            instanceMatrix.putRecog(result);

                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }
                        DetectorActivity.this.lastProcessingTimeMs1 += SystemClock.uptimeMillis() - startTime;


                        tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                        trackingOverlay.postInvalidate();

                        requestRender();
                        computingDetection = false;
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onSetDebug(final boolean debug) {
        detector.enableStatLogging(debug);
    }

//--Listener----------------------------------------------------------------------------------------------------------------------------------------


    // GPS Location ?????? ????????? ????????? ??????
    final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {

            service.setLatitude(location.getLatitude());
            service.setLongitude(location.getLongitude());

            Log.e("t", "service ??????: " + service.getLatitude());
            Log.e("t", "service ??????: " + service.getLongitude() + "\n..\n");

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.e("t", "startGps: ????????????");
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.e("t", "startGps: ????????????");
            //myGps.startGps();
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.e("t", "startGps: ????????????");
        }
    };

    public RecognitionListener getRecognitionListner(final MyCallback myCallback) {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                Toast.makeText(getApplicationContext(), "??????????????? ???????????????.", Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {

            }

            @Override
            public void onError(int i) {
                voice.TTS("?????? ??????. ?????? ???????????????");
                String message;

                switch (i) {

                    case SpeechRecognizer.ERROR_AUDIO:
                        message = "????????? ??????";
                        break;

                    case SpeechRecognizer.ERROR_CLIENT:
                        message = "??????????????? ??????";
                        break;

                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        message = "???????????????";
                        break;

                    case SpeechRecognizer.ERROR_NETWORK:
                        message = "???????????? ??????";
                        break;

                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        message = "????????? ????????????";
                        break;

                    case SpeechRecognizer.ERROR_NO_MATCH:
                        message = "????????? ??????";
                        ;
                        break;

                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        message = "?????????";
                        break;

                    case SpeechRecognizer.ERROR_SERVER:
                        message = "????????????";
                        ;
                        break;

                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        message = "????????? ????????????";
                        break;

                    default:
                        message = "????????????";
                        break;
                }
                Log.e("GoogleActivity", "SPEECH ERROR : " + message);
            }

            @Override
            public void onResults(Bundle results) {
                myCallback.callbackBundle(results);
            }

            @Override
            public void onPartialResults(Bundle bundle) {

            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        };
    }


//--Function----------------------------------------------------------------------------------------------------------------------------------------

    /* ????????? ????????? ?????? */
    //?????? - ???(??? ???), ???(???) ???(???)
    public static String[] arrChoSungEng = {"k", "K", "n", "d", "D", "r", "m", "b", "B", "s", "S",
            "a", "j", "J", "ch", "c", "t", "p", "h"};

    //?????? - ???(??? ???), ???(???), ???(???)
    public static String[] arrJungSungEng = {
            "a", "e", "ya", "ae", "eo", "e", "yeo", "e", "o", "wa", "wae", "oe",
            "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    };

    //?????? - ???(??????), ???(???)
    public static String[] arrJongSungEng = {"", "k", "K", "ks", "n", "nj", "nh",
            "d", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "b", "bs", "s", "ss",
            "ng", "j", "ch", "c", "t", "p", "h"};

    //?????? ?????? - ???,???,???,???... (???,???,?????? ????????????(??????)?????? ???????????? ?????????????????? ?????????)
    public static String[] arrSingleJaumEng = {"r", "R", "rt", "s", "sw", "sg", "e", "E", "f",
            "fr", "fa", "fq", "ft", "fx", "fv", "fg", "a", "q", "Q", "qt", "t", "T", "d", "w", "W",
            "c", "z", "x", "v", "g"};

    //?????? ????????? ????????? ???????????? ?????????
    public String recognizeStation(String stt_Station) {
        String resultEng = "", targetStation = "";

        if (stt_Station.contains("???")) {
            targetStation = stt_Station.split("???")[0];
        } else targetStation = stt_Station;
        Log.e("11", "stepppp done.");

        if (targetStation.contains("???") || targetStation.contains("???")) targetStation = "??????";
        else if (targetStation.contains("???") || targetStation.contains("???")) targetStation = "??????";
        Log.e("?????? ?????????", "step done...");

        Log.e("????????????????", targetStation);
        return targetStation;
    }

    ;

    //?????? ???????????? ???????????? ?????????
    public String recognizeExit(String stt_Exit) {
        String exitNum = "", exitMatch = "";
        String targetExit = "1";

        if (stt_Exit.contains("???")) {
            exitNum = stt_Exit.split("???")[0];
        } else exitNum = stt_Exit; // ????????? srcExitNumber="3" or "???"
        //Log.e("?????? ?????????", exitNum);

        if (exitNum.matches("^[0-9]+$")) {
            Log.e("srcExitNumber", "?????????");
        } else {
            //Log.e("srcExitNumber", "?????????");
            exitMatch = exitNum;
            switch (exitMatch) {
                case "???":
                    targetExit = "1";
                    break;
                case "???":
                    targetExit = "2";
                    break;
                case "???":
                case "???":
                    targetExit = "3";
                    break;
                case "???":
                    targetExit = "4";
                    break;
                case "???":
                    targetExit = "5";
                    break;
                case "???":
                    targetExit = "6";
                    break;
                case "???":
                case "???":
                    targetExit = "7";
                    break;
                case "???":
                case "???":
                case "???":
                    targetExit = "8";
                    break;
                case "???":
                    targetExit = "9";
                    break;
                case "???":
                    targetExit = "10";
                    break;
            }
            exitNum = targetExit;
        }
        return exitNum;
    }

    ;

    private int initCompletedStatus = 0;


    // ???????????? ????????? ???????????? ???????????? ???, ?????? ?????? ??????!
    public void initService(int status, final MyCallback myCallback) {

        final RecognitionListener sourceStationVoiceListener;
        final RecognitionListener destStationVoiceListener;
        final RecognitionListener confirmVoiceListener;

        // ????????? ?????? ?????? ????????? -> ???, ????????? ????????? ??????, ???????????? ?????? or navigate ?????? ??????.
        confirmVoiceListener = getRecognitionListner(new MyCallback() {
            @Override
            public void callback() {

            }


            @Override
            public void callbackBundle(Bundle results) {
                String key = "";
                key = SpeechRecognizer.RESULTS_RECOGNITION;
                ArrayList<String> mResult = results.getStringArrayList(key);

                String answer = mResult.get(0);
                Log.e("v", "answer: " + answer);

                try {
                    Thread.sleep(2000);

                    if (answer.charAt(0) == '???' && answer.charAt(1) == '???')     // ????????? ?????? ????????????
                        DetectorActivity.this.initCompletedStatus = 0;
                    else if (answer.charAt(0) != '???' && answer.charAt(0) != '???' && answer.charAt(0) != '???') { //????????? ??????????????? ??????????????????
                        // ?????????, ???????????? ????????? ???????????? ????????????, ?????? ?????? ??????!
                        voice.TTS("?????? ????????? ???????????????.");
                    } else {
                        //????????? ??????????????? ???????????? ???????????? ??????????????? ????????????.
                        Log.e("v", "Result src & dst: " + service.getSource_Station() + " " + service.getDest_Station());
                        Toast.makeText(DetectorActivity.this, "????????? = " + service.getSource_Station() + "\n ????????? = " + service.getDest_Station(), Toast.LENGTH_SHORT).show();

                        DetectorActivity.this.initCompletedStatus = 0;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        // ????????? ?????????
        destStationVoiceListener = getRecognitionListner(new MyCallback() {
            @Override
            public void callback() {

            }

            @Override
            public void callbackBundle(Bundle results) {

                String key = "", stt_dstStation = "";

                key = SpeechRecognizer.RESULTS_RECOGNITION;
                ArrayList<String> mResult = results.getStringArrayList(key);
                stt_dstStation = mResult.get(0);
                stt_dstStation = recognizeStation(stt_dstStation);

                service.setDest_Station(stt_dstStation);
                Log.e("v", "End Station onResults: " + service.getDest_Station());


                try {
                    Thread.sleep(1000);
                    voice.TTS(service.getSource_Station() + "????????? ???????????? " +
                            service.getDest_Station() + "????????? ????????? ????????????? ???, ???????????? ??????????????????.");
                    voice.setRecognitionListener(confirmVoiceListener);
                    Thread.sleep(8200);
                    voice.STT();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });


        sourceStationVoiceListener = getRecognitionListner(new MyCallback() {
            @Override
            public void callback() {
            }

            @Override
            public void callbackBundle(Bundle results) {
                String key = "", stt_srcStation = "";

                key = SpeechRecognizer.RESULTS_RECOGNITION;
                ArrayList<String> mResult = results.getStringArrayList(key);
                stt_srcStation = mResult.get(0);
                stt_srcStation = recognizeStation(stt_srcStation);//???????????? ?????? ??????
                service.setSource_Station(stt_srcStation);
                Log.e("v", "Start Station onResults: " + service.getSource_Station()); //????????? ?????? ??? ??? ?????? ?????? ????????????

                DetectorActivity.this.initCompletedStatus = 1;

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                voice.TTS(senario.destStationString);
//
                voice.setRecognitionListener(destStationVoiceListener);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                voice.STT();
            }
        });
        ArrayList<RecognitionListener> ListenerArray = new ArrayList<RecognitionListener>(Arrays.asList(sourceStationVoiceListener,
                destStationVoiceListener, confirmVoiceListener));

        // init ??????
        try {
            voice.setRecognitionListener(ListenerArray.get(status));
            if (status == 4) {
                voice.TTS(service.getSource_Station() + "????????? ??????, " +
                        service.getDest_Station() + "???????????? ????????????? ???, ???????????? ??????????????????.");
                Thread.sleep(8200);
            } else {
                voice.TTS(senario.getI(status));
                Thread.sleep(2500);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        voice.STT();
    }


    private Compass.CompassListener getCompassListener() {
        return new Compass.CompassListener() {
            @Override
            public void onNewAzimuth(final float azimuth) {
                DetectorActivity.this.service.setAzimuth(azimuth);
            }
        };
    }

    Bitmap cropBitmap(Bitmap bitmap, RectF location) {
        return Bitmap.createBitmap(bitmap, (int) location.left, (int) location.top, (int) (location.right - location.left), (int) (location.bottom - location.top));
    }


    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            this.debug = !this.debug;
            requestRender();
            onSetDebug(debug);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {

            //????????? ?????? ?????? ?????????!
//      Bitmap bitmap;
//      if(cropSignBitmap == null)
//        bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.ocrtest);
//      else
//        bitmap = cropSignBitmap;
//
//        getOcrString(bitmap, new Response.Listener<JSONObject>() {
//        @Override
//        public void onResponse(JSONObject response) {
//          Log.e("h", "OCR Response: " + response.toString());
//          try {
//            voice.TTS(response.getString("text"));
//          } catch (JSONException e) {
//            e.printStackTrace();
//          }
//        }
//      });


            //  ???????????? ?????? ????????? ?????? ??????

            initService(initCompletedStatus, new MyCallback() {
                @Override
                public void callback() {
                    Log.e("n", "Navigate ??????");
                    voice.TTS(service.getSource_Station() + "?????? " + service.getDest_Station() + "?????? ?????? ????????? ???????????????.");
//                    service.setReadyFlag(true);
                }

                @Override
                public void callbackBundle(Bundle result) {

                }
            });

            //debugSangsuMapdata();

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


//    public void debugSangsuMapdata() {
//        service.setDest_Station("??????");
//        service.setSource_Station("??????");
//        service.setSource_Exit("2");
//        service.setDest_Exit("3");
//        getMapData_To_Service_From_Server("sangsu", new MyCallback() {
//            @Override
//            public void callback() {
//                Log.e("n", "Navigate ??????");
//                voice.TTS(service.getSource_Station() + "?????? " + service.getDest_Station() + "?????? ?????? ????????? ???????????????.");
//                service.setReadyFlag(true);
//            }
//
//            @Override
//            public void callbackBundle(Bundle results) {
//
//            }
//        });
//    }

    // GPS ???????????? ?????? alert dialog
    protected void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    //  GPS ?????? dialog ?????????
    protected void turn_on_GPS_dialog() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(DetectorActivity.this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        //GPS get??? ????????? (GPS??? ???????????? ??????)
        task.addOnFailureListener(DetectorActivity.this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(DetectorActivity.this,
                                0x1);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    } finally {

                        myGps.startGps(DetectorActivity.this.service);
                        // GPS??? ???????????? ?????? ?????????????????? ????????? ????????????
                        // GPS??? ?????????
                    }
                }
            }
        });
    }//turn_on_gps end


    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DetectorActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    0);
        }
        Log.d("compass", "start compass");
        compass.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        compass.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        compass.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d("compass", "stop compass");
        compass.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        voice.close();
    }

}
