package com.billpro.app.printing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

object BluetoothThermalPrinter {

    // Standard SPP (Serial Port Profile) UUID for Bluetooth POS Thermal Printers
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PREFS_NAME = "billpro_printer_prefs"
    private const val KEY_LAST_PRINTER = "last_bt_printer_address"

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun isBluetoothSupported(context: Context): Boolean {
        return getBluetoothAdapter(context) != null
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return getBluetoothAdapter(context)?.isEnabled == true
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedPrinters(context: Context): List<BluetoothPrinterDevice> {
        if (!hasBluetoothPermission(context)) {
            return emptyList()
        }
        val adapter = getBluetoothAdapter(context) ?: return emptyList()
        if (!adapter.isEnabled) {
            return emptyList()
        }

        return try {
            val bonded = adapter.bondedDevices ?: return emptyList()
            bonded.map { device ->
                BluetoothPrinterDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLastPrinterAddress(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PRINTER, null)
    }

    fun setLastPrinterAddress(context: Context, address: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PRINTER, address)
            .apply()
    }

    /**
     * Connects to the Bluetooth thermal printer via SPP and streams raw ESC/POS bytes.
     */
    @SuppressLint("MissingPermission")
    suspend fun printEscPos(
        context: Context,
        address: String,
        bytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission(context)) {
            return@withContext Result.failure(SecurityException("Bluetooth permission (BLUETOOTH_CONNECT) is required to print"))
        }

        val adapter = getBluetoothAdapter(context)
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth is not supported on this device"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is turned off. Please turn it on in Settings."))
        }

        var socket: BluetoothSocket? = null
        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(address)

            // Cancel discovery to free up Bluetooth bandwidth
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (_: Exception) {}

            // Try standard secure SPP socket, fallback to insecure if needed
            socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            }

            socket.connect()

            val outputStream = socket.outputStream
            outputStream.write(bytes)
            outputStream.flush()

            // Small delay to ensure thermal printer buffer finishes printing
            delay(350)

            // Remember successfully used printer
            setLastPrinterAddress(context, address)

            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IOException("Failed to connect to printer at $address: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}

