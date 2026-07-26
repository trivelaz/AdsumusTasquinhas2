package pt.adsumus.pos.printer

import java.io.ByteArrayOutputStream
import java.text.Normalizer

private const val ESC = 0x1B
private const val GS = 0x1D

/**
 * Remove acentos/til (á, ã, ç, ...) para garantir que qualquer impressora
 * térmica genérica (Xprinter incluída) imprime o texto correto, mesmo sem
 * a página de código PT configurada. "Água" -> "Agua".
 */
private fun String.semAcentos(): String {
    val normalizado = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalizado.replace(Regex("\\p{M}"), "")
}

/**
 * Construtor simples de um documento ESC/POS para impressoras térmicas
 * de 58mm (32 colunas em fonte normal), como a Xprinter XP-C260K.
 */
class ReceiptDocument {
    private val buffer = ByteArrayOutputStream()

    init {
        // ESC @ - inicializar/repor a impressora
        buffer.write(ESC); buffer.write('@'.code)
    }

    fun align(centrado: Boolean): ReceiptDocument {
        buffer.write(ESC); buffer.write('a'.code); buffer.write(if (centrado) 1 else 0)
        return this
    }

    fun negrito(ligado: Boolean): ReceiptDocument {
        buffer.write(ESC); buffer.write('E'.code); buffer.write(if (ligado) 1 else 0)
        return this
    }

    fun tamanhoDuplo(ligado: Boolean): ReceiptDocument {
        buffer.write(GS); buffer.write('!'.code); buffer.write(if (ligado) 0x11 else 0x00)
        return this
    }

    fun linha(texto: String = ""): ReceiptDocument {
        val bytes = texto.semAcentos().toByteArray(Charsets.US_ASCII)
        buffer.write(bytes)
        buffer.write('\n'.code)
        return this
    }

    fun avancar(linhas: Int = 3): ReceiptDocument {
        repeat(linhas) { buffer.write('\n'.code) }
        return this
    }

    /** Corte (parcial) do papel — comando standard suportado pela família Xprinter. */
    fun cortar(): ReceiptDocument {
        buffer.write(GS); buffer.write('V'.code); buffer.write(1)
        return this
    }

    fun toBytes(): ByteArray = buffer.toByteArray()
}
