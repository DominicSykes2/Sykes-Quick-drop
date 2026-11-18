package com.sykes.sykeslink

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {
    private var pcIpAddress by mutableStateOf("Not Connected")
    private var pcIpInput by mutableStateOf("")
    private var statusLog by mutableStateOf("Drop Link Android Engine Ready.\n")
    private val PORT = 50555
    private val DISCOVERY_PORT = 50556
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sendFileToPC(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startReceiverServer()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Drop Link (C) Dominic Sykes", fontSize = 22.sp, style = MaterialTheme.typography.titleLarge)
                        Text("PC Host: $pcIpAddress", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                        Button(onClick = { discoverPC() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Auto-Discover PC on Wi-Fi")
                        }

                        OutlinedTextField(
                            value = pcIpInput,
                            onValueChange = { pcIpInput = it.trim() },
                            label = { Text("PC IP address (shown in PC app)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { connectToPCByIp() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Connect to PC by IP")
                        }

                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Select Document / File to Send to PC")
                        }

                        HorizontalDivider()

                        Text("Activity Log:", style = MaterialTheme.typography.titleMedium)
                        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(statusLog, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun log(msg: String) {
        withContext(Dispatchers.Main) {
            statusLog += "$msg\n"
        }
    }

    private fun discoverPC() {
        ioScope.launch {
            try {
                log("Broadcasting to discover PC...")
                val socket = DatagramSocket()
                socket.broadcast = true
                val data = "SYKESLINK_DISCOVER_PC".toByteArray()
                val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                socket.send(packet)

                socket.soTimeout = 3000
                val recvBuf = ByteArray(256)
                val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                socket.receive(recvPacket)

                val response = String(recvPacket.data, 0, recvPacket.length)
                if (response.startsWith("SYKESLINK_HERE:")) {
                    val discoveredIp = response.substringAfter("SYKESLINK_HERE:")
                    withContext(Dispatchers.Main) {
                        pcIpAddress = discoveredIp
                    }
                    log("Connected to PC at $discoveredIp")
                }
                socket.close()
            } catch (e: Exception) {
                log("Auto-discovery failed: ${e.localizedMessage}. Enter the PC Host IP below and tap Connect to PC by IP.")
            }
        }
    }

    private fun connectToPCByIp() {
        val address = pcIpInput.trim()
        if (address.isBlank()) {
            ioScope.launch { log("Enter the Host IP shown in the PC app.") }
            return
        }

        ioScope.launch {
            try {
                log("Checking PC connection at $address:$PORT...")
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(address, PORT), 3000)
                }
                withContext(Dispatchers.Main) {
                    pcIpAddress = address
                }
                log("Connected to PC at $address")
            } catch (e: Exception) {
                log("Could not connect to $address:$PORT: ${e.localizedMessage}. Check the PC IP, Wi-Fi, and firewall.")
            }
        }
    }

    private fun startReceiverServer() {
        ioScope.launch {
            try {
                val serverSocket = ServerSocket(PORT)
                log("Receiver server active on port $PORT")
                while (true) {
                    val client = serverSocket.accept()
                    launch { handleIncomingFromPC(client) }
                }
            } catch (e: Exception) {
                log("Server error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun handleIncomingFromPC(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val lenBytes = ByteArray(4)
            input.read(lenBytes)
            val headerLen = ByteBuffer.wrap(lenBytes).int

            val headerBytes = ByteArray(headerLen)
            input.read(headerBytes)
            val json = JSONObject(String(headerBytes, Charsets.UTF_8))
            val filename = json.getString("filename")
            val filesize = json.getLong("filesize")

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)

            FileOutputStream(file).use { fos ->
                val buffer = ByteArray(65536)
                var received = 0L
                while (received < filesize) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    received += read
                }
            }
            socket.outputStream.write("ACK_SUCCESS".toByteArray())
            socket.close()

            log("Received file saved to Downloads: $filename")
        } catch (e: Exception) {
            log("Error receiving file: ${e.localizedMessage}")
        }
    }

    private fun sendFileToPC(uri: Uri) {
        if (pcIpAddress == "Not Connected") {
            ioScope.launch { log("Error: Connect or discover PC first.") }
            return
        }

        ioScope.launch {
            try {
                val contentResolver = applicationContext.contentResolver
                val cursor = contentResolver.query(uri, null, null, null, null)
                var filename = "shared_file"
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) filename = it.getString(nameIndex)
                    }
                }

                val pfd = contentResolver.openFileDescriptor(uri, "r")
                val filesize = pfd?.statSize ?: 0L
                pfd?.close()
                val inputStream = contentResolver.openInputStream(uri)

                log("Sending $filename ($filesize bytes) to PC ($pcIpAddress)...")

                val socket = Socket(pcIpAddress, PORT)
                val headerJson = JSONObject().apply {
                    put("filename", filename)
                    put("filesize", filesize)
                }.toString().toByteArray(Charsets.UTF_8)

                val output = socket.outputStream
                output.write(ByteBuffer.allocate(4).putInt(headerJson.size).array())
                output.write(headerJson)

                inputStream?.use { it.copyTo(output) }
                output.flush()

                val ack = ByteArray(16)
                socket.inputStream.read(ack)
                socket.close()

                log("Successfully sent $filename to PC!")
            } catch (e: Exception) {
                log("Failed to send file: ${e.localizedMessage}")
            }
        }
    }
}
