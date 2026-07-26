package pt.adsumus.pos.model

/** Resumo de vendas de um produto dentro de um fecho de caixa. */
data class ProductSalesSummary(
    val productId: Int,
    val name: String,
    val quantidade: Int,
    val total: Double
)

/** Quantidade e valor vendido numa categoria, dentro de um fecho de caixa. */
data class CategorySummary(
    val quantidade: Int,
    val total: Double
)

/**
 * Um "fecho de caixa": resumo completo de todos os pedidos e movimentos de
 * caixa feitos entre o fecho anterior (ou o início da sessão) e o momento em
 * que se tocou em "FECHAR CAIXA". Fica guardado para sempre no histórico
 * (nunca é apagado automaticamente) e pode ser consultado e reimpresso a
 * qualquer momento.
 */
data class CashClosure(
    val id: Int,
    val timestamp: Long,
    val periodStart: Long,
    val orderCount: Int,
    val totalGeral: Double,
    val totalDinheiro: Double,
    val totalMBWay: Double,
    val totaisPorCategoria: Map<Category, CategorySummary>,
    val produtos: List<ProductSalesSummary>,
    val movimentos: List<CashMovement>,
    /** Troco com que a gaveta abriu no início deste período. */
    val fundoInicial: Double = 0.0,
    /** Dinheiro físico contado na gaveta no momento do fecho (null se não foi feita contagem). */
    val dinheiroContado: Double? = null
) {
    val totalUnidadesVendidas: Int get() = produtos.sumOf { it.quantidade }
    val totalEntradas: Double get() = movimentos.filter { it.type == CashMovementType.ENTRADA }.sumOf { it.amount }
    val totalSaidas: Double get() = movimentos.filter { it.type == CashMovementType.SAIDA }.sumOf { it.amount }

    /** Quanto dinheiro físico deveria estar na gaveta, segundo os registos da app. */
    val dinheiroEsperado: Double get() = fundoInicial + totalDinheiro + totalEntradas - totalSaidas

    /** Diferença entre o que foi contado e o que era esperado (positivo = sobra, negativo = falta). */
    val diferenca: Double? get() = dinheiroContado?.let { it - dinheiroEsperado }
}
