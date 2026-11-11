package org.emlii.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var vm: ChatViewModel
    private lateinit var micPermission: ActivityResultLauncher<String>
    private lateinit var pickWallpaper: ActivityResultLauncher<String>
    private lateinit var pickPlugin: ActivityResultLauncher<Array<String>>
    private var voiceOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ChatViewModel(applicationContext)
        vm.voice = VoiceOutput(applicationContext)

        micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) voiceOpen = true
            else Toast.makeText(this, "Microphone permission is needed for voice mode.", Toast.LENGTH_SHORT).show()
        }

        pickWallpaper = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) vm.setWallpaperFromUri(uri)
        }
        pickPlugin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importPlugin(uri)
        }

        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    ChatScreen(
                        vm = vm,
                        onMic = { openVoice() },
                        onPickWallpaper = { pickWallpaper.launch("image/*") },
                        onImportPlugin = { pickPlugin.launch(arrayOf("*/*")) }
                    )
                    if (voiceOpen) {
                        VoiceScreen(vm = vm, voice = vm.voice, onClose = { voiceOpen = false })
                    }
                }
            }
        }
    }

    private fun openVoice() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) voiceOpen = true else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onDestroy() {
        vm.voice?.shutdown()
        super.onDestroy()
    }
}
