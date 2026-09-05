package com.pingwei.lengkubao.ui.common

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberDismissKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current

    return remember(focusManager, keyboardController, view, context) {
        {
            val anchor = resolveImeAnchorView(view, context)
            hideIme(context, anchor)
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            scheduleImeRetries(view, context)
        }
    }
}

@Composable
fun rememberRunWithKeyboardDismiss(): ((() -> Unit) -> Unit) {
    val dismiss = rememberDismissKeyboard()
    return remember(dismiss) {
        { action ->
            dismiss()
            action()
        }
    }
}

/** 仅隐藏软键盘，不 clearFocus，适用于搜索框选中后收键盘。 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberHideKeyboardOnly(): () -> Unit {
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current

    return remember(keyboardController, view, context) {
        {
            hideIme(context, resolveImeAnchorView(view, context))
            keyboardController?.hide()
        }
    }
}

private fun resolveImeAnchorView(view: View, context: Context): View {
    return view.findFocus()
        ?: (context as? Activity)?.currentFocus
        ?: view
}

private fun hideIme(context: Context, anchorView: View?) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    val token = anchorView?.windowToken ?: return
    imm.hideSoftInputFromWindow(token, InputMethodManager.HIDE_IMPLICIT_ONLY)
    imm.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
}

private fun scheduleImeRetries(view: View, context: Context) {
    view.post {
        hideIme(context, resolveImeAnchorView(view, context))
    }
    view.postDelayed({
        hideIme(context, resolveImeAnchorView(view, context))
    }, 100)
    view.postDelayed({
        hideIme(context, resolveImeAnchorView(view, context))
    }, 200)
}
