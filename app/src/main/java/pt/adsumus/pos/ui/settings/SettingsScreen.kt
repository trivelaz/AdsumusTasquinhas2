package pt.adsumus.pos.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.data.BackupShare
import pt.adsumus.pos.data.CsvBackup
import pt.adsumus.pos.data.PermanentLedger
import pt.adsumus.pos.printer.ReceiptBuilder
import pt.adsumus.pos.printer.UsbPrinterManager
import pt.adsumus.pos.ui.components.AdsumusTopBar

@Composable
fun SettingsScreen(onBack: () -> Unit, onGerirProdutos: () -> Unit = {}) {
    val context = LocalContext.current
    val printer = remember { UsbPrinterManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var aTestar by remember { mutableStateOf(false) }
    var mostrarConfirmacaoLimpar by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AdsumusTopBar(title = "Configurações", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            SettingsSection(title = "Impressora") {
                Text(
                    "Envia um talão curto de teste para confirmar que o tablet " +
                        "consegue falar com a Xprinter por USB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (aTestar) return@Button
                        aTestar = true
                        scope.launch {
                            val resultado = printer.imprimir(ReceiptBuilder.talaoTeste())
                            snackbarHostState.showSnackbar(
                                if (resultado.sucesso) "Talão de teste enviado com sucesso."
                                else "Falhou: ${resultado.mensagem}"
                            )
                            aTestar = false
                        }
                    },
                    enabled = !aTestar,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (aTestar) "A TESTAR..." else "TESTAR IMPRESSORA")
                }
            }

            SettingsSection(title = "Produtos") {
                Text(
                    "Adiciona, edita ou remove comidas, bebidas e jogos do menu — nome, preço e categoria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onGerirProdutos,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("GERIR PRODUTOS")
                }
            }

            SettingsSection(title = "Faturação — cópia permanente") {
                Text(
                    "Cada fecho de caixa é gravado, para sempre, num ficheiro de texto fora da app: " +
                        PermanentLedger.caminhoParaMostrar + ". " +
                        "Este ficheiro NÃO é apagado ao limpar o armazenamento/dados da app nas " +
                        "Definições do Android — só desaparece se for apagado manualmente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Além disso, cada pedido, fecho de caixa e o menu de produtos são gravados " +
                        "automaticamente em CSV, prontos a abrir no Excel/Sheets: " +
                        CsvBackup.caminhoParaMostrar + ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val enviado = BackupShare.partilhar(context)
                        if (!enviado) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Ainda não há nenhum ficheiro de backup para enviar.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("ENVIAR BACKUP")
                }
            }

            SettingsSection(title = "Histórico") {
                Text(
                    "Apaga os pedidos e movimentos de caixa em aberto NA APP, para começar um " +
                        "novo evento. Os fechos de caixa já realizados NUNCA são apagados por aqui " +
                        "— ficam sempre guardados e disponíveis para consulta e reimpressão.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { mostrarConfirmacaoLimpar = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("LIMPAR HISTÓRICO")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (mostrarConfirmacaoLimpar) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacaoLimpar = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Isto apaga os pedidos e movimentos de caixa em aberto desta sessão. Os fechos de caixa já realizados não são afetados. Não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    HistoryRepository.limparHistorico()
                    mostrarConfirmacaoLimpar = false
                }) { Text("Limpar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacaoLimpar = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
