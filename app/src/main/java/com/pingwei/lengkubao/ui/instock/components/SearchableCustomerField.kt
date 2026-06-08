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
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.utils.CustomerSearchFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 可搜索客户选择框：支持编号、名称、拼音首字母实时过滤。
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
        if (selectedCustomer != null) {
            searchText = ""
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                val customer = selectedCustomer ?: return@collect
                if (searchText == CustomerSearchFilter.displayName(customer)) {
                    beginSearchInput()
                }
            }
        }
    }

    fun finishBlurClose() {
        if (isSelecting) return
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    LaunchedEffect(selectedCustomer) {
        if (!dropdownExpanded && !isUserEditing) {
            searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: ""
        }
    }

    val filteredCustomers = remember(searchText, customers, dropdownExpanded) {
        val keyword = if (dropdownExpanded) searchText else ""
        CustomerSearchFilter.filter(customers, keyword)
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
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun selectCustomer(customer: Customer) {
        isSelecting = true
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        searchText = CustomerSearchFilter.displayName(customer)
        onCustomerSelected(customer)
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
            label = { Text(label, style = labelTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text(CustomerSearchFilter.PLACEHOLDER, style = inputTextStyle) },
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
                    contentDescription = "展开客户列表",
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
                focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
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
                if (filteredCustomers.isEmpty()) {
                    Text(
                        text = if (searchText.isBlank()) "暂无客户数据" else "未找到相关客户",
                        modifier = Modifier.padding(AppDimens.pagePadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = AppDimens.dialogListMaxHeight)
                    ) {
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
                                    .clickable { selectCustomer(customer) }
                            )
                            HorizontalDivider()
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

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    fun beginSearchInput() {
        dropdownExpanded = true
        isUserEditing = true
        if (selectedCustomer != null) {
            searchText = ""
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                val customer = selectedCustomer ?: return@collect
                if (searchText == CustomerSearchFilter.displayName(customer)) {
                    beginSearchInput()
                }
            }
        }
    }

    fun finishBlurClose() {
        if (isSelecting) return
        dropdownExpanded = false
        isUserEditing = false
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    LaunchedEffect(selectedCustomer) {
        if (!dropdownExpanded && !isUserEditing) {
            searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: ""
        }
    }

    val fieldPlaceholder = if (selectedCustomer == null && searchText.isEmpty()) {
        allCustomersLabel
    } else {
        CustomerSearchFilter.PLACEHOLDER
    }

    val filteredCustomers = remember(searchText, customers, dropdownExpanded) {
        val keyword = if (dropdownExpanded) searchText else ""
        CustomerSearchFilter.filter(customers, keyword)
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
        searchText = selectedCustomer?.let { CustomerSearchFilter.displayName(it) } ?: searchText
    }

    fun selectAllCustomers() {
        isSelecting = true
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        searchText = ""
        onCustomerSelected(null)
        isUserEditing = false
        isSelecting = false
    }

    fun selectCustomer(customer: Customer) {
        isSelecting = true
        pendingBlurClose?.cancel()
        dropdownExpanded = false
        searchText = CustomerSearchFilter.displayName(customer)
        onCustomerSelected(customer)
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
            label = { Text(label) },
            placeholder = { Text(fieldPlaceholder) },
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
                    contentDescription = "展开客户列表",
                    modifier = Modifier.clickable {
                        if (dropdownExpanded) {
                            closeDropdown()
                        } else {
                            openDropdownForSearch(requestFocus = true)
                        }
                    }
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                LazyColumn(
                    modifier = Modifier.heightIn(max = AppDimens.dialogListMaxHeight)
                ) {
                    item {
                        ListItem(
                            headlineContent = { Text(allCustomersLabel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectAllCustomers() }
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
                                    .clickable { selectCustomer(customer) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
