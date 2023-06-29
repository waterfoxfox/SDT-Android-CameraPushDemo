package com.mediapro.camera_send_demo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class UserSettingActivity extends Activity {

    private static final String TAG = "SDMedia";

    private EditText et_ServerIp;
    private EditText et_RoomId;
    private EditText et_UserId;
    private Spinner spinner_Resolution;
    private Spinner spinner_Bitrate;
    private Spinner spinner_VFPS;
    private Spinner spinner_FEC;
    private Spinner spinner_AEC_DELAY;

    private CheckBox cb_EnableAutoBitrate;
    private CheckBox cb_EnableNack;
    private CheckBox cb_EnableAec;
    private CheckBox cb_EnableAgc;
    private CheckBox cb_EnableAns;
    private CheckBox cb_EnableSoft3A;
    private CheckBox cb_EnableHardwareDecode;

    private Button bntOK;
    private Button bntCancel;

    private SharedPreferences sharedPreferences;
    private UserSetting us;

    private List<String> listResolution;
    private List<String> listBitrate;
    private List<Integer> listVFPS;
    private List<Integer> listFEC;
    private List<Integer> listAecDelay;

    private ArrayAdapter<String> adapterResolution;
    private ArrayAdapter<String> adapterRate;
    private ArrayAdapter<Integer> adapterVFPS;
    private ArrayAdapter<Integer> adapterFEC;
    private ArrayAdapter<Integer> adapterAecDelay;

    // Spinner 选中值
    private String spinnerResolutionChoose;
    private String spinnerBitrateChoose;
    private int spinnerVFPSChoose;
    private int spinnerFECChoose;
    private int spinnerAecDelayChoose;

    public static final String action = "UserSettingActivity.broadcast.action";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowManager m = getWindowManager();
        Display d = m.getDefaultDisplay();
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.height = (int) (d.getHeight() * .8);
        p.width = (int) (d.getWidth() * 0.7);
        p.alpha = 1.0f;
        p.dimAmount = 0.0f;

        getWindow().setAttributes(p);

//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_user_setting);

        // response screen rotation event
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        setTitle(R.string.user_setting_title);

        // restore data.
        us = new UserSetting();
        sharedPreferences = getSharedPreferences("UserInfo", 0);
        us.ServerIp = sharedPreferences.getString(UserSettingKey.ServerIpKey, "47.106.195.225");
        us.UserId = sharedPreferences.getInt(UserSettingKey.UserIdKey, 0);
        us.RoomId = sharedPreferences.getInt(UserSettingKey.RoomIdKey, 1);
        us.Resolution = sharedPreferences.getString(UserSettingKey.ResolutionKey, "360*640");
        us.Framerate = sharedPreferences.getInt(UserSettingKey.FramerateKey, 30);
        us.Bitrate = sharedPreferences.getString(UserSettingKey.BitrateKey, "400kbps");
        us.FecRedunRatio = sharedPreferences.getInt(UserSettingKey.FecRedunKey, 30);
        us.AecDelay = sharedPreferences.getInt(UserSettingKey.AecDelayKey, 40);
        us.EnableAec = sharedPreferences.getBoolean(UserSettingKey.EnableAecKey, true);
        us.EnableAgc = sharedPreferences.getBoolean(UserSettingKey.EnableAgcKey, true);
        us.EnableAns = sharedPreferences.getBoolean(UserSettingKey.EnableAnsKey, true);
        us.EnableSoft3A = sharedPreferences.getBoolean(UserSettingKey.EnableSoft3AcKey, true);
        us.UseHwDecode = sharedPreferences.getBoolean(UserSettingKey.EnableHwDecode, true);
        us.EnableNack = sharedPreferences.getBoolean(UserSettingKey.EnableNackKey, true);
        us.EnableAutoBitrate = sharedPreferences.getBoolean(UserSettingKey.EnableAutoBitrateKey, true);

        et_ServerIp = (EditText)findViewById(R.id.et_ServerIp);
        et_RoomId = (EditText)findViewById(R.id.et_RoomId);
        et_UserId = (EditText)findViewById(R.id.et_UserId);
        spinner_Resolution = (Spinner) findViewById(R.id.spinner_Resolution);
        spinner_Bitrate = (Spinner)findViewById(R.id.spinner_Bitrate);
        spinner_VFPS = (Spinner)findViewById(R.id.spinner_VFPS);
        spinner_FEC = (Spinner)findViewById(R.id.spinner_FEC);
        spinner_AEC_DELAY = (Spinner)findViewById(R.id.spinner_AEC_DELAY);
        cb_EnableAec = (CheckBox)findViewById(R.id.cb_EnableAEC);
        cb_EnableAgc = (CheckBox)findViewById(R.id.cb_EnableAGC);
        cb_EnableAns = (CheckBox)findViewById(R.id.cb_EnableANS);
        cb_EnableSoft3A = (CheckBox)findViewById(R.id.cb_EnableSoft3A);
        cb_EnableAutoBitrate = (CheckBox)findViewById(R.id.cb_EnableAutoBitrate);
        cb_EnableNack = (CheckBox)findViewById(R.id.cb_EnableNack);
        cb_EnableHardwareDecode = (CheckBox)findViewById(R.id.cb_EnableHardwareDecode);

        /////////////////////分辨率
        listResolution = new ArrayList<String>();
        listResolution.add("180*320");
        listResolution.add("360*640");
        listResolution.add("480*848");
        listResolution.add("720*1280");

        adapterResolution = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, listResolution);
        adapterResolution.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_Resolution.setAdapter(adapterResolution);
        spinner_Resolution.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerResolutionChoose = (String) spinner_Resolution.getSelectedItem();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_Resolution.setSelection(0, true);
        for (int i = 0; i < listResolution.size(); i++) {
          if (us.Resolution.equals(listResolution.get(i))) {
              spinner_Resolution.setSelection(i, true);
              break;
          }
        }

        /////////////////////码率
        listBitrate = new ArrayList<String>();
        listBitrate.add("200kbps");
        listBitrate.add("300kbps");
        listBitrate.add("400kbps");
        listBitrate.add("600kbps");
        listBitrate.add("1000kbps");
        listBitrate.add("1500kbps");
        listBitrate.add("2000kbps");

        adapterRate = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, listBitrate);
        adapterRate.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_Bitrate.setAdapter(adapterRate);
        spinner_Bitrate.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerBitrateChoose = (String) spinner_Bitrate.getSelectedItem();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_Bitrate.setSelection(0, true);
        for (int i = 0; i < listBitrate.size(); i++) {
            if (us.Bitrate.equals(listBitrate.get(i))) {
                spinner_Bitrate.setSelection(i, true);
                break;
            }
        }


        /////////////////////帧率
        listVFPS = new ArrayList<Integer>();
        listVFPS.add(10);
        listVFPS.add(15);
        listVFPS.add(25);
        listVFPS.add(30);

        adapterVFPS = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, listVFPS);
        adapterVFPS.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_VFPS.setAdapter(adapterVFPS);
        spinner_VFPS.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerVFPSChoose = ((Integer)spinner_VFPS.getSelectedItem()).intValue();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_VFPS.setSelection(0, true);
        for (int i = 0; i < listVFPS.size(); i++) {
            if (us.Framerate == listVFPS.get(i)) {
                spinner_VFPS.setSelection(i, true);
                break;
            }
        }

        /////////////////////上行FEC冗余度
        listFEC = new ArrayList<Integer>();
        listFEC.add(0);
        listFEC.add(20);
        listFEC.add(30);
        listFEC.add(40);
        listFEC.add(50);

        adapterFEC = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, listFEC);
        adapterFEC.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_FEC.setAdapter(adapterFEC);
        spinner_FEC.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerFECChoose = ((Integer) spinner_FEC.getSelectedItem()).intValue();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_FEC.setSelection(0,true);
        for (int i = 0; i < listFEC.size(); i++) {
            if (us.FecRedunRatio == listFEC.get(i)) {
                spinner_FEC.setSelection(i, true);
                break;
            }
        }

        /////////////////////AEC Delay ms
        listAecDelay = new ArrayList<Integer>();
        listAecDelay.add(20);
        listAecDelay.add(40);
        listAecDelay.add(60);
        listAecDelay.add(80);
        listAecDelay.add(100);
        listAecDelay.add(120);
        listAecDelay.add(150);
        listAecDelay.add(180);
        listAecDelay.add(220);

        adapterAecDelay = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, listAecDelay);
        adapterAecDelay.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_AEC_DELAY.setAdapter(adapterAecDelay);
        spinner_AEC_DELAY.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub
                arg0.setVisibility(View.VISIBLE);
                spinnerAecDelayChoose = ((Integer) spinner_AEC_DELAY.getSelectedItem()).intValue();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_AEC_DELAY.setSelection(0,true);
        for (int i = 0; i < listAecDelay.size(); i++) {
            if (us.AecDelay == listAecDelay.get(i)) {
                spinner_AEC_DELAY.setSelection(i, true);
                break;
            }
        }

        et_ServerIp.setText(us.ServerIp);
        et_RoomId.setText(String.valueOf(us.RoomId));
        et_UserId.setText(String.valueOf(us.UserId));
        cb_EnableAutoBitrate.setChecked(us.EnableAutoBitrate);
        cb_EnableNack.setChecked(us.EnableNack);
        cb_EnableAec.setChecked(us.EnableAec);
        cb_EnableAgc.setChecked(us.EnableAgc);
        cb_EnableAns.setChecked(us.EnableAns);
        cb_EnableSoft3A.setChecked(us.EnableSoft3A);
        cb_EnableHardwareDecode.setChecked(us.UseHwDecode);

        bntOK = (Button) findViewById(R.id.btn_Ok);
        bntCancel = (Button) findViewById(R.id.btn_cancel);

        bntOK.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    boolean bWrite = false;

                    if (us.ServerIp != et_ServerIp.getText().toString()||
                            us.UserId != Integer.valueOf(et_UserId.getText().toString())||
                            us.RoomId != Integer.valueOf(et_RoomId.getText().toString()) ||
                            us.Resolution.equals(spinnerResolutionChoose) == false ||
                            us.Framerate != Integer.valueOf(spinnerVFPSChoose) ||
                            us.Bitrate.equals(spinnerBitrateChoose) == false ||
                            us.FecRedunRatio != spinnerFECChoose ||
                            us.AecDelay != spinnerAecDelayChoose ||
                            us.EnableAec != cb_EnableAec.isChecked() ||
                            us.EnableAgc != cb_EnableAgc.isChecked() ||
                            us.EnableAns != cb_EnableAns.isChecked() ||
                            us.EnableSoft3A != cb_EnableSoft3A.isChecked() ||
                            us.UseHwDecode != cb_EnableHardwareDecode.isChecked() ||
                            us.EnableAutoBitrate != cb_EnableAutoBitrate.isChecked() ||
                            us.EnableNack != cb_EnableNack.isChecked()) {
                        bWrite = true;
                    }

                    if (bWrite == true) {

                        sharedPreferences.edit()
                                .putString(UserSettingKey.ServerIpKey, et_ServerIp.getText().toString())
                                .putInt(UserSettingKey.UserIdKey, Integer.valueOf(et_UserId.getText().toString()))
                                .putInt(UserSettingKey.RoomIdKey, Integer.valueOf(et_RoomId.getText().toString()))
                                .putString(UserSettingKey.ResolutionKey, spinnerResolutionChoose)
                                .putInt(UserSettingKey.FramerateKey, Integer.valueOf(spinnerVFPSChoose))
                                .putString(UserSettingKey.BitrateKey, spinnerBitrateChoose)
                                .putInt(UserSettingKey.FecRedunKey, spinnerFECChoose)
                                .putInt(UserSettingKey.AecDelayKey, spinnerAecDelayChoose)
                                .putBoolean(UserSettingKey.EnableAecKey, cb_EnableAec.isChecked())
                                .putBoolean(UserSettingKey.EnableAgcKey, cb_EnableAgc.isChecked())
                                .putBoolean(UserSettingKey.EnableAnsKey, cb_EnableAns.isChecked())
                                .putBoolean(UserSettingKey.EnableSoft3AcKey, cb_EnableSoft3A.isChecked())
                                .putBoolean(UserSettingKey.EnableHwDecode, cb_EnableHardwareDecode.isChecked())
                                .putBoolean(UserSettingKey.EnableAutoBitrateKey, cb_EnableAutoBitrate.isChecked())
                                .putBoolean(UserSettingKey.EnableNackKey, cb_EnableNack.isChecked())
                                .commit();

                        Intent intent = new Intent(action);
                        sendBroadcast(intent);
                        finish();
                    }

                } catch (Exception e) {

                }
            }
        });
        bntCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }
}
