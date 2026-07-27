package pt.adsumus.pos.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.adsumus.pos.data.AdminAuth
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.data.BackupShare
import pt.adsumus.pos.data.CsvBackup
import pt.adsumus.pos.data.PermanentLedger
import pt.adsumus.pos.printer.ReceiptBuilder
import pt.adsumus.pos.printer.UsbPrinterManager
import pt.adsumus.pos.ui.components.AdsumusTopBar

@Composable
fun SettingsScreen(onBack: () -> Unit, onGerirProdutos: () -> Unit = {}, onVerAuditoria: () -> Unit = {}) {
    val context = LocalContext.current
    val printer = remember { UsbPrinterManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var aTestar by remember { mutableStateOf(false) }
    var mostrarConfirmacaoLimpar by remember { mutableStateOf(false) }
    var mostrarDialogoPin by remember { mutableStateOf(false) }

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

            SettingsSection(title = "Segurança") {
                Text(
                    if (AdminAuth.pinDefinido)
                        "Um pedido já pago só pode ser corrigido no Histórico depois de introduzir o PIN de administrador."
                    else
                        "Ainda não definiste um PIN de administrador — enquanto isso, a edição de pedidos pagos fica bloqueada no Histórico.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { mostrarDialogoPin = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(if (AdminAuth.pinDefinido) "ALTERAR PIN" else "DEFINIR PIN")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onVerAuditoria,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("VER REGISTO DE ALTERAÇÕES")
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

    if (mostrarDialogoPin) {
        DefinirPinDialog(
            onDismiss = { mostrarDialogoPin = false },
            onSucesso = {
                mostrarDialogoPin = false
                scope.launch { snackbarHostState.showSnackbar("PIN de administrador atualizado.") }
            }
        )
    }
}

/**
 * Diálogo para definir o primeiro PIN de administrador, ou para o trocar (exigindo sempre o PIN
 * atual, se já existir um). O PIN tem de ter pelo menos 4 dígitos e ser confirmado duas vezes,
 * para reduzir o risco de ficar definido um PIN escrito por engano.
 */
@Composable
private fun DefinirPinDialog(onDismiss: () -> Unit, onSucesso: () -> Unit) {
    val jaTemPin = AdminAuth.pinDefinido
    var pinAtual by remember { mutableStateOf("") }
    var pinNovo by remember { mutableStateOf("") }
    var pinConfirmar by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (jaTemPin) "Alterar PIN de administrador" else "Definir PIN de administrador") },
        text = {
            Column {
                if (jaTemPin) {
                    OutlinedTextField(
                        value = pinAtual,
                        onValueChange = { pinAtual = it.filter { c -> c.isDigit() }; erro = null },
                        label = { Text("PIN atual") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = pinNovo,
                    onValueChange = { pinNovo = it.filter { c -> c.isDigit() }; erro = null },
                    label = { Text("Novo PIN (mínimo 4 dígitos)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pinConfirmar,
                    onValueChange = { pinConfirmar = it.filter { c -> c.isDigit() }; erro = null },
                    label = { Text("Confirmar novo PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                erro?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    jaTemPin && !AdminAuth.validarPin(pinAtual) -> erro = "PIN atual incorreto."
                    pinNovo.length < 4 -> erro = "O novo PIN tem de ter pelo menos 4 dígitos."
                    pinNovo != pinConfirmar -> erro = "Os dois PIN não coincidem."
                    else -> {
                        AdminAuth.definirPin(pinNovo)
                        onSucesso()
                    }
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
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
