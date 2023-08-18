package com.example.cppnguide;

import android.app.Activity;
import android.widget.Toast;

import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class CreateMapAlgorithm extends Activity {
    private List rotationVectors;
    private String baseStorage;
    private List roomArea;
    private List<Location> Locations;
    private int stepCount;
    public String response="";
    private int num_image;
    private String defaultStorage;
    private List<Location> ReadLocations;

    static {
        System.loadLibrary("cppnguide");
    }
    public CreateMapAlgorithm(String defaultStorage,String baseStorage, List rotationVectors, List roomArea, int num_image, int steps){
        this.defaultStorage = defaultStorage;
        this.rotationVectors = rotationVectors;
        this.baseStorage = baseStorage;
        this.roomArea = roomArea;
        this.Locations = new ArrayList<Location>();
        this.response = createVocabulary(num_image,baseStorage);
        this.num_image = num_image;
        this.stepCount = steps;
        this.ReadLocations = new ArrayList<Location>();
    }
    public void create(){
        double current_x = 0;
        double current_y = 0;
        double prev_x = 0;
        double prev_y = 0;
        double step_length = 5;
        double prevAngle = 0;
        int prevSensorAngle = (Integer) rotationVectors.get(0);
        for(int i = 0; i< num_image;i++){
            String direction = getDirection((Integer) rotationVectors.get(i),prevSensorAngle);
            prevSensorAngle = (Integer) rotationVectors.get(i);
            Location location = new Location(current_x,current_y,(Integer) rotationVectors.get(i),direction,(String) roomArea.get(i),i);
            Locations.add(location);
            //this.response +=(int)current_x+" , "+(int)current_y + " rot= "+(Integer) prevAngle+"\n";
            current_x =  prev_x + (step_length * Math.sin(prevAngle));
            current_y =  prev_y + (step_length * Math.cos(prevAngle));
            prev_x = current_x;
            prev_y = current_y;
            prevAngle = Math.toRadians((Integer)rotationVectors.get(i));
        }

        WriteObjectToFile((Object) Locations);
        ReadFileToObject(baseStorage+"/Files/Locations.ser");
        for(Location l: Locations){

          if(l.getDirection()=="right"){
              this.response+=",r";
          }
          if(l.getDirection()=="left"){
              this.response+=",l";
          }
          if(l.getDirection()=="forward"){
              this.response+=",f";
          }

        }
        if(Global.LATEST_MAP) {
           // response = baseStorage + "/Files/Locations.ser";
            try {
                copy(new File(baseStorage + "/Files/Locations.ser"),new File(Global.APP_BASE_STORAGE+"/Locations.ser"));
                copy(new File(baseStorage + "/Map/map_db.yml.gz"),new File(Global.APP_BASE_STORAGE+"/map_db.yml.gz"));
                copy(new File(baseStorage + "/Map/map_voc.yml.gz"),new File(Global.APP_BASE_STORAGE+"/map_voc.yml.gz"));
                response="Using Latest Map in Navigation!";
            } catch (Exception e) {
                response = "e";

            }
        }
        //this.response = ""+ReadLocations.size()+","+ReadLocations.get(ReadLocations.size()-1).getX()+":"+ReadLocations.get(ReadLocations.size()-1).getY();

    }
    public  String getDirection(int current,int target){
        int thresh = 20;
        double angleDiff,diff;
        angleDiff = current - target;
        angleDiff = ((angleDiff + 180) % 360) - 180;
        if(Math.abs(angleDiff)<thresh){
            return "forward";
        }
        diff = target - current;
        if(diff < 0) {
            diff += 360;
        }
        if(diff > 180) {
            return "right";
        }
        else {
            return "left";
        }
    }

    public int compareImages(Mat prev, Mat current) {

        ORB orb = ORB.create();
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        // first image
        Mat descriptors1 = new Mat();
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        orb.detect(prev, keypoints1);
        orb.compute(prev, keypoints1, descriptors1);
        // second image
        Mat descriptors2 = new Mat();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();

        orb.detect(current, keypoints2);
        orb.compute(current, keypoints2, descriptors2);
        // match these two keypoints sets
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(descriptors1, descriptors2, matches);
        return matches.size(1);
    }
    public void WriteObjectToFile(Object serObj) {
        try {
            FileOutputStream writeData = new FileOutputStream(baseStorage+"/Files/Locations.ser");
            ObjectOutputStream writeStream = new ObjectOutputStream(writeData);
            writeStream.writeObject(serObj);
            writeStream.flush();
            writeStream.close();

        } catch (IOException e) {

        }
    }
    public void ReadFileToObject(String path){
        try{
            FileInputStream readData = new FileInputStream(path);
            ObjectInputStream readStream = new ObjectInputStream(readData);
            ReadLocations = (ArrayList<Location>) readStream.readObject();
            readStream.close();

        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    public native String createVocabulary(int num_images,String path);
}
