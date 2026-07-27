package pt.adsumus.pos.ui.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pt.adsumus.pos.data.AdminAuth

/**
 * Diálogo de autorização: pede o PIN de administrador e o nome de quem o está a introduzir,
 * antes de permitir alterar um pedido já pago. Só chama [onAutorizado] quando o PIN estiver
 * correto — nunca deixa prosseguir com um PIN errado ou em branco.
 *
 * Se ainda não houver nenhum PIN configurado, mostra antes uma mensagem a indicar que é preciso
 * defini-lo em Configurações > Segurança, em vez de deixar a edição sem qualquer proteção.
 */
@Composable
fun AdminAuthDialog(
    titulo: String = "Autorização necessária",
    mensagem: String = "Este pedido já está pago. Introduz o PIN de administrador para o poderes corrigir.",
    onDismiss: () -> Unit,
    onAutorizado: (autor: String) -> Unit
) {
    if (!AdminAuth.pinDefinido) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text("Nenhum PIN definido") },
            text = {
                Text(
                    "Ainda não foi definido nenhum PIN de administrador nesta app, por isso a " +
                        "edição de pedidos pagos está bloqueada. Define um PIN em " +
                        "Configurações > Segurança antes de continuar."
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Entendido") }
            }
        )
        return
    }

    var pin by remember { mutableStateOf("") }
    var autor by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(titulo) },
        text = {
            Column {
                Text(mensagem, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = autor,
                    onValueChange = { autor = it; erro = false },
                    label = { Text("O teu nome ou iniciais") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }; erro = false },
                    label = { Text("PIN de administrador") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = erro,
                    supportingText = {
                        if (erro) {
                            Text(
                                if (AdminAuth.tentativasFalhadas >= 3)
                                    "PIN incorreto (${AdminAuth.tentativasFalhadas} tentativas erradas)."
                                else "PIN incorreto."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.isNotBlank() && autor.isNotBlank(),
                onClick = {
                    if (AdminAuth.validarPin(pin)) {
                        onAutorizado(autor.trim())
                    } else {
                        erro = true
                        pin = ""
                    }
                }
            ) { Text("Autorizar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
