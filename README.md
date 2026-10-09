# service-track-bff

Backend for Frontend do ServiceTrack: **a única entrada pública da plataforma**. Compõe
respostas a partir dos microsserviços e não guarda nada.

Sem banco, sem cache, sem estado. No minuto em que este repositório ganhar persistência, ele
virou serviço de domínio com o nome errado — e a decisão de ter **um** BFF deixa de ser
reversível.

**Reativo de ponta a ponta.** `Mono` e `Flux` do controller até o WebClient, sem ponte de
coroutine e sem nenhum `block()`. Não há `suspend` em lugar nenhum do caminho.

---

## O que este serviço expõe

| Método | Rota | Para quê |
|---|---|---|
| `GET` | `/clientes` | lista clientes |
| `GET` | `/clientes/{id}` | cliente **com os veículos dele**, em duas chamadas compostas |
| `GET` | `/mecanicos` | lista mecânicos |
| `GET` | `/mecanicos/{id}` | mecânico por identificador |
| `GET` | `/catalogo/servicos` | serviços **ativos** que podem entrar num orçamento |
| `GET` | `/catalogo/insumos` | insumos ativos |
| `GET` | `/catalogo/insumos/{id}/saldo` | saldo do insumo — é o que diz se vale pedir a reserva |
| `POST` | `/ordens` | abre uma ordem de serviço |
| `GET` | `/ordens` | ordens de um cliente, por `clienteId` |
| `GET` | `/ordens/{id}` | ordem **composta**: cliente, mecânico, veículo, serviços e insumos resolvidos |
| `POST` | `/ordens/{id}/orcamento/aprovacao` | aprova o orçamento e dispara a saga de reserva |
| `GET` | `/ordens/{id}/historico` | linha do tempo da ordem |
| `GET` | `/ordens/{id}/saga` | progresso da transação distribuída |

Documentação em `/swagger-ui.html`, contrato em `/v3/api-docs`. **A documentação vive nos ports
de entrada**, não nos controllers: quem lê o contrato lê uma interface, e o controller fica com
uma linha por rota.

### A composição que importa

`GET /ordens/{id}` é o que justifica existir um BFF. Ele faz **cinco chamadas em paralelo** —
cliente, mecânico, veículo, serviços e insumos — e devolve a ordem com nome em vez de
identificador.

**Serviço indisponível não derruba a resposta.** O campo vem nulo e o nome do serviço aparece em
`parciaisIndisponiveis`, para o cliente saber que a resposta está incompleta em vez de achar que
o dado não existe:

```json
{
  "ordem": { "itensInsumo": [{ "insumoId": "018f30bb-…", "insumo": null, "quantidade": 2 }] },
  "cliente": { "nome": "Joana Ferreira" },
  "veiculo": null,
  "parciaisIndisponiveis": ["catalogo"]
}
```

O identificador **nunca** se perde: só o nome. Quem só precisa do id continua funcionando com o
catálogo fora do ar.

`GET /clientes/{id}` segue a mesma regra em escala menor: se a chamada de veículos falhar, a
resposta sai com o cliente e `veiculosIndisponiveis: true`. Se a do cliente falhar, a requisição
falha — sem cliente não há resposta.

A regra geral: **degrada o que é acessório, propaga o que é essencial.** Erro que não é
indisponibilidade sobe — 404 de uma dependência é 404 aqui, não resposta parcial.

### A aprovação não avança o estado, e a documentação diz isso

`POST /ordens/{id}/orcamento/aprovacao` responde com a ordem ainda em `AGUARDANDO_APROVACAO`.
Ela só vai para `EM_EXECUCAO` quando o catálogo confirmar a reserva de todos os insumos, o que
acontece pela fila. Quem chama acompanha em `GET /ordens/{id}/saga`.

Cliente que assume "aprovei, logo está em execução" quebra — por isso está escrito no Swagger da
rota, não só aqui.

---

## Arquitetura

Hexagonal enxuta. A porta que importa é a de **saída**; portas de entrada foram omitidas de
propósito, porque uma interface por caso de uso, com uma implementação só, é cerimônia sem
leitor.

```
domain/
  model/              Pessoa, Veiculo, ClienteVeiculos, TipoDeUsuario
  exception/          BffException e as quatro filhas, um arquivo por tipo
application/
  port/in/            ClienteApiPort, MecanicoApiPort — o contrato HTTP e a documentação
  port/out/           UsuariosPort — o que a aplicação precisa do mundo
  service/            ClientesService, MecanicosService
infra/
  web/controller/     ClienteController, MecanicoController — só implementam o port
  web/error/          GlobalExceptionHandler, ErrorResponse
  web/filter/         CorrelationFilter, MdcThreadLocalAccessor
  client/usuarios/    UsuariosApiClient + UsuariosWebClient (transporte),
                      UsuariosHttpAdapter (resiliência e tradução), DTOs, propriedades
  client/fake/        UsuariosFakeAdapter — perfil dev
  config/             OpenApi, Resilience, WebClient, ReactorContext
```

### Três ports, três fronteiras

| Port | Fronteira | Quem implementa |
|---|---|---|
| `ClienteApiPort`, `MecanicoApiPort` | **entrada HTTP** — rotas, status e documentação OpenAPI vivem aqui | os controllers, que ficam sem anotação de rota |
| `UsuariosPort` | **o que a aplicação precisa**, em termos de domínio | `UsuariosHttpAdapter` em produção, `UsuariosFakeAdapter` no perfil dev |
| `UsuariosApiClient` | **transporte HTTP puro**, em termos do contrato alheio | `UsuariosWebClient` |

A separação entre `UsuariosApiClient` e `UsuariosHttpAdapter` é o que permite testar resiliência
sem servidor: o teste do adaptador conta tentativas com um cliente falso, e o teste do
`UsuariosWebClient` confere que a URL e o parâmetro `tipo` saem certos.

### Convenção de nomes

**Português é exclusivo do domínio.** A linguagem ubíqua nomeia o que o negócio reconhece —
`Pessoa`, `Veiculo`, `ClienteVeiculos`, `TipoDeUsuario`, e as exceções de domínio. Fora do
domínio, tudo em inglês: nome de pacote, classe de configuração, propriedade, variável de
ambiente e sufixo técnico.

| Camada | Língua | Exemplo |
|---|---|---|
| `domain` | português | `Pessoa`, `RecursoNaoEncontradoException` |
| `application` | português do domínio + sufixo técnico | `ClientesService` |
| `infra` | inglês | `ResilienceConfig`, `MdcThreadLocalAccessor`, `CorrelationFilter` |
| properties e env | inglês | `servicetrack.resilience.max-attempts`, `ST_BFF_MAX_ATTEMPTS` |

O `dominio` não conhece Spring. Trocar o transporte de saída — HTTP hoje, outra coisa amanhã —
é escrever outro adaptador da `UsuariosPort`, sem tocar em caso de uso.

**DTO de fora não entra no domínio.** `UsuarioResponse` e `VeiculoResponse` descrevem o contrato
do outro serviço e morrem no adaptador; o que circula é `Pessoa` e `Veiculo`.

---

## Resiliência

Três controles, nesta ordem, em volta de toda chamada de saída:

| Controle | Configuração | Por quê |
|---|---|---|
| **Timeout** | 1s para conectar, 2s para responder | é o único controle que não depende de estado acumulado |
| **Retry com espera exponencial** | 3 tentativas, 200ms × 2, teto de 2s | leitura é idempotente; repetir é seguro e resolve falha transitória |
| **Circuit breaker** | janela de 20, mínimo de 10 chamadas, 50% de falha, 10s aberto | para de insistir quando a dependência está doente de verdade |

**O retry só dispara em falha transitória.** `404` vira `RecursoNaoEncontradoException` e **não
é retentado** — insistir num recurso que não existe é desperdício garantido. Verificado em
teste: 503 gera 3 chamadas, 404 gera 1.

O disjuntor fica **mais perto da chamada** do que o retry, e `CallNotPermittedException` não é
retentada: com o circuito aberto, a resposta é imediata.

Retry e disjuntor são adequados aqui, e **não** seriam numa função Lambda, onde o estado do
disjuntor morre com o contêiner. A diferença é ter processo longo.

---

## Exceções padronizadas

Uma hierarquia `sealed`, traduzida em um lugar só (`GlobalExceptionHandler`):

| Exceção | HTTP |
|---|---|
| `RecursoNaoEncontradoException` | `404` |
| `RequisicaoInvalidaException` | `400` |
| `NaoAutorizadoException` | `401` |
| `ServicoIndisponivelException` | `503` |
| `CallNotPermittedException` (disjuntor aberto) | `503` |
| qualquer outra | `500`, com a causa no log e não na resposta |

O corpo de erro é sempre o mesmo, e **carrega a correlação** — então o que o cliente vê na tela
é pesquisável no log:

```json
{
  "status": 503,
  "erro": "Service Unavailable",
  "mensagem": "Servico usuarios indisponivel",
  "rota": "/clientes",
  "correlationId": "...",
  "requestId": "..."
}
```

---

## Observabilidade

Padrão de log do `GLOBAL-ADR-006`, com os cinco campos entre colchetes:

```
[service-track-bff,<traceId>,<spanId>,<correlationId>,<requestId>]
```

**A fonte de verdade é o contexto do Reactor, não o MDC.** O filtro escreve a correlação com
`contextWrite`, e quem precisa dela lê do contexto: o tradutor de erro usa `deferContextual`, e
a propagação do cabeçalho para o serviço de destino também. **Só um arquivo no projeto encosta
em `MDC`**, o `MdcThreadLocalAccessor` — que existe porque o logback só lê `ThreadLocal`, e é a
ponte oficial do Micrometer entre contexto e `ThreadLocal`.

Por que não baggage, que seria o caminho sem ponte nenhuma: **medido três vezes, não funciona
aqui.** Com `management.tracing.baggage.correlation.fields` configurado e o
`Slf4JBaggageEventListener` no classpath, o baggage fica populado dentro do filtro
(`tracer.allBaggage` mostra os dois campos), mas o MDC chega nulo rio abaixo — o escopo de
baggage é `ThreadLocal` e fecha antes de a cadeia reativa executar.

As duas primeiras medições rodaram sem `@AutoConfigureTracing`, e **isso invalidaria o
resultado**: o suporte de teste do Spring Boot desliga o export de trace, e as beans de
propagação são `@ConditionalOnEnabledTracingExport`, então o propagador cai para
`NoopTextMapPropagator`. Repeti o experimento **com tracing ligado de verdade** em 06/10/2026 e
o baggage continuou não chegando ao MDC. A conclusão vale; a primeira justificativa estava
apoiada num arnês quebrado.

Os testes de correlação rodam com `@AutoConfigureTracing` justamente por isso: sem ele, o
`traceId` e o `spanId` do padrão de log são sempre nulos e o teste não exercita tracing nenhum.

Três testes guardam isso, e o terceiro existe para o primeiro não dar falso positivo:

1. o `correlationId` enviado aparece no `MDCPropertyMap` da linha de acesso do filtro;
2. **o mesmo `correlationId` aparece no log do `UsuariosHttpAdapter`**, que roda na thread do
   cliente HTTP, e o `requestId` é o mesmo nas duas pontas;
3. as threads registradas nos dois logs são **comprovadamente diferentes** — sem isso, o teste
   2 passaria por acidente se tudo rodasse numa thread só;
4. o corpo de erro carrega o `correlationId` lido do contexto, o que prova que o contexto chega
   até o tradutor de exceção;
5. a chamada de saída leva `X-Correlation-Id`, lido do contexto, e **não** inventa o cabeçalho
   quando o contexto não tem correlação.

A correlação **sai** nas chamadas de saída como `X-Correlation-Id`, que é o cabeçalho que
catálogo e usuários já leem. Antes disso o BFF recebia correlação e não a repassava, então o
rastro morria na fronteira.

Métricas em `/actuator/prometheus`, saúde em `/actuator/health`.

> **Não há `application.yaml` em `src/test/resources`.** Arquivo de teste com o mesmo nome
> **substitui** o de produção em vez de complementar, e isso já esconde defeito duas vezes neste
> projeto: a configuração real deixa de ser exercitada. Override de teste vai em
> `@SpringBootTest(properties = [...])`.

---

## Configuração

| Variável | Padrão | Para quê |
|---|---|---|
| `ST_BFF_USUARIOS_URL` | `http://localhost:8081` | base do serviço de usuários. Em AWS, a URL da API Gateway privada |
| `ST_BFF_CATALOGO_URL` | `http://localhost:8080` | base do catálogo |
| `ST_BFF_ORDENS_URL` | `http://localhost:8082` | base do serviço de ordens |
| `ST_BFF_CONNECT_TIMEOUT` | `1s` | timeout de conexão |
| `ST_BFF_RESPONSE_TIMEOUT` | `2s` | timeout de resposta |
| `ST_BFF_ORDENS_RESPONSE_TIMEOUT` | `5s` | timeout de resposta do serviço de ordens, maior porque ele escreve em banco e enfileira |
| `ST_BFF_JWT_ENABLED` | `true` | validação de JWT. **Desligar só fora de ambiente compartilhado** |
| `ST_BFF_JWT_PUBLIC_KEY` | `classpath:publicKey.pem` | PEM da chave pública emitida junto com a Lambda |
| `ST_BFF_MAX_ATTEMPTS` | `3` | tentativas por chamada |
| `ST_BFF_INITIAL_BACKOFF` | `200ms` | primeira espera do backoff |
| `ST_BFF_BACKOFF_MULTIPLIER` | `2.0` | fator do backoff |
| `ST_BFF_MAX_BACKOFF` | `2s` | teto da espera |
| `ST_BFF_FAILURE_RATE_THRESHOLD` | `50` | percentual de falha que abre o disjuntor |
| `ST_BFF_WAIT_DURATION_IN_OPEN_STATE` | `10s` | quanto tempo o disjuntor fica aberto |
| `ST_BFF_LOG_FORMAT` | `logstash` | vazio para texto legível no local |

---

## Cobertura

Portão no build: `./gradlew check` falha abaixo do mínimo. Medido em 08/10/2026, com 72 testes:

| Métrica | Atual | Mínimo |
|---|---|---|
| Linha | **94,7%** | 80% |
| Instrução | **91,5%** | 80% |
| Ramo | 75,8% | 60% |

O mínimo de ramo é 60% porque tradução de erro tem muitos caminhos de exceção que não compensa
exercitar um a um; linha e instrução ficam acima do exigido pela fase.

O portão fez o trabalho dele durante esta etapa: ao entrar a integração do catálogo a cobertura
caiu para 61%, e o build reprovou até os testes existirem. Nenhum número aqui foi ajustado para
caber.

## Rodar e testar

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'   # sem dependencia nenhuma
./gradlew bootRun                                         # exige o servico de usuarios de pe
./gradlew test
```

### Perfil `dev`: serviço de usuários mocado

`UsuariosFakeAdapter` implementa a `UsuariosPort` em memória e **substitui** a saída HTTP — o
`UsuariosHttpAdapter`, o `UsuariosWebClient` e o `WebClientConfig` são `@Profile("!dev")`, então
em dev nada sai pela rede e a aplicação sobe sozinha. O log avisa na subida.

Os dados foram escolhidos para exercitar os caminhos, não para parecerem bonitos:

| Identificador | O que exercita |
|---|---|
| `1111...1111` | cliente com dois veículos — o caminho feliz da composição |
| `2222...2222` | cliente cuja consulta de veículos **falha** — a degradação com `veiculosIndisponiveis: true` |
| `3333...3333` | mecânico — responde 404 na rota de cliente, 200 na de mecânico |
| `9999...9999` | identificador que devolve indisponibilidade — o caminho de 503 |

Quatro testes rodam contra esse perfil, então o mock não apodrece em silêncio.

25 testes, com `StepVerifier` em tudo que é reativo: casos de uso contra uma porta falsa,
adaptador contra uma `ExchangeFunction` de mentira — que é o que permite contar tentativas de
retry sem subir servidor — e a propagação de correlação contra o contexto real da aplicação.

---

## O que ainda não existe

Nada disto é esquecimento; é a fatia seguinte.

- **Layout `software/`.** Os outros três serviços têm o Gradle sob `software/`; aqui ele está na
  raiz. Os moldes de Dockerfile e de esteira contam com o primeiro, então a mudança entra junto
  com eles.
- **Nada de infraestrutura.** Dockerfile, `k8s/`, esteiras, `infra/terraform` e a rota no
  gateway público existem desde 09/10/2026.

---

## Como se chega até aqui

O BFF é a única entrada pública, e o caminho tem três saltos:

```
cliente → API Gateway público → VPC Link → NLB interno:30083 → NodePort → pod
```

Duas coisas que surpreendem:

1. **O prefixo `/bff` é removido no caminho.** O gateway expõe `/bff/{proxy+}` e entrega
   `/clientes` ao serviço. Este serviço **não sabe** que está atrás de um prefixo, e não deve
   passar a saber: o prefixo é decisão da borda.
2. **A rota pública só existe quando há NLB.** Com `habilitar_api_interna` desligada, os paths
   `/bff` desaparecem do contrato do gateway em vez de serem publicados apontando para um
   balanceador que não existe (`IAC-ADR-034`).

O gateway exige **chave de API**; o **JWT continua sendo validado aqui**. São duas barreiras com
propósitos diferentes: a chave identifica o consumidor e aplica o plano de uso, o token
identifica o usuário.

**O BFF não entra na lista da API interna.** Aquela lista é por onde o BFF chama catálogo,
usuários e ordens — a direção oposta. Ele só ganha um alvo no mesmo NLB.

---

## Autenticação

O BFF **valida** o JWT e **repassa** o token recebido para os serviços de trás, que continuam
validando por conta própria. O BFF não substitui a verificação de ninguém: ele é a única entrada
pública, e um serviço que confie cegamente em quem o chama fica vulnerável a qualquer coisa que
entre na rede privada.

| O quê | Como |
|---|---|
| validação | `oauth2-resource-server` reativo, RS256, chave pública em PEM — a mesma que a Lambda emite |
| repasse | o filtro de entrada põe o `Authorization` no contexto do Reactor; o de saída o reenvia |
| rotas abertas | só `/actuator/health`, `/actuator/info`, `/actuator/prometheus` e o contrato OpenAPI |

Sonda de saúde e documentação ficam abertas porque sonda não tem token e documentação sem
contrato visível não serve para nada.

**Sem a chave, a aplicação não sobe** — e a mensagem diz o que fazer. É deliberado: subir com
validação silenciosamente desligada é pior que não subir. Para desligar é preciso dizer
`ST_BFF_JWT_ENABLED=false`, e aí o log avisa em `WARN` a cada start.

O teste gera um par RSA em tempo de execução e assina o token: **nenhuma chave está versionada.**
Cobre credencial ausente, assinada por outra chave, expirada, e válida. Verificado com controle
negativo — abrindo as rotas, o teste falha.

---

## Fronteiras

**É dono de:** a forma da resposta pública, a composição entre serviços, e a tradução de erro de
dependência em erro de cliente.

**Não é dono e nunca será:** regra de negócio, dado persistido, schema de ninguém. Precisou de
dado: chama a API do dono. **Nunca o banco.**
