package com.example.cppnguide;

import org.opencv.core.Mat;

import java.io.Serializable;
import java.util.ArrayList;

public class Location implements Serializable{
    private double x;
    private double y;
    private String name;
    private int Angle;
    private String direction;
    private ArrayList<Location> edges;
    private int index;

    public Location(double x, double y,int angle, String direction,String name,int index){
        this.edges = new ArrayList<>();
        this.index = index;
        this.x = x;
        this.y = y;
        this.Angle = angle;
        this.direction = direction;
        if(name == "0"){
            this.name = "Hallway";
        }
        else {
            this.name = name;
        }
    }
    public double getX(){
        return this.x;
    }
    public double getY(){
        return this.y;
    }
    public String getName(){
        return this.name;
    }
    public int getAngle(){
        return this.Angle;
    }
    public void addEdge(Location location){
        edges.add(location);
    }


    public String getDirection() {return this.direction; }
}
