#!/usr/bin/env bash
set -euo pipefail

AMBIENTE="${1:-}"
PROJETO="${PROJETO:-servicetrack}"
REGIAO="${AWS_REGION:-us-east-1}"
NAMESPACE="${NAMESPACE:-service-track-bff}"
SEGREDO="service-track-bff-jwt"
PARAMETRO="/$PROJETO/$AMBIENTE/jwt-public"

if [[ "$AMBIENTE" != "hml" && "$AMBIENTE" != "prd" ]]; then
  echo "uso: $0 <hml|prd>" >&2
  exit 1
fi

if ! CHAVE="$(aws ssm get-parameter --name "$PARAMETRO" --region "$REGIAO" \
    --query Parameter.Value --output text 2>/dev/null)"; then
  echo "::error::$PARAMETRO nao existe no SSM." >&2
  echo "A chave publica e emitida pelo service-track-aws-iac junto com a Lambda de" >&2
  echo "autenticacao, e so existe com habilitar_autenticacao = true. Sem ela nao ha" >&2
  echo "quem emita token, e o BFF nao teria o que validar." >&2
  exit 1
fi

if [[ "$CHAVE" != *"BEGIN PUBLIC KEY"* ]]; then
  echo "::error::$PARAMETRO nao contem uma chave publica em PEM." >&2
  exit 1
fi

kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl create secret generic "$SEGREDO" \
  --namespace "$NAMESPACE" \
  --from-literal=publicKey.pem="$CHAVE" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "secret $SEGREDO aplicado em $NAMESPACE a partir de $PARAMETRO"
echo "e a mesma chave que a Lambda usa para assinar: um par por ambiente, emitido no apply."
