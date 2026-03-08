package com.example.aura.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client factory for the Aura backend.
 *
 * Update BASE_URL to your Cloud Run service URL after deployment.
 * During development, use your local machine's IP.
 */
object RetrofitClient {

    /**
     * Backend URL.
     * - Local dev: "http://10.0.2.2:8080/" (Android emulator → host machine)
     * - Local dev (physical device): "http://YOUR_LOCAL_IP:8080/"
     * - Production: Your Cloud Run URL
     */
    var BASE_URL = "http://10.0.2.2:8080/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // AI responses can take a while
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: AuraApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuraApiService::class.java)
    }
}
