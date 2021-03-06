package com.napier.c02savingsapp;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.akexorcist.googledirection.DirectionCallback;
import com.akexorcist.googledirection.GoogleDirection;
import com.akexorcist.googledirection.constant.AvoidType;
import com.akexorcist.googledirection.constant.TransportMode;
import com.akexorcist.googledirection.model.Direction;
import com.akexorcist.googledirection.model.Leg;
import com.akexorcist.googledirection.model.Route;
import com.akexorcist.googledirection.util.DirectionConverter;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.w3c.dom.Document;

import java.io.IOException;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * Directions acquired using https://github.com/akexorcist/Android-GoogleDirectionLibrary
 */

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    private GoogleMap mMap;
    private String[] colors = {"#996633", "#7f31c7c5", "#7fff8a00"};

    //One kilometer is 205 grams of C02 at average car speed of 35km
    private int oneKMC02 = 205;

    ArrayList<LatLng> locations = new ArrayList<LatLng>();
    LatLng origin;
    LatLng end;
    double totalDist = 0.0d;
    double totalEm = 0.0d;

    int accuracyCounter = 1;

    boolean isAveraging = false;
    double latitude = 0.0d;
    double longitude = 0.0d;

    ArrayList<Route> routes = new ArrayList<Route>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocationManager locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = this;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000, 10, locationListener);


        setContentView(R.layout.main_layout);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        ((Button)findViewById(R.id.ShareButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "I have saved "+ String.valueOf(Math.round(calculateC02Savings(totalDist)*100.0d)/100.0d) +"g of C02 today by walking!!";
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, message);

                startActivity(Intent.createChooser(share, "Title of the dialog the system will open"));
            }
        });
        ((Button)findViewById(R.id.ShareButton)).setBackgroundResource(R.drawable.share);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.setBuildingsEnabled(true);

        updateMap();
    }

    public void updateMap(){

        if (origin == null || end == null)
            return;

        GoogleDirection.withServerKey(getResources().getString(R.string.google_maps_key))
                .from(origin)
                .to(end)
                .avoid(AvoidType.FERRIES)
                .avoid(AvoidType.HIGHWAYS)
                .transitMode(TransportMode.DRIVING)
                .execute(new DirectionCallback() {
                    @Override
                    public void onDirectionSuccess(Direction direction, String rawBody) {
                        if (direction.isOK()) {
                            // Do something


                            mMap.clear();


                            for (int i = 0; i < direction.getRouteList().size(); i++) {
                                Route route = direction.getRouteList().get(i);
                                String color = colors[i % colors.length];
                                ArrayList<LatLng> directionPositionList = route.getLegList().get(0).getDirectionPoint();

                                double d = 0.0d;

                                LatLng prev = null;
                                for(LatLng l : directionPositionList){
                                    if(prev == null) {
                                        prev = l;
                                        continue;
                                    }
                                    d += CalculationByDistance(prev, l);
                                    prev = l;
                                }

                                if(d >= 0.02d){
                                    totalDist += d;
                                    ((TextView) findViewById(R.id.distanceWalkedNo)).setText(String.valueOf(totalDist));
                                    routes.add(route);
                                }else{
                                    locations.remove(end);
                                    end = null;
                                    //Toast.makeText(MapsActivity.this, "Removed: "+ d, Toast.LENGTH_SHORT).show();
                                }

                                for(Route r : routes) {
                                    ArrayList<LatLng> positions = r.getLegList().get(0).getDirectionPoint();

                                    mMap.addPolyline(DirectionConverter.createPolyline(getBaseContext(), positions, 5, Color.parseColor(color)));
                                    //mMap.addMarker(new MarkerOptions().position(positions.get(0)).title(""));
                                    //mMap.addMarker(new MarkerOptions().position(positions.get(positions.size()-1)).title(""));
                                }
                            }

                            String c02Savings = String.valueOf(Math.round(calculateC02Savings(totalDist)*100.0d)/100.0d) +"g";
                            ((TextView) findViewById(R.id.distanceWalkedNo)).setText(String.valueOf(Math.round(totalDist*100.0d)/100.0d) +"km");
                            ((TextView) findViewById(R.id.savingsNo)).setText(c02Savings);


                            NotificationCompat.Builder mBuilder =
                                    new NotificationCompat.Builder(getBaseContext())
                                            .setSmallIcon(R.mipmap.logo)
                                            .setContentTitle("C02 Savings")
                                            .setContentText(c02Savings);

                            int mNotificationId = 001;
                            NotificationManager mNotifyMgr =
                                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                            mNotifyMgr.notify(mNotificationId, mBuilder.build());


                            if(end != null) {
                                mMap.addMarker(new MarkerOptions()
                                        .position(end)
                                        .title("You")
                                        .snippet("Saved "+ c02Savings)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.scarylady)));
                            }else if(origin != null){
                                mMap.addMarker(new MarkerOptions()
                                        .position(origin)
                                        .title("You")
                                        .snippet("Saved "+ c02Savings)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.scarylady)));
                            }

                            /*
                            PolylineOptions lineOptions = new PolylineOptions().width(3).color(
                                    Color.RED);

                            lineOptions.addAll(locations);
                            lineOptions.width(10);
                            lineOptions.color(Color.RED);

                            if(locations.size() > 1)
                                    mMap.addPolyline(lineOptions);
                            for(LatLng l : locations){
                                mMap.addMarker(new MarkerOptions().position(l).title(""));
                            }
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(locations.get(0)));

                            if(locations.size() > 1) {
                                double dist = CalculationByDistance(origin, end);
                                ((TextView) findViewById(R.id.distanceWalkedNo)).setText(String.valueOf(dist));
                            }
                            */


                        } else {
                            // Do something
                            //Toast.makeText(MapsActivity.this, "Directions are not 'OK'.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onDirectionFailure(Throwable t) {
                        // Do something
                        //Toast.makeText(MapsActivity.this, "No directions.", Toast.LENGTH_SHORT).show();
                    }
                });

    }


    @Override
    public void onLocationChanged(Location loc) {

        /*
        if(loc.getAccuracy() < 50){
            if(!isAveraging){
                latitude = loc.getLatitude();
                longitude = loc.getLongitude();
                isAveraging = true;
            }else{
                latitude += loc.getLatitude();
                longitude += loc.getLongitude();
                accuracyCounter++;
            }
            Toast.makeText(this, "avg", Toast.LENGTH_SHORT).show();
            if(accuracyCounter < 3) {
                return;
            }
        }
        if(isAveraging && !(loc.getAccuracy() < 50)){
            loc.setLatitude(latitude/accuracyCounter);
            loc.setLongitude(longitude/accuracyCounter);
        }
        Toast.makeText(this, "Loc"+ loc.getLatitude()+", "+loc.getLongitude(), Toast.LENGTH_LONG).show();
        //Log.i("TESTTESTTEST", loc.getLatitude()+", "+loc.getLongitude());

        latitude = 0.0d;
        longitude = 0.0d;
        isAveraging = false;
        accuracyCounter = 1;
        */

        /*------- To get city name from coordinates -------- */
        String cityName = null;
        Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
        List<Address> addresses;
        try {
            addresses = gcd.getFromLocation(loc.getLatitude(),
                    loc.getLongitude(), 1);
            if (addresses.size() > 0) {
                System.out.println(addresses.get(0).getLocality());
                cityName = addresses.get(0).getLocality();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        LatLng newLoc = new LatLng(loc.getLatitude(), loc.getLongitude());
        if(origin == null){
            origin = newLoc;
        }

        if(locations.size() >= 2)
            origin = locations.get(locations.size()-2);

        locations.add(newLoc);
        end = locations.get(locations.size()-1);

        //mMap.moveCamera(CameraUpdateFactory.newLatLng(end));
        //mMap.animateCamera(CameraUpdateFactory.zoomTo(15), 2000, null);

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(end) // Sets the center of the map to
                .zoom(15)                   // Sets the zoom
                .tilt(30)    // Sets the tilt of the camera to 30 degrees
                .build();    // Creates a CameraPosition from the builder
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                cameraPosition));



        Toast.makeText(MapsActivity.this, "New Location", Toast.LENGTH_SHORT).show();

        updateMap();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    //Method to calculate c02 savings by distance walked
    public double calculateC02Savings(double distanceTravelled){
        return distanceTravelled * oneKMC02;
    }

    // Distance in km for a straight line
    public double CalculationByDistance(LatLng StartP, LatLng EndP) {
        int Radius = 6371;// radius of earth in Km
        double lat1 = StartP.latitude;
        double lat2 = EndP.latitude;
        double lon1 = StartP.longitude;
        double lon2 = EndP.longitude;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double valueResult = Radius * c;
        double km = valueResult / 1;
        DecimalFormat newFormat = new DecimalFormat("####");
        int kmInDec = Integer.valueOf(newFormat.format(km));
        double meter = valueResult % 1000;
        int meterInDec = Integer.valueOf(newFormat.format(meter));
        //Log.i("Radius Value", "" + valueResult + "   KM  " + kmInDec
        //         + " Meter   " + meterInDec);

        return Radius * c;
    }

}
