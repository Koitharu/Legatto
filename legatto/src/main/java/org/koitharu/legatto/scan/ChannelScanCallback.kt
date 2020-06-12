package org.koitharu.legatto.scan

import android.bluetooth.le.ScanCallback
import android.content.Context
import kotlinx.coroutines.channels.Channel
import org.koitharu.legatto.exception.BleIOException

internal abstract class ChannelScanCallback <T> constructor(
	protected val context: Context,
	protected val channel: Channel<T>
) : ScanCallback() {

	override fun onScanFailed(errorCode: Int) {
		val message = when(errorCode) {
			SCAN_FAILED_ALREADY_STARTED -> "Fails to start scan as BLE scan with the same settings is already started by the app"
			SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Fails to start scan as app cannot be registered"
			SCAN_FAILED_FEATURE_UNSUPPORTED -> "Fails to start power optimized scan as this feature is not supported"
			SCAN_FAILED_INTERNAL_ERROR -> "Fails to start scan due an internal error"
			5 -> "Fails to start scan as it is out of hardware resources"
			6 -> "Fails to start scan as application tries to scan too frequently"
			else -> "Scan failed with code $errorCode"
		}
		channel.close(BleIOException(message))
	}
}