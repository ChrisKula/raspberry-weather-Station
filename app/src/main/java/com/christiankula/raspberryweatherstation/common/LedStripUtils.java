/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.christiankula.raspberryweatherstation.common;

import static com.google.android.things.contrib.driver.rainbowhat.RainbowHat.LEDSTRIP_LENGTH;

import android.graphics.Color;

/**
 * Helper methods for computing outputs on the Rainbow HAT
 */
public final class LedStripUtils {
    //"Watch face" colors for the 'Time' feature
    /* Material orange 500 */
    public static final int WATCH_FACE_COLOR = Color.parseColor("#FF9800");
    public static final int SECONDS_HAND_COLOR = Color.GREEN;

    /* Barometer Range Constants */
    private static final float BAROMETER_RANGE_LOW = 965.f;
    private static final float BAROMETER_RANGE_HIGH = 1035.f;

    public static final int LOW_TEMPERATURE_COLOR = Color.GREEN;
    public static final int MEDIUM_TEMPERATURE_COLOR = Color.YELLOW;
    public static final int HIGH_TEMPERATURE_COLOR = Color.RED;

    /* LED Strip Color Constants*/
    private static final int[] sTimeColors;
    private static final int[] sTemperatureGaugeColors;
    private static final int[] sRainbowColors;

    static {
        sTimeColors = new int[LEDSTRIP_LENGTH];

        for (int i = 0; i < sTimeColors.length; i++) {
            sTimeColors[i] = WATCH_FACE_COLOR;
        }
    }

    static {
        sTemperatureGaugeColors = new int[]{
                HIGH_TEMPERATURE_COLOR,
                MEDIUM_TEMPERATURE_COLOR,
                MEDIUM_TEMPERATURE_COLOR,
                LOW_TEMPERATURE_COLOR,
                LOW_TEMPERATURE_COLOR,
                LOW_TEMPERATURE_COLOR,
                LOW_TEMPERATURE_COLOR
        };
    }

    static {
        sRainbowColors = new int[LEDSTRIP_LENGTH];
        for (int i = 0; i < sRainbowColors.length; i++) {
            float[] hsv = {i * 360.f / sRainbowColors.length, 1.0f, 1.0f};
            sRainbowColors[i] = Color.HSVToColor(255, hsv);
        }
    }

    private LedStripUtils() {

    }


    /**
     * Return an array of colors for the LED strip based on the given second of a minute
     *
     * @param secondOfMinute second to compare
     * @return Array of colors with the position of the {@link LedStripUtils#SECONDS_HAND_COLOR} colored LED as the
     * second hand
     */
    public static int[] getTimeColors(int secondOfMinute) {
        int[] colors = sTimeColors.clone();

        int i = -1 * (secondOfMinute % LEDSTRIP_LENGTH) + 6;
        colors[i] = SECONDS_HAND_COLOR;

        return colors;
    }

    /**
     * Return an array of colors for the LED strip based on the given temperature
     *
     * @param temperature Temperature reading to compare.
     * @return Array of colors forming a sort of gauge
     */
    public static int[] getTemperatureColors(float temperature) {
        int n = Math.round(temperature / 7);
        n = Math.max(0, Math.min(n, sTemperatureGaugeColors.length));

        int[] colors = new int[LEDSTRIP_LENGTH];
        for (int i = 0; i < n; i++) {
            int ri = sTemperatureGaugeColors.length - 1 - i;
            colors[ri] = sTemperatureGaugeColors[ri];
        }

        return colors;
    }

    /**
     * Return an array of colors for the LED strip based on the given pressure.
     *
     * @param pressure Pressure reading to compare.
     * @return Array of rainbowy colors to set on the LED strip.
     */
    public static int[] getPressureStripColors(float pressure) {
        float t = (pressure - BAROMETER_RANGE_LOW) / (BAROMETER_RANGE_HIGH - BAROMETER_RANGE_LOW);
        int n = (int) Math.ceil(sRainbowColors.length * t);
        n = Math.max(0, Math.min(n, sRainbowColors.length));

        int[] colors = new int[sRainbowColors.length];
        for (int i = 0; i < n; i++) {
            int ri = sRainbowColors.length - 1 - i;
            colors[ri] = sRainbowColors[ri];
        }

        return colors;
    }

    /**
     * Return an array of colors for the LED strip to represent turned off LEDs
     *
     * @return Array of {@link Color#BLACK} to represent turned off LEDs
     */
    public static int[] getTurnedOffColors() {
        int[] colors = new int[LEDSTRIP_LENGTH];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = Color.BLACK;
        }

        return colors;
    }
}
