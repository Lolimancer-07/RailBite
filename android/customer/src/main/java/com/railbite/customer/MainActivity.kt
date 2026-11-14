package com.railbite.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.railbite.core.ui.theme.RailBiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RailBiteCustomerApp).container
        setContent {
            RailBiteTheme {
                CustomerRoot(container)
            }
        }
    }
}

@Composable
private fun CustomerRoot(container: AppContainer) {
    val graph = rememberGraph(container)
    val start by graph.startDestination.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (start == null) {
            CircularProgressIndicator()
        } else {
            CustomerNavHost(graph, start!!)
        }
    }
}
