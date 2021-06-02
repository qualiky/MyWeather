package com.example.myweather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myweather.databinding.ActivityMainBinding
import com.example.myweather.models.WeatherResponse
import com.example.myweather.network.WeatherService
import com.google.android.gms.location.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var customProgressDialog: Dialog
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //fused location client is a google play dependency that provides location service, with the
        //exact latitude and longitude required to set up the weather
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if(!isLocationEnabled()) {
            turnLocationSettingsOn()
        } else {
            permissionRequester()
        }
    }

    //this method checks if the location service is turned on in the device, which is usually done
    //from the control centre equivalent position in Android
    private fun isLocationEnabled() : Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    // if the location service is turned off, this will open up an alert dialog that will redirect to
    // the settings screen to turn on the location services
    private fun turnLocationSettingsOn() {
        val locDialog = MaterialAlertDialogBuilder(this)
        locDialog.setTitle("Locations Disabled")
        locDialog.setMessage("Your location provider is turned off. Turn on locations to receive accurate weather data?")
        locDialog.setPositiveButton("Ok"){
                _, _ ->

            val intentLoc = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intentLoc)
        }
        locDialog.setNegativeButton("Cancel" ) {
            lcDialog, _ ->

            lcDialog.dismiss()
        }
        locDialog.show()
    }

    //once the location service has been turned on, if the permission to access the location has not
    //been granted to the app, this will check and ask for permissions if necessary

    private fun permissionRequester() {
        Dexter.withContext(this).withPermissions(
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION)
        ).withListener(object: MultiplePermissionsListener{
            override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                if(p0!!.areAllPermissionsGranted()) {
                    requestLocationData()
                }
            }
            override fun onPermissionRationaleShouldBeShown(
                p0: MutableList<PermissionRequest>?,
                p1: PermissionToken?
            ) {
                openRationaleDialogForPermission()
            }

        }).onSameThread().check()
    }


    //if the permission has been denied before, Android system won't display the request
    //permission dialog for the user to accept or deny the permission again. Therefore,
    //this alert dialog will take user to the settings screen to enable permissions manually
    private fun openRationaleDialogForPermission() {
        val dialog = MaterialAlertDialogBuilder(this)
        dialog.setTitle("Permissions Required")
        dialog.setMessage("This app requires Coarse and Fine location to provide you weather service. Please enable permissions from the Settings.")
        dialog.setPositiveButton("OK") {
                _,_ ->
            val settingsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName,null))
            startActivity(settingsIntent)
        }
        dialog.setNegativeButton("Cancel", ){
            inDialog, _ ->

            inDialog.dismiss()
        }
            .show()
    }



    //this function requests the accurate location data and initiates it so that the weather can be
    //displayed accordingly
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper()!!)
    }


    //if the device location has been changed, the locationCallBack variable notifies the fusedlocationclient
    //which provides the last location, latitude and longitude which can be useful to update the weather
    //to reflect the new location
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation
            var latitude = lastLocation.latitude
            var longitude = lastLocation.longitude
            getLocationWeather(latitude, longitude)
        }
    }

    private fun getLocationWeather(latitude: Double, longitude: Double) {
        if(Constants.isNetworkAvailable(this)){

            val retrofit= Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service = retrofit
                .create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressBar()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>,
                ) {
                    if(response.isSuccessful) {
                        customProgressDialog.hide()
                        val weatherList: WeatherResponse = response.body()!!
                        Log.v("Data: ", weatherList.toString())
                        binding.mainConstraintView.visibility = View.VISIBLE
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = sharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                    } else {
                        val responseCode = response.code()
                        when(responseCode) {
                            400 -> {
                                Toast.makeText(applicationContext,"Error 400: Bad connection", Toast.LENGTH_SHORT).show()
                            }
                            404 -> {
                                Toast.makeText(applicationContext,"Error 400: Not Found!", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                Toast.makeText(applicationContext, "Generic error!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Toast.makeText(applicationContext, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    Log.e("OnFailureError: ", t.localizedMessage)
                    customProgressDialog.hide()
                }

            })
        } else {
            Toast.makeText(this,"No internet available!", Toast.LENGTH_SHORT).show()
        }
    }

    fun showCustomProgressBar() {
       customProgressDialog = Dialog(this)
        customProgressDialog.setContentView(R.layout.progress_dialog)
        customProgressDialog.show()
    }

    private fun setupUI(){
        val weatherResponseJsonString = sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices) {
                binding.weatherDesc.text = weatherList.weather[i].description
                Log.d("Icon type: ", weatherList.weather[i].icon)
                val iconLast = "ic_" + weatherList.weather[i].icon
                binding.weatherIcon.setImageResource(this.resources.getIdentifier(iconLast,"drawable",this.packageName))
                binding.cityName.text = weatherList.name
                binding.currentTemp.text = "${getCelsiusTemp(weatherList.main.temp)}℃"
                binding.feelsLike.text = "Feels like " + getCelsiusTemp(weatherList.main.feels_like) + "℃"

                val timeFormat = SimpleDateFormat("HH:mm")
                val sunriseDate = Date(weatherList.sys.sunrise * 1000L)
                val sunsetDate = Date(weatherList.sys.sunset * 1000L)
                binding.sunriseTime.text = (timeFormat.format(sunriseDate)).toString()
                binding.sunsetTime.text = (timeFormat.format(sunsetDate)).toString()
                binding.windSpeed.text = "${weatherList.wind.speed}\nm/s"
                binding.windDir.text = "${weatherList.wind.deg}°"
                binding.minTemp.text = "${getCelsiusTemp(weatherList.main.temp_min)}"
                binding.maxTemp.text = "${getCelsiusTemp(weatherList.main.temp_max)}"
                binding.pressure.text = "${weatherList.main.pressure.toInt()}hPa"
                binding.humidity.text = "${weatherList.main.humidity}%"
            }
        }

    }

    private fun getCelsiusTemp(temp: Double): String {
        return temp.toString()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }
}
