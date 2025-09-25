package com.example.bike.ui.device
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CenterAlignedTopAppBar
import com.example.bike.ui.device.DeviceScreen

// --- 임시 데이터 모델 (BLE 연동 전) ---
data class DeviceUi(
    val name: String,
    val rssi: Int? = null,
    val battery: Int? = null,
    val connected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    connected: DeviceUi? = DeviceUi("BikeHaptic-001", battery = 85, rssi = -56, connected = true),
    available: List<DeviceUi> = listOf(
        DeviceUi("BikeHaptic-002", rssi = -72, battery = 62),
        DeviceUi("BikeHaptic-003", rssi = -68, battery = 74)
    ),
    onRefresh: () -> Unit = {},
    onConnect: (DeviceUi) -> Unit = {},
    onDisconnect: () -> Unit = {},
    onTestLeft: () -> Unit = {},
    onTestRight: () -> Unit = {},
    onPattern: (String) -> Unit = {},
    onVibrationLevel: (Float) -> Unit = {},
    onLeftLed: (Boolean) -> Unit = {},
    onRightLed: (Boolean) -> Unit = {}
) {
    var vib by remember { mutableFloatStateOf(0.75f) }
    var leftLed by remember { mutableStateOf(true) }
    var rightLed by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("디바이스 연결") },
                actions = {
                    OutlinedButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("새로고침") }
                }
            )
        }
    ) { padding ->

    LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 연결된 디바이스 카드
            item {
                ConnectedDeviceCard(
                    device = connected,
                    vib = vib,
                    onChangeVib = { vib = it; onVibrationLevel(it) },
                    leftLed = leftLed,
                    rightLed = rightLed,
                    onLeftLed = { leftLed = it; onLeftLed(it) },
                    onRightLed = { rightLed = it; onRightLed(it) },
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onTestLeft = onTestLeft,
                    onTestRight = onTestRight,
                    onPattern = onPattern
                )
            }

            // 사용 가능한 디바이스 목록
            item { Text("사용 가능한 디바이스", style = MaterialTheme.typography.titleMedium) }
            items(available) { d -> AvailableDeviceItem(device = d, onConnect = onConnect) }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: DeviceUi?,
    vib: Float,
    onChangeVib: (Float) -> Unit,
    leftLed: Boolean,
    rightLed: Boolean,
    onLeftLed: (Boolean) -> Unit,
    onRightLed: (Boolean) -> Unit,
    onConnect: (DeviceUi) -> Unit,
    onDisconnect: () -> Unit,
    onTestLeft: () -> Unit,
    onTestRight: () -> Unit,
    onPattern: (String) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(device?.name ?: "연결된 디바이스 없음", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (device?.connected == true) AssistChip(onClick = {}, label = { Text("연결됨") })
                else if (device != null) Button(onClick = { onConnect(device) }) { Text("연결") }
            }

            if (device != null) {
                Spacer(Modifier.height(12.dp))

                Text("배터리", style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(
                    progress = { (device.battery ?: 85) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${device.battery ?: 85}%", style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(12.dp))

                Text("기본 모터 테스트 (트리플 탭)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTestLeft) { Text("좌측 모터") }
                    OutlinedButton(onClick = onTestRight) { Text("우측 모터") }
                    if (device.connected) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDisconnect) { Text("연결 해제") }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("패턴 테스트", style = MaterialTheme.typography.labelLarge)
                FlowRowMainAxisSpaced {
                    PatternChip("좌대각선") { onPattern("diag_left") }
                    PatternChip("우대각선") { onPattern("diag_right") }
                    PatternChip("유턴") { onPattern("u_turn") }
                    PatternChip("경로 이탈") { onPattern("off_route") }
                    PatternChip("도착지 부근") { onPattern("near_dest") }
                    PatternChip("펄스") { onPattern("pulse") }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("진동 강도", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f)); Text("${(vib * 100).toInt()}%")
                }
                Slider(value = vib, onValueChange = onChangeVib)

                Spacer(Modifier.height(8.dp))
                Text("LED 설정", style = MaterialTheme.typography.labelLarge)
                SettingSwitch("좌측 LED", leftLed, onLeftLed)
                SettingSwitch("우측 LED", rightLed, onRightLed)
            }
        }
    }
}

@Composable
private fun AvailableDeviceItem(device: DeviceUi, onConnect: (DeviceUi) -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                val sub = buildString {
                    device.rssi?.let { append("RSSI: ${it}dBm  ") }
                    device.battery?.let { append("${it}%") }
                }
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            if (device.connected) AssistChip(onClick = {}, label = { Text("연결됨") })
            else Button(onClick = { onConnect(device) }) { Text("연결") }
        }
    }
}

@Composable
private fun PatternChip(text: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(text) },
        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 임시 FlowRow (칩을 가로로 나열) */
@Composable
private fun FlowRowMainAxisSpaced(content: @Composable () -> Unit) {
    Column { Row { content() } }
}
