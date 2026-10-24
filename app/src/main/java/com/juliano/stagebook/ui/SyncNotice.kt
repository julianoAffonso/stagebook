package com.juliano.stagebook.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.juliano.stagebook.R
import com.juliano.stagebook.data.SyncNotice
import com.juliano.stagebook.ui.components.InfoDialog

/** Aviso, na entrada no app, do que a sincronia com o Drive mudou. */
@Composable
fun SyncNoticeDialog(result: SyncNotice, onDismiss: () -> Unit, onDetails: () -> Unit) {
    val lines = buildList {
        if (result.added > 0) add(pluralStringResource(R.plurals.sync_added, result.added, result.added))
        if (result.updated > 0) add(pluralStringResource(R.plurals.sync_updated, result.updated, result.updated))
        if (result.sections > 0) add(pluralStringResource(R.plurals.sync_sections, result.sections, result.sections))
        if (result.meta > 0) add(pluralStringResource(R.plurals.sync_meta, result.meta, result.meta))
        if (result.mine > 0) add(pluralStringResource(R.plurals.sync_protected, result.mine, result.mine))
        add(stringResource(R.string.sync_kept))
    }
    InfoDialog(stringResource(R.string.sync_title), lines.joinToString("\n"), onDismiss, stringResource(R.string.sync_see_details) to onDetails)
}
