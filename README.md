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

Documentação em `/swagger-ui.html`, contrato em `/v3/api-docs`.

**Este é o primeiro corte, de propósito.** Só a integração com `service-track-usuarios-veiculos`
existe. Catálogo e ordens entram depois, e o motivo de começar pequeno é provar a canalização —
token, correlação e a API interna — antes de multiplicar rotas.

### A composição que importa

`GET /clientes/{id}` faz duas chamadas ao serviço de usuários: o cliente e os veículos dele.
Se a de veículos falhar, **a resposta sai com o cliente e `veiculosIndisponiveis: true`** em vez
de falhar inteira. Se a do cliente falhar, a requisição falha — sem cliente não há resposta.

É a regra geral: degrada o que é acessório, propaga o que é essencial.

---

## Arquitetura

Hexagonal enxuta. A porta que importa é a de **saída**; portas de entrada foram omitidas de
propósito, porque uma interface por caso de uso, com uma implementação só, é cerimônia sem
leitor.

```
domain/               modelos de leitura, sem framework
  port/               UsuariosPort — o contrato de saída
  exception/          BffException e as quatro filhas
application/          ClientesService, MecanicosService
infra/
  web/                controllers, GlobalExceptionHandler, CorrelationFilter
  usuarios/           UsuariosHttpAdapter e os DTOs do contrato alheio
  config/             ResilienceConfig, WebClientConfig, CorrelationContextConfig
```

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

**Em reativo isso não sai de graça.** MDC é `ThreadLocal` e Reactor troca de thread entre
operadores: a thread que atende a requisição não é a que executa a chamada HTTP de saída. A
propagação é feita escrevendo a correlação no contexto do Reactor e registrando um
`MdcThreadLocalAccessor` por chave no `ContextRegistry`, com
`Hooks.enableAutomaticContextPropagation()`.

Três testes guardam isso, e o terceiro existe para o primeiro não dar falso positivo:

1. o `correlationId` enviado aparece no `MDCPropertyMap` da linha de acesso do filtro;
2. **o mesmo `correlationId` aparece no log do `UsuariosHttpAdapter`**, que roda na thread do
   cliente HTTP, e o `requestId` é o mesmo nas duas pontas;
3. as threads registradas nos dois logs são **comprovadamente diferentes** — sem isso, o teste
   2 passaria por acidente se tudo rodasse numa thread só.

Métricas em `/actuator/prometheus`, saúde em `/actuator/health`.

---

## Configuração

| Variável | Padrão | Para quê |
|---|---|---|
| `ST_BFF_USUARIOS_URL` | `http://localhost:8081` | base do serviço de usuários. Em AWS, a URL da API Gateway privada |
| `ST_BFF_CONNECT_TIMEOUT` | `1s` | timeout de conexão |
| `ST_BFF_RESPONSE_TIMEOUT` | `2s` | timeout de resposta |
| `ST_BFF_MAX_ATTEMPTS` | `3` | tentativas por chamada |
| `ST_BFF_INITIAL_BACKOFF` | `200ms` | primeira espera do backoff |
| `ST_BFF_BACKOFF_MULTIPLIER` | `2.0` | fator do backoff |
| `ST_BFF_MAX_BACKOFF` | `2s` | teto da espera |
| `ST_BFF_FAILURE_RATE_THRESHOLD` | `50` | percentual de falha que abre o disjuntor |
| `ST_BFF_WAIT_DURATION_IN_OPEN_STATE` | `10s` | quanto tempo o disjuntor fica aberto |
| `ST_BFF_LOG_FORMAT` | `logstash` | vazio para texto legível no local |

---

## Rodar e testar

```bash
./gradlew bootRun        # exige o servico de usuarios em ST_BFF_USUARIOS_URL
./gradlew test
```

15 testes, com `StepVerifier` em tudo que é reativo: casos de uso contra uma porta falsa,
adaptador contra uma `ExchangeFunction` de mentira — que é o que permite contar tentativas de
retry sem subir servidor — e a propagação de correlação contra o contexto real da aplicação.

---

## O que ainda não existe

Nada disto é esquecimento; é a fatia seguinte.

- **Autenticação.** O BFF ainda não valida nem repassa o JWT. Os serviços por trás validam, e a
  decisão é que continuem validando — o BFF repassa o token, não substitui a verificação.
- **Integração com catálogo e com ordens.**
- **Dockerfile, manifestos `k8s/`, esteiras e `infra/terraform`.**
- **Rota no API Gateway público**, que é onde o WAF e a chave de API vivem.
- **Portão de cobertura.** A Fase 4 exige 80% por serviço com evidência no README.

---

## Fronteiras

**É dono de:** a forma da resposta pública, a composição entre serviços, e a tradução de erro de
dependência em erro de cliente.

**Não é dono e nunca será:** regra de negócio, dado persistido, schema de ninguém. Precisou de
dado: chama a API do dono. **Nunca o banco.**
