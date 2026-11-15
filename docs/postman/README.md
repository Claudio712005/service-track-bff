# Coleção Postman — borda pública

Exercita a **entrada pública inteira**: `POST /autenticacao`, servida pela Lambda, e `/bff/**`,
servido por este serviço. Nenhum outro microsserviço é alcançável por gateway (`IAC-ADR-035`),
então esta coleção cobre tudo que um cliente externo consegue chamar.

```
service-track-bff.postman_collection.json     17 requisições em 6 pastas,
                                              mais a pasta Apresentação com 35 em 3 cenários
ambientes/local.postman_environment.json      compose ou kind
ambientes/hml.postman_environment.json        modelo, sem valor volátil
ambientes/prd.postman_environment.json        modelo, sem valor volátil
```

## O prefixo `/bff` é a única diferença de caminho entre ambientes

A borda remove o prefixo antes de chamar a aplicação: a requisição vai para
`<gateway>/bff/clientes` e o BFF recebe `/clientes`. No local não há gateway, e o caminho é
`/clientes` direto.

A variável `prefixoBff` cobre isso — `/bff` em `hml` e `prd`, vazia no `local`. Toda requisição
usa `{{baseUrl}}{{prefixoBff}}/...`, menos o login, que fica na raiz do gateway e não leva
prefixo.

## Ambiente local

Importe `ambientes/local.postman_environment.json` e use como está. `baseUrl` aponta para
`http://localhost:8083`, que é a porta do BFF no compose da plataforma (`aws-iac`, `compose/`).
No kind, use `kubectl port-forward` e ajuste a porta.

**Não existe Lambda no local.** A pasta Autenticação não funciona, e não precisa: o overlay sobe
o BFF com `ST_BFF_JWT_ENABLED=false` e as rotas respondem sem token.

## Ambientes hml e prd

Os arquivos versionados são **modelos com os valores voláteis vazios**, de propósito: `baseUrl` e
`apiKey` mudam a cada recriação do ambiente, e chave de API não entra em git.

Gere o arquivo preenchido:

```bash
scripts/gerar-ambiente-postman.sh hml        # consumidor web, o padrão
scripts/gerar-ambiente-postman.sh hml ci     # ou mobile
```

Ele lê a URL de `/servicetrack/<env>/api/base-url` no SSM e a chave do API Gateway, e escreve
`ambientes/<env>.gerado.json` — que está no `.gitignore`. Importe **esse** arquivo no Postman,
não o modelo.

**Importar o modelo versionado é a causa mais comum de `403` em tudo.** A `apiKey` dele vem vazia
de propósito, e o gateway recusa toda requisição sem chave **antes** de chegar na aplicação. Com
a chave certa e sem token a resposta é `401`, não `403` — é assim que se distinguem os dois.

Depois de cada recriação de ambiente, gere de novo. Se a chamada responder `403` sem explicação,
quase sempre é a chave velha.

## Como usar

1. Importe a coleção e o ambiente **gerado**
2. Preencha `senha` — é o único campo que falta, e ela não é versionada aqui
3. Rode **Autenticação → Entrar e guardar o token**: o teste grava o token no ambiente ativo
4. As demais requisições usam esse token como Bearer, pela autenticação da coleção

**Os identificadores já vêm preenchidos, porque vêm de seed e não de `apply`.** `cpf`,
`clienteId`, `mecanicoId` e `veiculoId` são do baseline do `service-track-usuarios-veiculos`;
`insumoId`, `insumoComSaldo` e `insumoSemSaldo` são do baseline do `service-track-catalogo`. São
ids fixos: sobrevivem a recriação de ambiente porque a esteira Banco reaplica o mesmo seed. A
senha do usuário de demonstração está documentada no README do serviço de usuários — ela não
entra em git aqui.

`ordemId` é preenchido sozinho quando você abre uma ordem.

As rotas da pasta **Saúde** são abertas no BFF: não exigem token e ignoram a autenticação da
coleção.

## O que esperar em cada resposta

| Situação | Resposta |
|---|---|
| sem `x-api-key` em hml/prd | `403` do gateway, antes de chegar na aplicação |
| chave válida, sem token | `401` do BFF |
| rota fora do contrato de borda | `403` do gateway |
| login sem corpo | `400`, validado no gateway pelo JSON Schema |
| dependência fora do ar | `503` com `ServicoIndisponivelException` |
| disjuntor aberto | `503`, sem tentar a dependência |

Medido em `hml` em 01/11/2026.

## Ordem de aprovação do orçamento

`POST /ordens/{id}/orcamento/aprovacao` **não** avança o estado da OS por si. Ela dispara a saga
de reserva de insumo, e quem transiciona é a confirmação da saga (`GLOBAL-ADR-007`). Para ver o
efeito, consulte **Sagas da ordem** e **Histórico da ordem** depois de aprovar.

Se o consumo for recusado por falta de saldo, a OS fica em `EM_EXECUCAO` com uma linha de
bloqueio no histórico — não há compensação, porque baixar o reservado é irreversível.

## A pasta Apresentação

Três cenários, cada um autossuficiente, na ordem em que entram na gravação:

| Pasta | O que prova | Desfecho |
|---|---|---|
| **1 · OS completa** | as duas sagas da OS confirmando | `FINALIZADA` |
| **2 · Saga infeliz** | o catálogo responde **não**: compensação do que já havia confirmado | `CANCELADA` |
| **3 · Saga triste** | **ninguém** responde: o prazo da etapa vence e a varredura compensa | `CANCELADA` |

Cada pasta abre a própria OS e termina conferindo o saldo do insumo no catálogo. **É essa última
conferência que prova a saga**: o efeito — e a ausência dele, depois da compensação — está no
banco do serviço dono do dado, não num log do orquestrador.

### Um comando deixa o ambiente pronto

```bash
ST_SENHA_DEMO='<senha do seed>' scripts/preparar-apresentacao.sh hml --esperar
```

Com `--esperar`, ele aguarda a esteira **Orquestrar ambiente** terminar — e se nenhuma estiver em
curso, espera uma começar. Depois:

1. lê a URL da borda no SSM e a chave do consumidor no API Gateway;
2. confere que o BFF responde `readiness` pela borda;
3. **faz login e grava o token** no ambiente, para a pasta Apresentação rodar sem passar pela
   Autenticação;
4. **confere que o gatilho da saga está armado** — `OL-20W50-MIN-1L` com disponível zero — e
   avisa alto se não estiver, porque aí o cenário da recusa não recusa;
5. escreve `ambientes/<env>.gerado.json` e lista o que ficou sem valor.

Sem `ST_SENHA_DEMO` ele prepara tudo menos o token. A senha está documentada no README do
`service-track-usuarios-veiculos` e não é versionada aqui.

**O que o script não faz:** o port-forward. Ele fica no seu terminal:

```bash
kubectl -n service-track-ordens port-forward svc/service-track-ordens 18082:80
```

### Por que parte das requisições não passa pela borda

Diagnóstico, insumo, orçamento e finalização **não existem no BFF**. A borda pública tem duas
rotas e nada mais (`IAC-ADR-035`), e o BFF só abre e aprova a ordem. Montar a OS é conversa com o
serviço que é dono dela, e por isso essas requisições vão direto ao `ordens`, pelo port-forward.
É fronteira desenhada, não falta de rota — vale uma frase na narração.

### As requisições de saga pedem reenvio

A OUTBOX publica a cada 2 s, então a situação muda em segundos. Os testes dessas chamadas
conferem o formato e **imprimem a situação de cada passo no console**, sem reprovar enquanto a
saga está em curso. Quem reprova é a conferência de status que vem depois.

### O cenário 3 mexe no ambiente, e desfaz

Ele exige derrubar o consumidor antes e repor depois:

```bash
kubectl -n service-track-catalogo scale deploy/service-track-catalogo --replicas=0
kubectl -n service-track-catalogo scale deploy/service-track-catalogo --replicas=1
```

O prazo da etapa é de **2 minutos** por padrão, com varredura a cada 30 s. Para gravar sem corte
longo, baixe os dois no overlay antes de subir:

```
SERVICETRACK_MESSAGING_SAGA_STEP_DEADLINE=20s
SERVICETRACK_MESSAGING_SAGA_DEADLINE_SWEEP_INTERVAL=10s
```

Quando o catálogo volta, ele encontra o `ReservarEstoque` antigo no tópico e reserva estoque para
uma OS já cancelada. A confirmação atrasada é tratada pelo estado da etapa, e a reserva órfã cai
pelo `expiraEm` de 10 minutos — a rede de segurança do catálogo. Vale narrar: é desenho.
