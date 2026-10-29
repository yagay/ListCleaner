package com.yagay.ListCleaner.domain

/** SystemUI only binds exported device-control providers with the platform binding permission. */
object ControlsProviderEligibility {
    const val ACTION = "android.service.controls.ControlsProviderService"
    const val PERMISSION = "android.permission.BIND_CONTROLS"
    fun accepts(permission: String?, exported: Boolean): Boolean =
        exported && permission == PERMISSION
}
