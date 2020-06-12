package org.koitharu.legatto.sample

import android.content.Context
import android.widget.ArrayAdapter
import org.koitharu.legatto.scan.BleScanResult

class ScanResultAdapter(context: Context, resource: Int, objects: MutableList<BleScanResult>) :
	ArrayAdapter<BleScanResult>(context, resource, objects) {


}