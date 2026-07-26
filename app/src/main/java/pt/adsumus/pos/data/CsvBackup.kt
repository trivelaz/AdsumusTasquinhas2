package pt.adsumus.pos.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import pt.adsumus.pos.model.CashClosure
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.OrderRecord
import pt.adsumus.pos.model.Product
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup automático, em formato CSV, dos pedidos, fechos de caixa e produtos.
 * Guardado em Downloads/Adsumus/ (fora da área privada da app, tal como o
 * [PermanentLedger]), para depois abrir facilmente no Excel/Sheets e fazer as
 * contas de todos os dias do evento de uma vez.
 *
 * `pedidos.csv` e `fechos_caixa.csv` crescem por ANEXAÇÃO — uma linha nova a
 * cada pedido/fecho, nunca apagando o que já lá estava. `produtos.csv` é
 * substituído de cada vez que o menu muda, porque reflete sempre o menu
 * atual, não um histórico.
 *
 * Usa ponto e vírgula (;) como separador e vírgula como separador decimal —
 * é o formato que o Excel em português abre automaticamente já com os
 * números reconhecidos, sem ser preciso "Texto para Colunas".
 */
object CsvBackup {
    private const val PASTA = "Adsumus"
    private const val FICHEIRO_PEDIDOS = "pedidos.csv"
    private const val FICHEIRO_FECHOS = "fechos_caixa.csv"
    private const val FICHEIRO_PRODUTOS = "produtos.csv"

    private val PT = Locale("pt", "PT")
    private val FORMATO_DATA_HORA = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", PT)

    /** Caminho legível a mostrar ao utilizador (ex.: nas Configurações). */
    val caminhoParaMostrar: String get() = "Downloads/$PASTA/ (pedidos.csv, fechos_caixa.csv, produtos.csv)"

    private fun num(v: Double) = String.format(PT, "%.2f", v)

    private fun campo(valor: Any?): String {
        val texto = valor?.toString() ?: ""
        return if (texto.contains(";") || texto.contains("\"") || texto.contains("\n")) {
            "\"" + texto.replace("\"", "\"\"") + "\""
        } else texto
    }

    private fun linha(vararg campos: Any?): String = campos.joinToString(";") { campo(it) } + "\n"

    // ---------------------------------------------------------------- PEDIDOS

    private fun cabecalhoPedidos() =
        linha("id_pedido", "dia", "data_hora", "estado", "metodo_pagamento", "valor_entregue", "troco", "total", "itens")

    private fun linhaPedido(pedido: OrderRecord, dia: String): String {
        val itensTexto = pedido.items.joinToString(" | ") { "${it.quantity}x ${it.product.name} (${num(it.subtotal)}€)" }
        return linha(
            pedido.id,
            dia,
            FORMATO_DATA_HORA.format(Date(pedido.timestamp)),
            if (pedido.anulado) "ANULADO" else "ATIVO",
            pedido.paymentMethod.label,
            pedido.valorEntregue?.let { num(it) } ?: "",
            pedido.troco?.let { num(it) } ?: "",
            num(pedido.total),
            itensTexto
        )
    }

    /** Chamado automaticamente sempre que um pedido é concluído (ver HistoryRepository). */
    fun registarPedido(context: Context, pedido: OrderRecord, dia: String) {
        anexar(context, FICHEIRO_PEDIDOS, cabecalhoPedidos(), linhaPedido(pedido, dia))
    }

    /**
     * Chamado automaticamente sempre que um pedido é anulado (ou reativado) — ver
     * [HistoryRepository.anularPedido]. NÃO reescreve nem apaga a linha original do pedido:
     * acrescenta uma nova linha com o estado atualizado, para que o `pedidos.csv` mantenha o
     * registo completo de auditoria (quando foi lançado e quando/se foi anulado).
     */
    fun registarAnulacao(context: Context, pedido: OrderRecord, dia: String) {
        anexar(context, FICHEIRO_PEDIDOS, cabecalhoPedidos(), linhaPedido(pedido, dia))
    }

    // ----------------------------------------------------------------- FECHOS

    /** Chamado automaticamente sempre que se fecha a caixa (ver HistoryRepository). */
    fun registarFecho(context: Context, fecho: CashClosure) {
        val cabecalho = linha(
            "id_fecho", "data_hora", "periodo_desde", "num_pedidos",
            "total_geral", "total_dinheiro", "total_mbway",
            "fundo_inicial", "dinheiro_esperado", "dinheiro_contado", "diferenca",
            "qtd_comida", "total_comida", "qtd_bebida", "total_bebida", "qtd_jogos", "total_jogos",
            "total_entradas", "total_saidas"
        )
        val comida = fecho.totaisPorCategoria[Category.COMIDA]
        val bebida = fecho.totaisPorCategoria[Category.BEBIDA]
        val jogos = fecho.totaisPorCategoria[Category.JOGOS]
        val dados = linha(
            fecho.id,
            FORMATO_DATA_HORA.format(Date(fecho.timestamp)),
            FORMATO_DATA_HORA.format(Date(fecho.periodStart)),
            fecho.orderCount,
            num(fecho.totalGeral),
            num(fecho.totalDinheiro),
            num(fecho.totalMBWay),
            num(fecho.fundoInicial),
            num(fecho.dinheiroEsperado),
            fecho.dinheiroContado?.let { num(it) } ?: "",
            fecho.diferenca?.let { num(it) } ?: "",
            comida?.quantidade ?: 0, num(comida?.total ?: 0.0),
            bebida?.quantidade ?: 0, num(bebida?.total ?: 0.0),
            jogos?.quantidade ?: 0, num(jogos?.total ?: 0.0),
            num(fecho.totalEntradas),
            num(fecho.totalSaidas)
        )
        anexar(context, FICHEIRO_FECHOS, cabecalho, dados)
    }

    // --------------------------------------------------------------- PRODUTOS

    /** Chamado automaticamente sempre que o menu de produtos muda (ver ProductRepository). */
    fun exportarProdutos(context: Context, produtos: List<Product>) {
        val conteudo = buildString {
            append(linha("id", "nome", "preco", "categoria"))
            produtos.forEach { p -> append(linha(p.id, p.name, num(p.price), p.category.label)) }
        }
        substituir(context, FICHEIRO_PRODUTOS, conteudo)
    }

    // ---------------------------------------------------- Escrita de ficheiros

    private fun anexar(context: Context, nomeFicheiro: String, cabecalho: String, dados: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val existe = localizarUri(context, nomeFicheiro) != null
                escreverViaMediaStore(context, nomeFicheiro, if (existe) dados else cabecalho + dados, "wa")
            } else {
                @Suppress("DEPRECATION")
                anexarViaFicheiroLegado(nomeFicheiro, cabecalho, dados)
            }
        } catch (e: Exception) {
            // Uma falha a escrever o backup CSV nunca deve impedir a venda/fecho em si.
        }
    }

    private fun substituir(context: Context, nomeFicheiro: String, conteudo: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                escreverViaMediaStore(context, nomeFicheiro, conteudo, "wt")
            } else {
                @Suppress("DEPRECATION")
                escreverViaFicheiroLegado(nomeFicheiro, conteudo)
            }
        } catch (e: Exception) {
        }
    }

    private fun localizarUri(context: Context, nomeFicheiro: String): Uri? {
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + PASTA
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(nomeFicheiro, "$relativePath${File.separator}")
        return resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }
    }

    private fun escreverViaMediaStore(context: Context, nomeFicheiro: String, conteudo: String, modo: String) {
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + PASTA
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        var uri = localizarUri(context, nomeFicheiro)
        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFicheiro)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            uri = resolver.insert(collection, values)
        }
        uri?.let { resolver.openOutputStream(it, modo)?.use { out -> out.write(conteudo.toByteArray()) } }
    }

    @Suppress("DEPRECATION")
    private fun anexarViaFicheiroLegado(nomeFicheiro: String, cabecalho: String, dados: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PASTA)
        if (!dir.exists()) dir.mkdirs()
        val ficheiro = File(dir, nomeFicheiro)
        val jaExiste = ficheiro.exists()
        FileOutputStream(ficheiro, true).use {
            if (!jaExiste) it.write(cabecalho.toByteArray())
            it.write(dados.toByteArray())
        }
    }

    @Suppress("DEPRECATION")
    private fun escreverViaFicheiroLegado(nomeFicheiro: String, conteudo: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PASTA)
        if (!dir.exists()) dir.mkdirs()
        File(dir, nomeFicheiro).writeText(conteudo)
    }
}
