package org.koitharu.legatto

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import org.koitharu.legatto.device.LegattoDevice
import org.koitharu.legatto.scan.BleScanResult
import org.koitharu.legatto.scan.ScanMode
import org.koitharu.legatto.scan.SingleScanCallback

class Legatto(private val context: Context) {

	private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
		(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
	}

	val isAvailable
		get() = bluetoothAdapter != null

	val isEnabled: Boolean
		get() = bluetoothAdapter?.isEnabled == true

	fun scanDevices(scanMode: ScanMode = ScanMode.BALANCED): Flow<BleScanResult> {
		val adapter = bluetoothAdapter
		checkNotNull(adapter) { "Bluetooth is not available" }
		check(adapter.isEnabled) { "Bluetooth is disabled" }
		val scanner = adapter.bluetoothLeScanner
		val settings = ScanSettings.Builder()
			.setScanMode(scanMode.intVal)
			.build()
		val channel = Channel<BleScanResult>()
		val callback = SingleScanCallback(context, channel)
		scanner.startScan(emptyList(), settings, callback)
		channel.invokeOnClose {
			scanner.stopScan(callback)
		}
		return channel.consumeAsFlow()
	}

	suspend fun connectDevice(mac: String): LegattoDevice {
		val adapter = bluetoothAdapter
		requireNotNull(adapter) { "Bluetooth is not available" }
		require(adapter.isEnabled) { "Bluetooth is disabled" }
		val device = adapter.getRemoteDevice(mac)
		return LegattoDevice.connect(context, device)
	}
}