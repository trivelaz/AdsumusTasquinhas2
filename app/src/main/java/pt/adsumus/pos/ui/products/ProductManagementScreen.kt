package pt.adsumus.pos.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.adsumus.pos.data.ProductRepository
import pt.adsumus.pos.model.Category
import pt.adsumus.pos.model.Product
import pt.adsumus.pos.ui.components.AdsumusTopBar
import java.util.Locale

private val PT = Locale("pt", "PT")
private fun preco(v: Double) = String.format(PT, "%.2f €", v)

/**
 * Ecrã para gerir o menu: adicionar, editar e remover comidas, bebidas e
 * jogos, cada um com nome, preço e categoria. As alterações ficam gravadas
 * automaticamente e aparecem de imediato no ecrã de "Novo Pedido".
 */
@Composable
fun ProductManagementScreen(onBack: () -> Unit) {
    var produtoEmEdicao by remember { mutableStateOf<Product?>(null) }
    var aAdicionar by remember { mutableStateOf(false) }
    var produtoAApagar by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        topBar = { AdsumusTopBar(title = "Gerir Produtos", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { aAdicionar = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("NOVO PRODUTO") }
            )
        }
    ) { padding ->
        if (ProductRepository.products.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Ainda não há produtos. Toca em \"Novo Produto\" para adicionar o primeiro.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                Category.entries.forEach { categoria ->
                    val produtosCategoria = ProductRepository.products.filter { it.category == categoria }
                    if (produtosCategoria.isNotEmpty()) {
                        item {
                            Text(categoria.label, style = MaterialTheme.typography.titleLarge)
                        }
                        items(produtosCategoria, key = { it.id }) { produto ->
                            ProductRow(
                                produto = produto,
                                onEditar = { produtoEmEdicao = produto },
                                onApagar = { produtoAApagar = produto }
                            )
                        }
                    }
                }
            }
        }
    }

    if (aAdicionar) {
        ProductEditDialog(
            produto = null,
            onDismiss = { aAdicionar = false },
            onGuardar = { name, price, category ->
                ProductRepository.adicionar(name, price, category)
                aAdicionar = false
            }
        )
    }

    produtoEmEdicao?.let { produto ->
        ProductEditDialog(
            produto = produto,
            onDismiss = { produtoEmEdicao = null },
            onGuardar = { name, price, category ->
                ProductRepository.atualizar(produto.id, name, price, category)
                produtoEmEdicao = null
            }
        )
    }

    produtoAApagar?.let { produto ->
        AlertDialog(
            onDismissRequest = { produtoAApagar = null },
            title = { Text("Remover produto?") },
            text = { Text("Vais remover \"${produto.name}\" do menu. Os pedidos já registados com este produto não são afetados.") },
            confirmButton = {
                TextButton(onClick = {
                    ProductRepository.remover(produto.id)
                    produtoAApagar = null
                }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { produtoAApagar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ProductRow(produto: Product, onEditar: () -> Unit, onApagar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(produto.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    preco(produto.price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onEditar) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onApagar) {
                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditDialog(
    produto: Product?,
    onDismiss: () -> Unit,
    onGuardar: (String, Double, Category) -> Unit
) {
    var nome by remember { mutableStateOf(produto?.name ?: "") }
    var precoTexto by remember {
        mutableStateOf(produto?.price?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    var categoria by remember { mutableStateOf(produto?.category ?: Category.COMIDA) }
    var menuCategoriaAberto by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (produto == null) "Novo produto" else "Editar produto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = precoTexto,
                    onValueChange = { precoTexto = it.replace(',', '.') },
                    label = { Text("Preço (€)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = menuCategoriaAberto,
                    onExpandedChange = { menuCategoriaAberto = it }
                ) {
                    OutlinedTextField(
                        value = categoria.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuCategoriaAberto) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = menuCategoriaAberto,
                        onDismissRequest = { menuCategoriaAberto = false }
                    ) {
                        Category.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = { categoria = cat; menuCategoriaAberto = false }
                            )
                        }
                    }
                }
                erro?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val precoValido = precoTexto.toDoubleOrNull()
                when {
                    nome.isBlank() -> erro = "O nome não pode estar vazio."
                    precoValido == null || precoValido < 0 -> erro = "Indica um preço válido (ex.: 2.50)."
                    else -> onGuardar(nome, precoValido, categoria)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
