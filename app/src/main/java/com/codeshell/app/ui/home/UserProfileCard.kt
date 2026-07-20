package com.codeshell.app.ui.home



import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.codeshell.app.R

import com.codeshell.app.data.model.UserProfile

import com.codeshell.app.ui.chat.ChatThemeColors

import com.codeshell.app.ui.components.AvatarInitials

import com.codeshell.app.util.UsernameFormatter



@Composable

fun UserProfileCard(

    user: UserProfile,

    onClick: () -> Unit,

    colors: ChatThemeColors,

    modifier: Modifier = Modifier

) {

    Card(

        modifier = modifier

            .fillMaxWidth()

            .clickable(onClick = onClick),

        colors = CardDefaults.cardColors(containerColor = colors.bubbleOther)

    ) {

        Row(

            modifier = Modifier

                .fillMaxWidth()

                .padding(16.dp),

            verticalAlignment = Alignment.CenterVertically

        ) {

            AvatarInitials(username = user.username)

            Column(modifier = Modifier.padding(start = 16.dp)) {

                Text(

                    text = UsernameFormatter.display(user.username),

                    style = MaterialTheme.typography.titleMedium,

                    color = colors.text

                )

                Text(

                    text = stringResource(R.string.tap_open_session),

                    style = MaterialTheme.typography.bodySmall,

                    color = colors.mutedText

                )

            }

        }

    }

}


