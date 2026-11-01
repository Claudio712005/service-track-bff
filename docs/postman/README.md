# Coleção Postman — borda pública

Exercita a **entrada pública inteira**: `POST /autenticacao`, servida pela Lambda, e `/bff/**`,
servido por este serviço. Nenhum outro microsserviço é alcançável por gateway (`IAC-ADR-035`),
então esta coleção cobre tudo que um cliente externo consegue chamar.

```
service-track-bff.postman_collection.json     17 requisições em 6 pastas
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

Ele lê a URL de `/servicetrack/<env>/api/base-url` no SSM e a chave do API Gateway, consulta o
cluster para preencher `insumoId`, `servicoId`, `clienteId`, `mecanicoId` e `veiculoId`, e escreve
`ambientes/<env>.gerado.json` — que está no `.gitignore`. Importe **esse** arquivo no Postman, não
o modelo.

**Importar o modelo versionado é a causa mais comum de `403` em tudo.** Ele tem `apiKey` vazia de
propósito, e o gateway recusa toda requisição sem a chave, antes de chegar na aplicação. Se a
chave estiver certa e faltar o token, a resposta é `401`, não `403` — essa é a forma de
distinguir os dois.

Depois de cada recriação de ambiente, gere de novo. Se a chamada responder `403` sem explicação,
quase sempre é a chave velha.

## Como usar

1. Importe a coleção e o ambiente **gerado**
2. Preencha `cpf` e `senha` de um usuário que exista no serviço de usuários — ver abaixo
3. Rode **Autenticação → Entrar e guardar o token**: o teste grava o token no ambiente ativo
4. As demais requisições usam esse token como Bearer, pela autenticação da coleção

`insumoId` e `servicoId` o gerador preenche: o baseline do catálogo semeia insumos e serviços.
`ordemId` é preenchido sozinho quando você abre uma ordem.

## Não existe usuário em um ambiente novo, e isso bloqueia o login

O baseline do `service-track-usuarios-veiculos` **não semeia nenhum usuário**, ao contrário do
catálogo. E não há caminho público para criar um: o serviço não fica atrás do gateway
(`IAC-ADR-035`) e o BFF só lê — não tem `POST` para cliente nem para mecânico.

Então, num ambiente recém-criado, a pasta Autenticação não tem com quem funcionar, e o gerador
avisa isso. Para destravar, crie um usuário por dentro do cluster:

```bash
kubectl -n service-track-usuarios-veiculos port-forward \
  svc/service-track-usuarios-veiculos 18081:80 &

curl -sS -X POST http://localhost:18081/usuarios \
  -H 'Content-Type: application/json' \
  -d '{
        "documento": "52998224725",
        "nome": "Joana Ferreira Lima",
        "email": "joana.lima@oficina.com.br",
        "senha": "troque-esta-senha",
        "tipoDeUsuario": "CLIENTE",
        "telefone": "11988887777"
      }'
```

O documento é a chave de login — CPF de 11 dígitos, sem pontuação, com dígito verificador
válido. Repita com `"tipoDeUsuario": "MECANICO"` e outro CPF para poder abrir ordem.

Depois disso, rode o gerador de novo: ele passa a preencher `clienteId`, `mecanicoId` e
`veiculoId` sozinho.

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
