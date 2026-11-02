package br.app.stagebook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R

// "12 songs" / "12 setlists"; zero says "No songs" (Portuguese plurals put 0 in "one": "0 música", device test B-08)
@Composable
fun songCountText(count: Int): String =
    if (count == 0) stringResource(R.string.song_count_none) else pluralStringResource(R.plurals.song_count, count, count)

@Composable
fun setCountText(count: Int): String =
    if (count == 0) stringResource(R.string.set_count_none) else pluralStringResource(R.plurals.set_count, count, count)
