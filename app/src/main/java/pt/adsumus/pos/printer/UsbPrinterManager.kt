package pt.adsumus.pos.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ResultadoImpressao(val sucesso: Boolean, val mensagem: String)

/**
 * Fala diretamente com a impressora térmica (Xprinter XP-C260K) ligada por
 * USB ao tablet, usando a USB Host API do Android + comandos ESC/POS puros
 * (não é necessária nenhuma biblioteca/driver do fabricante).
 */
class UsbPrinterManager(private val context: Context) {

    companion object {
        private const val ACAO_PERMISSAO_USB = "pt.adsumus.pos.USB_PERMISSION"
    }

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    /** Envia os bytes para a impressora. Trata automaticamente da permissão USB. */
    suspend fun imprimir(dados: ByteArray): ResultadoImpressao = withContext(Dispatchers.IO) {
        val dispositivo = encontrarImpressora()
            ?: return@withContext ResultadoImpressao(
                false,
                "Impressora nao encontrada. Verifica se a Xprinter esta ligada por USB/OTG ao tablet e o cabo bem encaixado."
            )

        if (!usbManager.hasPermission(dispositivo)) {
            val concedida = pedirPermissao(dispositivo)
            if (!concedida) {
                return@withContext ResultadoImpressao(false, "Permissao USB negada para a impressora.")
            }
        }

        enviar(dispositivo, dados)
    }

    private fun encontrarImpressora(): UsbDevice? {
        val dispositivos = usbManager.deviceList.values

        // 1) preferir um dispositivo cuja interface se declare como "Printer" (classe 7)
        dispositivos.firstOrNull { dev ->
            (0 until dev.interfaceCount).any { i -> dev.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
        }?.let { return it }

        // 2) alternativa: muitas impressoras térmicas genéricas (Xprinter incluída)
        //    reportam-se como classe "vendor specific" — procurar apenas por um
        //    endpoint de saída em massa (bulk OUT), que é o que importa para imprimir.
        return dispositivos.firstOrNull { dev -> encontrarInterfaceEEndpoint(dev) != null }
    }

    private fun encontrarInterfaceEEndpoint(dispositivo: UsbDevice): Pair<UsbInterface, android.hardware.usb.UsbEndpoint>? {
        for (i in 0 until dispositivo.interfaceCount) {
            val intf = dispositivo.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    return intf to ep
                }
            }
        }
        return null
    }

    private suspend fun pedirPermissao(dispositivo: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuacao ->
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else 0
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACAO_PERMISSAO_USB), flags
            )

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACAO_PERMISSAO_USB) {
                        context.unregisterReceiver(this)
                        val concedida = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (continuacao.isActive) continuacao.resume(concedida)
                    }
                }
            }

            val filtro = IntentFilter(ACAO_PERMISSAO_USB)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filtro, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filtro)
            }

            usbManager.requestPermission(dispositivo, pendingIntent)
        }

    private fun enviar(dispositivo: UsbDevice, dados: ByteArray): ResultadoImpressao {
        val (intf, endpoint) = encontrarInterfaceEEndpoint(dispositivo)
            ?: return ResultadoImpressao(false, "Nao foi encontrado um canal de escrita (bulk OUT) na impressora.")

        val ligacao = usbManager.openDevice(dispositivo)
            ?: return ResultadoImpressao(false, "Nao foi possivel abrir ligacao com a impressora.")

        if (!ligacao.claimInterface(intf, true)) {
            ligacao.close()
            return ResultadoImpressao(false, "Nao foi possivel reservar a interface USB da impressora.")
        }

        // Enviar em blocos para não exceder o tamanho máximo de um bulkTransfer.
        val tamanhoBloco = 4096
        var offset = 0
        var falhou = false
        while (offset < dados.size) {
            val fim = minOf(offset + tamanhoBloco, dados.size)
            val bloco = dados.copyOfRange(offset, fim)
            val enviado = ligacao.bulkTransfer(endpoint, bloco, bloco.size, 5000)
            if (enviado < 0) {
                falhou = true
                break
            }
            offset = fim
        }

        ligacao.releaseInterface(intf)
        ligacao.close()

        return if (!falhou) {
            ResultadoImpressao(true, "Impresso com sucesso.")
        } else {
            ResultadoImpressao(false, "Falha ao enviar dados para a impressora.")
        }
    }
}
