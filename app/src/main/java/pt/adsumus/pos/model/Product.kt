package pt.adsumus.pos.model

data class Product(
    val id: Int,
    val name: String,
    val price: Double,
    val category: Category
)
