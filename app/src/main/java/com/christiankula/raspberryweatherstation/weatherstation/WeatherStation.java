package com.christiankula.raspberryweatherstation.weatherstation;

import android.os.Handler;
import android.util.Log;

import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;

import org.joda.time.DateTime;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WeatherStation {
    private static final String TAG = WeatherStation.class.getSimpleName();

    private static WeatherStation instance;


    private AlphanumericDisplay alphanumericDisplay;

    private Handler updateTimeHandler;
    private Runnable updateTimeRunnable;

    private WeatherStation() {
        try {
            alphanumericDisplay = RainbowHat.openDisplay();
            alphanumericDisplay.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            alphanumericDisplay.setEnabled(true);
            alphanumericDisplay.display("Init");

        } catch (IOException e) {
            Log.e(TAG, "Error while initializing alphanumeric display", e);
            throw new RuntimeException(e);
        }

        updateTimeHandler = new Handler();
        updateTimeRunnable = new UpdateTimeRunnable();
    }

    public static WeatherStation getInstance() {
        if (instance == null) {
            instance = new WeatherStation();
        }
        return instance;
    }


    public void startClock() {
        updateTimeHandler.post(updateTimeRunnable);
    }

    public void stopClock() {
        updateTimeHandler.removeCallbacks(updateTimeRunnable);
    }


    public void close() {
        stopClock();
        try {
            alphanumericDisplay.clear();
            alphanumericDisplay.close();
        } catch (IOException e) {
            Log.e(TAG, "Error while clearing and closing alphanumeric display", e);
        }
    }

    private class UpdateTimeRunnable implements Runnable {
        @Override
        public void run() {
            if (alphanumericDisplay != null) {

                DateTime lt = new DateTime();

                int hours = lt.getHourOfDay() + 2;
                hours = hours >= 24 ? hours - 24 : hours;

                String h = hours >= 10 ? "" + hours : "0" + hours;
                String min = lt.getMinuteOfHour() > 9 ? "" + lt.getMinuteOfHour() : "0" + lt.getMinuteOfHour();

                try {
                    alphanumericDisplay.display(h + min);
                    updateTimeHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
                } catch (IOException e) {
                    Log.e(TAG, "Error while displaying hour on alphanumeric display", e);
                }
            }
        }
    }
}
