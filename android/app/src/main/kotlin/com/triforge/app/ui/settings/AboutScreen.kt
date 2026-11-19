package com.triforge.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.triforge.app.BuildConfig
import com.triforge.app.R
import com.triforge.app.ui.common.CardHeader
import com.triforge.app.ui.common.CollapsibleCard
import com.triforge.app.ui.common.KeyValueRow
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.common.TriCard
import com.triforge.app.ui.theme.TriColors

/** Version, what TriForge is not, and the software and marks it relies on. */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.about_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TriCard {
                KeyValueRow(stringResource(R.string.about_version_label), BuildConfig.VERSION_NAME)
                KeyValueRow(stringResource(R.string.about_build_label), BuildConfig.VERSION_CODE.toString())
            }
            TriCard {
                CardHeader(title = stringResource(R.string.about_medical_title))
                Text(
                    text = stringResource(R.string.about_medical_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TriColors.textDim,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            CollapsibleCard(title = stringResource(R.string.about_licenses_title)) {
                Text(
                    text = stringResource(R.string.about_licenses_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TriColors.textDim,
                )
            }
            CollapsibleCard(title = stringResource(R.string.about_marks_title)) {
                Text(
                    text = stringResource(R.string.about_marks_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TriColors.textDim,
                )
            }
        }
    }
}
