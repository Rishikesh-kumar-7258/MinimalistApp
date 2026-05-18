package com.minimalist.launcher.core.data

object EmergencyBypass {
    // Phone, Messages, and Maps apps from major OEMs — always visible regardless of focus profile.
    val PACKAGES: Set<String> = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.huawei.contacts",
        "com.android.phone",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.google.android.apps.maps",
        "com.android.contacts",
    )

    fun isEmergency(packageName: String): Boolean = packageName in PACKAGES
}
