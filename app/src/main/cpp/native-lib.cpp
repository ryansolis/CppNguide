#include <jni.h>
#include <string>
#include <sstream>

#include <iostream>
#include <vector>

#include "DBoW2/DBoW2.h" //defines OrbVocabulary and OrbDatabase
// OpenCV
#include <opencv2/core.hpp>
#include <opencv2/highgui.hpp>
#include <opencv2/features2d.hpp>




using namespace DBoW2;
using namespace std;

void loadFeatures(vector<vector<cv::Mat > > &features);
void changeStructure(const cv::Mat &plain, vector<cv::Mat> &out);
void testVocCreation(const vector<vector<cv::Mat > > &features);
void testDatabase(const vector<vector<cv::Mat > > &features);

void changeStructure(const cv::Mat &plain, vector<cv::Mat> &out)
{
    out.resize(plain.rows);

    for(int i = 0; i < plain.rows; ++i)
    {
        out[i] = plain.row(i);
    }
}


extern "C" JNIEXPORT jstring
Java_com_example_cppnguide_MainActivity_stringFromJNI(JNIEnv *env, jobject thiz) {
    // TODO: implement stringFromJNI()
    std::string hello = "Indoor Navigation For Visually Impaired";
    return env->NewStringUTF(hello.c_str());

}
extern "C" JNIEXPORT jstring
Java_com_example_cppnguide_CreateMapAlgorithm_createVocabulary(JNIEnv *env, jobject thiz, int num_images, jstring path) {

    vector<vector<cv::Mat>>features;
    //getting orb in each image
    features.clear();
    features.reserve(num_images);
    cv::Ptr<cv::ORB> orb = cv::ORB::create();
    int count = 0;


    const char *str = env->GetStringUTFChars(path, 0);
    std::string str2 = str;
    std::string filename = str2;
    std::string imageNames;

    for(int i = 0; i < num_images; ++i) {
        stringstream ss;
        ss << filename << "/Images/" << std::setfill('0') << std::setw(10) << i << ".jpg";

        //imageNames+=","+ss.str();

        cv::Mat image = cv::imread(ss.str(), 0);
        cv::Mat mask;
        vector<cv::KeyPoint> keypoints;
        cv::Mat descriptors;
        orb->detectAndCompute(image, mask, keypoints, descriptors);
        features.push_back(vector<cv::Mat>());
        changeStructure(descriptors, features.back());
    }

    const int k = 9;
    const int L = 3;
    const WeightingType weight = TF_IDF;
    const ScoringType scoring = L1_NORM;
    OrbVocabulary voc(k, L, weight, scoring);
    voc.create(features);
    BowVector v1, v2;
    std::string score;


    for(int i = 0; i < num_images; i++)
    {
        voc.transform(features[i], v1);
        for(int j = 0; j < num_images; j++)
        {
            voc.transform(features[j], v2);
            score +=","+ to_string(voc.score(v1, v2));
        }
    }


    voc.save(filename + "/Map/map_voc.yml.gz");

    // creating database
    OrbDatabase db(voc, false, 0);
    // add images to the database
    for(int i = 0; i < num_images; i++)
    {
        db.add(features[i]);
    }
    db.save( filename + "/Map/map_db.yml.gz");
    return env->NewStringUTF("Success!");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_cppnguide_NavigationCamera_navigation(JNIEnv *env, jobject thiz, jstring path) {
    vector<vector<cv::Mat>>features;
    features.clear();
    features.reserve(1);
    cv::Ptr<cv::ORB> orb = cv::ORB::create();

    const char *str = env->GetStringUTFChars(path, 0);
    std::string str2 = str;
    std::string filename = str2;
    std::string imageName;
    std::string result;
    stringstream ss;
    ss << filename << "/query.jpg";
    cv::Mat image = cv::imread(ss.str(), 0);
    cv::Mat mask;
    vector<cv::KeyPoint> keypoints;
    cv::Mat descriptors;
    orb->detectAndCompute(image, mask, keypoints, descriptors);
    features.push_back(vector<cv::Mat>());
    changeStructure(descriptors, features.back());

    OrbDatabase db(filename+"/map_db.yml.gz");
    QueryResults ret;
    db.query(features[0],ret,1);
    stringstream res;
    res << ret[0].Id << "," << ret[0].Score;
    result = res.str();
    return env->NewStringUTF(result.c_str());
}

