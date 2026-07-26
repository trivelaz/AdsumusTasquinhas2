package pt.adsumus.pos.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pt.adsumus.pos.model.CartItem
import pt.adsumus.pos.model.CashClosure
import pt.adsumus.pos.model.CashMovement
import pt.adsumus.pos.model.CashMovementType
import pt.adsumus.pos.model.CategorySummary
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.OrderRecord
import pt.adsumus.pos.model.PaymentMethod
import pt.adsumus.pos.model.Product
import pt.adsumus.pos.model.ProductSalesSummary
import java.io.File

/**
 * Guarda todo o estado da app (produtos, pedidos, movimentos de caixa,
 * fechos) num único ficheiro JSON, na área privada da app. Isto garante que
 * os dados sobrevivem a reinícios normais — fechar a app, deslizar para a
 * matar nos "recentes", reiniciar o tablet, ficar sem bateria, etc.
 *
 * IMPORTANTE — o que este ficheiro NÃO sobrevive:
 * Este ficheiro vive dentro da área privada da app (`filesDir`). Por desenho
 * do próprio Android, quando se vai a "Definições > Aplicações > Adsumus POS
 * > Armazenamento > Limpar armazenamento" (ou "Limpar dados"), o sistema
 * apaga sempre TUDO o que pertence à app — para qualquer app, em qualquer
 * telemóvel Android, sem exceção. Nenhuma app, incluindo esta, pode impedir
 * essa operação: é uma proteção do próprio sistema operativo, pensada para
 * dar ao utilizador controlo total sobre o armazenamento do telemóvel.
 *
 * Por isso, os FECHOS DE CAIXA (a faturação) são também gravados, em
 * separado e em duplicado, num ficheiro público fora da app — ver
 * [PermanentLedger]. É essa cópia pública que garante que a faturação nunca
 * se perde, mesmo que alguém limpe os dados da app por engano.
 */
object AppStorage {
    private const val FILE_NAME = "adsumus_state.json"
    private lateinit var file: File
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        initialized = true
    }

    @Synchronized
    fun load(): SavedState? {
        if (!initialized || !file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            SavedState(
                products = json.optJSONArray("products")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        Product(
                            o.getInt("id"),
                            o.getString("name"),
                            o.getDouble("price"),
                            Category.valueOf(o.getString("category"))
                        )
                    }
                },
                nextProductId = json.optInt("nextProductId", -1).takeIf { it > 0 },
                orders = json.optJSONArray("orders")?.let { arr ->
                    (0 until arr.length()).map { i -> parseOrder(arr.getJSONObject(i)) }
                } ?: emptyList(),
                closures = json.optJSONArray("closures")?.let { arr ->
                    (0 until arr.length()).map { i -> parseClosure(arr.getJSONObject(i)) }
                } ?: emptyList(),
                movements = json.optJSONArray("movements")?.let { arr ->
                    (0 until arr.length()).map { i -> parseMovement(arr.getJSONObject(i)) }
                } ?: emptyList(),
                nextOrderId = json.optInt("nextOrderId", 1),
                nextClosureId = json.optInt("nextClosureId", 1),
                nextMovementId = json.optInt("nextMovementId", 1),
                ultimoFecho = json.optLong("ultimoFecho", System.currentTimeMillis()),
                diaAtualPedidos = json.optString("diaAtualPedidos", ""),
                fundoInicialAtual = json.optDouble("fundoInicialAtual", 0.0)
            )
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun save(state: SavedState) {
        if (!initialized) return
        try {
            val json = JSONObject()
            state.products?.let { products ->
                val arr = JSONArray()
                products.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("price", p.price)
                        put("category", p.category.name)
                    })
                }
                json.put("products", arr)
            }
            state.nextProductId?.let { json.put("nextProductId", it) }
            json.put("orders", JSONArray().apply { state.orders.forEach { put(orderToJson(it)) } })
            json.put("closures", JSONArray().apply { state.closures.forEach { put(closureToJson(it)) } })
            json.put("movements", JSONArray().apply { state.movements.forEach { put(movementToJson(it)) } })
            json.put("nextOrderId", state.nextOrderId)
            json.put("nextClosureId", state.nextClosureId)
            json.put("nextMovementId", state.nextMovementId)
            json.put("ultimoFecho", state.ultimoFecho)
            json.put("diaAtualPedidos", state.diaAtualPedidos)
            json.put("fundoInicialAtual", state.fundoInicialAtual)
            file.writeText(json.toString())
        } catch (e: Exception) {
            // Uma falha a gravar o backup local nunca deve rebentar a app.
        }
    }

    private fun parseOrder(o: JSONObject): OrderRecord {
        val itemsArr = o.getJSONArray("items")
        val items = (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            val produto = Product(
                it.getInt("productId"),
                it.getString("name"),
                it.getDouble("price"),
                Category.valueOf(it.getString("category"))
            )
            CartItem(produto, it.getInt("quantity"))
        }
        return OrderRecord(
            id = o.getInt("id"),
            timestamp = o.getLong("timestamp"),
            items = items,
            paymentMethod = o.optString("paymentMethod", PaymentMethod.DINHEIRO.name)
                .let { runCatching { PaymentMethod.valueOf(it) }.getOrDefault(PaymentMethod.DINHEIRO) },
            valorEntregue = if (o.has("valorEntregue") && !o.isNull("valorEntregue")) o.getDouble("valorEntregue") else null,
            troco = if (o.has("troco") && !o.isNull("troco")) o.getDouble("troco") else null,
            anulado = o.optBoolean("anulado", false)
        )
    }

    private fun orderToJson(order: OrderRecord): JSONObject = JSONObject().apply {
        put("id", order.id)
        put("timestamp", order.timestamp)
        put("paymentMethod", order.paymentMethod.name)
        order.valorEntregue?.let { put("valorEntregue", it) }
        order.troco?.let { put("troco", it) }
        put("anulado", order.anulado)
        put("items", JSONArray().apply {
            order.items.forEach { item ->
                put(JSONObject().apply {
                    put("productId", item.product.id)
                    put("name", item.product.name)
                    put("price", item.product.price)
                    put("category", item.product.category.name)
                    put("quantity", item.quantity)
                })
            }
        })
    }

    private fun parseMovement(o: JSONObject): CashMovement = CashMovement(
        id = o.getInt("id"),
        timestamp = o.getLong("timestamp"),
        description = o.getString("description"),
        amount = o.getDouble("amount"),
        type = CashMovementType.valueOf(o.getString("type"))
    )

    private fun movementToJson(m: CashMovement): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("timestamp", m.timestamp)
        put("description", m.description)
        put("amount", m.amount)
        put("type", m.type.name)
    }

    private fun parseClosure(o: JSONObject): CashClosure {
        val catObj = o.getJSONObject("categorias")
        val map = Category.entries.associateWith { cat ->
            val catData = catObj.optJSONObject(cat.name)
            if (catData != null) {
                CategorySummary(catData.optInt("quantidade", 0), catData.optDouble("total", 0.0))
            } else {
                // Compatibilidade com fechos antigos, gravados antes desta atualização
                // (só tinham o valor total por categoria, sem quantidade).
                CategorySummary(0, catObj.optDouble(cat.name, 0.0))
            }
        }
        val produtosArr = o.optJSONArray("produtos")
        val produtos = produtosArr?.let { arr ->
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                ProductSalesSummary(p.getInt("productId"), p.getString("name"), p.getInt("quantidade"), p.getDouble("total"))
            }
        } ?: emptyList()
        val movimentosArr = o.optJSONArray("movimentos")
        val movimentos = movimentosArr?.let { arr ->
            (0 until arr.length()).map { i -> parseMovement(arr.getJSONObject(i)) }
        } ?: emptyList()
        return CashClosure(
            id = o.getInt("id"),
            timestamp = o.getLong("timestamp"),
            periodStart = o.getLong("periodStart"),
            orderCount = o.getInt("orderCount"),
            totalGeral = o.getDouble("totalGeral"),
            totalDinheiro = o.optDouble("totalDinheiro", o.getDouble("totalGeral")),
            totalMBWay = o.optDouble("totalMBWay", 0.0),
            totaisPorCategoria = map,
            produtos = produtos,
            movimentos = movimentos,
            fundoInicial = o.optDouble("fundoInicial", 0.0),
            dinheiroContado = if (o.has("dinheiroContado") && !o.isNull("dinheiroContado")) o.getDouble("dinheiroContado") else null
        )
    }

    private fun closureToJson(c: CashClosure): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("timestamp", c.timestamp)
        put("periodStart", c.periodStart)
        put("orderCount", c.orderCount)
        put("totalGeral", c.totalGeral)
        put("totalDinheiro", c.totalDinheiro)
        put("totalMBWay", c.totalMBWay)
        put("fundoInicial", c.fundoInicial)
        c.dinheiroContado?.let { put("dinheiroContado", it) }
        put("categorias", JSONObject().apply {
            c.totaisPorCategoria.forEach { (cat, resumo) ->
                put(cat.name, JSONObject().apply {
                    put("quantidade", resumo.quantidade)
                    put("total", resumo.total)
                })
            }
        })
        put("produtos", JSONArray().apply {
            c.produtos.forEach { p ->
                put(JSONObject().apply {
                    put("productId", p.productId)
                    put("name", p.name)
                    put("quantidade", p.quantidade)
                    put("total", p.total)
                })
            }
        })
        put("movimentos", JSONArray().apply { c.movimentos.forEach { put(movementToJson(it)) } })
    }

    data class SavedState(
        val products: List<Product>?,
        val nextProductId: Int?,
        val orders: List<OrderRecord>,
        val closures: List<CashClosure>,
        val movements: List<CashMovement>,
        val nextOrderId: Int,
        val nextClosureId: Int,
        val nextMovementId: Int,
        val ultimoFecho: Long,
        val diaAtualPedidos: String = "",
        val fundoInicialAtual: Double = 0.0
    )
}
