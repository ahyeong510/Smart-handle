package com.example.smart_handle.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RouteSummaryBottomSheet(
    distance: String,
    time: String,
    info: String,
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(text = "경로 요약", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("거리")
            Text(distance)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("예상 시간")
            Text(time)
        }

        Spacer(Modifier.height(12.dp))
        Text("경로 정보: $info")

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("주행 시작")
        }
    }
}
