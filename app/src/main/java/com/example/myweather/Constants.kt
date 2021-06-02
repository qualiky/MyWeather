package com.example.myweather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Constants {

    const val APP_ID = ""
    const val BASE_URL = "http://api.openweathermap.org/data/"
    const val METRIC_UNIT = "metric"
    const val PREFERENCE_NAME = "weatherAppPreference"
    const val WEATHER_RESPONSE_DATA = "weatherResponseData"

    fun isNetworkAvailable(context: Context) : Boolean {
        val connectivityManager = context.
            getSystemService(Context.CONNECTIVITY_SERVICE) as
                ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}