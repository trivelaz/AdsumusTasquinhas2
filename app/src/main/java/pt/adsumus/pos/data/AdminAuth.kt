package pt.adsumus.pos.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Guarda e valida o PIN de administrador que protege a edição de pedidos já pagos.
 *
 * O PIN em si NUNCA é gravado — só um hash salgado (SHA-256 + salt aleatório de 16 bytes),
 * tal como se faria a uma palavra-passe. Isto evita que alguém com acesso ao ficheiro de
 * backup consiga ler o PIN diretamente.
 *
 * Não existe um PIN por omissão: enquanto ninguém definir um em Configurações > Segurança,
 * a edição de pedidos pagos fica simplesmente bloqueada (ver [HistoryRepository.podeEditar]
 * e o ecrã de Histórico) — mais seguro do que arrancar com um PIN previsível tipo "0000".
 */
object AdminAuth {

    private lateinit var appContext: Context
    private var initialized = false

    private var pinHash: String? = null
    private var pinSalt: String? = null

    /** Conta tentativas erradas seguidas, só para desencorajar tentativa-e-erro no ecrã. */
    private val _tentativasFalhadas = mutableStateOf(0)
    val tentativasFalhadas: Int get() = _tentativasFalhadas.value

    val pinDefinido: Boolean get() = pinHash != null

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        AppStorage.init(appContext)
        val guardado = AppStorage.load()
        pinHash = guardado?.adminPinHash
        pinSalt = guardado?.adminPinSalt
        initialized = true
    }

    private fun persistir() {
        if (!initialized) return
        val estadoAtual = AppStorage.load()
        AppStorage.save(
            AppStorage.SavedState(
                products = estadoAtual?.products,
                nextProductId = estadoAtual?.nextProductId,
                orders = estadoAtual?.orders ?: emptyList(),
                closures = estadoAtual?.closures ?: emptyList(),
                movements = estadoAtual?.movements ?: emptyList(),
                nextOrderId = estadoAtual?.nextOrderId ?: 1,
                nextClosureId = estadoAtual?.nextClosureId ?: 1,
                nextMovementId = estadoAtual?.nextMovementId ?: 1,
                ultimoFecho = estadoAtual?.ultimoFecho ?: System.currentTimeMillis(),
                diaAtualPedidos = estadoAtual?.diaAtualPedidos ?: "",
                fundoInicialAtual = estadoAtual?.fundoInicialAtual ?: 0.0,
                auditLog = estadoAtual?.auditLog ?: emptyList(),
                nextAuditId = estadoAtual?.nextAuditId ?: 1,
                adminPinHash = pinHash,
                adminPinSalt = pinSalt
            )
        )
    }

    private fun hash(pin: String, saltHex: String): String {
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val resultado = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return resultado.joinToString("") { "%02x".format(it) }
    }

    private fun novoSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Define (ou substitui) o PIN de administrador. Para trocar um PIN já existente, o ecrã de
     * Configurações deve exigir o PIN atual antes de chamar esta função — esta função em si não
     * volta a pedir confirmação.
     */
    fun definirPin(novoPin: String) {
        if (novoPin.length < 4) return
        val salt = novoSalt()
        pinSalt = salt
        pinHash = hash(novoPin, salt)
        _tentativasFalhadas.value = 0
        persistir()
    }

    /** Verdadeiro se o PIN introduzido corresponde ao PIN de administrador guardado. */
    fun validarPin(pin: String): Boolean {
        val salt = pinSalt
        val hashGuardado = pinHash
        if (salt == null || hashGuardado == null) return false
        val correto = hash(pin, salt) == hashGuardado
        _tentativasFalhadas.value = if (correto) 0 else _tentativasFalhadas.value + 1
        return correto
    }

    fun reiniciarTentativas() {
        _tentativasFalhadas.value = 0
    }
}
