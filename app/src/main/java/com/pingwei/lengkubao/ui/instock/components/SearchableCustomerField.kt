package com.pingwei.lengkubao.ui.instock.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.common.rememberHideKeyboardOnly
import com.pingwei.lengkubao.utils.CustomerSearchFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 可搜索客户选择框：支持编号、名称、拼音首字母实时过滤。
 * 下拉列表以 Popup 浮层展示，不撑高输入框本身。
 */
@Composable
fun SearchableCustomerField(
    customers: List<Customer>,
    selectedCustomer: Customer?,
    onCustomerSelected: (Customer?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "客户",
    isError: Boolean = false,
    fieldHeight: Dp = AppDimens.searchFieldHeight,
    fieldTextStyle: TextStyle? = null,
    fieldLabelStyle: TextStyle? = null,
    /** 为 false 时不用浮动 label，避免进入界面时高度闪动 */
    showFloatingLabel: Boolean = true,
) {
    var searchText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isUserEditing by remember { mutableStateOf(false) }
    var isSelecting by remember { mutableStateOf(false) }
    var pendingBlurClose by remember { mutableStateOf<Job?>(null) }
    var fieldWidthPx by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val hideKeyboard = rememberHideKeyboardOnly()
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val inputTextStyle = fieldTextStyle ?: MaterialTheme.typography.bodyMedium
    val labelTextStyle = fieldLabelStyle ?: MaterialTheme.typography.bodyLarge

    fun beginSearchInput() {
        dropdownExpanded = true
        isUserEditing = true
        searchText = ""
    }

    fun finishBlurClose() {
        if (isSelecting) return
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun openDropdownForSearch(requestFocus: Boolean = false, clearForSearch: Boolean = false) {
        dropdownExpanded = true
        if (clearForSearch) {
            beginSearchInput()
        } else {
            isUserEditing = false
            if (selectedCustomer != null) {
                searchText = CustomerSearchFilter.displayName(selectedCustomer)
            }
        }
        if (requestFocus) {
            coroutineScope.launch {
                focusRequester.requestFocus()
            }
        }
    }

    fun restoreDisplayFromSelection() {
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun closeDropdown() {
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun selectCustomer(customer: Customer) {
        isSelecting = true
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        searchText = CustomerSearchFilter.displayName(customer)
        isUserEditing = false
        onCustomerSelected(customer)
        hideKeyboard()
        coroutineScope.launch {
            delay(200)
            isSelecting = false
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release && selectedCustomer != null) {
                if (!dropdownExpanded) {
                    openDropdownForSearch(requestFocus = true, clearForSearch = true)
                } else if (searchText == CustomerSearchFilter.displayName(selectedCustomer)) {
                    beginSearchInput()
                }
            }
        }
    }

    LaunchedEffect(selectedCustomer?.id) {
        if (!dropdownExpanded && !isUserEditing) {
            searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: ""
        }
    }

    val filterKeyword = when {
        !dropdownExpanded -> ""
        isUserEditing -> searchText
        selectedCustomer != null &&
            searchText == CustomerSearchFilter.displayName(selectedCustomer) -> ""
        else -> searchText
    }

    val filteredCustomers = remember(filterKeyword, customers, dropdownExpanded) {
        CustomerSearchFilter.filter(customers, filterKeyword)
    }

    val fieldValue = when {
        isUserEditing || dropdownExpanded -> searchText
        selectedCustomer != null -> CustomerSearchFilter.displayName(selectedCustomer)
        else -> searchText
    }

    Box(
        modifier = modifier
            .height(fieldHeight)
            .onGloballyPositioned { fieldWidthPx = it.size.width }
    ) {
        val focusModifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    pendingBlurClose?.cancel()
                    openDropdownForSearch()
                } else {
                    pendingBlurClose?.cancel()
                    if (!isSelecting) {
                        restoreDisplayFromSelection()
                    }
                    pendingBlurClose = coroutineScope.launch {
                        delay(if (dropdownExpanded) 400 else 150)
                        if (!isSelecting) {
                            finishBlurClose()
                        }
                    }
                }
            }

        if (showFloatingLabel) {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    searchText = newValue
                    dropdownExpanded = true
                    isUserEditing = true
                },
                label = { Text(label, style = labelTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text(CustomerSearchFilter.PLACEHOLDER, style = inputTextStyle) },
                textStyle = inputTextStyle,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxSize()
                    .then(focusModifier),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = label, modifier = Modifier.size(22.dp))
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "展开客户列表",
                        modifier = Modifier.clickable {
                            if (dropdownExpanded) {
                                closeDropdown()
                            } else {
                                openDropdownForSearch(
                                    requestFocus = true,
                                    clearForSearch = selectedCustomer != null
                                )
                            }
                        }
                    )
                },
                isError = isError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            )
        } else {
            val borderColor = when {
                isError -> MaterialTheme.colorScheme.error
                dropdownExpanded -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            val centeredStyle = inputTextStyle.merge(
                TextStyle(color = MaterialTheme.colorScheme.onSurface)
            )
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    searchText = newValue
                    dropdownExpanded = true
                    isUserEditing = true
                },
                singleLine = true,
                textStyle = centeredStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxSize()
                    .then(focusModifier),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, borderColor, MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (fieldValue.isEmpty()) {
                                Text(
                                    text = CustomerSearchFilter.PLACEHOLDER,
                                    style = centeredStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "展开客户列表",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    if (dropdownExpanded) {
                                        closeDropdown()
                                    } else {
                                        openDropdownForSearch(
                                            requestFocus = true,
                                            clearForSearch = selectedCustomer != null
                                        )
                                    }
                                }
                        )
                    }
                }
            )
        }

        if (dropdownExpanded && fieldWidthPx > 0) {
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) { IntOffset(0, fieldHeight.roundToPx()) },
                onDismissRequest = {
                    if (!isSelecting) closeDropdown()
                },
                // 不抢焦点，便于继续输入筛选；点击项用 pointer 处理
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Card(
                    modifier = Modifier
                        .width(with(density) { fieldWidthPx.toDp() })
                        .heightIn(max = AppDimens.dialogListMaxHeight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    if (filteredCustomers.isEmpty()) {
                        Text(
                            text = if (searchText.isBlank()) "暂无客户数据" else "未找到相关客户",
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn {
                            items(filteredCustomers, key = { it.id }) { customer ->
                                ListItem(
                                    headlineContent = {
                                        Text(customer.customerName ?: "未知客户")
                                    },
                                    supportingContent = {
                                        Text("编号: ${customer.customerNo}")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(customer.id) {
                                            detectTapGestures(onTap = { selectCustomer(customer) })
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
}

/**
 * 可搜索客户选择框（可选）：支持编号、名称、拼音首字母实时过滤；未选择时表示「所有客户」。
 */
@Composable
fun SearchableOptionalCustomerField(
    customers: List<Customer>,
    selectedCustomer: Customer?,
    onCustomerSelected: (Customer?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "选择客户",
    allCustomersLabel: String = "所有客户",
    fieldHeight: Dp = AppDimens.searchFieldHeight
) {
    var searchText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isUserEditing by remember { mutableStateOf(false) }
    var isSelecting by remember { mutableStateOf(false) }
    var pendingBlurClose by remember { mutableStateOf<Job?>(null) }
    var fieldWidthPx by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val hideKeyboard = rememberHideKeyboardOnly()
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current

    fun beginSearchInput() {
        dropdownExpanded = true
        isUserEditing = true
        searchText = ""
    }

    fun finishBlurClose() {
        if (isSelecting) return
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun openDropdownForSearch(requestFocus: Boolean = false, clearForSearch: Boolean = false) {
        dropdownExpanded = true
        if (clearForSearch) {
            beginSearchInput()
        } else {
            isUserEditing = false
            if (selectedCustomer != null) {
                searchText = CustomerSearchFilter.displayName(selectedCustomer)
            }
        }
        if (requestFocus) {
            coroutineScope.launch {
                focusRequester.requestFocus()
            }
        }
    }

    fun restoreDisplayFromSelection() {
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun closeDropdown() {
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun selectAllCustomers() {
        isSelecting = true
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        searchText = ""
        isUserEditing = false
        onCustomerSelected(null)
        hideKeyboard()
        coroutineScope.launch {
            delay(200)
            isSelecting = false
        }
    }

    fun selectCustomer(customer: Customer) {
        isSelecting = true
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        searchText = CustomerSearchFilter.displayName(customer)
        isUserEditing = false
        onCustomerSelected(customer)
        hideKeyboard()
        coroutineScope.launch {
            delay(200)
            isSelecting = false
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release && selectedCustomer != null) {
                if (!dropdownExpanded) {
                    openDropdownForSearch(requestFocus = true, clearForSearch = true)
                } else if (searchText == CustomerSearchFilter.displayName(selectedCustomer)) {
                    beginSearchInput()
                }
            }
        }
    }

    LaunchedEffect(selectedCustomer?.id) {
        if (!dropdownExpanded && !isUserEditing) {
            searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: ""
        }
    }

    val fieldPlaceholder = if (selectedCustomer == null && searchText.isEmpty()) {
        allCustomersLabel
    } else {
        CustomerSearchFilter.PLACEHOLDER
    }

    val filterKeyword = when {
        !dropdownExpanded -> ""
        isUserEditing -> searchText
        selectedCustomer != null &&
            searchText == CustomerSearchFilter.displayName(selectedCustomer) -> ""
        else -> searchText
    }

    val filteredCustomers = remember(filterKeyword, customers, dropdownExpanded) {
        CustomerSearchFilter.filter(customers, filterKeyword)
    }

    val fieldValue = when {
        isUserEditing || dropdownExpanded -> searchText
        selectedCustomer != null -> CustomerSearchFilter.displayName(selectedCustomer)
        else -> searchText
    }

    Box(
        modifier = modifier
            .height(fieldHeight)
            .onGloballyPositioned { fieldWidthPx = it.size.width }
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                searchText = newValue
                dropdownExpanded = true
                isUserEditing = true
            },
            label = { Text(label) },
            placeholder = { Text(fieldPlaceholder) },
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        pendingBlurClose?.cancel()
                        openDropdownForSearch()
                    } else {
                        pendingBlurClose?.cancel()
                        if (!isSelecting) {
                            restoreDisplayFromSelection()
                        }
                        pendingBlurClose = coroutineScope.launch {
                            delay(if (dropdownExpanded) 400 else 150)
                            if (!isSelecting) {
                                finishBlurClose()
                            }
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
                    contentDescription = "展开客户列表",
                    modifier = Modifier.clickable {
                        if (dropdownExpanded) {
                            closeDropdown()
                        } else {
                            openDropdownForSearch(
                                requestFocus = true,
                                clearForSearch = selectedCustomer != null
                            )
                        }
                    }
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        if (dropdownExpanded && fieldWidthPx > 0) {
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) { IntOffset(0, fieldHeight.roundToPx()) },
                onDismissRequest = {
                    if (!isSelecting) closeDropdown()
                },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Card(
                    modifier = Modifier
                        .width(with(density) { fieldWidthPx.toDp() })
                        .heightIn(max = AppDimens.dialogListMaxHeight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text(allCustomersLabel) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput("all_customers") {
                                        detectTapGestures(onTap = { selectAllCustomers() })
                                    }
                            )
                            HorizontalDivider()
                        }

                        if (filteredCustomers.isEmpty()) {
                            item {
                                Text(
                                    text = if (searchText.isBlank()) {
                                        "暂无客户数据"
                                    } else {
                                        "未找到相关客户"
                                    },
                                    modifier = Modifier.padding(AppDimens.pagePadding),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(filteredCustomers, key = { it.id }) { customer ->
                                ListItem(
                                    headlineContent = {
                                        Text(customer.customerName ?: "未知客户")
                                    },
                                    supportingContent = {
                                        Text("编号: ${customer.customerNo}")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(customer.id) {
                                            detectTapGestures(onTap = { selectCustomer(customer) })
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
}
