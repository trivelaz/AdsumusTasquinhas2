package pt.adsumus.pos.model

/**
 * Um pedido já concluído (impresso), guardado no histórico da sessão atual.
 * O histórico não é persistido em disco — reinicia sempre que a app reinicia.
 */
data class OrderRecord(
    val id: Int,
    val timestamp: Long,
    val items: List<CartItem>,
    val paymentMethod: PaymentMethod = PaymentMethod.DINHEIRO,
    /** Só relevante para pagamentos em Dinheiro — valor entregue pelo cliente. */
    val valorEntregue: Double? = null,
    /** Só relevante para pagamentos em Dinheiro — troco devolvido ao cliente. */
    val troco: Double? = null,
    /**
     * Um pedido anulado (registado por engano — item errado, método de pagamento errado,
     * impressora encravou e repetiu-se o pedido, etc.) continua no histórico para auditoria,
     * mas deixa de contar para o total do dia, para o período atual e para o fecho de caixa.
     */
    val anulado: Boolean = false,
    /**
     * Verdadeiro se este pedido já foi corrigido pelo menos uma vez (ver
     * [pt.adsumus.pos.data.HistoryRepository.atualizarPedido]). Serve só para mostrar um aviso
     * discreto no Histórico — o pedido mantém o mesmo número, só os artigos/valores mudam.
     */
    val editado: Boolean = false,
    /** Instante da última correção, para mostrar "corrigido às HH:mm" no Histórico. */
    val editadoEm: Long? = null
) {
    val total: Double get() = items.sumOf { it.subtotal }

    fun totalPorCategoria(categoria: Category): Double =
        items.filter { it.product.category == categoria }.sumOf { it.subtotal }

    fun quantidadePorCategoria(categoria: Category): Int =
        items.filter { it.product.category == categoria }.sumOf { it.quantity }
}
