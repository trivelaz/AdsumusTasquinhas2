package pt.adsumus.pos.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.adsumus.pos.ui.components.AdsumusLogo

private data class HomeAction(
    val titulo: String,
    val subtitulo: String,
    val icone: ImageVector,
    val onClick: () -> Unit
)

/**
 * Ecrã inicial. Layout todo por pesos (weight), sem grelha "lazy" e sem
 * scroll: o cabeçalho ocupa só o espaço que precisa e os 4 cartões
 * dividem sempre o espaço restante em 2x2, cabendo no ecrã em qualquer
 * tablet — nada fica cortado nem é preciso arrastar para ver os botões.
 */
@Composable
fun HomeScreen(
    onNovoPedido: () -> Unit = {},
    onHistorico: () -> Unit = {},
    onFechoCaixa: () -> Unit = {},
    onConfiguracoes: () -> Unit = {}
) {
    val linha1 = listOf(
        HomeAction("Novo Pedido", "Registar uma nova venda", Icons.Filled.PointOfSale, onNovoPedido),
        HomeAction("Histórico", "Ver e corrigir pedidos desta sessão", Icons.Filled.History, onHistorico)
    )
    val linha2 = listOf(
        HomeAction("Fecho de Caixa", "Totais e fecho do período", Icons.Filled.Payments, onFechoCaixa),
        HomeAction("Configurações", "Impressora e manutenção", Icons.Filled.Settings, onConfiguracoes)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {

            // ---- Cabeçalho: tamanho fixo/compacto, nunca cresce à custa dos cartões ----
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AdsumusLogo(size = 64.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ADSUMUS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "PONTO DE VENDA",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Grelha 2x2: ocupa todo o espaço restante, dividido em partes iguais ----
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    linha1.forEach { acao ->
                        HomeActionCard(acao, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    linha2.forEach { acao ->
                        HomeActionCard(acao, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCard(acao: HomeAction, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = acao.onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(12.dp)
            ) {
                Icon(
                    acao.icone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Text(
                    text = acao.titulo.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = acao.subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
}
