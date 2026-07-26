package pt.adsumus.pos.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.adsumus.pos.R

/**
 * Logótipo da ADSUMUS, usado no ecrã inicial e na barra superior de todos
 * os outros ecrãs. A imagem em si vive em `res/drawable/logo_adsumus`
 * (atualmente um placeholder — substituir pelo ficheiro real assim que
 * disponível, mantendo o mesmo nome).
 */
@Composable
fun AdsumusLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Image(
        painter = painterResource(id = R.drawable.logo_adsumus),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.size(size)
    )
}
