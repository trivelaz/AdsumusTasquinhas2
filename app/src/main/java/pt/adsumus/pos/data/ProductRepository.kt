package pt.adsumus.pos.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.Product

/**
 * Menu de produtos da tasquinha da Associação ADSUMUS — comida, bebidas e
 * jogos. Editável a partir do ecrã "Gerir Produtos" (Configurações), com
 * as alterações gravadas automaticamente (sobrevive a fechar a app).
 *
 * Nota: à semelhança do resto do estado da app, esta lista fica na área
 * privada da app, por isso NÃO sobrevive a "Limpar armazenamento/dados" da
 * app nas Definições do Android — mas isso é normal para o menu (não é
 * faturação), e continua sempre editável na app.
 */
object ProductRepository {

    private val defaults = listOf(
        Product(1, "Bifana", 3.50, Category.COMIDA),
        Product(2, "Prego no Pão", 4.00, Category.COMIDA),
        Product(3, "Francesinha", 7.50, Category.COMIDA),
        Product(4, "Tosta Mista", 2.50, Category.COMIDA),
        Product(5, "Febras", 5.00, Category.COMIDA),
        Product(6, "Batata Frita", 2.00, Category.COMIDA),
        Product(7, "Sandes de Chouriço", 3.00, Category.COMIDA),

        Product(8, "Água 0.5L", 1.00, Category.BEBIDA),
        Product(9, "Imperial / Fino", 1.50, Category.BEBIDA),
        Product(10, "Refrigerante", 1.50, Category.BEBIDA),
        Product(11, "Sangria (copo)", 2.00, Category.BEBIDA),
        Product(12, "Vinho (copo)", 1.50, Category.BEBIDA),
        Product(13, "Café", 0.80, Category.BEBIDA),

        Product(14, "Ficha de Jogo", 1.00, Category.JOGOS),
        Product(15, "Rifa", 2.00, Category.JOGOS),
        Product(16, "Tômbola", 1.00, Category.JOGOS)
    )

    private val _products = mutableStateListOf<Product>()
    val products: List<Product> get() = _products

    private var proximoId = 1
    private var initialized = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        AppStorage.init(context)

        val guardado = AppStorage.load()
        if (guardado?.products != null) {
            _products.clear(); _products.addAll(guardado.products)
            proximoId = guardado.nextProductId ?: ((guardado.products.maxOfOrNull { it.id } ?: 0) + 1)
        } else {
            _products.clear(); _products.addAll(defaults)
            proximoId = (defaults.maxOfOrNull { it.id } ?: 0) + 1
        }
        initialized = true
        persistir()
    }

    private fun persistir() {
        if (!initialized) return
        val estadoAtual = AppStorage.load()
        AppStorage.save(
            AppStorage.SavedState(
                products = _products.toList(),
                nextProductId = proximoId,
                orders = estadoAtual?.orders ?: emptyList(),
                closures = estadoAtual?.closures ?: emptyList(),
                movements = estadoAtual?.movements ?: emptyList(),
                nextOrderId = estadoAtual?.nextOrderId ?: 1,
                nextClosureId = estadoAtual?.nextClosureId ?: 1,
                nextMovementId = estadoAtual?.nextMovementId ?: 1,
                ultimoFecho = estadoAtual?.ultimoFecho ?: System.currentTimeMillis(),
                diaAtualPedidos = estadoAtual?.diaAtualPedidos ?: "",
                fundoInicialAtual = estadoAtual?.fundoInicialAtual ?: 0.0
            )
        )
        CsvBackup.exportarProdutos(appContext, _products.toList())
    }

    /** Adiciona um novo produto (comida, bebida ou jogo) com nome, preço e categoria. */
    fun adicionar(name: String, price: Double, category: Category): Product {
        val produto = Product(id = proximoId++, name = name.trim(), price = price, category = category)
        _products.add(produto)
        persistir()
        return produto
    }

    /** Atualiza nome, preço e/ou categoria de um produto existente. */
    fun atualizar(id: Int, name: String, price: Double, category: Category) {
        val idx = _products.indexOfFirst { it.id == id }
        if (idx == -1) return
        _products[idx] = _products[idx].copy(name = name.trim(), price = price, category = category)
        persistir()
    }

    /** Remove um produto do menu (não afeta pedidos/faturação já registados, que guardam o preço da altura). */
    fun remover(id: Int) {
        _products.removeIf { it.id == id }
        persistir()
    }
}
