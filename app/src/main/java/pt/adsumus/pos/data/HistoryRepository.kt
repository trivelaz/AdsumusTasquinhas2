package pt.adsumus.pos.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import pt.adsumus.pos.model.AuditLogEntry
import pt.adsumus.pos.model.CartItem
import pt.adsumus.pos.model.CashClosure
import pt.adsumus.pos.model.CashMovement
import pt.adsumus.pos.model.CashMovementType
import pt.adsumus.pos.model.CategorySummary
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.OrderRecord
import pt.adsumus.pos.model.PaymentMethod
import pt.adsumus.pos.model.ProductSalesSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Repositório partilhado com o histórico de pedidos, os movimentos manuais de
 * caixa (entradas/saídas) e os fechos de caixa.
 *
 * O estado é gravado automaticamente (via [AppStorage]) sempre que muda, por
 * isso sobrevive a fechar a app, matá-la nos "recentes" ou reiniciar o
 * tablet. Os fechos de caixa são, adicionalmente, gravados para sempre num
 * ficheiro público fora da app (ver [PermanentLedger]), que não é apagado
 * mesmo que se limpe o armazenamento/dados da aplicação nas Definições do
 * Android. Um fecho de caixa NUNCA é apagado automaticamente.
 */
object HistoryRepository {

    private lateinit var appContext: Context
    private var initialized = false

    private val _pedidos = mutableStateListOf<OrderRecord>()
    val pedidos: List<OrderRecord> get() = _pedidos

    private val _fechos = mutableStateListOf<CashClosure>()
    val fechos: List<CashClosure> get() = _fechos

    private val _movimentos = mutableStateListOf<CashMovement>()
    val movimentos: List<CashMovement> get() = _movimentos

    private val _auditoria = mutableStateListOf<AuditLogEntry>()
    /** Registo de auditoria completo — quem corrigiu que pedido, quando, e o que mudou. */
    val auditoria: List<AuditLogEntry> get() = _auditoria

    private var proximoIdPedido = 1
    private var proximoIdFecho = 1
    private var proximoIdMovimento = 1
    private var proximoIdAuditoria = 1
    private var diaAtualPedidos = ""

    private val FORMATO_DIA = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * "Hora de corte" do dia da tasquinha: antes desta hora, um pedido ainda conta para o dia
     * anterior. Isto evita que um serviço que vai, por exemplo, do jantar de quinta até às 3h da
     * manhã de sexta seja partido ao meio à meia-noite — só passa a ser "dia seguinte" às 6h,
     * altura em que já não deve haver ninguém a fazer pedidos.
     */
    private const val HORA_CORTE_DIA = 6

    private fun diaDe(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        if (cal.get(Calendar.HOUR_OF_DAY) < HORA_CORTE_DIA) {
            cal.add(Calendar.DATE, -1)
        }
        return FORMATO_DIA.format(cal.time)
    }

    private val ultimoFecho = mutableStateOf(System.currentTimeMillis())

    /** Troco com que a gaveta abriu, definido manualmente pelo operador para o período atual. */
    private val _fundoInicialAtual = mutableStateOf(0.0)
    val fundoInicialAtual: Double get() = _fundoInicialAtual.value

    /** Chamar uma vez, ao iniciar a app (ver MainActivity), antes de usar o resto da classe. */
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        AppStorage.init(appContext)

        val guardado = AppStorage.load()
        if (guardado != null) {
            _pedidos.clear(); _pedidos.addAll(guardado.orders)
            _fechos.clear(); _fechos.addAll(guardado.closures)
            _movimentos.clear(); _movimentos.addAll(guardado.movements)
            _auditoria.clear(); _auditoria.addAll(guardado.auditLog)
            proximoIdPedido = guardado.nextOrderId
            proximoIdFecho = guardado.nextClosureId
            proximoIdMovimento = guardado.nextMovementId
            proximoIdAuditoria = guardado.nextAuditId
            ultimoFecho.value = guardado.ultimoFecho
            diaAtualPedidos = guardado.diaAtualPedidos.ifBlank {
                guardado.orders.maxByOrNull { it.timestamp }?.let { diaDe(it.timestamp) } ?: ""
            }
            _fundoInicialAtual.value = guardado.fundoInicialAtual
        }
        initialized = true
    }

    private fun persistir() {
        if (!initialized) return
        // Lê o que já lá está para não apagar os produtos gravados pelo ProductRepository
        // nem o PIN de administrador gravado pelo AdminAuth (o AppStorage grava sempre o
        // ficheiro inteiro de uma vez, por isso é preciso reler o que não é gerido aqui).
        val estadoAtual = AppStorage.load()
        AppStorage.save(
            AppStorage.SavedState(
                products = estadoAtual?.products,
                nextProductId = estadoAtual?.nextProductId,
                orders = _pedidos.toList(),
                closures = _fechos.toList(),
                movements = _movimentos.toList(),
                nextOrderId = proximoIdPedido,
                nextClosureId = proximoIdFecho,
                nextMovementId = proximoIdMovimento,
                ultimoFecho = ultimoFecho.value,
                diaAtualPedidos = diaAtualPedidos,
                fundoInicialAtual = _fundoInicialAtual.value,
                auditLog = _auditoria.toList(),
                nextAuditId = proximoIdAuditoria,
                adminPinHash = estadoAtual?.adminPinHash,
                adminPinSalt = estadoAtual?.adminPinSalt
            )
        )
    }

    /**
     * Reserva o próximo número de pedido (para imprimir no talão antes de o pedido ficar
     * concluído). O número reinicia em 1 sempre que muda o dia do calendário — tal como na app
     * do ano passado, cada dia do evento tem os seus próprios "Pedido #1, #2, #3...".
     */
    fun novoNumeroPedido(): Int {
        val hoje = diaDe(System.currentTimeMillis())
        if (hoje != diaAtualPedidos) {
            diaAtualPedidos = hoje
            proximoIdPedido = 1
        }
        val n = proximoIdPedido++
        persistir()
        return n
    }

    /** Regista um pedido concluído no histórico, com o número já reservado por [novoNumeroPedido]. */
    fun registarPedido(
        numero: Int,
        itens: List<CartItem>,
        metodoPagamento: PaymentMethod,
        valorEntregue: Double? = null,
        troco: Double? = null
    ) {
        if (itens.isEmpty()) return
        _pedidos.add(
            0,
            OrderRecord(
                id = numero,
                timestamp = System.currentTimeMillis(),
                items = itens,
                paymentMethod = metodoPagamento,
                valorEntregue = valorEntregue,
                troco = troco
            )
        )
        persistir()
        if (initialized) CsvBackup.registarPedido(appContext, _pedidos.first(), diaDe(_pedidos.first().timestamp))
    }

    /**
     * Anula (ou reativa) um pedido já registado — usado quando um pedido foi lançado por engano
     * (item errado, método de pagamento errado, impressora encravou e o pedido foi repetido, etc.).
     *
     * O pedido NUNCA é apagado: fica sempre visível no Histórico, marcado como anulado, para
     * auditoria. Só deixa de contar para o total do dia, para o dinheiro esperado na gaveta e
     * para o próximo fecho de caixa. Se o pedido já tiver entrado num fecho de caixa anterior
     * (fechado antes de o engano ser detetado), anulá-lo agora já não corrige esse fecho.
     *
     * O par (numero, timestamp) identifica o pedido de forma única, porque o número reinicia a
     * cada dia do evento.
     */
    fun anularPedido(numero: Int, timestamp: Long, anular: Boolean = true) {
        val idx = _pedidos.indexOfFirst { it.id == numero && it.timestamp == timestamp }
        if (idx < 0) return
        val pedido = _pedidos[idx]
        if (pedido.anulado == anular) return
        val atualizado = pedido.copy(anulado = anular)
        _pedidos[idx] = atualizado
        persistir()
        if (initialized) CsvBackup.registarAnulacao(appContext, atualizado, diaDe(atualizado.timestamp))
    }

    /**
     * Verdadeiro enquanto o pedido ainda está no período em aberto (ainda não entrou num fecho
     * de caixa) — só nesta janela faz sentido reabrir o pedido para o corrigir, porque depois do
     * fecho os totais impressos e guardados no [PermanentLedger]/CSV já não devem mudar.
     */
    fun estaNoPeriodoAberto(pedido: OrderRecord): Boolean = pedido.timestamp >= ultimoFecho.value

    /** Um pedido só pode ser editado se ainda estiver no período em aberto e não estiver anulado. */
    fun podeEditar(pedido: OrderRecord): Boolean = !pedido.anulado && estaNoPeriodoAberto(pedido)

    /**
     * Corrige um pedido já registado (ex.: item a mais/a menos, quantidade errada), sem ter de o
     * anular e lançar tudo de novo. Mantém o mesmo número de pedido e o mesmo instante original
     * (para não saltar de dia/posição no Histórico) — só os artigos e o pagamento são atualizados,
     * e o pedido fica marcado como [OrderRecord.editado] para auditoria. Só é permitido enquanto
     * [podeEditar] for verdadeiro; chamar fora dessa janela não faz nada.
     *
     * Um pedido já pago só deve chegar aqui depois de o ecrã ter confirmado o PIN de administrador
     * (ver [AdminAuth.validarPin]) — esta função em si não volta a validar o PIN, mas exige sempre
     * um [autor] não vazio (quem o introduziu), porque é isso que fica gravado no registo de
     * auditoria, permanentemente e sem poder ser editado.
     */
    fun atualizarPedido(
        numero: Int,
        timestampOriginal: Long,
        novosItens: List<CartItem>,
        metodoPagamento: PaymentMethod,
        autor: String,
        valorEntregue: Double? = null,
        troco: Double? = null
    ) {
        if (novosItens.isEmpty() || autor.isBlank()) return
        val idx = _pedidos.indexOfFirst { it.id == numero && it.timestamp == timestampOriginal }
        if (idx < 0) return
        val pedido = _pedidos[idx]
        if (!podeEditar(pedido)) return
        val agora = System.currentTimeMillis()
        val resumo = resumoDiferencas(pedido.items, novosItens, pedido.paymentMethod, metodoPagamento)
        val atualizado = pedido.copy(
            items = novosItens,
            paymentMethod = metodoPagamento,
            valorEntregue = valorEntregue,
            troco = troco,
            editado = true,
            editadoEm = agora
        )
        _pedidos[idx] = atualizado
        _auditoria.add(
            0,
            AuditLogEntry(
                id = proximoIdAuditoria++,
                timestamp = agora,
                autor = autor.trim(),
                pedidoId = numero,
                pedidoTimestamp = timestampOriginal,
                resumo = resumo
            )
        )
        persistir()
        if (initialized) CsvBackup.registarEdicao(appContext, atualizado, diaDe(atualizado.timestamp))
    }

    /** Compara os artigos e o método de pagamento antes/depois, para o texto do registo de auditoria. */
    private fun resumoDiferencas(
        antigos: List<CartItem>,
        novos: List<CartItem>,
        metodoAntigo: PaymentMethod,
        metodoNovo: PaymentMethod
    ): String {
        val porIdAntigo = antigos.associateBy { it.product.id }
        val porIdNovo = novos.associateBy { it.product.id }
        val partes = mutableListOf<String>()

        novos.forEach { item ->
            val antigo = porIdAntigo[item.product.id]
            when {
                antigo == null -> partes.add("+${item.quantity}x ${item.product.name}")
                antigo.quantity != item.quantity -> partes.add("${item.product.name}: ${antigo.quantity}→${item.quantity}")
            }
        }
        antigos.forEach { item ->
            if (!porIdNovo.containsKey(item.product.id)) {
                partes.add("-${item.quantity}x ${item.product.name}")
            }
        }
        if (metodoAntigo != metodoNovo) {
            partes.add("Pagamento: ${metodoAntigo.label}→${metodoNovo.label}")
        }
        return if (partes.isEmpty()) "Reimpressão sem alterações aos artigos" else partes.joinToString("; ")
    }

    /** Entradas do registo de auditoria para um pedido específico, mais recente primeiro. */
    fun auditoriaDoPedido(numero: Int, timestamp: Long): List<AuditLogEntry> =
        _auditoria.filter { it.pedidoId == numero && it.pedidoTimestamp == timestamp }
            .sortedByDescending { it.timestamp }

    /** Regista um movimento manual de caixa (reforço/entrada ou saída/pagamento avulso). */
    fun registarMovimento(descricao: String, valor: Double, tipo: CashMovementType): CashMovement {
        val movimento = CashMovement(
            id = proximoIdMovimento++,
            timestamp = System.currentTimeMillis(),
            description = descricao.trim(),
            amount = valor,
            type = tipo
        )
        _movimentos.add(0, movimento)
        persistir()
        return movimento
    }

    /**
     * Pedidos feitos desde o último fecho de caixa (ou desde o início da sessão), EXCLUINDO
     * pedidos anulados — um pedido anulado nunca conta para o total do dia, para o dinheiro
     * esperado na gaveta, nem para o fecho de caixa. Continua visível no Histórico, para auditoria.
     */
    fun pedidosDoPeriodoAtual(): List<OrderRecord> =
        _pedidos.filter { it.timestamp >= ultimoFecho.value && !it.anulado }

    /** Movimentos manuais de caixa feitos desde o último fecho de caixa. */
    fun movimentosDoPeriodoAtual(): List<CashMovement> =
        _movimentos.filter { it.timestamp >= ultimoFecho.value }

    fun totalPeriodoAtual(): Double =
        pedidosDoPeriodoAtual().sumOf { it.total }

    fun totalDinheiroPeriodoAtual(): Double =
        pedidosDoPeriodoAtual().filter { it.paymentMethod == PaymentMethod.DINHEIRO }.sumOf { it.total }

    fun totalMBWayPeriodoAtual(): Double =
        pedidosDoPeriodoAtual().filter { it.paymentMethod == PaymentMethod.MBWAY }.sumOf { it.total }

    fun totaisPorCategoriaPeriodoAtual(): Map<Category, CategorySummary> {
        val pedidosPeriodo = pedidosDoPeriodoAtual()
        return Category.entries.associateWith { cat ->
            CategorySummary(
                quantidade = pedidosPeriodo.sumOf { it.quantidadePorCategoria(cat) },
                total = pedidosPeriodo.sumOf { it.totalPorCategoria(cat) }
            )
        }
    }

    /** Quantidade e valor vendido de cada produto, no período atual (desde o último fecho). */
    fun produtosPeriodoAtual(): List<ProductSalesSummary> {
        val pedidosPeriodo = pedidosDoPeriodoAtual()
        val agrupado = LinkedHashMap<Int, ProductSalesSummary>()
        pedidosPeriodo.forEach { pedido ->
            pedido.items.forEach { item ->
                val atual = agrupado[item.product.id]
                agrupado[item.product.id] = if (atual == null) {
                    ProductSalesSummary(item.product.id, item.product.name, item.quantity, item.subtotal)
                } else {
                    atual.copy(quantidade = atual.quantidade + item.quantity, total = atual.total + item.subtotal)
                }
            }
        }
        return agrupado.values.sortedByDescending { it.total }
    }

    fun inicioPeriodoAtual(): Long = ultimoFecho.value

    /** Define/corrige o troco com que a gaveta abriu, para o período em curso. */
    fun definirFundoInicial(valor: Double) {
        _fundoInicialAtual.value = valor
        persistir()
    }

    /** Quanto dinheiro físico deveria estar na gaveta agora, segundo os registos da app. */
    fun dinheiroEsperadoPeriodoAtual(): Double =
        _fundoInicialAtual.value + totalDinheiroPeriodoAtual() +
            movimentosDoPeriodoAtual().filter { it.type == CashMovementType.ENTRADA }.sumOf { it.amount } -
            movimentosDoPeriodoAtual().filter { it.type == CashMovementType.SAIDA }.sumOf { it.amount }

    /**
     * Pedidos agrupados por dia do calendário (chave "yyyy-MM-dd"), ordenados do dia mais antigo
     * para o mais recente, e dentro de cada dia por número de pedido crescente — tal como na app
     * do ano passado, com um separador por dia do evento.
     */
    fun pedidosAgrupadosPorDia(): List<Pair<String, List<OrderRecord>>> =
        _pedidos.groupBy { diaDe(it.timestamp) }
            .toSortedMap()
            .map { (dia, lista) -> dia to lista.sortedBy { it.id } }

    /**
     * Fecha a caixa: soma o período atual (pedidos + movimentos manuais),
     * guarda-o na lista de fechos, reinicia o período e grava uma cópia
     * permanente e pública em [PermanentLedger] e em CSV (ver [CsvBackup]) —
     * essas cópias são as que nunca se perdem, mesmo limpando os dados da
     * app. Um fecho de caixa nunca é apagado automaticamente e fica sempre
     * disponível para consulta e reimpressão.
     *
     * @param dinheiroContado dinheiro físico contado na gaveta, se o operador o tiver feito.
     */
    fun fecharCaixa(dinheiroContado: Double? = null): CashClosure {
        val pedidosPeriodo = pedidosDoPeriodoAtual()
        val movimentosPeriodo = movimentosDoPeriodoAtual()
        val fecho = CashClosure(
            id = proximoIdFecho++,
            timestamp = System.currentTimeMillis(),
            periodStart = ultimoFecho.value,
            orderCount = pedidosPeriodo.size,
            totalGeral = pedidosPeriodo.sumOf { it.total },
            totalDinheiro = pedidosPeriodo.filter { it.paymentMethod == PaymentMethod.DINHEIRO }.sumOf { it.total },
            totalMBWay = pedidosPeriodo.filter { it.paymentMethod == PaymentMethod.MBWAY }.sumOf { it.total },
            totaisPorCategoria = Category.entries.associateWith { cat ->
                CategorySummary(
                    quantidade = pedidosPeriodo.sumOf { it.quantidadePorCategoria(cat) },
                    total = pedidosPeriodo.sumOf { it.totalPorCategoria(cat) }
                )
            },
            produtos = produtosPeriodoAtual(),
            movimentos = movimentosPeriodo,
            fundoInicial = _fundoInicialAtual.value,
            dinheiroContado = dinheiroContado
        )
        _fechos.add(0, fecho)
        ultimoFecho.value = System.currentTimeMillis()
        _fundoInicialAtual.value = 0.0
        persistir()
        if (initialized) {
            PermanentLedger.registarFecho(appContext, fecho)
            CsvBackup.registarFecho(appContext, fecho)
        }
        return fecho
    }

    /**
     * Limpa o histórico de pedidos e movimentos guardados NA APP (usado no
     * ecrã de Configurações, para começar um novo evento). Isto NÃO apaga os
     * fechos de caixa já realizados (nem na app, nem a cópia permanente em
     * Downloads/Adsumus/fechos_caixa.txt) — os fechos ficam sempre guardados
     * e consultáveis, exatamente como pedido.
     */
    fun limparHistorico() {
        _pedidos.clear()
        _movimentos.clear()
        ultimoFecho.value = System.currentTimeMillis()
        proximoIdPedido = 1
        _fundoInicialAtual.value = 0.0
        persistir()
    }
}
