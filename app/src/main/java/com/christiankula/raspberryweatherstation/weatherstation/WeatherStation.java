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

public class WeatherStation {
    private static final String TAG = WeatherStation.class.getSimpleName();

    private static WeatherStation instance;

    private static final int LED_STRIP_BRIGHTNESS = 1;

    private static final int DEFAULT_REFRESH_RATE_MS = 200;

    private final AlphanumericDisplay alphanumericDisplay;
    private final Bmx280SensorDriver environmentSensor;
    private final Apa102 ledStrip;

    private final Button timeButtonA;
    private final Button temperatureButtonB;
    private final Button pressureButtonC;

    private final Gpio timeRedLed;
    private final Gpio temperatureGreenLed;
    private final Gpio pressureBlueLed;

    private final SensorManager sensorManager;
    private final Sensor temperatureSensor;
    private final Sensor pressureSensor;

    private final Handler updateTimeHandler;
    private final Runnable updateTimeRunnable;
    private final SensorEventListener sensorEventListener;

    private WeatherStationState currentState;

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
            Log.e(TAG, "Error while opening LED strip", e);
            throw new RuntimeException(e);
        }

        try {
            timeRedLed = RainbowHat.openLed(RainbowHat.LED_RED);
            temperatureGreenLed = RainbowHat.openLed(RainbowHat.LED_GREEN);
            pressureBlueLed = RainbowHat.openLed(RainbowHat.LED_BLUE);

            timeRedLed.setValue(true);
            temperatureGreenLed.setValue(false);
            pressureBlueLed.setValue(false);

            currentState = WeatherStationState.TIME;
        } catch (IOException e) {
            Log.e(TAG, "Error while opening LEDs", e);
            throw new RuntimeException(e);
        }

        try {
            timeButtonA = new Button(RainbowHat.BUTTON_A, Button.LogicState.PRESSED_WHEN_LOW);
            timeButtonA.setOnButtonEventListener(new TimeButtonListener());
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing Button A", e);
            throw new RuntimeException(e);
        }

        try {
            temperatureButtonB = new Button(RainbowHat.BUTTON_B, Button.LogicState.PRESSED_WHEN_LOW);
            temperatureButtonB.setOnButtonEventListener(new TemperatureButtonListener());
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing Button B", e);
            throw new RuntimeException(e);
        }

        try {
            pressureButtonC = new Button(RainbowHat.BUTTON_C, Button.LogicState.PRESSED_WHEN_LOW);
            pressureButtonC.setOnButtonEventListener(new PressureButtonListener());
        } catch (IOException e) {
            Log.e(TAG, "Error while initializing Button B", e);
            throw new RuntimeException(e);
        }

        updateTimeHandler = new Handler();
        updateTimeRunnable = new UpdateTimeRunnable();
        sensorEventListener = new TemperaturePressureSensorEventListener();

        sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        temperatureSensor = sensorManager.getDynamicSensorList(Sensor.TYPE_AMBIENT_TEMPERATURE).get(0);
        pressureSensor = sensorManager.getDynamicSensorList(Sensor.TYPE_PRESSURE).get(0);
    }

    public static WeatherStation getInstance(Context context) {
        if (instance == null) {
            instance = new WeatherStation(context);
        }

        return instance;
    }

    public void start() {
        if (currentState == WeatherStationState.CLOSED) {
            throw new NullPointerException("Can't start the WeatherStation because instance was previously closed. "
                    + "Open it again with 'getInstance()'");
        }

        startClock();
        startTemperatureListening();
        startPressureListening();

        Log.d(TAG, "WeatherStation started");
    }

    public void stop() {
        if (currentState == WeatherStationState.CLOSED) {
            throw new NullPointerException("Can't stop the WeatherStation because instance is already closed. "
                    + "Open it again with 'getInstance()'");
        }

        stopClock();
        stopTemperatureListening();
        stopPressureListening();

        Log.d(TAG, "WeatherStation stopped");
    }

    public void close() {
        stop();

        try {
            clearAllPeripherals();
            alphanumericDisplay.close();

            environmentSensor.close();

            ledStrip.close();

            timeRedLed.close();
            temperatureGreenLed.close();
            pressureBlueLed.close();

            timeButtonA.close();
            temperatureButtonB.close();
            pressureButtonC.close();
        } catch (IOException e) {
            Log.e(TAG, "Error while clearing and closing all peripherals", e);
        }

        instance = null;
        currentState = WeatherStationState.CLOSED;

        Log.d(TAG, "WeatherStation closed");
    }

    private void clearAllPeripherals() throws IOException {
        alphanumericDisplay.clear();

        ledStrip.write(LedStripUtils.getTurnedOffColors());

        timeRedLed.setValue(false);
        temperatureGreenLed.setValue(false);
        pressureBlueLed.setValue(false);
    }

    private void startClock() {
        updateTimeHandler.post(updateTimeRunnable);
    }

    private void stopClock() {
        updateTimeHandler.removeCallbacks(updateTimeRunnable);
    }

    private boolean startTemperatureListening() {
        return sensorManager.registerListener(sensorEventListener, temperatureSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopTemperatureListening() {
        sensorManager.unregisterListener(sensorEventListener, temperatureSensor);
    }

    private boolean startPressureListening() {
        return sensorManager.registerListener(sensorEventListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopPressureListening() {
        sensorManager.unregisterListener(sensorEventListener, pressureSensor);
    }

    private void updateTemperatureDisplay(float temperature) {
        if (alphanumericDisplay != null && currentState == WeatherStationState.TEMPERATURE) {
            try {
                alphanumericDisplay.display((int) temperature + " C");
                ledStrip.write(LedStripUtils.getTemperatureColors(temperature));
            } catch (IOException e) {
                Log.e(TAG, "Error while displaying temperature", e);
            }
        }
    }

    private void updateBarometerDisplay(float pressure) {
        if (alphanumericDisplay != null && currentState == WeatherStationState.PRESSURE) {
            try {
                alphanumericDisplay.display(pressure);
                ledStrip.write(LedStripUtils.getPressureStripColors(pressure));
            } catch (IOException e) {
                Log.e(TAG, "Error while display pressure", e);
            }
        }
    }


    private enum WeatherStationState {
        TIME,
        TEMPERATURE,
        PRESSURE,
        CLOSED
    }

    private class UpdateTimeRunnable implements Runnable {
        @Override
        public void run() {
            if (currentState == WeatherStationState.TIME) {
                DateTime lt = new DateTime();

                int hours = lt.getHourOfDay() + 2;
                hours = hours >= 24 ? hours - 24 : hours;

                int min = lt.getMinuteOfHour();

                String h = hours >= 10 ? "" + hours : "0" + hours;
                String m = min > 9 ? "" + min : "0" + min;

                String time = lt.getSecondOfMinute() % 2 == 0 ? h + m : h + "." + m;

                try {
                    alphanumericDisplay.display(time);
                    ledStrip.write(LedStripUtils.getTimeColors(lt.getSecondOfMinute()));
                } catch (IOException e) {
                    Log.e(TAG, "Error while displaying hour on alphanumeric display", e);
                }
            }
            updateTimeHandler.postDelayed(this, DEFAULT_REFRESH_RATE_MS);
        }
    }

    private class TimeButtonListener implements Button.OnButtonEventListener {

        @Override
        public void onButtonEvent(Button button, boolean pressed) {
            if (pressed) {
                Log.d(TAG, "on Time button event");

                try {
                    timeRedLed.setValue(true);
                    temperatureGreenLed.setValue(false);
                    pressureBlueLed.setValue(false);

                    currentState = WeatherStationState.TIME;
                } catch (IOException e) {
                    Log.e(TAG, "Error while turning on red LED (Time)");
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
                    timeRedLed.setValue(false);
                    temperatureGreenLed.setValue(true);
                    pressureBlueLed.setValue(false);

                    currentState = WeatherStationState.TEMPERATURE;
                } catch (IOException e) {
                    Log.e(TAG, "Error while turning on green LED (Temperature)");
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
                    timeRedLed.setValue(false);
                    temperatureGreenLed.setValue(false);
                    pressureBlueLed.setValue(true);

                    currentState = WeatherStationState.PRESSURE;
                } catch (IOException e) {
                    Log.e(TAG, "Error while turning on blue LED (Pressure)");
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
            //Do nothing because not interested in it
        }
    }
}
