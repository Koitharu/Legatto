package org.koitharu.legatto.scan

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import org.koitharu.legatto.device.LegattoDevice

class BleScanResult internal constructor(private val context: Context, private val delegate: ScanResult) {

	val name: String? = delegate.scanRecord?.deviceName ?: delegate.device.name

	val macAddress: String = delegate.device.address

	val scanRecord: ScanRecord?
		get() = delegate.scanRecord

	suspend fun connect() = LegattoDevice.connect(context, delegate.device)
}