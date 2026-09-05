package com.pingwei.lengkubao.ui.query.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.common.rememberDismissKeyboard
import com.pingwei.lengkubao.utils.BillQuerySearchFilter

/**
 * 查询筛选栏 - 包含搜索框和时间筛选
 */
@Composable
fun QueryFilterBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    timeRangeLabel: String,
    onTimeRangeClick: () -> Unit,
    // 新增：添加 onFilterChipClick 参数，并显式指定类型 (String) -> Unit
    onFilterChipClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissKeyboard = rememberDismissKeyboard()
    val searchFocusRequester = remember { FocusRequester() }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.cardPadding)
        ) {
            // 搜索行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(AppDimens.searchFieldHeight)
                        .focusRequester(searchFocusRequester),
                    placeholder = {
                        Text(
                            text = BillQuerySearchFilter.PLACEHOLDER,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    // 修改点1：仅保留 KeyboardOptions 支持的参数，移除不存在的 autocorrect
                    // 补充 imeAction = ImeAction.Search 优化体验（可选，不影响原有功能）
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    // 关键：不添加任何不存在的参数，避免匹配不到重载方法
                )

                Button(
                    onClick = {
                        onSearch()
                        searchFocusRequester.requestFocus()
                    },
                    modifier = Modifier.height(AppDimens.searchFieldHeight)
                ) {
                    Text("搜索")
                }
            }

            // 时间筛选行（完全保留你的原有代码，无任何改动）
            // 在 QueryFilterBar.kt 中修改 FilterChip 部分：
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
            ) {
                listOf("今天", "7天", "本月", "全部").forEach { label ->
                    FilterChip(
                        selected = timeRangeLabel == label,
                        onClick = {
                            dismissKeyboard()
                            onFilterChipClick(label)
                        },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = {
                        dismissKeyboard()
                        onTimeRangeClick()
                    },
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text(timeRangeLabel)
                }
            }
        }
    }
}