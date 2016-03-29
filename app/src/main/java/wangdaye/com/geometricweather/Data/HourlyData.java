package wangdaye.com.geometricweather.Data;

/**
 * Hourly data.
 * */

public class HourlyData {
    public int[] temp;
    public float[] pop;

    public HourlyData(int[] temp, float[] pop) {
        this.temp = temp;
        this.pop = pop;
    };
}
