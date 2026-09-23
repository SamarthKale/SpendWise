package com.mj.spendwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mj.spendwise.navigation.SpendWiseNavHost
import com.mj.spendwise.ui.theme.SpendWiseTheme

/** Single Activity; all screens are Compose destinations. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpendWiseTheme {
                SpendWiseNavHost()
            }
        }
    }
}
