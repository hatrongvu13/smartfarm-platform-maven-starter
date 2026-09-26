#!/usr/bin/env sh
set -eu
mkdir -p .local
umask 077
if [ -e .local/identity-tls.key ] || [ -e .local/identity-tls.crt ]; then
 echo "Existing TLS certificate detected; refusing to overwrite." >&2
 exit 1
fi
openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 30 \
 -keyout .local/identity-tls.key -out .local/identity-tls.crt \
 -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
echo 'Dev TLS certificate created; import identity-tls.crt into Gateway JVM truststore.'
