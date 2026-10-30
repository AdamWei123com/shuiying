package com.shiftcam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shiftcam.app.AppGraph
import kotlinx.coroutines.launch

/** S01 首次启动引导：说明定位会写入照片水印、数据不上传（R27 / 隐私合规）。 */
@Composable
fun OnboardingScreen(graph: AppGraph, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("机械班次水印相机", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(
                "给工程机械驾驶员用的离线打卡与计数工具：\n" +
                    "上班打卡 → 切换施工方式 → 计数 → 下班打卡 → 拍照留证。\n\n" +
                    "说明：\n" +
                    "1. 照片水印会写入定位地址与拍摄时间，位置精度不足 100 米时不显示经纬度。\n" +
                    "2. 作业数据只存在本机，不上传服务器。\n" +
                    "3. 卸载会丢失数据，请定期在设置中导出备份。\n" +
                    "4. 无网络、无定位、无权限都不影响打卡和拍照。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    scope.launch {
                        graph.settingRepository.setOnboardingDone(true)
                        onDone()
                    }
                },
            ) {
                Text("开始使用")
            }
        }
    }
}
