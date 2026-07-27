package pt.adsumus.pos.ui.order

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.data.ProductRepository
import pt.adsumus.pos.model.CartItem
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.OrderRecord
import pt.adsumus.pos.model.PaymentMethod
import pt.adsumus.pos.model.Product
import pt.adsumus.pos.printer.ReceiptBuilder
import pt.adsumus.pos.printer.UsbPrinterManager
import pt.adsumus.pos.ui.components.AdsumusTopBar
import java.util.Locale

private fun iconeDaCategoria(categoria: Category): ImageVector = when (categoria) {
    Category.COMIDA -> Icons.Filled.Restaurant
    Category.BEBIDA -> Icons.Filled.LocalBar
    Category.JOGOS -> Icons.Filled.SportsEsports
}

/**
 * Ecrã de "Novo Pedido" — usado tanto para lançar um pedido de raiz como para EDITAR um pedido
 * já registado. Quando [pedidoParaEditar] não é nulo, o carrinho arranca preenchido com os
 * artigos desse pedido; ao concluir, em vez de criar um pedido novo, corrige o pedido existente
 * (mesmo número) e reimprime os talões marcados como corrigidos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    onBack: () -> Unit,
    pedidoParaEditar: OrderRecord? = null,
    autorEdicao: String? = null,
    onEdicaoConcluida: (() -> Unit)? = null
) {
    val emEdicao = pedidoParaEditar != null

    val context = LocalContext.current
    val printer = remember { UsbPrinterManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var categoriaSelecionada by remember { mutableStateOf(Category.COMIDA) }
    var pesquisa by remember { mutableStateOf("") }
    val carrinho = remember {
        mutableStateListOf<CartItem>().apply { pedidoParaEditar?.let { addAll(it.items) } }
    }
    var aImprimir by remember { mutableStateOf(false) }
    var mostrarConfirmacaoSair by remember { mutableStateOf(false) }
    var mostrarPagamento by remember { mutableStateOf(false) }
    var metodoPagamento by remember { mutableStateOf(pedidoParaEditar?.paymentMethod ?: PaymentMethod.DINHEIRO) }
    var valorEntregueTexto by remember { mutableStateOf("") }

    fun tentarVoltar() {
        if (carrinho.isNotEmpty()) {
            mostrarConfirmacaoSair = true
        } else {
            onBack()
        }
    }

    if (mostrarConfirmacaoSair) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacaoSair = false },
            title = { Text(if (emEdicao) "Sair sem guardar a correção?" else "Sair sem guardar?") },
            text = {
                Text(
                    if (emEdicao)
                        "As alterações a este pedido ainda não foram guardadas nem reimpressas. " +
                            "Se saíres agora, o pedido #${pedidoParaEditar?.id} fica tal como estava antes."
                    else
                        "Tens artigos no pedido atual que ainda nao foram enviados para impressao. Se saíres agora, o pedido perde-se."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mostrarConfirmacaoSair = false
                    if (!emEdicao) carrinho.clear()
                    onBack()
                }) { Text(if (emEdicao) "Sair sem guardar" else "Sair e perder pedido") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacaoSair = false }) {
                    Text(if (emEdicao) "Continuar a editar" else "Continuar pedido")
                }
            }
        )
    }

    fun adicionar(produto: Product) {
        val idx = carrinho.indexOfFirst { it.product.id == produto.id }
        if (idx >= 0) {
            carrinho[idx] = carrinho[idx].copy(quantity = carrinho[idx].quantity + 1)
        } else {
            carrinho.add(CartItem(produto, 1))
        }
    }

    fun alterarQuantidade(item: CartItem, delta: Int) {
        val idx = carrinho.indexOfFirst { it.product.id == item.product.id }
        if (idx == -1) return
        val novaQtd = carrinho[idx].quantity + delta
        if (novaQtd <= 0) carrinho.removeAt(idx) else carrinho[idx] = carrinho[idx].copy(quantity = novaQtd)
    }

    val total = carrinho.sumOf { it.subtotal }

    BackHandler(enabled = true) { tentarVoltar() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AdsumusTopBar(
                title = if (emEdicao) "Editar Pedido #${pedidoParaEditar?.id}" else "Novo Pedido",
                onBack = { tentarVoltar() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (emEdicao) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "A corrigir o pedido #${pedidoParaEditar?.id}" +
                                (autorEdicao?.let { " — autorizado por $it" } ?: "") +
                                ". Ajusta os artigos e conclui para reimprimir os talões corrigidos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {

                // ---- Coluna esquerda: categorias + lista de produtos ----
                Column(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
                    TabRow(selectedTabIndex = Category.entries.indexOf(categoriaSelecionada)) {
                        Category.entries.forEach { cat ->
                            Tab(
                                selected = categoriaSelecionada == cat,
                                onClick = { categoriaSelecionada = cat },
                                text = { Text(cat.label) },
                                icon = { Icon(iconeDaCategoria(cat), contentDescription = null) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = pesquisa,
                        onValueChange = { pesquisa = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Pesquisar produto...") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        trailingIcon = {
                            if (pesquisa.isNotEmpty()) {
                                IconButton(onClick = { pesquisa = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpar pesquisa")
                                }
                            }
                        }
                    )

                    // Com texto de pesquisa, procura em todas as categorias de uma vez (mais rápido
                    // do que andar a mudar de separador); sem texto, mostra só a categoria selecionada.
                    val produtos = if (pesquisa.isBlank()) {
                        ProductRepository.products.filter { it.category == categoriaSelecionada }
                    } else {
                        ProductRepository.products.filter { it.name.contains(pesquisa.trim(), ignoreCase = true) }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        items(produtos, key = { it.id }) { produto ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            iconeDaCategoria(produto.category),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Column {
                                            Text(produto.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                String.format(Locale("pt", "PT"), "%.2f €", produto.price),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    FilledTonalButton(onClick = { adicionar(produto) }) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                        Text("Adicionar")
                                    }
                                }
                            }
                        }
                    }
                }

                VerticalDivider()

                // ---- Coluna direita: carrinho / pedido atual ----
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Text(
                        if (emEdicao) "Artigos do Pedido #${pedidoParaEditar?.id}" else "Pedido Atual",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(12.dp))

                    if (carrinho.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                "Sem artigos ainda.\nToca em \"Adicionar\" à esquerda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(carrinho, key = { it.product.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name)
                                        Text(
                                            String.format(
                                                Locale("pt", "PT"),
                                                "%.2f € x %d  =  %.2f €",
                                                item.product.price, item.quantity, item.subtotal
                                            ),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(onClick = { alterarQuantidade(item, -1) }) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Diminuir quantidade")
                                    }
                                    Text("${item.quantity}", style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = { alterarQuantidade(item, 1) }) {
                                        Icon(Icons.Filled.Add, contentDescription = "Aumentar quantidade")
                                    }
                                    IconButton(onClick = { carrinho.removeIf { it.product.id == item.product.id } }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remover artigo", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        String.format(Locale("pt", "PT"), "TOTAL: %.2f €", total),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (carrinho.isEmpty() || aImprimir) return@Button
                            metodoPagamento = pedidoParaEditar?.paymentMethod ?: PaymentMethod.DINHEIRO
                            valorEntregueTexto = pedidoParaEditar?.valorEntregue
                                ?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                                ?: ""
                            mostrarPagamento = true
                        },
                        enabled = carrinho.isNotEmpty() && !aImprimir,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (aImprimir) "A IMPRIMIR..."
                            else if (emEdicao) "GUARDAR CORREÇÃO"
                            else "FINALIZAR PEDIDO"
                        )
                    }
                }
            }
        }
    }

    if (mostrarPagamento) {
        val valorEntregue = valorEntregueTexto.replace(',', '.').toDoubleOrNull()
        val troco = if (metodoPagamento == PaymentMethod.DINHEIRO && valorEntregue != null) valorEntregue - total else null
        val pagamentoValido = when (metodoPagamento) {
            PaymentMethod.MBWAY -> true
            PaymentMethod.DINHEIRO -> valorEntregue != null && valorEntregue >= total
        }

        AlertDialog(
            onDismissRequest = { if (!aImprimir) mostrarPagamento = false },
            title = { Text("Pagamento") },
            text = {
                Column {
                    Text(
                        String.format(Locale("pt", "PT"), "Total a pagar: %.2f €", total),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentMethod.entries.forEach { metodo ->
                            FilterChip(
                                selected = metodoPagamento == metodo,
                                onClick = { metodoPagamento = metodo },
                                label = { Text(metodo.label) }
                            )
                        }
                    }

                    if (metodoPagamento == PaymentMethod.DINHEIRO) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = valorEntregueTexto,
                            onValueChange = { valorEntregueTexto = it },
                            label = { Text("Valor entregue pelo cliente (€)") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Notas mais comuns em uso — carregar num valor preenche logo o campo,
                            // para não ser preciso escrever no teclado a meio do atendimento.
                            listOf(5.0, 10.0, 20.0, 50.0).forEach { valor ->
                                AssistChip(
                                    onClick = {
                                        valorEntregueTexto = if (valor == valor.toLong().toDouble())
                                            valor.toLong().toString()
                                        else valor.toString()
                                    },
                                    label = { Text(String.format(Locale("pt", "PT"), "%.0f €", valor)) }
                                )
                            }
                            AssistChip(
                                onClick = {
                                    // "Valor certo" — cliente paga exatamente o total, sem troco.
                                    valorEntregueTexto = if (total == total.toLong().toDouble())
                                        total.toLong().toString()
                                    else String.format(Locale.US, "%.2f", total)
                                },
                                label = { Text("Certo") }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (troco != null && troco >= 0)
                                String.format(Locale("pt", "PT"), "TROCO: %.2f €", troco)
                            else if (valorEntregueTexto.isNotBlank())
                                "Valor insuficiente"
                            else
                                "Introduz o valor entregue",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (troco != null && troco >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = pagamentoValido && !aImprimir,
                    onClick = {
                        aImprimir = true
                        val itensPedido = carrinho.toList()
                        val metodo = metodoPagamento
                        val entregue = if (metodo == PaymentMethod.DINHEIRO) valorEntregue else null
                        val trocoFinal = if (metodo == PaymentMethod.DINHEIRO) troco else null

                        scope.launch {
                            val itensComida = ReceiptBuilder.itensPorCategoria(itensPedido, Category.COMIDA)
                            val itensBebida = ReceiptBuilder.itensPorCategoria(itensPedido, Category.BEBIDA)

                            if (emEdicao && pedidoParaEditar != null) {
                                val numeroPedido = pedidoParaEditar.id

                                // 1) Recibo do cliente corrigido
                                val rCliente = printer.imprimir(
                                    ReceiptBuilder.reciboCliente(numeroPedido, itensPedido, corrigido = true)
                                )
                                if (!rCliente.sucesso) snackbarHostState.showSnackbar("Recibo: ${rCliente.mensagem}")

                                // 2) Talão da cozinha corrigido, só se houver artigos de comida
                                if (itensComida.isNotEmpty()) {
                                    val rComida = printer.imprimir(
                                        ReceiptBuilder.talaoProducao("COZINHA - COMIDA", numeroPedido, itensComida, corrigido = true)
                                    )
                                    if (!rComida.sucesso) snackbarHostState.showSnackbar("Talão comida: ${rComida.mensagem}")
                                }

                                // 3) Talão do bar corrigido, só se houver artigos de bebida
                                if (itensBebida.isNotEmpty()) {
                                    val rBebida = printer.imprimir(
                                        ReceiptBuilder.talaoProducao("BAR - BEBIDA", numeroPedido, itensBebida, corrigido = true)
                                    )
                                    if (!rBebida.sucesso) snackbarHostState.showSnackbar("Talão bebida: ${rBebida.mensagem}")
                                }

                                HistoryRepository.atualizarPedido(
                                    numeroPedido, pedidoParaEditar.timestamp, itensPedido, metodo,
                                    autor = autorEdicao ?: "Desconhecido",
                                    valorEntregue = entregue, troco = trocoFinal
                                )
                                carrinho.clear()
                                aImprimir = false
                                mostrarPagamento = false
                                snackbarHostState.showSnackbar("Pedido #$numeroPedido corrigido e reimpresso.")
                                onEdicaoConcluida?.invoke() ?: onBack()
                            } else {
                                val numeroPedido = HistoryRepository.novoNumeroPedido()

                                // 1) Recibo do cliente, com número do pedido e todos os artigos
                                val rCliente = printer.imprimir(ReceiptBuilder.reciboCliente(numeroPedido, itensPedido))
                                if (!rCliente.sucesso) snackbarHostState.showSnackbar("Recibo: ${rCliente.mensagem}")

                                // 2) Talão da cozinha, só se houver artigos de comida
                                if (itensComida.isNotEmpty()) {
                                    val rComida = printer.imprimir(ReceiptBuilder.talaoProducao("COZINHA - COMIDA", numeroPedido, itensComida))
                                    if (!rComida.sucesso) snackbarHostState.showSnackbar("Talão comida: ${rComida.mensagem}")
                                }

                                // 3) Talão do bar, só se houver artigos de bebida
                                if (itensBebida.isNotEmpty()) {
                                    val rBebida = printer.imprimir(ReceiptBuilder.talaoProducao("BAR - BEBIDA", numeroPedido, itensBebida))
                                    if (!rBebida.sucesso) snackbarHostState.showSnackbar("Talão bebida: ${rBebida.mensagem}")
                                }

                                HistoryRepository.registarPedido(numeroPedido, itensPedido, metodo, entregue, trocoFinal)
                                carrinho.clear()
                                aImprimir = false
                                mostrarPagamento = false
                                snackbarHostState.showSnackbar("Pedido #$numeroPedido registado.")
                            }
                        }
                    }
                ) { Text(if (aImprimir) "A PROCESSAR..." else if (emEdicao) "REIMPRIMIR CORRIGIDO" else "CONCLUIR PAGAMENTO") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarPagamento = false }, enabled = !aImprimir) { Text("Cancelar") }
            }
        )
    }
}
