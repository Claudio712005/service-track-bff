#!/usr/bin/env bash
set -euo pipefail

AMBIENTE="${1:-}"
CONSUMIDOR="${CONSUMIDOR:-web}"
REGIAO="${AWS_REGION:-us-east-1}"
REPO_PLATAFORMA="${REPO_PLATAFORMA:-Claudio712005/service-track-aws-iac}"
ESTEIRA="${ESTEIRA:-orquestrar-ambiente.yml}"
ESPERA_MAXIMA_MIN="${ESPERA_MAXIMA_MIN:-90}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODELO="$RAIZ/docs/postman/ambientes/$AMBIENTE.postman_environment.json"
SAIDA="$RAIZ/docs/postman/ambientes/$AMBIENTE.gerado.json"

ESPERAR=0
for argumento in "${@:2}"; do
  [ "$argumento" = "--esperar" ] && ESPERAR=1
done

if [[ "$AMBIENTE" != "hml" && "$AMBIENTE" != "prd" ]]; then
  cat >&2 <<'USO'
uso: preparar-apresentacao.sh <hml|prd> [--esperar]

  --esperar   aguarda a esteira Orquestrar ambiente terminar antes de preparar.
              Se nenhuma estiver em curso, espera uma comecar.

variaveis:
  ST_SENHA_DEMO   senha do usuario do seed. Sem ela o token nao e emitido e o
                  ambiente sai preenchido, mas sem token.
  CONSUMIDOR      web (padrao), mobile ou ci.
USO
  exit 2
fi

[ -f "$MODELO" ] || { echo "::error::modelo ausente: $MODELO" >&2; exit 1; }

aviso() { echo "  [aviso] $*"; }
passo() { echo ""; echo "==> $*"; }

if [ "$ESPERAR" -eq 1 ]; then
  passo "Esperando a esteira $ESTEIRA em $REPO_PLATAFORMA"
  LIMITE=$(( $(date +%s) + ESPERA_MAXIMA_MIN * 60 ))
  EXECUCAO=""
  while [ -z "$EXECUCAO" ]; do
    EXECUCAO="$(gh run list --repo "$REPO_PLATAFORMA" --workflow="$ESTEIRA" \
      --status in_progress --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)"
    [ -z "$EXECUCAO" ] && EXECUCAO="$(gh run list --repo "$REPO_PLATAFORMA" --workflow="$ESTEIRA" \
      --status queued --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)"
    if [ -z "$EXECUCAO" ]; then
      [ "$(date +%s)" -ge "$LIMITE" ] && {
        echo "::error::nenhuma execucao em $ESPERA_MAXIMA_MIN minutos. Dispare a esteira." >&2
        exit 1
      }
      sleep 30
    fi
  done
  echo "  execucao $EXECUCAO em curso, acompanhando"
  if ! gh run watch "$EXECUCAO" --repo "$REPO_PLATAFORMA" --exit-status >/dev/null 2>&1; then
    aviso "a esteira nao terminou com sucesso. Preparando o que for possivel."
  else
    echo "  esteira concluida com sucesso"
  fi
fi

passo "Credenciais da AWS"
if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "::error::credenciais invalidas ou expiradas. Rode aws-student-login.sh no aws-iac." >&2
  exit 1
fi
echo "  conta $(aws sts get-caller-identity --query Account --output text)"

passo "URL da borda e chave do gateway"
URL="$(aws ssm get-parameter --name "/servicetrack/$AMBIENTE/api/base-url" \
  --region "$REGIAO" --query Parameter.Value --output text 2>/dev/null || true)"
if [ -z "$URL" ] || [ "$URL" = "None" ]; then
  echo "::error::a URL de $AMBIENTE nao esta no SSM. O ambiente subiu?" >&2
  exit 1
fi

ID_CHAVE="$(aws apigateway get-api-keys --region "$REGIAO" --include-values \
  --query "items[?name=='servicetrack-$AMBIENTE-$CONSUMIDOR'].id" --output text 2>/dev/null || true)"
if [ -z "$ID_CHAVE" ] || [ "$ID_CHAVE" = "None" ]; then
  echo "::error::consumidor '$CONSUMIDOR' nao existe em $AMBIENTE." >&2
  exit 1
fi
CHAVE="$(aws apigateway get-api-key --api-key "$ID_CHAVE" --include-value \
  --region "$REGIAO" --query value --output text)"
echo "  $URL"
echo "  chave do consumidor $CONSUMIDOR lida"

passo "O BFF responde pela borda"
PRONTIDAO="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
  -H "x-api-key: $CHAVE" "$URL/bff/actuator/health/readiness" || true)"
if [ "$PRONTIDAO" = "200" ]; then
  echo "  readiness 200"
else
  aviso "readiness devolveu $PRONTIDAO. O BFF pode ainda estar subindo."
fi

CPF="$(python3 - "$MODELO" <<'PY'
import json, sys
modelo = json.load(open(sys.argv[1], encoding="utf-8"))
print(next((v["value"] for v in modelo["values"] if v["key"] == "cpf"), ""))
PY
)"

TOKEN=""
passo "Token do usuario de demonstracao"
if [ -z "${ST_SENHA_DEMO:-}" ]; then
  aviso "ST_SENHA_DEMO ausente: o ambiente sai sem token."
  aviso "A senha do seed esta no README do service-track-usuarios-veiculos."
elif [ -z "$CPF" ]; then
  aviso "o modelo nao traz cpf. Nada a autenticar."
else
  RESPOSTA="$(curl -s --max-time 20 -X POST "$URL/autenticacao" \
    -H "x-api-key: $CHAVE" -H 'Content-Type: application/json' \
    -d "{\"cpf\":\"$CPF\",\"senha\":\"$ST_SENHA_DEMO\"}" || true)"
  TOKEN="$(printf '%s' "$RESPOSTA" | python3 -c '
import json, sys
try:
    print(json.load(sys.stdin).get("token", ""))
except Exception:
    print("")
')"
  if [ -n "$TOKEN" ]; then
    echo "  token emitido para o CPF $CPF"
  else
    aviso "login falhou. Resposta: $(printf '%s' "$RESPOSTA" | head -c 200)"
    aviso "Em ambiente novo, confira se a esteira Banco do servico de usuarios rodou."
  fi
fi

passo "Gatilho de falha da saga"
SEM_SALDO="$(python3 - "$MODELO" <<'PY'
import json, sys
modelo = json.load(open(sys.argv[1], encoding="utf-8"))
print(next((v["value"] for v in modelo["values"] if v["key"] == "insumoSemSaldo"), ""))
PY
)"
if [ -n "$TOKEN" ] && [ -n "$SEM_SALDO" ]; then
  SALDO="$(curl -s --max-time 20 -H "x-api-key: $CHAVE" -H "Authorization: Bearer $TOKEN" \
    "$URL/bff/catalogo/insumos/$SEM_SALDO/saldo" || true)"
  DISPONIVEL="$(printf '%s' "$SALDO" | python3 -c '
import json, sys
try:
    print(json.load(sys.stdin).get("quantidadeDisponivel", "?"))
except Exception:
    print("?")
')"
  if [ "$DISPONIVEL" = "0" ] || [ "$DISPONIVEL" = "0.0" ] || [ "$DISPONIVEL" = "0.0000" ]; then
    echo "  armado: OL-20W50-MIN-1L com disponivel $DISPONIVEL"
  else
    aviso "disponivel do insumo-gatilho e '$DISPONIVEL', nao zero."
    aviso "O cenario da saga infeliz NAO vai recusar. Reaplique o seed do catalogo."
  fi
else
  aviso "sem token: nao foi possivel conferir o gatilho."
fi

passo "Escrevendo o ambiente"
MODELO="$MODELO" URL="$URL" CHAVE="$CHAVE" TOKEN="$TOKEN" SAIDA="$SAIDA" \
SENHA="${ST_SENHA_DEMO:-}" python3 - <<'PY'
import json, os

modelo = json.load(open(os.environ["MODELO"], encoding="utf-8"))
preenchido = {
    "baseUrl": os.environ["URL"],
    "apiKey": os.environ["CHAVE"],
    "token": os.environ["TOKEN"],
    "senha": os.environ["SENHA"],
}
preenchido = {chave: valor for chave, valor in preenchido.items() if valor}

for valor in modelo["values"]:
    if valor["key"] in preenchido:
        valor["value"] = preenchido[valor["key"]]

modelo["name"] += " (gerado)"
modelo["id"] += "-gerado"

with open(os.environ["SAIDA"], "w", encoding="utf-8") as arquivo:
    json.dump(modelo, arquivo, ensure_ascii=False, indent=2)
    arquivo.write("\n")

vazias = [v["key"] for v in modelo["values"] if not v["value"]]
print("  " + os.environ["SAIDA"])
print("  sem valor: " + (", ".join(vazias) if vazias else "nenhuma"))
PY

passo "Pronto"
echo "  Importe $SAIDA no Postman."
if [ -n "$TOKEN" ]; then
  echo "  O token ja esta no arquivo: a pasta Apresentacao roda sem passar pela Autenticacao."
else
  echo "  Sem token: rode Autenticacao antes da pasta Apresentacao."
fi
echo "  Falta ainda, fora deste script:"
echo "    kubectl -n service-track-ordens port-forward svc/service-track-ordens 18082:80"
echo ""
echo "  Este arquivo carrega chave de API e token. Nao e versionado."
