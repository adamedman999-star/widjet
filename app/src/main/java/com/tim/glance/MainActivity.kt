package com.tim.glance

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }
        val text = TextView(this).apply {
            text = "Выдай доступ к геолокации, чтобы виджет показывал погоду.\n" +
                "Потом добавь виджет Glance на рабочий стол."
            textSize = 18f
        }
        val button = Button(this).apply {
            this.text = "Дать доступ к геолокации"
            setOnClickListener { askPermission() }
        }
        root.addView(text)
        root.addView(button)
        setContentView(root)
    }

    private fun askPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            GlanceWidget.updateAll(this)
        }
    }

    override fun onResume() {
        super.onResume()
        GlanceWidget.updateAll(this)
    }
}
