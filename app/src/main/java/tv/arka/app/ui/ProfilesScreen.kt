package tv.arka.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.arka.app.R
import tv.arka.app.data.Profile
import tv.arka.app.data.Repo

/**
 * "Qui regarde ?" — local profiles, no account. Each profile has its own favourites, list, history
 * and search history; the sources and their catalogue are shared.
 */
@Composable
fun ProfilesScreen(onDone: () -> Unit) {
    val tv = LocalIsTv.current
    val tile = if (tv) 70.dp else 108.dp
    var managing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    val profiles = Repo.profiles

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(ArkaTheme.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (tv) 24.dp else 40.dp))
        Logo(wordmark = tv, height = if (tv) 18.dp else 28.dp)
        Text(
            if (managing) "GÉRER LES PROFILS" else "QUI REGARDE ?",
            fontFamily = ArkaDisplayFont,
            fontWeight = FontWeight(800),
            fontSize = if (tv) 26.sp else 30.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = ArkaSpacing.md, bottom = ArkaSpacing.xl),
        )

        // Two tiles per row on handsets, one row on TV.
        val perRow = if (tv) Profile.MAX + 1 else 2
        val cells: List<Profile?> = if (profiles.size < Profile.MAX) profiles + listOf<Profile?>(null) else profiles
        cells.chunked(perRow).forEach { row ->
            Row(
                Modifier.padding(bottom = ArkaSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(if (tv) ArkaSpacing.lg else ArkaSpacing.xxl),
            ) {
                row.forEach { profile ->
                    Column(Modifier.width(tile + 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (profile != null) {
                            ProfileAvatar(
                                profile,
                                size = tile,
                                radius = ArkaRadius.lg,
                                modifier = Modifier.focusHighlight(ArkaRadius.lg).clickable {
                                    if (managing) {
                                        editing = profile
                                    } else {
                                        Repo.switchProfile(profile)
                                        onDone()
                                    }
                                },
                            )
                            Text(
                                profile.name,
                                style = ArkaTheme.type.label,
                                fontWeight = if (profile.id == Repo.activeProfile.id) FontWeight(700) else FontWeight(500),
                                color = if (profile.id == Repo.activeProfile.id) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = ArkaSpacing.sm),
                            )
                        } else {
                            val shape = RoundedCornerShape(ArkaRadius.lg)
                            Box(
                                Modifier.size(tile).focusHighlight(ArkaRadius.lg).clip(shape)
                                    .border(1.dp, ArkaColors.Border, shape)
                                    .clickable { adding = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                ArkaIcon(R.drawable.ic_arka_ajouter, "Ajouter un profil", tint = ArkaColors.TextSecondary, size = if (tv) 22.dp else 30.dp)
                            }
                            Text(
                                "Ajouter",
                                color = ArkaColors.TextSecondary,
                                style = ArkaTheme.type.label,
                                modifier = Modifier.padding(top = ArkaSpacing.sm),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(ArkaSpacing.lg))
        SecondaryButton(if (managing) "Terminé" else "Gérer les profils", { managing = !managing })
        if (managing) {
            Text(
                "Touchez un profil pour le renommer ou le supprimer.",
                color = ArkaColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = ArkaSpacing.md),
            )
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = ArkaColors.SurfaceHigh,
            title = { Text("Nouveau profil") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Prénom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    Repo.addProfile(name)
                    adding = false
                }) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Annuler") } },
        )
    }

    editing?.let { profile ->
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        var confirmDelete by remember(profile.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = ArkaColors.SurfaceHigh,
            title = { Text(if (confirmDelete) "Supprimer « ${profile.name} » ?" else "Modifier le profil") },
            text = {
                Column {
                    if (confirmDelete) {
                        Text("Ses favoris, sa liste et son historique seront effacés. Les sources ne sont pas touchées.")
                    } else {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(20) },
                            label = { Text("Prénom") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (profile.isMain) {
                            Text(
                                "Le profil principal ne peut pas être supprimé.",
                                color = ArkaColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = ArkaSpacing.sm),
                            )
                        } else {
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("Supprimer ce profil", color = ArkaColors.RedBright)
                            }
                        }
                    }
                }
            },
            // A destructive dialog opens with the focus on "Annuler", never on the deletion.
            dismissButton = {
                TextButton(onClick = {
                    if (confirmDelete) confirmDelete = false else editing = null
                }) { Text("Annuler") }
            },
            confirmButton = {
                if (confirmDelete) {
                    TextButton(onClick = {
                        Repo.deleteProfile(profile)
                        editing = null
                    }) { Text("Supprimer", color = ArkaColors.RedBright) }
                } else {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        Repo.renameProfile(profile, name)
                        editing = null
                    }) { Text("Enregistrer") }
                }
            },
        )
    }
}
