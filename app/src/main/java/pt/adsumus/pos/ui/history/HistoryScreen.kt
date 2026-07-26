package pt.adsumus.pos.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.OrderRecord
import pt.adsumus.pos.printer.ReceiptBuilder
import pt.adsumus.pos.printer.UsbPrinterManager
import pt.adsumus.pos.ui.components.AdsumusTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PT = Locale("pt", "PT")
private fun formatarHora(timestamp: Long) = SimpleDateFormat("HH:mm", PT).format(Date(timestamp))
private fun formatarDiaCompleto(chaveDia: String): String = try {
    val data = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(chaveDia)
    if (data != null) SimpleDateFormat("dd/MM/yyyy", PT).format(data) else chaveDia
} catch (e: Exception) {
    chaveDia
}
private fun preco(v: Double) = String.format(PT, "%.2f €", v)

private enum class TipoTalao(val rotulo: String) {
    CLIENTE("Recibo"),
    COZINHA("Talão de cozinha"),
    BAR("Talão de bar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val dias = HistoryRepository.pedidosAgrupadosPorDia()
    val context = LocalContext.current
    val printer = remember { UsbPrinterManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var idAReimprimir by remember { mutableStateOf<Int?>(null) }
    var pedidoParaAnular by remember { mutableStateOf<OrderRecord?>(null) }

    // Por omissão mostra o dia mais recente (o dia em curso do evento).
    var diaSelecionadoIdx by remember(dias.size) { mutableStateOf((dias.size - 1).coerceAtLeast(0)) }

    fun reimprimir(pedido: OrderRecord, tipo: TipoTalao) {
        if (idAReimprimir != null) return
        idAReimprimir = pedido.id
        scope.launch {
            val talao = when (tipo) {
                TipoTalao.CLIENTE -> ReceiptBuilder.reciboCliente(pedido.id, pedido.items)
                TipoTalao.COZINHA -> ReceiptBuilder.talaoProducao(
                    "COZINHA - COMIDA", pedido.id, ReceiptBuilder.itensPorCategoria(pedido.items, Category.COMIDA)
                )
                TipoTalao.BAR -> ReceiptBuilder.talaoProducao(
                    "BAR - BEBIDA", pedido.id, ReceiptBuilder.itensPorCategoria(pedido.items, Category.BEBIDA)
                )
            }
            val resultado = printer.imprimir(talao)
            snackbarHostState.showSnackbar(
                if (resultado.sucesso) "${tipo.rotulo} do pedido #${pedido.id} reimpresso."
                else "${tipo.rotulo}: ${resultado.mensagem}"
            )
            idAReimprimir = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AdsumusTopBar(title = "Histórico de Pedidos", onBack = onBack) }
    ) { padding ->
        if (dias.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Ainda não há pedidos registados.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = diaSelecionadoIdx) {
                dias.forEachIndexed { idx, (_, _) ->
                    Tab(
                        selected = diaSelecionadoIdx == idx,
                        onClick = { diaSelecionadoIdx = idx },
                        text = { Text("Dia ${idx + 1}") }
                    )
                }
            }

            val (chaveDia, pedidosDoDia) = dias[diaSelecionadoIdx.coerceIn(0, dias.size - 1)]
            val totalDoDia = pedidosDoDia.filter { !it.anulado }.sumOf { it.total }

            Text(
                formatarDiaCompleto(chaveDia),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pedidosDoDia, key = { "${it.id}-${it.timestamp}" }) { pedido ->
                    OrderRecordCard(
                        pedido = pedido,
                        aReimprimir = idAReimprimir == pedido.id,
                        onReimprimirCliente = { reimprimir(pedido, TipoTalao.CLIENTE) },
                        onReimprimirCozinha = { reimprimir(pedido, TipoTalao.COZINHA) },
                        onReimprimirBar = { reimprimir(pedido, TipoTalao.BAR) },
                        onAnularOuReativar = {
                            if (pedido.anulado) {
                                HistoryRepository.anularPedido(pedido.id, pedido.timestamp, anular = false)
                            } else {
                                pedidoParaAnular = pedido
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total do dia", style = MaterialTheme.typography.titleMedium)
                Text(
                    preco(totalDoDia),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    pedidoParaAnular?.let { pedido ->
        AlertDialog(
            onDismissRequest = { pedidoParaAnular = null },
            title = { Text("Anular pedido #${pedido.id}?") },
            text = {
                Text(
                    "O pedido fica marcado como anulado e deixa de contar para o total do dia " +
                        "e para o fecho de caixa. Continua visível no Histórico, para auditoria. " +
                        "Pode ser reativado depois, se for engano."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    HistoryRepository.anularPedido(pedido.id, pedido.timestamp, anular = true)
                    pedidoParaAnular = null
                }) { Text("Anular") }
            },
            dismissButton = {
                TextButton(onClick = { pedidoParaAnular = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun OrderRecordCard(
    pedido: OrderRecord,
    aReimprimir: Boolean,
    onReimprimirCliente: () -> Unit,
    onReimprimirCozinha: () -> Unit,
    onReimprimirBar: () -> Unit,
    onAnularOuReativar: () -> Unit
) {
    val temComida = pedido.items.any { it.product.category == Category.COMIDA }
    val temBebida = pedido.items.any { it.product.category == Category.BEBIDA }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (pedido.anulado)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Pedido #${pedido.id} — ${formatarHora(pedido.timestamp)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (pedido.anulado) {
                        Text(
                            "ANULADO — não conta para o total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    preco(pedido.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (pedido.anulado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            pedido.items.forEach { item ->
                Text(
                    "${item.quantity}x ${item.product.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onAnularOuReativar) {
                    Text(
                        if (pedido.anulado) "REATIVAR PEDIDO" else "ANULAR PEDIDO",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row {
                    if (temComida) {
                        TextButton(onClick = onReimprimirCozinha, enabled = !aReimprimir) {
                            Text("TALÃO COZINHA")
                        }
                    }
                    if (temBebida) {
                        TextButton(onClick = onReimprimirBar, enabled = !aReimprimir) {
                            Text("TALÃO BAR")
                        }
                    }
                    TextButton(onClick = onReimprimirCliente, enabled = !aReimprimir) {
                        Text(if (aReimprimir) "A REIMPRIMIR..." else "RECIBO")
                    }
                }
            }
        }
    }
}
