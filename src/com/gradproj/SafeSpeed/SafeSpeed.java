package com.gradproj.SafeSpeed;

import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.gradproj.SafeSpeed.receivers.AdminReceiver;
import com.gradproj.SafeSpeed.service.LocationService;
import com.gradproj.SafeSpeed.service.LocationService.SpeedBinder;

/////////////////////////////////////////////////////////////////////////
//-----------------------------------------------------------------------
//-- SafeSpeed Class
//-----------------------------------------------------------------------
//This class interacts with the main display of the application. It gives the 
//buttons and switches their functionality, and is also responsible for starting
//up the LocationService, which runs in the background. This class is also
//responsible for displaying the current speed to the user, if the device
//is linked up to gps, 4g, or network. 
//
//There are four buttons, which when clicked, each launch an app relative to their
//displayed image (Phone, Text, Email, Contacts). If the application is powered on, 
//it will track the device's speed, and display a warning whenever a button is pressed
//if the speed is greater than 5 miles per hour. This warning will sleep the
//device if the user decides not to continue with use. Otherwise, resulting
//app is launched for use.
/////////////////////////////////////////////////////////////////////////
public class SafeSpeed extends Activity{
	
	static final int ACTIVATION_REQUEST=0;
	private static final String TAG = "SafeSpeed";
	public static final String PREFS_NAME = "SafeSpeedPrefs";
	
	private static Context context = MyApp.getAppContext();
	private static Activity activity;
	private static TextView speedText;
	private static Locale locale;
	private static Switch powerSwitch;
	private static ImageButton phoneButton, textButton, emailButton, contactButton;
	private static LinearLayout linearLayout1, linearLayout2;
	private static float nCurrentSpeed = (float)0.0F;
	private static Intent i;
	private static final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
	LocationService SpeedService;
	private static SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
	private static Editor editor = settings.edit();
	private static AlertDialog.Builder popUpMessage;
	private static AlertDialog alert;
	private static PackageManager pacman;
	
	private static float drivingSpeed = 2; // This is the Speed Limit we are setting for the device in mph.
	
	static DevicePolicyManager devicePolicyManager;
	ComponentName admin;
	
	// Launched when SafeSpeed.class object is created.
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "SafeSpeed.OnCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_speed);
		
		devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		admin = new ComponentName(this, AdminReceiver.class);
		
		speedText = (TextView) this.findViewById(R.id.speedText);
		powerSwitch = (Switch) this.findViewById(R.id.powerSwitch);
		phoneButton = (ImageButton) this.findViewById(R.id.phoneButton);
		textButton = (ImageButton) this.findViewById(R.id.textButton);
		emailButton = (ImageButton) this.findViewById(R.id.emailButton);
		contactButton = (ImageButton) this.findViewById(R.id.contactButton);
		linearLayout1 = (LinearLayout) this.findViewById(R.id.linearLayout1);
		linearLayout2 = (LinearLayout) this.findViewById(R.id.linearLayout2);

		activity = this;
		locale = Locale.getDefault();
		pacman = context.getPackageManager();
		
		// Initialize shared preferences
		editor.putBoolean("stopTracking", true);
		editor.putBoolean("isBound", false);
		editor.putBoolean("isAdmin", false);
		editor.putBoolean("isWaiting", false);
		editor.putBoolean("isOpen", false);
		editor.putInt("buttonLabel", 0);
		editor.commit();
		
		// Asks for admin permissions if not already acquired
		if(!settings.getBoolean("isAdmin", false)){
			Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
			intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
			intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Close App");
			startActivityForResult(intent, ACTIVATION_REQUEST);
		}
		
		// Power Switch onClick Listener
		powerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			    if (isChecked) {
			    	editor.putBoolean("stopTracking", false);
			    	editor.apply();
			    	doBind();
			    	linearLayout1.setVisibility(LinearLayout.VISIBLE);
			    	linearLayout2.setVisibility(LinearLayout.VISIBLE);
	        	}
			    else{
			    	editor.putBoolean("stopTracking", true);
			    	editor.apply();
			    	doUnbind();
			    	linearLayout1.setVisibility(LinearLayout.GONE);
			    	linearLayout2.setVisibility(LinearLayout.GONE);
			    	setLastSpeed(-1.0F);
			    }	
			}       
		});
		
		// Phone Call Button onClick Listener
		phoneButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				editor.putInt("buttonLabel", 1);
				editor.commit();
				if (powerSwitch.isChecked() && nCurrentSpeed > drivingSpeed) {
					warning();
				} else
					launchPhone();

			}
	    });
		
		// Text Message Button onClick Listener
		textButton.setOnClickListener(new OnClickListener() {
	        @Override
	        public void onClick(View v) {
	        	editor.putInt("buttonLabel", 2);
	    		editor.commit();
	        	if(powerSwitch.isChecked() && nCurrentSpeed > drivingSpeed){
	        		warning();
	        	}
	        	else
	        		launchText();
	        }
	    });
		
		// Email Button onClick Listener
		emailButton.setOnClickListener(new OnClickListener() {
	        @Override
	        public void onClick(View v) {
	        	editor.putInt("buttonLabel", 3);
	    		editor.commit();
	        	if(powerSwitch.isChecked() && nCurrentSpeed > drivingSpeed){
	        		warning();
	        	}
	        	else
	        		launchEmail();
	        	
	        }
	    });
		
		// Contact Button onClick Listener
		contactButton.setOnClickListener(new OnClickListener() {
	        @Override
	        public void onClick(View v) {
	        	//setLastSpeed(10); // use this as a trigger for tests to set a speed over the limit.
	        	editor.putInt("buttonLabel", 4);
	    		editor.commit();
	        	if(powerSwitch.isChecked() && nCurrentSpeed > drivingSpeed){
	        		warning();
	        	}
	        	else
	        		launchContact();
	        }
	    });
		
		// Builds the Alert Dialog to be called later.
		popUpMessage = new AlertDialog.Builder(activity);
		popUpMessage
		    .setTitle("Movement Detected")
		    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) {         	
		            editor.putBoolean("isOpen", false);
		            editor.commit();
		            Log.i(TAG, " isOpen: " + !settings.getBoolean("isOpen", false));
		            
		            dialog.cancel();
		            int flag = settings.getInt("buttonLabel", 0);
		            switch(flag){
		            case 1:
		            	launchPhone();
		            	break;
		            case 2:
		            	launchText();
		            	break;
		            case 3:
		            	launchEmail();
		            	break;
		            case 4:
		            	launchContact();
		            	break;
		            default:
		            	break;
		            }
		        }
		     })
		    
		    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) {
		            try {
		            	editor.putBoolean("isOpen", false);
			            editor.commit();
		            	devicePolicyManager.lockNow();
		            } catch (SecurityException ex) {
		                Toast.makeText(
		                        context, 
		                        "You must enable this app as a device administrator\n\n" +
		                        "Please enable it.",
		                        Toast.LENGTH_LONG).show();
		            }
		            dialog.cancel();
		        }
		     })
		    .setIcon(android.R.drawable.ic_dialog_alert);
			alert = popUpMessage.create();
			LayoutInflater inflater = getLayoutInflater();
			View dialogLayout = inflater.inflate(R.layout.popup, null);
			alert.setView(dialogLayout);
			
			alert.setOnShowListener(new DialogInterface.OnShowListener() {
	            @Override
	            public void onShow(DialogInterface d) {
	                ImageView image = (ImageView) alert.findViewById(R.id.popUpImage);
	                Bitmap icon = BitmapFactory.decodeResource(context.getResources(),
	                        R.drawable.car_crash1);
	                float imageWidthInPX = (float)image.getWidth();

	                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Math.round(imageWidthInPX),
	                        Math.round(imageWidthInPX * (float)icon.getHeight() / (float)icon.getWidth()));
	                image.setLayoutParams(layoutParams);
	            }
	        });
	}
	
	// Launched when SafeSpeed.class object is destroyed
	public void onDestroy(){
		super.onDestroy();
		doUnbind();
	}
	
	// Binds SafeSpeed to a LocationService 
	public void doBind(){
		boolean isBound = settings.getBoolean("isBound", false);
		if(!isBound){
    		i = new Intent(context, LocationService.class);
    		context.bindService(i, SpeedConnection, Context.BIND_AUTO_CREATE);
    		context.startService(i);
    		editor.putBoolean("isBound", true);
    		editor.apply();
    	}
	}
	
	// Unbinds SafeSpeed from a LocationService
	public void doUnbind(){
		boolean isBound = settings.getBoolean("isBound", false);
		if(isBound){
			context.unbindService(SpeedConnection);
			context.stopService(i);
			editor.putBoolean("isBound", false);
			editor.apply();
		}
	}
	
	// Attempts to launch the Warning Alert Dialog
	//		- Warning only pops if speed is > drivingSpeed variable
	public void warning() {
		Log.i(TAG, "SafeSpeed.warning()");
		Log.i(TAG, "Screen on: " + pm.isScreenOn() + " isOpen: " + !settings.getBoolean("isOpen", false));
	
		if(pm.isScreenOn() && !settings.getBoolean("isOpen", false)) {
			editor.putBoolean("isOpen", true);
		    editor.commit();
		            
		    alert.show();    
		}
	}
	
	// Launches default Telephone app/intent
	public void launchPhone(){
		Intent sendIntent = new Intent(Intent.ACTION_DIAL);
    	sendIntent.setData(Uri.parse("tel:"));
    	
    	final ResolveInfo mInfo = pacman.resolveActivity(sendIntent, 0);
    	String s = pacman.getApplicationLabel(mInfo.activityInfo.applicationInfo).toString();
    	
    	if (s != null && s.length() > 2) {
            startActivity(sendIntent);
            return;
        } else {
           	Toast.makeText(context, "No Phone Call App Availible", Toast.LENGTH_SHORT).show();
           	return;
        }
	}
	
	// Launches default Texting app
	public void launchText(){
		String s = Telephony.Sms.getDefaultSmsPackage(context);
        
        if (s != null && s.length() > 2) {
        	Intent sendIntent = pacman.getLaunchIntentForPackage(s);
        	startActivity(sendIntent);
        	return;
        } else {
           	Toast.makeText(context, "No Texting App Availible", Toast.LENGTH_SHORT).show();
           	return;
        }
	}
	
	// Launches default Email app
	public void launchEmail(){
		Intent sendIntent = new Intent(Intent.ACTION_MAIN);         
    	sendIntent.addCategory(Intent.CATEGORY_APP_EMAIL);

        if (sendIntent.resolveActivity(pacman) != null) {
        	startActivity(sendIntent);
        	return;
        } else {
           	Toast.makeText(context, "No Email App Availible", Toast.LENGTH_SHORT).show();
           	return;
        }
	}
	
	// Launches default Contact app
	public void launchContact(){
		Intent sendIntent = new Intent(Intent.ACTION_VIEW);
    	sendIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);

        if (sendIntent.resolveActivity(pacman) != null) {
         	startActivity(sendIntent);
         	return;
        } else {
           	Toast.makeText(context, "No Texting App Availible", Toast.LENGTH_SHORT).show();
           	return;
        }
	}

	// Gets speed from LocationService, and displays it to the speedometer
	public static void setLastSpeed(float speed) {
		float fakeSpeed = speed;
		Log.i(TAG, "SafeSpeed.setLastSpeed()");
		
		if (fakeSpeed < 0)
		{
			speedText.setText("-.-");
		}
		else
		{
			nCurrentSpeed = (float)(fakeSpeed * 2.2369);
			String s = String.format(locale, "%.1f", nCurrentSpeed);
			speedText.setText(s);
		}
	}

	// ServiceConnection used in binding to LocationService
	private ServiceConnection SpeedConnection = new ServiceConnection() {
	
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			SpeedBinder binder = (SpeedBinder) service;
			SpeedService = binder.getService();
			
			Log.i(TAG, "SafeSpeed.onServiceConnected()");
			Toast toast = Toast.makeText(context, "Service Connected", Toast.LENGTH_SHORT);
			toast.show();
		} 
	
		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "SafeSpeed.onServiceDisconnected()");
			Toast toast = Toast.makeText(context, "Service Disconnected", Toast.LENGTH_SHORT);
			toast.show();
		}
	};
	
}
