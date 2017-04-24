package com.christiankula.raspberryweatherstation.weatherstation;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

import com.christiankula.raspberryweatherstation.common.LedStripUtils;
import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.pio.Gpio;

import org.joda.time.DateTime;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WeatherStation {
    private static final String TAG = WeatherStation.class.getSimpleName();

    private static final int LED_STRIP_BRIGHTNESS = 1;


    private static WeatherStation instance;

    private AlphanumericDisplay alphanumericDisplay;
    private Bmx280SensorDriver environmentSensor;
    private Apa102 ledStrip;

    private Button timeButton;
    private Button temperatureButton;
    private Button pressureButton;

    private Gpio redLed;
    private Gpio greenLed;
    private Gpio blueLed;

    private Handler updateTimeHandler;
    private Runnable updateTimeRunnable;
    private SensorEventListener sensorEventListener;

    private SensorManager sensorManager;

    private WeatherStationState state;

    private WeatherStation(Context context) {
        try {
            alphanumericDisplay = RainbowHat.openDisplay();
            alphanumericDisplay.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            alphanumericDisplay.setEnabled(true);
            alphanumericDisplay.display("Init");
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing alphanumeric display", e);
            throw new RuntimeException(e);
        }

        try {
            environmentSensor = RainbowHat.createSensorDriver();
            environmentSensor.registerTemperatureSensor();
            environmentSensor.registerPressureSensor();
        } catch (IOException e) {
            Log.e(TAG, "Error initializing sensor", e);
            throw new RuntimeException(e);
        }

        try {
            ledStrip = RainbowHat.openLedStrip();
            ledStrip.setBrightness(LED_STRIP_BRIGHTNESS);
        } catch (IOException e) {
            Log.d(TAG, "Error while opening LED strip", e);
        }

        try {
            redLed = RainbowHat.openLed(RainbowHat.LED_RED);
            greenLed = RainbowHat.openLed(RainbowHat.LED_GREEN);
            blueLed = RainbowHat.openLed(RainbowHat.LED_BLUE);

            redLed.setValue(true);
            greenLed.setValue(false);
            blueLed.setValue(false);

            state = WeatherStationState.TIME;
        } catch (IOException e) {
            Log.d(TAG, "Error while opening LEDs");
        }

        try {
            timeButton = new Button(RainbowHat.BUTTON_A, Button.LogicState.PRESSED_WHEN_LOW);
            timeButton.setOnButtonEventListener(new TimeButtonListener());
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing Button A", e);
            throw new RuntimeException(e);
        }

        try {
            temperatureButton = new Button(RainbowHat.BUTTON_B, Button.LogicState.PRESSED_WHEN_LOW);
            temperatureButton.setOnButtonEventListener(new TemperatureButtonListener());
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing Button B", e);
            throw new RuntimeException(e);
        }

        try {
            pressureButton = new Button(RainbowHat.BUTTON_C, Button.LogicState.PRESSED_WHEN_LOW);
            pressureButton.setOnButtonEventListener(new PressureButtonListener());
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing Button B", e);
            throw new RuntimeException(e);
        }

        updateTimeHandler = new Handler();
        updateTimeRunnable = new UpdateTimeRunnable();
        sensorEventListener = new TemperaturePressureSensorEventListener();

        sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);

        Sensor temperatureSensor = sensorManager.getDynamicSensorList(Sensor.TYPE_AMBIENT_TEMPERATURE).get(0);
        sensorManager.registerListener(sensorEventListener, temperatureSensor, SensorManager.SENSOR_DELAY_NORMAL);

        Sensor pressureSensor = sensorManager.getDynamicSensorList(Sensor.TYPE_PRESSURE).get(0);
        sensorManager.registerListener(sensorEventListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public static WeatherStation getInstance(Context context) {
        if (instance == null) {
            instance = new WeatherStation(context);
        }

        return instance;
    }

    public void startClock() {
        updateTimeHandler.post(updateTimeRunnable);
    }

    public void stopClock() {
        updateTimeHandler.removeCallbacks(updateTimeRunnable);
    }

    private void updateTemperatureDisplay(float temperature) {
        if (alphanumericDisplay != null && state == WeatherStationState.TEMPERATURE) {
            try {
                alphanumericDisplay.display((int) temperature + " C");
                ledStrip.write(LedStripUtils.getTemperatureColors(temperature));
            } catch (IOException e) {
                Log.e(TAG, "Error displaying temperature", e);
            }
        }
    }

    private void updateBarometerDisplay(float pressure) {
        if (alphanumericDisplay != null && state == WeatherStationState.PRESSURE) {
            try {
                alphanumericDisplay.display(pressure);
                ledStrip.write(LedStripUtils.getPressureStripColors(pressure));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        stopClock();
        sensorManager.unregisterListener(sensorEventListener);

        try {
            alphanumericDisplay.clear();
            alphanumericDisplay.close();

            environmentSensor.close();

            ledStrip.close();

            redLed.close();
            greenLed.close();
            blueLed.close();

            timeButton.close();
            temperatureButton.close();
            pressureButton.close();
        } catch (IOException e) {
            Log.e(TAG, "Error while clearing and closing all peripherals", e);
        }
    }


    private enum WeatherStationState {
        TIME,
        TEMPERATURE,
        PRESSURE;
    }

    private class UpdateTimeRunnable implements Runnable {
        @Override
        public void run() {
            if (alphanumericDisplay != null && state == WeatherStationState.TIME) {
                DateTime lt = new DateTime();

                int hours = lt.getHourOfDay() + 2;
                hours = hours >= 24 ? hours - 24 : hours;

                String h = hours >= 10 ? "" + hours : "0" + hours;
                String min = lt.getMinuteOfHour() > 9 ? "" + lt.getMinuteOfHour() : "0" + lt.getMinuteOfHour();

                try {
                    alphanumericDisplay.display(h + min);
                } catch (IOException e) {
                    Log.e(TAG, "Error while displaying hour on alphanumeric display", e);
                }
            }
            updateTimeHandler.postDelayed(this, TimeUnit.MILLISECONDS.toMillis(20));
        }
    }

    private class TimeButtonListener implements Button.OnButtonEventListener {

        @Override
        public void onButtonEvent(Button button, boolean pressed) {
            if (pressed) {
                Log.d(TAG, "on Time button event");

                try {
                    redLed.setValue(true);
                    greenLed.setValue(false);
                    blueLed.setValue(false);

                    state = WeatherStationState.TIME;
                } catch (IOException e) {
                    Log.d(TAG, "Error while turning on red LED (Time)");
                }
            }
        }
    }

    private class TemperatureButtonListener implements Button.OnButtonEventListener {

        @Override
        public void onButtonEvent(Button button, boolean pressed) {
            if (pressed) {
                Log.d(TAG, "on Temperature button event");

                try {
                    redLed.setValue(false);
                    greenLed.setValue(true);
                    blueLed.setValue(false);

                    state = WeatherStationState.TEMPERATURE;
                } catch (IOException e) {
                    Log.d(TAG, "Error while turning on green LED (Temperature)");
                }
            }
        }
    }

    private class PressureButtonListener implements Button.OnButtonEventListener {

        @Override
        public void onButtonEvent(Button button, boolean pressed) {
            if (pressed) {
                Log.d(TAG, "on Pressure button event");

                try {
                    redLed.setValue(false);
                    greenLed.setValue(false);
                    blueLed.setValue(true);

                    state = WeatherStationState.PRESSURE;
                } catch (IOException e) {
                    Log.d(TAG, "Error while turning on blue LED (Pressure)");
                }
            }
        }
    }

    private class TemperaturePressureSensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_AMBIENT_TEMPERATURE:
                    updateTemperatureDisplay(event.values[0]);
                    break;
                case Sensor.TYPE_PRESSURE:
                    updateBarometerDisplay(event.values[0]);
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
}
