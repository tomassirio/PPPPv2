#!/usr/bin/env bash
# Prints the base64 kubeconfig of the ppppv2 deployer (deploy/ci-access.yaml), for the KUBECONFIG_CONTENT secret:
#   deploy/kubeconfig.sh | gh secret set KUBECONFIG_CONTENT --env prod -R tomassirio/PPPPv2
# Reads the cluster address and CA from your own kubeconfig's context (default: turing).
set -euo pipefail
ctx="${1:-turing}"
cluster=$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$ctx\")].context.cluster}")
server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$cluster\")].cluster.server}")
ca=$(kubectl config view --raw -o jsonpath="{.clusters[?(@.name==\"$cluster\")].cluster.certificate-authority-data}")
token=$(kubectl --context "$ctx" -n ppppv2 get secret github-deployer-token -o jsonpath='{.data.token}' | base64 -d)
base64 <<EOF | tr -d '\n'
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
