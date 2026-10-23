package com.monsivamon.golender.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.data.holiday.HolidayCountry
import com.monsivamon.golender.ui.SECTION_LABEL_BOTTOM
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel

// 祝日表示の対象国を複数選択する独立セクション
@Composable
fun HolidaySettingsSection(viewModel: CalendarViewModel, colors: AppColors) {
    val selected by viewModel.holidayCountries.collectAsState()
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    var query by remember { mutableStateOf("") }

    val selectedSet = selected.toSet()
    val filtered = HolidayCountry.entries.filter { country ->
        query.isBlank() ||
            country.displayName(locale).contains(query, ignoreCase = true) ||
            country.iso.contains(query, ignoreCase = true)
    }

    SettingsSection(stringResource(R.string.sc_holidaySection), colors) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.sc_holidayCountrySearch), fontSize = 14.sp)
            },
            // ダークテーマでも背景と同化しないよう、文字・枠・カーソル色を明示する
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                focusedBorderColor = colors.primaryAccent,
                unfocusedBorderColor = colors.divider,
                cursorColor = colors.primaryAccent,
                focusedPlaceholderColor = colors.textGray,
                unfocusedPlaceholderColor = colors.textGray,
            ),
        )
        Text(
            text = stringResource(R.string.sc_holidaySelectedCount, selected.size),
            fontSize = 13.sp,
            color = colors.textGray,
            modifier = Modifier.padding(top = 8.dp, bottom = SECTION_LABEL_BOTTOM),
        )
        filtered.forEach { country ->
            val checked = country in selectedSet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val updated = if (checked) {
                            selected - country
                        } else {
                            (selected + country).sortedBy { it.ordinal }
                        }
                        viewModel.setHolidayCountries(updated)
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.primaryAccent,
                        uncheckedColor = colors.textGray,
                    ),
                )
                Text(country.flag, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(country.displayName(locale), fontSize = 16.sp, color = colors.text)
                Spacer(modifier = Modifier.width(8.dp))
                Text(country.iso, fontSize = 12.sp, color = colors.textGray)
            }
        }
        if (selected.isEmpty()) {
            Text(
                text = stringResource(R.string.sc_holidayEmptyHint),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textGray,
                modifier = Modifier.padding(top = SECTION_LABEL_BOTTOM),
            )
        }
    }
}
