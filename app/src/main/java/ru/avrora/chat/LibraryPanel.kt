package ru.avrora.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPanel(
    stickers: List<CatalogItem>,
    photos: List<CatalogItem>,
    onSticker: (CatalogItem) -> Unit,
    onPhoto: (CatalogItem) -> Unit,
    onAddSticker: () -> Unit,
    onAddPhoto: () -> Unit,
    onDelete: (CatalogItem) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val isStickers = tab == 0
    val items = if (isStickers) stickers else photos

    Column(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Panel)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TabLabel("Стикеры", isStickers, Modifier.weight(1f)) { tab = 0 }
            TabLabel("Альбом", !isStickers, Modifier.weight(1f)) { tab = 1 }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isStickers) 92.dp else 112.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Glow.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { if (isStickers) onAddSticker() else onAddPhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить", tint = Glow)
                }
            }
            items(items, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .combinedClickable(
                            onClick = { if (isStickers) onSticker(item) else onPhoto(item) },
                            onLongClick = { if (!item.builtIn) onDelete(item) }
                        )
                ) {
                    AsyncImage(
                        model = item.model,
                        contentDescription = null,
                        contentScale = if (isStickers) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isStickers) 6.dp else 0.dp)
                    )
                }
            }
        }
        Text(
            "Удержание на своём — удалить",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = if (selected) TextMain else TextDim, fontSize = 15.sp)
        Box(
            Modifier
                .padding(top = 8.dp)
                .height(2.dp)
                .fillMaxWidth()
                .background(if (selected) Glow else Color.Transparent)
        )
    }
}
