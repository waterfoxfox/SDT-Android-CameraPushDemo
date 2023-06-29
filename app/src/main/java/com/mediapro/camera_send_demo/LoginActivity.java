package com.mediapro.camera_send_demo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission.CAMERA;

import static android.os.Build.VERSION_CODES.M;


public class LoginActivity extends Activity implements OnClickListener {
	private static final String TAG = "SDMedia";
	private static final int REQUEST_PERMISSIONS = 1;

	private EditText etIP;
	private EditText etRoom;
	private EditText etSendPosition;

	private Button loginButton;
	private Button serverIpClearBtn;
	private Button roomIdClearBtn;
	private Button sendPositionClearBtn;

	private int userId = 0;
	private int roomId = 0;
	private int sendPostison = 0;

	private boolean hasNetworkConnected = false;

	private SharedPreferences sharedPreferences;

	private TextWatcher serverIpClearTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable s) {
			// TODO Auto-generated method stub
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {
			etRoom.setText("");
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);

		init();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	private void init() {

		etIP = (EditText)findViewById(R.id.login_edit_ip);
		etIP.addTextChangedListener(serverIpClearTextWatcher);
		etRoom = (EditText)findViewById(R.id.login_edit_room);
		etSendPosition = (EditText) findViewById(R.id.et_send_position);

		serverIpClearBtn = (Button)findViewById(R.id.ip_clear_btn);
		serverIpClearBtn.setOnClickListener(this);
		roomIdClearBtn = (Button)findViewById(R.id.room_clear_btn);
		roomIdClearBtn.setOnClickListener(this);
		sendPositionClearBtn = (Button) findViewById(R.id.send_position_clear_btn);
		sendPositionClearBtn.setOnClickListener(this);

		loginButton = (Button)findViewById(R.id.login_button_login);
		loginButton.setOnClickListener(this);

		sharedPreferences = getSharedPreferences("UserInfo", 0);
		etIP.setText(sharedPreferences.getString(UserSettingKey.ServerIpKey,"47.106.195.225")+"");
		etRoom.setText(sharedPreferences.getInt(UserSettingKey.RoomIdKey, 888)+"");
		etSendPosition.setText(sharedPreferences.getInt(UserSettingKey.SendPositionKey, 1)+"");
		
		//DEMO使用随机生成的用户ID
		userId = sharedPreferences.getInt(UserSettingKey.UserIdKey, 0);
		if (userId == 0) {
			userId = 100000 + (int) (Math.random() * (999999 - 100000));
		}
	}


	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
		case R.id.login_button_login:
			login();
			break;
		case R.id.ip_clear_btn:
			etIP.setText("");
			break;
		case R.id.room_clear_btn:
			etRoom.setText("");
			break;
		case R.id.send_position_clear_btn:
			etSendPosition.setText("");
			break;
		}
	}

	//检查应用权限
	private boolean hasPermissions() {
		PackageManager pm = getPackageManager();
		String packageName = getPackageName();
		int granted = pm.checkPermission(RECORD_AUDIO, packageName) | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName) | pm.checkPermission(CAMERA, packageName);
		return granted == PackageManager.PERMISSION_GRANTED;
	}

	@TargetApi(M)
	private void requestPermissions() {
		String[] permissions = new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO, CAMERA};
		boolean showRationale = false;
		for (String perm : permissions) {
			showRationale |= shouldShowRequestPermissionRationale(perm);
		}
		if (!showRationale) {
			requestPermissions(permissions, REQUEST_PERMISSIONS);
			return;
		}
		new AlertDialog.Builder(this)
				.setMessage(getString(R.string.using_your_mic_to_record_audio))
				.setCancelable(false)
				.setPositiveButton(android.R.string.ok, (dialog, which) ->
						requestPermissions(permissions, REQUEST_PERMISSIONS))
				.setNegativeButton(android.R.string.cancel, null)
				.create()
				.show();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_PERMISSIONS) {
			int granted = PackageManager.PERMISSION_GRANTED;
			for (int r : grantResults) {
				granted |= r;
			}
			if (granted == PackageManager.PERMISSION_GRANTED)
			{
				Intent intent = new Intent(LoginActivity.this, MainActivity.class);
				startActivity(intent);
			} else {
				Toast.makeText(this, "请为应用打开相关权限", Toast.LENGTH_SHORT).show();
			}
		}
	}

	public void login() {

		ConnectivityManager manager = (ConnectivityManager) getApplicationContext()
		.getSystemService(Context.CONNECTIVITY_SERVICE);
		//确保移动运营商网络或者WIFI网络连通
		try
		{
			State state = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
			if(State.CONNECTED == state) {
				hasNetworkConnected = true;
			}
		}
		catch (Exception e)
		{
			//仅当做警告，平板无移动网络时可能触发异常
			Log.w(TAG, "getNetworkInfo TYPE_MOBILE failed.");
		}

		if (hasNetworkConnected == false) {
			State state = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
			if(State.CONNECTED == state)
			{
				hasNetworkConnected = true;
			}
		}
		
		if(hasNetworkConnected == false) {
			Toast.makeText(LoginActivity.this, "设置失败，请链接网络", Toast.LENGTH_SHORT).show();
			return;
		}

		//IP地址校验
		String sServerIp = etIP.getText().toString().trim();
		if(sServerIp.length() == 0) {
			Toast.makeText(this, "请输入服务器IP地址", Toast.LENGTH_SHORT).show();
			return;
		}

		if (!isIpv4(sServerIp)){
			Toast.makeText(this, "请输入合法的IP地址", Toast.LENGTH_SHORT).show();
			return;
		}

		//房间ID校验
		final String sRoomId = etRoom.getText().toString().trim();
		if(sRoomId.length() == 0) {
			Toast.makeText(this, "请输入房间ID", Toast.LENGTH_SHORT).show();
			return;
		}
		if (!TextUtils.isDigitsOnly(sRoomId)){
			Toast.makeText(this, "请输入正确的房间ID", Toast.LENGTH_SHORT).show();
			return;
		}

		String sSendPosition = etSendPosition.getText().toString().trim();
		if(sSendPosition.length() == 0) {
			Toast.makeText(this, "请输入发送位置", Toast.LENGTH_SHORT).show();
			return;
		}
		if (!TextUtils.isDigitsOnly(sSendPosition)){
			Toast.makeText(this, "请输入正确的发送位置", Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			roomId = Integer.parseInt(sRoomId);
			sendPostison = Integer.parseInt(sSendPosition);
		} catch (Exception e) {
			Toast.makeText(this, "请输入正确的房间ID、位置信息", Toast.LENGTH_SHORT).show();
			return;
		}

		sharedPreferences.edit()
				.putString(UserSettingKey.ServerIpKey, sServerIp)
				.putInt(UserSettingKey.RoomIdKey, roomId)
				.putInt(UserSettingKey.UserIdKey, userId)
				.putInt(UserSettingKey.SendPositionKey, sendPostison)
				.commit();

		if (hasPermissions())
		{
			//已经拥有权限
			Intent intent = new Intent(LoginActivity.this, MainActivity.class);
			startActivity(intent);
		}
		else if (Build.VERSION.SDK_INT >= M)
		{
			//符合动态申请权限时
			requestPermissions();
		}
		else
		{
			//无权限且无法申请
			Toast.makeText(this, "请为应用打开相关权限", Toast.LENGTH_SHORT).show();
		}

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {  
			case KeyEvent.KEYCODE_BACK:
			Log.i(TAG,"KEYCODE_BACK");
			moveTaskToBack(true);
			return true;  
		}  
	
		return super.onKeyDown(keyCode, event);
	}


	public static boolean isIpv4(String ipv4){
		if(ipv4==null || ipv4.length()==0){
			return false;//字符串为空或者空串
		}
		String[] parts=ipv4.split("\\.");//因为java doc里已经说明, split的参数是reg, 即正则表达式, 如果用"|"分割, 则需使用"\\|"
		if(parts.length!=4){
			return false;//分割开的数组根本就不是4个数字
		}
		for(int i=0;i<parts.length;i++){
			try{
				int n=Integer.parseInt(parts[i]);
				if(n<0 || n>255){
					return false;//数字不在正确范围内
				}
			}catch (NumberFormatException e) {
				return false;//转换数字不正确
			}
		}
		return true;
	}
}
