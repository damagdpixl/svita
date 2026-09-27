package com.damagdpixl.svita

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.damagdpixl.svita.core.designsystem.EditorialTheme
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.SvitaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Manual DI graph: database + repositories + photo storage.
        SvitaGraph.init(this)
        enableEdgeToEdge()
        setContent {
            EditorialTheme {
                SvitaApp()
            }
        }
    }
}
