package com.example.cppnguide;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Size;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapCreationCamera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private Mat mRGBA, imageToSave;

    private static final String TAG = "OpenCVCamera";
    private CameraBridgeViewBase cameraBridgeViewBase;

    private TextView step_view;
    private TextView angle_view;

    private int angle = 0;
    private int stepCount = 0;
    private int prevStepCount =0;
    private boolean detected = false;
    private boolean consumed = false;
    private int canvasHeight = 1000,canvasWidth = 1000;


    private Mat prev;
    private int count = 0;
    private int imageCount = 0;

    private List roomDetection;

    private Boolean isRecording = false;
    private Button record;
    private String baseStorage;

    private ProgressBar pb;
    private Button add_room;
    private List<Integer> rotationVectors;
    private List<String> rooms;
    private boolean added_room = false;
    private int roomCount = 0;
    private TextView roomsView;
    private int num_image=0;
    private TextToSpeech textToSpeech;

    private Map<Integer,String> renameRooms = new Hashtable<Integer,String>();
    private int roomPointer = 0;

    private AlertDialog.Builder dialog ;
    private EditText taskEditText;
    private List<EditText> editTextsRenameRoom;

    private String step = "Right";
    private TextView step_timing;

    private ImageView mapView;

    private Bitmap bitmap;
    private Canvas canvas;


    private double current_x = 0,current_y = 0,prev_x = 0,prev_y = 0,prevAngle= 0;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_open_cvcamera);
        cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(MapCreationCamera.this);

        step_view = findViewById(R.id.step);
        angle_view = findViewById(R.id.angle);
        record = findViewById(R.id.Record);
        add_room =findViewById(R.id.addRoom);
        roomsView = findViewById(R.id.rooms);
        step_timing = findViewById(R.id.step2);

        rooms = new ArrayList<String>();
        editTextsRenameRoom = new ArrayList<EditText>();
        rotationVectors = new ArrayList<Integer>();
        add_room.setBackgroundColor(Color.GREEN);
        add_room.setVisibility(View.INVISIBLE);

        taskEditText = new EditText(this);
        dialog = new AlertDialog.Builder(MapCreationCamera.this);

        bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        prev_x = canvas.getWidth()/2;
        prev_y = canvas.getHeight()/2;


        mapView = findViewById(R.id.mapView);
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.speak("Map Creation.",TextToSpeech.QUEUE_FLUSH,null,null);
            }
        });

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        CreateMapAlgorithm cm = new CreateMapAlgorithm(getBaseContext().getExternalFilesDir(null).getAbsolutePath(),baseStorage,rotationVectors,rooms,num_image,stepCount);
                        cm.create();
                        num_image = 0;
                        stepCount = 0;
                        imageCount = 0;
                        renameRooms.clear();
                        editTextsRenameRoom.clear();
                        current_x = 0;
                        current_y = 0;
                        prev_x = canvas.getWidth()/2;
                        prev_y = canvas.getHeight()/2;
                        Toast.makeText(getBaseContext(),"Response: "+ cm.response,Toast.LENGTH_LONG).show();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        rooms.clear();
                        renameRooms.clear();
                        current_x = 0;
                        current_y = 0;
                        prev_x = canvas.getWidth()/2;
                        prev_y = canvas.getHeight()/2;
                        deleteRecursive(new File(baseStorage));
                        break;
                }
            }
        };

        DialogInterface.OnClickListener edit = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:

                        int count = 0;
                        for(int key : renameRooms.keySet()) {
                            try {
                                String str;
                                str = editTextsRenameRoom.get(count).getText().toString();
                                if(!str.equals("")) {
                                    rooms.set(key, str);
                                    //Toast.makeText(getBaseContext(), "renamed: index" + key + " :" + str, Toast.LENGTH_LONG).show();
                                }
                                count++;
                            }
                            catch (Exception e){};
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(MapCreationCamera.this);
                        builder.setMessage("Do you want to create the map?").setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show();

                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        num_image = 0;
                        stepCount = 0;
                        imageCount = 0;
                        current_x = 0;
                        current_y = 0;
                        prev_x = canvas.getWidth()/2;
                        prev_y = canvas.getHeight()/2;
                        rooms.clear();
                        renameRooms.clear();
                        editTextsRenameRoom.clear();
                        deleteRecursive(new File(baseStorage));
                        break;
                }
            }
        };

        add_room.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Paint paint = new Paint();
                paint.setColor(Color.GREEN);
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                paint.setStrokeWidth(5);
                paint.setAntiAlias(true);
                canvas.drawCircle((float)current_x,(float)current_y,2,paint);

                roomCount+=1;
                roomsView.setText("Destinations Added: "+(roomCount));
                added_room = true;
                textToSpeech.speak("Destination Added.",TextToSpeech.QUEUE_FLUSH,null,null);

            }
        });

        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isRecording){
                    onPause();

                    //count registered destination
                    for(int i=0;i<rooms.size();i++){
                        if(rooms.get(i)!="0"){
                            System.out.println(rooms.get(i));
                            renameRooms.put(i,rooms.get(i));
                        }
                    }
                    Context context = getBaseContext();
                    LinearLayout layout = new LinearLayout(context);
                    ScrollView scrollView = new ScrollView(context);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    scrollView.addView(layout);
                    //Toast.makeText(getBaseContext(),"size = "+roomCount,Toast.LENGTH_LONG).show();

                    for( int key : renameRooms.keySet()) {
                        //Toast.makeText(getBaseContext(),"index = "+key+",name = "+renameRooms.get(key),Toast.LENGTH_LONG).show();
                        final EditText titleBox = new EditText(context);
                        titleBox.setHint("Rename Location no. "+renameRooms.get(key));
                        editTextsRenameRoom.add(titleBox);
                        layout.addView(titleBox); // Notice this is an add method

                    }
                    if(roomCount>0) {
                        dialog.setTitle("Rename Registered Destination").setView(scrollView).setPositiveButton("Ok", edit).setNegativeButton("Cancel", edit).show();
                    }
                    else{
                        AlertDialog.Builder builder = new AlertDialog.Builder(MapCreationCamera.this);
                        builder.setMessage("Do you want to create the map?").setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show();

                    }
                    record.setBackgroundColor(Color.BLUE);
                    record.setText("Record");
                    isRecording = false;
                    add_room.setVisibility(View.INVISIBLE);

                }
                else{
                    onResume();
                    String timeStamp = new SimpleDateFormat("dd_MM_yyyy_HH_mm_SS").format(new Date());
                    File folder = new File( getBaseContext().getExternalFilesDir(null).getAbsolutePath()+
                            File.separator +"nGuideCPP"+  File.separator+ timeStamp);
                    boolean success = false;
                    if (!folder.exists()) {
                        success = folder.mkdirs();
                    }
                    baseStorage = getBaseContext().getExternalFilesDir(null).getAbsolutePath() + "/nGuideCPP/" + timeStamp  ;
                    File outFolder = new File(baseStorage + "/Images");
                    if (!outFolder.exists()) {
                        outFolder.mkdirs();
                    }
                    outFolder = new File(baseStorage + "/Map");
                    if (!outFolder.exists()) {
                        outFolder.mkdirs();
                    }

                    outFolder = new File(baseStorage + "/Files");
                    if (!outFolder.exists()) {
                        outFolder.mkdirs();
                    }

                    try{
                        rooms.clear();
                        rotationVectors.clear();
                    }catch(Exception e) {

                    }
                    count = 0;
                    roomCount = 0;
                    add_room.setVisibility(View.VISIBLE);
                    stepCount = 0;

                    roomsView.setText("Rooms Added: 0");
                    //step_view.setText("Steps: 0");
                    record.setBackgroundColor(Color.RED);
                    record.setText("Recording...");
                    isRecording = true;
                    rooms.clear();
                }
            }
        });


        android.hardware.SensorManager sensorManager = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensorRotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor sensorAccelometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        SensorEventListener sensorEventListenerRotationVector = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float[] rotationMatrix = new float[16];
                android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                float[] remappedRotationMatrix = new float[16];
                android.hardware.SensorManager.remapCoordinateSystem(rotationMatrix, android.hardware.SensorManager.AXIS_X, android.hardware.SensorManager.AXIS_Z,
                        remappedRotationMatrix);
                float[] orientations = new float[3];
                android.hardware.SensorManager.getOrientation(remappedRotationMatrix, orientations);
                angle = (int) ((Math.toDegrees(orientations[0]) + 360) % 360);
                angle_view.setText("Direction: "+angle);
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        SensorEventListener sensorEventListenerAccelometer = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if(isRecording){
                    float upperThreshold = 10.5f;
                    float lowerTreshold = 10.0f;

                    float x_acceleration = event.values[0];
                    float y_acceleration = event.values[1];
                    float z_acceleration = event.values[2];

                    double magnitude = (double) Math.sqrt(x_acceleration * x_acceleration + y_acceleration * y_acceleration + z_acceleration * z_acceleration);
                    /*
                    ssc.findStep(magnitude);
                    if(ssc.getStepCount() > stepCount){
                       stepCount = ssc.getStepCount();
                       rotationVectors.add(angle);
                       step_view.setText("Steps: "+stepCount);
                       stepCount++;
                    }
                    */

                    if (!detected) {
                        if (magnitude > upperThreshold) {
                            detected = true;
                        }
                    } else if (magnitude < lowerTreshold) {
                        detected = false;
                        consumed = false;
                    }
                    if (detected && !consumed) {
                        stepCount++;
                        consumed = true;
                        rotationVectors.add(angle);
                        //step_view.setText("Steps: " + stepCount);

                    }
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(sensorEventListenerRotationVector, sensorRotation, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListenerAccelometer, sensorAccelometer, SensorManager.SENSOR_DELAY_NORMAL);

    }
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
        if(isRecording) {
            /*
            if(prevStepCount < stepCount) {
                Mat resizeimage = new Mat();
                Size scaleSize = new Size(640, 480);
                Imgproc.resize(mRGBA, resizeimage, scaleSize);
                Imgproc.cvtColor(resizeimage, resizeimage, Imgproc.COLOR_RGBA2GRAY);
                if (added_room) {
                    rooms.add(roomCount);
                    added_room = false;
                } else {
                    rooms.add(0);
                }
                rotationVectors.add(angle);
                String imageName = String.format("%010d", count) + ".jpg";
                Imgcodecs.imwrite(baseStorage + "/Images/" + imageName, mRGBA);
                num_image += 1;
                prevStepCount = stepCount;
                count++;
            }
            */
            if(count%10 == 0) {
                runOnUiThread(new Runnable() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {
                        //Map View
                        double step_length = 5;
                        double recordedLastX = prev_x;
                        double recordedLastY = prev_y;
                        current_x =  prev_x + (step_length * Math.sin(prevAngle));
                        current_y =  prev_y + (step_length * Math.cos(prevAngle));
                        prev_x = current_x;
                        prev_y = current_y;
                        prevAngle = (int)Math.toRadians(angle);

                        if(Global.MAP_VIEW_MAP_CREATION) {
                            Paint paint = new Paint();
                            paint.setColor(Color.RED);
                            paint.setStyle(Paint.Style.FILL_AND_STROKE);
                            paint.setStrokeWidth(3);
                            paint.setAntiAlias(true);
                            canvas.drawLine((float) recordedLastX,(float) recordedLastY, (float) current_x, (float) current_y, paint);
                            canvas.drawCircle((float)current_x,(float)current_y,2,paint);
                            /*
                            if(current_x<0 || current_y<0 || current_y>bitmap.getHeight() || current_x>bitmap.getWidth()) {
                                bitmap = Bitmap.createScaledBitmap(bitmap,bitmap.getWidth()+10,bitmap.getHeight()+10,false);
                                canvas.scale(canvas.getWidth()+20,canvas.getHeight()+20);
                            }
                             */
                            try (FileOutputStream out = new FileOutputStream(getBaseContext().getExternalFilesDir(null).getAbsolutePath()+"/mapImage.png")) {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                                // PNG is a lossless format, the compression factor (100) is ignored
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try (FileOutputStream out = new FileOutputStream(baseStorage+"/Map/mapImage.png")) {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                                // PNG is a lossless format, the compression factor (100) is ignored
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            mapView.setImageBitmap(bitmap);
                        }

                    }
                });

                if(step.equals("Left")){
                    step= "Right";
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            step_timing.setText("Timing: Step Right");
                        }
                    });
                }
                else{
                    step= "Left";
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            step_timing.setText("Timing: Step Left");
                        }
                    });
                }

                Mat resizeimage = new Mat();
                Size scaleSize = new Size(640, 480);
                Imgproc.resize(mRGBA, resizeimage, scaleSize);
                Imgproc.cvtColor(resizeimage, resizeimage, Imgproc.COLOR_RGBA2GRAY);
                if (added_room) {
                    rooms.add(""+roomCount);
                    added_room = false;
                } else {
                    rooms.add(""+0);
                }
                rotationVectors.add(this.angle);
                String imageName = String.format("%010d", imageCount) + ".jpg";
                Imgcodecs.imwrite(baseStorage + "/Images/" + imageName, resizeimage);
                num_image += 1;
                prevStepCount = stepCount;
                imageCount++;
            }
            count++;
        }

        mRGBA = featureDetector(mRGBA);
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
    public Mat featureDetector(Mat mRGB){
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        ORB orb = ORB.create(1000);
        orb.detect(mRGB, keypoints1);
        Mat descriptors1 = new Mat();
        orb.compute(mRGB, keypoints1, descriptors1);
        Features2d.drawKeypoints(mRGB,keypoints1,mRGB);
        return mRGB;
    }
    //FileHandler
    public void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);
        fileOrDirectory.delete();
    }
    public float[] lowPassFilter( float[] input, float[] output ) {
        if ( output == null ) return input;
        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + 1.0f * (input[i] - output[i]);
        }
        return output;
    }


    // Feedback
    public void Speak(String message){
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null,null);
    }




}

