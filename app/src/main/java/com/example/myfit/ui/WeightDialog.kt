package com.example.myfit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myfit.R
import com.example.myfit.viewmodel.MainViewModel
import com.example.myfit.ui.components.GlassButton

// Shared native tool fallback; independent of the archived daily workout page.
@Composable
fun WeightDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val profile by viewModel.userProfile.collectAsState()
    val needFullInfo = remember(profile) { profile.height == 0f || profile.age == 0 || profile.age < 22 }
    var weightInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var heightInput by remember { mutableStateOf(if (profile.height > 0) profile.height.toString() else "") }
    var selectedGender by remember { mutableStateOf(profile.gender) }
    var bodyFatInput by remember { mutableStateOf("") }; var muscleInput by remember { mutableStateOf("") }; var waterInput by remember { mutableStateOf("") }; var waistInput by remember { mutableStateOf("") }; var hipInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (needFullInfo) R.string.dialog_profile_title else R.string.dialog_weight_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = weightInput, onValueChange = { weightInput = it }, label = { Text(stringResource(R.string.label_weight_kg)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))
                if (needFullInfo) {
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = ageInput, onValueChange = { ageInput = it }, label = { Text(stringResource(R.string.hint_input_age)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = heightInput, onValueChange = { heightInput = it }, label = { Text(stringResource(R.string.hint_input_height)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.label_gender) + ": ")
                        FilterChip(selected = selectedGender == 0, onClick = { selectedGender = 0 }, label = { Text(stringResource(R.string.gender_male)) })
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(selected = selectedGender == 1, onClick = { selectedGender = 1 }, label = { Text(stringResource(R.string.gender_female)) })
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.weekly_metrics_subtitle), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bodyFatInput, onValueChange = { bodyFatInput = it }, label = { Text(stringResource(R.string.label_body_fat_kg)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = muscleInput, onValueChange = { muscleInput = it }, label = { Text(stringResource(R.string.label_skeletal_muscle_kg)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
                OutlinedTextField(value = waterInput, onValueChange = { waterInput = it }, label = { Text(stringResource(R.string.label_body_water)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = waistInput, onValueChange = { waistInput = it }, label = { Text(stringResource(R.string.label_waist_circumference)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = hipInput, onValueChange = { hipInput = it }, label = { Text(stringResource(R.string.label_hip_circumference)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
            }
        },
        confirmButton = { 
            GlassButton(text = stringResource(R.string.btn_save)) {
                weightInput.toFloatOrNull()?.let { 
                    viewModel.logWeightAndProfile(it, ageInput.toIntOrNull(), heightInput.toFloatOrNull(), selectedGender, bodyFatInput.toFloatOrNull(), muscleInput.toFloatOrNull(), waterInput.toFloatOrNull(), waistInput.toFloatOrNull(), hipInput.toFloatOrNull())
                    onDismiss()
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

