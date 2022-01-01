package com.example.routeapplication;

import static com.mapbox.mapboxsdk.style.layers.Property.ICON_ROTATION_ALIGNMENT_VIEWPORT;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.OnLocationClickListener;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.annotation.LineManager;
import com.mapbox.mapboxsdk.plugins.annotation.LineOptions;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.lang.ref.WeakReference;
import java.util.List;

import ir.map.sdk_map.MapirStyle;
import ir.map.sdk_map.maps.MapView;
import ir.map.servicesdk.MapService;
import ir.map.servicesdk.ResponseListener;
import ir.map.servicesdk.enums.RouteOverView;
import ir.map.servicesdk.enums.RouteType;
import ir.map.servicesdk.model.base.MapirError;
import ir.map.servicesdk.request.EstimatedTimeArrivalRequest;
import ir.map.servicesdk.request.RouteRequest;
import ir.map.servicesdk.response.EstimatedTimeArrivalResponse;
import ir.map.servicesdk.response.FastReverseGeoCodeResponse;
import ir.map.servicesdk.response.RouteResponse;

public class MainActivity extends AppCompatActivity {
    MapboxMap map;
    Style mapStyle;
    MapView mapView;
    private final MapService mapService = new MapService();
    private Integer numOfMarkerSelects = 0;
    private LatLng firstMarkerLocation;
    private LatLng secondMarkerLocation;
    TextView txvAddress;
    TextView txvFirstLocation;
    TextView txvSecondLocation;
    TextView txvTime;
    ImageView currentLocationImg;
    ImageView marker;
    CardView crdTime;

    LatLng lastKnowLatLng = null;
    private final MyLocationCallback callback = new MyLocationCallback(this);

    private static class MyLocationCallback implements LocationEngineCallback<LocationEngineResult> {
        private final WeakReference<MainActivity> activityWeakReference;

        MyLocationCallback(MainActivity activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void onSuccess(LocationEngineResult result) {
            MainActivity activity = activityWeakReference.get();
            if (activity != null) {
                Location location = result.getLastLocation();
                if (location == null)
                    return;
                activity.lastKnowLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                Toast.makeText(activity, activity.lastKnowLatLng.getLatitude() + ", " + activity.lastKnowLatLng.getLongitude(), Toast.LENGTH_SHORT).show();
                if (activity.map != null && result.getLastLocation() != null)
                    activity.map.getLocationComponent().forceLocationUpdate(result.getLastLocation());
            }
        }

        @Override
        public void onFailure(@NonNull Exception exception) {
            Log.d("LocationChangeActivity", exception.getLocalizedMessage());
            MainActivity activity = activityWeakReference.get();
            if (activity != null)
                Toast.makeText(activity, exception.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        txvAddress = findViewById(R.id.txv_address);
        txvFirstLocation = findViewById(R.id.txv_first_location);
        txvSecondLocation = findViewById(R.id.txv_second_location);
        txvTime = findViewById(R.id.txv_arrival_time_des);
        currentLocationImg = findViewById(R.id.img_current_location);
        marker = findViewById(R.id.img_marker);
        crdTime=findViewById(R.id.crd_estimated_time);
        mapView.getMapAsync(mapboxMap -> {
            map = mapboxMap;
            map.setStyle(new Style.Builder().fromUri(MapirStyle.VERNA), style -> {
                mapStyle = style;

                map.addOnCameraIdleListener(() -> {
                    mapService.fastReverseGeoCode(map.getCameraPosition().target.getLatitude(),
                            map.getCameraPosition().target.getLongitude(), new ResponseListener<FastReverseGeoCodeResponse>() {
                                @Override
                                public void onSuccess(FastReverseGeoCodeResponse response) {
                                    txvAddress.setText(response.getAddressCompact());
                                }

                                @Override
                                public void onError(MapirError error) {
                                    txvAddress.setText("مشکلی پیش آمده");
                                }
                            });
                });
            });

        });
        currentLocationImg.setOnClickListener(view -> enableLocationComponent());

        marker.setOnClickListener(view -> {
            if (numOfMarkerSelects == 0)
                addMarkerForStartPoint(map.getCameraPosition().target);
            else
                addMarkerForEndPoint(map.getCameraPosition().target);
            numOfMarkerSelects++;
            if (numOfMarkerSelects > 1) {
                marker.setVisibility(View.INVISIBLE);
                txvSecondLocation.setText(txvAddress.getText());
                RouteRequest requestBody = new RouteRequest.Builder(
                        firstMarkerLocation.getLatitude(), firstMarkerLocation.getLongitude(),
                        secondMarkerLocation.getLatitude(), secondMarkerLocation.getLongitude(),
                        RouteType.DRIVING
                ).build();
                mapService.route(requestBody, new ResponseListener<RouteResponse>() {
                    @Override
                    public void onSuccess(RouteResponse response) {
                        showRouteOnMap(response.getRoutes().get(0).getGeometry());
                        crdTime.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onError(MapirError error) {
                    }
                });
                EstimatedTimeArrivalRequest esRequestBody = new EstimatedTimeArrivalRequest.Builder( firstMarkerLocation.getLatitude(), firstMarkerLocation.getLongitude())
                        .addDestination( secondMarkerLocation.getLatitude(), secondMarkerLocation.getLongitude())
                        .build();
                mapService.estimatedTimeArrival(
                        esRequestBody,
                        new ResponseListener<EstimatedTimeArrivalResponse>() {
                            @Override
                            public void onSuccess(EstimatedTimeArrivalResponse response) {
                                txvTime.setText((response.getDuration().intValue()+" ثانیه "));
                            }
                            @Override
                            public void onError(MapirError error) {
                                Toast.makeText(MainActivity.this, "مشکلی در تخمین زمان رسیدن پیش آمده", Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            if (numOfMarkerSelects == 1)
                txvFirstLocation.setText(txvAddress.getText());
        });
    }

    @SuppressLint("MissingPermission")
    private void enableLocationComponent() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            LocationComponentOptions customLocationComponentOptions = LocationComponentOptions.builder(this)
                    .elevation(5)
                    .accuracyAlpha(.6f)
                    .accuracyColor(Color.RED)
                    .build();
            LocationComponent locationComponent = map.getLocationComponent();
            LocationComponentActivationOptions locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this, mapStyle)
                            .useDefaultLocationEngine(false) // This line is necessary
                            .locationComponentOptions(customLocationComponentOptions)
                            .build();
            locationComponent.activateLocationComponent(locationComponentActivationOptions);
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING);
            locationComponent.setRenderMode(RenderMode.COMPASS);
            initLocationEngine();
            locationComponent.addOnLocationClickListener(new OnLocationClickListener() {
                @Override
                public void onLocationComponentClick() {
                }
            });
        } else {
            PermissionsManager permissionsManager = new PermissionsManager(new PermissionsListener() {
                @Override
                public void onExplanationNeeded(List<String> permissionsToExplain) {
                }

                @Override
                public void onPermissionResult(boolean granted) {
                    if (granted)
                        enableLocationComponent();
                    else
                        Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_LONG).show();
                }
            });
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressLint("MissingPermission")
    private void initLocationEngine() {
        LocationEngine locationEngine = LocationEngineProvider.getBestLocationEngine(this);
        long DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L;
        long DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5;
        LocationEngineRequest request = new LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME).build();
        locationEngine.requestLocationUpdates(request, callback, getMainLooper());
        locationEngine.getLastLocation(callback);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void addMarkerForEndPoint(LatLng latLng) {
        mapStyle.addImage(
                "end_point_image_id",
                getDrawable(R.drawable.ic_marker_map_small)
        );
        secondMarkerLocation = latLng;
        SymbolManager sampleSymbolManager = new SymbolManager(mapView, map, mapStyle);

        sampleSymbolManager.setIconAllowOverlap(true);
        sampleSymbolManager.setIconRotationAlignment(ICON_ROTATION_ALIGNMENT_VIEWPORT);
        SymbolOptions sampleSymbolOptions = new SymbolOptions();
        sampleSymbolOptions.withLatLng(latLng);
        sampleSymbolOptions.withIconImage("end_point_image_id");
        sampleSymbolOptions.withIconSize(0.5f);
        sampleSymbolManager.create(sampleSymbolOptions);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void addMarkerForStartPoint(LatLng latLng) {
        mapStyle.addImage(
                "end_point_image_id",
                getDrawable(R.drawable.ic_marker_map_small)
        );
        firstMarkerLocation = latLng;
        SymbolManager sampleSymbolManager = new SymbolManager(mapView, map, mapStyle);

        sampleSymbolManager.setIconAllowOverlap(true);
        sampleSymbolManager.setIconRotationAlignment(ICON_ROTATION_ALIGNMENT_VIEWPORT);
        SymbolOptions sampleSymbolOptions = new SymbolOptions();
        sampleSymbolOptions.withLatLng(latLng);
        sampleSymbolOptions.withIconImage("end_point_image_id");
        sampleSymbolOptions.withIconSize(0.5f);
        sampleSymbolManager.create(sampleSymbolOptions);
    }

    public void showRouteOnMap(String geometry) {
        LineString lineString = LineString.fromPolyline(geometry, 5);
        FeatureCollection featureCollection = FeatureCollection.fromFeature(Feature.fromGeometry(lineString));
        GeoJsonSource geoJsonSource = new GeoJsonSource("sample_source_id", featureCollection);
        mapStyle.addSource(geoJsonSource);

        LineLayer lineLayer = new LineLayer("sample_layer_id", "sample_source_id");
        lineLayer.setProperties(
                lineWidth(5f),
                lineColor("#729dec"),
                lineJoin(Property.LINE_JOIN_ROUND)
        );
        mapStyle.addLayer(lineLayer);
    }
}