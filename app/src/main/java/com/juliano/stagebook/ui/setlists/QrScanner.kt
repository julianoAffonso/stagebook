package com.juliano.stagebook.ui.setlists

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.ResultMetadataType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.juliano.stagebook.domain.QrPayload
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Câmera lendo QR (decodificação no aparelho, com ZXing; sem serviço do Google). */
@Composable
fun QrScanner(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onText)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    // a câmera pode ficar pronta depois de a tela sair (Cancelar logo após liberar a permissão):
    // aí ela não pode ser ligada, senão fica gastando bateria até o app ir para segundo plano
    val disposed = remember { AtomicBoolean(false) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                providerFuture.addListener({
                    if (disposed.get()) return@addListener
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor, QrAnalyzer { text -> view.post { latest(text) } })
                    val camera = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, camera, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier,
    )
    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            providerFuture.addListener({ providerFuture.get().unbindAll() }, ContextCompat.getMainExecutor(context))
            analysisExecutor.shutdown()
        }
    }
}

/** Procura um QR no quadro (só o brilho, plano Y). */
private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { b -> buffer.get(b) }
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
            try {
                val result = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
                // QR compactado: os bytes crus vêm nos segmentos binários, não no texto
                @Suppress("UNCHECKED_CAST")
                val bytes = (result.resultMetadata?.get(ResultMetadataType.BYTE_SEGMENTS) as? List<ByteArray>)
                    ?.fold(ByteArray(0)) { acc, b -> acc + b }
                onText(QrPayload.unpack(bytes) ?: result.text)
            } catch (_: NotFoundException) {
                // sem QR neste quadro
            } catch (_: Exception) {
                // QR borrado/parcial: tenta no próximo quadro
            } finally {
                reader.reset()
            }
        }
    }
}
