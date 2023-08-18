package com.example.cppnguide;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NavigationCamera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "Navigation";
    private static final int REQUEST_CODE_SPEECH_INPUT = 1090;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRGBA = new Mat();
    private ObjectDetector objectDetector;
    private List<Location> Locations;
    private TextView score;
    private TextView index;
    private TextView location;
    private int count = 0;
    private String destination;
    private List<Location> path;
    private String []result = new String[2];
    private int destination_index = 0;
    private int currentIndex = 0;
    private boolean navigating = false;
    private TextView navigation;
    private TextToSpeech textToSpeech;
    private TextView steps_estimation;
    private int lastDirectionIndex = 0;
    private boolean lastDirection = false;
    private TextView direction;
    private boolean speakdirection = false;
    private String next1;
    private ImageView nav_map_view;

    private Button speak;
    static {
        System.loadLibrary("cppnguide");
    }

    private int lastIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_navigation);
        cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.camera_view_2);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(NavigationCamera.this);
        score = findViewById(R.id.score);
        index = findViewById(R.id.index);
        location = findViewById(R.id.location);
        speak = findViewById(R.id.speak);
        navigation = findViewById(R.id.destination);
        direction = findViewById(R.id.direction);
        Locations = new ArrayList<>();
        path = new ArrayList<>();
        steps_estimation = findViewById(R.id.step_estimation);
        nav_map_view = findViewById(R.id.nav_mapView);

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.speak("Navigation.",TextToSpeech.QUEUE_FLUSH,null,null);
            }
        });

        if(Global.OBJECT_DETECTION) {

            LoadFile();
            Toast.makeText(getBaseContext(), "" + getBaseContext().getExternalFilesDir(null).getAbsolutePath(), Toast.LENGTH_LONG).show();

            try {
                objectDetector = new ObjectDetector(getAssets(), "ssd_mobilenet.tflite", "labelmap.txt", 300);
                Toast.makeText(getBaseContext(), "Loaded object detection Model", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getBaseContext(), "Not loaded object detection Model", Toast.LENGTH_SHORT).show();
            }
        }
        if(Global.MAP_VIEW_NAVIGATION){
            Toast.makeText(getBaseContext(),Global.APP_BASE_STORAGE+"",Toast.LENGTH_LONG).show();
            File image = new File(Global.APP_BASE_STORAGE+"/mapImage.png");
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(),bmOptions);
            bitmap = Bitmap.createScaledBitmap(bitmap,1000,1000,true);
            nav_map_view.setImageBitmap(bitmap);
        }
        else{
            Toast.makeText(getBaseContext(),"Check Display Map(Navigation) and Auto use Latest Map to view Map in Navigation.",Toast.LENGTH_LONG).show();
        }

        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            onPause();
                Intent intent
                        = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                        Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to text");
                try {
                    startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
                }
                catch (Exception e) {
                    Toast.makeText(getBaseContext(), " " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data)
    {
        onResume();
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                destination = result.get(0);
                boolean hasRoom = false;
                destination_index = 0;
                for(int i = 0 ;i < Locations.size(); i++ ){
                    if((Locations.get(i).getName()).toLowerCase().equals(destination.toLowerCase())){
                        destination_index = i;
                        destination = Locations.get(i).getName();
                        hasRoom = true;
                        break;
                        }
                    }
                if(hasRoom){
                   // Toast.makeText(getBaseContext(),"Room is found! Navigating to room - "+ destination,Toast.LENGTH_LONG).show();
                    navigating = true;
                    textToSpeech = new TextToSpeech(getBaseContext(), new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int i) {
                            textToSpeech.setLanguage(Locale.US);
                            textToSpeech.speak("Navigating to "+destination, TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    });
                }
                else{
                    textToSpeech = new TextToSpeech(getBaseContext(), new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int i) {
                            textToSpeech.setLanguage(Locale.US);
                            textToSpeech.speak("Place not found. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    });
                    destination = "";
                }
            }
        }
    }
    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    cameraBridgeViewBase.setCameraPermissionGranted();
                    cameraBridgeViewBase.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };
    @Override
    public void onResume(){
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, baseLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRGBA = new Mat(height,width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mRGBA.release();

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRGBA = inputFrame.rgba();
        if(navigating==true) {
            if (count % 10 == 0) {
                Mat resizeimage = new Mat();
                Size scaleSize = new Size(640, 480);
                Imgproc.resize(mRGBA, resizeimage, scaleSize);
                Imgproc.cvtColor(resizeimage, resizeimage, Imgproc.COLOR_RGBA2GRAY);
                String imageName = getBaseContext().getExternalFilesDir(null).getAbsolutePath() + "/query.jpg";
                Imgcodecs.imwrite(imageName, resizeimage);
                String res = navigation("" + getBaseContext().getExternalFilesDir(null).getAbsolutePath());
                result = res.split(",");
                speakdirection = false;

                if (Math.abs(currentIndex - Integer.parseInt(result[0])) <= 10 ) {
                    currentIndex = Integer.parseInt(result[0]);
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            navigation.setText("Navigating: "+destination +" ("+ destination_index+")" );
                            index.setText("Index: " + result[0]);
                            score.setText("Score: " + result[1]);
                            location.setText("Location: " + (int) Locations.get(lastIndex).getX() + "," + (int) Locations.get(lastIndex).getY());
                            steps_estimation.setText("Remaining Steps: "+(destination_index - currentIndex));
                            if((destination_index-currentIndex)!=0 && (destination_index-currentIndex)%2==0 && lastIndex!=currentIndex){
                                textToSpeech = new TextToSpeech(getBaseContext(), new TextToSpeech.OnInitListener() {
                                    @Override
                                    public void onInit(int i) {
                                        textToSpeech.setLanguage(Locale.US);
                                        textToSpeech.speak((destination_index-currentIndex)+" steps ", TextToSpeech.QUEUE_FLUSH, null, null);
                                    }
                                });
                                lastIndex = currentIndex;
                            }
                        }
                    });

                    if((destination_index-currentIndex)!=0) {
                        try {

                            //direction.setText("Next Location = " +Locations.get(currentIndex + 1).getDirection()+"-"+Locations.get(currentIndex + 2).getDirection());
                            if ((destination_index!=currentIndex+1 || destination_index!=currentIndex+2)  && Locations.get(currentIndex + 1).getDirection().equals("right") || Locations.get(currentIndex+2).equals("right")) {
                                runOnUiThread(new Runnable() {
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void run() {
                                        textToSpeech = new TextToSpeech(getBaseContext(), new TextToSpeech.OnInitListener() {
                                            @Override
                                            public void onInit(int i) {
                                                textToSpeech.setLanguage(Locale.US);
                                                textToSpeech.speak("turn right", TextToSpeech.QUEUE_FLUSH, null, null);
                                                direction.setText("-------->>>");
                                            }
                                        });
                                    }
                                });

                            } else if ( (destination_index!=currentIndex+1 || destination_index!=currentIndex+2)  && Locations.get(currentIndex + 1).getDirection().equals("left") || Locations.get(currentIndex+2).equals("left")) {
                                runOnUiThread(new Runnable() {
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void run() {
                                        textToSpeech = new TextToSpeech(getBaseContext(), new TextToSpeech.OnInitListener() {
                                            @Override
                                            public void onInit(int i) {
                                                textToSpeech.setLanguage(Locale.US);
                                                textToSpeech.speak("turn left", TextToSpeech.QUEUE_FLUSH, null, null);
                                                direction.setText("<<<--------");
                                            }
                                        });
                                    }
                                });
                            }

                        } catch (Exception e) {

                        }
                    }
                    direction.setText("");

                    if(destination_index == currentIndex || currentIndex+1 == destination_index){
                        navigating = false;
                        runOnUiThread(new Runnable() {
                            @SuppressLint("SetTextI18n")
                            @Override
                            public void run() {
                                textToSpeech = new TextToSpeech(getBaseContext(), new TextToSpeech.OnInitListener() {
                                    @Override
                                    public void onInit(int i) {
                                        textToSpeech.setLanguage(Locale.US);
                                        textToSpeech.speak("Arrived at " +destination,TextToSpeech.QUEUE_FLUSH,null,null);
                                    }
                                });

                            }
                        });
                    }
                }
            }
            count++;
        }
        if(Global.OBJECT_DETECTION) {
            mRGBA = objectDetector.recognizeImage(mRGBA);
        }
        return mRGBA;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(cameraBridgeViewBase!= null){
            cameraBridgeViewBase.disableView();
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if(cameraBridgeViewBase!= null){
            cameraBridgeViewBase.disableView();
        }
    }
    private void LoadFile(){
        try{
            FileInputStream readData = new FileInputStream(getBaseContext().getExternalFilesDir(null).getAbsolutePath()+"/Locations.ser");
            ObjectInputStream readStream = new ObjectInputStream(readData);
            Locations = (ArrayList<Location>) readStream.readObject();
            readStream.close();
            Toast.makeText(getBaseContext(),"Loaded Serialized File",Toast.LENGTH_LONG).show();

        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    private native String navigation(String path);
}