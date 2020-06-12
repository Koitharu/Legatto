package org.koitharu.legatto.device

import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.legatto.exception.BleIOException
import org.koitharu.legatto.exception.CharacteristicNotFoundException
import org.koitharu.legatto.exception.DescriptorNotFoundException
import org.koitharu.legatto.exception.DeviceDisconnectedException
import java.io.Closeable
import java.util.*
import kotlin.collections.HashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("unused")
class LegattoDevice private constructor(
	private val context: Context,
	private val device: BluetoothDevice
) : Closeable, AutoCloseable {

	private lateinit var gatt: BluetoothGatt
	private var connectionCont: Continuation<Unit>? = null
	private var characteristicReadCont: Continuation<ByteArray>? = null
	private var characteristicWriteCont: Continuation<ByteArray>? = null
	private var descriptorReadCont: Continuation<ByteArray>? = null
	private var descriptorWriteCont: Continuation<ByteArray>? = null
	private var characteristicObservers = HashMap<UUID, Channel<ByteArray>>()
	private var onDisconnect: (() -> Unit)? = null
	private val mutex = Mutex(locked = true)

	private suspend fun connect() {
		suspendCoroutine<Unit> { cont ->
			connectionCont?.resumeWith(Result.failure(CancellationException()))
			connectionCont = cont
			gatt = device.connectGatt(context, false, Callback())
		}
		if (mutex.isLocked) {
			mutex.unlock()
		}
	}

	fun hasCharacteristic(uuid: UUID) = getCharacteristicOrNull(uuid) != null

	/**
	 * Reads the requested characteristic from the associated remote device
	 * @param uuid UUID of characteristic to read from the remote device
	 * @throws CharacteristicNotFoundException if characteristic with specific uuid not found
	 * @throws BleIOException
	 */
	suspend fun readCharacteristic(uuid: UUID): ByteArray = mutex.withLock {
		val characteristic = getCharacteristic(uuid)
		suspendCoroutine { cont ->
			characteristicReadCont = cont
			if (!gatt.readCharacteristic(characteristic)) {
				cont.resumeWithException(BleIOException("Unable to init read operation"))
				characteristicReadCont = null
			}
		}
	}

	suspend fun writeCharacteristic(uuid: UUID, value: ByteArray): ByteArray = mutex.withLock {
		val characteristic = getCharacteristic(uuid)
		suspendCoroutine { cont ->
			characteristic.value = value
			characteristicWriteCont = cont
			if (!gatt.writeCharacteristic(characteristic)) {
				cont.resumeWithException(BleIOException("Unable to init write operation"))
				characteristicWriteCont = null
			}
		}
	}

	suspend fun readDescriptor(uuid: UUID): ByteArray = mutex.withLock {
		val descriptor = getDescriptor(uuid)
		suspendCoroutine { cont ->
			descriptorReadCont = cont
			if (!gatt.readDescriptor(descriptor)) {
				cont.resumeWithException(BleIOException("Unable to init read operation"))
				descriptorReadCont = null
			}
		}
	}

	suspend fun writeDescriptor(uuid: UUID, value: ByteArray) = mutex.withLock {
		val descriptor = getDescriptor(uuid)
		suspendCoroutine<ByteArray> { cont ->
			descriptor.value = value
			descriptorWriteCont = cont
			if (!gatt.writeDescriptor(descriptor)) {
				cont.resumeWithException(BleIOException("Unable to init write operation"))
				descriptorWriteCont = null
			}
		}
	}

	fun observeCharacteristic(uuid: UUID): Flow<ByteArray> {
		val characteristic = getCharacteristic(uuid)
		if (!gatt.setCharacteristicNotification(characteristic, true)) {
			throw BleIOException("Unable to set up notifications")
		}
		val channel = Channel<ByteArray>()
		characteristicObservers[uuid] = channel
		channel.invokeOnClose {
			gatt.setCharacteristicNotification(characteristic, false)
			characteristicObservers.remove(uuid)
		}
		return channel.consumeAsFlow()
	}

	private fun getCharacteristicOrNull(uuid: UUID): BluetoothGattCharacteristic? {
		for (service in gatt.services ?: return null) {
			service.getCharacteristic(uuid)?.let {
				return it
			}
		}
		return null
	}

	private fun getCharacteristic(uuid: UUID) = getCharacteristicOrNull(uuid)
		?: throw CharacteristicNotFoundException("Characteristic $uuid not found")

	private fun getDescriptorOrNull(uuid: UUID): BluetoothGattDescriptor? {
		for (service in gatt.services ?: return null) {
			for (c in service.characteristics) {
				c.getDescriptor(uuid)?.let {
					return it
				}
			}
		}
		return null
	}

	private fun getDescriptor(uuid: UUID) =
		getDescriptorOrNull(uuid) ?: throw DescriptorNotFoundException("Descriptor $uuid not found")

	private inner class Callback : BluetoothGattCallback() {

		override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
			super.onConnectionStateChange(gatt, status, newState)
			if (newState == BluetoothProfile.STATE_CONNECTED && connectionCont != null) {
				gatt.discoverServices()
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				val exception = DeviceDisconnectedException(
					if (status == BluetoothGatt.GATT_SUCCESS) {
						null
					} else {
						getErrorMessage(status)
					}
				)
				characteristicReadCont?.resumeWithException(exception)
				characteristicWriteCont?.resumeWithException(exception)
				descriptorReadCont?.resumeWithException(exception)
				descriptorWriteCont?.resumeWithException(exception)
				connectionCont?.resumeWithException(exception)
				onDisconnect?.invoke()
			}
		}

		override fun onCharacteristicChanged(
			gatt: BluetoothGatt?,
			characteristic: BluetoothGattCharacteristic?
		) {
			super.onCharacteristicChanged(gatt, characteristic)
			characteristicObservers[characteristic?.uuid]?.sendBlocking(characteristic!!.value)
		}

		override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
			super.onServicesDiscovered(gatt, status)
			connectionCont?.resume(Unit)
		}

		override fun onCharacteristicRead(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic,
			status: Int
		) {
			characteristicReadCont?.resumeWith(
				when (status) {
					BluetoothGatt.GATT_SUCCESS -> Result.success(characteristic.value)
					else -> Result.failure(BleIOException(getErrorMessage(status)))
				}
			)
			characteristicReadCont = null
		}

		override fun onDescriptorRead(
			gatt: BluetoothGatt,
			descriptor: BluetoothGattDescriptor,
			status: Int
		) {
			descriptorReadCont?.resumeWith(
				when (status) {
					BluetoothGatt.GATT_SUCCESS -> Result.success(descriptor.value)
					else -> Result.failure(BleIOException(getErrorMessage(status)))
				}
			)
			descriptorReadCont = null
		}

		override fun onDescriptorWrite(
			gatt: BluetoothGatt?,
			descriptor: BluetoothGattDescriptor,
			status: Int
		) {
			descriptorWriteCont?.resumeWith(
				when (status) {
					BluetoothGatt.GATT_SUCCESS -> Result.success(descriptor.value)
					else -> Result.failure(BleIOException(getErrorMessage(status)))
				}
			)
			descriptorWriteCont = null
		}

		override fun onCharacteristicWrite(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic,
			status: Int
		) {
			characteristicWriteCont?.resumeWith(
				when (status) {
					BluetoothGatt.GATT_SUCCESS -> Result.success(characteristic.value)
					else -> Result.failure(BleIOException(getErrorMessage(status)))
				}
			)
			characteristicWriteCont = null
		}
	}

	override fun close() {
		gatt.close()
	}

	internal companion object {

		private fun getErrorMessage(status: Int) = when (status) {
			BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT read operation is not permitted"
			BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT write operation is not permitted"
			BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "Insufficient authentication for a given operation"
			BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "The given request is not supported"
			BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "Insufficient encryption for a given operation"
			BluetoothGatt.GATT_INVALID_OFFSET -> "A read or write operation was requested with an invalid offset"
			BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "A write operation exceeds the maximum length of the attribute"
			BluetoothGatt.GATT_CONNECTION_CONGESTED -> "A remote device connection is congested"
			else -> "A GATT operation failed: status code $status"
		}

		suspend fun connect(context: Context, device: BluetoothDevice): LegattoDevice {
			val gatt = LegattoDevice(context, device)
			gatt.connect()
			return gatt
		}
	}
}