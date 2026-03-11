package com.calory.swapcal.ai

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.*
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {

    private const val TAG = "AdManager"

    // Test IDs — replace with production IDs
    private const val INTERSTITIAL_ID = "ca-app-pub-7081526478061042/1029713155"
    private const val REWARDED_ID = "ca-app-pub-7081526478061042/2685429819"

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // ================================
    // Initialization
    // ================================
    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: $initializationStatus")
        }
        loadInterstitial(context)
        loadRewarded(context)
    }

    // ================================
    // Interstitial Ads
    // ================================
    fun loadInterstitial(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "Interstitial failed to load: ${adError.message}")
                }
            })
    }

    fun showInterstitial(activity: Activity) {
        interstitialAd?.show(activity) ?: run {
            Log.d(TAG, "Interstitial not ready, reloading...")
            loadInterstitial(activity)
        }

        // Always preload the next ad
        loadInterstitial(activity)
    }

    // ================================
    // Rewarded Ads
    // ================================
    fun loadRewarded(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_ID, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded ad loaded")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${adError.message}")
                }
            })
    }

    fun showRewarded(activity: Activity, onReward: (RewardItem) -> Unit) {
        rewardedAd?.show(activity) { rewardItem ->
            onReward(rewardItem)
            // Reload after showing
            loadRewarded(activity)
        } ?: run {
            Log.d(TAG, "Rewarded ad not ready, reloading...")
            loadRewarded(activity)
        }
    }

    // ================================
    // Banner Ads
    // ================================
    fun loadBanner(adView: AdView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    // Optional: cleanup to avoid memory leaks
    fun destroyBanner(adView: AdView?) {
        adView?.destroy()
    }
}