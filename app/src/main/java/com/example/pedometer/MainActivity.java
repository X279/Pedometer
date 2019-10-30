package com.example.pedometer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;

public class MainActivity extends Activity implements SensorEventListener,
        OnClickListener
{
    private Button mWriteButton,mStopButton;
    private boolean doWrite = false;
    private SensorManager sm;
    private float lowX = 0,lowY = 0,lowZ = 0;
    private final float FILTERING_VALAUE = 0.1f; //筛选值
    private TextView AT,ACT,stepNumText;
    private int stepNum = 0;
    //存放三轴的数据
    private float[] oriValues = new float[3];
    private final int valueNum = 4;
    //用来存放计算机阈值波峰波谷差值
    private float[] tempValue = new float[valueNum];
    private int tempCount = 0;
    private boolean isDirectioup = false;
    private int continueUpCount = 0;    //持续上升的次数
    private int continueUpFormerCount = 0;//记录波峰的上升次数
    private boolean laststatus = false; //上一点的状态，上升还是下降
    private float peakOfWave = 0;//波峰值
    private float valleyOfWave = 0;//波谷值
    private long timeofThisPeak = 0; //此次波峰时间
    private long timeOflastPeak = 0;   //上次波峰时间
    private long timeOfNow = 0;//当前的时间
    private float gravityNew = 0; //当前传感器的值
    private float gravityOld = 0;//上次传感器的值
    //动态阈值
    private final float InitiaValue = (float)1.3;
    //
    private float ThreadValue = (float)2.0;
    private int TimeInterVal = 250;

    //写文件申请的权限
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };

    private Handler mHandler = new Handler(){

        public void handleMessage(Message msg) {

            stepNumText.setText("步数"+stepNum);
        };

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //检测是否有写的权限
        try{
            int permission = ActivityCompat.checkSelfPermission(this,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if(permission != PackageManager.PERMISSION_GRANTED){
                //没有写的权限就去申请写的权限
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        AT = (TextView)findViewById(R.id.AT);
        ACT = (TextView)findViewById(R.id.onAccuracyChanged);
        stepNumText = findViewById(R.id.stepNum);

        sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        sm.registerListener(this,sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        try{
            FileOutputStream fout = openFileOutput("acc.txt",Context.MODE_PRIVATE);
            fout.close();
        }
        catch (IOException e){
            e.printStackTrace();
        }
        mWriteButton = (Button)findViewById(R.id.Button_Write);
        mWriteButton.setOnClickListener(this);
        mStopButton = (Button)findViewById(R.id.Button_Stop);
        mStopButton.setOnClickListener(this);
    }

    public void pause(){
        super.onPause();
    }

    public void onClick(View v){
        if(v.getId() == R.id.Button_Write){
            doWrite = true;
            Toast.makeText(this,"允许写",Toast.LENGTH_SHORT).show();
        }
        else if(v.getId() == R.id.Button_Stop){
            doWrite = false;
            Toast.makeText(this,"不允许写",Toast.LENGTH_SHORT).show();
        }
    }

    public void onAccuracyChanged(Sensor sensor,int accuracy){
        ACT.setText("onAccuracyChanged is detonated " + stepNum);
    }

    public void onSensorChanged(SensorEvent event){
        String message = new String();
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            float X = event.values[0]; //x轴加速度
            float Y = event.values[1]; //y轴加速度
            float Z = event.values[2]; //z轴加速度
            lowX = X*FILTERING_VALAUE + lowX*(1.0f - FILTERING_VALAUE);
            lowY = Y*FILTERING_VALAUE + lowY*(1.0f - FILTERING_VALAUE);
            lowZ = Z*FILTERING_VALAUE + lowZ*(1.0f - FILTERING_VALAUE);

            float highX = X - lowX;
            float highY = Y - lowY;
            float highZ = Z - lowZ;

            double highA = Math.sqrt(highX*highX + highY*highY + highZ*highZ);
            gravityNew = (float)highA;

            deteorNewStep(gravityNew);

            DecimalFormat df = new DecimalFormat("#,##0.000");
            message = df.format(highX) + " ";
            message += df.format(highY) + " ";
            message += df.format(highZ) + " ";
            message += df.format(highA) + "\n";
            AT.setText(message + "\n");
            if(doWrite){
                write2file(message);
            }
        }
    }

    //进行计步
    private void deteorNewStep(float values){
        if (gravityOld==0){
            gravityOld = values;
        }else {
            //检测是否为波峰
            if (detectorPeak(values,gravityOld)){
                //如果为波峰，记录这次的时间和上次的时间
                timeOflastPeak = timeofThisPeak;
                timeOfNow =System.currentTimeMillis();
                //两次时间差大于250，并且波峰和波谷的差值大于阈值就判定为一步，进行步数加1


                if (timeOfNow-timeOflastPeak>=TimeInterVal &&(peakOfWave-valleyOfWave>=ThreadValue)){
                    timeOflastPeak = timeOfNow;

                    //开始进行步数加一
                    stepNum++;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Message message = new Message();
                            message.what = 1;
                            mHandler.sendMessage(message);
                        }
                    }).start();

                }
            }

            if (timeOfNow-timeOflastPeak>=TimeInterVal&&(peakOfWave-valleyOfWave>=InitiaValue)){
                timeOflastPeak =timeOfNow;
                ThreadValue = peakValleyThread(peakOfWave-valleyOfWave);
            }
        }
        gravityOld = values;
    }

    /**
     * 检测波峰
     * 以下4个条件判断为波峰：
     * 1.目前点为下降的趋势：isDirectionup为false
     * 2.之前的点为上升的趋势：lastStatus为true
     * 3.到波峰为止,持续上升大于等于2次
     * 4.波峰值大于20
     * 记录波谷值
     * 1.观察波形图，可以发现出现步子的地方，波谷的下一个就是波峰，有比较明显的特征和差值
     * 2.所以要记录每次的波谷值，为了和下次的波峰进行对比
     * @param newValue
     * @param oldValue
     * @return
     */
    private boolean detectorPeak(float newValue,float oldValue){
        laststatus = isDirectioup;
        //新的值大于旧值说明在上升
        if (newValue>=oldValue){
            isDirectioup =true;
            continueUpCount++;
        }else {
            continueUpFormerCount =continueUpCount;
            continueUpCount = 0;
            isDirectioup = false;
        }

        if (!isDirectioup && laststatus && (continueUpFormerCount >= 2 || oldValue >= 20)){
            peakOfWave = oldValue;
            return  true;
        }else if (!laststatus && isDirectioup){
            valleyOfWave =oldValue;
            return false;
        }else {
            return false;
        }
    }

    /**
     * 阈值的计算
     * 通过波峰波谷的差值计算阈值,计算四个值，记录在tempValue数组中
     * 再将数组传入函数averageValue中计算
     * @param value
     * @return
     */
    private float peakValleyThread(float value){
        float tempThread =ThreadValue;

        if (tempCount<valueNum){
            tempValue[tempCount] =value;
            tempCount++;
        }else {
            tempThread =averageValue(tempValue,valueNum);
            for (int i=1;i<valueNum;i++){
                tempValue[i-1] =tempValue[i];
            }
            tempValue[valueNum-1] =value;
        }
        return tempThread;
    }

    /**
     *梯度化阈值，计算数组的平均值，通过均值将阈值梯度化在一个范围里
     * @param value
     * @param n
     * @return
     */
    private float averageValue(float value[],int n){
        float ave = 0;
        for (int i=0;i<n;i++){
            ave+=value[i];
        }
        ave =ave/valueNum;
        if (ave>=8)
            ave =(float)4.3;
        else if (ave>=7&&ave<8)
            ave =(float)3.3;
        else if (ave>=4&&ave<7)
            ave = (float)2.3;
        else if (ave>=3&&ave<4)
            ave =(float)2.0;
        else
            ave =(float)1.3;

        return ave;
    }


    private void write2file(String a){
        try{
            File file = new File("/sdcard/acc.txt");
            if(!file.exists()){
                file.createNewFile();
            }
            System.out.println(file.getPath());
            RandomAccessFile randomFile = new RandomAccessFile("/sdcard/acc.txt","rw");
            long fileLength = randomFile.length();
            randomFile.seek(fileLength);
            randomFile.writeBytes(a);
            randomFile.close();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
