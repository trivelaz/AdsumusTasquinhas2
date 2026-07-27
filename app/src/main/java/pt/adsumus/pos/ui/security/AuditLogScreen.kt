package pt.adsumus.pos.ui.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.ui.components.AdsumusTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FORMATO = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT"))

/**
 * Regista de auditoria completo — cada linha corresponde a uma correção feita a um pedido já
 * pago (ver [HistoryRepository.atualizarPedido]). Só para consulta: nada aqui pode ser apagado
 * ou alterado a partir da app.
 */
@Composable
fun AuditLogScreen(onBack: () -> Unit) {
    val entradas = HistoryRepository.auditoria

    Scaffold(
        topBar = { AdsumusTopBar(title = "Registo de Alterações", onBack = onBack) }
    ) { padding ->
        if (entradas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Ainda não há nenhuma correção registada.\nAparecem aqui sempre que um pedido já pago for editado com o PIN de administrador.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(entradas, key = { it.id }) { entrada ->
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pedido #${entrada.pedidoId}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                FORMATO.format(Date(entrada.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Autorizado por: ${entrada.autor}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(entrada.resumo, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
