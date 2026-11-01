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

ler_do_servico() {
  local ns="$1" caminho="$2" jq_filtro="$3"
  kubectl -n "$ns" exec "deploy/$ns" -- \
    sh -c "wget -qO- --timeout=8 http://localhost:8080$caminho" 2>/dev/null \
    | python3 -c "import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
itens = d if isinstance(d, list) else d.get('content', [])
print(itens[0]['$jq_filtro'] if itens else '', end='')" 2>/dev/null || true
}

INSUMO_ID=""; SERVICO_ID=""; CLIENTE_ID=""; MECANICO_ID=""; VEICULO_ID=""
if kubectl get ns service-track-catalogo >/dev/null 2>&1; then
  echo "lendo identificadores do ambiente..."
  INSUMO_ID="$(ler_do_servico service-track-catalogo /insumos id)"
  SERVICO_ID="$(ler_do_servico service-track-catalogo /servicos id)"
  CLIENTE_ID="$(ler_do_servico service-track-usuarios-veiculos '/usuarios?tipo=CLIENTE' id)"
  MECANICO_ID="$(ler_do_servico service-track-usuarios-veiculos '/usuarios?tipo=MECANICO' id)"
  [ -n "$CLIENTE_ID" ] && VEICULO_ID="$(ler_do_servico service-track-usuarios-veiculos "/veiculos?clienteId=$CLIENTE_ID" id)"
else
  echo "::warning::kubeconfig sem o cluster de $AMBIENTE: os identificadores ficam vazios."
fi

MODELO="$MODELO" URL="$URL" CHAVE="$CHAVE" AMBIENTE="$AMBIENTE" SAIDA="$SAIDA" \
INSUMO_ID="$INSUMO_ID" SERVICO_ID="$SERVICO_ID" CLIENTE_ID="$CLIENTE_ID" \
MECANICO_ID="$MECANICO_ID" VEICULO_ID="$VEICULO_ID" python3 - <<'PY'
import json, os

modelo = json.load(open(os.environ["MODELO"], encoding="utf-8"))
preenchido = {
    "baseUrl": os.environ["URL"],
    "apiKey": os.environ["CHAVE"],
    "insumoId": os.environ.get("INSUMO_ID", ""),
    "servicoId": os.environ.get("SERVICO_ID", ""),
    "clienteId": os.environ.get("CLIENTE_ID", ""),
    "mecanicoId": os.environ.get("MECANICO_ID", ""),
    "veiculoId": os.environ.get("VEICULO_ID", ""),
}
preenchido = {k: v for k, v in preenchido.items() if v}

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
printf "  insumoId: %s\n" "${INSUMO_ID:-<vazio>}"
printf "  clienteId: %s\n" "${CLIENTE_ID:-<vazio: nenhum cliente cadastrado>}"
printf "  mecanicoId: %s\n" "${MECANICO_ID:-<vazio: nenhum mecanico cadastrado>}"
if [ -z "$CLIENTE_ID" ]; then
  echo ""
  echo "::warning::Sem cliente cadastrado, a autenticacao nao tem com quem funcionar."
  echo "O baseline do servico de usuarios nao semeia usuario, e nao existe rota publica"
  echo "para criar um: o servico nao fica atras do gateway e o BFF so le."
fi
echo ""
echo "Este arquivo carrega a chave de API e nao e versionado. Importe no Postman e,"
echo "depois da proxima recriacao do ambiente, gere de novo: URL e chave mudam."
