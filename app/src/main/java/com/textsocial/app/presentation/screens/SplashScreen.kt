package com.textsocial.app.presentation.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textsocial.app.R
import com.textsocial.app.presentation.viewmodel.SplashViewModel
import com.textsocial.app.ui.theme.BrandBlue
import com.textsocial.app.ui.theme.BrandCyanSoft
import com.textsocial.app.ui.theme.BrandVioletLight
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()

    LaunchedEffect(isUserLoggedIn) {
        if (isUserLoggedIn != null) {
            delay(900)
            if (isUserLoggedIn == true) {
                onNavigateToHome()
            } else {
                onNavigateToLogin()
            }
        }
    }

    var logoVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }
    var loaderVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(250)
        titleVisible = true
        delay(200)
        taglineVisible = true
        delay(250)
        loaderVisible = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "logo_alpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "title_alpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (titleVisible) 0f else 14f,
        animationSpec = tween(durationMillis = 450),
        label = "title_offset"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (taglineVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "tagline_alpha"
    )
    val loaderAlpha by animateFloatAsState(
        targetValue = if (loaderVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "loader_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.splash_logo),
                        contentDescription = "OpenText app icon",
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "OpenText",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                    translationY = titleOffsetY
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Fast • Private • Open Source",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = taglineAlpha }
            )

            Spacer(modifier = Modifier.height(44.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = loaderAlpha }
            ) {
                BrandSpinner()
            }
        }
    }
}

@Composable
private fun BrandSpinner(size: androidx.compose.ui.unit.Dp = 32.dp, strokeWidth: androidx.compose.ui.unit.Dp = 3.dp) {
    // Spinner lingkaran yang berputar terus-menerus, mirip loading indicator
    // di Instagram/Facebook/WhatsApp -- dipakai infiniteTransition bawaan
    // Compose (bukan animasi manual per-frame) supaya animasinya reliable
    // dan tidak "macet"/berhenti seperti masalah di dot animation sebelumnya.
    val infiniteTransition = rememberInfiniteTransition(label = "spinner_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation_value"
    )

    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val strokePx = strokeWidth.toPx()
        // Lingkaran (bounding box) untuk drawArc secara default persis menyentuh
        // ke-4 tepi Canvas (atas/bawah/kiri/kanan). Karena Stroke digambar rata
        // tengah pada garis lingkaran itu, setengah ketebalan strokePx jadi
        // "keluar" pas di titik singgung ke-4 sisi tsb dan ke-crop rata kotak --
        // efeknya lingkaran kelihatan kepotong kayak kotak. Fix-nya: susutkan
        // (inset) lingkarannya ke dalam sebesar setengah strokePx di semua sisi,
        // supaya seluruh badan garis lingkaran muat penuh di dalam Canvas.
        val inset = strokePx / 2
        val diameter = this.size.minDimension - strokePx
        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    BrandVioletLight,
                    BrandBlue,
                    BrandCyanSoft,
                    BrandCyanSoft.copy(alpha = 0f),
                    Color.Transparent,
                    Color.Transparent
                )
            ),
            startAngle = 0f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(diameter, diameter),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}