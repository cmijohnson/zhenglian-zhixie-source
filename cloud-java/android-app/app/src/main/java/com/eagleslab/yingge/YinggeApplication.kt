package com.eagleslab.yingge

import android.app.Application
import com.eagleslab.yingge.data.MockYinggeRepository
import com.eagleslab.yingge.data.LiveYinggeRepository
import com.eagleslab.yingge.data.PreferencesStore
import com.eagleslab.yingge.data.YinggeRepository
import com.eagleslab.yingge.data.contract.MobileTokenVault

class YinggeApplication : Application() {
    lateinit var repository: YinggeRepository
        private set
    lateinit var preferences: PreferencesStore
        private set
    lateinit var tokenVault: MobileTokenVault
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesStore(this)
        tokenVault = MobileTokenVault.get(this)
        repository = if (BuildConfig.USE_MOCK_REPOSITORY) {
            MockYinggeRepository()
        } else {
            LiveYinggeRepository(this, tokenVault)
        }
    }
}
