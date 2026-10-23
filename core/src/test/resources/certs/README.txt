Test-only certificates for gw_paste98's unit tests. Not used by the program.

  rsa-san.der       RSA 2048, CN pastes.example.com, SANs pastes.example.com,
                    *.pastes.example.com, IP 192.0.2.7
  ec-cn-only.der    EC P-256, CN cn-only.example.org, no SANs
  ed25519.der       Ed25519, CN ed.example.net
  test-server.p12   RSA 2048 key and certificate for a local test TLS server
                    (CN/SAN localhost, IP 127.0.0.1), password "testonly"

pins.txt lists each certificate's SHA-256 SPKI pin as computed by:

  openssl x509 -inform der -in FILE -pubkey -noout \
    | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64
