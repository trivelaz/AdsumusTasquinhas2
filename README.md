# ADSUMUS POS

App de balcão (POS) para a tasquinha da Associação ADSUMUS, com categorias
**Comida / Bebida / Jogos** e impressão direta por USB para a impressora
térmica **Xprinter XP-C260K**, ligada ao **Samsung Galaxy Tab S10**.

## Como funciona a impressão

Ao tocar em **IMPRIMIR** no ecrã de "Novo Pedido", a app envia até 3 talões
para a impressora, um a seguir ao outro:

1. **Recibo do cliente** — todos os artigos do pedido, com preços e total.
2. **Talão da cozinha** ("COZINHA - COMIDA") — só os artigos da categoria
   Comida. Só é impresso se houver pelo menos um artigo de comida no pedido.
3. **Talão do bar** ("BAR - BEBIDA") — só os artigos da categoria Bebida.
   Só é impresso se houver pelo menos um artigo de bebida no pedido.

Artigos da categoria **Jogos** aparecem apenas no recibo do cliente (não
precisam de preparação em cozinha/bar).

A ligação à impressora é feita diretamente por **USB Host** (o tablet como
"anfitrião"), com comandos ESC/POS enviados em bruto — não é preciso
instalar nenhum driver ou app do fabricante. É preciso um cabo/adaptador
**USB-C para USB-A (OTG)**, já que a Xprinter tem entrada USB tipo A.

Na primeira impressão o Android vai pedir para autorizares a app a aceder
ao dispositivo USB — aceita e marca "usar sempre" para não repetir o pedido.

**Nota sobre acentos:** para garantir que qualquer impressora térmica
genérica imprime bem, o texto enviado à impressora tem os acentos
removidos automaticamente (ex.: "Água" imprime como "Agua"). O texto no
ecrã do tablet mantém os acentos normalmente.

## Como compilar o APK (sem PC, direto do tablet)

1. Cria uma conta gratuita no GitHub (github.com), se ainda não tiveres.
2. Cria um repositório novo (pode ser privado), por exemplo `adsumus-pos`.
3. Carrega todos os ficheiros e pastas deste pacote para o repositório
   (mantendo a mesma estrutura de pastas): no browser do tablet, abre o
   repositório → "Add file" → "Upload files" → arrasta/seleciona tudo →
   "Commit changes".
4. Ao fazer o commit para o branch `main`, o workflow
   `.github/workflows/build-apk.yml` arranca automaticamente a compilação
   na nuvem — vês isto no separador **Actions** do repositório.
5. Espera 2–4 minutos até o workflow "Build APK" ficar com um ✅ verde.
6. Abre esse workflow → em baixo, em **Artifacts**, aparece
   `adsumus-pos-debug` → toca para descarregar (.zip com o `app-debug.apk`
   lá dentro).
7. No tablet: extrai o .zip, toca no `app-debug.apk` para instalar. Se for
   a primeira vez, autoriza "instalar apps de fontes desconhecidas" só
   para esta instalação.

## Faturação permanente e gestão de produtos (novo)

- **Os dados já não se perdem ao fechar a app.** Produtos, pedidos e fechos
  de caixa são gravados automaticamente e recarregados sempre que a app
  arranca — antes ficava tudo só em memória e perdia-se ao fechar a app.
- **Os fechos de caixa (faturação) ficam também gravados para sempre, fora
  da app**, num ficheiro de texto simples em
  `Downloads/Adsumus/fechos_caixa.txt` no tablet, visível em qualquer
  gestor de ficheiros. Cada fecho é acrescentado a este ficheiro, nunca
  substituído.
  - **Importante (limitação do Android, não da app):** "Definições >
    Aplicações > Adsumus POS > Armazenamento > Limpar armazenamento/dados"
    apaga sempre TUDO o que pertence à app — é uma proteção do próprio
    sistema operativo e nenhuma app, desta ou de qualquer developer, pode
    impedir isso. É por isso que a faturação é gravada em duplicado, fora
    da área privada da app: essa cópia pública não é afetada por essa
    operação e só desaparece se for apagada manualmente.
  - O botão "LIMPAR HISTÓRICO", nas Configurações, continua a limpar só o
    que se vê dentro da app (para começar um evento novo) — nunca apaga o
    ficheiro permanente.
- **Novo ecrã "Gerir Produtos"** (Configurações → Gerir Produtos): permite
  adicionar, editar e remover comidas, bebidas e jogos, com nome, preço e
  categoria. Aparece de imediato no ecrã de "Novo Pedido".

## O que já está pronto

- Projeto Gradle completo e a compilar (`build.gradle.kts`,
  `settings.gradle.kts`, manifest, tema preto/dourado do brasão, com
  cantos arredondados e tipografia mais trabalhada).
- **Ecrã inicial** com o logótipo em destaque e 4 cartões, todos já
  ligados aos respetivos ecrãs.
- **Novo Pedido**: separadores Comida / Bebida / Jogos, lista de produtos
  com botão "Adicionar", carrinho com quantidades editáveis e total, e
  botão "IMPRIMIR". Cada pedido impresso fica automaticamente guardado no
  histórico (mesmo que algum talão falhe, o pedido conta como feito).
- **Histórico**: lista de todos os pedidos feitos na sessão atual, mais
  recentes primeiro, com hora, artigos e total de cada um.
- **Fecho de Caixa**: totais do período atual (desde o último fecho),
  por categoria e no total geral, com botão "FECHAR CAIXA" (pede
  confirmação) e lista dos fechos já feitos na sessão.
- **Configurações**: botão para imprimir um talão de teste (confirma a
  ligação USB à impressora) e botão para limpar todo o histórico/fechos
  (pede confirmação), útil para começar de novo num evento seguinte.
- Logótipo da ADSUMUS presente em todos os ecrãs, através de uma barra
  superior partilhada (`AdsumusTopBar`) e de um componente de logótipo
  reutilizável (`AdsumusLogo`).
- Menu de exemplo em `ProductRepository.kt` (Bifana, Francesinha, Água,
  Imperial, Fichas de jogo, etc.) — **edita esta lista com os produtos e
  preços reais da associação**.
- Motor de impressão ESC/POS + gestão da ligação USB (`printer/`),
  incluindo o pedido automático de permissão USB.

## Logótipo

O logótipo real da ADSUMUS já está integrado (`app/src/main/res/drawable/logo_adsumus.png`,
fundo removido/transparente) e aparece em todos os ecrãs: no ecrã inicial
em destaque, e em tamanho pequeno na barra superior de todos os outros
ecrãs. O ícone da app (o que aparece na gaveta de apps do tablet) também
foi atualizado para usar o mesmo brasão.

## O histórico e os fechos de caixa não são guardados em disco

Por agora, o histórico de pedidos e os fechos de caixa vivem só em
memória: se a app fechar ou o tablet reiniciar, esses dados perdem-se
(o menu de produtos não é afetado, esse continua fixo no código). Isto
é suficiente para um dia de evento; se um dia for preciso manter o
histórico entre reinícios, dá-me um toque que troco por uma pequena
base de dados local.

## Próximos passos possíveis

- Persistência do menu e do histórico entre reinícios da app.
- Ecrã de edição do menu diretamente na app (sem precisar de editar
  código).

Diz-me qual destes queres a seguir e preencho da mesma forma, já dentro
deste projeto.
