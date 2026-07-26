package pt.adsumus.pos.model

enum class CashMovementType(val label: String) {
    ENTRADA("Entrada"),
    SAIDA("Saída")
}

/**
 * Um movimento manual de dinheiro na gaveta que não corresponde a uma venda —
 * por exemplo, um reforço de troco (Entrada) ou um pagamento a um fornecedor
 * feito diretamente da caixa (Saída).
 */
data class CashMovement(
    val id: Int,
    val timestamp: Long,
    val description: String,
    val amount: Double,
    val type: CashMovementType
)
