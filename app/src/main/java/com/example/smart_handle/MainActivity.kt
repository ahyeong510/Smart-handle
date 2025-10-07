package com.example.smart_handle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_handle.ui.theme.SMARTHANDLETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SMARTHANDLETheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // ✅ 현재는 SettingsScreen 실행 (테스트용)
                    SettingsScreen()
                    // 나중에 Navigation 적용하면 HomeScreen() 도 연결 가능
                }
            }
        }
    }
}

/* ------------------------
   ⚙️  SettingsScreen (팀원 UI)
   ------------------------ */
@Composable
fun SettingsScreen() {
    var firstAlert by remember { mutableFloatStateOf(80f) }
    var secondAlert by remember { mutableFloatStateOf(20f) }

    val patterns = listOf(
        PatternItemData("좌회전", "트리플 탭 (고정)", "3회 연속 진동", Icons.Default.ChevronLeft, lockFixed = true),
        PatternItemData("우회전", "트리플 탭 (고정)", "3회 연속 진동", Icons.Default.ChevronRight, lockFixed = true),
        PatternItemData("좌대각선", "더블 탭", "2회 연속 진동", Icons.Default.Route),
        PatternItemData("우대각선", "더블 탭", "2회 연속 진동", Icons.Default.Directions),
        PatternItemData("유턴", "긴 진동", "500ms 단일 진동", Icons.Default.MyLocation),
        PatternItemData("경로 이탈", "버스트", "5회 급속 반복", Icons.Default.Build),
        PatternItemData("도착지 부근", "펄스", "점진적 강도 변화", Icons.Default.Info)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item { SectionHeader("거리 임계값 설정") }
        item {
            DistanceCard("1차 알림", firstAlert, { firstAlert = it }, "m", 0f..200f)
        }
        item {
            DistanceCard("2차 알림 (강화)", secondAlert, { secondAlert = it }, "m", 0f..200f)
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { SectionHeader("진동 패턴 설정") }
        items(patterns) { PatternRow(it) }
        item { Spacer(Modifier.height(12.dp)) }
        item { TipBlock() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/* ------------------------
   📍  HomeScreen (아영이 UI)
   ------------------------ */
@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("출발지를 입력하세요", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text("도착지를 입력하세요", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { /* TODO: 경로 생성 로직 */ }) {
            Text("경로 생성")
        }
    }
}

/* ------------------------
   🧩  Settings 관련 컴포저블
   ------------------------ */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
    )
}

@Composable
fun DistanceCard(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueSuffix: String,
    valueRange: ClosedFloatingPointRange<Float>
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${value.toInt()}$valueSuffix", style = MaterialTheme.typography.titleMedium)
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
        }
    }
}

@Composable
fun PatternRow(data: PatternItemData) {
    var chosen by rememberSaveable(data.title) { mutableStateOf(data.subtitle) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    data.leading ?: Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(data.title, style = MaterialTheme.typography.titleMedium)
                    if (data.lockFixed) {
                        Pill("고정", small = true, enabled = false, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Pill(
                        text = data.subtitle,
                        selected = (chosen == data.subtitle),
                        enabled = true
                    ) { chosen = data.subtitle }

                    Spacer(Modifier.width(8.dp))

                    Pill(
                        text = data.meta,
                        selected = (chosen == data.meta),
                        enabled = true
                    ) { chosen = data.meta }
                }
            }

            Icon(Icons.Default.Settings, contentDescription = null)
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val padV = if (small) 2.dp else 4.dp
    val padH = if (small) 8.dp else 10.dp

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .then(
                if (onClick != null)
                    Modifier.clickable(enabled = enabled) { onClick() }
                else Modifier
            )
            .padding(horizontal = padH, vertical = padV),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = fg,
            fontSize = if (small) 11.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TipBlock() {
    Column(Modifier.fillMaxWidth()) {
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFFFF2CC))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "팁: 좌회전/우회전은 안전을 위해 고정 패턴이며, 다른 패턴들은 원하는 진동 종류로 변경할 수 있습니다. 실제 진동 테스트는 네비앱 화면에서 가능합니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

data class PatternItemData(
    val title: String,
    val subtitle: String,
    val meta: String,
    val leading: ImageVector? = null,
    val lockFixed: Boolean = false
)
