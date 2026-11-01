#!/usr/bin/env bash
set -euo pipefail

AMBIENTE="${1:-}"
CONSUMIDOR="${2:-web}"
REGIAO="${AWS_REGION:-us-east-1}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODELO="$RAIZ/docs/postman/ambientes/$AMBIENTE.postman_environment.json"
SAIDA="$RAIZ/docs/postman/ambientes/$AMBIENTE.gerado.json"

if [[ "$AMBIENTE" != "hml" && "$AMBIENTE" != "prd" ]]; then
  echo "uso: $0 <hml|prd> [web|mobile|ci]" >&2
  echo "O ambiente local nao precisa ser gerado: ele nao tem valor volatil." >&2
  exit 2
fi

[ -f "$MODELO" ] || { echo "::error::modelo ausente: $MODELO" >&2; exit 1; }

if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "::error::credenciais da AWS invalidas ou expiradas. Renove o laboratorio." >&2
  exit 1
fi

URL="$(aws ssm get-parameter --name "/servicetrack/$AMBIENTE/api/base-url" \
  --region "$REGIAO" --query Parameter.Value --output text 2>/dev/null || true)"
if [ -z "$URL" ] || [ "$URL" = "None" ]; then
  echo "::error::a URL da API de $AMBIENTE nao esta no SSM. O ambiente esta de pe?" >&2
  exit 1
fi

ID_CHAVE="$(aws apigateway get-api-keys --region "$REGIAO" --include-values \
  --query "items[?name=='servicetrack-$AMBIENTE-$CONSUMIDOR'].id" --output text 2>/dev/null || true)"
if [ -z "$ID_CHAVE" ] || [ "$ID_CHAVE" = "None" ]; then
  echo "::error::consumidor '$CONSUMIDOR' nao encontrado em $AMBIENTE. Use web, mobile ou ci." >&2
  exit 1
fi

CHAVE="$(aws apigateway get-api-key --api-key "$ID_CHAVE" --include-value \
  --region "$REGIAO" --query value --output text)"

MODELO="$MODELO" URL="$URL" CHAVE="$CHAVE" AMBIENTE="$AMBIENTE" SAIDA="$SAIDA" python3 - <<'PY'
import json, os

modelo = json.load(open(os.environ["MODELO"], encoding="utf-8"))
preenchido = {"baseUrl": os.environ["URL"], "apiKey": os.environ["CHAVE"]}

for valor in modelo["values"]:
    if valor["key"] in preenchido:
        valor["value"] = preenchido[valor["key"]]

modelo["name"] += " (gerado)"
modelo["id"] += "-gerado"

with open(os.environ["SAIDA"], "w", encoding="utf-8") as arquivo:
    json.dump(modelo, arquivo, ensure_ascii=False, indent=2)
    arquivo.write("\n")
PY

echo "ambiente de $AMBIENTE gerado em:"
echo "  $SAIDA"
echo ""
echo "  baseUrl: $URL"
echo "  apiKey:  <$(printf %s "$CHAVE" | wc -c | tr -d ' ') caracteres, consumidor $CONSUMIDOR>"
echo ""
echo "Este arquivo carrega a chave de API e nao e versionado. Importe no Postman e,"
echo "depois da proxima recriacao do ambiente, gere de novo: URL e chave mudam."
