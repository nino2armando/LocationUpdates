package com.example.ninokhodabandeh.extractedlocation;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


public class MainActivity extends FragmentActivity implements
        LocationListener,
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener{

    private LocationRequest mLocationRequest;
    private LocationClient mLocationClient;
    private ProgressBar mActivityIndicator;

    private Location mLocation;

    private TextView tv;
    private TextView tv2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationRequest = LocationRequest.create();

        mLocationRequest.setInterval(LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setFastestInterval(LocationUtils.FAST_INTERVAL_CEILING_IN_MILLISECONDS);

        mLocationClient = new LocationClient(this, this, this);


        tv = (TextView) findViewById(R.id.text_connection_state);
        tv2 =(TextView) findViewById(R.id.text_address);
        mActivityIndicator = (ProgressBar) findViewById(R.id.address_progress);
        try {
            mLocationClient.connect();
        }catch (Exception ex){
            Log.d(LocationUtils.APPTAG, ex.getMessage());
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        mLocationClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mLocationClient.isConnected()){
            stopPeriodicUpdates();
        }
        mLocationClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case LocationUtils.CONNETION_FAILURE_RESOLUTION_REQUEST:
                switch (resultCode){
                    case Activity.RESULT_OK:
                        Log.d(LocationUtils.APPTAG, getString(R.string.resolved));
                        break;
                    default:
                        Log.d(LocationUtils.APPTAG, getString(R.string.no_resolution));
                }
                break;
            default:
                Log.d(LocationUtils.APPTAG, getString(R.string.unknown_activity_request_code, requestCode));
                break;
        }
    }


    private boolean serviceConnected(){
        // check google playservice is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // available
        if(ConnectionResult.SUCCESS == resultCode){
            Log.d(LocationUtils.APPTAG, getString(R.string.play_service_available));
            return true;
        }else  {
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
            if(dialog != null){
                ErrorFragment errorFragment = new ErrorFragment();
                errorFragment.setDialog(dialog);
                errorFragment.show(getFragmentManager(), LocationUtils.APPTAG);
            }
            return false;
        }
    }

    private Location getLocation(){
        Location location = null;
        if(serviceConnected()){
            location = mLocationClient.getLastLocation();
        }
        return location;
    }

    public void getAddress(){
        if(!Geocoder.isPresent()){
            Toast.makeText(this, R.string.no_geoCoder_available, Toast.LENGTH_LONG).show();
            return;
        }

        if(serviceConnected()){
            Location location = mLocationClient.getLastLocation();
            mActivityIndicator.setVisibility(View.VISIBLE);
            (new GetAddressTask(this)).execute(location);
        }
    }

    private void startPeriodicUpdates(){
        try{
           mLocationClient.requestLocationUpdates(mLocationRequest, this);
        }catch(Exception ex){
            Log.e(LocationUtils.APPTAG, ex.getMessage());
        }
    }

    public void stopPeriodicUpdates(){
        try{
            mLocationClient.removeLocationUpdates(this);
        }catch (Exception ex){
            Log.e(LocationUtils.APPTAG, ex.getMessage());
        }
    }



    private void showErrorDialog(int errorCode){
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode, this, LocationUtils.CONNETION_FAILURE_RESOLUTION_REQUEST);

        if(errorDialog != null){
            ErrorFragment errorFragment = new ErrorFragment();
            errorFragment.setDialog(errorDialog);
            errorFragment.show(getFragmentManager(), LocationUtils.APPTAG);
        }
    }

    public static class ErrorFragment extends DialogFragment {

        private Dialog _dialog;

        public ErrorFragment(){
            super();
            _dialog = null;
        }

        public void setDialog(Dialog dialog){
            _dialog =  dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return _dialog;
        }
    }


    // com.google.android.gms.location.LocationListener METHODS
    @Override
    public void onLocationChanged(Location location) {
        // todo: do location changed stuff here

        mLocation = location;
        TextView tv = (TextView) findViewById(R.id.text_connection_state);
        tv.setText(LocationUtils.getLatLong(this, location));

    }

    // GooglePlayServicesClient.ConnectionCallbacks METHODS
    @Override
    public void onConnected(Bundle bundle) {
        // todo: if update is required
        // then do the periodic updates here
        mLocation = getLocation();

        startPeriodicUpdates();
        tv.setText(LocationUtils.getLatLong(this, mLocation));

        getAddress();
    }

    @Override
    public void onDisconnected() {
        // todo: maybe log or notify
    }

     // GooglePlayServicesClient.OnConnectionFailedListener METHODS
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if(connectionResult.hasResolution()){
            try{
                connectionResult.startResolutionForResult(this, LocationUtils.CONNETION_FAILURE_RESOLUTION_REQUEST);
            }catch (IntentSender.SendIntentException ex){
                ex.printStackTrace();
            }

        }else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    protected class GetAddressTask extends AsyncTask<Location, Void, String>{

        Context mContext;

        public GetAddressTask(Context context)
        {
            super();
            mContext = context;
        }

        @Override
        protected String doInBackground(Location... locations) {
            Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
            Location location = locations[0];
            List<Address> addresses = null;

            try{
                addresses = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(), 1);
            }catch (IOException ex)
            {
                Log.e(LocationUtils.APPTAG, getString(R.string.IO_Exception_getFromLocation));
                ex.printStackTrace();
                return (getString(R.string.IO_Exception_getFromLocation));
            }catch (IllegalArgumentException ex2)
            {
                String errorString = getString(R.string.illegal_argument_exception, location.getLatitude(), location.getLongitude());
                Log.e(LocationUtils.APPTAG, errorString);
                ex2.printStackTrace();
                return errorString;
            }

            if(addresses != null && addresses.size() > 0)
            {
                Address address = addresses.get(0);

                String addressText = getString(R.string.address_output_string,
                        address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0): "",
                        address.getLocality(), // locality is the city
                        address.getCountryName());
                return addressText;
            }else
            {
                return getString(R.string.no_address_found);
            }
        }

        @Override
        protected void onPostExecute(String s) {
            mActivityIndicator.setVisibility(View.GONE);
            tv2.setText(s);
        }
    }
}


