package org.koitharu.legatto.sample

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.koitharu.legatto.Legatto
import org.koitharu.legatto.ScanMode
import org.koitharu.legatto.scan.BleScanResult
import kotlin.coroutines.CoroutineContext

class MainActivity : Activity(), CoroutineScope {

	private val job = SupervisorJob()
	override val coroutineContext: CoroutineContext = Dispatchers.Main + job
	private lateinit var adapter: ScanResultAdapter
	private val scanResult = ArrayList<BleScanResult>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		adapter = ScanResultAdapter(this, android.R.layout.simple_list_item_2, scanResult)
		startScan()
	}

	override fun onDestroy() {
		job.cancelChildren()
		super.onDestroy()
	}

	private fun startScan() {
		launch {
			val legatto = Legatto(this@MainActivity)
			legatto.scanDevices(ScanMode.LOW_LATENCY)
				.collect {
					scanResult += it
					adapter.notifyDataSetChanged()
				}
		}
	}
}