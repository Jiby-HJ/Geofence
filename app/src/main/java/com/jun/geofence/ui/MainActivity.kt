package com.jun.geofence.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.jun.geofence.R
import com.jun.geofence.app.PermissionHelper
import com.jun.geofence.data.viewModel.MainViewModel
import com.jun.geofence.databinding.ActivityMainBinding
import com.jun.geofence.receiver.GeofenceReceiver



class MainActivity : AppCompatActivity(), OnMapReadyCallback, MainViewModel.Callbacks {

    private var currentLocation: Location? = null
    private var viewModel: MainViewModel? = null
    private var homeBinding: ActivityMainBinding? = null
    private var permissionHelper: PermissionHelper? = null
    private val REQUEST_CODE_LOCATION_ENABLE = 2481
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    lateinit var geofencingClient: GeofencingClient
    // 조선대 위도 경도
    private val defaultLocation = LatLng(35.142, 126.93)
    private var geofenceLatLng: LatLng = defaultLocation
    private var geofenceRange = 50.0

    private var map: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeBinding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(homeBinding?.root)

        //Without viewmodelfactory
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        viewModel?.initCallbacks(this)
        permissionHelper = PermissionHelper(this)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(
                R.id.map
            ) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)
        listener()
    }

    // 리스너 활성화
    private fun listener() {
        homeBinding?.let {
            it.RangeSetting.max = 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.RangeSetting.min = 50
            }
            it.RangeSetting.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    geofenceRange = progress.toDouble()
                    printGeofenceCircle()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    printShortToast(geofenceRange.toString())
                }

            })

            //Remove button 동작
            it.ButtonRemove.setOnClickListener { _ ->
                map?.clear()
                it.placeDesignation.visibility = View.GONE
            }

            //Add Button 동작
            it.ButtonAdd.setOnClickListener { _ ->
                viewModel?.createGeoFence(geofenceLatLng,geofenceRange.toFloat())
                it.placeDesignation.visibility = View.GONE
            }
        }
    }


    // 지도 조작
    // 지도를 사용할 준비가 되면 실행
    override fun onMapReady(map: GoogleMap) {
        this.map = map
        this.map?.setOnMapClickListener {
            if (currentLocation != null) {
                geofenceLatLng = it
                printGeofenceCircle()
                homeBinding?.placeDesignation?.visibility = View.VISIBLE
            }
        }
        verifyLocationPermission()
    }

    // 지오펜스 출력
    private fun printGeofenceCircle() {
        map?.let {
            it.clear()
            it.addCircle(
                CircleOptions()
                    .center(geofenceLatLng)
                    .radius(geofenceRange)
                    .strokeWidth(0f)
                    .fillColor(0x55003e8d)
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper!!.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /*
    * verify the location permission is enabled or not
    * if the location is not enable it will ask the permission
    * */
    private fun verifyLocationPermission() {
        if (permissionHelper!!.isPermissionGranted(PermissionHelper.PermissionType.LOCATION))
            openLocationEnableDialog()
        else permissionHelper!!.openPermissionDialog(PermissionHelper.PermissionType.LOCATION,
            object : PermissionHelper.PermissionListener {
                override fun onPermissionGranted() {
                    openLocationEnableDialog()
                }

                override fun onPermissionDenied() {
                    printShortToast("Permission Denied")

                }
            })
    }


    /*Enable the user GPS location*/
    private fun openLocationEnableDialog() {
        val locationRequest = LocationRequest.create()
            .setInterval(30)
            .setFastestInterval(30)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        LocationServices
            .getSettingsClient(this)
            .checkLocationSettings(builder.build())
            .addOnSuccessListener(
                this
            ) {
                getCurrentLocation()
            }
            .addOnFailureListener(
                this
            ) { ex: Exception? ->
                if (ex is ResolvableApiException) {
                    // Location settings are NOT satisfied,  but this can be fixed  by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),  and check the result in onActivityResult().
                        ex.startResolutionForResult(
                            this,
                            REQUEST_CODE_LOCATION_ENABLE
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (REQUEST_CODE_LOCATION_ENABLE == requestCode) {
            if (RESULT_OK == resultCode) {
                getCurrentLocation()
            }
        }

    }

    /*
    * find the user current location latitude and longitude
    * */
    private fun getCurrentLocation() {
        map?.isMyLocationEnabled = true
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        mFusedLocationClient?.getCurrentLocation(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            null
        )?.addOnCompleteListener {
            val location: Location? = it.result
            location?.let {
                currentLocation = it
                viewModel?.animateCameraToLatLng(it, map)
            }
        }
    }

    private fun printShortToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, Gravity.CENTER_HORIZONTAL, Gravity.CENTER_VERTICAL)
        toast.show()
    }
    private fun printLongToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    override fun geofenceRequest(arrayList: ArrayList<Geofence>) {
        val request = GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(arrayList)
        }.build() // build the GeoFenceRequest

        val intent = Intent(this, GeofenceReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        geofencingClient.addGeofences(request, pendingIntent).run {
            addOnSuccessListener {
                printShortToast(getString(R.string.geofence_enabled))
            }
            addOnFailureListener {
                printShortToast(getString(R.string.geofence_error))
            }
        }
    }

}