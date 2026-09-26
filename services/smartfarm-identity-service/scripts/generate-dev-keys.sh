#!/usr/bin/env sh
set -eu
mkdir -p .local
umask 077
if [ -e .local/identity-private.pem ] || [ -e .local/identity-public.pem ]; then
  echo "Existing signing key detected; refusing to overwrite." >&2
  exit 1
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local/identity-private.pem
openssl pkey -in .local/identity-private.pem -pubout -out .local/identity-public.pem
printf 'Generated dev keys under .local/ (do not commit).\n'
