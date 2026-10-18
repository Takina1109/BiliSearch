package com.takina.bilisearch.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.ui.components.LoadingBox
import com.takina.bilisearch.ui.components.TopBar

/**
 * 编辑资料：昵称、性别、自我介绍。
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    vm: EditProfileViewModel = viewModel()
) {
    val state = vm.state

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "编辑资料", onBack = onBack)

        if (state.loading) {
            LoadingBox(Modifier.weight(1f))
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            FieldLabel("名称")
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onNameChange,
                singleLine = true,
                placeholder = { Text("名称") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))

            FieldLabel("性别")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SexOption("男", state.sex == 1) { vm.onSexChange(1) }
                SexOption("女", state.sex == 2) { vm.onSexChange(2) }
                SexOption("保密", state.sex == 0) { vm.onSexChange(0) }
            }

            Spacer(Modifier.height(18.dp))

            FieldLabel("自我介绍")
            OutlinedTextField(
                value = state.sign,
                onValueChange = vm::onSignChange,
                placeholder = { Text("自我介绍") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = vm::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.saving) "保存中…" else "保存")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SexOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
