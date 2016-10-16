package comgothicvoid.httpsgithub.ffscanner;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ZoomControls;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.TextureMapView;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.model.LatLng;

public class MainActivity extends AppCompatActivity {

    //地图相关
    TextureMapView mMapView = null;
    BaiduMap mBaiduMap;
    //定位相关
    boolean isFirstLoc = true; // 是否首次定位
    LocationClient mLocationClient;
    BDLocation mlocation;
    BDLocationListener myListener = new MyLocationListener();
    BitmapDescriptor mCurrentMarker;
    int mXDirection = 0;
    //方向传感器相关
    MyOrientationListener myOrientationListener;
    //计时器相关
    Handler handler;
    Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        //注意该方法要再setContentView方法之前实现
        SDKInitializer.initialize(getApplicationContext());
        mLocationClient = new LocationClient(getApplicationContext());     //声明LocationClient类
        mLocationClient.registerLocationListener( myListener );    //注册监听函数
        setContentView(R.layout.activity_main);

        //获取地图控件引用
        mMapView = (TextureMapView) findViewById(R.id.bmapView);
        mMapView.showZoomControls(false);// 不显示默认的缩放控件
        mMapView.showScaleControl(false);// 不显示默认比例尺控件
        // 隐藏logo
        View child = mMapView.getChildAt(1);
        if (child != null && (child instanceof ImageView || child instanceof ZoomControls)){
            child.setVisibility(View.INVISIBLE);
        }
        mBaiduMap = mMapView.getMap();
        UiSettings settings=mBaiduMap.getUiSettings();
        settings.setScrollGesturesEnabled(false);

        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);

        //定位设置
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true); //打开GPRS
        option.setAddrType("all"); //返回的定位结果包含地址信息
        option.setCoorType("bd09ll"); //返回的定位结果是百度经纬度,默认值gcj02
        option.setPriority(LocationClientOption.GpsFirst); // 设置GPS优先
        option.setScanSpan(3000);   //设置发起定位请求的间隔时间为3000ms
        option.disableCache(true); //禁止启用缓存定位
        mLocationClient.setLocOption(option);  //设置定位参数
        //设置定位图层的配置（定位模式，是否允许方向信息，用户自定义定位图标）
        mCurrentMarker = BitmapDescriptorFactory.fromResource(R.drawable.bat);
        MyLocationConfiguration config = new MyLocationConfiguration
                (MyLocationConfiguration.LocationMode.FOLLOWING, true, mCurrentMarker);
        mBaiduMap.setMyLocationConfigeration(config);

        //方向传感器监听
        myOrientationListener = new MyOrientationListener(getApplicationContext());
        myOrientationListener.setOnOrientationListener(new MyOrientationListener.OnOrientationListener() {
            @Override
            public void onOrientationChanged(float x) {
                mXDirection = (int) x;
            }
        });

        //开始定位及方向监听
        mLocationClient.start();
        myOrientationListener.start();

        //定时更新方向,并且保持自己处于地图中心
        handler = new Handler();
        runnable = new Runnable(){
            @Override
            public void run() {
                MyLocationData locData = new MyLocationData.Builder()
                        .accuracy(0)
                        // 此处设置开发者获取到的方向信息，顺时针0-360
                        .direction(mXDirection)
                        .latitude(mlocation.getLatitude())
                        .longitude(mlocation.getLongitude()).build();
                mBaiduMap.setMyLocationData(locData);
                // 设置定位图层的配置（定位模式，是否允许方向信息，用户自定义定位图标）
                mCurrentMarker = BitmapDescriptorFactory.fromResource(R.drawable.bat);
                MyLocationConfiguration config = new MyLocationConfiguration
                        (MyLocationConfiguration.LocationMode.FOLLOWING, true, mCurrentMarker);
                mBaiduMap.setMyLocationConfigeration(config);

                LatLng ll = new LatLng(mlocation.getLatitude(), mlocation.getLongitude());
                MapStatus.Builder builder = new MapStatus.Builder();
                builder.target(ll);
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
                handler.postDelayed(this, 500); // 50是延时时长
            }
        };
        handler.postDelayed(runnable, 500); // 打开定时器，执行操作
    }
    @Override
    protected void onDestroy() {
        handler.removeCallbacks(runnable);// 关闭定时器处理
        // 退出时销毁定位
        mLocationClient.stop();
        // 关闭方向传感器
        myOrientationListener.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
        super.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }

    public class MyLocationListener implements BDLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null) return;
            mlocation = location;
            if (isFirstLoc) {
                isFirstLoc = false;
                LatLng ll = new LatLng(location.getLatitude(),
                        location.getLongitude());
                MapStatus.Builder builder = new MapStatus.Builder();
                builder.target(ll).zoom(18.0f);
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
            }
        }
    }
}
