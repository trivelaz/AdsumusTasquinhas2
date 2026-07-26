package pt.adsumus.pos.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import pt.adsumus.pos.model.CashClosure
import pt.adsumus.pos.model.CashMovementType
import pt.adsumus.pos.model.Category
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Regista cada fecho de caixa, para sempre, num ficheiro de texto PÚBLICO —
 * fora da pasta privada da aplicação — em Downloads/Adsumus/fechos_caixa.txt
 * (visível em qualquer gestor de ficheiros ou ligando o tablet a um PC).
 *
 * Como este ficheiro não pertence à área privada da app, NÃO é apagado
 * quando se "Limpa o armazenamento/dados" da app nas Definições do Android.
 * Só desaparece se alguém o apagar manualmente (o próprio, com um gestor de
 * ficheiros) — exatamente como pedido: "até eu mesmo apagar".
 *
 * Cada fecho é escrito por ANEXAÇÃO (nunca substitui o que já lá está), por
 * isso o histórico completo dos 5 dias de tasquinhas vai-se acumulando neste
 * único ficheiro, mesmo que a app seja reinstalada ou os dados limpos entre
 * eventos. Um fecho de caixa nunca é apagado automaticamente.
 */
object PermanentLedger {
    private const val PASTA = "Adsumus"
    private const val FICHEIRO = "fechos_caixa.txt"
    private val PT = Locale("pt", "PT")
    private val FORMATO_DATA = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", PT)
    private fun preco(v: Double) = String.format(PT, "%.2f €", v)

    /** Caminho legível a mostrar ao utilizador (ex.: nas Configurações). */
    val caminhoParaMostrar: String get() = "Downloads/$PASTA/$FICHEIRO"

    fun registarFecho(context: Context, fecho: CashClosure) {
        val texto = buildString {
            appendLine("=".repeat(48))
            appendLine("FECHO DE CAIXA #${fecho.id}")
            appendLine("Data/Hora do fecho: ${FORMATO_DATA.format(Date(fecho.timestamp))}")
            appendLine("Período desde: ${FORMATO_DATA.format(Date(fecho.periodStart))}")
            appendLine("Nº de pedidos: ${fecho.orderCount}")
            appendLine("-".repeat(48))
            appendLine("PAGAMENTOS")
            appendLine("  Dinheiro: ${preco(fecho.totalDinheiro)}")
            appendLine("  MB WAY:   ${preco(fecho.totalMBWay)}")
            appendLine("-".repeat(48))
            appendLine("CONTAGEM DE CAIXA")
            appendLine("  Fundo inicial:     ${preco(fecho.fundoInicial)}")
            appendLine("  Dinheiro esperado: ${preco(fecho.dinheiroEsperado)}")
            if (fecho.dinheiroContado != null) {
                appendLine("  Dinheiro contado:  ${preco(fecho.dinheiroContado)}")
                val dif = fecho.diferenca ?: 0.0
                val etiqueta = if (dif >= 0) "sobra" else "falta"
                appendLine("  Diferença: ${preco(kotlin.math.abs(dif))} ($etiqueta)")
            } else {
                appendLine("  Dinheiro contado:  (não contado)")
            }
            appendLine("-".repeat(48))
            appendLine("RESUMO POR CATEGORIA")
            Category.entries.forEach { cat ->
                val r = fecho.totaisPorCategoria[cat]
                appendLine("  ${cat.label}: ${r?.quantidade ?: 0} un.  ${preco(r?.total ?: 0.0)}")
            }
            appendLine("-".repeat(48))
            appendLine("VENDAS POR PRODUTO")
            fecho.produtos.forEach { p ->
                appendLine("  ${p.name}: ${p.quantidade} un.  ${preco(p.total)}")
            }
            appendLine("  TOTAL UNIDADES: ${fecho.totalUnidadesVendidas}")
            if (fecho.movimentos.isNotEmpty()) {
                appendLine("-".repeat(48))
                appendLine("MOVIMENTOS DE CAIXA")
                fecho.movimentos.sortedBy { it.timestamp }.forEach { m ->
                    val sinal = if (m.type == CashMovementType.ENTRADA) "+" else "-"
                    appendLine("  ${FORMATO_DATA.format(Date(m.timestamp))} [${m.type.label}] ${m.description}: $sinal${preco(m.amount)}")
                }
                appendLine("  Total entradas: ${preco(fecho.totalEntradas)}")
                appendLine("  Total saídas:   ${preco(fecho.totalSaidas)}")
            }
            appendLine("-".repeat(48))
            appendLine("TOTAL VENDIDO: ${preco(fecho.totalGeral)}")
            appendLine()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                registarViaMediaStore(context, texto)
            } else {
                @Suppress("DEPRECATION")
                registarViaFicheiroLegado(texto)
            }
        } catch (e: Exception) {
            // Uma falha a escrever a cópia pública nunca deve impedir o fecho de caixa em si;
            // o fecho fica sempre gravado no histórico normal da app (AppStorage).
        }
    }

    private fun registarViaMediaStore(context: Context, texto: String) {
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + PASTA
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(FICHEIRO, "$relativePath${File.separator}")

        var uri = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }

        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FICHEIRO)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            uri = resolver.insert(collection, values)
        }

        uri?.let {
            resolver.openOutputStream(it, "wa")?.use { out -> out.write(texto.toByteArray()) }
        }
    }

    @Suppress("DEPRECATION")
    private fun registarViaFicheiroLegado(texto: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PASTA)
        if (!dir.exists()) dir.mkdirs()
        val ficheiro = File(dir, FICHEIRO)
        FileOutputStream(ficheiro, true).use { it.write(texto.toByteArray()) }
    }
}
