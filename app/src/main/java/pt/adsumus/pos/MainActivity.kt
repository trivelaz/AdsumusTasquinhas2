package pt.adsumus.pos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pt.adsumus.pos.data.AdminAuth
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.data.ProductRepository
import pt.adsumus.pos.model.OrderRecord
import pt.adsumus.pos.ui.caixa.FechoCaixaScreen
import pt.adsumus.pos.ui.history.HistoryScreen
import pt.adsumus.pos.ui.home.HomeScreen
import pt.adsumus.pos.ui.order.OrderScreen
import pt.adsumus.pos.ui.products.ProductManagementScreen
import pt.adsumus.pos.ui.security.AuditLogScreen
import pt.adsumus.pos.ui.settings.SettingsScreen
import pt.adsumus.pos.ui.theme.ADSUMUSTheme

private enum class Ecra { INICIO, PEDIDO, HISTORICO, FECHO_CAIXA, CONFIGURACOES, PRODUTOS, AUDITORIA }

class MainActivity : ComponentActivity() {

    private val pedirPermissaoArmazenamento =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional; se recusada, o fecho de caixa continua a ficar guardado dentro da app */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Só necessário em Android 9 (API 28) ou anterior: em Android 10+ a
        // cópia permanente em Downloads/Adsumus é gravada via MediaStore, sem
        // precisar desta permissão.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermissaoArmazenamento.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // Carrega produtos, pedidos e fechos de caixa gravados anteriormente
        // (tem de ser feito antes de desenhar qualquer ecrã).
        ProductRepository.init(this)
        HistoryRepository.init(this)
        AdminAuth.init(this)

        setContent {
            ADSUMUSTheme {
                var ecra by remember { mutableStateOf(Ecra.INICIO) }

                // Pedido que está a ser corrigido (não nulo só quando se chega ao ecrã de Pedido
                // a partir do botão "EDITAR" no Histórico) — permite ao ecrã de Pedido saber que
                // deve pré-preencher o carrinho e, ao concluir, voltar para o Histórico em vez de
                // ficar pronto para lançar um pedido novo.
                var pedidoEmEdicao by remember { mutableStateOf<OrderRecord?>(null) }
                // Nome de quem introduziu o PIN de administrador para autorizar a correção atual.
                var autorEdicao by remember { mutableStateOf<String?>(null) }

                when (ecra) {
                    Ecra.INICIO -> HomeScreen(
                        onNovoPedido = {
                            pedidoEmEdicao = null
                            autorEdicao = null
                            ecra = Ecra.PEDIDO
                        },
                        onHistorico = { ecra = Ecra.HISTORICO },
                        onFechoCaixa = { ecra = Ecra.FECHO_CAIXA },
                        onConfiguracoes = { ecra = Ecra.CONFIGURACOES }
                    )
                    Ecra.PEDIDO -> OrderScreen(
                        pedidoParaEditar = pedidoEmEdicao,
                        autorEdicao = autorEdicao,
                        onBack = {
                            val voltarPara = if (pedidoEmEdicao != null) Ecra.HISTORICO else Ecra.INICIO
                            pedidoEmEdicao = null
                            autorEdicao = null
                            ecra = voltarPara
                        },
                        onEdicaoConcluida = {
                            pedidoEmEdicao = null
                            autorEdicao = null
                            ecra = Ecra.HISTORICO
                        }
                    )
                    Ecra.HISTORICO -> HistoryScreen(
                        onBack = { ecra = Ecra.INICIO },
                        onEditarPedido = { pedido, autor ->
                            pedidoEmEdicao = pedido
                            autorEdicao = autor
                            ecra = Ecra.PEDIDO
                        }
                    )
                    Ecra.FECHO_CAIXA -> FechoCaixaScreen(onBack = { ecra = Ecra.INICIO })
                    Ecra.CONFIGURACOES -> SettingsScreen(
                        onBack = { ecra = Ecra.INICIO },
                        onGerirProdutos = { ecra = Ecra.PRODUTOS },
                        onVerAuditoria = { ecra = Ecra.AUDITORIA }
                    )
                    Ecra.PRODUTOS -> ProductManagementScreen(onBack = { ecra = Ecra.CONFIGURACOES })
                    Ecra.AUDITORIA -> AuditLogScreen(onBack = { ecra = Ecra.CONFIGURACOES })
                }
            }
        }
    }
}
