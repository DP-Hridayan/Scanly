@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.actions.ScanActionDetector
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.viewmodel.AiScanViewModel
import com.skeler.scanely.ui.viewmodel.OcrViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Results Screen
 * - Selectable Text Container
 * - Copied to Clipboard toast
 * - Source image quick view + full screen expand
 * - AI Results with Translate button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val aiViewModel: AiScanViewModel = hiltViewModel(activity)
    val ocrViewModel: OcrViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val scanState by scanViewModel.uiState.collectAsState()
    val aiState by aiViewModel.aiState.collectAsState()
    val ocrState by ocrViewModel.uiState.collectAsState()
    
    val imageUri = scanState.selectedImageUri
    val isProcessing = scanState.isProcessing || aiState.isProcessing || ocrState.isProcessing
    val pdfThumbnail = scanState.pdfThumbnail
    val progressMessage = scanState.progressMessage
    
    // History text (restored from history - highest priority, no re-extraction)
    val historyText = scanState.historyText

    // AI result text
    val aiResultText = when (val result = aiState.result) {
        is AiResult.Success -> result.text
        is AiResult.Error -> "Error: ${result.message}"
        is AiResult.RateLimited -> "Rate limited. Wait ${result.remainingMs / 1000}s"
        null -> null
    }
    
    // OCR result text (on-device ML Kit) - FREE, UNLIMITED
    val ocrResultText = when (val result = ocrState.result) {
        is OcrResult.Success -> result.text
        is OcrResult.Error -> null
        is OcrResult.Empty -> null
        null -> null
    }
    
    // Priority: History > AI > OCR
    val primaryResultText = historyText ?: aiResultText ?: ocrResultText
    
    // Track if result is from AI (only AI results can be translated)
    val isAiResult = aiResultText != null
    
    // Display translated text if available, otherwise original
    val displayText = aiState.translatedText ?: primaryResultText
    val hasTranslation = aiState.translatedText != null
    val isTranslating = aiState.isTranslating

    // Network & Rate Limit State
    val isOnline by scanViewModel.isOnline.collectAsState()
    val rateLimitState by scanViewModel.rateLimitState.collectAsState()
    val showRateLimitSheet by scanViewModel.showRateLimitSheet.collectAsState()
    val cooldownSeconds = rateLimitState.remainingSeconds

    var showContent by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }
    var navigatingUp by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    val topBarScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    var lastScrollOffset by remember { mutableIntStateOf(0) }

    val isFabExpanded by remember {
        derivedStateOf {
            listState.shouldFabExpand(lastScrollOffset) {
                lastScrollOffset = it
            }
        }
    }

    // Translation languages
    val languages = listOf(
        "English", "Spanish", "French", "German", "Italian", 
        "Portuguese", "Russian", "Chinese", "Japanese", "Korean",
        "Arabic", "Hindi", "Turkish", "Dutch", "Polish"
    )

    val onBack: () -> Unit = {
        if (!navigatingUp) {
            navigatingUp = true
            navController.popBackStack(Routes.HOME, inclusive = false)
            scope.launch(Dispatchers.Default) {
                delay(600)
                scanViewModel.clearState()
                aiViewModel.clearResult()
                ocrViewModel.clearResult()
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    BackHandler { onBack() }

    if (showFullImage) {
        FullImageDialog(
            imageUri = imageUri,
            pdfThumbnail = pdfThumbnail,
            onDismiss = { showFullImage = false }
        )
    }

    // Rate Limit Sheet Modal
    if (showRateLimitSheet) {
        RateLimitSheet(
            remainingSeconds = cooldownSeconds,
            onDismiss = { scanViewModel.dismissRateLimitSheet() }
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                scrollBehavior = topBarScrollBehavior,
                title = {
                    val collapsedFraction = topBarScrollBehavior.state.collapsedFraction
                    val expandedFontSize = 33.sp
                    val collapsedFontSize = 20.sp

                    val fontSize = lerp(expandedFontSize, collapsedFontSize, collapsedFraction)
                    Text(
                        modifier = Modifier.basicMarquee(),
                        text = "Results",
                        maxLines = 1,
                        fontSize = fontSize,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.05.em
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (displayText != null && !isProcessing && !isTranslating) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val clipboardManager = context.getSystemService(android.content.ClipboardManager::class.java)
                        val clipData = ClipData.newPlainText("AI Result", displayText)
                        clipboardManager.setPrimaryClip(clipData)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null
                        )
                    },
                    text = { Text("Copy") }
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Image Preview Card
            item {
                AnimatedVisibility(
                    visible = showContent && (imageUri != null || pdfThumbnail != null),
                    enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -it / 4 })
                ) {
                    ImagePreviewCard(
                        imageUri = imageUri,
                        pdfThumbnail = pdfThumbnail,
                        onClick = { showFullImage = true }
                    )
                }
            }

            // Results Card with AI/OCR Content
            item {
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(400, delayMillis = 150)) + slideInVertically(initialOffsetY = { it / 4 })
                ) {
                    // Determine the processing message
                    val processingMessage = when {
                        aiState.isProcessing -> "AI analyzing image..."
                        ocrState.isProcessing -> if (ocrState.isPdf) "Extracting PDF text..." else "Extracting text..."
                        else -> progressMessage
                    }
                    
                    AiResultsCard(
                        isProcessing = isProcessing,
                        isTranslating = isTranslating,
                        progressMessage = processingMessage,
                        resultText = displayText,
                        hasTranslation = hasTranslation,
                        canTranslate = isAiResult && isOnline, // Only AI results + online
                        onTranslateClick = { showLanguageMenu = true },
                        onRevertClick = { aiViewModel.clearTranslation() },
                        showLanguageMenu = showLanguageMenu,
                        onLanguageMenuDismiss = { showLanguageMenu = false },
                        languages = languages,
                        onLanguageSelected = { language ->
                            showLanguageMenu = false
                            // Rate limit check before translation
                            scanViewModel.triggerAiWithRateLimit {
                                aiViewModel.translateResult(language)
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AiResultsCard(
    isProcessing: Boolean,
    isTranslating: Boolean,
    progressMessage: String,
    resultText: String?,
    hasTranslation: Boolean,
    canTranslate: Boolean, // true = AI result + online
    onTranslateClick: () -> Unit,
    onRevertClick: () -> Unit,
    showLanguageMenu: Boolean,
    onLanguageMenuDismiss: () -> Unit,
    languages: List<String>,
    onLanguageSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header with Translate/Revert buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasTranslation) "Translated Text" else "Result",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (resultText != null && !isProcessing && !isTranslating) {
                    Row {
                        if (hasTranslation) {
                            // Revert to original button
                            OutlinedButton(
                                onClick = onRevertClick,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Undo,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Original", style = MaterialTheme.typography.labelMedium)
                            }
                        } else if (canTranslate) {
                            // Translate button - only for AI results when online
                            Box {
                                FilledTonalButton(
                                    onClick = onTranslateClick,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Translate",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                DropdownMenu(
                                    expanded = showLanguageMenu,
                                    onDismissRequest = onLanguageMenuDismiss
                                ) {
                                    languages.forEach { language ->
                                        DropdownMenuItem(
                                            text = { Text(language) },
                                            onClick = { onLanguageSelected(language) }
                                        )
                                    }
                                }
                            }
                        }
                        // else: offline - button is completely hidden
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isProcessing -> {
                    ProcessingContent(progressMessage)
                }
                isTranslating -> {
                    TranslatingContent()
                }
                resultText != null -> {
                    AiTextContent(text = resultText)
                }
                else -> {
                    EmptyResultContent()
                }
            }
        }
    }
}

@Composable
private fun AiTextContent(text: String) {
    val customSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides customSelectionColors) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    textDirection = TextDirection.Content
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TranslatingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Translating...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImagePreviewCard(
    imageUri: Uri?,
    pdfThumbnail: Bitmap? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                pdfThumbnail != null -> {
                    Image(
                        bitmap = pdfThumbnail.asImageBitmap(),
                        contentDescription = "PDF preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop,
                        alpha = 0.9f
                    )
                }
                imageUri != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Captured image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop,
                        alpha = 0.9f
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            ) {
                Text(
                    text = "Tap to expand",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FullImageDialog(
    imageUri: Uri?,
    pdfThumbnail: Bitmap?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
        ) {
            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    pdfThumbnail != null -> {
                        Image(
                            bitmap = pdfThumbnail.asImageBitmap(),
                            contentDescription = "Full PDF preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    imageUri != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUri)
                                .build(),
                            contentDescription = "Full captured image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingContent(progressMessage: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularWavyProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = progressMessage.ifEmpty { "Processing..." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This may take a few moments",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun EmptyResultContent() {
    Text(
        text = "No AI result available.\nSelect an image from the home screen.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}

private fun LazyListState.shouldFabExpand(
    lastScrollOffset: Int,
    onScrollOffsetChanged: (Int) -> Unit
): Boolean {
    val currentOffset = firstVisibleItemIndex * 1000 + firstVisibleItemScrollOffset
    val isScrollingUp = currentOffset < lastScrollOffset
    val isScrollingDown = currentOffset > lastScrollOffset
    onScrollOffsetChanged(currentOffset)

    val atTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val lastVisible = visible.lastOrNull()

    val fitsOnScreen = if (info.totalItemsCount == 0 || lastVisible == null) {
        true
    } else {
        val allItemsVisible = info.totalItemsCount == visible.size
        val lastBottom = lastVisible.offset + lastVisible.size
        allItemsVisible && lastBottom <= info.viewportEndOffset
    }

    return when {
        atTop -> true
        fitsOnScreen -> true
        isScrollingUp -> true
        isScrollingDown -> false
        else -> false
    }
}