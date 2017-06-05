package com.christiankula.raspberryweatherstation;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.christiankula.raspberryweatherstation.weatherstation.WeatherStation;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private WeatherStation weatherStation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        weatherStation = WeatherStation.getInstance(this);
        weatherStation.start();
    }

    @Override
    protected void onDestroy() {
        weatherStation.close();
        super.onDestroy();
    }
}
