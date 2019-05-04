package com.superpowered.crossexample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private var playing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Checking permissions.
        val permissions = arrayOf(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        for (s in permissions) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                // Some permissions are not granted, ask the user.
                ActivityCompat.requestPermissions(this, permissions, 0)
                return
            }
        }

        // Got all permissions, initialize.
        initialize()

        playPause.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view?.let { CrossExample_PlayPause(it) }
                        return true
                    }
                }

                return false
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // Called when the user answers to the permission dialogs.
        if (requestCode != 0 || grantResults.size < 1 || grantResults.size != permissions.size) return
        var hasAllPermissions = true

        for (grantResult in grantResults)
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false
                Toast.makeText(applicationContext, "Please allow all permissions for the app.", Toast.LENGTH_LONG).show()
            }

        if (hasAllPermissions) initialize()
    }

    private fun initialize() {
        // Get the device's sample rate and buffer size to enable
        // low-latency Android audio output, if available.
        var samplerateString: String? = null
        var buffersizeString: String? = null
        if (Build.VERSION.SDK_INT >= 17) {
            val audioManager = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        }
        if (samplerateString == null) samplerateString = "48000"
        if (buffersizeString == null) buffersizeString = "480"
        val samplerate = Integer.parseInt(samplerateString)
        val buffersize = Integer.parseInt(buffersizeString)

        // Files under res/raw are not zipped, just copied into the APK.
        // Get the offset and length to know where our files are located.
        val apkPath = packageResourcePath
        val fd0 = resources.openRawResourceFd(R.raw.lycka)
        val fd1 = resources.openRawResourceFd(R.raw.nuyorica)
        val fileAoffset = fd0.startOffset.toInt()
        val fileAlength = fd0.length.toInt()
        val fileBoffset = fd1.startOffset.toInt()
        val fileBlength = fd1.length.toInt()
        try {
            fd0.parcelFileDescriptor.close()
            fd1.parcelFileDescriptor.close()
        } catch (e: IOException) {
            android.util.Log.d("", "Close error.")
        }

        // Initialize the players and effects, and start the audio engine.
        System.loadLibrary("CrossExample")
        // If the application crashes, please disable Instant Run under Build, Execution, Deployment in preferences.
        CrossExample(
                samplerate, // sampling rate
                buffersize, // buffer size
                apkPath, // path to .apk package
                fileAoffset, // offset (start) of file A in the APK
                fileAlength, // length of file A
                fileBoffset, // offset (start) of file B in the APK
                fileBlength     // length of file B
        )

        // Setup crossfader events
        val crossfader = findViewById<SeekBar>(R.id.crossFader)
        crossfader?.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onCrossfader(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Setup FX fader events
        val fxfader = findViewById<SeekBar>(R.id.fxFader)
        fxfader?.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onFxValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                onFxValue(seekBar.progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onFxOff()
            }
        })

        // Setup FX select event
        val group = findViewById<RadioGroup>(R.id.radioGroup1)
        group?.setOnCheckedChangeListener { radioGroup, checkedId ->
            val checkedRadioButton = radioGroup.findViewById<RadioButton>(checkedId)
            onFxSelect(radioGroup.indexOfChild(checkedRadioButton))
        }
    }

    // PlayPause - Toggle playback state of the player.
    fun CrossExample_PlayPause(button: View) {
        playing = !playing
        onPlayPause(playing)
        val b = findViewById<Button>(R.id.playPause)
        if (b != null) b.text = if (playing) "Pause" else "Play"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId


        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)

    }

    // Functions implemented in the native library.
    private external fun CrossExample(samplerate: Int, buffersize: Int, apkPath: String, fileAoffset: Int, fileAlength: Int, fileBoffset: Int, fileBlength: Int)

    private external fun onPlayPause(play: Boolean)
    private external fun onCrossfader(value: Int)
    private external fun onFxSelect(value: Int)
    private external fun onFxOff()
    private external fun onFxValue(value: Int)
}
