package com.example.capstone

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private lateinit var repository: CarPropertyRepository

    private val requiredCarPermissions = arrayOf(
        "android.car.permission.CAR_SPEED",
        "android.car.permission.CAR_ENERGY"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        repository.connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = CarPropertyRepository(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by repository.state.collectAsStateWithLifecycle()
                CarDashboardScreen(state)
            }
        }

        val missing = requiredCarPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            repository.connect()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        repository.disconnect()
        super.onDestroy()
    }
}

@Composable
fun CarDashboardScreen(state: CarUiState) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column {
            Text(
                text = "AutomotiveCarPropApp",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(propertyCards(state)) { card -> PropertyCard(card) }
            }
        }
    }
}

private data class PropertyCardData(val title: String, val unit: String?, val value: String)

private fun propertyCards(state: CarUiState): List<PropertyCardData> = listOf(
    PropertyCardData("Speed", "MPH", state.speedMph.toString()),
    PropertyCardData("Gear", null, state.gear),
    PropertyCardData("Fuel Level", null, state.fuelLevel),
    PropertyCardData("Manufacturer", null, state.manufacturer),
    PropertyCardData("Car Model", null, state.carModel),
    PropertyCardData("Model Year", null, state.modelYear),
)

@Composable
private fun PropertyCard(data: PropertyCardData) {
    Column(
        modifier = Modifier
            .background(Color(0xFF1C1C1C))
            .fillMaxWidth()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(data.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        data.unit?.let {
            Text(it, color = Color(0xFF4FC3F7), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = data.value,
                color = Color(0xFFFF5722),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}
