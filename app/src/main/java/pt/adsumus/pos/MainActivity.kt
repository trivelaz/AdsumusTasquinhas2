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
import pt.adsumus.pos.data.HistoryRepository
import pt.adsumus.pos.data.ProductRepository
import pt.adsumus.pos.ui.caixa.FechoCaixaScreen
import pt.adsumus.pos.ui.history.HistoryScreen
import pt.adsumus.pos.ui.home.HomeScreen
import pt.adsumus.pos.ui.order.OrderScreen
import pt.adsumus.pos.ui.products.ProductManagementScreen
import pt.adsumus.pos.ui.settings.SettingsScreen
import pt.adsumus.pos.ui.theme.ADSUMUSTheme

private enum class Ecra { INICIO, PEDIDO, HISTORICO, FECHO_CAIXA, CONFIGURACOES, PRODUTOS }

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

        setContent {
            ADSUMUSTheme {
                var ecra by remember { mutableStateOf(Ecra.INICIO) }

                when (ecra) {
                    Ecra.INICIO -> HomeScreen(
                        onNovoPedido = { ecra = Ecra.PEDIDO },
                        onHistorico = { ecra = Ecra.HISTORICO },
                        onFechoCaixa = { ecra = Ecra.FECHO_CAIXA },
                        onConfiguracoes = { ecra = Ecra.CONFIGURACOES }
                    )
                    Ecra.PEDIDO -> OrderScreen(onBack = { ecra = Ecra.INICIO })
                    Ecra.HISTORICO -> HistoryScreen(onBack = { ecra = Ecra.INICIO })
                    Ecra.FECHO_CAIXA -> FechoCaixaScreen(onBack = { ecra = Ecra.INICIO })
                    Ecra.CONFIGURACOES -> SettingsScreen(
                        onBack = { ecra = Ecra.INICIO },
                        onGerirProdutos = { ecra = Ecra.PRODUTOS }
                    )
                    Ecra.PRODUTOS -> ProductManagementScreen(onBack = { ecra = Ecra.CONFIGURACOES })
                }
            }
        }
    }
}
