package com.bill.usermanagmentsystem

import android.app.Application
import com.bill.usermanagmentsystem.di.initKoinAndroid

class UserManagementApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinAndroid(
            application = this,
            apiToken = BuildConfig.GOREST_ACCESS_TOKEN,
        )
    }
}
