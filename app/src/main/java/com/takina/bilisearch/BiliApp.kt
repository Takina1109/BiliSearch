package com.takina.bilisearch

import android.app.Application
import com.takina.bilisearch.data.AppGraph

class BiliApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
