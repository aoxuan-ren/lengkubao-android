package com.pingwei.lengkubao.ui.instock.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.ui.common.rememberHideKeyboardOnly
import com.pingwei.lengkubao.ui.theme.AppDimens

/**
 * 经手人选择框：点选列表，不弹出软键盘。
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
    var dropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val hideKeyboard = rememberHideKeyboardOnly()
    val interactionSource = remember { MutableInteractionSource() }
    val inputTextStyle = fieldTextStyle ?: MaterialTheme.typography.bodyMedium
    val labelTextStyle = fieldLabelStyle ?: MaterialTheme.typography.bodyLarge

    fun openDropdown() {
        hideKeyboard()
        focusManager.clearFocus(force = true)
        dropdownExpanded = true
    }

    fun closeDropdown() {
        dropdownExpanded = false
        hideKeyboard()
        focusManager.clearFocus(force = true)
    }

    fun selectOperator(operator: Operator) {
        if (!operator.enabled) return
        dropdownExpanded = false
        onOperatorSelected(operator)
        hideKeyboard()
        focusManager.clearFocus(force = true)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                if (dropdownExpanded) {
                    closeDropdown()
                } else {
                    openDropdown()
                }
            }
        }
    }

    val fieldValue = selectedOperator?.name.orEmpty()
    val enabledOperators = remember(operators) { operators.filter { it.enabled } }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(label, style = labelTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            placeholder = { Text("点击选择经手人", style = inputTextStyle) },
            textStyle = inputTextStyle,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = fieldHeight)
                .focusProperties { canFocus = false },
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
                            openDropdown()
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
                if (enabledOperators.isEmpty()) {
                    Text(
                        text = "暂无经手人数据",
                        modifier = Modifier.padding(AppDimens.pagePadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = AppDimens.dialogListMaxHeight)
                    ) {
                        items(enabledOperators, key = { it.id }) { operator ->
                            ListItem(
                                headlineContent = { Text(operator.name) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(operator.id) {
                                        detectTapGestures(onTap = { selectOperator(operator) })
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
