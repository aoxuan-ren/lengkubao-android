package com.pingwei.lengkubao.ui.instock.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.utils.OperatorSearchFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 可搜索经手人选择框：支持编号、姓名、拼音首字母实时过滤。
 */
@Composable
fun SearchableOperatorField(
    operators: List<Operator>,
    selectedOperator: Operator?,
    onOperatorSelected: (Operator?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "选择经手人",
    isError: Boolean = false,
    fieldHeight: Dp = AppDimens.searchFieldHeight,
    fieldTextStyle: TextStyle? = null,
    fieldLabelStyle: TextStyle? = null
) {
    var searchText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isUserEditing by remember { mutableStateOf(false) }
    var isSelecting by remember { mutableStateOf(false) }
    var pendingBlurClose by remember { mutableStateOf<Job?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val inputTextStyle = fieldTextStyle ?: MaterialTheme.typography.bodyMedium
    val labelTextStyle = fieldLabelStyle ?: MaterialTheme.typography.bodyLarge

    fun beginSearchInput() {
        dropdownExpanded = true
        isUserEditing = true
        if (selectedOperator != null) {
            searchText = ""
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                val operator = selectedOperator ?: return@collect
                if (searchText == OperatorSearchFilter.displayName(operator)) {
                    beginSearchInput()
                }
            }
        }
    }

    fun finishBlurClose() {
        if (isSelecting) return
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedOperator?.let { OperatorSearchFilter.displayName(it) } ?: searchText
    }

    LaunchedEffect(selectedOperator) {
        if (!dropdownExpanded && !isUserEditing) {
            searchText = selectedOperator?.let { OperatorSearchFilter.displayName(it) } ?: ""
        }
    }

    val filteredOperators = remember(searchText, operators, dropdownExpanded) {
        val keyword = if (dropdownExpanded) searchText else ""
        OperatorSearchFilter.filter(operators, keyword)
    }

    fun openDropdownForSearch(requestFocus: Boolean = false) {
        beginSearchInput()
        if (requestFocus) {
            coroutineScope.launch {
                focusRequester.requestFocus()
            }
        }
    }

    fun closeDropdown() {
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedOperator?.let { OperatorSearchFilter.displayName(it) } ?: searchText
    }

    fun selectOperator(operator: Operator) {
        if (!operator.enabled) return
        isSelecting = true
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        searchText = OperatorSearchFilter.displayName(operator)
        onOperatorSelected(operator)
        isUserEditing = false
        isSelecting = false
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { newValue ->
                searchText = newValue
                dropdownExpanded = true
                isUserEditing = true
            },
            label = {
                Text(label, style = labelTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            placeholder = { Text(OperatorSearchFilter.PLACEHOLDER, style = inputTextStyle) },
            textStyle = inputTextStyle,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        pendingBlurClose?.cancel()
                        openDropdownForSearch()
                    } else {
                        pendingBlurClose?.cancel()
                        pendingBlurClose = coroutineScope.launch {
                            delay(150)
                            finishBlurClose()
                        }
                    }
                },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = label)
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "展开经手人列表",
                    modifier = Modifier.clickable {
                        if (dropdownExpanded) {
                            closeDropdown()
                        } else {
                            openDropdownForSearch(requestFocus = true)
                        }
                    }
                )
            },
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                unfocusedBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        )

        if (dropdownExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = fieldHeight)
                    .zIndex(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                if (filteredOperators.isEmpty()) {
                    Text(
                        text = if (searchText.isBlank()) "暂无经手人数据" else "未找到相关经手人",
                        modifier = Modifier.padding(AppDimens.pagePadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = AppDimens.dialogListMaxHeight)
                    ) {
                        items(filteredOperators, key = { it.id }) { operator ->
                            ListItem(
                                headlineContent = { Text(operator.name) },
                                supportingContent = { Text("编号: ${operator.operatorNo}") },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (operator.enabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = operator.enabled) {
                                        selectOperator(operator)
                                    }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
