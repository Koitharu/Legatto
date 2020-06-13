package org.koitharu.legatto.scan

import android.bluetooth.le.ScanSettings

enum class ScanMode(internal val intVal: Int) {

	LOW_POWER(ScanSettings.SCAN_MODE_LOW_POWER),
	BALANCED(ScanSettings.SCAN_MODE_BALANCED),
	LOW_LATENCY(ScanSettings.SCAN_MODE_LOW_LATENCY);
}