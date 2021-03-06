package com.mapbox.search.demo.api

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.geojson.Point
import com.mapbox.search.MapboxSearchSdk
import com.mapbox.search.ReverseGeoOptions
import com.mapbox.search.ReverseGeocodingSearchEngine
import com.mapbox.search.ReverseMode
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchRequestTask
import com.mapbox.search.result.SearchResult

class ReverseGeocodingKotlinExampleActivity : AppCompatActivity() {

    private lateinit var reverseGeocoding: ReverseGeocodingSearchEngine
    private lateinit var searchRequestTask: SearchRequestTask

    private val searchCallback = object : SearchCallback {

        override fun onResults(results: List<SearchResult>) {
            if (results.isEmpty()) {
                Log.i("SearchApiExample", "No reverse geocoding results")
            } else {
                Log.i("SearchApiExample", "Reverse geocoding results: $results")
            }
        }

        override fun onError(e: Exception) {
            Log.i("SearchApiExample", "Reverse geocoding error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reverseGeocoding = MapboxSearchSdk.createReverseGeocodingSearchEngine()

        val options = ReverseGeoOptions(
            center = Point.fromLngLat(2.294434, 48.858349),
            limit = 1,
            reverseMode = ReverseMode.DISTANCE
        )
        searchRequestTask = reverseGeocoding.search(options, searchCallback)
    }

    override fun onDestroy() {
        searchRequestTask.cancel()
        super.onDestroy()
    }
}
