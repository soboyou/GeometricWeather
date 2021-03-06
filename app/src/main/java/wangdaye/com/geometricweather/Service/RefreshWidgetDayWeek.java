package wangdaye.com.geometricweather.Service;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.Poi;

import java.util.Calendar;
import java.util.List;

import wangdaye.com.geometricweather.Data.HefengResult;
import wangdaye.com.geometricweather.Data.HefengWeather;
import wangdaye.com.geometricweather.Data.WeatherInfoToShow;
import wangdaye.com.geometricweather.View.MainActivity;
import wangdaye.com.geometricweather.Data.JuheResult;
import wangdaye.com.geometricweather.Data.JuheWeather;
import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.Receiver.WidgetProviderDayWeek;
import wangdaye.com.geometricweather.Widget.HandlerContainer;
import wangdaye.com.geometricweather.Widget.SafeHandler;

/**
 * A service to refresh widget [day + week].
 * */

public class RefreshWidgetDayWeek extends Service
        implements HandlerContainer{
    // data
    private String locationName;
    private JuheResult juheResult;
    private HefengResult hefengResult;
    private boolean isDay;

    private final int REFRESH_DATA_SUCCEED = 1;
    private final int REFRESH_DATA_FAILED = 0;

    // baidu location
    public LocationClient mLocationClient = null;
    public BDLocationListener myListener = new MyLocationListener();

    // handler
    private SafeHandler<RefreshWidgetDayWeek> safeHandler;

    //TAG
//    private final String TAG = "RefreshWidgetDayWeek";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.safeHandler = new SafeHandler<>(this);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        isDay = 5 < hour && hour < 19;

        SharedPreferences sharedPreferences = this.getSharedPreferences(
                getString(R.string.sp_widget_day_week_setting),
                Context.MODE_PRIVATE);
        this.locationName = sharedPreferences.getString(getString(R.string.key_location), getString(R.string.local));

        RefreshWidgetDayWeek.refreshUIFromLocalData(this, isDay);
        this.refreshWidget();

        this.stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void refreshWidget() {
        if(locationName.equals(getString(R.string.local))) {
            mLocationClient = new LocationClient(this); // 声明LocationClient类
            mLocationClient.registerLocationListener( myListener ); // 注册监听函数

            this.initBaiduMap();
        } else {
            this.getWeather(locationName);
        }
    }

    private void getWeather(final String searchLocation) {
        Thread thread=new Thread(new Runnable()
        {
            @Override
            public void run()
            { // TODO Auto-generated method stub
                if (searchLocation.replaceAll(" ", "").matches("[a-zA-Z]+")) {
                    hefengResult = HefengWeather.requestInternationalData(searchLocation);
                } else {
                    juheResult = JuheWeather.getRequest(searchLocation);
                }

                Message message=new Message();
                if (searchLocation.replaceAll(" ", "").matches("[a-zA-Z]+") && hefengResult == null) {
                    message.what = REFRESH_DATA_FAILED;
                } else if (searchLocation.replaceAll(" ", "").matches("[a-zA-Z]+") && ! hefengResult.heWeather.get(0).status.equals("ok")) {
                    message.what = REFRESH_DATA_FAILED;
                } else if (! searchLocation.replaceAll(" ", "").matches("[a-zA-Z]+") && juheResult == null) {
                    message.what = REFRESH_DATA_FAILED;
                } else if (! searchLocation.replaceAll(" ", "").matches("[a-zA-Z]+") && ! juheResult.error_code.equals("0")) {
                    message.what = REFRESH_DATA_FAILED;
                } else {
                    message.what = REFRESH_DATA_SUCCEED;
                }
                safeHandler.sendMessage(message);
            }
        });
        thread.start();
    }

    private void initBaiduMap() {
        // initialize baidu location
        mLocationClient.registerLocationListener( myListener );    //注册监听函数
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Battery_Saving);//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        option.setCoorType("bd09ll");//可选，默认gcj02，设置返回的定位结果坐标系
        option.setScanSpan(0);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
        option.setIsNeedAddress(true);//可选，设置是否需要地址信息，默认不需要
        option.setOpenGps(true);//可选，默认false,设置是否使用gps
        option.setLocationNotify(true);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        option.setIsNeedLocationDescribe(true);//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        option.setIsNeedLocationPoiList(true);//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        option.setIgnoreKillProcess(false);//可选，默认false，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认杀死
        option.SetIgnoreCacheException(false);//可选，默认false，设置是否收集CRASH信息，默认收集
        option.setEnableSimulateGps(false);//可选，默认false，设置是否需要过滤gps仿真结果，默认需要
        mLocationClient.setLocOption(option);
        mLocationClient.start();
    }

    private void refreshUI() {
        WeatherInfoToShow info = null;
        if (locationName.replaceAll(" ", "").matches("[a-zA-Z]+")) {
            if (hefengResult != null) {
                if (hefengResult.heWeather.get(0).status.equals("ok")) {
                    info = HefengWeather.getWeatherInfoToShow(this, hefengResult, isDay);
                }
            }
        } else {
            if (juheResult != null) {
                if (juheResult.error_code.equals("0")) {
                    info = JuheWeather.getWeatherInfoToShow(this, juheResult, isDay);
                }
            }
        }
        if(this.juheResult == null && this.hefengResult == null) {
            Toast.makeText(this, getString(R.string.refresh_widget_error), Toast.LENGTH_SHORT).show();
        } else {
            RefreshWidgetDayWeek.refreshUIFromInternet(this, info, isDay);
        }
    }

    public static void refreshUIFromInternet(Context context, WeatherInfoToShow info, boolean isDay) {
        if (info == null) {
            return;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_day_week);

        int imageId[] = JuheWeather.getWeatherIcon(info.weatherKindNow, isDay);
        views.setImageViewResource(R.id.widget_day_image, imageId[3]);

        String weatherTextNow = info.weatherNow + "\n" + info.tempNow + "℃";
        views.setTextViewText(R.id.widget_day_weather, weatherTextNow);

        String weatherTextTemp = info.miniTemp[0] + "°" + "\n" + info.maxiTemp[0] + "°";
        views.setTextViewText(R.id.widget_day_temp, weatherTextTemp);

        String refreshText = info.location + "." + info.refreshTime;
        views.setTextViewText(R.id.widget_day_time, refreshText);

        // icon
        imageId = JuheWeather.getWeatherIcon(info.weatherKind[1], isDay);
        views.setImageViewResource(R.id.widget_4_day_image_1, imageId[3]);
        imageId = JuheWeather.getWeatherIcon(info.weatherKind[2], isDay);
        views.setImageViewResource(R.id.widget_4_day_image_2, imageId[3]);
        imageId = JuheWeather.getWeatherIcon(info.weatherKind[3], isDay);
        views.setImageViewResource(R.id.widget_4_day_image_3, imageId[3]);
        imageId = JuheWeather.getWeatherIcon(info.weatherKind[4], isDay);
        views.setImageViewResource(R.id.widget_4_day_image_4, imageId[3]);

        // temperature
        views.setTextViewText(R.id.widget_4_day_temp_1, info.miniTemp[1] + "/" + info.maxiTemp[1] + "°");
        views.setTextViewText(R.id.widget_4_day_temp_2, info.miniTemp[2] + "/" + info.maxiTemp[2] + "°");
        views.setTextViewText(R.id.widget_4_day_temp_3, info.miniTemp[3] + "/" + info.maxiTemp[3] + "°");
        views.setTextViewText(R.id.widget_4_day_temp_4, info.miniTemp[4] + "/" + info.maxiTemp[4] + "°");

        // week
        views.setTextViewText(R.id.widget_4_day_week_1, info.week[1]);
        views.setTextViewText(R.id.widget_4_day_week_2, info.week[2]);
        views.setTextViewText(R.id.widget_4_day_week_3, info.week[3]);
        views.setTextViewText(R.id.widget_4_day_week_4, info.week[4]);

        SharedPreferences sharedPreferences = context.getSharedPreferences(
                context.getString(R.string.sp_widget_day_week_setting),
                Context.MODE_PRIVATE);
        boolean showCard = sharedPreferences.getBoolean(context.getString(R.string.key_show_card), false);
        boolean hideRefreshTime = sharedPreferences.getBoolean(context.getString(R.string.key_hide_refresh_time), false);
        boolean blackText = sharedPreferences.getBoolean(context.getString(R.string.key_black_text), false);
        if (hideRefreshTime) {
            views.setViewVisibility(R.id.widget_day_time, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_day_time, View.VISIBLE);
        }
        if (blackText) {
            // today text
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(context, R.color.colorTextDark));
            // week text
            views.setTextColor(R.id.widget_4_day_week_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_4, ContextCompat.getColor(context, R.color.colorTextDark));
            // temperature text
            views.setTextColor(R.id.widget_4_day_temp_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_4, ContextCompat.getColor(context, R.color.colorTextDark));
        } else {
            // today text
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(context, R.color.colorTextLight));
            // week text
            views.setTextColor(R.id.widget_4_day_week_1, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_week_2, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_week_3, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_week_4, ContextCompat.getColor(context, R.color.colorTextLight));
            // temperature text
            views.setTextColor(R.id.widget_4_day_temp_1, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_temp_2, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_temp_3, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_temp_4, ContextCompat.getColor(context, R.color.colorTextLight));
        }
        if (showCard) {
            views.setViewVisibility(R.id.widget_day_week_card, View.VISIBLE);
            // today text
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(context, R.color.colorTextDark));
            // week text
            views.setTextColor(R.id.widget_4_day_week_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_4, ContextCompat.getColor(context, R.color.colorTextDark));
            // temperature text
            views.setTextColor(R.id.widget_4_day_temp_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_4, ContextCompat.getColor(context, R.color.colorTextDark));
        } else {
            views.setViewVisibility(R.id.widget_day_week_card, View.GONE);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_day_week_button, pendingIntent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        appWidgetManager.updateAppWidget(new ComponentName(context, WidgetProviderDayWeek.class), views);

        SharedPreferences.Editor editor = context.getSharedPreferences(
                context.getString(R.string.sp_widget_day_week_setting), Context.MODE_PRIVATE).edit();
        editor.putBoolean(context.getString(R.string.key_saved_data), true);
        editor.putString(context.getString(R.string.key_weather_kind_today), info.weatherKindNow);
        editor.putString(context.getString(R.string.key_weather_today), weatherTextNow);
        editor.putString(context.getString(R.string.key_temperature_today), weatherTextTemp);
        editor.putString(context.getString(R.string.key_city_time), refreshText);
        // weather
        editor.putString(context.getString(R.string.key_weather_2), info.weatherKind[1]);
        editor.putString(context.getString(R.string.key_weather_3), info.weatherKind[2]);
        editor.putString(context.getString(R.string.key_weather_4), info.weatherKind[3]);
        editor.putString(context.getString(R.string.key_weather_5), info.weatherKind[4]);
        // week
        editor.putString(context.getString(R.string.key_week_2), info.week[1]);
        editor.putString(context.getString(R.string.key_week_3), info.week[2]);
        editor.putString(context.getString(R.string.key_week_4), info.week[3]);
        editor.putString(context.getString(R.string.key_week_5), info.week[4]);
        // temperature
        editor.putString(context.getString(R.string.key_temperature_2), info.miniTemp[1] + "/" + info.maxiTemp[1] + "°");
        editor.putString(context.getString(R.string.key_temperature_3), info.miniTemp[2] + "/" + info.maxiTemp[2] + "°");
        editor.putString(context.getString(R.string.key_temperature_4), info.miniTemp[3] + "/" + info.maxiTemp[3] + "°");
        editor.putString(context.getString(R.string.key_temperature_5), info.miniTemp[4] + "/" + info.maxiTemp[4] + "°");

        editor.apply();
    }

    public static void refreshUIFromLocalData(Context context, boolean isDay) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                context.getString(R.string.sp_widget_day_week_setting), Context.MODE_PRIVATE);
        if (! sharedPreferences.getBoolean(context.getString(R.string.key_saved_data), false)) {
            return;
        }

        String weatherKindToday = sharedPreferences.getString(context.getString(R.string.key_weather_kind_today), "阴");
        String weatherToday = sharedPreferences.getString(context.getString(R.string.key_weather_today), context.getString(R.string.ellipsis));
        String temperatureToday = sharedPreferences.getString(context.getString(R.string.key_temperature_today), context.getString(R.string.ellipsis));
        String cityTime = sharedPreferences.getString(context.getString(R.string.key_city_time), context.getString(R.string.wait_refresh));

        String[] weekText = new String[4];
        weekText[0] = sharedPreferences.getString(context.getString(R.string.key_week_2), context.getString(R.string.ellipsis));
        weekText[1] = sharedPreferences.getString(context.getString(R.string.key_week_3), context.getString(R.string.ellipsis));
        weekText[2] = sharedPreferences.getString(context.getString(R.string.key_week_4), context.getString(R.string.ellipsis));
        weekText[3] = sharedPreferences.getString(context.getString(R.string.key_week_5), context.getString(R.string.ellipsis));

        String[] weatherKind = new String[4];
        weatherKind[0] = sharedPreferences.getString(context.getString(R.string.key_weather_2), "阴");
        weatherKind[1] = sharedPreferences.getString(context.getString(R.string.key_weather_3), "阴");
        weatherKind[2] = sharedPreferences.getString(context.getString(R.string.key_weather_4), "阴");
        weatherKind[3] = sharedPreferences.getString(context.getString(R.string.key_weather_5), "阴");

        String[] tempText = new String[4];
        tempText[0] = sharedPreferences.getString(context.getString(R.string.key_temperature_2), context.getString(R.string.ellipsis));
        tempText[1] = sharedPreferences.getString(context.getString(R.string.key_temperature_3), context.getString(R.string.ellipsis));
        tempText[2] = sharedPreferences.getString(context.getString(R.string.key_temperature_4), context.getString(R.string.ellipsis));
        tempText[3] = sharedPreferences.getString(context.getString(R.string.key_temperature_5), context.getString(R.string.ellipsis));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_day_week);
        // today
        int imageId[] = JuheWeather.getWeatherIcon(weatherKindToday, isDay);
        views.setImageViewResource(R.id.widget_day_image, imageId[3]);
        views.setTextViewText(R.id.widget_day_weather, weatherToday);
        views.setTextViewText(R.id.widget_day_temp, temperatureToday);
        views.setTextViewText(R.id.widget_day_time, cityTime);
        // week
        // icon
        if (isDay) {
            imageId = JuheWeather.getWeatherIcon(weatherKind[0], true);
            views.setImageViewResource(R.id.widget_4_day_image_1, imageId[3]);
            imageId = JuheWeather.getWeatherIcon(weatherKind[1], true);
            views.setImageViewResource(R.id.widget_4_day_image_2, imageId[3]);
            imageId = JuheWeather.getWeatherIcon(weatherKind[2], true);
            views.setImageViewResource(R.id.widget_4_day_image_3, imageId[3]);
            imageId = JuheWeather.getWeatherIcon(weatherKind[3], true);
            views.setImageViewResource(R.id.widget_4_day_image_4, imageId[3]);
        } else {
            imageId = JuheWeather.getWeatherIcon(weatherKind[0], false);
            views.setImageViewResource(R.id.widget_4_day_image_1, imageId[3]);
            imageId = JuheWeather.getWeatherIcon(weatherKind[1], false);
            views.setImageViewResource(R.id.widget_4_day_image_2, imageId[3]);
            imageId = JuheWeather.getWeatherIcon(weatherKind[2], false);
            views.setImageViewResource(R.id.widget_4_day_image_3, imageId[3]);
            imageId = JuheWeather.getWeatherIcon(weatherKind[3], false);
            views.setImageViewResource(R.id.widget_4_day_image_4, imageId[3]);
        }
        // temperature
        views.setTextViewText(R.id.widget_4_day_temp_1, tempText[0]);
        views.setTextViewText(R.id.widget_4_day_temp_2, tempText[1]);
        views.setTextViewText(R.id.widget_4_day_temp_3, tempText[2]);
        views.setTextViewText(R.id.widget_4_day_temp_4, tempText[3]);
        // week
        views.setTextViewText(R.id.widget_4_day_week_1, weekText[0]);
        views.setTextViewText(R.id.widget_4_day_week_2, weekText[1]);
        views.setTextViewText(R.id.widget_4_day_week_3, weekText[2]);
        views.setTextViewText(R.id.widget_4_day_week_4, weekText[3]);

        boolean showCard = sharedPreferences.getBoolean(context.getString(R.string.key_show_card), false);
        boolean hideRefreshTime = sharedPreferences.getBoolean(context.getString(R.string.key_hide_refresh_time), false);
        boolean blackText = sharedPreferences.getBoolean(context.getString(R.string.key_black_text), false);
        if (hideRefreshTime) {
            views.setViewVisibility(R.id.widget_day_time, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_day_time, View.VISIBLE);
        }
        if (blackText) {
            // today text
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(context, R.color.colorTextDark));
            // week text
            views.setTextColor(R.id.widget_4_day_week_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_4, ContextCompat.getColor(context, R.color.colorTextDark));
            // temperature text
            views.setTextColor(R.id.widget_4_day_temp_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_4, ContextCompat.getColor(context, R.color.colorTextDark));
        } else {
            // today text
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(context, R.color.colorTextLight));
            // week text
            views.setTextColor(R.id.widget_4_day_week_1, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_week_2, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_week_3, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_week_4, ContextCompat.getColor(context, R.color.colorTextLight));
            // temperature text
            views.setTextColor(R.id.widget_4_day_temp_1, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_temp_2, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_temp_3, ContextCompat.getColor(context, R.color.colorTextLight));
            views.setTextColor(R.id.widget_4_day_temp_4, ContextCompat.getColor(context, R.color.colorTextLight));
        }
        if (showCard) {
            views.setViewVisibility(R.id.widget_day_week_card, View.VISIBLE);
            // today text
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(context, R.color.colorTextDark));
            // week text
            views.setTextColor(R.id.widget_4_day_week_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_week_4, ContextCompat.getColor(context, R.color.colorTextDark));
            // temperature text
            views.setTextColor(R.id.widget_4_day_temp_1, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_2, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_3, ContextCompat.getColor(context, R.color.colorTextDark));
            views.setTextColor(R.id.widget_4_day_temp_4, ContextCompat.getColor(context, R.color.colorTextDark));
        } else {
            views.setViewVisibility(R.id.widget_day_week_card, View.GONE);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_day_week_button, pendingIntent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        appWidgetManager.updateAppWidget(new ComponentName(context, WidgetProviderDayWeek.class), views);
    }

    // inner class
    private class MyLocationListener implements BDLocationListener {
        // initialize baidu location listener
        @Override
        public void onReceiveLocation(BDLocation location) {
            //Receive Location
            String locationName = null;

            StringBuilder sb = new StringBuilder(256);
            sb.append("time : ");
            sb.append(location.getTime());
            if (location.getLocType() == BDLocation.TypeGpsLocation){// GPS定位结果
                sb.append("\naddr : ");
                sb.append(location.getAddrStr());
                sb.append("\ndescribe : ");
                sb.append("gps定位成功");
                locationName = location.getCity();
            } else if (location.getLocType() == BDLocation.TypeNetWorkLocation){// 网络定位结果
                sb.append("\naddr : ");
                sb.append(location.getAddrStr());
                sb.append("\ndescribe : ");
                sb.append("网络定位成功");
                locationName = location.getCity();
            } else if (location.getLocType() == BDLocation.TypeOffLineLocation) {// 离线定位结果
                sb.append("\ndescribe : ");
                sb.append("离线定位成功，离线定位结果也是有效的");
                locationName = location.getCity();
            } else if (location.getLocType() == BDLocation.TypeServerError) {
                sb.append("\ndescribe : ");
                sb.append("服务端网络定位失败，可以反馈IMEI号和大体定位时间到loc-bugs@baidu.com，会有人追查原因");
            } else if (location.getLocType() == BDLocation.TypeNetWorkException) {
                sb.append("\ndescribe : ");
                sb.append("网络不同导致定位失败，请检查网络是否通畅");
            } else if (location.getLocType() == BDLocation.TypeCriteriaException) {
                sb.append("\ndescribe : ");
                sb.append("无法获取有效定位依据导致定位失败，一般是由于手机的原因，处于飞行模式下一般会造成这种结果，可以试着重启手机");
            }

            getWeather(locationName);

            sb.append("\nlocationdescribe : ");
            sb.append(location.getLocationDescribe());// 位置语义化信息
            List<Poi> list = location.getPoiList();// POI数据
            if (list != null) {
                sb.append("\npoilist size = : ");
                sb.append(list.size());
                for (Poi p : list) {
                    sb.append("\npoi= : ");
                    sb.append(p.getId()).
                            append(" ").
                            append(p.getName()).
                            append(" ").
                            append(p.getRank());
                }
            }
            Log.i("BaiduLocationApiDem", sb.toString());
            mLocationClient.stop();
        }
    }

    @Override
    public void handleMessage(Message message) {
        switch(message.what)
        {
            case REFRESH_DATA_SUCCEED:
                refreshUI();
                break;
            default:
                Toast.makeText(this,
                        getString(R.string.refresh_widget_error),
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }

}
