package pt.adsumus.pos.model

/**
 * Um registo de auditoria — criado sempre que um pedido já pago é corrigido através do PIN de
 * administrador (ver [pt.adsumus.pos.data.AdminAuth] e [pt.adsumus.pos.data.HistoryRepository]).
 * Nunca é apagado nem alterado depois de criado: é o rasto de "quem alterou o quê e quando".
 */
data class AuditLogEntry(
    val id: Int,
    val timestamp: Long,
    /** Nome/iniciais de quem introduziu o PIN e autorizou a alteração. */
    val autor: String,
    val pedidoId: Int,
    val pedidoTimestamp: Long,
    /** Descrição legível das diferenças (artigos a mais/menos, quantidades, pagamento). */
    val resumo: String
)
