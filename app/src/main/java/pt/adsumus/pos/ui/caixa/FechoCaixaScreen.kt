package pt.adsumus.pos.ui.caixa

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
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.model.CashClosure
import pt.adsumus.pos.model.CashMovement
import pt.adsumus.pos.model.CashMovementType
import pt.adsumus.pos.model.CategorySummary
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.ProductSalesSummary
import pt.adsumus.pos.printer.ReceiptBuilder
import pt.adsumus.pos.printer.UsbPrinterManager
import pt.adsumus.pos.ui.components.AdsumusTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PT = Locale("pt", "PT")
private fun formatarHoraCompleta(timestamp: Long) = SimpleDateFormat("dd/MM HH:mm", PT).format(Date(timestamp))
private fun preco(v: Double) = String.format(PT, "%.2f €", v)

/** Estrutura simples usada para mostrar tanto o período em aberto como um fecho já feito, com o mesmo layout. */
private data class ResumoCaixa(
    val titulo: String,
    val orderCount: Int,
    val totalGeral: Double,
    val totalDinheiro: Double,
    val totalMBWay: Double,
    val categorias: Map<Category, CategorySummary>,
    val produtos: List<ProductSalesSummary>,
    val movimentos: List<CashMovement>,
    val fundoInicial: Double,
    val dinheiroEsperado: Double,
    val dinheiroContado: Double?
)

private fun CashClosure.paraResumo() = ResumoCaixa(
    titulo = "Fecho #$id — ${formatarHoraCompleta(timestamp)}",
    orderCount = orderCount,
    totalGeral = totalGeral,
    totalDinheiro = totalDinheiro,
    totalMBWay = totalMBWay,
    categorias = totaisPorCategoria,
    produtos = produtos,
    movimentos = movimentos,
    fundoInicial = fundoInicial,
    dinheiroEsperado = dinheiroEsperado,
    dinheiroContado = dinheiroContado
)

@Composable
fun FechoCaixaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val printer = remember { UsbPrinterManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recompor sempre que o histórico muda, sem precisar de um ViewModel dedicado.
    var tick by remember { mutableStateOf(0) }
    val numPedidos = HistoryRepository.pedidosDoPeriodoAtual().size
    val resumoAtual = ResumoCaixa(
        titulo = "Período atual",
        orderCount = numPedidos,
        totalGeral = HistoryRepository.totalPeriodoAtual(),
        totalDinheiro = HistoryRepository.totalDinheiroPeriodoAtual(),
        totalMBWay = HistoryRepository.totalMBWayPeriodoAtual(),
        categorias = HistoryRepository.totaisPorCategoriaPeriodoAtual(),
        produtos = HistoryRepository.produtosPeriodoAtual(),
        movimentos = HistoryRepository.movimentosDoPeriodoAtual(),
        fundoInicial = HistoryRepository.fundoInicialAtual,
        dinheiroEsperado = HistoryRepository.dinheiroEsperadoPeriodoAtual(),
        dinheiroContado = null
    )

    var mostrarConfirmacao by remember { mutableStateOf(false) }
    var mostrarNovoMovimento by remember { mutableStateOf(false) }
    var mostrarFundoInicial by remember { mutableStateOf(false) }
    var fechoSelecionado by remember { mutableStateOf<CashClosure?>(null) }
    var aImprimir by remember { mutableStateOf(false) }

    fun imprimirResumo(fecho: CashClosure) {
        if (aImprimir) return
        aImprimir = true
        scope.launch {
            val resultado = printer.imprimir(ReceiptBuilder.relatorioFecho(fecho))
            snackbarHostState.showSnackbar(
                if (resultado.sucesso) "Relatório do fecho #${fecho.id} impresso."
                else "Impressão: ${resultado.mensagem}"
            )
            aImprimir = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AdsumusTopBar(title = "Fecho de Caixa", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ResumoCaixaCard(resumoAtual, subtitulo = "$numPedidos pedido(s) desde o último fecho")
            }

            item {
                OutlinedButton(
                    onClick = { mostrarFundoInicial = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("DEFINIR FUNDO DE CAIXA INICIAL (${preco(resumoAtual.fundoInicial)})") }
            }

            item {
                OutlinedButton(
                    onClick = { mostrarNovoMovimento = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ ADICIONAR MOVIMENTO DE CAIXA") }
            }

            item {
                Button(
                    onClick = { mostrarConfirmacao = true },
                    enabled = numPedidos > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("FECHAR CAIXA")
                }
            }

            if (HistoryRepository.fechos.isNotEmpty()) {
                item {
                    Text(
                        "Fechos anteriores",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(HistoryRepository.fechos, key = { it.id }) { fecho ->
                    ClosureCard(fecho, onClick = { fechoSelecionado = fecho })
                }
            }
        }
    }

    if (mostrarConfirmacao) {
        var dinheiroContadoTexto by remember { mutableStateOf("") }
        val dinheiroContado = dinheiroContadoTexto.replace(',', '.').toDoubleOrNull()
        val diferenca = dinheiroContado?.let { it - resumoAtual.dinheiroEsperado }

        AlertDialog(
            onDismissRequest = { mostrarConfirmacao = false },
            title = { Text("Fechar caixa") },
            text = {
                Column {
                    Text("Total vendido: ${preco(resumoAtual.totalGeral)} em $numPedidos pedido(s).")
                    Spacer(Modifier.height(12.dp))
                    Text("Dinheiro esperado na gaveta: ${preco(resumoAtual.dinheiroEsperado)}", fontWeight = FontWeight.Bold)
                    Text(
                        "(fundo inicial ${preco(resumoAtual.fundoInicial)} + vendas em dinheiro ${preco(resumoAtual.totalDinheiro)} + entradas/saídas de caixa)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = dinheiroContadoTexto,
                        onValueChange = { dinheiroContadoTexto = it },
                        label = { Text("Dinheiro contado na gaveta (€) — opcional") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (diferenca != null) {
                        Spacer(Modifier.height(8.dp))
                        val etiqueta = if (diferenca >= 0) "SOBRA" else "FALTA"
                        Text(
                            "$etiqueta: ${preco(kotlin.math.abs(diferenca))}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (diferenca == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Esta ação não pode ser desfeita.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    HistoryRepository.fecharCaixa(dinheiroContado)
                    mostrarConfirmacao = false
                    tick++
                }) { Text("Fechar Caixa") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacao = false }) { Text("Cancelar") }
            }
        )
    }

    if (mostrarFundoInicial) {
        var valorTexto by remember { mutableStateOf(if (resumoAtual.fundoInicial > 0) "%.2f".format(resumoAtual.fundoInicial) else "") }
        val valor = valorTexto.replace(',', '.').toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { mostrarFundoInicial = false },
            title = { Text("Fundo de caixa inicial") },
            text = {
                Column {
                    Text(
                        "Quanto troco puseste na gaveta no início deste período? Isto é usado para calcular quanto dinheiro deveria lá estar no fecho.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = valorTexto,
                        onValueChange = { valorTexto = it },
                        label = { Text("Fundo inicial (€)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valor != null && valor >= 0,
                    onClick = {
                        HistoryRepository.definirFundoInicial(valor ?: 0.0)
                        mostrarFundoInicial = false
                        tick++
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarFundoInicial = false }) { Text("Cancelar") }
            }
        )
    }

    if (mostrarNovoMovimento) {
        NovoMovimentoDialog(
            onDismiss = { mostrarNovoMovimento = false },
            onConfirmar = { descricao, valor, tipo ->
                HistoryRepository.registarMovimento(descricao, valor, tipo)
                mostrarNovoMovimento = false
                tick++
            }
        )
    }

    fechoSelecionado?.let { fecho ->
        Dialog(onDismissRequest = { fechoSelecionado = null }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                        LazyColumn {
                            item { ResumoCaixaContent(fecho.paraResumo()) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { fechoSelecionado = null }, modifier = Modifier.weight(1f)) {
                            Text("Fechar")
                        }
                        Button(
                            onClick = { imprimirResumo(fecho) },
                            enabled = !aImprimir,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (aImprimir) "A IMPRIMIR..." else "REIMPRIMIR")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovoMovimentoDialog(
    onDismiss: () -> Unit,
    onConfirmar: (String, Double, CashMovementType) -> Unit
) {
    var descricao by remember { mutableStateOf("") }
    var valorTexto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(CashMovementType.ENTRADA) }
    val valor = valorTexto.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo movimento de caixa") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CashMovementType.entries.forEach { t ->
                        FilterChip(
                            selected = tipo == t,
                            onClick = { tipo = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição (ex.: Reforço de troco)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = valorTexto,
                    onValueChange = { valorTexto = it },
                    label = { Text("Valor (€)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = descricao.isNotBlank() && valor != null && valor > 0,
                onClick = { onConfirmar(descricao, valor ?: 0.0, tipo) }
            ) { Text("Adicionar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ResumoCaixaCard(resumo: ResumoCaixa, subtitulo: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(resumo.titulo, style = MaterialTheme.typography.titleLarge)
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            ResumoCaixaContent(resumo)
        }
    }
}

@Composable
private fun ResumoCaixaContent(resumo: ResumoCaixa) {
    Column {
        // ---- Pagamentos ----
        SecaoTitulo("Pagamentos")
        LinhaValor("Dinheiro", resumo.totalDinheiro)
        LinhaValor("MB WAY", resumo.totalMBWay)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ---- Contagem de caixa ----
        SecaoTitulo("Contagem de caixa")
        LinhaValor("Fundo inicial", resumo.fundoInicial)
        LinhaValor("Dinheiro esperado", resumo.dinheiroEsperado)
        if (resumo.dinheiroContado != null) {
            LinhaValor("Dinheiro contado", resumo.dinheiroContado)
            val dif = resumo.dinheiroContado - resumo.dinheiroEsperado
            val etiqueta = if (dif >= 0) "Sobra" else "Falta"
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(etiqueta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    preco(kotlin.math.abs(dif)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (dif == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        } else {
            Text(
                "Sem contagem física registada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ---- Categorias ----
        SecaoTitulo("Resumo por categoria")
        Category.entries.forEach { cat ->
            val r = resumo.categorias[cat] ?: CategorySummary(0, 0.0)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(cat.label, style = MaterialTheme.typography.bodyLarge)
                Text("${r.quantidade} un.  ${preco(r.total)}", style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ---- Por produto ----
        SecaoTitulo("Vendas por produto")
        if (resumo.produtos.isEmpty()) {
            Text("Sem vendas neste período.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Produto", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
                Text("Qtd.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }
            resumo.produtos.forEach { p ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(p.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                    Text("${p.quantidade}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(preco(p.total), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Total unidades: ${resumo.produtos.sumOf { it.quantidade }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }

        // ---- Movimentos de caixa ----
        if (resumo.movimentos.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SecaoTitulo("Movimentos de caixa")
            resumo.movimentos.sortedBy { it.timestamp }.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${formatarHoraCompleta(m.timestamp)} · ${m.type.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val sinal = if (m.type == CashMovementType.ENTRADA) "+" else "-"
                    Text(
                        "$sinal${preco(m.amount)}",
                        color = if (m.type == CashMovementType.ENTRADA) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TOTAL", style = MaterialTheme.typography.headlineSmall)
                Text("${resumo.orderCount} pedido(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                preco(resumo.totalGeral),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SecaoTitulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun LinhaValor(label: String, valor: Double) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(preco(valor), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ClosureCard(fecho: CashClosure, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Fecho #${fecho.id} — ${formatarHoraCompleta(fecho.timestamp)}", style = MaterialTheme.typography.titleMedium)
                Text("${fecho.orderCount} pedido(s) · toca para ver detalhes", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                preco(fecho.totalGeral),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
