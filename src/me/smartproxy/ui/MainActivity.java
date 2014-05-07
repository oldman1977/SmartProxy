package me.smartproxy.ui;

import java.io.File;
import java.util.Calendar;
import me.smartproxy.core.LocalVpnService;
import me.smartproxy.R;
 
import android.net.Uri;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
 
public class MainActivity extends Activity implements android.view.View.OnClickListener, OnCheckedChangeListener,LocalVpnService.onStatusChangedListener {

	private static String GL_HISTORY_LOGS;
	private final String CONFIG_URL_KEY="CONFIG_URL_KEY";
	
	private Switch switchProxy;
	private TextView textViewLog;
	private ScrollView scrollViewLog;
	private TextView textViewConfigUrl;
	private Calendar mCalendar;
 
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		textViewConfigUrl=(TextView)findViewById(R.id.textViewConfigUrl);
		scrollViewLog=(ScrollView)findViewById(R.id.scrollViewLog);
		textViewLog=(TextView)findViewById(R.id.textViewLog);
	    findViewById(R.id.configUrlLayout).setOnClickListener(this);

	    textViewConfigUrl.setText(readConfigUrl(getString(R.string.config_not_set_value)));
		textViewLog.setText(GL_HISTORY_LOGS);
		scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);

		mCalendar=Calendar.getInstance();
        LocalVpnService.addOnStatusChangedListener(this);
	}

	String readConfigUrl(String defaultValue){
		 // 读取 SharedPreferences 
		 SharedPreferences preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE); 
		 String configUrl= preferences.getString(CONFIG_URL_KEY, defaultValue);
		 return configUrl;
	}
	
	void setConfigUrl(String configUrl){
		 SharedPreferences preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE); 
		 Editor editor = preferences.edit(); 
		 editor.putString(CONFIG_URL_KEY, configUrl);
		 editor.commit();
	}
	
	String getVersionName()  {
		 try {
	           PackageManager packageManager = getPackageManager();
	           // getPackageName()是你当前类的包名，0代表是获取版本信息
	           PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(),0);
	           String version = packInfo.versionName;
	           return version;
		} catch (Exception e) {
			return "0.0";
		}   
	 }

	boolean isValidUrl(String url){
		try {
			 if(url==null||url.isEmpty())
				 return false;
			 
			 if(url.startsWith("/")){//file path
				 File file=new File(url);
				 if(!file.exists()){
					 onLogReceived(String.format("File(%s) not exists.",url));
					 return false;
				 }
				 if(!file.canRead()){
					 onLogReceived(String.format("File(%s) can't read.",url));
					 return false;
				 }
			 }else { //url
				 Uri uri=Uri.parse(url);
				 if(!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
					 return false;
				 if(uri.getHost()==null)
					 return false;
			 }
			 return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	@Override
	public void onClick(View v) {
		if(!switchProxy.isChecked()){
			String value=textViewConfigUrl.getText().toString();
			if(getString(R.string.config_not_set_value).equals(value))
				value="";
			
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle(getString(R.string.config_url));
			final EditText editText=new EditText(this);
			editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
			editText.setHint(getString(R.string.config_url_hint));
			editText.setText(value);
			builder.setView(editText);
			builder.setPositiveButton(getString(R.string.btn_ok),new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					 // 读取 SharedPreferences 
					 String configUrl=editText.getText().toString().toLowerCase().trim();
					 if(isValidUrl(configUrl)){
						 setConfigUrl(configUrl);
						 textViewConfigUrl.setText(configUrl);
					 }else {
						 Toast.makeText(MainActivity.this, getString(R.string.err_invalid_url), Toast.LENGTH_SHORT).show();
					 }
					 //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
				}
			});
			
		    builder.setNegativeButton(getString(R.string.btn_cancel),null);
		    
		    //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			builder.create().show();
		}
	}
	
 
	@SuppressLint("DefaultLocale")
	@Override
	public void onLogReceived(String logString) {
		
		mCalendar.setTimeInMillis(System.currentTimeMillis());
		logString=String.format("[%1$02d:%2$02d:%3$02d] %4$s\n",
				mCalendar.get(Calendar.HOUR_OF_DAY),
				mCalendar.get(Calendar.MINUTE),
				mCalendar.get(Calendar.SECOND),
				logString);
		
		System.out.println(logString);
		
		if(textViewLog.getLineCount()>200){
			textViewLog.setText("");
		}
		textViewLog.append(logString);
		scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);
		GL_HISTORY_LOGS=textViewLog.getText().toString();
	}
	
	@Override
	public void onStatusChanged(String status, Boolean isRunning) {
		 switchProxy.setEnabled(true);
		 switchProxy.setChecked(isRunning);
		 onLogReceived(status);
    	 Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
	}
	 
 
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if(LocalVpnService.IsRunning!=isChecked){
			 switchProxy.setEnabled(false);
			 if(isChecked){
				 Intent intent=LocalVpnService.prepare(this);
					if(intent==null){
						startVPNService();
					}else {
						startActivityForResult(intent,1985);
					}
			 }
			 else{
				 LocalVpnService.IsRunning=false;
			 }
		}
	}
 
	private void startVPNService(){
		try {
			String configUrl=textViewConfigUrl.getText().toString().trim();
			if(!isValidUrl(configUrl))
				throw new Exception(getString(R.string.err_invalid_url));
			
			 textViewLog.setText("");
			 GL_HISTORY_LOGS=null;
			 onLogReceived("starting...");
			 LocalVpnService.ConfigUrl=configUrl;
			 Intent service=new Intent(this,LocalVpnService.class);
			 startService(service);
		} catch (Exception e) {
			Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
			switchProxy.post(new Runnable() {
				@Override
				public void run() {
					switchProxy.setChecked(false);
					switchProxy.setEnabled(true);
				}
			});
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if( requestCode==1985){
			Log.i("", "onActivityResult:"+resultCode);
			if(resultCode==RESULT_OK ){
				startVPNService();
			}else {
				switchProxy.setChecked(false);
				switchProxy.setEnabled(true);
				onLogReceived("canceled.");
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		MenuItem menuItem= menu.findItem(R.id.menu_item_switch);
		switchProxy=(Switch)menuItem.getActionView();
		switchProxy.setChecked(LocalVpnService.IsRunning);
		switchProxy.setOnCheckedChangeListener(this);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle presses on the action bar items
	    switch (item.getItemId()) {
	        case R.id.menu_item_about:{
	        	AlertDialog.Builder builder = new Builder(this);
				builder.setTitle(getString(R.string.app_name)+getVersionName());
				builder.setMessage(getString(R.string.about_info));
				builder.setPositiveButton(getString(R.string.btn_ok),null);
				builder.setNegativeButton(getString(R.string.btn_more), new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Uri uri = Uri.parse("http://smartproxy.me");  
						Intent intent = new Intent(Intent.ACTION_VIEW, uri);  
						startActivity(intent);
					}
				});
				builder.create().show();
				return true;
	        }
	        case R.id.menu_item_exit:{
	        	if(LocalVpnService.IsRunning){
	        		AlertDialog.Builder builder = new Builder(this);
					builder.setTitle(getString(R.string.menu_item_exit));
					builder.setMessage(getString(R.string.exit_confirm_info));
					builder.setPositiveButton(getString(R.string.btn_ok),new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							 LocalVpnService.IsRunning=false;
							 LocalVpnService.Instance.disconnectVPN();
							 Intent service=new Intent(MainActivity.this,LocalVpnService.class);
							 stopService(service);
							 System.runFinalization();
							 System.exit(0);
						}
					});
				    builder.setNegativeButton(getString(R.string.btn_cancel),null);
					builder.create().show();
	        	}else {
	        		finish();
				}
				return true;
	        }
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	protected void onDestroy() {
		LocalVpnService.removeOnStatusChangedListener(this);
		super.onDestroy();
	}

}
