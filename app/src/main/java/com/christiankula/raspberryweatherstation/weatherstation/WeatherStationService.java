package com.christiankula.raspberryweatherstation.weatherstation;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;


public class WeatherStationService extends Service {

    private final static String TAG = WeatherStationService.class.getSimpleName();

    private WeatherStation weatherStation;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WeatherStationService created");
        weatherStation = WeatherStation.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WeatherStationService started");
        weatherStation.startClock();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        weatherStation.close();
    }
}
