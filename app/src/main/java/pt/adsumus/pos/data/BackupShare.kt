package pt.adsumus.pos.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Junta os ficheiros de backup (pedidos.csv, fechos_caixa.csv, produtos.csv, fechos_caixa.txt,
 * todos em Downloads/Adsumus/) e abre o menu de partilha do Android — WhatsApp, Email, Google
 * Drive, Bluetooth, etc. — para os enviar sem precisar de ligar o tablet a um PC ou usar um
 * gestor de ficheiros.
 *
 * Só inclui na partilha os ficheiros que já existem (por exemplo, antes do primeiro pedido do
 * dia ainda não há pedidos.csv).
 */
object BackupShare {
    private const val PASTA = "Adsumus"
    private val FICHEIROS = listOf("pedidos.csv", "fechos_caixa.csv", "produtos.csv", "fechos_caixa.txt")

    /** Resultado da tentativa de reunir os ficheiros de backup para partilhar. */
    sealed class Resultado {
        data class Pronto(val uris: List<Uri>) : Resultado()
        object SemFicheiros : Resultado()
    }

    /**
     * Abre o menu de partilha do Android com os ficheiros de backup encontrados.
     * @return false se não houver nenhum ficheiro de backup ainda (por exemplo, primeiro
     * arranque, sem nenhum pedido registado).
     */
    fun partilhar(context: Context): Boolean {
        val uris = when (val resultado = localizarFicheiros(context)) {
            is Resultado.Pronto -> resultado.uris
            Resultado.SemFicheiros -> return false
        }
        if (uris.isEmpty()) return false

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Enviar Backup Adsumus").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return true
    }

    private fun localizarFicheiros(context: Context): Resultado {
        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            FICHEIROS.mapNotNull { localizarUriMediaStore(context, it) }
        } else {
            localizarFicheirosLegado(context)
        }
        return if (uris.isEmpty()) Resultado.SemFicheiros else Resultado.Pronto(uris)
    }

    private fun localizarUriMediaStore(context: Context, nomeFicheiro: String): Uri? {
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + PASTA
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(nomeFicheiro, "$relativePath${File.separator}")
        return resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }
    }

    @Suppress("DEPRECATION")
    private fun localizarFicheirosLegado(context: Context): List<Uri> {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PASTA)
        val autoridade = "${context.packageName}.fileprovider"
        return FICHEIROS
            .map { File(dir, it) }
            .filter { it.exists() }
            .map { FileProvider.getUriForFile(context, autoridade, it) }
    }
}
