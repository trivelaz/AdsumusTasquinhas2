package pt.adsumus.pos.printer

import pt.adsumus.pos.model.CartItem
import pt.adsumus.pos.model.CashClosure
import pt.adsumus.pos.model.CashMovementType
import pt.adsumus.pos.model.Category
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptBuilder {

    private val PT = Locale("pt", "PT")
    private fun preco(v: Double) = String.format(PT, "%.2f EUR", v)
    private const val LARGURA = 32
    private fun separador() = "-".repeat(LARGURA)

    fun itensPorCategoria(itens: List<CartItem>, categoria: Category): List<CartItem> =
        itens.filter { it.product.category == categoria }

    /** Talão completo para o cliente, com número do pedido, todos os artigos e o total. */
    fun reciboCliente(numeroPedido: Int, itens: List<CartItem>): ByteArray {
        val doc = ReceiptDocument()
        doc.align(true).negrito(true).tamanhoDuplo(true)
        doc.linha("ADSUMUS")
        doc.tamanhoDuplo(false).negrito(false)
        doc.linha("Associacao Adsumus")
        doc.linha(SimpleDateFormat("dd/MM/yyyy HH:mm", PT).format(Date()))
        doc.linha(separador())
        doc.negrito(true).tamanhoDuplo(true)
        doc.linha("PEDIDO #$numeroPedido")
        doc.tamanhoDuplo(false).negrito(false)
        doc.linha(separador())
        doc.align(false)

        var total = 0.0
        for (item in itens) {
            doc.linha("${item.quantity}x ${item.product.name}")
            doc.linha("   ${preco(item.product.price)} un.  =  ${preco(item.subtotal)}")
            total += item.subtotal
        }

        doc.linha(separador())
        doc.negrito(true).tamanhoDuplo(true)
        doc.linha("TOTAL: ${preco(total)}")
        doc.tamanhoDuplo(false).negrito(false)
        doc.align(true)
        doc.linha("Obrigado!")
        doc.avancar(4)
        doc.cortar()
        return doc.toBytes()
    }

    /** Talão simples para a cozinha/bar — número do pedido, nomes e quantidades, em letra grande. */
    fun talaoProducao(titulo: String, numeroPedido: Int, itens: List<CartItem>): ByteArray {
        val doc = ReceiptDocument()
        doc.align(true).negrito(true).tamanhoDuplo(true)
        doc.linha(titulo)
        doc.linha("PEDIDO #$numeroPedido")
        doc.tamanhoDuplo(false)
        doc.linha(SimpleDateFormat("HH:mm", PT).format(Date()))
        doc.linha(separador())
        doc.align(false).tamanhoDuplo(true)
        for (item in itens) {
            doc.linha("${item.quantity}x ${item.product.name}")
        }
        doc.tamanhoDuplo(false).negrito(false)
        doc.avancar(4)
        doc.cortar()
        return doc.toBytes()
    }

    /** Talão simples de teste, usado no ecrã de Configurações para confirmar a ligação. */
    fun talaoTeste(): ByteArray {
        val doc = ReceiptDocument()
        doc.align(true).negrito(true).tamanhoDuplo(true)
        doc.linha("ADSUMUS")
        doc.tamanhoDuplo(false)
        doc.linha("TESTE DE IMPRESSORA")
        doc.negrito(false)
        doc.linha(SimpleDateFormat("dd/MM/yyyy HH:mm:ss", PT).format(Date()))
        doc.linha(separador())
        doc.align(false)
        doc.linha("Se estás a ler isto no papel,")
        doc.linha("a ligacao USB esta a funcionar bem.")
        doc.align(true)
        doc.linha(separador())
        doc.avancar(4)
        doc.cortar()
        return doc.toBytes()
    }

    /** Relatório completo de um fecho de caixa — resumo de vendas, pagamentos, produtos e movimentos. */
    fun relatorioFecho(fecho: CashClosure): ByteArray {
        val doc = ReceiptDocument()
        val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", PT)

        doc.align(true).negrito(true).tamanhoDuplo(true)
        doc.linha("ADSUMUS")
        doc.tamanhoDuplo(false)
        doc.linha("FECHO DE CAIXA #${fecho.id}")
        doc.negrito(false)
        doc.linha(dataHora.format(Date(fecho.timestamp)))
        doc.linha("Periodo desde: ${dataHora.format(Date(fecho.periodStart))}")
        doc.linha(separador())
        doc.align(false)

        doc.negrito(true).linha("PAGAMENTOS").negrito(false)
        doc.linha("Dinheiro:  ${preco(fecho.totalDinheiro)}")
        doc.linha("MB WAY:    ${preco(fecho.totalMBWay)}")
        doc.linha(separador())

        doc.negrito(true).linha("CONTAGEM DE CAIXA").negrito(false)
        doc.linha("Fundo inicial: ${preco(fecho.fundoInicial)}")
        doc.linha("Esperado:      ${preco(fecho.dinheiroEsperado)}")
        if (fecho.dinheiroContado != null) {
            doc.linha("Contado:       ${preco(fecho.dinheiroContado)}")
            val dif = fecho.diferenca ?: 0.0
            val etiqueta = if (dif >= 0) "sobra" else "falta"
            doc.negrito(true).linha("Diferenca: ${preco(kotlin.math.abs(dif))} ($etiqueta)").negrito(false)
        } else {
            doc.linha("Contado:       (nao contado)")
        }
        doc.linha(separador())

        doc.negrito(true).linha("POR CATEGORIA").negrito(false)
        Category.entries.forEach { cat ->
            val r = fecho.totaisPorCategoria[cat]
            doc.linha("${cat.label}: ${r?.quantidade ?: 0} un.  ${preco(r?.total ?: 0.0)}")
        }
        doc.linha(separador())

        doc.negrito(true).linha("POR PRODUTO").negrito(false)
        fecho.produtos.forEach { p ->
            doc.linha("${p.quantidade}x ${p.name}")
            doc.linha("   ${preco(p.total)}")
        }
        doc.linha("Total unidades: ${fecho.totalUnidadesVendidas}")
        doc.linha(separador())

        if (fecho.movimentos.isNotEmpty()) {
            doc.negrito(true).linha("MOVIMENTOS DE CAIXA").negrito(false)
            fecho.movimentos.sortedBy { it.timestamp }.forEach { m ->
                val sinal = if (m.type == CashMovementType.ENTRADA) "+" else "-"
                doc.linha("${m.description}: $sinal${preco(m.amount)}")
            }
            doc.linha("Entradas: ${preco(fecho.totalEntradas)}")
            doc.linha("Saidas:   ${preco(fecho.totalSaidas)}")
            doc.linha(separador())
        }

        doc.negrito(true).tamanhoDuplo(true)
        doc.linha("N. PEDIDOS: ${fecho.orderCount}")
        doc.linha("TOTAL: ${preco(fecho.totalGeral)}")
        doc.tamanhoDuplo(false).negrito(false)
        doc.avancar(4)
        doc.cortar()
        return doc.toBytes()
    }
}
