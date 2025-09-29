package com.example.smart_handle.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.smart_handle.ui.common.RouteSummaryBottomSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val tabs = listOf("길찾기", "운동", "관광")
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // BottomSheet 상태
    var showSheet by remember { mutableStateOf(false) }
    var sheetDistance by remember { mutableStateOf("") }
    var sheetTime by remember { mutableStateOf("") }
    var sheetInfo by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState()

    // 메인 화면
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 제목
        Text(
            text = "🚴 Bike Navi",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 카드 (탭 + 내용)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column {
                // 탭
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.scrollToPage(index) } },
                            text = { Text(title) }
                        )
                    }
                }

                // 탭 내용
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .padding(16.dp)
                ) { page ->
                    when (page) {
                        0 -> DirectionsTab(onGenerate = {
                            sheetDistance = "12.4km"
                            sheetTime = "42분"
                            sheetInfo = "일반 코스"
                            showSheet = true
                        })
                        1 -> WorkoutTab(onGenerate = {
                            sheetDistance = "10km"
                            sheetTime = "35분"
                            sheetInfo = "운동 코스"
                            showSheet = true
                        })
                        2 -> TourTab(onGenerate = { placeCount ->
                            sheetDistance = "18.7km"
                            sheetTime = "65분"
                            sheetInfo = "관광 코스 (${placeCount}개 명소 경유)"
                            showSheet = true
                        })
                    }
                }
            }
        }
    }

    // BottomSheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            RouteSummaryBottomSheet(
                distance = sheetDistance,
                time = sheetTime,
                info = sheetInfo,
                onStartClick = { showSheet = false }
            )
        }
    }
}

/* ---------- 각 탭 UI ---------- */

@Composable
fun DirectionsTab(onGenerate: () -> Unit) {
    Column {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("출발지를 입력하세요") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("도착지를 입력하세요") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text("경로 생성")
        }
    }
}

@Composable
fun WorkoutTab(onGenerate: () -> Unit) {
    Column {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("목표 거리 (km)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text("경로 생성")
        }
    }
}

@Composable
fun TourTab(onGenerate: (Int) -> Unit) {
    var places by remember { mutableStateOf(listOf("")) }

    Column {
        // 동적으로 장소 입력 필드 생성
        places.forEachIndexed { index, place ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = place,
                    onValueChange = { newValue ->
                        places = places.toMutableList().also { it[index] = newValue }
                    },
                    placeholder = { Text("관광지 ${index + 1}") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                // 삭제 버튼 (최소 1개는 유지)
                if (places.size > 1) {
                    IconButton(onClick = {
                        places = places.toMutableList().also { it.removeAt(index) }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "삭제"
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 장소 추가 버튼
        Button(
            onClick = { places = places + "" },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ 장소 추가")
        }

        Spacer(Modifier.height(16.dp))

        // 경로 생성 버튼
        Button(onClick = { onGenerate(places.size) }, modifier = Modifier.fillMaxWidth()) {
            Text("경로 생성")
        }
    }
}
