package com.quietping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity hosting the entire Jetpack Compose UI (Clean Architecture
 * + MVVM, unidirectional data flow). It is the [targetActivity] for every
 * launcher [activity-alias]; the disguised/mono/default icons all resolve here.
 *
 * [AndroidEntryPoint] enables field/ViewModel injection. The theme and nav graph
 * are owned by the UI layer ([com.quietping.ui.theme.QuietPingTheme] and
 * [com.quietping.ui.nav.QuietPingNavGraph]); this Activity only wires them up.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            com.quietping.ui.theme.QuietPingTheme {
                com.quietping.ui.nav.QuietPingNavGraph()
            }
        }
    }
}
