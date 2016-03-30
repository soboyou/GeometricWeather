package wangdaye.com.geometricweather;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

/**
 * Created by yuyidong on 16/3/30.
 */
public class WeatherApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LeakCanary.install(this);
    }
}
