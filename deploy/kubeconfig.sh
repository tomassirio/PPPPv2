#!/usr/bin/env bash
# Sets the KUBECONFIG_CONTENT secret (environment prod) to the base64 kubeconfig of the ppppv2 deployer
# (deploy/ci-access.yaml, apply it first):
#   deploy/kubeconfig.sh            # context "turing"
#   deploy/kubeconfig.sh other-ctx
# Sets nothing when any piece is missing (a piped `| gh secret set` would have stored an empty secret). Nothing is
# written to disk. Reads the cluster address and CA from your own kubeconfig's context.
set -euo pipefail
ctx="${1:-turing}"
cluster=$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$ctx\")].context.cluster}")
server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$cluster\")].cluster.server}")
ca=$(kubectl config view --raw -o jsonpath="{.clusters[?(@.name==\"$cluster\")].cluster.certificate-authority-data}")
token=$(kubectl --context "$ctx" -n ppppv2 get secret github-deployer-token -o jsonpath='{.data.token}' | base64 -d)
for v in cluster server ca token; do
  [ -n "${!v}" ] || { echo "No $v for context $ctx (applied deploy/ci-access.yaml?); secret left as it was" >&2; exit 1; }
done
config=$(base64 <<EOF | tr -d '\n'
apiVersion: v1
kind: Config
clusters:
- name: turing
  cluster:
    server: $server
    certificate-authority-data: $ca
users:
- name: github-deployer
  user:
    token: $token
contexts:
- name: ppppv2
  context:
    cluster: turing
    user: github-deployer
    namespace: ppppv2
current-context: ppppv2
EOF
)
gh secret set KUBECONFIG_CONTENT --env prod -R tomassirio/PPPPv2 --body "$config"
echo "KUBECONFIG_CONTENT set (deployer for $server, namespace ppppv2)"
