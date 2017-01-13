package com.gradproj.SafeSpeed.service;

import java.util.HashMap;
import java.util.Map;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.gradproj.SafeSpeed.MyApp;
import com.gradproj.SafeSpeed.SafeSpeed;

/////////////////////////////////////////////////////////////////////////
//-----------------------------------------------------------------------
//-- LocationService Class
//-----------------------------------------------------------------------
//This class will 
//
//
//
//
/////////////////////////////////////////////////////////////////////////
public class LocationService extends Service {

	private static final String TAG = "SafeSpeed";
	private final IBinder binder = new SpeedBinder();
	
	private LocationManager manager;
	private String provider;
	private Map<String, ProviderListener> providerListeners = new HashMap<String, ProviderListener>();
	private boolean tracking;
	private Context context;
	
	private static SharedPreferences settings = MyApp.getAppContext().getSharedPreferences(SafeSpeed.PREFS_NAME, 0);

	private String getBestProvider() {
		Criteria localCriteria = new Criteria();
		localCriteria.setAccuracy(1);
		localCriteria.setAltitudeRequired(false);

		localCriteria.setCostAllowed(true);
		localCriteria.setPowerRequirement(3);
		return this.manager.getBestProvider(localCriteria, true);
	}

	public void UpdateSpeed(ProviderListener paramProviderListener) {
		Log.i(TAG, "LocationService.UpdateLocation()");
		ProviderListener localProviderListener = paramProviderListener;
		
		if (!localProviderListener.isProviderEnabled()) {
			UpdatedLocationServicesStatus();
			return;
		}

		if (settings.getBoolean("stopTracking", false)) {
			stopTracking();
			return;
		}

		SafeSpeed.setLastSpeed(localProviderListener.speed);
	}

	public void UpdatedLocationServicesStatus() {
		Log.i(TAG, "LocationService.UpdateLocationServicesStatus()");
		if(this.tracking)
			stopTracking();
		
		if (this.manager.isProviderEnabled("passive")){
			if (this.manager.isProviderEnabled("gps") || this.manager.isProviderEnabled("network")) {
				this.provider = getBestProvider();
				Log.i(TAG, "Best Provider is: " + provider);
				return;
			}
			this.provider = "passive";
			return;
		}
		else{
			Toast toast = Toast.makeText(context, "Please Enable Location Services", Toast.LENGTH_SHORT);
			toast.show();
		}
	}

	public boolean isTracking() {
		return this.tracking;
	}
	

	public void onCreate() {
		Log.i(TAG, "LocationService.OnCreate()");
		super.onCreate();
		
		context = MyApp.getAppContext();
		this.manager = ((LocationManager) getSystemService("location"));
		this.providerListeners.put("passive", new ProviderListener("passive", this.manager, this));
		this.providerListeners.put("network", new ProviderListener("network", this.manager, this));
		this.providerListeners.put("gps", new ProviderListener("gps", this.manager, this));
		
		Toast toast = Toast.makeText(context, "Service Started", Toast.LENGTH_SHORT);
		toast.show();
	}
	

	public void onDestroy() {
		Log.i(TAG, "LocationService.OnDestroy()");
		stopTracking();
		Toast toast = Toast.makeText(context, "Destroying Service", Toast.LENGTH_SHORT);
		toast.show();
		super.onDestroy();
	}

	
	public int onStartCommand(Intent paramIntent, int paramInt1, int paramInt2) {
		Log.i(TAG, "LocationService.OnStartCommand()");
		
		if (!this.tracking) {
			startTracking();
		}
		return super.onStartCommand(paramIntent, paramInt1, paramInt2);
	}	

	public void startTracking() {
		Log.i(TAG, "LocationService.startTracking()");
		UpdatedLocationServicesStatus();
		((ProviderListener) this.providerListeners.get(provider)).StartTracking(3000, 0);
		this.tracking = true;
	}
	

	public void stopTracking() {
		Log.i(TAG, "LocationService.stopTracking()");
		
		((ProviderListener) this.providerListeners.get("passive")).StopTracking();
		((ProviderListener) this.providerListeners.get("network")).StopTracking();
		((ProviderListener) this.providerListeners.get("gps")).StopTracking();
		
		this.tracking = false;
	}

/////////////////////////////////////////////////////////////////////////
//-----------------------------------------------------------------------
//-- ProviderListener Class
//-----------------------------------------------------------------------
//This class will act as our LocationListener. Once the best location 
//data provider has been determined (gps, network, or passive), a 
//location listener is created for that service. As the location changes,
//the ProviderListener updates the LocationService class on the speed of
//the device.
/////////////////////////////////////////////////////////////////////////
	private class ProviderListener implements LocationListener {
		private LocationManager manager;
		public LocationService owner;
		public String provider = "";
		public float speed = -1.0F;

		private ProviderListener(String paramString, LocationManager paramLocationManager, LocationService paramLocationService) {
			this.provider = paramString;
			this.manager = paramLocationManager;
			this.owner = paramLocationService;
		}

		public void StartTracking(int paramInt1, int paramInt2) {
			this.manager.requestLocationUpdates(this.provider, paramInt1, paramInt2, this);
			onLocationChanged(this.manager.getLastKnownLocation(this.provider));
		}

		public void StopTracking() {
			this.manager.removeUpdates(this);
		}

		public boolean isProviderEnabled() {
			return this.manager.isProviderEnabled(this.provider);
		}

		public void onLocationChanged(Location paramLocation) {
			Log.i(TAG, "Provider(" + this.provider + ").onLocationChanged()");
			if (paramLocation != null) {
				try {
					this.speed = paramLocation.getSpeed();
					this.owner.UpdateSpeed(this);
					return;
				} catch (Exception e) {
				}
			}
		}

		public void onProviderDisabled(String paramString) {
			this.owner.UpdatedLocationServicesStatus();
		}

		public void onProviderEnabled(String paramString) {
			this.owner.UpdatedLocationServicesStatus();
		}

		public void onStatusChanged(String paramString, int paramInt, Bundle paramBundle) {
			this.owner.UpdatedLocationServicesStatus();
		}
	}
	
	public IBinder onBind(Intent paramIntent) {
		return binder;
	}
	
	public class SpeedBinder extends Binder {
		public SpeedBinder() {}

		public LocationService getService()
		{
			return LocationService.this;
		}
	}
}
