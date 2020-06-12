package org.koitharu.legatto.scan

import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking

internal class SingleScanCallback(context: Context, channel: Channel<BleScanResult>) :
	ChannelScanCallback<BleScanResult>(context, channel) {

	override fun onScanResult(callbackType: Int, result: ScanResult?) {
		if (channel.isClosedForSend || callbackType != ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
			return
		}
		if (result != null) {
			channel.sendBlocking(BleScanResult(context, result))
		}
	}
}